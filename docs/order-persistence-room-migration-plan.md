# 订单持久化 Room/SQLite 迁移评估

更新日期：`2026-06-19`

本文档记录从 `SharedPreferences + JSON` 迁移到 Room/SQLite 的设计与实施结果。

## 当前结论

结论：**Room v1 已切换为订单主存储。**

当前实施边界：

1. `RoomOrderPersistenceStore` 是运行时主存储，继续复用 `LocalOrderStore` 中已验证的订单状态机规则。
2. 首次访问 Room 时从旧 `local_order_store` SharedPreferences 一次性导入；旧 JSON 保留只读，不再双写。
3. 已落地订单、alias、订单事件、PLC 命令审计、PLC 故障事件和迁移元数据六张表。
4. 心跳和普通轮询不入库；只记录低频命令和升级后的通信故障/恢复。
5. Room schema 导出到 `app/schemas/`，后续版本必须提供显式 Migration，禁止破坏性回退。

## 迁移触发条件

满足下面任意两条，就建议启动 Room/SQLite 实现：

1. 需要断电/重启后恢复未完成订单。
2. 需要追溯订单从下单到完成的完整生命周期。
3. 需要证明“某个订单是否已经加水/加料/传锅/取消”。
4. 需要排查催单、转台、退单后为什么出现或没有出现某个订单。
5. 需要记录 PLC 命令号、命令参数、写入结果和失败原因。
6. 需要做现场故障复盘，定位是平台订单、安卓逻辑、PLC 状态还是现场操作造成。
7. 本地订单数量超过轻量缓存适合范围，例如需要保留多天数据。

如果只是短期联调、现场试跑、每天清空缓存，当前 JSON 实现可以继续保留。

## 目标边界

Room/SQLite 迁移后，目标不是“把所有东西都塞进数据库”，而是明确三类数据：

1. 当前订单状态：用于 UI 展示、断电恢复、重新加水、待传锅。
2. 订单事件审计：用于解释订单为什么变成当前状态。
3. PLC 命令审计：用于解释设备层到底收到了什么命令。

不要记录高频心跳明细。M400 心跳、轮询快照这类高频数据只建议记录异常摘要，避免数据库快速膨胀。

## 推荐表结构 v1

### `orders`

用途：保存订单当前态，是 `OrderPersistenceStore.allOrders()` 的主要来源。

建议字段：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | TEXT PRIMARY KEY | 301 根记录 ID；同内容多锅仍保持唯一 |
| `recipe_code` | TEXT | 配方/锅底匹配码 |
| `quantity` | INTEGER | 数量 |
| `table_code` | TEXT | 桌台 |
| `order_time` | TEXT | 订单时间 |
| `pot_mode` | TEXT | 单锅/拼锅/三拼/四宫格 |
| `pot_bottom_name` | TEXT | 锅底名称 |
| `taste_summary` | TEXT | 口味 |
| `slot_summary` | TEXT | 接口数组顺序下的锅底摘要 |
| `slot_code_summary` | TEXT | 锅底 Code 摘要 |
| `status` | TEXT | `OrderStatus` |
| `base_hash` | TEXT | 同一锅底组合去重/转台匹配辅助 |
| `ikms_order` | TEXT | IKMS 订单号 |
| `source_root_id` | TEXT | POS 根节点 ID |
| `root_pos_food_code` | TEXT | 锅型 Code |
| `operator` | TEXT | 操作员 |
| `operation` | TEXT | 301/302/303/304 |
| `pot_bottom_summary` | TEXT | 锅底摘要 |
| `structure_status` | TEXT | 结构完整性状态 |
| `structure_message` | TEXT | 结构异常说明 |
| `expected_slot_count` | INTEGER | 期望锅底数 |
| `attached_bottom_count` | INTEGER | 已挂载锅底数 |
| `raw_bottom_candidate_count` | INTEGER | 原始候选锅底数 |
| `missing_slot_labels_json` | TEXT | 缺失锅位标签 JSON |
| `created_at` | INTEGER | 本地创建时间戳 |
| `updated_at` | INTEGER | 本地更新时间戳 |

建议索引：

1. `idx_orders_status_order_time(status, order_time)`
2. `idx_orders_base_hash(base_hash)`
3. `idx_orders_ikms_order(ikms_order)`
4. `idx_orders_source_root(source_root_id, root_pos_food_code)`

### `order_aliases`

用途：保存转台、催单、平台订单号变化后的 alias，避免误插重复订单。

建议字段：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `alias` | TEXT | posOrder / ikmsOrder / mainPosOrder 等别名 |
| `order_id` | TEXT | 对应本地订单 ID |
| `alias_type` | TEXT | `IKMS` / `POS` / `MAIN_POS` / `TRANSFER` / `UNKNOWN` |
| `created_at` | INTEGER | 建立时间 |

建议索引：

1. `idx_order_aliases_order_id(order_id)`

约束：

1. `(alias, order_id)` 为联合主键；同一账单 alias 可以关联多口锅。
2. 转台后新订单 alias 必须追加到旧订单上。
3. 催单时优先用 alias 命中旧单，命中后不再插入新单。

### `order_events`

用途：记录订单生命周期，解释“为什么订单现在是这个状态”。

建议字段：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | INTEGER PRIMARY KEY AUTOINCREMENT | 事件 ID |
| `order_id` | TEXT | 本地订单 ID，可为空 |
| `event_type` | TEXT | 事件类型 |
| `operation` | TEXT | 平台 operation，301/302/303/304 |
| `from_status` | TEXT | 变化前状态 |
| `to_status` | TEXT | 变化后状态 |
| `table_code` | TEXT | 事件发生时桌台 |
| `source_order_alias` | TEXT | 触发事件的订单 alias |
| `message` | TEXT | 人类可读说明 |
| `raw_payload_json` | TEXT | 必要时保存裁剪后的原始事件 |
| `created_at` | INTEGER | 事件时间 |

建议事件类型：

1. `ORDER_RECEIVED`
2. `ORDER_URGED`
3. `ORDER_CANCELLED_BY_API`
4. `ORDER_TRANSFERRED`
5. `ORDER_DELAYED`
6. `ORDER_RESTORED_FROM_DELAY`
7. `ORDER_REWATER_REQUESTED`
8. `ORDER_DISPATCH_STARTED`
9. `ORDER_ROTATION_SKIPPED`
10. `ORDER_WAITING_TRANSFER`
11. `ORDER_TRANSFER_CONFIRMED`
12. `ORDER_FAILED`
13. `ORDER_STRUCTURE_BLOCKED`
14. `ORDER_STRUCTURE_RECOVERED`

建议索引：

1. `idx_order_events_order_id_created(order_id, created_at)`
2. `idx_order_events_type_created(event_type, created_at)`

### `plc_command_audits`

用途：记录事件驱动写入 PLC 的命令，不记录高频轮询。

建议字段：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | INTEGER PRIMARY KEY AUTOINCREMENT | 审计 ID |
| `command_id` | INTEGER | PLC 命令号 |
| `order_id` | TEXT | 关联订单，可为空 |
| `command_type` | TEXT | 相位/加热/单独加水/备用泵配置 |
| `command_name` | TEXT | 人类可读命令名 |
| `request_register_start` | INTEGER | 写入 D 区起始地址 |
| `request_payload_json` | TEXT | 命令参数快照 |
| `tx_hex` | TEXT | 可选，关键命令 TX 帧 |
| `rx_hex` | TEXT | 可选，关键命令 RX 帧 |
| `result` | TEXT | `SUCCESS` / `FAILED` / `TIMEOUT` / `CANCELLED` |
| `error_message` | TEXT | 失败原因 |
| `created_at` | INTEGER | 创建时间 |
| `completed_at` | INTEGER | 完成时间 |

建议命令类型：

1. `PHASE_COMMAND`
2. `HEATER_PARAMETER`
3. `HEATER_ENABLE`
4. `HEATER_DISABLE`
5. `STANDALONE_WATER_PARAMETER`
6. `SPARE_PUMP_CONFIG`
7. `DEBUG_COMMAND`

建议索引：

1. `idx_plc_commands_command_id(command_id)`
2. `idx_plc_commands_order_id(order_id)`
3. `idx_plc_commands_type_created(command_type, created_at)`

注意：

1. M400 心跳不写入本表。
2. 高频轮询快照不写入本表。
3. 如果要记录异常轮询，只记录异常摘要，例如通信断开、CRC 错误、连续超时。

### `plc_fault_events`

用途：记录低频故障和恢复事件。

建议字段：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | INTEGER PRIMARY KEY AUTOINCREMENT | 事件 ID |
| `fault_type` | TEXT | 通信/液位/急停/加热/相位/备用泵 |
| `severity` | TEXT | INFO/WARN/ERROR/FATAL |
| `message` | TEXT | 说明 |
| `snapshot_json` | TEXT | 关键 PLC 快照 |
| `created_at` | INTEGER | 发生时间 |
| `resolved_at` | INTEGER | 恢复时间，可为空 |

建议记录：

1. 通信断开。
2. 通信恢复。
3. 连续读超时。
4. CRC 异常重新出现。
5. 液位异常。
6. 急停触发。
7. 加热被安全条件禁止。
8. 相位等待超时。
9. 备用泵互斥配置被拒绝。

## `OrderPersistenceStore` 到 Room 的映射

当前接口方法与表操作建议：

| 接口方法 | Room 实现建议 |
| --- | --- |
| `insertIgnore(order)` | 事务：查 `orders.id` / alias / sourceRoot，决定插入或结构刷新 |
| `cancelPendingByBaseHash(baseHash)` | 事务：更新待加工订单状态，写 `order_events` |
| `updateTableByBaseHash(...)` | 事务：更新 table，追加 alias，写转台事件 |
| `updateTableByOrderAlias(...)` | 事务：alias 命中旧单，更新 table，追加新 alias |
| `markUrgedByBaseHash(...)` | 更新 `isUrged/urgedAt`，写催单事件，不覆盖转台 operation |
| `markUrgedByOrderAliases(...)` | alias 命中并合并，防止重复订单 |
| `markDispatching(orderId)` | 更新状态，写开始执行事件 |
| `markPendingDelivery(orderId)` | 更新待传锅，写完成加水事件 |
| `markCompleted(orderId)` | 更新完成，写传锅完成事件 |
| `markCancelled(orderId)` | 更新取消，写取消事件 |
| `markFailed(orderId)` | 更新失败，写失败事件 |
| `markDelayed(orderId)` | 更新延时，写延时事件 |
| `markPendingWater(orderId)` | 更新待加水，写恢复/重新加水事件 |
| `allOrders()` | 查询最近有效订单 |
| `activeOrders()` | 查询未完成/未取消订单 |

## 迁移步骤与实施状态

### 第 1 阶段：准备（已完成）

1. 保留当前 `LocalOrderStore`。
2. 新增 Room entity/DAO/repository，并由 `OrderPersistenceStore` 隔离上层调用。
3. 用单元测试固定 `OrderPersistenceStore` 行为。
4. 切换运行链路前完成订单身份与事件匹配规则复核。

### 第 2 阶段：Room 实现并行验证（已完成）

1. 新增 `RoomOrderPersistenceStore`。
2. 复用 `LocalOrderStore` 已验证的状态机规则，Room 只替换持久化载体。
3. 用现有契约测试对比 `allOrders()`、`activeOrders()`、转台 alias、催单、延时、取消和重新加水行为。
4. 未保留长期双写开关，避免 JSON 与 Room 形成两个事实源。

### 第 3 阶段：切换主存储（已完成）

1. Room 固定为主存储，不保留容易形成分叉事实源的长期双写开关。
2. 首次启动从 JSON 导入 Room。
3. 导入成功后标记迁移完成。
4. 保留 JSON 只读回退一段时间。
5. 现场验证通过后移除回退。

### 第 4 阶段：开启审计日志（已完成基础接入）

1. 先开启订单事件审计。
2. 再开启 PLC 命令审计。
3. 最后开启低频故障事件审计。
4. 不记录高频心跳和完整轮询明细。

## 数据保留策略

建议默认：

1. 未完成订单永久保留到完成或人工清理。
2. 已完成/已取消订单保留 7 天。
3. 订单事件保留 14 天。
4. PLC 命令审计保留 7 天。
5. PLC 故障事件保留 30 天。

如果现场需要长期追溯，可以按门店容量调整。

## 风险与规避

### 风险 1：迁移导致订单重复

规避：

- `order_aliases` 使用 `(alias, order_id)` 联合键；同一 alias 下通过锅型、baseHash、事件时间和 FIFO 继续缩小候选。
- 导入 JSON 时在同一事务中先写订单，再写带外键的 alias。
- 转台和催单优先用 `baseHash`，必要时再按 alias、锅型和事件时间缩小候选。

### 风险 2：断电时事务中断

规避：

- 状态变更和事件写入放在同一事务。
- PLC 命令审计先写 `PENDING`，命令完成后再更新结果。

### 风险 3：数据库膨胀

规避：

- 不记录心跳明细。
- 不记录每次轮询快照。
- 定期清理已完成订单和旧审计。

### 风险 4：现场回滚困难

规避：

- 第一阶段只做并行验证。
- 切换前保留 JSON 只读导入和回退。
- 迁移版本号写入设置，避免重复导入。

## 当前维护要求

1. 每次 schema 变化必须提交新的 schema JSON 与显式 Room Migration。
2. 现场验证首次升级后的 JSON 导入数量、未完成订单状态和待传锅记录。
3. 调试清理只能从维护页执行；正在制作或存在物理输出时禁止清理。
4. `baseHash` 只用于事件关联和 FIFO 候选匹配，不能再作为订单实体主键。
