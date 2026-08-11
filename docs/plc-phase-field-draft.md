# 加水四路独立时长与辅料约束字段草案

关联文档：
[plc-command-handshake-draft.md](/D:/Users/achillesniu/Documents/自研打锅机项目/docs/plc-command-handshake-draft.md)
[plc-action-execution-baseline.md](/D:/Users/achillesniu/Documents/自研打锅机项目/docs/plc-action-execution-baseline.md)
[control-routing.md](/D:/Users/achillesniu/Documents/自研打锅机项目/docs/control-routing.md)

这份文档只做一件事：

把原来单一的 `waterDuration` 升级成适合当前设备逻辑的“4 路独立加水时长 + 辅料受左上工位约束”的字段草案。

## 1. 当前字段设计目标

字段设计必须同时满足下面 4 条：

1. 单锅、拼锅、四宫格都支持“所有需要加水的锅位统一启动”。
2. 每个锅位的加水时间独立停止。
3. 鸡油和骨膏只允许在当前处于左上工位、且允许执行辅料的锅位上动作。
4. 非左上锅位如果也需要鸡油或骨膏，则必须拆到人工转锅后的下一相位执行。
5. 只要整单里存在需要鸡油或骨膏、但当前不在左上角的锅位，就应优先先把其中一个转到左上角，再开始第一相位。
6. 当前左上锅位如果鸡油和骨膏都需要，则两者允许同时开启。
7. 对于延后锅位，转锅后的相位应把该锅位的加水、鸡油、骨膏放在同一相位里做。

## 2. 最终建议

建议把一次 PLC 命令理解成“一次执行相位”。

这里的“相位”可以直接理解成：

1. 安卓对 PLC 的一次完整下发包
2. PLC 对这一包参数的一次完整执行轮次
3. 这一轮里允许哪些水路启动、当前左上是谁、要不要鸡油和骨膏，都在这一次命令里说清楚
4. 只有当这一轮允许的动作全部结束后，PLC 才把这条 `commandId` 标记为完成

这一相位里允许同时描述：

1. 4 路水路统一启动，但各自独立停止
2. 当前左上工位是否执行鸡油
3. 当前左上工位是否执行骨膏

## 3. 命令区字段草案

下面仍按建议协议区 `D300 ~ D319` 来写。

| 寄存器 | 字段名 | 含义 |
| --- | --- | --- |
| `D300` | `commandId` | 本次相位命令号 |
| `D301` | `commandType` | 建议固定为“相位执行命令” |
| `D302` | `jobSlotMask` | 本轮参与加水的逻辑锅位掩码 |
| `D303` | `waterDuration1` | 1号位加水时长，单位建议 `100ms` |
| `D304` | `waterDuration2` | 2号位加水时长 |
| `D305` | `waterDuration3` | 3号位加水时长 |
| `D306` | `waterDuration4` | 4号位加水时长 |
| `D307` | `activeTopLeftLogicalSlot` | 当前这轮左上工位代表的逻辑锅位 |
| `D308` | `chickenOilDuration` | 本轮左上工位鸡油时长，`0` 表示不执行 |
| `D309` | `bonePasteDuration` | 本轮左上工位骨膏时长，`0` 表示不执行 |
| `D310` | `phaseNo` | 该锅任务的阶段号 |
| `D311` | `commandVersion` | 协议版本 |
| `D312` | `reserved1` | 预留 |
| `D313` | `reserved2` | 预留 |
| `D314` | `reserved3` | 预留 |
| `D315` | `commandChecksum` | 可选校验 |

## 4. 字段解释

### `jobSlotMask`

表示这轮哪些逻辑锅位参与统一加水。

按位定义：

1. bit0 = 1号位 左上
2. bit1 = 2号位 右上
3. bit2 = 3号位 左下
4. bit3 = 4号位 右下

示例：

1. 单锅：`1111b = 15`
2. 拼锅左侧：`0101b = 5`
3. 拼锅右侧：`1010b = 10`
4. 四宫格全开：`1111b = 15`

### `waterDuration1 ~ waterDuration4`

表示 4 个逻辑锅位各自的加水时长。

规则：

1. 某锅位不参与本轮加水时，其时长写 `0`
2. 参与本轮加水时，其时长按该锅底配方填写
3. PLC 在同一开始时刻启动所有 `duration > 0` 的水路
4. 每一路按自己的时长单独关闭

### `activeTopLeftLogicalSlot`

表示当前左上工位在这一相位里代表的是哪一个逻辑锅位。

示例：

1. 单锅：通常写 `1`
2. 拼锅左侧在左上时：写 `1`
3. 四宫格初始左上：写 `1`
4. 四宫格人工转锅后，原 2 号位转到左上：写 `2`

这个字段的作用是：

让 PLC 和安卓都清楚，当前鸡油和骨膏是加给哪一个逻辑锅位的。

### `chickenOilDuration`

表示本轮左上工位鸡油时长。

规则：

1. `0` 表示本轮不加鸡油
2. `>0` 表示只对 `activeTopLeftLogicalSlot` 所代表的逻辑锅位加鸡油
3. 如果 `bonePasteDuration > 0`，鸡油与骨膏允许同时开启

### `bonePasteDuration`

表示本轮左上工位骨膏时长。

规则：

1. `0` 表示本轮不加骨膏
2. `>0` 表示只对 `activeTopLeftLogicalSlot` 所代表的逻辑锅位加骨膏
3. 如果 `chickenOilDuration > 0`，骨膏与鸡油允许同时开启

## 5. 一条非常重要的工艺约束

这条必须固化到协议解释里：

1. `waterDuration1 ~ waterDuration4` 可以同时非零
2. 但 `chickenOilDuration` 和 `bonePasteDuration` 在任一相位里，只能对应当前 `activeTopLeftLogicalSlot`
3. 不能在同一相位里同时表达“左上工位给 1 号位加鸡油”和“还没转过来的 2 号位也加鸡油”
4. 如果 2 号位也需要鸡油或骨膏，则它的加水和辅料都必须拆到下一相位
5. 这条优先转左上规则不区分单个辅料锅位还是多个辅料锅位，只要存在不在左上的辅料锅位，就应先转一个到左上
6. 多个待选辅料锅位的当前固定优先级为
   `左上 -> 左下 -> 右上 -> 右下`

## 6. 单锅示例

场景：

1. 1/2/3/4 同时加水 `2.3s`
2. 左上同时加鸡油 `1.0s`
3. 左上同时加骨膏 `2.8s`

示例值：

| 字段 | 值 |
| --- | --- |
| `commandId` | `1001` |
| `jobSlotMask` | `15` |
| `waterDuration1` | `23` |
| `waterDuration2` | `23` |
| `waterDuration3` | `23` |
| `waterDuration4` | `23` |
| `activeTopLeftLogicalSlot` | `1` |
| `chickenOilDuration` | `10` |
| `bonePasteDuration` | `28` |
| `phaseNo` | `1` |

## 7. 拼锅示例

场景：

1. 左侧三鲜：加水 `2.3s`，鸡油 `1.0s`，骨膏 `2.8s`
2. 右侧番茄：加水 `1.7s`

如果右侧不需要辅料，则可以同一相位表达：

| 字段 | 值 |
| --- | --- |
| `commandId` | `1101` |
| `jobSlotMask` | `15` |
| `waterDuration1` | `23` |
| `waterDuration2` | `17` |
| `waterDuration3` | `23` |
| `waterDuration4` | `17` |
| `activeTopLeftLogicalSlot` | `1` |
| `chickenOilDuration` | `10` |
| `bonePasteDuration` | `28` |
| `phaseNo` | `1` |

解释：

1. 左右两侧同时开始加水
2. 左侧按 `23` 停
3. 右侧按 `17` 停
4. 本轮辅料只属于左上的左侧逻辑锅位

## 8. 四宫格示例

场景：

1. 1号位 番茄：加水 `1.7s`
2. 2号位 三鲜：加水 `2.6s`，鸡油 `1.0s`，骨膏 `1.4s`
3. 3号位 清油：加水 `2.0s`
4. 4号位 清水：加水 `2.5s`

这里因为 2 号位需要辅料，但初始不在左上，所以应先按当前固定优先级把 2 号位转到左上，再开始第 1 相位。

### 第 1 相位

第 1 相位可以表达：

| 字段 | 值 |
| --- | --- |
| `commandId` | `1201` |
| `jobSlotMask` | `15` |
| `waterDuration1` | `17` |
| `waterDuration2` | `26` |
| `waterDuration3` | `20` |
| `waterDuration4` | `25` |
| `activeTopLeftLogicalSlot` | `2` |
| `chickenOilDuration` | `10` |
| `bonePasteDuration` | `14` |
| `phaseNo` | `1` |

解释：

1. 4 路水统一启动
2. 1/2/3/4 号位按各自时长独立停止
3. 2 号位因为已经被优先转到左上，所以它的加水、鸡油、骨膏可以在同一相位里执行
4. 鸡油和骨膏如果都需要，允许同时开启

## 9. PLC 执行解释

PLC 收到一条相位命令后，建议按下面理解执行：

1. 所有 `waterDurationN > 0` 的水路同时启动
2. 每一路按自己的 `waterDurationN` 单独关闭
3. 如果 `chickenOilDuration > 0`，则只对 `activeTopLeftLogicalSlot` 执行鸡油
4. 如果 `bonePasteDuration > 0`，则只对 `activeTopLeftLogicalSlot` 执行骨膏
5. 如果鸡油和骨膏都大于 `0`，则两者允许同时开启
6. 当本相位内所有启用的动作都完成后，PLC 才把这条 `commandId` 标记为完成
7. 只要整单里存在需要辅料但不在左上的锅位，调度层都应先完成一次“优先转左上”，再把被选中的那个锅位作为第一相位的 `activeTopLeftLogicalSlot`
