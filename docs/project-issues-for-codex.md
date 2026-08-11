# 当前项目问题清单

更新日期：`2026-06-17`

这份文档记录当前仍值得关注的问题。早期“没有真实串口”“没有单元测试”“手动加水未实现”“订单链路未接入”“ModbusRtu.kt 仍 1500 行”等结论已经过期。

## 当前结论

项目已经进入工程化中后期：

1. POS 订单、配方同步、注册、催单、转台、延时、取消/重新加水等业务链路已接入。
2. 手动模式和接单模式已共用相位规划核心。
3. 鸡油/骨膏左上规则、接口数组顺序、不使用 `potSort`、配方 Code 匹配等关键规则已有单元测试保护。
4. 真实串口 Modbus 链路已现场验证过，并保留 IUCLC 问题日志。
5. `MachineCoordinator.kt` 已拆到约 442 行，当前作为 UI 门面 facade 使用。
6. 原 `ModbusRtu.kt` 已拆分为 protocol、transport、controller、commands、mock、queue 等文件。
7. 订单锅型与锅底数量完整性校验已接入，并在相位下发前做安全拦截。
8. `SettingsStore` 已完成第一阶段写入收口，复合设置保存走批量写入并只刷新一次配置状态。
9. `LocalOrderStore` 已完成第一阶段持久化边界抽象，并补本地订单状态迁移单元测试。

## P0：真实 PLC 长时间稳定性仍需验证

问题：

1. 当前多数关键链路已经编译和单测通过，但长期运行、断线恢复、急停/液位/加热/备用泵互斥仍需要现场反复验证。
2. Modbus 字节级行为已经被现场验证过，不建议无验证窗口时继续改。

建议：

1. 按 [field-validation-checklist.md](D:/Users/achillesniu/Documents/pot_machine_project/docs/field-validation-checklist.md) 做现场验证。
2. 出问题时优先记录 Logcat TX/RX、PLC 寄存器/线圈、现场动作，而不是直接重构代码。

## P1：`LocalOrderStore` 仍使用 SharedPreferences JSON

文件：

1. `app/src/main/java/com/example/plccontroller/data/local/LocalOrderStore.kt`

已完成：

1. 已抽出 `OrderPersistenceStore`。
2. `HttpOrderRepository` 和 `OrderEventReducer` 已改为依赖持久化接口。
3. `LocalOrderStore` 已支持测试用 key-value storage 与 codec。
4. 已新增 `LocalOrderStoreTest` 覆盖延时、取消、重新加水、转台 alias、催单去重、结构异常刷新。

仍需关注：

1. 当前底层仍是 SharedPreferences JSON，适合作为轻量缓存，但不适合作为长期订单数据库。
2. 如果现场要求断电恢复、历史追溯、日志对账，后续会吃力。
3. Room/SQLite 迁移评估与 schema 草案已记录在 `order-persistence-room-migration-plan.md`。

建议：

1. 短期保留当前 JSON 实现，避免引入 Room 造成额外变量。
2. 上线前如果确认需要订单恢复和审计，再按迁移草案新增 Room/SQLite 实现并挂到 `OrderPersistenceStore`。

## P1：`SettingsStore` 后续增强

文件：

1. `app/src/main/java/com/example/plccontroller/data/SettingsStore.kt`

已完成：

1. 增加统一写入锁。
2. 增加 `updateBatch` 批量写入入口。
3. `MachineCoordinator.updateSettings()` 和加热执行器安全保存已改为批量写入。

后续建议：

1. 暂不继续扩大。
2. 如果未来需要配置审计、版本回滚、保存失败提示，再单独设计。
3. 保存加热、备用泵等互斥配置时，继续保持保存前校验。

## P1：运行状态模型仍偏大

文件：

1. `app/src/main/java/com/example/plccontroller/runtime/MachineRuntimeModels.kt`
2. `app/src/main/java/com/example/plccontroller/runtime/MachineRuntimeStore.kt`

问题：

1. 运行状态仍承载 PLC、网络、订单、配方、维护、传锅、副屏等多组状态。
2. 高频轮询和 UI 重组继续增加时，可能需要拆成更细的 `StateFlow` 或投影模型。

建议：

1. 先保持当前结构。
2. 等 UI 页面继续稳定后，再评估拆 `PlcRuntimeState`、`OrderRuntimeState`、`TransferRuntimeState`、`FormulaRuntimeState`。

## P2：部分 UI 文件仍偏大

当前较大的 UI 文件包括：

1. `SettingsMaintenancePage.kt`
2. `ManualWaterComponents.kt`
3. `SettingsAccessPage.kt`
4. `OrdersScreen.kt`
5. `SettingsFormulaComponents.kt`

建议：

1. 这些不是立即风险。
2. 后续按功能变更顺手拆，不要为了拆而拆。
3. UI 不直接拼复杂业务规则，业务规则继续沉到 domain/runtime。

## P2：文档仍有历史草案

问题：

1. `docs` 中仍保留早期草案、确认清单和历史分析。
2. 部分旧文档的“当前状态”已经不是当前状态。

建议：

1. 后续查文档优先从 [README.md](D:/Users/achillesniu/Documents/pot_machine_project/docs/README.md) 进入。
2. 历史草案只用于回查，不作为当前实施依据。

## 已完成且不应重复返工

1. `Models.kt` 已拆分。
2. `UiModels.kt` 已拆分。
3. `UiComponents.kt` 已拆分。
4. `SecondaryDisplayScreen.kt` 已拆分到入口编排级别。
5. `SettingsDevicePage.kt` 已拆分配置模型和 UI 区块。
6. `HomeScreen.kt` 已拆分主面板、订单队列和 Preview。
7. `MachineCoordinator.kt` 已抽出订单同步、配方同步、相位执行、加热控制、单独加水、传锅、设备注册等协调器。
8. Modbus 通信层已完成文件边界拆分。
9. `SettingsStore` 已完成第一阶段写入收口。
10. `LocalOrderStore` 已完成第一阶段持久化边界抽象。
11. 已补最小单元测试覆盖相位规划、订单事件、队列投影、本地订单存储、Modbus、加热、单独加水、副屏分区等关键规则。
12. 订单结构完整性校验已接入：接口缺失不乱补，解析缺失先保守重解析，解析失败或数量异常禁止加水/PLC 下发。

## 推荐下一步

1. 短期：按现场验证清单继续验证订单模式、手动模式、转锅弹窗、鸡油/骨膏多相位、断线恢复和安全互斥。
2. 中期：根据现场要求决定是否按 `order-persistence-room-migration-plan.md` 迁移到 Room/SQLite。
3. 后续：如果 UI 或运行状态继续膨胀，再拆运行状态模型和页面组件。
4. 暂缓：Modbus 行为级调整，只在真实 PLC 验证窗口内单独处理。
