package com.example.plccontroller.runtime.aging

import com.example.plccontroller.domain.PlcPollingSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class RealPlcAgingSafetyTest {
    @Test
    fun everyScenarioBuildsCorrectCommand() {
        val expectedDurations =
            mapOf(
                PlcAgingScenario.WaterOutlet1 to listOf(12, 0, 0, 0),
                PlcAgingScenario.WaterOutlet2 to listOf(0, 12, 0, 0),
                PlcAgingScenario.WaterOutlet3 to listOf(0, 0, 12, 0),
                PlcAgingScenario.WaterOutlet4 to listOf(0, 0, 0, 12),
                PlcAgingScenario.AllWaterOutlets to listOf(12, 12, 12, 12),
                PlcAgingScenario.ChickenOilPump to listOf(0, 0, 0, 0),
                PlcAgingScenario.BonePastePump to listOf(0, 0, 0, 0),
                PlcAgingScenario.SparePumpChicken to listOf(0, 0, 0, 0),
                PlcAgingScenario.SparePumpBone to listOf(0, 0, 0, 0),
                PlcAgingScenario.Heating to listOf(0, 0, 0, 0),
            )

        expectedDurations.forEach { (scenario, expected) ->
            val command =
                PlcAgingCycle(index = 7, scenario = scenario)
                    .toAgingPhaseCommand(commandId = 91, actionTicks = 12)

            assertEquals(91, command.commandId)
            assertEquals(scenario.waterSlotMask, command.jobSlotMask)
            assertEquals(
                expected,
                listOf(
                    command.waterDuration1,
                    command.waterDuration2,
                    command.waterDuration3,
                    command.waterDuration4,
                ),
            )

            val isChicken =
                scenario == PlcAgingScenario.ChickenOilPump ||
                    scenario == PlcAgingScenario.SparePumpChicken
            val expectedChicken = if (isChicken) 12 else 0

            val isBone =
                scenario == PlcAgingScenario.BonePastePump ||
                    scenario == PlcAgingScenario.SparePumpBone
            val expectedBone = if (isBone) 12 else 0

            assertEquals(expectedChicken, command.chickenOilDuration)
            assertEquals(expectedBone, command.bonePasteDuration)
            assertEquals(7, command.phaseNo)
        }
    }

    @Test
    fun validLevelStatesWithAllOutputsOffAreSafe() {
        assertNull(snapshot(level = 1).agingUnsafeReason())
        assertNull(snapshot(level = 3).agingUnsafeReason())
        assertNull(snapshot(level = 7).agingUnsafeReason())
    }

    @Test
    fun emergencyStopAndUnsafeLevelsAreBlocked() {
        val emgSnapshot = snapshot(level = 3, emergencyStopCircuitClosed = false)
        assertNotNull(emgSnapshot.agingUnsafeReason())
        listOf(0, 2, 4, 5, 6, 8).forEach { level ->
            val msg = "D210=$level should be blocked"
            assertNotNull(msg, snapshot(level = level).agingUnsafeReason())
        }
    }

    @Test
    fun anyControlledPhysicalOutputIsBlocked() {
        val controlledOutputOffsets = 0..11
        controlledOutputOffsets.forEach { outputOffset ->
            val snap = snapshot(level = 3, outputOffsets = setOf(outputOffset))
            val msg = "output offset $outputOffset should be blocked"
            assertNotNull(msg, snap.agingUnsafeReason())
        }
    }

    @Test
    fun runningPhaseOrEmergencyActionIsBlocked() {
        assertNotNull(snapshot(level = 3, phaseState = 2).agingUnsafeReason())
        assertNotNull(snapshot(level = 3, emergencyState = 2).agingUnsafeReason())
    }

    private fun snapshot(
        level: Int,
        emergencyStopCircuitClosed: Boolean = true,
        outputOffsets: Set<Int> = emptySet(),
        phaseState: Int = 0,
        emergencyState: Int = 0,
    ): PlcPollingSnapshot {
        val mirrors = MutableList(40) { false }
        mirrors[0] = emergencyStopCircuitClosed
        outputOffsets.forEach { offset -> mirrors[20 + offset] = true }

        val holding = MutableList(11) { 0 }
        holding[10] = level
        val phase = MutableList(10) { 0 }
        phase[3] = phaseState
        val emergency = MutableList(10) { 0 }
        emergency[0] = emergencyState
        return PlcPollingSnapshot(
            mirrorBits = mirrors,
            holdingRegisters = holding,
            phaseStatusRegisters = phase,
            emergencyStatusRegisters = emergency,
        )
    }
}
