package com.example.plccontroller.runtime

import com.example.plccontroller.data.http.HttpOrderRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class OrderSyncCoordinator(
    private val orderRepository: HttpOrderRepository,
    private val runtimeStore: MachineRuntimeStore,
    private val deviceCodeProvider: () -> String,
    private val log: (String) -> Unit,
) {
    suspend fun refreshOrders() {
        runCatching {
            runtimeStore.update {
                it.copy(
                    networkState = NetworkConnectionState.Syncing,
                    lastMessage = "Syncing order queue",
                )
            }
            val deviceCode = deviceCodeProvider().trim()
            if (deviceCode.isBlank()) {
                runtimeStore.update {
                    it.copy(
                        networkState = NetworkConnectionState.Fault,
                        lastMessage = "未配置设备编码，无法同步订单；请先在设置-接入中填写设备编码或自动注册设备",
                    )
                }
                log("Order sync skipped because deviceCode is blank.")
                return
            }
            val (loadedOrders, warnings) =
                withContext(Dispatchers.IO) {
                    orderRepository.loadPendingOrders(deviceCode)
                }
            if (warnings.isNotEmpty()) {
                warnings.forEach { warning ->
                    log("Warning: $warning")
                }
            }
            runtimeStore.update { state ->
                OrderQueueProjector.project(
                    state = state,
                    loadedOrders = loadedOrders,
                    warnings = warnings,
                    markNetworkOnline = true,
                )
            }
            log("Order sync completed")
        }.onFailure { error ->
            markNetworkFault(error)
        }
    }

    suspend fun refreshOrdersLocal() {
        runCatching {
            val loadedOrders =
                withContext(Dispatchers.IO) {
                    orderRepository.allLocalOrders()
                }
            runtimeStore.update { state ->
                OrderQueueProjector.project(
                    state = state,
                    loadedOrders = loadedOrders,
                )
            }
        }.onFailure { error ->
            log("Local order refresh failed: ${error.message ?: "unknown"}")
        }
    }

    private fun markNetworkFault(error: Throwable) {
        val message = error.message ?: "Order API failed"
        log("Network sync error: $message")
        runtimeStore.update {
            it.copy(
                networkState = NetworkConnectionState.Fault,
                lastMessage = message,
            )
        }
    }
}
