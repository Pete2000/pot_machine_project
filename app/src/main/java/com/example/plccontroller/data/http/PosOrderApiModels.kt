package com.example.plccontroller.data.http

import com.example.plccontroller.domain.PosOrderLine
import org.json.JSONObject

data class PosOrderQueryResponse(
    val success: Boolean,
    val msg: String?,
    val code: String?,
    val data: List<PosOrderLine>,
)

fun JSONObject.toPosOrderLine(): PosOrderLine {
    val knownKeys =
        setOf(
            "id",
            "ikmsOrder",
            "posOrder",
            "mainPosOrder",
            "parentId",
            "operation",
            "operator",
            "operateTime",
            "tableCode",
            "turnTableCode",
            "posFoodCode",
            "potTypeCode",
            "potBottomCode",
            "tasteCode",
            "foodCount",
            "deviceCode",
            "storeCode",
            "createTime",
            "updateTime",
            "flag",
            "testTime",
            "potSort",
            "description",
            "standardName",
            "soupMachineCode",
        )

    return PosOrderLine(
        id = getString("id"),
        ikmsOrder = optString("ikmsOrder").nullIfBlank(),
        posOrder = optString("posOrder").nullIfBlank(),
        mainPosOrder = optString("mainPosOrder").nullIfBlank(),
        parentId = optString("parentId").rootParentIdOrNull(),
        operation = optString("operation").nullIfBlank(),
        operator = optString("operator").nullIfBlank(),
        operateTime = optString("operateTime").nullIfBlank(),
        tableCode = optString("tableCode").nullIfBlank(),
        turnTableCode = optString("turnTableCode").nullIfBlank(),
        posFoodCode = optString("posFoodCode").nullIfBlank(),
        potTypeCode = optString("potTypeCode").nullIfBlank(),
        potBottomCode = optString("potBottomCode").nullIfBlank(),
        tasteCode = optString("tasteCode").nullIfBlank(),
        foodCount = optInt("foodCount", 1),
        deviceCode = optString("deviceCode").nullIfBlank(),
        storeCode = optString("storeCode").nullIfBlank(),
        createTime = optString("createTime").nullIfBlank(),
        updateTime = optString("updateTime").nullIfBlank(),
        flag = if (has("flag") && !isNull("flag")) optInt("flag") else null,
        testTime = optString("testTime").nullIfBlank(),
        potSort = if (has("potSort") && !isNull("potSort")) optInt("potSort") else null,
        description = optString("description").nullIfBlank(),
        standardName = optString("standardName").nullIfBlank(),
        soupMachineCode =
            optString("soupMachineCode").nullIfBlank()
                ?: extraMachineCode(knownKeys),
    )
}

private fun JSONObject.extraMachineCode(knownKeys: Set<String>): String? {
    val iterator = keys()
    while (iterator.hasNext()) {
        val key = iterator.next()
        if (key !in knownKeys) {
            return optString(key).nullIfBlank()
        }
    }
    return null
}

private fun String?.rootParentIdOrNull(): String? {
    val normalized = nullIfBlank() ?: return null
    return normalized.takeUnless { value ->
        value == "0" || value == "-1" || value.equals("root", ignoreCase = true)
    }
}

private fun String?.nullIfBlank(): String? {
    val normalized = this?.trim().orEmpty()
    return normalized.takeIf { value ->
        value.isNotBlank() &&
            !value.equals("null", ignoreCase = true) &&
            !value.equals("undefined", ignoreCase = true)
    }
}
