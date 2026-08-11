package com.example.plccontroller.data.local

import android.content.SharedPreferences
import com.example.plccontroller.domain.Order
import com.example.plccontroller.domain.OrderStatus
import com.example.plccontroller.domain.OrderStructureStatus
import com.example.plccontroller.domain.PotMode
import org.json.JSONArray
import org.json.JSONObject

internal interface LocalOrderKeyValueStorage {
    fun getString(key: String): String?

    fun putString(
        key: String,
        value: String,
    )
}

internal interface LocalOrderCodec {
    fun encode(orders: List<Order>): String

    fun decode(payload: String): List<Order>
}

internal object JsonLocalOrderCodec : LocalOrderCodec {
    override fun encode(orders: List<Order>): String =
        JSONArray()
            .also { array ->
                orders.forEach { array.put(it.toJson()) }
            }.toString()

    override fun decode(payload: String): List<Order> {
        val array = runCatching { JSONArray(payload) }.getOrNull() ?: return emptyList()
        return buildList {
            for (index in 0 until array.length()) {
                array.optJSONObject(index)?.toOrder()?.let(::add)
            }
        }
    }

    private fun Order.toJson(): JSONObject =
        JSONObject()
            .put("id", id)
            .put("recipeCode", recipeCode)
            .put("quantity", quantity)
            .put("targetTemperature", targetTemperature)
            .put("cookSeconds", cookSeconds)
            .put("spiceLevel", spiceLevel)
            .put("tableCode", tableCode)
            .put("orderTime", orderTime)
            .put("potMode", potMode.name)
            .put("potBottomName", potBottomName)
            .put("tasteSummary", tasteSummary)
            .put("slotSummary", slotSummary)
            .put("status", status.name)
            .put("baseHash", baseHash)
            .put("ikmsOrder", ikmsOrder)
            .put("rootPosFoodCode", rootPosFoodCode)
            .put("slotCodeSummary", slotCodeSummary)
            .put("operator", operator)
            .put("operation", operation)
            .put("potBottomSummary", potBottomSummary)
            .put("sourceRootId", sourceRootId)
            .put("structureStatus", structureStatus.name)
            .put("structureMessage", structureMessage)
            .put("expectedSlotCount", expectedSlotCount)
            .put("attachedBottomCount", attachedBottomCount)
            .put("rawBottomCandidateCount", rawBottomCandidateCount)
            .put("waterCompletedAt", waterCompletedAt)
            .put("cancelledAt", cancelledAt)
            .put("transferCompletedAt", transferCompletedAt)
            .put("statusUpdatedAt", statusUpdatedAt)
            .put("isUrged", isUrged)
            .put("urgedAt", urgedAt)
            .put(
                "missingSlotLabels",
                JSONArray().also { array ->
                    missingSlotLabels.forEach { array.put(it) }
                },
            ).put(
                "orderAliases",
                JSONArray().also { array ->
                    mergeAliases(orderAliases + ikmsOrder).forEach { array.put(it) }
                },
            )

    private fun JSONObject.toOrder(): Order? {
        val id = optString("id").takeIf(String::isNotBlank) ?: return null
        return Order(
            id = id,
            recipeCode = optString("recipeCode"),
            quantity = optInt("quantity", 1),
            targetTemperature = optInt("targetTemperature", 180),
            cookSeconds = optInt("cookSeconds", 60),
            spiceLevel = optInt("spiceLevel", 0),
            tableCode = optString("tableCode").nullIfBlank(),
            orderTime = optString("orderTime").nullIfBlank(),
            potMode = runCatching { PotMode.valueOf(optString("potMode")) }.getOrDefault(PotMode.Single),
            potBottomName = optString("potBottomName").nullIfBlank(),
            tasteSummary = optString("tasteSummary").nullIfBlank(),
            slotSummary = optString("slotSummary").nullIfBlank(),
            status = runCatching { OrderStatus.valueOf(optString("status")) }.getOrDefault(OrderStatus.PendingWater),
            baseHash = optString("baseHash").nullIfBlank(),
            ikmsOrder = optString("ikmsOrder").nullIfBlank(),
            rootPosFoodCode = optString("rootPosFoodCode").nullIfBlank(),
            slotCodeSummary = optString("slotCodeSummary").nullIfBlank(),
            operator = optString("operator").nullIfBlank(),
            operation = optString("operation").nullIfBlank(),
            potBottomSummary = optString("potBottomSummary").nullIfBlank(),
            orderAliases = readAliases(),
            sourceRootId = optString("sourceRootId").nullIfBlank(),
            structureStatus = readStructureStatus(),
            structureMessage = optString("structureMessage").nullIfBlank(),
            expectedSlotCount = optInt("expectedSlotCount", 0),
            attachedBottomCount = optInt("attachedBottomCount", 0),
            rawBottomCandidateCount = optInt("rawBottomCandidateCount", 0),
            missingSlotLabels = readStringArray("missingSlotLabels"),
            waterCompletedAt = optString("waterCompletedAt").nullIfBlank(),
            cancelledAt = optString("cancelledAt").nullIfBlank(),
            transferCompletedAt = optString("transferCompletedAt").nullIfBlank(),
            statusUpdatedAt = optString("statusUpdatedAt").nullIfBlank(),
            isUrged = optBoolean("isUrged", false),
            urgedAt = optString("urgedAt").nullIfBlank(),
        )
    }

    private fun JSONObject.readAliases(): List<String> {
        val array = optJSONArray("orderAliases") ?: return emptyList()
        return buildList {
            for (index in 0 until array.length()) {
                array.optString(index).nullIfBlank()?.let(::add)
            }
        }.let(::mergeAliases)
    }

    private fun JSONObject.readStructureStatus(): OrderStructureStatus =
        runCatching {
            OrderStructureStatus.valueOf(optString("structureStatus"))
        }.getOrDefault(OrderStructureStatus.Complete)

    private fun JSONObject.readStringArray(key: String): List<String> {
        val array = optJSONArray(key) ?: return emptyList()
        return buildList {
            for (index in 0 until array.length()) {
                array.optString(index).nullIfBlank()?.let(::add)
            }
        }
    }

    private fun mergeAliases(values: List<String?>): List<String> =
        values
            .mapNotNull { it?.trim()?.takeIf { value -> value.isNotBlank() && value != "null" } }
            .distinct()

    private fun String?.nullIfBlank(): String? = this?.takeIf { it.isNotBlank() && it != "null" }
}

internal class SharedPreferencesOrderKeyValueStorage(
    private val prefs: SharedPreferences,
) : LocalOrderKeyValueStorage {
    override fun getString(key: String): String? = prefs.getString(key, null)

    override fun putString(
        key: String,
        value: String,
    ) {
        prefs.edit().putString(key, value).apply()
    }
}
