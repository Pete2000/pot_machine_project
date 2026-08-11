package com.example.plccontroller.runtime

import com.example.plccontroller.domain.FormulaCatalog
import com.example.plccontroller.domain.Order
import com.example.plccontroller.domain.PlcCommunicationConfig
import com.example.plccontroller.domain.PlcConnectionState
import com.example.plccontroller.domain.PlcPollingSnapshot

enum class NetworkConnectionState {
    Idle,
    Syncing,
    Online,
    Fault,
}

enum class FaultType {
    CommunicationDisconnect,
    EmergencyStop,
}

data class FaultMeltdownState(
    val active: Boolean = false,
    val type: FaultType? = null,
    val interruptedOrderId: String? = null,
)

enum class TransferDisplayMode {
    OldestFirst,
    LatestFirst,
}

enum class TransferConfirmSource {
    MainScreen,
    SecondaryDisplay,
    PhysicalButton,
}

enum class PhaseFlowSource {
    Manual,
    Order,
}

enum class PhasePromptKind {
    Rotation,
    FormulaMissing,
}

data class PhaseRotationPrompt(
    val source: PhaseFlowSource,
    val kind: PhasePromptKind = PhasePromptKind.Rotation,
    val title: String,
    val message: String,
    val confirmText: String = "已转到左上，开始执行",
    val cancelText: String = "暂不处理",
    val targetLogicalSlot: Int? = null,
)

data class OrderPhaseReservation(
    val orderId: String,
    val promptKind: PhasePromptKind,
    val physicalActionStarted: Boolean,
)

data class OperatorAlert(
    val id: String,
    val title: String,
    val message: String,
)

enum class OperatorNoticeLevel {
    Info,
    Warning,
    Error,
}

data class OperatorNotice(
    val id: String,
    val level: OperatorNoticeLevel,
    val message: String,
    val relatedOrderId: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
)

enum class TransferCardPosition(
    val cardNumber: Int,
) {
    TopRight(1),
    TopCenter(2),
    TopLeft(3),
    BottomRight(4),
    BottomLeft(5),
    ;

    companion object {
        val confirmationSequence =
            listOf(
                BottomLeft,
                BottomRight,
                TopLeft,
                TopCenter,
                TopRight,
            )

        val displayOrder = entries.sortedBy(TransferCardPosition::cardNumber)
    }
}

data class TransferCardModel(
    val position: TransferCardPosition,
    val order: Order? = null,
) {
    val isConfirmSlot: Boolean
        get() = position == TransferCardPosition.BottomLeft
}

data class TransferDeckState(
    val mode: TransferDisplayMode = TransferDisplayMode.OldestFirst,
    val cards: List<TransferCardModel> = emptyList(),
    val totalWaitingCount: Int = 0,
    val overflowCount: Int = 0,
    val previewOrder: Order? = null,
    val confirmOrder: Order? = null,
    val hasDeferredConfirmationBacklog: Boolean = false,
) {
    companion object {
        @Suppress("MaxLineLength")
        fun empty(mode: TransferDisplayMode = TransferDisplayMode.OldestFirst): TransferDeckState = build(waitingTransferOrders = emptyList(), mode = mode)

        fun build(
            waitingTransferOrders: List<Order>,
            mode: TransferDisplayMode,
        ): TransferDeckState {
            val prioritizedOrders =
                when (mode) {
                    TransferDisplayMode.OldestFirst -> waitingTransferOrders
                    TransferDisplayMode.LatestFirst -> waitingTransferOrders.asReversed()
                }
            val assignments =
                TransferCardPosition.confirmationSequence
                    .zip(prioritizedOrders)
                    .toMap()
            val cards =
                TransferCardPosition.displayOrder.map { position ->
                    TransferCardModel(
                        position = position,
                        order = assignments[position],
                    )
                }
            return TransferDeckState(
                mode = mode,
                cards = cards,
                totalWaitingCount = waitingTransferOrders.size,
                overflowCount =
                    (waitingTransferOrders.size - TransferCardPosition.displayOrder.size)
                        .coerceAtLeast(0),
                previewOrder = prioritizedOrders.getOrNull(TransferCardPosition.displayOrder.size),
                confirmOrder = assignments[TransferCardPosition.BottomLeft],
                hasDeferredConfirmationBacklog =
                    mode == TransferDisplayMode.LatestFirst &&
                        waitingTransferOrders.size > TransferCardPosition.displayOrder.size,
            )
        }
    }
}

data class MaintenanceState(
    val active: Boolean = false,
    val reason: String? = null,
)

data class SecondaryDisplayDemoState(
    val enabled: Boolean = false,
    val waitingTransferOrders: List<Order> = emptyList(),
    val completedOrders: List<Order> = emptyList(),
)

data class MachineRuntimeState(
    val plcState: PlcConnectionState = PlcConnectionState.Disconnected,
    val networkState: NetworkConnectionState = NetworkConnectionState.Idle,
    val automationEnabled: Boolean = false,
    val communicationConfig: PlcCommunicationConfig? = null,
    val plcPollingSnapshot: PlcPollingSnapshot = PlcPollingSnapshot(),
    val currentOrder: Order? = null,
    val pinnedOrderIds: Set<String> = emptySet(),
    val pendingOrders: List<Order> = emptyList(),
    val delayedOrders: List<Order> = emptyList(),
    val waitingTransferOrders: List<Order> = emptyList(),
    val completedOrders: List<Order> = emptyList(),
    val cancelledOrders: List<Order> = emptyList(),
    val formulaCatalog: FormulaCatalog? = null,
    val formulaSourceLabel: String = "Local Default",
    val availableCatalogs: List<FormulaCatalog> = emptyList(),
    val manualPhasePrompt: String? = null,
    val manualPhaseTargetLogicalSlot: Int? = null,
    val phaseRotationPrompt: PhaseRotationPrompt? = null,
    val orderPhaseReservation: OrderPhaseReservation? = null,
    val phaseCurrentTopLeftLogicalSlot: Int = 1,
    val manualPhaseCompletionToken: Int = 0,
    val transferDisplayMode: TransferDisplayMode = TransferDisplayMode.OldestFirst,
    val transferDeck: TransferDeckState = TransferDeckState.empty(),
    val secondaryDisplayDemoState: SecondaryDisplayDemoState = SecondaryDisplayDemoState(),
    val maintenanceState: MaintenanceState = MaintenanceState(),
    val heaterActuatorConfigEditActive: Boolean = false,
    val faultMeltdownState: FaultMeltdownState = FaultMeltdownState(),
    val operatorAlerts: List<OperatorAlert> = emptyList(),
    val acknowledgedOperatorAlertIds: Set<String> = emptySet(),
    val operatorNotice: OperatorNotice? = null,
    val lastMessage: String = "System Idle",
    val logs: List<String> = listOf("Application started"),
) {
    fun secondaryWaitingTransferOrders(): List<Order> =
        if (secondaryDisplayDemoState.enabled) {
            secondaryDisplayDemoState.waitingTransferOrders
        } else {
            waitingTransferOrders
        }

    fun secondaryCompletedOrders(): List<Order> =
        if (secondaryDisplayDemoState.enabled) {
            secondaryDisplayDemoState.completedOrders
        } else {
            completedOrders
        }

    fun withRefreshedTransferDeck(): MachineRuntimeState =
        copy(
            transferDeck =
                TransferDeckState.build(
                    waitingTransferOrders = secondaryWaitingTransferOrders(),
                    mode = transferDisplayMode,
                ),
        )
}
