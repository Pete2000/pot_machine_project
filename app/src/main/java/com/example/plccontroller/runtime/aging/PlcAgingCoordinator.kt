package com.example.plccontroller.runtime.aging

import android.util.Log
import com.example.plccontroller.data.SettingsStore
import com.example.plccontroller.data.plc.PlcController
import com.example.plccontroller.data.plc.PlcPhaseCommand
import com.example.plccontroller.domain.PlcPollingSnapshot
import com.example.plccontroller.runtime.MachineRuntimeStore

class PlcAgingCoordinator(
    private val plcController: PlcController,
    private val settingsStore: SettingsStore,
    private val runtimeStore: MachineRuntimeStore,
    private val executePhase: suspend (PlcPhaseCommand) -> PlcPollingSnapshot,
    private val pendingOrderId: () -> String?,
    private val withCriticalMaintenance: suspend (String, suspend () -> Unit) -> Unit,
    private val withPlcCommandLock: suspend (suspend () -> Unit) -> Unit,
    private val log: (String) -> Unit,
) {
    suspend fun run(
        config: PlcAgingTestConfig,
        onProgress: (PlcAgingProgress) -> Unit = {},
    ): PlcAgingReport {
        var report: PlcAgingReport? = null
        withCriticalMaintenance("PLC water aging test in progress") {
            val runtime = runtimeStore.snapshot()
            require(
                runtime.currentOrder == null &&
                    runtime.phaseRotationPrompt == null &&
                    pendingOrderId() == null,
            ) {
                "存在正在处理或等待转锅的订单，禁止启动 PLC 老化测试"
            }
            withPlcCommandLock {
                val gateway =
                    RealPlcAgingGateway(
                        plcController = plcController,
                        settingsStore = settingsStore,
                        executePhase = executePhase,
                    )
                report =
                    PlcAgingTestRunner(gateway).run(config) { progress ->
                        Log.i(TAG_PLC_AGING, progress.message)
                        log("PLC aging: ${progress.message}")
                        onProgress(progress)
                    }
            }
        }
        return checkNotNull(report) { "PLC aging test did not produce a report" }.also { result ->
            Log.i(
                TAG_PLC_AGING,
                "finished status=${result.status}, completed=${result.completedCycles}/${result.requestedCycles}, " +
                    "message=${result.message}",
            )
        }
    }
}

private const val TAG_PLC_AGING = "PlcAging"
