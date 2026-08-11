package com.example.plccontroller.ui

import androidx.compose.ui.graphics.Color
import com.example.plccontroller.domain.FormulaCatalog
import com.example.plccontroller.domain.Order
import com.example.plccontroller.domain.PlcCommunicationConfig
import com.example.plccontroller.domain.PlcConnectionState
import com.example.plccontroller.runtime.NetworkConnectionState

internal data class SettingsOverviewCardModel(
    val label: String,
    val value: String,
    val detail: String,
    val accent: Color,
)

internal data class SettingsFieldModel(
    val label: String,
    val value: String,
    val detail: String? = null,
    val accent: Color = Cyan,
)

data class SettingsViewState(
    val networkState: NetworkConnectionState,
    val plcState: PlcConnectionState,
    val formulaCatalog: FormulaCatalog?,
    val availableCatalogs: List<FormulaCatalog>,
    val formulaSourceLabel: String,
    val communicationConfig: PlcCommunicationConfig?,
    val businessUrl: String,
    val managementUrl: String,
    val deviceCode: String,
    val formulaSyncIntervalSeconds: Long,
    val deviceConfig: com.example.plccontroller.data.AppPersistentConfig?,
    val plcPollingSnapshot: com.example.plccontroller.domain.PlcPollingSnapshot,
    val pendingOrders: List<Order>,
    val waitingTransferOrders: List<Order>,
    val completedOrders: List<Order>,
    val cancelledOrders: List<Order>,
    val lastMessage: String,
    val logCount: Int,
    val logs: List<String>,
    val registrationState: RegistrationState,
)

internal fun PlcConnectionState.shortLabel(): String =
    when (this) {
        PlcConnectionState.Disconnected -> "PLC 未连接"
        PlcConnectionState.Connecting -> "PLC 连接中"
        PlcConnectionState.Connected -> "PLC 正常"
        PlcConnectionState.Fault -> "PLC 故障"
    }

internal fun PlcConnectionState.statusColor(): Color =
    when (this) {
        PlcConnectionState.Connected -> Green
        PlcConnectionState.Fault -> Red
        else -> Yellow
    }

internal fun NetworkConnectionState.statusLabel(): String =
    when (this) {
        NetworkConnectionState.Idle -> "待同步"
        NetworkConnectionState.Syncing -> "同步中"
        NetworkConnectionState.Online -> "在线"
        NetworkConnectionState.Fault -> "异常"
    }

internal fun NetworkConnectionState.statusColor(): Color =
    when (this) {
        NetworkConnectionState.Online -> Green
        NetworkConnectionState.Syncing -> Cyan
        NetworkConnectionState.Fault -> Red
        NetworkConnectionState.Idle -> Yellow
    }
