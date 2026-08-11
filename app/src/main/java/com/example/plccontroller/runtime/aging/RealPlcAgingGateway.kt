package com.example.plccontroller.runtime.aging

import com.example.plccontroller.data.SettingsStore
import com.example.plccontroller.data.plc.PlcController
import com.example.plccontroller.data.plc.PlcHeaterCommand
import com.example.plccontroller.data.plc.PlcPhaseCommand
import com.example.plccontroller.domain.PlcPollingSnapshot
import kotlinx.coroutines.delay

class RealPlcAgingGateway(
    private val plcController: PlcController,
    private val settingsStore: SettingsStore,
    private val executePhase: suspend (PlcPhaseCommand) -> PlcPollingSnapshot,
) : PlcAgingGateway {
    override suspend fun preflight(cycle: PlcAgingCycle): PlcAgingPreflight {
        val snapshot = plcController.pollSnapshotStableClean()
        val unsafeReason = snapshot.agingUnsafeReason(exceptHeater = cycle.scenario == PlcAgingScenario.Heating)
        return PlcAgingPreflight(
            safe = unsafeReason == null,
            detail = unsafeReason ?: "液位=${snapshot.liquidLevelState}，所有受控输出已关闭",
        )
    }

    override suspend fun execute(
        cycle: PlcAgingCycle,
        config: PlcAgingTestConfig,
    ): PlcAgingCycleResult {
        if (cycle.scenario == PlcAgingScenario.Heating) {
            return executeHeatingAging(cycle, config)
        }

        val originalSparePumpMode = settingsStore.snapshot().deviceSparePumpMode
        val targetSparePumpMode =
            when (cycle.scenario) {
                PlcAgingScenario.SparePumpChicken -> 1
                PlcAgingScenario.SparePumpBone -> 2
                PlcAgingScenario.ChickenOilPump, PlcAgingScenario.BonePastePump -> 0
                else -> originalSparePumpMode
            }

        if (targetSparePumpMode != originalSparePumpMode) {
            settingsStore.updateDeviceSparePumpMode(targetSparePumpMode)
        }

        val command =
            cycle.toAgingPhaseCommand(
                commandId = settingsStore.nextPlcCommandId(),
                actionTicks = config.actionTicks,
            )

        try {
            executePhase(command)
        } finally {
            if (targetSparePumpMode != originalSparePumpMode) {
                settingsStore.updateDeviceSparePumpMode(originalSparePumpMode)
            }
        }

        val postSnapshot = plcController.pollSnapshotStableClean()
        val unsafeReason = postSnapshot.agingUnsafeReason()
        check(unsafeReason == null) { "动作完成后的安全检查失败：$unsafeReason" }
        check(postSnapshot.phaseStatusRegisters.getOrNull(2) == command.commandId) {
            "PLC 完成命令号不一致：expected=${command.commandId}, actual=${postSnapshot.phaseStatusRegisters.getOrNull(2)}"
        }
        check(postSnapshot.phaseResultCode == PLC_RESULT_OK) {
            "PLC 相位结果异常：result=${postSnapshot.phaseResultCode}"
        }

        return PlcAgingCycleResult(
            commandId = command.commandId,
            detail = "${cycle.scenario.displayName} ${config.actionTicks * 100}ms",
        )
    }

    private suspend fun executeHeatingAging(
        cycle: PlcAgingCycle,
        config: PlcAgingTestConfig,
    ): PlcAgingCycleResult {
        val initialSnapshot = plcController.pollSnapshotStableClean()
        val settings = settingsStore.snapshot()
        val heaterSelect = settings.deviceHeaterSelect.takeIf { it == 1 || it == 2 } ?: 1
        val currentTemp =
            if (heaterSelect == 2) {
                initialSnapshot.temperatureSensor1 ?: 25
            } else {
                initialSnapshot.temperatureSensor0 ?: 25
            }

        val testTargetTemp = (currentTemp + 5).coerceIn(20, 90)

        val paramCmdId = settingsStore.nextPlcCommandId()
        val paramCmd =
            PlcHeaterCommand(
                commandId = paramCmdId,
                commandType = HEATER_COMMAND_TYPE_SAVE_PARAMS,
                heaterSelect = heaterSelect,
                targetTemp = testTargetTemp,
                hysteresis = 5,
                sensorSelect = settings.deviceHeaterSensorSelect.takeIf { it in 0..2 } ?: 0,
            )
        plcController.writeHeaterCommand(paramCmd)
        delay(HEATER_AFTER_WRITE_SETTLE_MS)

        val enableCmdId = settingsStore.nextPlcCommandId()
        val enableCmd =
            PlcHeaterCommand(
                commandId = enableCmdId,
                commandType = HEATER_COMMAND_TYPE_ENABLE,
                heaterSelect = heaterSelect,
                targetTemp = testTargetTemp,
                hysteresis = 5,
                sensorSelect = settings.deviceHeaterSensorSelect.takeIf { it in 0..2 } ?: 0,
            )
        plcController.writeHeaterCommand(enableCmd)

        val testDurationMs = config.actionTicks * PHASE_TICK_MS
        val deadline = System.currentTimeMillis() + testDurationMs
        var outputWasActive = false

        while (System.currentTimeMillis() < deadline) {
            val snap = plcController.pollSnapshotStableClean()
            val isActive = if (heaterSelect == 2) snap.backupHeaterOutput else snap.mainHeaterOutput
            if (isActive) {
                outputWasActive = true
            }
            val unsafe = snap.agingUnsafeReason(exceptHeater = true)
            check(unsafe == null) { "加热测试中安全检查失败：$unsafe" }
            delay(HEATER_POLL_INTERVAL_MS)
        }

        val disableCmdId = settingsStore.nextPlcCommandId()
        val disableCmd =
            PlcHeaterCommand(
                commandId = disableCmdId,
                commandType = HEATER_COMMAND_TYPE_DISABLE,
                heaterSelect = heaterSelect,
                targetTemp = testTargetTemp,
                hysteresis = 5,
                sensorSelect = settings.deviceHeaterSensorSelect.takeIf { it in 0..2 } ?: 0,
            )
        plcController.writeHeaterCommand(disableCmd)

        delay(HEATER_OFF_SETTLE_MS)
        val finalSnapshot = plcController.pollSnapshotStableClean()
        check(!finalSnapshot.mainHeaterOutput && !finalSnapshot.backupHeaterOutput) {
            "加热器在测试完成后未能成功关闭"
        }

        val activeText = if (outputWasActive) "输出已激活" else "输出未激活"
        return PlcAgingCycleResult(
            commandId = enableCmdId,
            detail = "${cycle.scenario.displayName}：$activeText，测试温度=${testTargetTemp}C",
        )
    }
}

internal fun PlcAgingCycle.toAgingPhaseCommand(
    commandId: Int,
    actionTicks: Int,
): PlcPhaseCommand {
    val isWaterOutlet =
        scenario in
            listOf(
                PlcAgingScenario.WaterOutlet1,
                PlcAgingScenario.WaterOutlet2,
                PlcAgingScenario.WaterOutlet3,
                PlcAgingScenario.WaterOutlet4,
                PlcAgingScenario.AllWaterOutlets,
            )
    val durations =
        IntArray(4) { slotIndex ->
            if (isWaterOutlet && (scenario.waterSlotMask and (1 shl slotIndex) != 0)) actionTicks else 0
        }
    val chickenDuration =
        if (scenario == PlcAgingScenario.ChickenOilPump || scenario == PlcAgingScenario.SparePumpChicken) {
            actionTicks
        } else {
            0
        }
    val boneDuration =
        if (scenario == PlcAgingScenario.BonePastePump || scenario == PlcAgingScenario.SparePumpBone) {
            actionTicks
        } else {
            0
        }
    return PlcPhaseCommand(
        commandId = commandId,
        commandType = PHASE_COMMAND_TYPE_EXECUTE,
        jobSlotMask = scenario.waterSlotMask,
        waterDuration1 = durations[0],
        waterDuration2 = durations[1],
        waterDuration3 = durations[2],
        waterDuration4 = durations[3],
        activeTopLeftLogicalSlot = 1,
        chickenOilDuration = chickenDuration,
        bonePasteDuration = boneDuration,
        phaseNo = index,
    )
}

internal fun PlcPollingSnapshot.agingUnsafeReason(exceptHeater: Boolean = false): String? {
    val basic = checkBasicSafety(exceptHeater)
    return basic ?: checkOutputsSafety()
}

private fun PlcPollingSnapshot.checkBasicSafety(exceptHeater: Boolean): String? {
    val checks =
        listOfNotNull(
            if (emergencyStopActive) "急停按下或常闭回路断开" else null,
            if (liquidLevelState !in SAFE_AGING_LEVELS) "液位必须为中液位或高液位，当前 D210=$liquidLevelState" else null,
            if (!exceptHeater && (mainHeaterOutput || backupHeaterOutput)) "加热输出仍处于开启状态" else null,
            if (phaseActionStateCode == PLC_ACTION_RUNNING) "PLC 相位仍在执行" else null,
            if (emergencyActionStateCode == PLC_ACTION_RUNNING) "PLC 应急动作仍在执行" else null,
        )
    return checks.firstOrNull()
}

private fun PlcPollingSnapshot.checkOutputsSafety(): String? {
    val checks =
        listOfNotNull(
            if (waterValve0Output || waterValve1Output || waterValve2Output || waterValve3Output) "出水阀仍有输出" else null,
            if (levelFillValveOutput || singleWaterValveOutput || sprayValveOutput) "补水、单独加水或喷雾输出仍处于开启状态" else null,
            if (chickenOilPumpOutput || bonePastePumpOutput || sparePumpOutput) "鸡油、骨膏或备用泵输出仍处于开启状态" else null,
        )
    return checks.firstOrNull()
}

private val SAFE_AGING_LEVELS = setOf(1, 3, 7)
private const val PHASE_COMMAND_TYPE_EXECUTE = 1
private const val PLC_ACTION_RUNNING = 2
private const val PLC_RESULT_OK = 0

private const val HEATER_COMMAND_TYPE_SAVE_PARAMS = 10
private const val HEATER_COMMAND_TYPE_ENABLE = 11
private const val HEATER_COMMAND_TYPE_DISABLE = 12
private const val HEATER_AFTER_WRITE_SETTLE_MS = 200L
private const val HEATER_POLL_INTERVAL_MS = 300L
private const val HEATER_OFF_SETTLE_MS = 500L
private const val PHASE_TICK_MS = 100L
