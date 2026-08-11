package com.example.plccontroller.runtime

import com.example.plccontroller.data.SettingsStore
import com.example.plccontroller.data.http.DeviceTypeDto
import com.example.plccontroller.data.http.EquipmentDto
import com.example.plccontroller.data.http.HttpOrderRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class EquipmentRegistrationCoordinator(
    private val orderRepository: HttpOrderRepository,
    private val runtimeStore: MachineRuntimeStore,
    private val settingsStore: SettingsStore,
    private val log: (String) -> Unit,
) {
    suspend fun registerFirstEquipment() {
        runCatching {
            runtimeStore.update {
                it.copy(
                    networkState = NetworkConnectionState.Syncing,
                    lastMessage = "Registering equipment",
                )
            }
            val equipment =
                withContext(Dispatchers.IO) {
                    val deviceTypes = orderRepository.fetchDeviceTypes()
                    val selectedType =
                        selectPotMachineType(deviceTypes)
                            ?: throw IllegalStateException("No device type returned")
                    orderRepository.fetchEquipment(selectedType.deviceTypeCode).firstOrNull()
                        ?: throw IllegalStateException("No equipment returned for ${selectedType.deviceTypeName}")
                }
            require(equipment.deviceCode.isNotBlank()) { "Equipment deviceCode is blank" }
            settingsStore.updateDeviceCode(equipment.deviceCode)
            runtimeStore.update {
                it.copy(
                    networkState = NetworkConnectionState.Online,
                    lastMessage = "Equipment registered: ${equipment.equipmentName}",
                )
            }
            log("Equipment registered: ${equipment.equipmentName}, code=${equipment.deviceCode}")
        }.onFailure { error ->
            markNetworkFault(error)
        }
    }

    suspend fun fetchDeviceTypes(): List<DeviceTypeDto> =
        withContext(Dispatchers.IO) {
            orderRepository.fetchDeviceTypes()
        }

    suspend fun fetchEquipment(deviceTypeCode: String): List<EquipmentDto> =
        withContext(Dispatchers.IO) {
            orderRepository.fetchEquipment(deviceTypeCode)
        }

    private fun selectPotMachineType(deviceTypes: List<DeviceTypeDto>): DeviceTypeDto? =
        deviceTypes.firstOrNull { type ->
            type.deviceTypeName.contains("打锅") ||
                type.deviceTypeName.contains("POT", ignoreCase = true) ||
                type.deviceTypeCode.contains("POT", ignoreCase = true)
        } ?: deviceTypes.firstOrNull()

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
