package com.example.plccontroller.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.plccontroller.domain.PlcPollingSnapshot
import com.example.plccontroller.runtime.NetworkConnectionState
import com.example.plccontroller.runtime.PhaseFlowSource
import com.example.plccontroller.runtime.PhasePromptKind
import kotlinx.coroutines.delay

@Composable
fun MainScreen(
    viewModel: MainViewModel,
    onOpenSecondaryDisplay: () -> Unit = {},
    onPreviewSecondaryDisplay: () -> Unit = {},
) {
    val state by viewModel.uiState.collectAsState()
    val registrationState by viewModel.registrationState.collectAsState()
    val activeOrder = state.currentOrder ?: state.pendingOrders.firstOrNull()
    val phaseRotationPrompt = state.phaseRotationPrompt
    val operatorAlert = state.operatorAlerts.firstOrNull()
    val operatorNotice = state.operatorNotice

    var selectedTab by rememberSaveable { mutableStateOf(BottomTab.Home) }
    var homeMode by rememberSaveable { mutableStateOf(HomeMode.Receive) }
    var ordersInitialTab by rememberSaveable { mutableStateOf(OrdersPageTab.PendingWater) }

    LaunchedEffect(Unit) {
        delay(350)
        viewModel.refreshOrdersIfStale(minIntervalMs = 0L)
    }

    LaunchedEffect(selectedTab) {
        if (selectedTab == BottomTab.Orders) {
            viewModel.refreshOrdersIfStale()
        }
    }

    LaunchedEffect(operatorNotice?.id) {
        val activeNotice = operatorNotice ?: return@LaunchedEffect
        delay(OPERATOR_NOTICE_AUTO_DISMISS_MS)
        viewModel.dismissOperatorNotice(activeNotice.id)
    }

    // 当系统判定当前锅位需要旋转（例如非左上锅位需要加鸡油或骨膏时），
    // 业务调度中心会下发 phaseRotationPrompt 提示，此处在最外层弹出全局模态对话框，
    // 强制店员确认手动转锅完成，方可继续下发 PLC 加料动作指令。
    if (state.faultMeltdownState.active) {
        val typeStr =
            if (state.faultMeltdownState.type == com.example.plccontroller.runtime.FaultType.EmergencyStop) {
                "物理急停已按下"
            } else {
                "底层通讯严重中断"
            }
        val interruptedText = state.faultMeltdownState.interruptedOrderId?.let { "\n\n这导致订单 $it 在执行中被强行打断！" } ?: ""

        val isPlcStillFaulty =
            state.plcState != com.example.plccontroller.domain.PlcConnectionState.Connected ||
                state.plcPollingSnapshot.emergencyStopActive
        val hardwareWarning =
            if (isPlcStillFaulty) {
                "\n\n⚠️ 请先排除硬件故障（释放急停按钮或恢复接线），否则无法解除熔断锁定。"
            } else {
                "\n\n硬件故障已恢复，请选择如何处理被中断的订单（如有）。"
            }

        AlertDialog(
            onDismissRequest = {}, // 不允许取消
            title = {
                Text(
                    text = "⚠ 系统故障熔断！ ($typeStr)",
                    color = Color(0xFFFF4D6D),
                    fontWeight = FontWeight.Bold,
                )
            },
            text = {
                Text(
                    text = "发生严重异常：$typeStr。为保护设备安全，Android 调度系统已强制挂起所有自动化操作。$interruptedText$hardwareWarning",
                )
            },
            confirmButton = {
                if (state.faultMeltdownState.interruptedOrderId != null) {
                    TextButton(
                        onClick = { viewModel.resolveFaultMeltdown(completeInterruptedOrder = true) },
                        enabled = !isPlcStillFaulty,
                        colors =
                            androidx.compose.material3.ButtonDefaults
                                .textButtonColors(contentColor = Yellow),
                    ) {
                        Text(text = "我已线下补料，强制完成此单")
                    }
                } else {
                    TextButton(
                        onClick = { viewModel.resolveFaultMeltdown(completeInterruptedOrder = false) },
                        enabled = !isPlcStillFaulty,
                        colors =
                            androidx.compose.material3.ButtonDefaults
                                .textButtonColors(contentColor = Yellow),
                    ) {
                        Text(text = "确认并恢复系统")
                    }
                }
            },
            dismissButton =
                if (state.faultMeltdownState.interruptedOrderId != null) {
                    {
                        TextButton(
                            onClick = { viewModel.resolveFaultMeltdown(completeInterruptedOrder = false) },
                            enabled = !isPlcStillFaulty,
                            colors =
                                androidx.compose.material3.ButtonDefaults
                                    .textButtonColors(contentColor = Color(0xFFFF4D6D)),
                        ) {
                            Text(text = "作废中断订单")
                        }
                    }
                } else {
                    null
                },
            containerColor = Color(0xFF2C1C1C), // 红色警示背景
            titleContentColor = Color.White,
            textContentColor = Color(0xFFFFB3B3),
        )
    } else if (operatorAlert != null) {
        AlertDialog(
            onDismissRequest = {},
            title = {
                Text(
                    text = operatorAlert.title,
                    color = Color(0xFFFF4D6D),
                    fontWeight = FontWeight.Bold,
                )
            },
            text = {
                Text(text = operatorAlert.message)
            },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.acknowledgeOperatorAlert(operatorAlert.id) },
                    colors =
                        androidx.compose.material3.ButtonDefaults
                            .textButtonColors(contentColor = Yellow),
                ) {
                    Text(text = "我知道了")
                }
            },
            containerColor = Panel,
            titleContentColor = TextPrimary,
            textContentColor = TextSecondary,
        )
    } else if (phaseRotationPrompt != null) {
        AlertDialog(
            onDismissRequest = {}, // 禁用点击空白处消失，确保店员必须明确选择
            title = {
                Text(text = phaseRotationPrompt.title)
            },
            text = {
                Text(
                    text =
                        if (phaseRotationPrompt.kind == PhasePromptKind.FormulaMissing) {
                            phaseRotationPrompt.message
                        } else if (phaseRotationPrompt.source == PhaseFlowSource.Order) {
                            phaseRotationPrompt.message +
                                "\n\n提示：设备已完成部分加水，请完成转锅流程。"
                        } else {
                            phaseRotationPrompt.message +
                                "\n\n提示：暂不处理后可重新开始，已执行的物理加水/加料不会回退。"
                        },
                )
            },
            confirmButton = {
                TextButton(
                    onClick = viewModel::confirmPhaseRotation, // 点击确认已转锅，开始写入下阶段 PLC 指令
                    colors =
                        androidx.compose.material3.ButtonDefaults
                            .textButtonColors(contentColor = Yellow),
                ) {
                    Text(text = phaseRotationPrompt.confirmText)
                }
            },
            dismissButton =
                if (
                    phaseRotationPrompt.source == PhaseFlowSource.Manual ||
                    phaseRotationPrompt.kind == PhasePromptKind.FormulaMissing
                ) {
                    {
                        TextButton(
                            onClick = viewModel::cancelPhaseRotation,
                            colors =
                                androidx.compose.material3.ButtonDefaults
                                    .textButtonColors(contentColor = TextSecondary),
                        ) {
                            Text(text = phaseRotationPrompt.cancelText)
                        }
                    }
                } else {
                    null
                },
            containerColor = Panel,
            titleContentColor = TextPrimary,
            textContentColor = TextSecondary,
        )
    }

    val isWateringActive = state.currentOrder != null || state.plcPollingSnapshot.isWatering()

    Scaffold(
        containerColor = PageBackground,
        contentWindowInsets = WindowInsets(0.dp),
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .background(PageBackground)
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                when (selectedTab) {
                    BottomTab.Home ->
                        HomeScreen(
                            state = state,
                            activeOrder = activeOrder,
                            homeMode = homeMode,
                            isWateringActive = isWateringActive,
                            onHomeModeChange = { if (!isWateringActive) homeMode = it },
                            onRequestWater = viewModel::requestWater,
                            onRequestManualWater = viewModel::requestManualWater,
                            onContinueManualPhase = viewModel::continueManualPhase,
                            onSetOrderDelayed = { order, delayed ->
                                viewModel.setOrderDelayed(order.id, delayed)
                            },
                            onPinOrder = viewModel::toggleOrderPinned,
                            onOpenPendingWaterOrders = {
                                ordersInitialTab = OrdersPageTab.PendingWater
                                selectedTab = BottomTab.Orders
                            },
                            modifier = Modifier.weight(1f),
                        )

                    BottomTab.Orders ->
                        OrdersScreen(
                            initialTab = ordersInitialTab,
                            pendingOrders = state.pendingOrders,
                            waitingTransferOrders = state.waitingTransferOrders,
                            currentOrder = state.currentOrder,
                            completedOrders = state.completedOrders,
                            cancelledOrders = state.cancelledOrders,
                            onRefreshOrders = viewModel::refreshOrders,
                            onCancelPendingOrder = viewModel::cancelPendingOrder,
                            onRewaterOrder = viewModel::rewaterOrder,
                            emptyReason =
                                when {
                                    state.deviceCode.isBlank() -> "未配置设备编码，无法同步订单"
                                    state.networkState == NetworkConnectionState.Fault -> state.lastMessage
                                    else -> null
                                },
                            formulaCatalog = state.formulaCatalog,
                            modifier = Modifier.weight(1f),
                        )

                    BottomTab.Settings -> {
                        val settingsViewState = SettingsViewState(
                            networkState = state.networkState,
                            plcState = state.plcState,
                            formulaCatalog = state.formulaCatalog,
                            availableCatalogs = state.availableCatalogs,
                            formulaSourceLabel = state.formulaSourceLabel,
                            communicationConfig = state.communicationConfig,
                            businessUrl = state.businessUrl,
                            managementUrl = state.managementUrl,
                            deviceCode = state.deviceCode,
                            formulaSyncIntervalSeconds = state.formulaSyncIntervalSeconds,
                            deviceConfig = state.deviceConfig,
                            plcPollingSnapshot = state.plcPollingSnapshot,
                            pendingOrders = state.pendingOrders,
                            waitingTransferOrders = state.waitingTransferOrders,
                            completedOrders = state.completedOrders,
                            cancelledOrders = state.cancelledOrders,
                            lastMessage = state.lastMessage,
                            logCount = state.logs.size,
                            logs = state.logs,
                            registrationState = registrationState,
                        )
                        SettingsScreen(
                            state = settingsViewState,
                            onOpenSecondaryDisplay = onOpenSecondaryDisplay,
                            onPreviewSecondaryDisplay = onPreviewSecondaryDisplay,
                            onSaveFormulaParameter = viewModel::saveFormulaParameter,
                            onSelectCatalog = viewModel::selectFormulaCatalog,
                            onUpdateSettings = viewModel::updateSettings,
                            onStartRegistration = viewModel::startRegistration,
                            onSelectDeviceType = viewModel::selectDeviceType,
                            onSelectEquipment = viewModel::selectEquipment,
                            onResetRegistrationState = viewModel::resetRegistrationState,
                            onBeginHeaterActuatorConfigEdit = viewModel::beginHeaterActuatorConfigEdit,
                            onCancelHeaterActuatorConfigEdit = viewModel::cancelHeaterActuatorConfigEdit,
                            onSaveHeaterActuatorConfig = viewModel::saveHeaterActuatorConfig,
                            onRefreshPlcStatus = viewModel::refreshPlcStatus,
                            onSendHeartbeatPulse = viewModel::sendDebugHeartbeatPulse,
                            onSendHeaterTestCommand = viewModel::sendDebugHeaterTestCommand,
                            onSendEmergencyWaterTestCommand = viewModel::sendDebugEmergencyWaterTestCommand,
                            onSendPhaseTestCommand = viewModel::sendDebugPhaseTestCommand,
                            onClearAllLocalOrders = viewModel::clearAllLocalOrdersForDebug,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }

                operatorNotice?.let { notice ->
                    OperatorNoticeBanner(
                        notice = notice,
                        onDismiss = { viewModel.dismissOperatorNotice(notice.id) },
                    )
                }

                BottomTabBar(
                    selectedTab = selectedTab,
                    isWateringActive = isWateringActive,
                    onSelect = { tab ->
                        selectedTab = tab
                    },
                )
            }
        }
    }
}

private fun PlcPollingSnapshot.isWatering(): Boolean =
    phaseActionStateCode == 2 ||
        emergencyActionStateCode == 2 ||
        waterValve0Output ||
        waterValve1Output ||
        waterValve2Output ||
        waterValve3Output ||
        singleWaterValveOutput ||
        chickenOilPumpOutput ||
        bonePastePumpOutput

private const val OPERATOR_NOTICE_AUTO_DISMISS_MS = 12_000L
