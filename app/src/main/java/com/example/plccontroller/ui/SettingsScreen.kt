package com.example.plccontroller.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.plccontroller.domain.*
import com.example.plccontroller.runtime.NetworkConnectionState
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
// This Compose coordinator keeps page ownership visible; page actions are grouped above.
@Suppress("LongMethod")
@Composable
internal fun SettingsScreen(
    state: SettingsViewState,
    actions: SettingsScreenActions,
    modifier: Modifier = Modifier,
) {
    val tabs = SettingsTab.entries
    val initialPage = remember { tabs.indexOf(SettingsTab.Formula).coerceAtLeast(0) }
    val pagerState = rememberPagerState(initialPage = initialPage, pageCount = { tabs.size })
    val coroutineScope = rememberCoroutineScope()
    val selectedTab = tabs[pagerState.currentPage]
    var contentReady by rememberSaveable { mutableStateOf(false) }
    val formulaOptions = remember(state.formulaCatalog) { state.formulaCatalog.settingOptions() }
    var selectedFormulaCode by remember(state.formulaCatalog?.formulaCode) {
        mutableStateOf(formulaOptions.firstOrNull()?.code.orEmpty())
    }

    LaunchedEffect(formulaOptions) {
        if (formulaOptions.none { it.code == selectedFormulaCode }) {
            selectedFormulaCode = formulaOptions.firstOrNull()?.code.orEmpty()
        }
    }

    val selectedFormula = formulaOptions.firstOrNull { it.code == selectedFormulaCode }
    val overviewCards =
        remember(
            selectedTab,
            state.networkState,
            state.plcState,
            state.formulaCatalog,
            state.formulaSourceLabel,
            state.deviceCode,
            state.pendingOrders,
            state.lastMessage,
            state.communicationConfig,
            state.deviceConfig,
        ) {
            settingsOverviewCardsClean(
                selectedTab = selectedTab,
                state = state,
            )
        }

    LaunchedEffect(Unit) {
        if (!contentReady) {
            withFrameNanos { }
            contentReady = true
        }
    }

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(20.dp))
                .background(PanelDeep)
                .border(1.dp, BorderBlue.copy(alpha = 0.2f), RoundedCornerShape(20.dp))
                .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        SettingsTabs(
            selectedTab = selectedTab,
            onSelect = { tab ->
                coroutineScope.launch {
                    pagerState.animateScrollToPage(tabs.indexOf(tab))
                }
            },
        )

        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(120.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            overviewCards.forEach { card ->
                SettingsOverviewCard(
                    model = card,
                    modifier = Modifier.weight(1f),
                    height = 116.dp,
                )
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        if (!contentReady) {
            PageWarmupPlaceholder(
                title = "正在准备设置页",
                detail = "先打开页面框架，再补齐配方和参数内容",
                modifier = Modifier.weight(1f),
            )
        } else {
            HorizontalPager(
                state = pagerState,
                userScrollEnabled = false,
                modifier = Modifier.weight(1f),
            ) { page ->
                val pageTab = tabs[page]
                when (pageTab) {
                    SettingsTab.Access ->
                        AccessSettingsContent(
                            networkState = state.networkState,
                            plcState = state.plcState,
                            businessUrl = state.businessUrl,
                            managementUrl = state.managementUrl,
                            deviceCode = state.deviceCode,
                            registrationState = state.registrationState,
                            onStartRegistration = actions.access.startRegistration,
                            onSelectDeviceType = actions.access.selectDeviceType,
                            onSelectEquipment = actions.access.selectEquipment,
                            onResetRegistrationState = actions.access.resetRegistrationState,
                            onUpdateSettings = actions.access.updateSettings,
                            modifier = Modifier.fillMaxSize(),
                        )

                    SettingsTab.Formula ->
                        FormulaSettingsContent(
                            formulaCatalog = state.formulaCatalog,
                            availableCatalogs = state.availableCatalogs,
                            formulaSourceLabel = state.formulaSourceLabel,
                            formulaSyncIntervalSeconds = state.formulaSyncIntervalSeconds,
                            options = formulaOptions,
                            selectedOption = selectedFormula,
                            onSelectCatalog = actions.formula.selectCatalog,
                            onSelectOption = { selectedFormulaCode = it },
                            onSaveFormulaParameter = actions.formula.saveParameter,
                            modifier = Modifier.fillMaxSize(),
                        )

                    SettingsTab.Device ->
                        DeviceSettingsContent(
                            communicationConfig = state.communicationConfig,
                            formulaSyncIntervalSeconds = state.formulaSyncIntervalSeconds,
                            deviceConfig = state.deviceConfig,
                            plcPollingSnapshot = state.plcPollingSnapshot,
                            onUpdateSettings = actions.access.updateSettings,
                            onBeginHeaterActuatorConfigEdit = actions.device.beginHeaterActuatorConfigEdit,
                            onCancelHeaterActuatorConfigEdit = actions.device.cancelHeaterActuatorConfigEdit,
                            onSaveHeaterActuatorConfig = actions.device.saveHeaterActuatorConfig,
                            modifier = Modifier.fillMaxSize(),
                        )

                    SettingsTab.Maintenance ->
                        MaintenanceReadOnlyContent(
                            networkState = state.networkState,
                            plcState = state.plcState,
                            pendingOrders = state.pendingOrders,
                            waitingTransferOrders = state.waitingTransferOrders,
                            completedOrders = state.completedOrders,
                            cancelledOrders = state.cancelledOrders,
                            lastMessage = state.lastMessage,
                            logCount = state.logCount,
                            logs = state.logs,
                            communicationConfig = state.communicationConfig,
                            plcPollingSnapshot = state.plcPollingSnapshot,
                            onBeginHeaterActuatorConfigEdit = actions.device.beginHeaterActuatorConfigEdit,
                            onCancelHeaterActuatorConfigEdit = actions.device.cancelHeaterActuatorConfigEdit,
                            onSaveHeaterActuatorConfig = actions.device.saveHeaterActuatorConfig,
                            onRefreshPlcStatus = actions.diagnostics.refreshPlcStatus,
                            onSendHeartbeatPulse = actions.diagnostics.sendHeartbeatPulse,
                            onSendHeaterTestCommand = actions.diagnostics.sendHeaterTestCommand,
                            onSendEmergencyWaterTestCommand = actions.diagnostics.sendEmergencyWaterTestCommand,
                            onSendPhaseTestCommand = actions.diagnostics.sendPhaseTestCommand,
                            onUpdateSettings = actions.access.updateSettings,
                            modifier = Modifier.fillMaxSize(),
                        )

                    SettingsTab.Debug ->
                        DebugSettingsContent(
                            plcState = state.plcState,
                            communicationConfig = state.communicationConfig,
                            plcPollingSnapshot = state.plcPollingSnapshot,
                            lastMessage = state.lastMessage,
                            onRefreshPlcStatus = actions.diagnostics.refreshPlcStatus,
                            onSendHeartbeatPulse = actions.diagnostics.sendHeartbeatPulse,
                            onSendHeaterTestCommand = actions.diagnostics.sendHeaterTestCommand,
                            onSendEmergencyWaterTestCommand = actions.diagnostics.sendEmergencyWaterTestCommand,
                            onSendPhaseTestCommand = actions.diagnostics.sendPhaseTestCommand,
                            onOpenSecondaryDisplay = actions.display.openSecondaryDisplay,
                            onPreviewSecondaryDisplay = actions.display.previewSecondaryDisplay,
                            onClearAllLocalOrders = actions.orders.clearAllLocalOrders,
                            modifier = Modifier.fillMaxSize(),
                        )
                }
            }
        }
    }
}

@Suppress("CyclomaticComplexMethod", "LongMethod")
private fun settingsOverviewCardsClean(
    selectedTab: SettingsTab,
    state: SettingsViewState,
): List<SettingsOverviewCardModel> =
    when (selectedTab) {
        SettingsTab.Access ->
            listOf(
                SettingsOverviewCardModel(
                    label = "网络连接",
                    value = state.networkState.statusLabel(),
                    detail = "数据接口与云端同步状态",
                    accent = state.networkState.statusColor(),
                ),
                SettingsOverviewCardModel(
                    label = "设备编码",
                    value = state.deviceCode.ifBlank { "未绑定" },
                    detail = "本机在管理系统的唯一标识",
                    accent = Cyan,
                ),
            )

        SettingsTab.Formula ->
            listOf(
                SettingsOverviewCardModel(
                    label = "配方库",
                    value = state.formulaCatalog?.formulaName ?: "未加载",
                    detail = "版本: ${state.formulaCatalog?.formulaCode ?: "--"}",
                    accent = Green,
                ),
                SettingsOverviewCardModel(
                    label = "数据源",
                    value = state.formulaSourceLabel,
                    detail = "配方参数的同步来源",
                    accent = Blue,
                ),
            )

        SettingsTab.Device ->
            listOf(
                SettingsOverviewCardModel(
                    label = "设备编码",
                    value = state.deviceCode.ifBlank { "未登记" },
                    detail = "用于平台设备绑定与配方同步",
                    accent = if (state.deviceCode.isNotBlank()) Green else Yellow,
                ),
                SettingsOverviewCardModel(
                    label = "水箱加热",
                    value = if (state.plcPollingSnapshot.temperatureSensor0 != null) {
                        state.plcPollingSnapshot.formatTemperature0Cn()
                    } else {
                        "未联机"
                    },
                    detail = state.deviceConfig?.let {
                        "目标: ${it.deviceHeaterTargetTemp}℃ / 回差: ${it.deviceHeaterHysteresisTemp}℃"
                    } ?: "暂无参数",
                    accent = if (state.plcPollingSnapshot.temperatureSensor0 != null) Blue else Yellow,
                ),
            )

        SettingsTab.Maintenance ->
            listOf(
                SettingsOverviewCardModel(
                    label = "系统自检",
                    value = if (state.plcState == PlcConnectionState.Connected) "正常" else "受限",
                    detail = "核心 PLC 链路与自检状态",
                    accent = if (state.plcState == PlcConnectionState.Connected) Green else Red,
                ),
                SettingsOverviewCardModel(
                    label = "待处理队列",
                    value = "${state.pendingOrders.size} 单",
                    detail = "当前积压待加水的订单数",
                    accent = Cyan,
                ),
            )

        SettingsTab.Debug ->
            listOf(
                SettingsOverviewCardModel(
                    label = "串口驱动诊断",
                    value = state.plcPollingSnapshot.serialDiagnostic.summary,
                    detail = state.plcPollingSnapshot.serialDiagnostic.method,
                    accent = if (state.plcPollingSnapshot.serialDiagnostic.rawModeOk == true) Green else Yellow,
                ),
                SettingsOverviewCardModel(
                    label = "Modbus轮询",
                    value = if (state.plcPollingSnapshot.lastSuccessfulPollAtMs != null) "通信中" else "停止",
                    detail = state.communicationConfig?.let {
                        val slave = it.slaveId
                        val gap = it.frameGapMs
                        val poll = it.idlePollingIntervalMs
                        "从站: $slave / 帧间隔: ${gap}ms / 轮询: ${poll}ms"
                    } ?: "未连接",
                    accent = if (state.plcPollingSnapshot.lastSuccessfulPollAtMs != null) Green else Red,
                ),
            )
    }

@Preview(widthDp = 800, heightDp = 1280, showBackground = true)
@Composable
private fun SettingsScreenPreview() {
    MaterialTheme {
        Box(modifier = Modifier.background(PageBackground)) {
            val previewState = SettingsViewState(
                networkState = NetworkConnectionState.Online,
                plcState = PlcConnectionState.Connected,
                formulaCatalog = null,
                availableCatalogs = emptyList(),
                formulaSourceLabel = "本地默认",
                communicationConfig = null,
                businessUrl = "http://127.0.0.1",
                managementUrl = "http://127.0.0.1",
                deviceCode = "DEV-001",
                formulaSyncIntervalSeconds = 30L,
                deviceConfig = null,
                plcPollingSnapshot = PlcPollingSnapshot(),
                pendingOrders = emptyList(),
                waitingTransferOrders = emptyList(),
                completedOrders = emptyList(),
                cancelledOrders = emptyList(),
                lastMessage = "系统就绪",
                logCount = 0,
                logs =
                    listOf(
                        "1717723200000  应用启动完成",
                        "1717723205000  PLC 通讯恢复正常",
                    ),
                registrationState = RegistrationState.Idle
            )
            SettingsScreen(
                state = previewState,
                actions = emptySettingsScreenActions(),
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
