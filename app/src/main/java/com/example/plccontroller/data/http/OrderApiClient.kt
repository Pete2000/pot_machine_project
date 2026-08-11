package com.example.plccontroller.data.http

import com.example.plccontroller.domain.FormulaCatalog
import com.example.plccontroller.domain.FormulaParameterUpdate
import com.example.plccontroller.domain.Order
import com.example.plccontroller.domain.OrderStatus
import com.example.plccontroller.domain.PosOrderLine
import com.example.plccontroller.domain.PotMode
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class OrderApiClient(
    initialBaseUrl: String,
) {
    var baseUrl: String = initialBaseUrl
        private set

    fun updateBaseUrl(newUrl: String) {
        baseUrl = newUrl
    }

    fun fetchDeviceTypes(): List<DeviceTypeDto> {
        val response =
            JSONObject(
                request(
                    path = "/pot/getDeviceType",
                    method = "GET",
                ),
            )
        val data = response.optJSONArray("data") ?: JSONArray()
        return buildList {
            for (index in 0 until data.length()) {
                add(data.getJSONObject(index).toDeviceTypeDto())
            }
        }
    }

    fun fetchEquipment(deviceTypeCode: String): List<EquipmentDto> {
        val response =
            JSONObject(
                request(
                    path = "/pot/getEquipment",
                    method = "POST",
                    body = formBody(mapOf("deviceTypeCode" to deviceTypeCode)),
                    contentType = "application/x-www-form-urlencoded",
                ),
            )
        val data = response.optJSONArray("data") ?: JSONArray()
        return buildList {
            for (index in 0 until data.length()) {
                add(data.getJSONObject(index).toEquipmentDto())
            }
        }
    }

    fun fetchPosOrderLines(deviceCode: String): List<PosOrderLine> {
        val jsonBody = JSONObject().put("deviceCode", deviceCode).toString()
        val response =
            request(
                path = "/pos/order/query",
                method = "POST",
                body = jsonBody,
                contentType = "application/json",
            )
        val data = parseArrayPayload(response)
        return buildList {
            for (index in 0 until data.length()) {
                add(data.getJSONObject(index).toPosOrderLine())
            }
        }
    }

    fun fetchPendingOrders(): List<Order> {
        val response =
            request(
                path = "/api/orders/pending",
                method = "GET",
            )
        val jsonArray = parseOrderArray(response)
        return buildList {
            for (index in 0 until jsonArray.length()) {
                add(jsonArray.getJSONObject(index).toOrder())
            }
        }
    }

    fun fetchFormulaCatalogs(deviceCode: String): List<FormulaCatalog> {
        val response =
            JSONObject(
                request(
                    path = "/pot/getFormula",
                    method = "POST",
                    body =
                        formBody(
                            mapOf(
                                "deviceCode" to deviceCode,
                            ),
                        ),
                    contentType = "application/x-www-form-urlencoded",
                ),
            )
        return response.toFormulaCatalogs()
    }

    fun updateEquipmentParam(
        deviceCode: String,
        update: FormulaParameterUpdate,
    ) {
        val response =
            request(
                path = "/pot/updateEquipmentParam",
                method = "POST",
                body =
                    formBody(
                        mapOf(
                            "deviceCode" to deviceCode,
                            "potCode" to update.potCode,
                            "potTypeCode" to update.potTypeCode,
                            "addWater" to update.addWaterSeconds.toPlainSeconds(),
                            "addChickenOil" to update.addChickenOilSeconds.toPlainSeconds(),
                            "boneOil" to update.addBonePasteSeconds.toPlainSeconds(),
                        ),
                    ),
                contentType = "application/x-www-form-urlencoded",
            )
        val root = runCatching { JSONObject(response) }.getOrNull() ?: return
        val code = root.optString("code")
        val success = root.optBoolean("success", code.isBlank() || code == "200")
        if (!success || (code.isNotBlank() && code != "200")) {
            throw IllegalStateException(root.optString("msg", "Formula save failed"))
        }
    }

    fun reportOrderFeedback(
        deviceId: String,
        orderId: String,
    ) {
        request(
            path = "/pot/order/feedback",
            method = "POST",
            body =
                formBody(
                    mapOf(
                        "deviceId" to deviceId,
                        "deviceCode" to deviceId,
                        "orderId" to orderId,
                    ),
                ),
            contentType = "application/x-www-form-urlencoded",
        )
    }

    private fun request(
        path: String,
        method: String,
        body: String? = null,
        contentType: String = "application/json",
    ): String {
        val fullUrl = baseUrl.trimEnd('/') + "/" + path.trimStart('/')
        val connection = URL(fullUrl).openConnection() as HttpURLConnection
        try {
            connection.apply {
                requestMethod = method
                connectTimeout = 5_000
                readTimeout = 5_000
                setRequestProperty("Content-Type", contentType)
                doInput = true
                if (body != null) {
                    doOutput = true
                }
            }

            body?.let { payload ->
                OutputStreamWriter(connection.outputStream).use { writer ->
                    writer.write(payload)
                    writer.flush()
                }
            }

            val statusCode = connection.responseCode
            runCatching {
                val serverTimeMs = connection.date
                if (serverTimeMs > 0L) {
                    val offset = serverTimeMs - System.currentTimeMillis()
                    TimeCalibrator.updateOffset(offset)
                }
            }
            val stream =
                if (statusCode in 200..299) {
                    connection.inputStream
                } else {
                    connection.errorStream
                }

            val response =
                if (stream != null) {
                    BufferedReader(stream.reader()).use { reader ->
                        reader.readText()
                    }
                } else {
                    ""
                }

            if (statusCode !in 200..299) {
                throw IllegalStateException("HTTP $statusCode: $response")
            }

            return response
        } finally {
            connection.disconnect()
        }
    }

    private fun formBody(values: Map<String, String>): String =
        values.entries.joinToString("&") { (key, value) ->
            "${key.urlEncode()}=${value.urlEncode()}"
        }

    private fun String.urlEncode(): String = URLEncoder.encode(this, Charsets.UTF_8.name())

    private fun Double.toPlainSeconds(): String =
        if (this % 1.0 == 0.0) {
            toInt().toString()
        } else {
            toString()
        }

    private fun parseOrderArray(response: String): JSONArray {
        val payload = response.trim()
        return when {
            payload.startsWith("[") -> JSONArray(payload)
            payload.startsWith("{") -> {
                val root = JSONObject(payload)
                root.optJSONArray("data")
                    ?: root.optJSONArray("items")
                    ?: root.optJSONArray("rows")
                    ?: throw IllegalStateException("Order response does not contain an array payload")
            }
            else -> throw IllegalStateException("Unsupported order response payload")
        }
    }

    private fun parseArrayPayload(response: String): JSONArray {
        val payload = response.trim()
        return when {
            payload.isBlank() -> JSONArray()
            payload.startsWith("[") -> JSONArray(payload)
            payload.startsWith("{") -> {
                val root = JSONObject(payload)
                root.findNestedArrayPayload() ?: JSONArray()
            }
            else -> JSONArray()
        }
    }

    private fun JSONObject.findNestedArrayPayload(): JSONArray? {
        val keys = arrayOf("data", "records", "rows", "items", "list", "result")
        keys.firstNotNullOfOrNull { key -> optJSONArray(key) }?.let { return it }
        return keys.firstNotNullOfOrNull { key ->
            optJSONObject(key)?.findNestedArrayPayload()
        }
    }

    private fun JSONObject.toOrder(): Order {
        val state = resolveOrderStatus(optString("status", OrderStatus.Pending.name))
        val mode = resolvePotMode(optString("potMode", optString("potType", "")))
        return Order(
            id = getString("id"),
            recipeCode = optString("recipeCode", "0"),
            quantity = optInt("quantity", 1),
            targetTemperature = optInt("targetTemperature", 180),
            cookSeconds = optInt("cookSeconds", 60),
            spiceLevel = optInt("spiceLevel", 0),
            tableCode = optNonBlank("tableCode") ?: optNonBlank("tableNo"),
            orderTime =
                optNonBlank("operateTime")
                    ?: optNonBlank("createTime")
                    ?: optNonBlank("createdAt")
                    ?: optNonBlank("orderTime")
                    ?: optNonBlank("time"),
            potMode = mode,
            potBottomName = optNonBlank("potBottomName") ?: optNonBlank("recipeName"),
            tasteSummary = optNonBlank("tasteSummary") ?: optNonBlank("tasteName") ?: optNonBlank("flavor"),
            slotSummary = optNonBlank("slotSummary") ?: optNonBlank("potSlotSummary"),
            status = state,
        )
    }

    private fun resolveOrderStatus(rawValue: String): OrderStatus =
        when (rawValue.trim()) {
            "待加水", "pendingWater", "PendingWater", "PENDING_WATER" -> OrderStatus.PendingWater
            "待传锅", "waitingTransfer", "WaitingTransfer", "WAITING_TRANSFER" -> OrderStatus.WaitingTransfer
            "下发中", "dispatching", "Dispatching", "DISPATCHING" -> OrderStatus.Dispatching
            "运行中", "running", "Running", "RUNNING" -> OrderStatus.Running
            "已完成", "completed", "Completed", "COMPLETED" -> OrderStatus.Completed
            "已取消", "cancelled", "Cancelled", "CANCELLED", "canceled", "Canceled", "CANCELED" -> OrderStatus.Cancelled
            "失败", "failed", "Failed", "FAILED" -> OrderStatus.Failed
            "待处理", "待加工", "pending", "Pending", "PENDING" -> OrderStatus.Pending
            else ->
                OrderStatus.entries.firstOrNull { it.name.equals(rawValue, ignoreCase = true) }
                    ?: OrderStatus.Pending
        }

    private fun resolvePotMode(rawValue: String): PotMode =
        when (rawValue.trim()) {
            "单锅", "single", "Single", "SINGLE" -> PotMode.Single
            "拼锅", "split", "Split", "SPLIT" -> PotMode.Split
            "三拼锅", "三宫格", "threeGrid", "ThreeGrid", "THREE_GRID" -> PotMode.ThreeGrid
            "四宫格", "fourGrid", "FourGrid", "FOUR_GRID" -> PotMode.FourGrid
            else ->
                PotMode.entries.firstOrNull { it.name.equals(rawValue, ignoreCase = true) }
                    ?: PotMode.Single
        }

    private fun JSONObject.optNonBlank(name: String): String? = optString(name).takeIf { it.isNotBlank() }
}

data class DeviceTypeDto(
    val deviceTypeCode: String,
    val deviceTypeName: String,
)

data class EquipmentDto(
    val deviceCode: String,
    val equipmentName: String,
    val storeCode: String?,
)

private fun JSONObject.toDeviceTypeDto(): DeviceTypeDto =
    DeviceTypeDto(
        deviceTypeCode = firstNonBlank("deviceTypeCode", "typeCode", "code", "id"),
        deviceTypeName =
            firstNonBlank("deviceTypeName", "typeName", "name", "standardName")
                .ifBlank { firstNonBlank("deviceTypeCode", "typeCode", "code", "id") },
    )

private fun JSONObject.toEquipmentDto(): EquipmentDto =
    EquipmentDto(
        deviceCode = firstNonBlank("deviceCode", "equipmentNo", "equipmentCode", "code", "id"),
        equipmentName =
            firstNonBlank("equipmentName", "deviceName", "name", "standardName")
                .ifBlank { firstNonBlank("deviceCode", "equipmentNo", "equipmentCode", "code", "id") },
        storeCode = firstNonBlank("storeCode", "shopCode").takeIf(String::isNotBlank),
    )

private fun JSONObject.firstNonBlank(vararg names: String): String =
    names
        .firstNotNullOfOrNull { name ->
            optString(name).takeIf(String::isNotBlank)
        }.orEmpty()
