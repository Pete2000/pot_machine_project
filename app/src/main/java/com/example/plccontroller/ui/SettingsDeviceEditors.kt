package com.example.plccontroller.ui

import androidx.compose.ui.graphics.Color
import com.example.plccontroller.data.AppPersistentConfig
import com.example.plccontroller.data.SettingsStore
import com.example.plccontroller.domain.PlcCommunicationConfig
import com.example.plccontroller.domain.PlcPollingSnapshot

internal enum class DeviceSettingKey {
    SerialPortPath,
    BaudRate,
    DataBits,
    Parity,
    StopBits,
    SlaveId,
    ReadTimeoutMs,
    WriteTimeoutMs,
    RetryCount,
    HeartbeatPeriodMs,
    ActivePollingIntervalMs,
    IdlePollingIntervalMs,
    OrderPollingIntervalMs,
    FormulaSyncIntervalSeconds,
    FrameGapMs,
    RegisterOnlyMode,
    RegisterOnlyBlockStart,
    RegisterOnlyBlockCount,
    ConnectionTestRegister,
    DeviceHeaterTargetTemp,
    DeviceHeaterHysteresisTemp,
    DeviceStandaloneWaterMode,
    DeviceStandaloneWaterTimedSeconds,
    DeviceHeaterComposite,
    DeviceStandaloneWaterComposite,
    DeviceWaterCalibrationComposite,
    DevicePumpCorrectionComposite,
    DeviceHeaterConfigComposite,
    DeviceWaterOutlet1CalibrationPercent,
    DeviceWaterOutlet2CalibrationPercent,
    DeviceWaterOutlet3CalibrationPercent,
    DeviceWaterOutlet4CalibrationPercent,
    DeviceChickenOilCalibrationPercent,
    DeviceBonePasteCalibrationPercent,
    DeviceHeaterSelect,
    DeviceHeaterSensorSelect,
    DevicePeristalticPumpGroup,
    DeviceChickenPumpSource,
    DeviceBonePumpSource,
}

internal data class DeviceSettingEditor(
    val key: DeviceSettingKey,
    val label: String,
    val value: String,
    val detail: String,
    val accent: Color,
    val options: List<String>? = null,
    val enabled: Boolean = true,
)

internal fun communicationParameterEditors(config: PlcCommunicationConfig?): List<DeviceSettingEditor> =
    listOf(
        DeviceSettingEditor(DeviceSettingKey.SerialPortPath, "串口路径", config?.serialPortPath ?: "", "设备路径 /dev/tty...", Cyan),
        DeviceSettingEditor(
            DeviceSettingKey.BaudRate,
            "波特率",
            config?.baudRate?.toString().orEmpty(),
            "传输速率 (bps)",
            Blue,
            listOf("9600", "19200", "38400", "57600", "115200"),
        ),
        DeviceSettingEditor(DeviceSettingKey.SlaveId, "PLC 站号", config?.slaveId?.toString().orEmpty(), "Modbus Slave ID", Cyan),
        DeviceSettingEditor(DeviceSettingKey.DataBits, "数据位", config?.dataBits?.toString().orEmpty(), "常用 7 或 8", Blue, listOf("7", "8")),
        DeviceSettingEditor(
            DeviceSettingKey.Parity,
            "校验位",
            config?.parityName.orEmpty(),
            "NONE / EVEN / ODD",
            Yellow,
            listOf("NONE", "EVEN", "ODD"),
        ),
        DeviceSettingEditor(DeviceSettingKey.StopBits, "停止位", config?.stopBits?.toString().orEmpty(), "常用 1 或 2", Yellow, listOf("1", "2")),
    )

internal fun pollingParameterEditors(
    config: PlcCommunicationConfig?,
    formulaSyncIntervalSeconds: Long,
): List<DeviceSettingEditor> =
    listOf(
        DeviceSettingEditor(
            DeviceSettingKey.ActivePollingIntervalMs,
            "运行轮询",
            config?.activePollingIntervalMs?.toString().orEmpty(),
            "运行频率 (ms)",
            Blue,
        ),
        DeviceSettingEditor(
            DeviceSettingKey.IdlePollingIntervalMs,
            "空闲轮询",
            config?.idlePollingIntervalMs?.toString().orEmpty(),
            "空闲频率 (ms)",
            Blue,
        ),
        DeviceSettingEditor(
            DeviceSettingKey.OrderPollingIntervalMs,
            "拉单周期",
            config?.orderPollingIntervalMs?.toString().orEmpty(),
            "订单刷新 (ms)",
            Blue,
        ),
        DeviceSettingEditor(
            DeviceSettingKey.FormulaSyncIntervalSeconds,
            "配方同步周期",
            formulaSyncIntervalSeconds.toString(),
            "配方同步 (s)",
            Green,
        ),
        DeviceSettingEditor(DeviceSettingKey.HeartbeatPeriodMs, "心跳周期", config?.heartbeatPeriodMs?.toString().orEmpty(), "翻转间隔 (ms)", Cyan),
        DeviceSettingEditor(DeviceSettingKey.FrameGapMs, "帧间隔", config?.frameGapMs?.toString().orEmpty(), "每帧间隔 (ms)", Yellow),
        DeviceSettingEditor(
            DeviceSettingKey.RegisterOnlyMode,
            "只读03模式",
            if (config?.registerOnlyMode == true) "开启" else "关闭",
            "关闭01线圈与05心跳",
            Green,
            listOf("开启", "关闭"),
        ),
        DeviceSettingEditor(
            DeviceSettingKey.RegisterOnlyBlockStart,
            "03测试块",
            if ((config?.registerOnlyBlockStart ?: 0) > 0) "D${config?.registerOnlyBlockStart}" else "全部",
            "0=全部/200/320/350/370",
            Yellow,
            listOf("全部", "200", "320", "350", "370"),
        ),
        DeviceSettingEditor(
            DeviceSettingKey.RegisterOnlyBlockCount,
            "03测试数量",
            (config?.registerOnlyBlockCount ?: 0).takeIf { it > 0 }?.toString() ?: "标准",
            "0=标准，D320建议试9或11",
            Yellow,
        ),
    )

internal fun timeoutParameterEditors(config: PlcCommunicationConfig?): List<DeviceSettingEditor> =
    listOf(
        DeviceSettingEditor(DeviceSettingKey.ReadTimeoutMs, "读超时", config?.readTimeoutMs?.toString().orEmpty(), "等待回包 (ms)", Green),
        DeviceSettingEditor(DeviceSettingKey.WriteTimeoutMs, "写超时", config?.writeTimeoutMs?.toString().orEmpty(), "命令确认 (ms)", Green),
        DeviceSettingEditor(DeviceSettingKey.RetryCount, "重试次数", config?.retryCount?.toString().orEmpty(), "异常自动重试", Yellow),
        DeviceSettingEditor(
            DeviceSettingKey.ConnectionTestRegister,
            "测试寄存器",
            config?.connectionTestRegister?.toString().orEmpty(),
            "连接自检 Dxxx",
            Cyan,
        ),
    )

internal fun runningParameterEditors(config: AppPersistentConfig?): List<DeviceSettingEditor> =
    listOf(
        DeviceSettingEditor(
            key = DeviceSettingKey.DeviceHeaterComposite,
            label = "水箱设置温度",
            value = "${config?.deviceHeaterTargetTemp ?: 0}|${config?.deviceHeaterHysteresisTemp ?: 0}",
            detail = "目标温度 / 回差温度",
            accent = Cyan,
        ),
        DeviceSettingEditor(
            key = DeviceSettingKey.DeviceStandaloneWaterComposite,
            label = "应急加水设置",
            value = "${config?.deviceStandaloneWaterMode ?: 0}|${config?.deviceStandaloneWaterTimedTicks ?: 0}",
            detail = "加水模式 / 时控时间",
            accent = Green,
        ),
    )

internal fun calibrationParameterEditors(config: AppPersistentConfig?): List<DeviceSettingEditor> =
    listOf(
        DeviceSettingEditor(
            key = DeviceSettingKey.DeviceWaterCalibrationComposite,
            label = "出水校准",
            value = "${config?.deviceWaterOutlet1CalibrationPercent ?: 100}|${config?.deviceWaterOutlet2CalibrationPercent ?: 100}|${config?.deviceWaterOutlet3CalibrationPercent ?: 100}|${config?.deviceWaterOutlet4CalibrationPercent ?: 100}",
            detail = "1口 | 2口 | 3口 | 4口 (校准系数)",
            accent = Blue,
        ),
        DeviceSettingEditor(
            key = DeviceSettingKey.DevicePumpCorrectionComposite,
            label = "蠕动泵修正",
            value = "${config?.deviceChickenOilCalibrationPercent ?: 100}|${config?.deviceBonePasteCalibrationPercent ?: 100}",
            detail = "鸡油修正 / 骨膏修正",
            accent = Yellow,
        ),
    )

internal fun actuatorConfigEditors(
    config: AppPersistentConfig?,
    heaterConfigBusy: Boolean,
    pumpConfigBusy: Boolean,
): List<DeviceSettingEditor> {
    val heaterLockDetail =
        if (heaterConfigBusy) {
            "当前 PLC 正在执行加水/加料动作，暂不切换加热执行器"
        } else {
            null
        }
    val pumpLockDetail =
        if (pumpConfigBusy) {
            "当前 PLC 正在执行相位或蠕动泵动作，暂不切换备用泵"
        } else {
            null
        }
    return listOf(
        DeviceSettingEditor(
            key = DeviceSettingKey.DeviceHeaterConfigComposite,
            label = "水箱加热配置",
            value = "${config?.deviceHeaterSelect ?: 1}|${config?.deviceHeaterSensorSelect ?: 0}",
            detail = heaterLockDetail ?: "打开后会先暂停加热，保存后恢复允许",
            accent = Yellow,
            enabled = !heaterConfigBusy,
        ),
        DeviceSettingEditor(
            key = DeviceSettingKey.DevicePeristalticPumpGroup,
            label = "蠕动泵",
            value = peristalticPumpGroupLabel(config?.deviceSparePumpMode),
            detail = pumpLockDetail ?: "鸡油/骨膏备用切换；不影响水箱加热",
            accent = Green,
            options = listOf("无备用", "鸡油泵切备用", "骨膏泵切备用"),
            enabled = !pumpConfigBusy,
        ),
    )
}

internal fun displayParameterCards(): List<SettingsFieldModel> =
    listOf(
        SettingsFieldModel(
            label = "显示语言",
            value = "中文",
            detail = "用于本机界面语言显示，后续可扩展多语言",
            accent = Cyan,
        ),
    )

internal fun applyDeviceSettingUpdate(
    editor: DeviceSettingEditor,
    newValue: String,
    onUpdateSettings: ((SettingsStore) -> Unit) -> Unit,
) {
    val trimmed = newValue.trim()
    if (trimmed.isEmpty()) return
    onUpdateSettings { store ->
        applyDeviceSettingUpdateToStore(editor, trimmed, store)
    }
}

internal fun applyDeviceSettingUpdateToStore(
    editor: DeviceSettingEditor,
    trimmed: String,
    store: SettingsStore,
) {
    if (trimmed.isEmpty()) return
    when (editor.key) {
        DeviceSettingKey.SerialPortPath -> store.updatePlcSerialPortPath(trimmed)
        DeviceSettingKey.BaudRate -> trimmed.toIntOrNull()?.let(store::updatePlcBaudRate)
        DeviceSettingKey.DataBits -> trimmed.toIntOrNull()?.let(store::updatePlcDataBits)
        DeviceSettingKey.Parity -> store.updatePlcParity(trimmed)
        DeviceSettingKey.StopBits -> trimmed.toIntOrNull()?.let(store::updatePlcStopBits)
        DeviceSettingKey.SlaveId -> trimmed.toIntOrNull()?.let(store::updatePlcSlaveId)
        DeviceSettingKey.ReadTimeoutMs -> trimmed.toLongOrNull()?.let(store::updateReadTimeoutMs)
        DeviceSettingKey.WriteTimeoutMs -> trimmed.toLongOrNull()?.let(store::updateWriteTimeoutMs)
        DeviceSettingKey.RetryCount -> trimmed.toIntOrNull()?.let(store::updateRetryCount)
        DeviceSettingKey.HeartbeatPeriodMs -> trimmed.toLongOrNull()?.let(store::updateHeartbeatPeriodMs)
        DeviceSettingKey.ActivePollingIntervalMs -> trimmed.toLongOrNull()?.let(store::updateActivePollingIntervalMs)
        DeviceSettingKey.IdlePollingIntervalMs -> trimmed.toLongOrNull()?.let(store::updateIdlePollingIntervalMs)
        DeviceSettingKey.OrderPollingIntervalMs -> trimmed.toLongOrNull()?.let(store::updateOrderPollingInterval)
        DeviceSettingKey.FrameGapMs -> trimmed.toLongOrNull()?.let(store::updateFrameGapMs)
        DeviceSettingKey.RegisterOnlyMode -> parseDeviceSettingBoolean(trimmed)?.let(store::updateRegisterOnlyMode)
        DeviceSettingKey.RegisterOnlyBlockStart ->
            parseRegisterOnlyBlockStart(trimmed)
                ?.let(store::updateRegisterOnlyBlockStart)
        DeviceSettingKey.RegisterOnlyBlockCount -> trimmed.toIntOrNull()?.let(store::updateRegisterOnlyBlockCount)
        DeviceSettingKey.FormulaSyncIntervalSeconds -> trimmed.toLongOrNull()?.let(store::updateFormulaSyncIntervalSeconds)
        DeviceSettingKey.ConnectionTestRegister -> trimmed.toIntOrNull()?.let(store::updateConnectionTestRegister)
        DeviceSettingKey.DeviceHeaterTargetTemp ->
            trimmed
                .toIntOrNull()
                ?.let(store::updateDeviceHeaterTargetTemp)
        DeviceSettingKey.DeviceHeaterHysteresisTemp ->
            trimmed
                .toIntOrNull()
                ?.let(store::updateDeviceHeaterHysteresisTemp)
        DeviceSettingKey.DeviceStandaloneWaterMode ->
            parseStandaloneWaterMode(trimmed)
                ?.let(store::updateDeviceStandaloneWaterMode)
        DeviceSettingKey.DeviceStandaloneWaterTimedSeconds ->
            parseSecondsToTicks(trimmed)
                ?.let(store::updateDeviceStandaloneWaterTimedTicks)
        DeviceSettingKey.DeviceWaterOutlet1CalibrationPercent ->
            parsePercent(trimmed)
                ?.let(store::updateDeviceWaterOutlet1CalibrationPercent)
        DeviceSettingKey.DeviceWaterOutlet2CalibrationPercent ->
            parsePercent(trimmed)
                ?.let(store::updateDeviceWaterOutlet2CalibrationPercent)
        DeviceSettingKey.DeviceWaterOutlet3CalibrationPercent ->
            parsePercent(trimmed)
                ?.let(store::updateDeviceWaterOutlet3CalibrationPercent)
        DeviceSettingKey.DeviceWaterOutlet4CalibrationPercent ->
            parsePercent(trimmed)
                ?.let(store::updateDeviceWaterOutlet4CalibrationPercent)
        DeviceSettingKey.DeviceChickenOilCalibrationPercent ->
            parsePercent(trimmed)
                ?.let(store::updateDeviceChickenOilCalibrationPercent)
        DeviceSettingKey.DeviceBonePasteCalibrationPercent ->
            parsePercent(trimmed)
                ?.let(store::updateDeviceBonePasteCalibrationPercent)
        DeviceSettingKey.DeviceHeaterSelect ->
            parseHeaterSelect(trimmed)
                ?.let(store::updateDeviceHeaterSelect)
        DeviceSettingKey.DeviceHeaterSensorSelect ->
            parseHeaterSensorSelect(trimmed)
                ?.let(store::updateDeviceHeaterSensorSelect)
        DeviceSettingKey.DeviceChickenPumpSource ->
            parseChickenPumpSource(trimmed)
                ?.let(store::updateDeviceSparePumpMode)
        DeviceSettingKey.DeviceBonePumpSource ->
            parseBonePumpSource(trimmed)
                ?.let(store::updateDeviceSparePumpMode)
        DeviceSettingKey.DevicePeristalticPumpGroup ->
            parsePeristalticPumpGroup(trimmed)
                ?.let(store::updateDeviceSparePumpMode)
        DeviceSettingKey.DeviceHeaterComposite -> {
            val parts = trimmed.split("|")
            parts.getOrNull(0)?.toIntOrNull()?.let(store::updateDeviceHeaterTargetTemp)
            parts.getOrNull(1)?.toIntOrNull()?.let(store::updateDeviceHeaterHysteresisTemp)
        }
        DeviceSettingKey.DeviceStandaloneWaterComposite -> {
            val parts = trimmed.split("|")
            parts.getOrNull(0)?.toIntOrNull()?.let(store::updateDeviceStandaloneWaterMode)
            parts.getOrNull(1)?.toIntOrNull()?.let(store::updateDeviceStandaloneWaterTimedTicks)
        }
        DeviceSettingKey.DeviceWaterCalibrationComposite -> {
            val parts = trimmed.split("|")
            parts.getOrNull(0)?.toIntOrNull()?.let(store::updateDeviceWaterOutlet1CalibrationPercent)
            parts.getOrNull(1)?.toIntOrNull()?.let(store::updateDeviceWaterOutlet2CalibrationPercent)
            parts.getOrNull(2)?.toIntOrNull()?.let(store::updateDeviceWaterOutlet3CalibrationPercent)
            parts.getOrNull(3)?.toIntOrNull()?.let(store::updateDeviceWaterOutlet4CalibrationPercent)
        }
        DeviceSettingKey.DevicePumpCorrectionComposite -> {
            val parts = trimmed.split("|")
            parts.getOrNull(0)?.toIntOrNull()?.let(store::updateDeviceChickenOilCalibrationPercent)
            parts.getOrNull(1)?.toIntOrNull()?.let(store::updateDeviceBonePasteCalibrationPercent)
        }
        DeviceSettingKey.DeviceHeaterConfigComposite -> {
            val parts = trimmed.split("|")
            parts.getOrNull(0)?.toIntOrNull()?.let(store::updateDeviceHeaterSelect)
            parts.getOrNull(1)?.toIntOrNull()?.let(store::updateDeviceHeaterSensorSelect)
        }
    }
}

internal fun DeviceSettingEditor.displayValue(): String {
    val parts = value.split("|")
    return when (key) {
        DeviceSettingKey.DeviceHeaterComposite ->
            "${parts.getOrNull(0) ?: "--"}℃ / ${parts.getOrNull(1) ?: "--"}℃"
        DeviceSettingKey.DeviceStandaloneWaterComposite -> {
            val mode = parts.getOrNull(0)?.toIntOrNull()
            val ticks = parts.getOrNull(1)?.toIntOrNull()
            "${standaloneWaterModeLabel(mode)} / ${ticks?.ticksToSecondsText() ?: "--"}s"
        }
        DeviceSettingKey.DeviceWaterCalibrationComposite ->
            parts.joinToString(" | ") { if (it.isBlank()) "--" else "$it%" }
        DeviceSettingKey.DevicePumpCorrectionComposite ->
            "鸡油 ${parts.getOrNull(0) ?: "--"}% / 骨膏 ${parts.getOrNull(1) ?: "--"}%"
        DeviceSettingKey.DeviceHeaterConfigComposite -> {
            val select = parts.getOrNull(0)?.toIntOrNull()
            val sensor = parts.getOrNull(1)?.toIntOrNull()
            "${heaterSelectLabel(select)} / ${heaterSensorLabel(sensor)}"
        }
        else -> value
    }
}

private fun parseRegisterOnlyBlockStart(value: String): Int? {
    val normalized = value.trim().uppercase().removePrefix("D")
    val parsed = normalized.toIntOrNull() ?: return null
    return parsed.takeIf { it in setOf(0, 200, 320, 350, 370) }
}

private fun parseDeviceSettingBoolean(value: String): Boolean? =
    when (value.trim().lowercase()) {
        "1", "true", "on", "yes", "y", "开启", "开" -> true
        "0", "false", "off", "no", "n", "关闭", "关" -> false
        else -> null
    }

private fun standaloneWaterModeLabel(mode: Int?): String =
    when (mode) {
        0 -> "关闭"
        1 -> "点动"
        2 -> "时间控制"
        else -> "--"
    }

private fun heaterSelectLabel(select: Int?): String =
    when (select) {
        1 -> "主加热管"
        2 -> "备加热管"
        else -> "--"
    }

private fun heaterSensorLabel(select: Int?): String =
    when (select) {
        0 -> "温度0"
        1 -> "温度1"
        2 -> "温度1优先"
        else -> "--"
    }

private fun peristalticPumpGroupLabel(value: Int?): String =
    when (value) {
        0 -> "无备用"
        1 -> "鸡油泵切备用"
        2 -> "骨膏泵切备用"
        else -> "--"
    }

internal fun Int.ticksToSecondsText(): String {
    val seconds = this / 10.0
    return if (this % 10 == 0) {
        seconds.toInt().toString()
    } else {
        "%.1f".format(seconds)
    }
}

private fun parseStandaloneWaterMode(value: String): Int? =
    when (value.trim().lowercase()) {
        "0", "off", "关闭", "关" -> 0
        "1", "jog", "点动" -> 1
        "2", "timed", "time", "时间控制", "时控" -> 2
        else -> null
    }

private fun parseHeaterSelect(value: String): Int? =
    when (value.trim()) {
        "1", "主", "主加热", "主加热管" -> 1
        "2", "备", "备用", "备加热", "备加热管" -> 2
        else -> null
    }

private fun parseHeaterSensorSelect(value: String): Int? =
    when (value.trim()) {
        "0", "温度0", "传感器0" -> 0
        "1", "温度1", "传感器1" -> 1
        "2", "温度1优先", "自动", "备用传感器" -> 2
        else -> null
    }

private fun parseChickenPumpSource(value: String): Int? =
    when (value.trim()) {
        "原鸡油泵", "原泵", "不用备用", "0" -> 0
        "使用备用泵", "备用泵", "备用", "1" -> 1
        else -> null
    }

private fun parseBonePumpSource(value: String): Int? =
    when (value.trim()) {
        "原骨膏泵", "原泵", "不用备用", "0" -> 0
        "使用备用泵", "备用泵", "备用", "2" -> 2
        else -> null
    }

private fun parsePeristalticPumpGroup(value: String): Int? =
    when (value.trim()) {
        "无备用", "0" -> 0
        "鸡油泵切备用", "1" -> 1
        "骨膏泵切备用", "2" -> 2
        else -> null
    }

private fun parsePercent(value: String): Int? = value.trim().removeSuffix("%").toIntOrNull()

private fun parseSecondsToTicks(value: String): Int? {
    val seconds =
        value
            .trim()
            .removeSuffix("s")
            .removeSuffix("秒")
            .toDoubleOrNull() ?: return null
    return (seconds * 10).toInt().coerceAtLeast(1)
}

internal fun PlcPollingSnapshot.isHeaterActuatorConfigBusy(): Boolean {
    val busyActionStates = setOf(1, 2)
    return phaseActionStateCode in busyActionStates ||
        emergencyActionStateCode in busyActionStates ||
        waterValve0Output ||
        waterValve1Output ||
        waterValve2Output ||
        waterValve3Output ||
        singleWaterValveOutput ||
        chickenOilPumpOutput ||
        bonePastePumpOutput ||
        sparePumpOutput
}

internal fun PlcPollingSnapshot.isPumpActuatorConfigBusy(): Boolean {
    val busyActionStates = setOf(1, 2)
    return phaseActionStateCode in busyActionStates ||
        emergencyActionStateCode in busyActionStates ||
        chickenOilPumpOutput ||
        bonePastePumpOutput ||
        sparePumpOutput
}
