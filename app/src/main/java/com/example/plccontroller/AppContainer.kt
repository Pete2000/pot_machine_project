package com.example.plccontroller

import android.content.Context
import com.example.plccontroller.data.SettingsStore
import com.example.plccontroller.data.http.HttpOrderRepository
import com.example.plccontroller.data.http.LogUploadManager
import com.example.plccontroller.data.http.OrderApiClient
import com.example.plccontroller.data.http.OtaUpdateManager
import com.example.plccontroller.data.local.LocalOrderStore
import com.example.plccontroller.data.local.room.OrderDatabase
import com.example.plccontroller.data.local.room.RoomMachineAuditStore
import com.example.plccontroller.data.local.room.RoomOrderPersistenceStore
import com.example.plccontroller.data.plc.ModbusQueueConfig
import com.example.plccontroller.data.plc.PlcController
import com.example.plccontroller.data.plc.QueuedModbusTransport
import com.example.plccontroller.data.plc.SerialModbusTransport
import com.example.plccontroller.data.toCommunicationConfig
import com.example.plccontroller.domain.PlcRegisterMap
import com.example.plccontroller.runtime.MachineCoordinator
import com.example.plccontroller.runtime.MachineRuntimeStore
import com.example.plccontroller.runtime.PlcPollingService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

class AppContainer(
    context: Context,
) {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    val settingsStore = SettingsStore(context)
    private val initialConfig = settingsStore.snapshot()

    private val serialTransport = SerialModbusTransport(initialConfig.toCommunicationConfig())

    private val queuedTransport =
        QueuedModbusTransport(
            delegate = serialTransport,
            initialConfig =
                ModbusQueueConfig(
                    readTimeoutMs = initialConfig.readTimeoutMs,
                    writeTimeoutMs = initialConfig.writeTimeoutMs,
                    retries = initialConfig.retryCount,
                    frameGapMs = initialConfig.frameGapMs,
                ),
        )

    private val businessApiClient = OrderApiClient(initialConfig.businessUrl)
    private val legacyLocalOrderStore = LocalOrderStore(context)
    private val orderDatabase = OrderDatabase.create(context)
    private val localOrderStore =
        RoomOrderPersistenceStore(
            database = orderDatabase,
            legacyOrdersProvider = legacyLocalOrderStore::allOrders,
        )
    private val machineAuditStore = RoomMachineAuditStore(orderDatabase)

    val orderRepository: HttpOrderRepository =
        HttpOrderRepository(
            apiClient = businessApiClient,
            localOrderStore = localOrderStore,
        )

    val otaUpdateManager = OtaUpdateManager()
    val logUploadManager = LogUploadManager()

    val plcController: PlcController =
        PlcController(
            initialSlaveId = initialConfig.plcSlaveId,
            registerMap = PlcRegisterMap(),
            transport = queuedTransport,
        )

    val runtimeStore: MachineRuntimeStore =
        MachineRuntimeStore(
            initialState =
                com.example.plccontroller.runtime
                    .MachineRuntimeState(
                        communicationConfig = initialConfig.toCommunicationConfig(),
                    ).withRefreshedTransferDeck(),
        )

    val plcPollingService: PlcPollingService =
        PlcPollingService(
            plcController = plcController,
            settingsStore = settingsStore,
            runtimeStore = runtimeStore,
            auditStore = machineAuditStore,
            parentScope = appScope,
        )

    val machineCoordinator: MachineCoordinator =
        MachineCoordinator(
            orderRepository = orderRepository,
            plcController = plcController,
            runtimeStore = runtimeStore,
            settingsStore = settingsStore,
            auditStore = machineAuditStore,
            externalScope = appScope,
        )

    init {
        settingsStore.configFlow
            .onEach { config ->
                businessApiClient.updateBaseUrl(config.businessUrl)
                plcController.updateCommunicationConfig(config.toCommunicationConfig())
                runtimeStore.update { state ->
                    state.copy(communicationConfig = config.toCommunicationConfig())
                }
            }.launchIn(appScope)
        plcPollingService.start()
    }
}
