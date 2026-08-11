# 控制器伪代码框架草案

这份文档把前面已经收敛的控制逻辑翻成“接近代码、但不绑定语言”的伪代码。

适用对象：

1. PLC 结构化文本 / 步序逻辑
2. MCU / RTOS 主循环
3. C / C++ / C# / Kotlin 的控制层

目标：

`先把控制结构写清楚，再落到具体编程语言。`

---

## 1. 全局结构

建议控制器每次循环固定按这个顺序执行：

```text
loop:
    readInputs()
    updateDerivedSignals()
    updateSafetyState()
    handleCommandMailbox()
    runHeaterController()
    runEmergencyWaterExecutor()
    runPhaseExecutor()
    applyOutputInterlocks()
    writeOutputs()
    publishStatus()
end loop
```

这里的意思是：

1. 先采输入
2. 再更新安全状态
3. 再处理上位机命令
4. 然后分别运行：
   - 加热
   - 应急补水
   - 相位执行
5. 最后统一输出和回写状态

---

## 2. 共享变量示例

```text
// 上位机命令区
cmd_command_id
cmd_command_type
cmd_job_slot_mask
cmd_water_duration_1
cmd_water_duration_2
cmd_water_duration_3
cmd_water_duration_4
cmd_active_top_left_slot
cmd_chicken_duration
cmd_bone_duration
cmd_phase_no

// 加热参数
heater_selected_channel
heater_target_temp
heater_hysteresis
heater_request_enable

// 现场输入
btn_emergency_stop
btn_emg_water
hmi_emg_water_mode
hmi_emg_water_duration

// 传感器
temp_current
liquid_level
fault_flags
heartbeat_timeout

// 物理输出
out_water_1
out_water_2
out_water_3
out_water_4
out_chicken
out_bone
out_heater_main
out_heater_backup
out_emg_water

// 通用状态反馈
sts_accepted_command_id
sts_executing_command_id
sts_finished_command_id
sts_action_state
sts_result_code
sts_fault_code
```

---

## 3. 主循环前置函数

### 3.1 读取输入

```text
function readInputs():
    btn_emergency_stop = readEmergencyStop()
    btn_emg_water = readEmergencyWaterButton()

    hmi_emg_water_mode = readEmgWaterModeFromHmi()
    hmi_emg_water_duration = readEmgWaterDurationFromHmi()

    cmd_command_id = readRegister(D300)
    cmd_command_type = readRegister(D301)
    cmd_job_slot_mask = readRegister(D302)
    cmd_water_duration_1 = readRegister(D303)
    cmd_water_duration_2 = readRegister(D304)
    cmd_water_duration_3 = readRegister(D305)
    cmd_water_duration_4 = readRegister(D306)
    cmd_active_top_left_slot = readRegister(D307)
    cmd_chicken_duration = readRegister(D308)
    cmd_bone_duration = readRegister(D309)
    cmd_phase_no = readRegister(D310)

    temp_current = readTemperature()
    liquid_level = readLiquidLevel()
    fault_flags = readFaultFlags()
    heartbeat_timeout = checkHeartbeatTimeout()
end function
```

### 3.2 更新派生信号

```text
function updateDerivedSignals():
    emg_water_button_rising_edge =
        (btn_emg_water == true) and (btn_emg_water_last == false)

    btn_emg_water_last = btn_emg_water
end function
```

### 3.3 更新安全状态

```text
function updateSafetyState():
    emergency_stop_active = btn_emergency_stop

    global_fatal_block =
        emergency_stop_active

    heater_blocked =
        emergency_stop_active
        or (liquid_level == LOW_LOW)
        or heartbeat_timeout
        or hasHeaterBlockFault(fault_flags)

    water_blocked =
        emergency_stop_active
        or hasWaterBlockFault(fault_flags)
end function
```

---

## 4. 命令邮箱处理伪代码

```text
function handleCommandMailbox():
    if cmd_command_id == 0:
        return

    if cmd_command_id == sts_finished_command_id:
        return

    if cmd_command_id == sts_executing_command_id:
        return

    if cmd_command_id == sts_accepted_command_id:
        return

    switch cmd_command_type:
        case 1:
            tryAcceptPhaseCommand()
            break

        case 10:
            tryApplyHeaterParams()
            break

        case 11:
            tryEnableHeater()
            break

        case 12:
            tryDisableHeater()
            break

        case 20:
            tryStopCurrentPhase()
            break

        case 30:
            tryResetFault()
            break

        default:
            sts_accepted_command_id = cmd_command_id
            sts_finished_command_id = cmd_command_id
            sts_action_state = REJECTED
            sts_result_code = RESULT_INVALID_PARAM
            break
end function
```

---

## 5. 相位执行器伪代码

## 5.1 相位执行器内部变量

```text
phase_state

latched_phase_command_id
latched_job_slot_mask
latched_water_duration_1
latched_water_duration_2
latched_water_duration_3
latched_water_duration_4
latched_active_top_left_slot
latched_chicken_duration
latched_bone_duration
latched_phase_no

phase_tick_1
phase_tick_2
phase_tick_3
phase_tick_4
chicken_tick
bone_tick
```

## 5.2 接收相位命令

```text
function tryAcceptPhaseCommand():
    if phase_state == RUNNING:
        sts_accepted_command_id = cmd_command_id
        sts_finished_command_id = cmd_command_id
        sts_action_state = REJECTED
        sts_result_code = RESULT_BUSY
        return

    if not validatePhaseCommand():
        sts_accepted_command_id = cmd_command_id
        sts_finished_command_id = cmd_command_id
        sts_action_state = REJECTED
        sts_result_code = RESULT_INVALID_PARAM
        return

    latched_phase_command_id = cmd_command_id
    latched_job_slot_mask = cmd_job_slot_mask
    latched_water_duration_1 = cmd_water_duration_1
    latched_water_duration_2 = cmd_water_duration_2
    latched_water_duration_3 = cmd_water_duration_3
    latched_water_duration_4 = cmd_water_duration_4
    latched_active_top_left_slot = cmd_active_top_left_slot
    latched_chicken_duration = cmd_chicken_duration
    latched_bone_duration = cmd_bone_duration
    latched_phase_no = cmd_phase_no

    sts_accepted_command_id = cmd_command_id
    sts_action_state = ACCEPTED
    sts_result_code = 0

    phase_state = ACCEPTED
end function
```

## 5.3 运行相位执行器

```text
function runPhaseExecutor():
    switch phase_state:
        case IDLE:
            return

        case ACCEPTED:
            if global_fatal_block:
                phase_state = FAULT
                sts_action_state = FAULT
                sts_result_code = RESULT_SAFETY_BLOCK
                return

            phase_tick_1 = latched_water_duration_1
            phase_tick_2 = latched_water_duration_2
            phase_tick_3 = latched_water_duration_3
            phase_tick_4 = latched_water_duration_4
            chicken_tick = latched_chicken_duration
            bone_tick = latched_bone_duration

            sts_executing_command_id = latched_phase_command_id
            sts_action_state = RUNNING
            phase_state = RUNNING
            return

        case RUNNING:
            runPhaseOutputs()
            updatePhaseTimers()

            if isPhaseAllDone():
                out_water_1 = OFF
                out_water_2 = OFF
                out_water_3 = OFF
                out_water_4 = OFF
                out_chicken = OFF
                out_bone = OFF

                sts_finished_command_id = latched_phase_command_id
                sts_action_state = COMPLETED
                sts_result_code = 0
                phase_state = COMPLETED
            return

        case COMPLETED:
            // 等待新命令覆盖或上位机读取后回到空闲
            return

        case FAULT:
            out_water_1 = OFF
            out_water_2 = OFF
            out_water_3 = OFF
            out_water_4 = OFF
            out_chicken = OFF
            out_bone = OFF
            return

        case STOPPED:
            out_water_1 = OFF
            out_water_2 = OFF
            out_water_3 = OFF
            out_water_4 = OFF
            out_chicken = OFF
            out_bone = OFF
            return
end function
```

## 5.4 相位输出控制

```text
function runPhaseOutputs():
    if phase_tick_1 > 0:
        out_water_1 = ON
    else:
        out_water_1 = OFF

    if phase_tick_2 > 0:
        out_water_2 = ON
    else:
        out_water_2 = OFF

    if phase_tick_3 > 0:
        out_water_3 = ON
    else:
        out_water_3 = OFF

    if phase_tick_4 > 0:
        out_water_4 = ON
    else:
        out_water_4 = OFF

    if chicken_tick > 0:
        out_chicken = ON
    else:
        out_chicken = OFF

    if bone_tick > 0:
        out_bone = ON
    else:
        out_bone = OFF
end function
```

## 5.5 相位计时更新

```text
function updatePhaseTimers():
    if phase_tick_1 > 0:
        phase_tick_1 -= 1

    if phase_tick_2 > 0:
        phase_tick_2 -= 1

    if phase_tick_3 > 0:
        phase_tick_3 -= 1

    if phase_tick_4 > 0:
        phase_tick_4 -= 1

    if chicken_tick > 0:
        chicken_tick -= 1

    if bone_tick > 0:
        bone_tick -= 1
end function
```

## 5.6 相位完成判断

```text
function isPhaseAllDone():
    return
        phase_tick_1 == 0 and
        phase_tick_2 == 0 and
        phase_tick_3 == 0 and
        phase_tick_4 == 0 and
        chicken_tick == 0 and
        bone_tick == 0
end function
```

## 5.7 停止相位命令

```text
function tryStopCurrentPhase():
    if phase_state != RUNNING:
        sts_accepted_command_id = cmd_command_id
        sts_finished_command_id = cmd_command_id
        sts_action_state = COMPLETED
        sts_result_code = 0
        return

    out_water_1 = OFF
    out_water_2 = OFF
    out_water_3 = OFF
    out_water_4 = OFF
    out_chicken = OFF
    out_bone = OFF

    sts_accepted_command_id = cmd_command_id
    sts_finished_command_id = cmd_command_id
    sts_action_state = STOPPED
    sts_result_code = RESULT_STOPPED
    phase_state = STOPPED
end function
```

---

## 6. 加热控制器伪代码

## 6.1 加热内部变量

```text
heater_state
heater_selected_channel
heater_target_temp
heater_hysteresis
heater_request_enable
heater_output_enable
```

## 6.2 设置加热参数

```text
function tryApplyHeaterParams():
    heater_selected_channel = cmd_job_slot_mask   // 这里复用 D302
    heater_target_temp = cmd_water_duration_1     // 这里复用 D303
    heater_hysteresis = cmd_water_duration_2      // 这里复用 D304

    sts_accepted_command_id = cmd_command_id
    sts_finished_command_id = cmd_command_id
    sts_action_state = COMPLETED
    sts_result_code = 0
end function
```

## 6.3 开启加热请求

```text
function tryEnableHeater():
    heater_request_enable = true

    sts_accepted_command_id = cmd_command_id
    sts_finished_command_id = cmd_command_id
    sts_action_state = COMPLETED
    sts_result_code = 0
end function
```

## 6.4 关闭加热请求

```text
function tryDisableHeater():
    heater_request_enable = false

    out_heater_main = OFF
    out_heater_backup = OFF

    sts_accepted_command_id = cmd_command_id
    sts_finished_command_id = cmd_command_id
    sts_action_state = COMPLETED
    sts_result_code = 0
end function
```

## 6.5 运行加热控制器

```text
function runHeaterController():
    lower_limit = heater_target_temp - heater_hysteresis
    upper_limit = heater_target_temp + heater_hysteresis

    if not heater_request_enable:
        out_heater_main = OFF
        out_heater_backup = OFF
        heater_state = DISABLED
        return

    if heater_blocked:
        out_heater_main = OFF
        out_heater_backup = OFF
        heater_state = BLOCKED
        return

    if temp_current <= lower_limit:
        heater_output_enable = true
    else if temp_current >= upper_limit:
        heater_output_enable = false
    else:
        // 带内保持原状态
        heater_output_enable = heater_output_enable

    if not heater_output_enable:
        out_heater_main = OFF
        out_heater_backup = OFF
        heater_state = READY
        return

    if heater_selected_channel == 1:
        out_heater_main = ON
        out_heater_backup = OFF
        heater_state = HEATING
        return

    if heater_selected_channel == 2:
        out_heater_main = OFF
        out_heater_backup = ON
        heater_state = HEATING
        return

    // 无有效选择则全关
    out_heater_main = OFF
    out_heater_backup = OFF
    heater_state = READY
end function
```

---

## 7. 应急补水执行器伪代码

## 7.1 应急补水内部变量

```text
emg_water_mode
emg_water_timed_duration
emg_water_timed_tick
emg_water_state
emg_water_button_rising_edge
```

## 7.2 运行应急补水执行器

```text
function runEmergencyWaterExecutor():
    emg_water_mode = hmi_emg_water_mode
    emg_water_timed_duration = hmi_emg_water_duration

    if water_blocked:
        out_emg_water = OFF
        emg_water_state = FAULT
        return

    if emg_water_mode == 0:
        out_emg_water = OFF
        emg_water_state = IDLE
        return

    if emg_water_mode == 1:
        runEmergencyWaterJog()
        return

    if emg_water_mode == 2:
        runEmergencyWaterTimed()
        return
end function
```

## 7.3 点动模式

```text
function runEmergencyWaterJog():
    if btn_emg_water:
        out_emg_water = ON
        emg_water_state = JOGGING
    else:
        out_emg_water = OFF
        emg_water_state = IDLE
end function
```

## 7.4 时控模式

```text
function runEmergencyWaterTimed():
    if emg_water_state != TIMED_RUNNING:
        if emg_water_button_rising_edge:
            emg_water_timed_tick = emg_water_timed_duration
            out_emg_water = ON
            emg_water_state = TIMED_RUNNING
        else:
            out_emg_water = OFF
            if emg_water_state == COMPLETED:
                emg_water_state = IDLE
        return

    if emg_water_timed_tick > 0:
        emg_water_timed_tick -= 1
        out_emg_water = ON
        return

    out_emg_water = OFF
    emg_water_state = COMPLETED
end function
```

---

## 8. 输出互锁与最终输出

### 8.1 加热互锁

```text
function applyOutputInterlocks():
    if out_heater_main == ON:
        out_heater_backup = OFF

    if out_heater_backup == ON:
        out_heater_main = OFF

    if emergency_stop_active:
        out_water_1 = OFF
        out_water_2 = OFF
        out_water_3 = OFF
        out_water_4 = OFF
        out_chicken = OFF
        out_bone = OFF
        out_heater_main = OFF
        out_heater_backup = OFF
        out_emg_water = OFF
end function
```

### 8.2 写输出

```text
function writeOutputs():
    writeWaterValve1(out_water_1)
    writeWaterValve2(out_water_2)
    writeWaterValve3(out_water_3)
    writeWaterValve4(out_water_4)
    writeChickenPump(out_chicken)
    writeBonePump(out_bone)
    writeMainHeater(out_heater_main)
    writeBackupHeater(out_heater_backup)
    writeEmergencyWaterValve(out_emg_water)
end function
```

---

## 9. 状态发布伪代码

```text
function publishStatus():
    writeRegister(D320, sts_accepted_command_id)
    writeRegister(D321, sts_executing_command_id)
    writeRegister(D322, sts_finished_command_id)
    writeRegister(D323, sts_action_state)
    writeRegister(D324, sts_result_code)
    writeRegister(D325, sts_fault_code)

    writeStatusOutputMainHeater(out_heater_main)
    writeStatusOutputBackupHeater(out_heater_backup)
    writeStatusOutputWater1(out_water_1)
    writeStatusOutputWater2(out_water_2)
    writeStatusOutputWater3(out_water_3)
    writeStatusOutputWater4(out_water_4)
    writeStatusOutputChicken(out_chicken)
    writeStatusOutputBone(out_bone)
    writeStatusOutputEmergencyWater(out_emg_water)
end function
```

---

## 10. 三个典型流程的伪代码举例

## 10.1 相位加水 + 鸡油 + 骨膏

```text
// 上位机已写入：
cmd_command_type = 1
cmd_water_duration_1 = 17
cmd_water_duration_2 = 26
cmd_water_duration_3 = 20
cmd_water_duration_4 = 0
cmd_active_top_left_slot = 2
cmd_chicken_duration = 10
cmd_bone_duration = 14

handleCommandMailbox()
runPhaseExecutor()

// 运行时：
out_water_1 = ON
out_water_2 = ON
out_water_3 = ON
out_chicken = ON
out_bone = ON

// 各自到时关闭
```

## 10.2 加热

```text
heater_selected_channel = 1
heater_target_temp = 55
heater_hysteresis = 3
heater_request_enable = true
temp_current = 50

runHeaterController()

// 因为 50 <= 52
out_heater_main = ON
out_heater_backup = OFF
```

## 10.3 应急补水时控

```text
hmi_emg_water_mode = 2
hmi_emg_water_duration = 30
emg_water_button_rising_edge = true

runEmergencyWaterExecutor()

// 启动后
out_emg_water = ON
emg_water_state = TIMED_RUNNING

// 30 tick 后
out_emg_water = OFF
emg_water_state = COMPLETED
```

---

## 11. 最后建议

真正落地时，不要追求一版把所有异常分支写满。

第一版建议先实现：

1. 相位执行主流程
2. 加热主流程
3. 应急补水点动 / 时控
4. 通用状态回写

先把最小闭环跑通，再补边界条件。
