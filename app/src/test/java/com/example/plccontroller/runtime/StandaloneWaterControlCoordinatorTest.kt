package com.example.plccontroller.runtime

import com.example.plccontroller.data.AppPersistentConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StandaloneWaterControlCoordinatorTest {
    @Test
    fun emergencyWaterCommandClampsModeDurationAndSparePumpMode() {
        val command =
            config(
                mode = 9,
                timedTicks = 9_999,
                sparePumpMode = 9,
            ).toEmergencyWaterParameterCommand()

        assertEquals(0, command.mode)
        assertEquals(3_600, command.timedDuration)
        assertEquals(0, command.sparePumpMode)
        assertEquals(listOf(0, 3_600, 0, 0, 0, 0, 0, 0, 0, 0), command.toRegisters())
    }

    @Test
    fun emergencyWaterParameterDiffOnlyTracksStandaloneWaterFields() {
        val base = config()

        assertTrue(base.hasEmergencyWaterParameterDiff(base.copy(deviceStandaloneWaterMode = 1)))
        assertTrue(base.hasEmergencyWaterParameterDiff(base.copy(deviceStandaloneWaterTimedTicks = 200)))
        assertTrue(base.hasEmergencyWaterParameterDiff(base.copy(deviceSparePumpMode = 2)))
        assertFalse(base.hasEmergencyWaterParameterDiff(base.copy(deviceHeaterTargetTemp = 95)))
    }

    @Test
    fun emergencyWaterSummaryUsesOperatorFriendlyLabels() {
        val summary =
            config(
                mode = 2,
                timedTicks = 150,
                sparePumpMode = 1,
            ).emergencyWaterSummaryText()

        assertEquals("时间控制, timedTicks=150, 备用替代鸡油", summary)
    }

    private fun config(
        mode: Int = 2,
        timedTicks: Int = 100,
        sparePumpMode: Int = 0,
    ): AppPersistentConfig =
        AppPersistentConfig(
            businessUrl = "",
            managementUrl = "",
            deviceCode = "",
            plcSerialPortPath = "/dev/ttyS4",
            plcBaudRate = 19_200,
            plcDataBits = 8,
            plcParity = "NONE",
            plcStopBits = 1,
            plcSlaveId = 1,
            readTimeoutMs = 500L,
            writeTimeoutMs = 500L,
            retryCount = 1,
            frameGapMs = 200L,
            registerOnlyMode = false,
            registerOnlyBlockStart = 0,
            registerOnlyBlockCount = 0,
            heartbeatPeriodMs = 1_000L,
            activePollingIntervalMs = 200L,
            idlePollingIntervalMs = 500L,
            orderPollingIntervalMs = 5_000L,
            formulaSyncIntervalSeconds = 30L,
            connectionTestRegister = 200,
            deviceHeaterTargetTemp = 90,
            deviceHeaterHysteresisTemp = 5,
            deviceHeaterSelect = 1,
            deviceHeaterSensorSelect = 0,
            deviceStandaloneWaterMode = mode,
            deviceStandaloneWaterTimedTicks = timedTicks,
            deviceWaterOutlet1CalibrationPercent = 100,
            deviceWaterOutlet2CalibrationPercent = 100,
            deviceWaterOutlet3CalibrationPercent = 100,
            deviceWaterOutlet4CalibrationPercent = 100,
            deviceChickenOilCalibrationPercent = 100,
            deviceBonePasteCalibrationPercent = 100,
            deviceSparePumpMode = sparePumpMode,
        )
}
