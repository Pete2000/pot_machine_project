package com.example.plccontroller.runtime

import com.example.plccontroller.data.AppPersistentConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HeaterControlCoordinatorTest {
    @Test
    fun heaterCommandsUseExpectedCommandTypesAndClampParameters() {
        val config =
            config(
                heaterSelect = 9,
                targetTemp = 150,
                hysteresis = 99,
                sensorSelect = 9,
            )

        val saveParams = config.toHeaterParameterCommand(commandId = 21)
        val enable = config.toHeaterEnableCommand(commandId = 22)
        val disable = config.toHeaterDisableCommand(commandId = 23)

        assertEquals(21, saveParams.commandId)
        assertEquals(10, saveParams.commandType)
        assertEquals(1, saveParams.heaterSelect)
        assertEquals(120, saveParams.targetTemp)
        assertEquals(30, saveParams.hysteresis)
        assertEquals(0, saveParams.sensorSelect)

        assertEquals(22, enable.commandId)
        assertEquals(11, enable.commandType)

        assertEquals(23, disable.commandId)
        assertEquals(12, disable.commandType)
    }

    @Test
    fun heaterParameterDiffOnlyTracksHeaterFields() {
        val base = config()

        assertTrue(base.hasHeaterParameterDiff(base.copy(deviceHeaterTargetTemp = 95)))
        assertTrue(base.hasHeaterParameterDiff(base.copy(deviceHeaterHysteresisTemp = 8)))
        assertTrue(base.hasHeaterParameterDiff(base.copy(deviceHeaterSelect = 2)))
        assertTrue(base.hasHeaterParameterDiff(base.copy(deviceHeaterSensorSelect = 1)))
        assertFalse(base.hasHeaterParameterDiff(base.copy(deviceStandaloneWaterTimedTicks = 200)))
    }

    private fun config(
        heaterSelect: Int = 1,
        targetTemp: Int = 90,
        hysteresis: Int = 5,
        sensorSelect: Int = 0,
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
            deviceHeaterTargetTemp = targetTemp,
            deviceHeaterHysteresisTemp = hysteresis,
            deviceHeaterSelect = heaterSelect,
            deviceHeaterSensorSelect = sensorSelect,
            deviceStandaloneWaterMode = 2,
            deviceStandaloneWaterTimedTicks = 100,
            deviceWaterOutlet1CalibrationPercent = 100,
            deviceWaterOutlet2CalibrationPercent = 100,
            deviceWaterOutlet3CalibrationPercent = 100,
            deviceWaterOutlet4CalibrationPercent = 100,
            deviceChickenOilCalibrationPercent = 100,
            deviceBonePasteCalibrationPercent = 100,
            deviceSparePumpMode = 0,
        )
}
