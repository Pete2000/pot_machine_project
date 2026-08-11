package com.example.plccontroller.ui

import com.example.plccontroller.domain.PlcConnectionState
import com.example.plccontroller.domain.PlcPollingSnapshot
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal fun PlcPollingSnapshot.formatTemperature0Cn(): String = formatTenthsTemperatureCn(temperatureSensor0)

internal fun PlcPollingSnapshot.formatTemperature1Cn(): String = formatTenthsTemperatureCn(temperatureSensor1)

private fun formatTenthsTemperatureCn(rawValue: Int?): String {
    val value = rawValue ?: return "--"
    return String.format(Locale.CHINA, "%.1f°C", value / 10.0)
}

internal fun PlcPollingSnapshot.liquidLevelLabelCn(): String =
    when (liquidLevelState) {
        null -> "--"
        0 -> "超低液位"
        1 -> "低液位"
        2 -> "低液位异常"
        3 -> "中液位"
        4 -> "低液位与中液位异常"
        5 -> "中液位异常"
        6 -> "低液位异常"
        7 -> "高液位"
        else -> "液位状态异常($liquidLevelState)"
    }

internal fun plcActionStateLabelCn(code: Int?): String =
    when (code) {
        null -> "--"
        0 -> "空闲"
        1 -> "已接收"
        2 -> "执行中"
        3 -> "已完成"
        4 -> "故障"
        5 -> "拒绝执行"
        6 -> "已停止"
        else -> "未知($code)"
    }

internal fun plcResultCodeLabelCn(code: Int?): String =
    when (code) {
        null -> "--"
        0 -> "成功"
        1 -> "参数非法"
        2 -> "PLC忙"
        3 -> "液位不满足"
        4 -> "安全联锁"
        5 -> "动作超时"
        6 -> "被停止"
        7 -> "未知故障"
        else -> "代码($code)"
    }

internal fun PlcPollingSnapshot.heaterDisplayLabelCn(plcState: PlcConnectionState): String =
    when {
        mainHeaterOutput && backupHeaterOutput -> "主备并行"
        mainHeaterOutput -> "主加热运行"
        backupHeaterOutput -> "备加热运行"
        plcState == PlcConnectionState.Fault -> "通讯异常"
        else -> "待机"
    }

internal fun plcFaultSummaryCn(
    plcState: PlcConnectionState,
    snapshot: PlcPollingSnapshot,
    lastMessage: String,
): String {
    val warning = snapshot.pollWarnings.firstOrNull()?.let(::translatePlcDiagnosticMessageCn)
    if (plcState == PlcConnectionState.Fault) {
        return when {
            snapshot.phaseActionStateCode == 4 -> "相位故障：${plcResultCodeLabelCn(snapshot.phaseResultCode)}"
            snapshot.heaterActionStateCode == 4 -> "加热故障：${plcResultCodeLabelCn(snapshot.heaterResultCode)}"
            snapshot.emergencyActionStateCode == 4 -> "应急故障：${plcResultCodeLabelCn(snapshot.emergencyResultCode)}"
            snapshot.emergencyStopActive -> "急停输入触发"
            warning != null -> warning
            else -> translatePlcDiagnosticMessageCn(lastMessage)
        }
    }
    return when {
        warning != null -> warning
        snapshot.phaseActionStateCode == 2 -> "相位执行中"
        snapshot.mainHeaterOutput || snapshot.backupHeaterOutput -> snapshot.heaterDisplayLabelCn(plcState)
        snapshot.emergencyActionStateCode == 2 -> "应急补水运行中"
        else -> "状态正常"
    }
}

internal fun PlcPollingSnapshot.lastPollTimeText(): String {
    val ms = lastSuccessfulPollAtMs ?: return "--"
    return SimpleDateFormat("HH:mm:ss", Locale.CHINA).format(Date(ms))
}

internal fun PlcPollingSnapshot.lastPollAgeText(): String {
    val ms = lastSuccessfulPollAtMs ?: return "--"
    val diff = System.currentTimeMillis() - ms
    return when {
        diff < 0 -> "0s"
        diff < 1000 -> "${diff}ms"
        else -> "${diff / 1000}s"
    }
}

internal fun translatePlcDiagnosticMessageCn(message: String): String {
    val normalized = message.trim()
    if (normalized.isBlank()) {
        return "PLC 通讯异常，请检查串口、站号和返回帧。"
    }
    return when {
        normalized.contains("I/O", ignoreCase = true) && normalized.contains("failed", ignoreCase = true) ->
            "I/O 线圈读取失败"
        normalized.contains("温度/液位", ignoreCase = true) && normalized.contains("失败") ->
            normalized
        normalized.contains("phase", ignoreCase = true) && normalized.contains("failed", ignoreCase = true) ->
            "相位状态区读取失败"
        normalized.contains("heater", ignoreCase = true) && normalized.contains("failed", ignoreCase = true) ->
            "加热状态区读取失败"
        normalized.contains("emergency", ignoreCase = true) && normalized.contains("failed", ignoreCase = true) ->
            "应急状态区读取失败"
        normalized.contains("Invalid Modbus write-coil CRC", ignoreCase = true) ->
            "写线圈响应 CRC 异常"
        normalized.contains("Invalid Modbus coil CRC", ignoreCase = true) ->
            "线圈读取 CRC 异常"
        normalized.contains("Invalid Modbus register CRC", ignoreCase = true) ->
            "寄存器读取 CRC 异常"
        normalized.contains("Timed out while reading", ignoreCase = true) ->
            "PLC 返回超时，请检查从站响应"
        normalized.contains("poll partial warnings=", ignoreCase = true) ->
            normalized.removePrefix("poll partial warnings=")
        normalized.contains("PLC polling tick ok", ignoreCase = true) ->
            "PLC 轮询正常"
        normalized.contains("PLC polling failed", ignoreCase = true) ->
            "PLC 轮询失败"
        normalized.contains("Unable to open serial port", ignoreCase = true) ->
            "串口打开失败，请检查端口占用和权限"
        normalized.contains("CRC", ignoreCase = true) ->
            "PLC 返回帧 CRC 异常"
        normalized.contains("timeout", ignoreCase = true) ->
            "PLC 通讯超时"
        else -> normalized
    }
}
