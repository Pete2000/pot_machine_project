package com.example.plccontroller.data.local

import com.example.plccontroller.domain.Order

data class OrderEventIdentity(
    val orderAliases: List<String>,
    val recipeCode: String,
    val slotCodeSummary: String?,
)

enum class OrderMutationOutcome {
    Applied,
    NotFound,
    RequiresManualIntervention,
    Ambiguous,
}

data class OrderMutationResult(
    val outcome: OrderMutationOutcome,
    val changedCount: Int = 0,
    val blockedCount: Int = 0,
    val candidateIds: List<String> = emptyList(),
) {
    companion object {
        fun applied(
            changedCount: Int,
            blockedCount: Int = 0,
        ): OrderMutationResult =
            OrderMutationResult(
                outcome =
                    if (blockedCount > 0) {
                        OrderMutationOutcome.RequiresManualIntervention
                    } else {
                        OrderMutationOutcome.Applied
                    },
                changedCount = changedCount,
                blockedCount = blockedCount,
            )

        fun notFound(): OrderMutationResult = OrderMutationResult(OrderMutationOutcome.NotFound)

        fun ambiguous(candidateIds: List<String>): OrderMutationResult =
            OrderMutationResult(
                outcome = OrderMutationOutcome.Ambiguous,
                candidateIds = candidateIds,
            )
    }
}

internal sealed interface OrderAliasMatchResult {
    data class Unique(
        val order: Order,
    ) : OrderAliasMatchResult

    data class Ambiguous(
        val orders: List<Order>,
    ) : OrderAliasMatchResult

    data object NotFound : OrderAliasMatchResult
}

internal object OrderEventMatcher {
    fun matchByAlias(
        orders: List<Order>,
        identity: OrderEventIdentity,
        isChronologicallyValid: (Order) -> Boolean,
    ): OrderAliasMatchResult {
        val aliases = normalizeAliases(identity.orderAliases)
        if (aliases.isEmpty() || identity.recipeCode.isBlank()) {
            return OrderAliasMatchResult.NotFound
        }

        val eventFingerprint = normalizePotContentFingerprint(identity.slotCodeSummary)
        val candidates =
            orders.filter { order ->
                val knownAliases = normalizeAliases(order.orderAliases + order.ikmsOrder)
                aliases.any { it in knownAliases } &&
                    (
                        order.recipeCode == identity.recipeCode ||
                            order.rootPosFoodCode == identity.recipeCode
                    ) &&
                    (
                        eventFingerprint == null ||
                            normalizePotContentFingerprint(order.slotCodeSummary) == eventFingerprint
                    ) &&
                    isChronologicallyValid(order)
            }

        return when (candidates.size) {
            0 -> OrderAliasMatchResult.NotFound
            1 -> OrderAliasMatchResult.Unique(candidates.single())
            else -> OrderAliasMatchResult.Ambiguous(candidates)
        }
    }

    private fun normalizeAliases(values: List<String?>): List<String> =
        values
            .mapNotNull { value ->
                value?.trim()?.takeIf {
                    it.isNotBlank() &&
                        !it.equals("null", ignoreCase = true) &&
                        !it.equals("undefined", ignoreCase = true)
                }
            }.distinct()

    internal fun normalizePotContentFingerprint(value: String?): String? =
        value
            ?.split('|')
            ?.map { slot -> slot.substringAfter(':', slot).trim() }
            ?.filter(String::isNotBlank)
            ?.sorted()
            ?.joinToString("|")
            ?.takeIf(String::isNotBlank)
}
