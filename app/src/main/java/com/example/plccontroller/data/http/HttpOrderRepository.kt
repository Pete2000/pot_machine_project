package com.example.plccontroller.data.http

import com.example.plccontroller.data.local.LocalOrderEventStore
import com.example.plccontroller.data.local.OrderEventReducer
import com.example.plccontroller.data.local.OrderPersistenceStore
import com.example.plccontroller.data.local.PosOrderTaskAssembler
import com.example.plccontroller.domain.FormulaCatalog
import com.example.plccontroller.domain.FormulaParameterUpdate
import com.example.plccontroller.domain.Order

class HttpOrderRepository(
    private val apiClient: OrderApiClient,
    private val localOrderStore: OrderPersistenceStore,
) {
    private val orderEventReducer = OrderEventReducer(LocalOrderEventStore(localOrderStore))

    suspend fun loadPendingOrders(deviceCode: String): Pair<List<Order>, List<String>> {
        val warnings = mutableListOf<String>()
        if (deviceCode.isNotBlank()) {
            val lines = apiClient.fetchPosOrderLines(deviceCode)
            val tasks = PosOrderTaskAssembler.assemble(lines)
            tasks.forEach { task ->
                warnings += orderEventReducer.reduce(task).warnings
            }
        }
        return Pair(localOrderStore.allOrders(), warnings)
    }

    fun fetchDeviceTypes(): List<DeviceTypeDto> = apiClient.fetchDeviceTypes()

    fun fetchEquipment(deviceTypeCode: String): List<EquipmentDto> = apiClient.fetchEquipment(deviceTypeCode)

    fun loadFormulaCatalogs(deviceCode: String): List<FormulaCatalog> = apiClient.fetchFormulaCatalogs(deviceCode)

    fun updateFormulaParameter(
        deviceCode: String,
        update: FormulaParameterUpdate,
    ) {
        apiClient.updateEquipmentParam(deviceCode = deviceCode, update = update)
    }

    suspend fun markStarted(orderId: String) = localOrderStore.markDispatching(orderId)

    suspend fun markFinished(
        orderId: String,
        success: Boolean,
        message: String,
    ) {
        if (success) {
            localOrderStore.markPendingDelivery(orderId)
        } else {
            localOrderStore.markFailed(orderId)
        }
    }

    suspend fun markCompleted(
        deviceCode: String,
        orderId: String,
    ) {
        localOrderStore.markCompleted(orderId)
        if (deviceCode.isNotBlank()) {
            apiClient.reportOrderFeedback(deviceId = deviceCode, orderId = orderId)
        }
    }

    suspend fun markCancelled(orderId: String) = localOrderStore.markCancelled(orderId)

    suspend fun markPendingWater(orderId: String) = localOrderStore.markPendingWater(orderId)

    suspend fun setOrderDelayed(
        orderId: String,
        delayed: Boolean,
    ) {
        if (delayed) {
            localOrderStore.markDelayed(orderId)
        } else {
            localOrderStore.markPendingWater(orderId)
        }
    }

    suspend fun allLocalOrders(): List<Order> = localOrderStore.allOrders()

    suspend fun clearAllLocalOrdersForDebug() = localOrderStore.clearAllOrdersForDebug()
}
