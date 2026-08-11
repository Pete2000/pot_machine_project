package com.example.plccontroller.data.local.room

import com.example.plccontroller.domain.Order
import com.example.plccontroller.domain.OrderStatus
import com.example.plccontroller.domain.OrderStructureStatus
import com.example.plccontroller.domain.PotMode
import org.json.JSONArray
import java.text.SimpleDateFormat
import java.util.Locale

internal fun Order.toEntity(
    previous: OrderEntity? = null,
    now: Long = System.currentTimeMillis(),
): OrderEntity {
    val previousOrder = previous?.toOrder()
    val createdAt = previous?.createdAt ?: parseOrderTimestamp(orderTime) ?: now
    val updatedAt =
        if (previous != null && previousOrder == this) {
            previous.updatedAt
        } else {
            parseOrderTimestamp(statusUpdatedAt) ?: now
        }
    return OrderEntity(
        id = id,
        recipeCode = recipeCode,
        quantity = quantity,
        targetTemperature = targetTemperature,
        cookSeconds = cookSeconds,
        spiceLevel = spiceLevel,
        tableCode = tableCode,
        orderTime = orderTime,
        potMode = potMode.name,
        potBottomName = potBottomName,
        tasteSummary = tasteSummary,
        slotSummary = slotSummary,
        status = status.name,
        baseHash = baseHash,
        ikmsOrder = ikmsOrder,
        sourceRootId = sourceRootId,
        rootPosFoodCode = rootPosFoodCode,
        slotCodeSummary = slotCodeSummary,
        operator = operator,
        operation = operation,
        potBottomSummary = potBottomSummary,
        orderAliasesJson = encodeStrings(orderAliases),
        structureStatus = structureStatus.name,
        structureMessage = structureMessage,
        expectedSlotCount = expectedSlotCount,
        attachedBottomCount = attachedBottomCount,
        rawBottomCandidateCount = rawBottomCandidateCount,
        missingSlotLabelsJson = encodeStrings(missingSlotLabels),
        waterCompletedAt = waterCompletedAt,
        cancelledAt = cancelledAt,
        transferCompletedAt = transferCompletedAt,
        statusUpdatedAt = statusUpdatedAt,
        isUrged = isUrged,
        urgedAt = urgedAt,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )
}

internal fun OrderEntity.toOrder(): Order =
    Order(
        id = id,
        recipeCode = recipeCode,
        quantity = quantity,
        targetTemperature = targetTemperature,
        cookSeconds = cookSeconds,
        spiceLevel = spiceLevel,
        tableCode = tableCode,
        orderTime = orderTime,
        potMode = enumValueOrDefault(potMode, PotMode.Single),
        potBottomName = potBottomName,
        tasteSummary = tasteSummary,
        slotSummary = slotSummary,
        status = enumValueOrDefault(status, OrderStatus.PendingWater),
        baseHash = baseHash,
        ikmsOrder = ikmsOrder,
        rootPosFoodCode = rootPosFoodCode,
        slotCodeSummary = slotCodeSummary,
        operator = operator,
        operation = operation,
        potBottomSummary = potBottomSummary,
        orderAliases = decodeStrings(orderAliasesJson),
        sourceRootId = sourceRootId,
        structureStatus = enumValueOrDefault(structureStatus, OrderStructureStatus.Complete),
        structureMessage = structureMessage,
        expectedSlotCount = expectedSlotCount,
        attachedBottomCount = attachedBottomCount,
        rawBottomCandidateCount = rawBottomCandidateCount,
        missingSlotLabels = decodeStrings(missingSlotLabelsJson),
        waterCompletedAt = waterCompletedAt,
        cancelledAt = cancelledAt,
        transferCompletedAt = transferCompletedAt,
        statusUpdatedAt = statusUpdatedAt,
        isUrged = isUrged,
        urgedAt = urgedAt,
    )

internal fun List<Order>.toAliasEntities(now: Long = System.currentTimeMillis()): List<OrderAliasEntity> =
    flatMap { order ->
        (order.orderAliases + order.ikmsOrder)
            .mapNotNull { it?.trim()?.takeIf(String::isNotBlank) }
            .distinct()
            .map { alias ->
                OrderAliasEntity(
                    alias = alias,
                    orderId = order.id,
                    aliasType = if (alias == order.ikmsOrder) "IKMS" else "UNKNOWN",
                    createdAt = now,
                )
            }
    }.distinctBy { it.alias to it.orderId }

internal fun buildOrderAuditEvents(
    before: List<Order>,
    after: List<Order>,
    now: Long = System.currentTimeMillis(),
    importMode: Boolean = false,
): List<OrderEventEntity> {
    val previousById = before.associateBy(Order::id)
    return buildList {
        after.forEach { order ->
            val previous = previousById[order.id]
            if (previous == null) {
                add(
                    order.toAuditEvent(
                        eventType = if (importMode) "ORDER_IMPORTED" else "ORDER_RECEIVED",
                        fromStatus = null,
                        toStatus = order.status.name,
                        message = if (importMode) "从 SharedPreferences 导入订单" else "收到 POS 订单",
                        now = now,
                    ),
                )
                return@forEach
            }
            if (previous.status != order.status) {
                add(
                    order.toAuditEvent(
                        eventType = order.status.eventType(),
                        fromStatus = previous.status.name,
                        toStatus = order.status.name,
                        message = "订单状态 ${previous.status.name} -> ${order.status.name}",
                        now = now,
                    ),
                )
            }
            if (previous.tableCode != order.tableCode) {
                add(
                    order.toAuditEvent(
                        eventType = "ORDER_TRANSFERRED",
                        fromStatus = previous.status.name,
                        toStatus = order.status.name,
                        message = "桌号 ${previous.tableCode.orEmpty()} -> ${order.tableCode.orEmpty()}",
                        now = now,
                    ),
                )
            }
            if (!previous.isUrged && order.isUrged) {
                add(
                    order.toAuditEvent(
                        eventType = "ORDER_URGED",
                        fromStatus = previous.status.name,
                        toStatus = order.status.name,
                        message = "订单收到催单事件",
                        now = now,
                    ),
                )
            }
            if (previous.structureStatus != order.structureStatus) {
                add(
                    order.toAuditEvent(
                        eventType =
                            if (order.structureStatus == OrderStructureStatus.Complete ||
                                order.structureStatus == OrderStructureStatus.ParseRecovered
                            ) {
                                "ORDER_STRUCTURE_RECOVERED"
                            } else {
                                "ORDER_STRUCTURE_BLOCKED"
                            },
                        fromStatus = previous.status.name,
                        toStatus = order.status.name,
                        message = order.structureMessage ?: "订单结构状态变更为 ${order.structureStatus.name}",
                        now = now,
                    ),
                )
            }
        }
    }
}

private fun Order.toAuditEvent(
    eventType: String,
    fromStatus: String?,
    toStatus: String?,
    message: String,
    now: Long,
): OrderEventEntity =
    OrderEventEntity(
        orderId = id,
        eventType = eventType,
        operation = operation,
        fromStatus = fromStatus,
        toStatus = toStatus,
        tableCode = tableCode,
        sourceOrderAlias = ikmsOrder ?: orderAliases.firstOrNull(),
        message = message,
        createdAt = parseOrderTimestamp(statusUpdatedAt ?: urgedAt ?: orderTime) ?: now,
    )

private fun OrderStatus.eventType(): String =
    when (this) {
        OrderStatus.Pending,
        OrderStatus.PendingWater,
        -> "ORDER_RESTORED_TO_PENDING"
        OrderStatus.Dispatching,
        OrderStatus.Running,
        -> "ORDER_DISPATCH_STARTED"
        OrderStatus.WaitingTransfer -> "ORDER_WAITING_TRANSFER"
        OrderStatus.Completed -> "ORDER_TRANSFER_CONFIRMED"
        OrderStatus.Cancelled -> "ORDER_CANCELLED"
        OrderStatus.Failed -> "ORDER_FAILED"
        OrderStatus.Delayed -> "ORDER_DELAYED"
    }

private fun encodeStrings(values: List<String>): String = JSONArray().also { array -> values.distinct().forEach(array::put) }.toString()

private fun decodeStrings(payload: String): List<String> {
    val array = runCatching { JSONArray(payload) }.getOrNull() ?: return emptyList()
    return buildList {
        for (index in 0 until array.length()) {
            array
                .optString(index)
                .trim()
                .takeIf(String::isNotBlank)
                ?.let(::add)
        }
    }.distinct()
}

private fun parseOrderTimestamp(value: String?): Long? {
    val text = value?.trim().orEmpty()
    if (text.isBlank()) return null
    return runCatching {
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA).parse(text)?.time
    }.getOrNull()
}

private inline fun <reified T : Enum<T>> enumValueOrDefault(
    value: String,
    default: T,
): T =
    runCatching {
        enumValueOf<T>(value)
    }.getOrDefault(default)
