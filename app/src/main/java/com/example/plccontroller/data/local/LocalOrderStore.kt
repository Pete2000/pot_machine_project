package com.example.plccontroller.data.local

import android.content.Context
import com.example.plccontroller.domain.Order
import com.example.plccontroller.domain.OrderStatus
import com.example.plccontroller.domain.currentOrderTimestampText
import com.example.plccontroller.domain.hasBlockingStructureIssue
import com.example.plccontroller.domain.withLifecycleStatus

class LocalOrderStore internal constructor(
    private val storage: LocalOrderKeyValueStorage,
    private val codec: LocalOrderCodec = JsonLocalOrderCodec,
    private val maxOrders: Int? = MAX_LOCAL_ORDERS,
) {
    constructor(context: Context) : this(
        SharedPreferencesOrderKeyValueStorage(
            context.getSharedPreferences("local_order_store", Context.MODE_PRIVATE),
        ),
    )

    @Synchronized
    fun insertIgnore(order: Order): Boolean {
        val orders = loadOrdersMutable()
        val exactIndex = orders.indexOfFirst { it.id == order.id }
        if (exactIndex >= 0) {
            val existing = orders[exactIndex]
            if (existing.shouldAcceptStructureRefresh(order)) {
                orders[exactIndex] = existing.mergeIncomingOrder(order)
                saveOrders(orders)
                return true
            }
            return false
        }
        val businessIndex = orders.indexOfFirst { it.sameSourceRootAs(order) }
        if (businessIndex >= 0) {
            val existing = orders[businessIndex]
            if (existing.shouldAcceptStructureRefresh(order)) {
                orders[businessIndex] = existing.mergeIncomingOrder(order)
                saveOrders(orders)
                return true
            }
            val isExistingActive =
                existing.status != OrderStatus.Completed &&
                    existing.status != OrderStatus.Cancelled &&
                    existing.status != OrderStatus.Failed
            if (isExistingActive) {
                orders[businessIndex] = existing.mergeIncomingOrder(order)
                saveOrders(orders)
                return true
            }
            return false
        }
        orders += order
        saveOrders(orders)
        return true
    }

    @Synchronized
    fun cancelPendingByBaseHash(
        baseHash: String,
        cancelEventId: String? = null,
        eventTime: String? = null,
    ): CancelResult {
        val orders = loadOrdersMutable()

        // 1. Idempotency check: if there is already a Cancelled order that matches cancelEventId, do nothing
        if (!cancelEventId.isNullOrBlank()) {
            val alreadyCancelled =
                orders.any { order ->
                    order.status == OrderStatus.Cancelled &&
                        (order.sourceRootId == cancelEventId || order.orderAliases.contains(cancelEventId))
                }
            if (alreadyCancelled) {
                return CancelResult.Canceled
            }
        }

        // 2. Find matched active orders
        val matched =
            orders
                .filter { order ->
                    order.baseHash == baseHash &&
                        order.status != OrderStatus.Cancelled &&
                        order.status != OrderStatus.Failed &&
                        isChronologicallyValid(order, eventTime)
                }.eventFifo()
        if (matched.isEmpty()) {
            return CancelResult.NotFound
        }

        // 3. If cancelEventId is specified, we ONLY cancel ONE of the matched orders to avoid multi-hitting
        val toCancel =
            if (!cancelEventId.isNullOrBlank()) {
                matched
                    .firstOrNull()
                    ?.takeIf { it.isAutomaticallyCancellable() }
                    ?.let(::listOf)
                    .orEmpty()
            } else {
                matched
            }

        if (toCancel.isEmpty()) {
            return CancelResult.RequiresManualIntervention
        }

        var changed = false
        var blocked = false
        val updated =
            orders.map { order ->
                if (order !in toCancel) {
                    order
                } else if (order.status == OrderStatus.Pending ||
                    order.status == OrderStatus.PendingWater ||
                    order.status == OrderStatus.Delayed
                ) {
                    changed = true
                    val newAliases =
                        if (!cancelEventId.isNullOrBlank() && !order.orderAliases.contains(cancelEventId)) {
                            order.orderAliases + cancelEventId
                        } else {
                            order.orderAliases
                        }
                    order.copy(orderAliases = newAliases).withLifecycleStatus(OrderStatus.Cancelled)
                } else {
                    blocked = true
                    order
                }
            }
        if (changed) {
            saveOrders(updated)
        }
        return when {
            blocked -> CancelResult.RequiresManualIntervention
            changed -> CancelResult.Canceled
            else -> CancelResult.NotFound
        }
    }

    @Synchronized
    fun cancelPendingByOrderAliases(
        orderAliases: List<String>,
        recipeCode: String,
        cancelEventId: String? = null,
        eventTime: String? = null,
    ): CancelResult {
        val aliases = mergeAliases(orderAliases)
        if (aliases.isEmpty()) {
            return CancelResult.NotFound
        }
        val orders = loadOrdersMutable()

        // 1. Idempotency check: if there is already a Cancelled order that matches cancelEventId, do nothing
        if (!cancelEventId.isNullOrBlank()) {
            val alreadyCancelled =
                orders.any { order ->
                    order.status == OrderStatus.Cancelled &&
                        (order.sourceRootId == cancelEventId || order.orderAliases.contains(cancelEventId))
                }
            if (alreadyCancelled) {
                return CancelResult.Canceled
            }
        }

        // 2. Find matched active orders
        val matched =
            orders
                .filter { order ->
                    order.matchesAnyOrderAlias(aliases) &&
                        (order.recipeCode == recipeCode || order.rootPosFoodCode == recipeCode) &&
                        order.status != OrderStatus.Cancelled &&
                        order.status != OrderStatus.Failed &&
                        isChronologicallyValid(order, eventTime)
                }.eventFifo()
        if (matched.isEmpty()) {
            return CancelResult.NotFound
        }

        // 3. If cancelEventId is specified, we ONLY cancel ONE of the matched orders to avoid multi-hitting
        val toCancel =
            if (!cancelEventId.isNullOrBlank()) {
                matched
                    .firstOrNull()
                    ?.takeIf { it.isAutomaticallyCancellable() }
                    ?.let(::listOf)
                    .orEmpty()
            } else {
                matched
            }

        if (toCancel.isEmpty()) {
            return CancelResult.RequiresManualIntervention
        }

        var changed = false
        var blocked = false
        val updated =
            orders.map { order ->
                if (order !in toCancel) {
                    order
                } else if (order.status == OrderStatus.Pending ||
                    order.status == OrderStatus.PendingWater ||
                    order.status == OrderStatus.Delayed
                ) {
                    changed = true
                    val newAliases =
                        if (!cancelEventId.isNullOrBlank() && !order.orderAliases.contains(cancelEventId)) {
                            order.orderAliases + cancelEventId
                        } else {
                            order.orderAliases
                        }
                    order.copy(orderAliases = newAliases).withLifecycleStatus(OrderStatus.Cancelled)
                } else {
                    blocked = true
                    order
                }
            }
        if (changed) {
            saveOrders(updated)
        }
        return when {
            blocked -> CancelResult.RequiresManualIntervention
            changed -> CancelResult.Canceled
            else -> CancelResult.NotFound
        }
    }

    @Synchronized
    fun updateTableByBaseHash(
        baseHash: String,
        newBaseHash: String? = null,
        transferEventId: String? = null,
        newTableCode: String,
        newOrderAliases: List<String>,
        eventTime: String? = null,
    ): Int {
        val orders = loadOrdersMutable()

        // 1. Idempotency check: if transferEventId is already processed, skip
        if (!transferEventId.isNullOrBlank()) {
            val alreadyTransferred =
                orders.count { order ->
                    order.orderAliases.contains(transferEventId) || order.sourceRootId == transferEventId
                }
            if (alreadyTransferred > 0) {
                return alreadyTransferred
            }
        }

        // 2. Find matching active orders (non-terminal statuses)
        val matched =
            orders
                .filter { order ->
                    order.baseHash == baseHash &&
                        order.status != OrderStatus.Completed &&
                        order.status != OrderStatus.Cancelled &&
                        order.status != OrderStatus.Failed &&
                        isChronologicallyValid(order, eventTime)
                }.eventFifo()

        if (matched.isEmpty()) {
            return 0
        }

        // 3. Single targeting vs batch default
        val toTransfer =
            if (!transferEventId.isNullOrBlank()) {
                matched.firstOrNull()?.let { listOf(it) } ?: emptyList()
            } else {
                matched
            }

        var count = 0
        val updated =
            orders.map { order ->
                if (order in toTransfer) {
                    count += 1
                    order.copy(
                        baseHash = newBaseHash ?: order.baseHash,
                        tableCode = newTableCode,
                        ikmsOrder = newOrderAliases.firstOrNull() ?: order.ikmsOrder,
                        operation = "304",
                        statusUpdatedAt = eventTime ?: order.statusUpdatedAt,
                        orderAliases =
                            mergeAliases(
                                order.orderAliases +
                                    order.ikmsOrder +
                                    newOrderAliases +
                                    listOfNotNull(transferEventId),
                            ),
                    )
                } else {
                    order
                }
            }
        if (count > 0) {
            saveOrders(updated)
        }
        return count
    }

    @Synchronized
    fun updateTableByOrderAlias(
        previousOrderAlias: String,
        recipeCode: String,
        newBaseHash: String? = null,
        transferEventId: String? = null,
        newTableCode: String,
        newOrderAliases: List<String>,
        eventTime: String? = null,
    ): Int {
        val alias = previousOrderAlias.trim()
        if (alias.isBlank() || newTableCode.isBlank()) {
            return 0
        }
        val orders = loadOrdersMutable()

        // 1. Idempotency check: if transferEventId is already processed, skip
        if (!transferEventId.isNullOrBlank()) {
            val alreadyTransferred =
                orders.count { order ->
                    order.orderAliases.contains(transferEventId) || order.sourceRootId == transferEventId
                }
            if (alreadyTransferred > 0) {
                return alreadyTransferred
            }
        }

        // 2. Find matching active orders
        val matched =
            orders
                .filter { order ->
                    order.ikmsOrder == alias &&
                        (order.recipeCode == recipeCode || order.rootPosFoodCode == recipeCode) &&
                        order.status != OrderStatus.Completed &&
                        order.status != OrderStatus.Cancelled &&
                        order.status != OrderStatus.Failed &&
                        isChronologicallyValid(order, eventTime)
                }.eventFifo()

        if (matched.isEmpty()) {
            return 0
        }

        // 3. Single targeting vs batch default
        val toTransfer =
            if (!transferEventId.isNullOrBlank()) {
                matched.firstOrNull()?.let { listOf(it) } ?: emptyList()
            } else {
                matched
            }

        var count = 0
        val updated =
            orders.map { order ->
                if (order in toTransfer) {
                    count += 1
                    order.copy(
                        baseHash = newBaseHash ?: order.baseHash,
                        tableCode = newTableCode,
                        ikmsOrder = newOrderAliases.firstOrNull() ?: order.ikmsOrder,
                        operation = "304",
                        statusUpdatedAt = eventTime ?: order.statusUpdatedAt,
                        orderAliases =
                            mergeAliases(
                                order.orderAliases +
                                    order.ikmsOrder +
                                    previousOrderAlias +
                                    newOrderAliases +
                                    listOfNotNull(transferEventId),
                            ),
                    )
                } else {
                    order
                }
            }
        if (count > 0) {
            saveOrders(updated)
        }
        return count
    }

    @Synchronized
    fun markUrgedByBaseHash(
        baseHash: String,
        urgeEventId: String? = null,
        eventTime: String? = null,
    ): Int {
        val orders = loadOrdersMutable()

        // 1. Idempotency check: if urgeEventId is already processed, skip
        if (!urgeEventId.isNullOrBlank()) {
            val alreadyUrged =
                orders.count { order ->
                    order.isUrged && (order.sourceRootId == urgeEventId || order.orderAliases.contains(urgeEventId))
                }
            if (alreadyUrged > 0) {
                return alreadyUrged
            }
        }

        // 2. Find matching active orders
        val matched =
            orders
                .filter { order ->
                    order.baseHash == baseHash &&
                        order.status != OrderStatus.Completed &&
                        order.status != OrderStatus.Cancelled &&
                        order.status != OrderStatus.Failed &&
                        isChronologicallyValid(order, eventTime)
                }.eventFifo()

        if (matched.isEmpty()) {
            return 0
        }

        // 3. Single targeting vs batch default
        val toUrge =
            if (!urgeEventId.isNullOrBlank()) {
                (matched.firstOrNull { !it.isUrged } ?: matched.firstOrNull())?.let { listOf(it) } ?: emptyList()
            } else {
                matched
            }

        var count = 0
        val updated =
            orders.map { order ->
                if (order in toUrge) {
                    count += 1
                    val isDelayed = order.status == OrderStatus.Delayed
                    order.copy(
                        isUrged = true,
                        urgedAt = eventTime ?: currentOrderTimestampText(),
                        status = if (isDelayed) OrderStatus.PendingWater else order.status,
                        statusUpdatedAt = eventTime ?: order.statusUpdatedAt,
                        orderAliases = mergeAliases(order.orderAliases + listOfNotNull(urgeEventId)),
                    )
                } else {
                    order
                }
            }
        if (count > 0) {
            saveOrders(updated)
        }
        return count
    }

    @Synchronized
    fun markUrgedByOrderAliases(
        orderAliases: List<String>,
        recipeCode: String,
        urgeEventId: String? = null,
        eventTime: String? = null,
    ): Int {
        val aliases = mergeAliases(orderAliases)
        if (aliases.isEmpty()) {
            return 0
        }
        val orders = loadOrdersMutable()

        // 1. Idempotency check: if urgeEventId is already processed, skip
        if (!urgeEventId.isNullOrBlank()) {
            val alreadyUrged =
                orders.count { order ->
                    order.isUrged && (order.sourceRootId == urgeEventId || order.orderAliases.contains(urgeEventId))
                }
            if (alreadyUrged > 0) {
                return alreadyUrged
            }
        }

        // 2. Find matching active orders
        val matched =
            orders
                .filter { order ->
                    order.matchesAnyOrderAlias(aliases) &&
                        (order.recipeCode == recipeCode || order.rootPosFoodCode == recipeCode) &&
                        order.status != OrderStatus.Completed &&
                        order.status != OrderStatus.Cancelled &&
                        order.status != OrderStatus.Failed &&
                        isChronologicallyValid(order, eventTime)
                }.eventFifo()
        if (matched.isEmpty()) {
            return 0
        }

        // 3. Single targeting vs batch fallback
        if (!urgeEventId.isNullOrBlank()) {
            val toUrge = matched.firstOrNull { !it.isUrged } ?: matched.first()
            var count = 0
            val updated =
                orders.map { order ->
                    if (order.id == toUrge.id) {
                        count += 1
                        val isDelayed = order.status == OrderStatus.Delayed
                        order.copy(
                            isUrged = true,
                            urgedAt = eventTime ?: currentOrderTimestampText(),
                            status = if (isDelayed) OrderStatus.PendingWater else order.status,
                            statusUpdatedAt = eventTime ?: order.statusUpdatedAt,
                            orderAliases = mergeAliases(order.orderAliases + urgeEventId),
                        )
                    } else {
                        order
                    }
                }
            if (count > 0) {
                saveOrders(updated)
            }
            return count
        }

        // Legacy fallback when urgeEventId is null or blank (original code exactly as-is)
        val groups = matched.groupBy { it.baseHash }
        val primaries =
            groups.values.map { list ->
                list.maxWith(
                    compareBy<Order> { it.status.aliasMergePriority() }
                        .thenBy { mergeAliases(it.orderAliases + it.ikmsOrder).size }
                        .thenBy { it.orderTime.orEmpty() },
                )
            }
        val primaryIds = primaries.map { it.id }.toSet()
        val matchedIds = matched.map(Order::id).toSet()
        val mergedAliases =
            mergeAliases(
                aliases + matched.flatMap { order -> order.orderAliases + order.ikmsOrder },
            )

        val updated =
            orders.mapNotNull { order ->
                when {
                    order.id in primaryIds -> {
                        val matchingGroup = groups[order.baseHash].orEmpty().asReversed()
                        val newestTable =
                            matchingGroup.firstNotNullOfOrNull {
                                it.tableCode?.takeIf(String::isNotBlank)
                            }
                        val newestOperator =
                            matchingGroup.firstNotNullOfOrNull {
                                it.operator?.takeIf(String::isNotBlank)
                            }
                        val newestBottomSummary =
                            matchingGroup.firstNotNullOfOrNull {
                                it.potBottomSummary?.takeIf(String::isNotBlank)
                            }
                        val newestTasteSummary =
                            matchingGroup.firstNotNullOfOrNull {
                                it.tasteSummary?.takeIf(String::isNotBlank)
                            }
                        val newestSlotSummary =
                            matchingGroup.firstNotNullOfOrNull {
                                it.slotSummary?.takeIf(String::isNotBlank)
                            }
                        val newestSlotCodeSummary =
                            matchingGroup.firstNotNullOfOrNull {
                                it.slotCodeSummary?.takeIf(String::isNotBlank)
                            }
                        val newestPotBottomName =
                            matchingGroup.firstNotNullOfOrNull {
                                it.potBottomName?.takeIf(String::isNotBlank)
                            }
                        val newestIkmsOrder =
                            matchingGroup.firstNotNullOfOrNull {
                                it.ikmsOrder?.takeIf(String::isNotBlank)
                            }
                        val newestRootFoodCode =
                            matchingGroup.firstNotNullOfOrNull {
                                it.rootPosFoodCode?.takeIf(String::isNotBlank)
                            }

                        val isDelayed = order.status == OrderStatus.Delayed
                        order.copy(
                            tableCode = newestTable ?: order.tableCode,
                            operator = newestOperator ?: order.operator,
                            isUrged = true,
                            urgedAt = eventTime ?: currentOrderTimestampText(),
                            status = if (isDelayed) OrderStatus.PendingWater else order.status,
                            statusUpdatedAt = eventTime ?: order.statusUpdatedAt,
                            potBottomSummary = newestBottomSummary ?: order.potBottomSummary,
                            tasteSummary = newestTasteSummary ?: order.tasteSummary,
                            slotSummary = newestSlotSummary ?: order.slotSummary,
                            slotCodeSummary = newestSlotCodeSummary ?: order.slotCodeSummary,
                            potBottomName = newestPotBottomName ?: order.potBottomName,
                            ikmsOrder = newestIkmsOrder ?: order.ikmsOrder,
                            rootPosFoodCode = newestRootFoodCode ?: order.rootPosFoodCode,
                            orderAliases = mergedAliases,
                        )
                    }
                    order.id in matchedIds -> {
                        if (order.status == OrderStatus.Completed ||
                            order.status == OrderStatus.Cancelled ||
                            order.status == OrderStatus.Failed
                        ) {
                            null
                        } else {
                            val isDelayed = order.status == OrderStatus.Delayed
                            order.copy(
                                isUrged = true,
                                urgedAt = eventTime ?: currentOrderTimestampText(),
                                status = if (isDelayed) OrderStatus.PendingWater else order.status,
                                statusUpdatedAt = eventTime ?: order.statusUpdatedAt,
                            )
                        }
                    }
                    else -> order
                }
            }
        saveOrders(updated)
        return matched.size
    }

    @Synchronized
    fun markDispatching(orderId: String) {
        updateOrder(orderId) { it.withLifecycleStatus(OrderStatus.Dispatching) }
    }

    @Synchronized
    fun markPendingDelivery(orderId: String) {
        updateOrder(orderId) { it.withLifecycleStatus(OrderStatus.WaitingTransfer) }
    }

    @Synchronized
    fun markCompleted(orderId: String) {
        updateOrder(orderId) { it.withLifecycleStatus(OrderStatus.Completed) }
    }

    @Synchronized
    fun markCancelled(orderId: String) {
        updateOrder(orderId) { it.withLifecycleStatus(OrderStatus.Cancelled) }
    }

    @Synchronized
    fun markFailed(orderId: String) {
        updateOrder(orderId) { it.withLifecycleStatus(OrderStatus.Failed) }
    }

    @Synchronized
    fun markDelayed(orderId: String) {
        updateOrder(orderId) { it.withLifecycleStatus(OrderStatus.Delayed) }
    }

    @Synchronized
    fun markPendingWater(orderId: String) {
        updateOrder(orderId) { it.withLifecycleStatus(OrderStatus.PendingWater) }
    }

    @Synchronized
    fun activeOrders(): List<Order> =
        loadOrders()
            .filterNot { it.status == OrderStatus.Completed || it.status == OrderStatus.Cancelled }
            .sortedWith(
                compareBy<Order> { it.status != OrderStatus.PendingWater }
                    .thenBy { it.orderTime.orEmpty() }
                    .thenBy { it.id },
            )

    @Synchronized
    fun allOrders(): List<Order> = loadOrders()

    @Synchronized
    fun clearAllOrdersForDebug() {
        saveOrders(emptyList())
    }

    private fun updateOrder(
        orderId: String,
        updater: (Order) -> Order,
    ) {
        val orders = loadOrdersMutable()
        val updated =
            orders.map { order ->
                if (order.id == orderId) updater(order) else order
            }
        saveOrders(updated)
    }

    private fun loadOrdersMutable(): MutableList<Order> = loadOrders().toMutableList()

    private fun loadOrders(): List<Order> {
        val payload = storage.getString(KEY_ORDERS) ?: return emptyList()
        return codec.decode(payload)
    }

    private fun saveOrders(orders: List<Order>) {
        val trimmed = maxOrders?.let(orders::takeLast) ?: orders
        storage.putString(KEY_ORDERS, codec.encode(trimmed))
    }

    private fun Order.sameSourceRootAs(other: Order): Boolean {
        val source = sourceRootId?.takeIf(String::isNotBlank) ?: return false
        return source == other.sourceRootId &&
            rootPosFoodCode == other.rootPosFoodCode &&
            mergeAliases(orderAliases + ikmsOrder)
                .intersect(mergeAliases(other.orderAliases + other.ikmsOrder).toSet())
                .isNotEmpty()
    }

    private fun Order.shouldAcceptStructureRefresh(incoming: Order): Boolean {
        if (status == OrderStatus.Dispatching ||
            status == OrderStatus.Running ||
            status == OrderStatus.WaitingTransfer ||
            status == OrderStatus.Completed ||
            status == OrderStatus.Cancelled ||
            status == OrderStatus.Failed
        ) {
            return false
        }
        if (hasBlockingStructureIssue() && !incoming.hasBlockingStructureIssue()) {
            return true
        }
        return hasBlockingStructureIssue() &&
            incoming.hasBlockingStructureIssue() &&
            structureStatus != incoming.structureStatus
    }

    private fun getMergedOperation(
        localOp: String?,
        incomingOp: String?,
    ): String? {
        if (localOp == "303" || incomingOp == "303") return "303"
        val localIsSpecial = localOp == "302" || localOp == "304"
        val incomingIsBasic = incomingOp == "301" || incomingOp.isNullOrBlank()
        if (localIsSpecial && incomingIsBasic) {
            return localOp
        }
        return incomingOp ?: localOp
    }

    private fun Order.mergeIncomingOrder(incoming: Order): Order {
        val preserveTransferredIdentity =
            operation == "304" &&
                (incoming.operation == "301" || incoming.operation.isNullOrBlank())
        return incoming.copy(
            status = status,
            operation = getMergedOperation(operation, incoming.operation),
            tableCode = if (preserveTransferredIdentity) tableCode else incoming.tableCode,
            baseHash = if (preserveTransferredIdentity) baseHash else incoming.baseHash,
            ikmsOrder = if (preserveTransferredIdentity) ikmsOrder else incoming.ikmsOrder,
            isUrged = isUrged || incoming.isUrged,
            urgedAt = maxOf(urgedAt.orEmpty(), incoming.urgedAt.orEmpty()).ifBlank { null },
            waterCompletedAt = waterCompletedAt,
            cancelledAt = cancelledAt,
            transferCompletedAt = transferCompletedAt,
            statusUpdatedAt = statusUpdatedAt,
            orderAliases =
                mergeAliases(
                    orderAliases +
                        ikmsOrder +
                        incoming.orderAliases +
                        incoming.ikmsOrder,
                ),
        )
    }

    private fun Order.matchesOrderAlias(alias: String): Boolean = mergeAliases(orderAliases + ikmsOrder).any { it == alias }

    private fun Order.matchesAnyOrderAlias(aliases: List<String>): Boolean {
        val knownAliases = mergeAliases(orderAliases + ikmsOrder)
        return aliases.any { alias -> alias in knownAliases }
    }

    private fun isChronologicallyValid(
        order: Order,
        eventTime: String?,
    ): Boolean {
        if (eventTime.isNullOrBlank()) {
            val hasManualUpdate = !order.statusUpdatedAt.isNullOrBlank() && order.statusUpdatedAt != order.orderTime
            return !hasManualUpdate
        }
        val refTime = order.orderTime.orEmpty().trim()
        if (refTime.isBlank()) return true

        val timeValid = refTime <= eventTime.trim()
        if (!timeValid) return false

        val statusUpdate = order.statusUpdatedAt.orEmpty().trim()
        if (statusUpdate.isNotBlank() && statusUpdate > refTime) {
            return statusUpdate <= eventTime.trim()
        }
        return true
    }

    private fun List<Order>.eventFifo(): List<Order> = sortedWith(compareBy<Order> { it.orderTime.orEmpty() }.thenBy(Order::id))

    private fun Order.isAutomaticallyCancellable(): Boolean =
        status == OrderStatus.Pending ||
            status == OrderStatus.PendingWater ||
            status == OrderStatus.Delayed

    private fun OrderStatus.aliasMergePriority(): Int =
        when (this) {
            OrderStatus.Dispatching,
            OrderStatus.Running,
            -> 6
            OrderStatus.PendingWater,
            OrderStatus.Pending,
            -> 5
            OrderStatus.WaitingTransfer -> 4
            OrderStatus.Delayed -> 3
            OrderStatus.Failed -> 2
            OrderStatus.Cancelled -> 1
            OrderStatus.Completed -> 0
        }

    private fun mergeAliases(values: List<String?>): List<String> =
        values
            .mapNotNull { it?.trim()?.takeIf { value -> value.isNotBlank() && value != "null" } }
            .distinct()

    private fun String?.nullIfBlank(): String? = this?.takeIf { it.isNotBlank() && it != "null" }

    companion object {
        private const val KEY_ORDERS = "orders"
        private const val MAX_LOCAL_ORDERS = 300
    }
}

enum class CancelResult {
    Canceled,
    RequiresManualIntervention,
    NotFound,
}
