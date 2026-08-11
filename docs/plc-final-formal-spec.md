# PLC 正式版地址与协议总规

这份文档是当前项目面向 **PLC / 下位机正式实施** 的总规。

权威说明：

1. 这是当前唯一正式实施基线
2. 如果其他 `baseline / draft / sketch / checklist` 文档与本文件冲突，一律以本文件为准

用途：

1. 作为后续 PLC 梯形图 / 结构化文本 / 嵌入式控制实现的唯一正式基线
2. 解决前面草案里“相位与加热共用 D 区”“物理层与镜像层混用”的问题
3. 明确：
   - `X / Y` 真实物理地址
   - `M300 ~ M339` 安卓镜像区
   - `M500+` 内部状态机位
   - `D300+` 协议与内部数据分区

这份文档优先级高于以下草案：

1. [controller-memory-map-draft.md](/D:/Users/achillesniu/Documents/自研打锅机项目/docs/controller-memory-map-draft.md)
2. [plc-full-implementation-spec.md](/D:/Users/achillesniu/Documents/自研打锅机项目/docs/plc-full-implementation-spec.md)
3. [plc-command-handshake-draft.md](/D:/Users/achillesniu/Documents/自研打锅机项目/docs/plc-command-handshake-draft.md)

### 7.6 补充修正版

以下定义覆盖上面旧表述，正式实施以这一版为准：

1. `Y06`
   - 作为液位补水控制阀处理
   - 由 PLC 本地 `LevelController` 控制
2. 它不属于相位命令里的 4 路分锅出水位
   - 不由 `D303 ~ D306` 直接驱动
3. 上位机边界
   - 不给 `Y06` 单独下发使能命令
   - 只监控液位状态与 `Y06` 实际输出状态
4. 第一版液位控制策略
   - `D210 == 0` 或 `D210 == 1`：置位液位补水请求
   - `D210 == 7`：复位液位补水请求
   - `D210 == 3`：保持上一次补水请求状态
   - `M570` 或 `M573` 成立时：强制关闭 `Y06`
5. 因此当前相位输出网络里只处理：
   - `Y02 ~ Y05`
   - `Y11 / Y12 / Y13`
   - 不把 `Y06` 混进相位 4 路出水逻辑
6. PLC 需要单独实现 `LevelController`

---

## 1. 正式分层原则

正式版固定分 4 层：

1. `X / Y`
   - PLC 真实物理输入输出
   - 梯形图和实际执行以它为准
2. `M300 ~ M339`
   - 安卓主机读取/对接的镜像层
   - 不承载状态机主逻辑
3. `M500+`
   - 下位机内部状态机位
4. `D300+`
   - 协议区、锁存区、内部数据区

一句话：

`X/Y 负责真实现场，M300/M320 负责安卓对接，M500+/D500+ 负责控制器内部逻辑。`

---

## 2. 真实物理输入输出

## 2.1 物理输入 `X`

| 真实地址 | 功能 | 安卓镜像 |
| --- | --- | --- |
| `X00` | 急停按钮 | `M300` |
| `X01` | 加热/加水按钮 | `M301` |
| `X02` | 传锅完成按钮 | `M302` |
| `X03` | 应急加水按钮 | `M303` |
| `X04` | 应急骨膏按钮 | `M304` |
| `X05` | 应急鸡油按钮 | `M305` |
| `X06` | 出水阀 0 按钮 | `M306` |
| `X07` | 出水阀 1 按钮 | `M307` |
| `X10` | 出水阀 2 按钮 | `M308` |
| `X11` | 出水阀 3 按钮 | `M309` |
| `X12` | 单独加水按钮 | `M310` |
| `X13` | 主加热器状态输入 | `M311` |
| `X14` | 备加热器状态输入 | `M312` |
| `X15` | 液位开关 0 | `M313` |
| `X16` | 液位开关 1 | `M314` |
| `X17` | 液位开关 2 | `M315` |
| `X20` | DI20 预留 | `M316` |
| `X21` | DI21 预留 | `M317` |
| `X22` | DI22 预留 | `M318` |
| `X23` | DI23 预留 | `M319` |

## 2.2 物理输出 `Y`

| 真实地址 | 功能 | 安卓镜像 | 历史直接写线圈 |
| --- | --- | --- | --- |
| `Y00` | 主加热器 | `M320` | `M100` |
| `Y01` | 备加热器 | `M321` | `M101` |
| `Y02` | 出水气动阀 0 | `M322` | `M102` |
| `Y03` | 出水气动阀 1 | `M323` | `M103` |
| `Y04` | 出水气动阀 2 | `M324` | `M104` |
| `Y05` | 出水气动阀 3 | `M325` | `M105` |
| `Y06` | 进水气动阀 | `M326` | `M106` |
| `Y07` | 单独补水阀 | `M327` | `M107` |
| `Y10` | 出水喷雾阀 | `M328` | `M108` |
| `Y11` | 鸡油输出泵 | `M329` | `M109` |
| `Y12` | 骨膏输出泵 | `M330` | `M110` |
| `Y13` | 备用输出泵 | `M331` | `M111` |
| `Y14` | DO14 预留 | `M332` | `M112` |
| `Y15` | DO15 预留 | `M333` | `M113` |
| `Y16` | DO16 预留 | `M334` | `M114` |
| `Y17` | DO17 预留 | `M335` | `M115` |
| `Y20` | DO20 预留 | `M336` | `M116` |
| `Y21` | DO21 预留 | `M337` | `M117` |
| `Y22` | DO22 预留 | `M338` | `M118` |
| `Y23` | DO23 预留 | `M339` | `M119` |

## 2.3 传感器字

| 地址 | 含义 |
| --- | --- |
| `D200` | 温度 1 |
| `D201` | 温度 2 |
| `D210` | 液位 |

---

## 3. 安卓镜像区

这部分只负责给安卓读，不是主状态机区。

| 地址范围 | 含义 |
| --- | --- |
| `M300 ~ M319` | 物理输入镜像，由 `X00 ~ X23` 驱动 |
| `M320 ~ M339` | 物理输出镜像，由 `Y00 ~ Y23` 驱动 |
| `M400` | 心跳脉冲位，安卓 1/0 交替写入 |

---

## 4. 正式协议区分配

## 4.1 相位命令区 `D300 ~ D315`

| 地址 | 字段 | 说明 |
| --- | --- | --- |
| `D300` | `phaseCommandId` | 相位命令号 |
| `D301` | `phaseCommandType` | 固定 `1/20/30` 等相位相关命令 |
| `D302` | `jobSlotMask` | 本相位参与的逻辑锅位掩码 |
| `D303` | `waterDuration1` | 当前相位物理左上出水位时长 |
| `D304` | `waterDuration2` | 当前相位物理右上出水位时长 |
| `D305` | `waterDuration3` | 当前相位物理左下出水位时长 |
| `D306` | `waterDuration4` | 当前相位物理右下出水位时长 |
| `D307` | `activeTopLeftLogicalSlot` | 当前左上逻辑锅位 |
| `D308` | `chickenOilDuration` | 鸡油时长 |
| `D309` | `bonePasteDuration` | 骨膏时长 |
| `D310` | `phaseNo` | 相位号 |
| `D311` | `commandVersion` | 版本号 |
| `D312` | `reserved1` | 预留 |
| `D313` | `reserved2` | 预留 |
| `D314` | `reserved3` | 预留 |
| `D315` | `commandChecksum` | 可选校验 |

`D301 phaseCommandType` 当前固定按下面 3 类解释：

| `D301` | 含义 | PLC 侧处理 |
| --- | --- | --- |
| `1` | 执行相位 | 校验并锁存 `D302 ~ D310`，进入 Accepted/Running |
| `20` | 停止当前相位 | 不校验相位参数，立即停止当前相位输出并回写 Stopped |
| `30` | 复位 / 清相位状态 | 不启动输出，只在未运行时清相位状态并回到 Idle |

## 4.2 相位状态区 `D320 ~ D329`

| 地址 | 字段 | 说明 |
| --- | --- | --- |
| `D320` | `acceptedCommandId` | 已接收相位命令号 |
| `D321` | `executingCommandId` | 执行中相位命令号 |
| `D322` | `finishedCommandId` | 已完成相位命令号 |
| `D323` | `phaseActionState` | `Idle/Accepted/Running/Completed/Fault/Stopped` |
| `D324` | `phaseResultCode` | 结果码 |
| `D325` | `phaseFaultCode` | 故障码 |
| `D326` | `phaseRemainingTick` | 当前剩余 tick |
| `D327` | `phaseActualDurationTick` | 实际执行 tick |
| `D328` | `echoCommandType` | 回显相位命令类型 |
| `D329` | `echoJobSlotMask` | 回显锅位掩码 |

## 4.3 加热命令区 `D340 ~ D349`

| 地址 | 字段 | 说明 |
| --- | --- | --- |
| `D340` | `heaterCommandId` | 加热命令号 |
| `D341` | `heaterCommandType` | `10/11/12` |
| `D342` | `heaterSelect` | `1=主,2=备` |
| `D343` | `targetTemp` | 设定温度 |
| `D344` | `hysteresis` | 温差 |
| `D345` | `sensorSelect` | `0/1/2` |
| `D346` | `reserved1` | 预留 |
| `D347` | `reserved2` | 预留 |
| `D348` | `commandVersion` | 版本号 |
| `D349` | `commandChecksum` | 可选校验 |

## 4.4 加热状态区 `D350 ~ D359`

| 地址 | 字段 | 说明 |
| --- | --- | --- |
| `D350` | `acceptedCommandId` | 已接收加热命令号 |
| `D351` | `executingCommandId` | 执行中加热命令号 |
| `D352` | `finishedCommandId` | 已完成加热命令号 |
| `D353` | `heaterActionState` | `Idle/Accepted/Completed/Fault` |
| `D354` | `heaterResultCode` | 结果码 |
| `D355` | `heaterFaultCode` | 故障码 |
| `D356` | `echoHeaterSelect` | 回显主/备选择 |
| `D357` | `echoTargetTemp` | 回显设定温度 |
| `D358` | `echoHysteresis` | 回显温差 |
| `D359` | `actualHeaterOutputState` | `0=都关,1=主开,2=备开` |

## 4.5 应急补水 / HMI 参数区 `D360 ~ D369`

| 地址 | 字段 | 说明 |
| --- | --- | --- |
| `D360` | `emgWaterMode` | `0=Off,1=Jog,2=Timed` |
| `D361` | `emgWaterTimedDuration` | 时控总时长 |
| `D362` | `sparePumpMode` | `0=不用备用,1=备用替代鸡油,2=备用替代骨膏` |
| `D363` | `reserved1` | 预留 |
| `D364` | `reserved3` | 预留 |
| `D365` | `reserved4` | 预留 |
| `D366` | `reserved5` | 预留 |
| `D367` | `reserved6` | 预留 |
| `D368` | `paramVersion` | 版本号 |
| `D369` | `paramChecksum` | 可选校验 |

## 4.6 应急补水状态区 `D370 ~ D379`

| 地址 | 字段 | 说明 |
| --- | --- | --- |
| `D370` | `emgWaterState` | `Idle/Jogging/TimedRunning/Completed/Fault` |
| `D371` | `emgWaterRemainingTick` | 剩余 tick |
| `D372` | `emgWaterResultCode` | 结果码 |
| `D373` | `emgWaterFaultCode` | 故障码 |
| `D374` | `lastEmgWaterMode` | 最近一次模式 |
| `D375` | `actualEmgWaterOutputState` | `0=关,1=开` |
| `D376` | `sparePumpModeEcho` | 回显当前备用泵归属模式 |
| `D377` | `actualSparePumpOutputState` | `0=关,1=开` |
| `D378 ~ D379` | `reserved` | 预留 |

---

## 5. 正式内部 M 区分配

## 5.1 相位执行内部位 `M500 ~ M529`

| 地址 | 名称 | 说明 |
| --- | --- | --- |
| `M500` | `phase_idle` | 空闲 |
| `M501` | `phase_accepted` | 已接收 |
| `M502` | `phase_running` | 执行中 |
| `M503` | `phase_completed` | 完成 |
| `M504` | `phase_fault` | 故障 |
| `M505` | `phase_stopped` | 停止 |
| `M506` | `phase_cmd_new` | 新命令 |
| `M507` | `phase_param_valid` | 参数有效 |
| `M508` | `phase_all_done` | 全部完成 |
| `M509` | `phase_stop_request` | 停止请求 |
| `M510` | `water_1_enable` | 1 号位启用 |
| `M511` | `water_2_enable` | 2 号位启用 |
| `M512` | `water_3_enable` | 3 号位启用 |
| `M513` | `water_4_enable` | 4 号位启用 |
| `M514` | `water_1_done` | 1 号位完成 |
| `M515` | `water_2_done` | 2 号位完成 |
| `M516` | `water_3_done` | 3 号位完成 |
| `M517` | `water_4_done` | 4 号位完成 |
| `M518` | `chicken_enable` | 鸡油启用 |
| `M519` | `chicken_done` | 鸡油完成 |
| `M520` | `bone_enable` | 骨膏启用 |
| `M521` | `bone_done` | 骨膏完成 |
| `M522` | `phase_reset_request` | 复位 / 清相位状态请求 |

## 5.2 加热内部位 `M530 ~ M549`

| 地址 | 名称 | 说明 |
| --- | --- | --- |
| `M530` | `heater_disabled` | 未使能 |
| `M531` | `heater_ready` | 就绪 |
| `M532` | `heater_heating` | 加热中 |
| `M533` | `heater_blocked` | 被阻止 |
| `M534` | `heater_fault` | 故障 |
| `M535` | `heater_request_enable` | 请求允许加热 |
| `M536` | `heater_output_enable` | 当前周期允许输出 |
| `M537` | `heater_select_main` | 当前选主 |
| `M538` | `heater_select_backup` | 当前选备 |
| `M539` | `heater_switch_pending` | 切换中先全关 |

## 5.3 应急补水内部位 `M550 ~ M569`

| 地址 | 名称 | 说明 |
| --- | --- | --- |
| `M550` | `emg_water_off` | 停用 |
| `M551` | `emg_water_jog_mode` | 点动模式 |
| `M552` | `emg_water_timed_mode` | 时控模式 |
| `M553` | `emg_water_jogging` | 点动运行中 |
| `M554` | `emg_water_timed_running` | 时控运行中 |
| `M555` | `emg_water_completed` | 时控完成 |
| `M556` | `emg_water_fault` | 故障 |
| `M557` | `emg_water_btn_last` | 按钮上一扫描值 |
| `M558` | `emg_water_btn_rising` | 按钮上升沿 |

## 5.4 安全与系统内部位 `M570 ~ M589`

| 地址 | 名称 | 说明 |
| --- | --- | --- |
| `M570` | `emergency_stop_active` | 急停有效 |
| `M571` | `global_fatal_block` | 全局禁止输出 |
| `M572` | `heater_block_fault` | 禁止加热类故障 |
| `M573` | `water_block_fault` | 禁止补水类故障 |
| `M574` | `heartbeat_timeout` | 心跳超时 |
| `M575` | `liquid_low_low` | 液位超低 |
| `M576` | `transfer_waiting` | 等待转锅 |
| `M577` | `level_fill_request` | 液位补水请求保持位 |
| `M578` | `level_fill_output_enable` | 液位补水输出允许 |
| `M579` | `level_sensor_fault` | 液位信号异常预留 |
| `M586` | `spare_pump_enable` | 备用泵投入使用 |
| `M587` | `spare_as_chicken` | 备用泵替代鸡油泵 |
| `M588` | `spare_as_bone` | 备用泵替代骨膏泵 |
| `M589` | `spare_pump_output_request` | 备用泵当前输出请求 |
| `M580` | `phase_slotmask_ok` | 相位锅位掩码合法 |
| `M581` | `phase_water_ok` | 相位 4 路水时长合法 |
| `M582` | `phase_top_left_ok` | 左上逻辑锅位合法 |
| `M583` | `phase_material_ok` | 鸡油/骨膏时长合法 |
| `M584` | `phase_no_ok` | 相位号合法 |
| `M585` | `phase_has_action` | 相位至少存在一个动作 |

---

## 6. 正式内部 D 区分配

## 6.1 相位锁存与运行 `D500 ~ D519`

| 地址 | 名称 | 说明 |
| --- | --- | --- |
| `D500` | `latched_phase_command_id` | 锁存命令号 |
| `D501` | `latched_job_slot_mask` | 锁存锅位掩码 |
| `D502` | `latched_water_duration_1` | 1 号位水时长 |
| `D503` | `latched_water_duration_2` | 2 号位水时长 |
| `D504` | `latched_water_duration_3` | 3 号位水时长 |
| `D505` | `latched_water_duration_4` | 4 号位水时长 |
| `D506` | `latched_active_top_left_slot` | 左上逻辑锅位 |
| `D507` | `latched_chicken_duration` | 鸡油时长 |
| `D508` | `latched_bone_duration` | 骨膏时长 |
| `D509` | `latched_phase_no` | 相位号 |
| `D510` | `phase_tick_1` | 水路 1 剩余 tick |
| `D511` | `phase_tick_2` | 水路 2 剩余 tick |
| `D512` | `phase_tick_3` | 水路 3 剩余 tick |
| `D513` | `phase_tick_4` | 水路 4 剩余 tick |
| `D514` | `chicken_tick` | 鸡油剩余 tick |
| `D515` | `bone_tick` | 骨膏剩余 tick |
| `D516` | `phase_elapsed_tick` | 相位已执行 tick |

## 6.2 加热内部数据 `D530 ~ D539`

| 地址 | 名称 | 说明 |
| --- | --- | --- |
| `D530` | `heater_selected_channel` | `1=主,2=备` |
| `D531` | `heater_target_temp` | 设定温度 |
| `D532` | `heater_hysteresis` | 温差 |
| `D533` | `heater_lower_limit` | 下限 |
| `D534` | `heater_upper_limit` | 上限 |
| `D535` | `heater_current_temp` | 当前温度快照 |

## 6.3 应急补水内部数据 `D550 ~ D559`

| 地址 | 名称 | 说明 |
| --- | --- | --- |
| `D550` | `emg_water_mode` | `0/1/2` |
| `D551` | `emg_water_timed_duration` | 总时长 |
| `D552` | `emg_water_timed_tick` | 剩余 tick |

## 6.4 故障与诊断 `D570 ~ D579`

| 地址 | 名称 | 说明 |
| --- | --- | --- |
| `D570` | `fault_group_code` | 故障大类 |
| `D571` | `fault_detail_code` | 故障细类 |
| `D572` | `last_failed_command_id` | 最近失败命令号 |
| `D573` | `last_failed_command_type` | 最近失败命令类型 |

---

## 7. 为什么正式版必须这样拆

## 7.1 D 区必须拆开的原因

如果相位和加热共用 `D302/D303/D304`：

1. PLC 梯形图每次都要先判断 `commandType`
2. 同一个字在不同命令下含义完全不同
3. 读程序、调程序、查故障都会很痛苦

正式分开后：

1. `PhaseExecutor` 只看 `D300 ~ D329`
2. `HeaterController` 只看 `D340 ~ D359`
3. `EmergencyWaterExecutor` 只看 `D360 ~ D379`

## 7.2 M 区正式版为什么也要固定段

如果内部位不按执行域分段：

1. 后面很容易出现“这个位到底是相位的、加热的还是应急补水的”
2. 梯形图跨网络排查会很乱

正式分段后：

1. `M500 段` 一眼就是相位
2. `M530 段` 一眼就是加热
3. `M550 段` 一眼就是应急补水
4. `M570 段` 一眼就是安全

## 7.3 备用泵切换原则

正式版按“屏幕/HMI 参数切换归属”实现备用泵：

1. `Y13`
   - 定义为备用输出泵
2. `D362 = sparePumpMode`
   - `0 = 不用备用`
   - `1 = 备用泵替代鸡油泵`
   - `2 = 备用泵替代骨膏泵`
3. 备用泵采用“替代模式”，不是“并行模式”
   - 当 `D362 = 1` 时：
     - 鸡油请求由 `Y13` 输出
     - 原鸡油泵 `Y11` 不输出
   - 当 `D362 = 2` 时：
     - 骨膏请求由 `Y13` 输出
     - 原骨膏泵 `Y12` 不输出
4. 不允许备用泵同时承担鸡油和骨膏
5. 这部分不改变订单相位和加热协议，只是改变泵输出映射

## 7.4 逻辑锅位、转锅与相位命令的边界

这一条在正式实施里要明确：

1. 客人点单形成的逻辑锅位顺序不能被 PLC 改写
   - `1/2/3/4` 仍然是订单逻辑锅位
2. 允许发生变化的只有：
   - 当前这一相位里，哪个逻辑锅位被人工转到了左上工位
3. `D307 = activeTopLeftLogicalSlot`
   - 只回答“当前左上工位服务的是哪个逻辑锅位”
   - 不代表订单锅位顺序被改掉
4. `D303 ~ D306`
   - 由上位机根据当前这一相位的执行计划组织后下发
   - PLC 只把它当作“当前这一轮 4 路水各自要跑多久”
5. 因此，转锅策略、哪个锅位先去左上、哪一相位先做谁
   - 主要由上位机决定
   - PLC 相位执行主逻辑基本不需要因为这个原则改变

一句话：

`上位机负责保留原始逻辑锅位并生成当前相位，PLC 只负责执行当前相位，不负责改客人的点锅顺序。`

## 7.5 相位参数校验与职责边界

当前正式版对相位参数的理解固定为：

1. `D302`
   - 表示“这一相位参与执行的逻辑锅位集合”
2. `D303 ~ D306`
   - 表示“这一相位当前 4 个物理出水位各自的执行时长”
3. `D307`
   - 表示“当前物理左上工位服务的是哪个逻辑锅位”

因此，PLC 侧参数校验只做：

1. 范围合法性校验
2. 非负值校验
3. 至少存在一个动作的校验

而不在 PLC 内部强行校验：

1. `D302` 与 `D303 ~ D306` 是否一一对应
2. 逻辑锅位是否经过了怎样的转锅映射

这些一致性关系由上位机负责保证。

一句话：

`PLC 只校验“这条相位命令能不能执行”，不校验“上位机为什么这样安排这一相位”。`

## 7.6 总进水阀 `Y06` 的边界

正式版当前按下面方式理解 `Y06`：

1. `Y06`
   - 作为独立总进水阀处理
2. 它不属于相位命令里的 4 路分锅出水位
   - 不由 `D303 ~ D306` 直接驱动
3. 它的控制边界更接近：
   - 加热控制这种独立执行线
   - 或单独补水这种独立执行线
4. 因此当前相位输出网络里只处理：
   - `Y02 ~ Y05`
   - `Y11 / Y12 / Y13`
   - 不把 `Y06` 混进相位 4 路出水逻辑

---

## 8. 第一版最少要落地的范围

必须落地：

1. `X00 ~ X17`
2. `Y00 ~ Y12`
3. `M300 ~ M339`
4. `M400`
5. `M500 ~ M522`
6. `M530 ~ M539`
7. `M550 ~ M558`
8. `M570 ~ M579`
9. `D300 ~ D379`
10. `D500 ~ D516`
11. `D530 ~ D535`
12. `D550 ~ D552`

---

## 9. 最终结论

正式版现在建议你这样执行：

1. **真实执行只认 `X / Y`**
2. **安卓对接只认 `M300/M320/D300/D320/D340/D350/D360/D370`**
3. **内部逻辑只认 `M500+ / D500+`**

## 10. 实施补充说明

### 10.1 物理层、镜像层、逻辑层的推荐走法

正式实施时，推荐按下面三层理解同一个信号：

1. `X / Y`
   - 真实硬件输入输出
   - 梯形图最终控制以它为准
2. `M300 ~ M339`
   - 仅用于给安卓主机读状态
   - 不建议承载主状态机判断
3. `M500+`
   - 控制器内部逻辑位

例如急停信号建议理解为：

1. `X00`
   - 真实急停输入
2. `M300`
   - 急停输入镜像给安卓
3. `M570`
   - 急停参与安全联锁的内部逻辑位

即：

`X00 -> M300 -> M570`

同理，主加热器输出建议理解为：

`Y00 -> M320`

其中：

1. `Y00` 是真实输出
2. `M320` 只是输出状态镜像
4. **相位、加热、应急补水三条控制线彻底分区，不再共用命令字**

如果你后面愿意，我下一步最值得做的是：

1. 基于这份正式总规，继续给你画一版 **正式版梯形图程序段表**
2. 或者直接继续把 **相位执行 NW** 按这个正式地址版展开一遍
