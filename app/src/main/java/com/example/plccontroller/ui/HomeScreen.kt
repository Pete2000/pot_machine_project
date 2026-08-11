package com.example.plccontroller.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import com.example.plccontroller.domain.Order

@Composable
internal fun HomeScreen(
    state: MainUiState,
    activeOrder: Order?,
    homeMode: HomeMode,
    isWateringActive: Boolean,
    onHomeModeChange: (HomeMode) -> Unit,
    onRequestWater: () -> Unit,
    onRequestManualWater: (ManualPotMode, List<ManualRecipeOption>) -> Unit,
    onContinueManualPhase: () -> Unit,
    onSetOrderDelayed: (Order, Boolean) -> Unit,
    onPinOrder: (String) -> Unit,
    onOpenPendingWaterOrders: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isCompact = LocalConfiguration.current.screenWidthDp < 600
    var showDelayedOrdersDialog by remember { mutableStateOf(false) }
    var showUrgedOrdersDialog by remember { mutableStateOf(false) }
    var delayCandidateOrder by remember { mutableStateOf<Order?>(null) }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .then(
                        if (isCompact) {
                            Modifier.verticalScroll(rememberScrollState())
                        } else {
                            Modifier
                        },
                    ),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            TopStatusPanel(
                plcState = state.plcState,
                networkState = state.networkState,
                homeMode = homeMode,
                pendingCount = state.pendingOrders.size,
                onHomeModeChange = { if (!isWateringActive) onHomeModeChange(it) },
                isCompact = isCompact,
            )
            when (homeMode) {
                HomeMode.Receive -> {
                    EnhancedDeviceMetricsPanel(
                        plcState = state.plcState,
                        snapshot = state.plcPollingSnapshot,
                        pendingOrders = state.pendingOrders,
                        delayedOrders = state.delayedOrders,
                        urgedOrders = state.urgedOrders,
                        onDelayPillClick = { if (!isWateringActive) showDelayedOrdersDialog = true },
                        onPendingPillClick = { if (!isWateringActive) onOpenPendingWaterOrders() },
                        onUrgePillClick = { if (!isWateringActive) showUrgedOrdersDialog = true },
                        lastMessage = state.lastMessage,
                        isCompact = isCompact,
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    OrderQueuePanel(
                        orders = state.pendingOrders,
                        onOrderClick = { order -> delayCandidateOrder = order },
                        modifier = Modifier.weight(1.0f),
                    )
                    CurrentOrderPanel(
                        order = activeOrder,
                        formulaCatalog = state.formulaCatalog,
                        forcedTopLeftLogicalSlot = state.phaseRotationPrompt?.targetLogicalSlot,
                        currentTopLeftLogicalSlot =
                            state.currentOrder
                                ?.takeIf { current -> current.id == activeOrder?.id }
                                ?.let { state.phaseCurrentTopLeftLogicalSlot },
                        isWateringActive = isWateringActive,
                        onRequestWater = onRequestWater,
                        isCompact = isCompact,
                        modifier = Modifier.weight(1.6f),
                    )
                }
                HomeMode.Manual ->
                    ManualWaterPage(
                        formulaCatalog = state.formulaCatalog,
                        formulaSourceLabel = state.formulaSourceLabel,
                        phaseRotationPrompt = state.phaseRotationPrompt,
                        phaseCurrentTopLeftLogicalSlot = state.phaseCurrentTopLeftLogicalSlot,
                        manualPhaseCompletionToken = state.manualPhaseCompletionToken,
                        isWateringActive = isWateringActive,
                        onRequestManualWater = onRequestManualWater,
                        onContinueManualPhase = onContinueManualPhase,
                        isCompact = isCompact,
                        modifier = if (isCompact) Modifier.fillMaxWidth() else Modifier.weight(1f),
                    )
            }
        }
    }

    if (showDelayedOrdersDialog) {
        DelayedOrdersDialog(
            delayedOrders = state.delayedOrders,
            onDismiss = { showDelayedOrdersDialog = false },
            onStartProcess = { order ->
                onSetOrderDelayed(order, false)
            },
        )
    }

    if (showUrgedOrdersDialog) {
        UrgedOrdersDialog(
            urgedOrders = state.urgedOrders,
            pinnedOrderIds = state.pinnedOrderIds,
            onDismiss = { showUrgedOrdersDialog = false },
            onPinOrder = { orderId ->
                onPinOrder(orderId)
            },
        )
    }

    delayCandidateOrder?.let { order ->
        DelayOrderConfirmDialog(
            order = order,
            onDismiss = { delayCandidateOrder = null },
            onConfirmDelay = {
                onSetOrderDelayed(it, true)
                delayCandidateOrder = null
            },
        )
    }
}
