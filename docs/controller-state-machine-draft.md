# 控制器状态机实现草案

这份草案不把实现限定为 `PLC` 或 `嵌入式 MCU`。

它更像一份“控制器内核设计”：

1. 如果你用 PLC
   - 可以翻译成 `步序 + 定时器 + 保持寄存器 + 线圈输出`
2. 如果你用嵌入式
   - 可以翻译成 `主循环 + 任务状态机 + 软件定时器 + IO 驱动`

目标只有一个：

`让订单相位、加热、应急补水三条执行线能稳定并行，同时对上位机反馈一致状态。`

---

## 1. 总体结构

建议控制器内部拆成 6 个模块：

1. `CommandMailbox`
   - 接收上位机命令区
2. `PhaseExecutor`
   - 执行订单相位
3. `HeaterController`
   - 执行加热回差控制
4. `EmergencyWaterExecutor`
   - 执行应急补水
5. `SafetyManager`
   - 做急停、液位、故障等统一放行
6. `StatusPublisher`
   - 回写状态区与实际输出状态

可以把它理解成：

`上位机写参数 -> 控制器锁存参数 -> 各执行器按自己的状态机运行 -> 状态发布器回写结果`

---

## 2. 执行域划分

### 2.1 `PhaseExecutor`

负责：

1. `commandType = 1`
2. 4 路水统一启动
3. 4 路水独立停止
4. 当前左上锅位的鸡油
5. 当前左上锅位的骨膏

特点：

1. 事务型
2. 一次命令对应一个相位
3. 有明确开始和结束

### 2.2 `HeaterController`

负责：

1. 设定温度
2. 温差
3. 主/备选择
4. 加热总使能
5. 本地回差开关
6. 主备互锁

特点：

1. 常驻型
2. 不依赖相位是否运行
3. 每个扫描周期都要运行

### 2.3 `EmergencyWaterExecutor`

负责：

1. 应急补水独立阀
2. 点动模式
3. 时控模式

特点：

1. 独立执行域
2. 可与 `PhaseExecutor` 并行
3. 统一受安全条件限制

---

## 3. 共享输入、输出、内部变量

## 3.1 输入量

### 来自上位机命令区

1. `cmd_command_id`
2. `cmd_command_type`
3. `cmd_job_slot_mask`
4. `cmd_water_duration_1`
5. `cmd_water_duration_2`
6. `cmd_water_duration_3`
7. `cmd_water_duration_4`
8. `cmd_active_top_left_slot`
9. `cmd_chicken_duration`
10. `cmd_bone_duration`
11. `cmd_phase_no`

### 来自上位机加热参数

1. `heater_select`
   - `1 = 主`
   - `2 = 备`
2. `heater_target_temp`
3. `heater_hysteresis`
4. `heater_enable_request`

### 来自现场按钮 / 触摸屏

1. `btn_emergency_stop`
2. `btn_add_water`
3. `btn_transfer_done`
4. `btn_emg_water`
5. `hmi_emg_water_mode`
   - `0 = Off`
   - `1 = Jog`
   - `2 = Timed`
6. `hmi_emg_water_duration`
7. `hmi_heater_select`
8. `hmi_heater_enable`

### 来自传感器

1. `temp_current`
2. `liquid_level`
3. `fault_flags`

---

## 3.2 输出量

### 执行器输出

1. `out_water_1`
2. `out_water_2`
3. `out_water_3`
4. `out_water_4`
5. `out_chicken`
6. `out_bone`
7. `out_heater_main`
8. `out_heater_backup`
9. `out_emg_water`

### 回写状态区

1. `sts_accepted_command_id`
2. `sts_executing_command_id`
3. `sts_finished_command_id`
4. `sts_action_state`
5. `sts_result_code`
6. `sts_fault_code`
7. `sts_remaining_tick`
8. `sts_actual_duration_tick`

### 扩展状态

1. `sts_phase_state`
2. `sts_heater_state`
3. `sts_emg_water_state`
4. `sts_actual_top_left_slot`
5. `sts_waiting_transfer_confirm`

---

## 3.3 内部变量

### 相位锁存变量

1. `latched_phase_command_id`
2. `latched_job_slot_mask`
3. `latched_water_duration_1`
4. `latched_water_duration_2`
5. `latched_water_duration_3`
6. `latched_water_duration_4`
7. `latched_active_top_left_slot`
8. `latched_chicken_duration`
9. `latched_bone_duration`
10. `latched_phase_no`

### 相位运行变量

1. `phase_state`
2. `phase_tick_1`
3. `phase_tick_2`
4. `phase_tick_3`
5. `phase_tick_4`
6. `chicken_tick`
7. `bone_tick`

### 加热变量

1. `heater_state`
2. `heater_request_enable`
3. `heater_output_enable`
4. `heater_selected_channel`
5. `heater_last_output_state`

### 应急补水变量

1. `emg_water_state`
2. `emg_water_mode`
3. `emg_water_timed_duration`
4. `emg_water_timed_tick`
5. `emg_water_button_last`
6. `emg_water_button_rising_edge`

---

## 4. 统一安全管理

建议先做一个统一放行层：

### 4.1 全局禁止条件

1. `emergency_stop_active`
2. `controller_fatal_fault`

这些条件下：

1. 相位输出全部关闭
2. 加热输出全部关闭
3. 应急补水输出关闭

### 4.2 加热禁止条件

1. `liquid_level == 超低`
2. `emergency_stop_active`
3. `heater_block_fault`
4. `heartbeat_timeout`

### 4.3 补水禁止条件

建议后续细分，但第一版至少留：

1. `emergency_stop_active`
2. `water_block_fault`

---

## 5. `PhaseExecutor` 状态机

## 5.1 状态定义

1. `Idle`
2. `Accepted`
3. `Running`
4. `Completed`
5. `Fault`
6. `Stopped`

## 5.2 状态流转

### `Idle -> Accepted`

条件：

1. 发现 `cmd_command_type == 1`
2. `cmd_command_id` 是新值
3. 参数校验通过

动作：

1. 锁存命令参数
2. `sts_accepted_command_id = cmd_command_id`
3. `sts_action_state = 1`
4. `phase_state = Accepted`

### `Accepted -> Running`

条件：

1. 全局安全允许

动作：

1. 装载 4 路水计时器
2. 装载鸡油计时器
3. 装载骨膏计时器
4. `sts_executing_command_id = latched_phase_command_id`
5. `sts_action_state = 2`
6. `phase_state = Running`

### `Running`

动作：

1. 所有 `water_duration > 0` 的路同时置输出 `ON`
2. 所有水路各自按自己的 tick 倒计时
3. `latched_chicken_duration > 0` 时开鸡油
4. `latched_bone_duration > 0` 时开骨膏
5. 鸡油与骨膏允许同时开启
6. 任一路 tick 到 0，则关对应输出
7. 所有启用动作完成后进入 `Completed`

### `Running -> Completed`

条件：

1. 4 路启用的水路都完成
2. 鸡油已完成或未启用
3. 骨膏已完成或未启用

动作：

1. 所有相位输出确认关闭
2. `sts_finished_command_id = latched_phase_command_id`
3. `sts_result_code = 0`
4. `sts_action_state = 3`
5. `phase_state = Completed`

### `Running -> Fault`

条件：

1. 执行中命中致命故障
2. 执行中参数异常
3. 执行中安全条件强制中断

动作：

1. 关闭相位相关输出
2. `sts_result_code = 对应故障结果`
3. `sts_fault_code = 细分故障`
4. `sts_action_state = 4`
5. `phase_state = Fault`

### `Running -> Stopped`

条件：

1. 收到 `commandType = 20`

动作：

1. 关闭相位相关输出
2. `sts_result_code = 6`
3. `sts_action_state = 6`
4. `phase_state = Stopped`

### `Completed/Fault/Stopped -> Idle`

条件：

1. 新命令覆盖
或
2. 显式清状态

---

## 6. `HeaterController` 状态机

## 6.1 状态定义

1. `Disabled`
2. `Ready`
3. `Heating`
4. `Blocked`
5. `Fault`

## 6.2 输入参数

1. `heater_target_temp`
2. `heater_hysteresis`
3. `heater_selected_channel`
4. `heater_request_enable`

## 6.3 回差规则

定义：

1. `lower_limit = target_temp - hysteresis`
2. `upper_limit = target_temp + hysteresis`

规则：

1. 当前温度 `<= lower_limit`
   - 请求加热
2. 当前温度 `>= upper_limit`
   - 关闭加热
3. 中间区间
   - 保持上一次输出状态

## 6.4 输出规则

### `Disabled`

条件：

1. `heater_request_enable == false`

动作：

1. 主加热关
2. 备加热关

### `Ready`

条件：

1. `heater_request_enable == true`
2. 安全允许
3. 但当前未达到开启条件

动作：

1. 输出全关

### `Ready -> Heating`

条件：

1. 当前温度 `<= lower_limit`
2. 安全允许

动作：

1. 如果选主
   - `out_heater_main = ON`
   - `out_heater_backup = OFF`
2. 如果选备
   - `out_heater_main = OFF`
   - `out_heater_backup = ON`

### `Heating -> Ready`

条件：

1. 当前温度 `>= upper_limit`

动作：

1. 主关
2. 备关

### 任意状态 -> `Blocked`

条件：

1. `liquid_level == 超低`
2. `emergency_stop_active`
3. `heartbeat_timeout`
4. `heater_block_fault`

动作：

1. 主关
2. 备关

### 主备互锁铁律

无论在哪个状态，都必须保证：

1. 主开则备关
2. 备开则主关
3. 切换时先全关，再开目标路
4. 绝不允许同时输出

---

## 7. `EmergencyWaterExecutor` 状态机

## 7.1 状态定义

1. `Idle`
2. `Jogging`
3. `TimedRunning`
4. `Completed`
5. `Fault`

## 7.2 模式定义

1. `0 = Off`
2. `1 = Jog`
3. `2 = Timed`

## 7.3 点动模式

条件：

1. `emg_water_mode == Jog`

规则：

1. 按钮按下且安全允许
   - 阀开
   - `state = Jogging`
2. 按钮松开
   - 阀关
   - `state = Idle`

## 7.4 时控模式

条件：

1. `emg_water_mode == Timed`

规则：

1. 只看按钮上升沿
2. 上升沿到来且当前未运行且安全允许
   - 锁存 `emg_water_timed_duration`
   - 阀开
   - `state = TimedRunning`
3. tick 归零
   - 阀关
   - `state = Completed`
4. 下一次空闲后回 `Idle`

## 7.5 与相位并行

前提：

1. 硬件阀完全独立
2. 安全条件允许

则：

1. `EmergencyWaterExecutor` 可与 `PhaseExecutor` 并行
2. 不需要中断订单相位

---

## 8. 命令邮箱处理建议

## 8.1 `CommandMailbox`

建议做一层统一处理：

1. 发现新 `commandId`
2. 根据 `commandType` 分发
3. 拒绝不支持的命令类型
4. 拒绝重复执行已完成命令

## 8.2 幂等建议

如果收到重复命令：

1. `commandId == accepted`
   - 不重复启动
2. `commandId == executing`
   - 不重复启动
3. `commandId == finished`
   - 不重复执行

---

## 9. 状态发布建议

建议区分两类状态：

### 9.1 命令处理状态

1. `acceptedCommandId`
2. `executingCommandId`
3. `finishedCommandId`
4. `plcActionState`
5. `resultCode`

### 9.2 实际输出状态

1. 4 路水阀状态
2. 鸡油泵状态
3. 骨膏泵状态
4. 主加热状态
5. 备加热状态
6. 应急补水阀状态

上位机显示时应以“实际输出状态”为准，不只看命令状态。

---

## 10. 主循环建议

如果是 PLC 扫描周期或 MCU 主循环，建议每一轮固定按下面顺序：

1. 采集输入
   - 按钮
   - 传感器
   - 上位机命令区
2. 更新安全状态
3. 处理命令邮箱
4. 运行 `HeaterController`
5. 运行 `EmergencyWaterExecutor`
6. 运行 `PhaseExecutor`
7. 统一做输出互锁和安全收口
8. 刷新物理输出
9. 发布状态区

这样做的好处：

1. 安全优先
2. 加热是常驻控制
3. 应急补水独立
4. 订单相位是事务执行

---

## 11. 第一版最小实现建议

如果要快速落第一版，我建议只先做这些：

1. `PhaseExecutor`
   - 单条相位命令
   - 4 路水统一启动独立停止
   - 鸡油骨膏同时可开
2. `HeaterController`
   - 参数锁存
   - 回差控制
   - 主备互锁
   - 超低液位/急停/心跳超时保护
3. `EmergencyWaterExecutor`
   - 点动
   - 时控
4. `StatusPublisher`
   - 基本状态回写

先把最小闭环做出来，再补复杂故障分类和更细的恢复逻辑。
