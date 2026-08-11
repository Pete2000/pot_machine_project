package com.example.plccontroller.domain

import com.example.plccontroller.data.http.TimeCalibrator
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class OrderStatus {
    Pending,
    PendingWater,
    WaitingTransfer,
    Dispatching,
    Running,
    Completed,
    Cancelled,
    Failed,
    Delayed,
    ;

    fun isPendingWaterBucket(): Boolean = this == PendingWater || this == Pending
}

data class Order(
    val id: String,
    val recipeCode: String,
    val quantity: Int,
    val targetTemperature: Int,
    val cookSeconds: Int,
    val spiceLevel: Int,
    val tableCode: String? = null,
    val orderTime: String? = null,
    val potMode: PotMode = PotMode.Single,
    val potBottomName: String? = null,
    val tasteSummary: String? = null,
    val slotSummary: String? = null,
    val status: OrderStatus = OrderStatus.Pending,
    val baseHash: String? = null,
    val ikmsOrder: String? = null,
    val rootPosFoodCode: String? = null,
    val slotCodeSummary: String? = null,
    val operator: String? = null,
    val operation: String? = null,
    val potBottomSummary: String? = null,
    val orderAliases: List<String> = emptyList(),
    val sourceRootId: String? = null,
    val structureStatus: OrderStructureStatus = OrderStructureStatus.Complete,
    val structureMessage: String? = null,
    val expectedSlotCount: Int = 0,
    val attachedBottomCount: Int = 0,
    val rawBottomCandidateCount: Int = 0,
    val missingSlotLabels: List<String> = emptyList(),
    val waterCompletedAt: String? = null,
    val cancelledAt: String? = null,
    val transferCompletedAt: String? = null,
    val statusUpdatedAt: String? = null,
    val isUrged: Boolean = false,
    val urgedAt: String? = null,
)

enum class PotMode {
    Single,
    Split,
    ThreeGrid,
    FourGrid,
}

enum class ManualWaterPotMode {
    Small,
    Single,
    Split,
    ThreeGrid,
    FourGrid,
}

data class ManualWaterPhaseSlot(
    val logicalSlot: Int,
    val waterDurationMs: Long,
    val chickenOilDurationMs: Long = 0L,
    val bonePasteDurationMs: Long = 0L,
    val label: String = "",
    val formulaMissing: Boolean = false,
)

fun ManualWaterPhaseSlot.hasPhysicalAction(): Boolean =
    waterDurationMs > 0L || chickenOilDurationMs > 0L || bonePasteDurationMs > 0L

data class ManualWaterPhaseRequest(
    val mode: ManualWaterPotMode,
    val slots: List<ManualWaterPhaseSlot>,
)

fun Order.operationLabel(): String {
    val basic =
        when (operation) {
            "301" -> "下单"
            "302" -> "催单"
            "303" -> "退单"
            "304" -> "转台"
            else -> operation ?: "下单"
        }
    return if (isUrged && operation != "302") {
        if (basic == "下单") "催单" else "$basic/催单"
    } else {
        basic
    }
}

enum class OrderStructureStatus {
    Complete,
    IncompleteFromApi,
    ParseLostSuspected,
    ParseRecovered,
    Overflow,
}

fun Order.hasBlockingStructureIssue(): Boolean =
    when (structureStatus) {
        OrderStructureStatus.Complete,
        OrderStructureStatus.ParseRecovered,
        -> false
        OrderStructureStatus.IncompleteFromApi,
        OrderStructureStatus.ParseLostSuspected,
        OrderStructureStatus.Overflow,
        -> true
    }

fun Order.structureIssueTitle(): String? =
    when (structureStatus) {
        OrderStructureStatus.Complete,
        OrderStructureStatus.ParseRecovered,
        -> null
        OrderStructureStatus.IncompleteFromApi -> "接口锅底缺失"
        OrderStructureStatus.ParseLostSuspected -> "订单解析异常"
        OrderStructureStatus.Overflow -> "锅底数量异常"
    }

fun Order.structureIssueSummary(): String? {
    if (!hasBlockingStructureIssue()) return null
    val message = structureMessage?.takeIf(String::isNotBlank)
    if (message != null) return message
    val expected = expectedSlotCount.takeIf { it > 0 } ?: return structureIssueTitle()
    val attached = attachedBottomCount
    val raw = rawBottomCandidateCount
    return when (structureStatus) {
        OrderStructureStatus.IncompleteFromApi -> "期望 $expected 个锅底，接口疑似只返回 $raw 个"
        OrderStructureStatus.ParseLostSuspected -> "期望 $expected 个锅底，已挂载 $attached 个，疑似解析漏挂"
        OrderStructureStatus.Overflow -> "期望 $expected 个锅底，实际挂载 $attached 个"
        OrderStructureStatus.Complete,
        OrderStructureStatus.ParseRecovered,
        -> null
    }
}

fun Order.structureDebugSummary(): String? {
    if (structureStatus == OrderStructureStatus.Complete && expectedSlotCount <= 0) return null
    val missing =
        missingSlotLabels
            .joinToString("/")
            .ifBlank { "--" }
    return "structure=${structureStatus.name}, expected=$expectedSlotCount, attached=$attachedBottomCount, raw=$rawBottomCandidateCount, missing=$missing"
}

fun currentOrderTimestampText(): String {
    val calibratedMs = System.currentTimeMillis() + TimeCalibrator.getOffset()
    return SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA).format(Date(calibratedMs))
}

fun Order.withLifecycleStatus(
    status: OrderStatus,
    timestamp: String = currentOrderTimestampText(),
): Order =
    when (status) {
        OrderStatus.Pending,
        OrderStatus.PendingWater,
        ->
            copy(
                status = status,
                waterCompletedAt = null,
                cancelledAt = null,
                transferCompletedAt = null,
                statusUpdatedAt = timestamp,
            )

        OrderStatus.WaitingTransfer ->
            copy(
                status = status,
                waterCompletedAt = timestamp,
                cancelledAt = null,
                transferCompletedAt = null,
                statusUpdatedAt = timestamp,
            )

        OrderStatus.Cancelled,
        OrderStatus.Failed,
        ->
            copy(
                status = status,
                cancelledAt = timestamp,
                statusUpdatedAt = timestamp,
            )

        OrderStatus.Completed ->
            copy(
                status = status,
                transferCompletedAt = timestamp,
                statusUpdatedAt = timestamp,
            )

        OrderStatus.Dispatching,
        OrderStatus.Running,
        OrderStatus.Delayed,
        ->
            copy(
                status = status,
                statusUpdatedAt = timestamp,
            )
    }
