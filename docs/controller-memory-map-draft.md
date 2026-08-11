# 控制器内存映射草案

这份文档按 **梯形图 / PLC 实现习惯** 来整理。

说明：

1. 这份文档是配套参考件
2. 如果和 [plc-final-formal-spec.md](/D:/Users/achillesniu/Documents/自研打锅机项目/docs/plc-final-formal-spec.md) 冲突，一律以正式版为准

如果后面不是 PLC，而是嵌入式实现，也可以直接把这里的：

- `M` 位
- `D` 字
- `T` 定时器
- `C` 计数器

理解成：

- 布尔变量
- 16 位整型变量
- 软件定时器
- 计数器

这份表的目标是：

`把外部协议区、物理 IO、内部状态、锁存参数、定时器资源彻底分开。`

---

## 1. 分区原则

建议先分 5 层：

1. **物理输入区**
   - 现场按钮、传感器
2. **物理输出区**
   - 阀、泵、加热器
3. **上位机协议区**
   - 安卓写命令
   - 控制器回状态
4. **内部保持区**
   - 状态机位、锁存位、故障位
5. **内部数据区**
   - 锁存参数、剩余时长、温度参数、诊断字

---

## 2. 已有物理地址区

这部分沿用你们现在已经有的地址，不建议随便改。

建议分三层使用：

1. `X / Y`
   - 真实 PLC 物理输入输出地址
2. `M300 ~ M339`
   - 安卓主机可轮询的镜像区
3. `M500+ / D500+`
   - 控制器内部状态机和锁存区

## 2.1 物理输入 `X`

| 地址 | 含义 | 镜像 |
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
| `X13` | 主加热管状态输入 | `M311` |
| `X14` | 备加热管状态输入 | `M312` |
| `X15` | 液位开关 0 | `M313` |
| `X16` | 液位开关 1 | `M314` |
| `X17` | 液位开关 2 | `M315` |
| `X20` | DI20 预留 | `M316` |
| `X21` | DI21 预留 | `M317` |
| `X22` | DI22 预留 | `M318` |
| `X23` | DI23 预留 | `M319` |

## 2.2 物理输出 `Y`

| 地址 | 含义 | 镜像 | 历史直接写线圈 |
| --- | --- | --- | --- |
| `Y00` | 主加热器控制 | `M320` | `M100` |
| `Y01` | 备加热器控制 | `M321` | `M101` |
| `Y02` | 出水阀 1 | `M322` | `M102` |
| `Y03` | 出水阀 2 | `M323` | `M103` |
| `Y04` | 出水阀 3 | `M324` | `M104` |
| `Y05` | 出水阀 4 | `M325` | `M105` |
| `Y06` | 进水阀 | `M326` | `M106` |
| `Y07` | 单独出水阀 | `M327` | `M107` |
| `Y10` | 喷雾阀 | `M328` | `M108` |
| `Y11` | 鸡油泵 | `M329` | `M109` |
| `Y12` | 骨膏泵 | `M330` | `M110` |
| `Y13` | 备用输出泵 | `M331` | `M111` |
| `Y14` | 预留 | `M332` | `M112` |
| `Y15` | 预留 | `M333` | `M113` |
| `Y16` | 预留 | `M334` | `M114` |
| `Y17` | 预留 | `M335` | `M115` |
| `Y20` | 预留 | `M336` | `M116` |
| `Y21` | 预留 | `M337` | `M117` |
| `Y22` | 预留 | `M338` | `M118` |
| `Y23` | 预留 | `M339` | `M119` |

## 2.3 安卓镜像区

| 地址 | 含义 |
| --- | --- |
| `M300 ~ M319` | 物理输入镜像，由 `X00 ~ X23` 得到 |
| `M320 ~ M339` | 物理输出镜像，由 `Y00 ~ Y23` 得到 |
| `M400` | 心跳脉冲线圈，由安卓主机交替写入 |

## 2.4 历史直接写线圈区

| 地址 | 含义 |
| --- | --- |
| `M100 ~ M119` | 历史“直接写输出”线圈区，建议保留但不作为新状态机主逻辑输入 |

## 2.5 传感器字

| 地址 | 含义 |
| --- | --- |
| `D200` | 温度 1 |
| `D201` | 温度 2 |
| `D210` | 液位 |
| `D212` | 鸡油时长寄存器 |
| `D214` | 骨膏时长寄存器 |

---

## 3. 上位机协议区

这部分建议保留给安卓和控制器通讯使用。

正式版建议不要再让“相位命令”和“加热参数命令”共用同一段 `D` 区。

建议固定分成 6 段：

1. `D300 ~ D315`
   - 相位命令区
2. `D320 ~ D329`
   - 相位状态区
3. `D340 ~ D349`
   - 加热命令 / 参数区
4. `D350 ~ D359`
   - 加热状态区
5. `D360 ~ D369`
   - 应急补水 / HMI 参数区
6. `D370 ~ D379`
   - 应急补水状态区

## 3.1 相位命令区

| 地址 | 字段 | 说明 |
| --- | --- | --- |
| `D300` | `commandId` | 当前命令号 |
| `D301` | `commandType` | 命令类型 |
| `D302` | `jobSlotMask` | 相位参与的逻辑锅位掩码 |
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

## 3.2 相位状态区

| 地址 | 字段 | 说明 |
| --- | --- | --- |
| `D320` | `acceptedCommandId` | 已接收命令号 |
| `D321` | `executingCommandId` | 正在执行命令号 |
| `D322` | `finishedCommandId` | 已完成命令号 |
| `D323` | `plcActionState` | `Idle/Accepted/Running/Completed/Fault/Stopped` |
| `D324` | `resultCode` | 结果码 |
| `D325` | `faultCode` | 故障细码 |
| `D326` | `remainingTick` | 当前相位剩余时长 |
| `D327` | `actualDurationTick` | 当前相位实际执行时长 |
| `D328` | `echoCommandType` | 回显命令类型 |
| `D329` | `echoJobSlotMask` | 回显参与锅位 |

## 3.3 加热命令 / 参数区

| 地址 | 字段 | 说明 |
| --- | --- | --- |
| `D340` | `commandId` | 当前加热命令号 |
| `D341` | `commandType` | `10/11/12` |
| `D342` | `heaterSelect` | `1=主`，`2=备` |
| `D343` | `targetTemp` | 设定温度 |
| `D344` | `hysteresis` | 温差 |
| `D345` | `sensorSelect` | `1=D200`, `2=D201`, `0=默认` |
| `D346` | `reserved1` | 预留 |
| `D347` | `reserved2` | 预留 |
| `D348` | `commandVersion` | 版本号 |
| `D349` | `commandChecksum` | 可选校验 |

## 3.4 加热状态区

| 地址 | 字段 | 说明 |
| --- | --- | --- |
| `D350` | `acceptedCommandId` | 已接收加热命令号 |
| `D351` | `executingCommandId` | 正在处理加热命令号 |
| `D352` | `finishedCommandId` | 已完成加热命令号 |
| `D353` | `heaterActionState` | `Idle/Accepted/Completed/Fault` |
| `D354` | `heaterResultCode` | 结果码 |
| `D355` | `heaterFaultCode` | 故障细码 |
| `D356` | `echoHeaterSelect` | 回显主/备选择 |
| `D357` | `echoTargetTemp` | 回显设定温度 |
| `D358` | `echoHysteresis` | 回显温差 |
| `D359` | `actualHeaterOutputState` | `0=都关,1=主开,2=备开` |

## 3.5 应急补水 / HMI 参数区

| 地址 | 字段 | 说明 |
| --- | --- | --- |
| `D360` | `emgWaterMode` | `0=Off,1=Jog,2=Timed` |
| `D361` | `emgWaterTimedDuration` | 时控补水总时长 |
| `D362` | `sparePumpMode` | `0=不用备用,1=备用替代鸡油,2=备用替代骨膏` |
| `D363` | `reserved1` | 预留 |
| `D364` | `reserved3` | 预留 |
| `D365` | `reserved4` | 预留 |
| `D366` | `reserved5` | 预留 |
| `D367` | `reserved6` | 预留 |
| `D368` | `paramVersion` | 版本号 |
| `D369` | `paramChecksum` | 可选校验 |

## 3.6 应急补水状态区

| 地址 | 字段 | 说明 |
| --- | --- | --- |
| `D370` | `emgWaterState` | `Idle/Jogging/TimedRunning/Completed/Fault` |
| `D371` | `emgWaterRemainingTick` | 时控剩余时长 |
| `D372` | `emgWaterResultCode` | 结果码 |
| `D373` | `emgWaterFaultCode` | 故障细码 |
| `D374` | `lastEmgWaterMode` | 最近一次模式 |
| `D375` | `actualEmgWaterOutputState` | `0=关,1=开` |
| `D376` | `sparePumpModeEcho` | 回显当前备用泵归属模式 |
| `D377` | `actualSparePumpOutputState` | `0=关,1=开` |
| `D378` | `reserved3` | 预留 |
| `D379` | `reserved4` | 预留 |

---

## 4. 建议新增内部 M 位区

这部分是 **内部保持位**，不直接暴露给上位机。

建议从 `M500` 往后开始，尽量避开现有硬件地址区。

正式版建议固定分段：

1. `M500 ~ M529`
   - 相位执行内部位
2. `M530 ~ M549`
   - 加热内部位
3. `M550 ~ M569`
   - 应急补水内部位
4. `M570 ~ M589`
   - 安全 / 系统内部位

## 4.1 相位执行器 M 位

| 地址 | 变量 | 含义 |
| --- | --- | --- |
| `M500` | `phase_idle` | 相位空闲 |
| `M501` | `phase_accepted` | 相位已接收 |
| `M502` | `phase_running` | 相位运行中 |
| `M503` | `phase_completed` | 相位完成 |
| `M504` | `phase_fault` | 相位故障 |
| `M505` | `phase_stopped` | 相位停止 |
| `M506` | `phase_cmd_new` | 发现新相位命令 |
| `M507` | `phase_param_valid` | 相位参数校验通过 |
| `M508` | `phase_all_done` | 本相位全部动作完成 |
| `M509` | `phase_stop_request` | 收到停止相位请求 |

## 4.2 水路运行位

| 地址 | 变量 | 含义 |
| --- | --- | --- |
| `M510` | `water_1_enable` | 1 号位当前相位启用 |
| `M511` | `water_2_enable` | 2 号位当前相位启用 |
| `M512` | `water_3_enable` | 3 号位当前相位启用 |
| `M513` | `water_4_enable` | 4 号位当前相位启用 |
| `M514` | `water_1_done` | 1 号位当前相位完成 |
| `M515` | `water_2_done` | 2 号位当前相位完成 |
| `M516` | `water_3_done` | 3 号位当前相位完成 |
| `M517` | `water_4_done` | 4 号位当前相位完成 |
| `M518` | `chicken_enable` | 鸡油启用 |
| `M519` | `chicken_done` | 鸡油完成 |
| `M520` | `bone_enable` | 骨膏启用 |
| `M521` | `bone_done` | 骨膏完成 |

## 4.3 加热控制器 M 位

| 地址 | 变量 | 含义 |
| --- | --- | --- |
| `M530` | `heater_disabled` | 加热未使能 |
| `M531` | `heater_ready` | 加热就绪 |
| `M532` | `heater_heating` | 加热中 |
| `M533` | `heater_blocked` | 加热被阻止 |
| `M534` | `heater_fault` | 加热故障 |
| `M535` | `heater_request_enable` | 安卓请求允许加热 |
| `M536` | `heater_output_enable` | 当前周期允许实际输出 |
| `M537` | `heater_select_main` | 当前选主 |
| `M538` | `heater_select_backup` | 当前选备 |
| `M539` | `heater_switch_pending` | 主备切换中，先全关 |

## 4.4 应急补水 M 位

| 地址 | 变量 | 含义 |
| --- | --- | --- |
| `M550` | `emg_water_off` | 应急补水停用 |
| `M551` | `emg_water_jog_mode` | 点动模式 |
| `M552` | `emg_water_timed_mode` | 时控模式 |
| `M553` | `emg_water_jogging` | 点动运行中 |
| `M554` | `emg_water_timed_running` | 时控运行中 |
| `M555` | `emg_water_completed` | 时控完成 |
| `M556` | `emg_water_fault` | 应急补水故障 |
| `M557` | `emg_water_btn_last` | 上一扫描按钮状态 |
| `M558` | `emg_water_btn_rising` | 本扫描检测到上升沿 |

## 4.5 安全与系统 M 位

| 地址 | 变量 | 含义 |
| --- | --- | --- |
| `M570` | `emergency_stop_active` | 急停有效 |
| `M571` | `global_fatal_block` | 全局禁止输出 |
| `M572` | `heater_block_fault` | 禁止加热类故障 |
| `M573` | `water_block_fault` | 禁止补水类故障 |
| `M574` | `heartbeat_timeout` | 心跳超时 |
| `M575` | `liquid_low_low` | 液位超低 |
| `M576` | `transfer_waiting` | 等待转锅确认 |
| `M586` | `spare_pump_enable` | 备用泵投入使用 |
| `M587` | `spare_as_chicken` | 备用泵替代鸡油泵 |
| `M588` | `spare_as_bone` | 备用泵替代骨膏泵 |
| `M589` | `spare_pump_output_request` | 备用泵输出请求 |

---

## 5. 建议新增内部 D 字区

这部分是 **内部保持字**，用于锁存参数、剩余时长、诊断信息。

建议从 `D500` 往后开始。

## 5.1 相位锁存参数

| 地址 | 变量 | 含义 |
| --- | --- | --- |
| `D500` | `latched_phase_command_id` | 已锁存相位命令号 |
| `D501` | `latched_job_slot_mask` | 已锁存参与锅位掩码 |
| `D502` | `latched_water_duration_1` | 1 号位水时长 |
| `D503` | `latched_water_duration_2` | 2 号位水时长 |
| `D504` | `latched_water_duration_3` | 3 号位水时长 |
| `D505` | `latched_water_duration_4` | 4 号位水时长 |
| `D506` | `latched_active_top_left_slot` | 左上逻辑锅位 |
| `D507` | `latched_chicken_duration` | 鸡油时长 |
| `D508` | `latched_bone_duration` | 骨膏时长 |
| `D509` | `latched_phase_no` | 相位号 |

## 5.2 相位运行时长 / 剩余时长

| 地址 | 变量 | 含义 |
| --- | --- | --- |
| `D510` | `phase_tick_1` | 1 号位剩余 tick |
| `D511` | `phase_tick_2` | 2 号位剩余 tick |
| `D512` | `phase_tick_3` | 3 号位剩余 tick |
| `D513` | `phase_tick_4` | 4 号位剩余 tick |
| `D514` | `chicken_tick` | 鸡油剩余 tick |
| `D515` | `bone_tick` | 骨膏剩余 tick |
| `D516` | `phase_elapsed_tick` | 相位已运行 tick |

## 5.3 加热参数与边界

| 地址 | 变量 | 含义 |
| --- | --- | --- |
| `D530` | `heater_selected_channel` | `1=主`，`2=备` |
| `D531` | `heater_target_temp` | 设定温度 |
| `D532` | `heater_hysteresis` | 温差 |
| `D533` | `heater_lower_limit` | 下限 |
| `D534` | `heater_upper_limit` | 上限 |
| `D535` | `heater_current_temp` | 当前温度快照 |

## 5.4 应急补水数据

| 地址 | 变量 | 含义 |
| --- | --- | --- |
| `D550` | `emg_water_mode` | `0=Off`，`1=Jog`，`2=Timed` |
| `D551` | `emg_water_timed_duration` | 时控补水总时长 |
| `D552` | `emg_water_timed_tick` | 时控剩余 tick |

## 5.5 故障与诊断

| 地址 | 变量 | 含义 |
| --- | --- | --- |
| `D570` | `fault_group_code` | 故障大类 |
| `D571` | `fault_detail_code` | 故障细类 |
| `D572` | `last_failed_command_id` | 最近失败命令 |
| `D573` | `last_failed_command_type` | 最近失败命令类型 |

---

## 6. T 定时器 / C 计数器建议

这里有两种实现路线。

## 6.1 方案 A：统一 100ms 节拍 + D 字递减

推荐度：**最高**

做法：

1. 用一个全局 `100ms` 节拍位
2. 每来一次节拍，就把：
   - `D510~D515`
   - `D542`
   这些剩余 tick 做减 1

优点：

1. 和上位机协议的 `100ms` 单位完全一致
2. 不容易把很多 `T` 定时器用乱
3. 梯形图里更容易看

## 6.2 方案 B：每路独立 T 定时器

推荐度：中等

做法：

1. `T500`：1 号位水
2. `T501`：2 号位水
3. `T502`：3 号位水
4. `T503`：4 号位水
5. `T504`：鸡油
6. `T505`：骨膏
7. `T506`：应急补水时控

优点：

1. 逻辑直观

缺点：

1. 不如 D 字 tick 统一
2. 相位诊断不如 D 字好看

**建议第一版用方案 A。**

---

## 7. 对应梯形图的建议拆块

如果你后面用梯形图实现，我建议直接按下面分网络或分程序段：

1. `NW1`
   - 输入采集
2. `NW2`
   - 心跳超时判断
3. `NW3`
   - 液位、急停、故障统一放行
4. `NW4`
   - 命令接收与新命令识别
5. `NW5`
   - 相位命令锁存
6. `NW6`
   - 相位运行与 D510~D515 递减
7. `NW7`
   - 加热参数锁存
8. `NW8`
   - 加热回差判断
9. `NW9`
   - 主备互锁
10. `NW10`
    - 应急补水点动
11. `NW11`
    - 应急补水时控
12. `NW12`
    - 状态回写

---

## 8. 第一版最小可用映射建议

如果你现在就准备开做，我建议第一版最少先落这些：

### 必须落地

1. `D300 ~ D315`
2. `D320 ~ D329`
3. `D340 ~ D349`
4. `D350 ~ D359`
5. `D360 ~ D369`
6. `D370 ~ D379`
7. `M500 ~ M521`
8. `M530 ~ M539`
9. `M550 ~ M558`
10. `M570 ~ M576`
11. `D500 ~ D515`
12. `D530 ~ D535`
13. `D550 ~ D552`

### 可以后补

1. `D550 ~ D553`
2. 更细的故障分组
3. 更多诊断字

---

## 9. 一句话收口

这张映射表的核心思想是：

1. **物理 IO 不动**
2. **协议区独立**
3. **内部状态位独立**
4. **内部数据字独立**
5. **定时优先按 100ms tick 统一实现**

这样后面不管你用梯形图还是嵌入式，都不会把协议、物理 IO、内部状态搅在一起。
