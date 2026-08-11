package com.example.plccontroller

object AppConfig {
    // Public builds must be safe on a fresh install. Production endpoints belong
    // in the device-specific integration layer, never in the open-source defaults.
    const val businessBaseUrl = "http://127.0.0.1:8050"
    const val managementBaseUrl = "http://127.0.0.1:8080"
    const val formulaDeviceCode = ""
    const val plcSerialPortPath = "mock"
    const val plcBaudRate = 19_200
    const val plcDataBits = 8
    const val plcParity = "NONE"
    const val plcStopBits = 1
    const val plcSlaveId = 1
    const val orderPollingIntervalMs = 5_000L
    const val plcReadTimeoutMs = 500L
    const val plcWriteTimeoutMs = 500L
    const val plcRetryCount = 1
    const val plcFrameGapMs = 200L
    const val plcRegisterOnlyMode = false
    const val plcRegisterOnlyBlockStart = 0
    const val plcRegisterOnlyBlockCount = 0
    const val plcHeartbeatPeriodMs = 1_000L
    const val plcActivePollingIntervalMs = 200L
    const val plcIdlePollingIntervalMs = 500L
    const val plcConnectionTestRegister = 200
    const val formulaSyncIntervalSeconds = 30L
    const val localOrderHistoryLimit = 80

    const val deviceHeaterTargetTemp = 90
    const val deviceHeaterHysteresisTemp = 5
    const val deviceHeaterSelect = 1
    const val deviceHeaterSensorSelect = 0
    const val deviceStandaloneWaterMode = 2
    const val deviceStandaloneWaterTimedTicks = 100
    const val deviceWaterOutletCalibrationPercent = 100
    const val deviceChickenOilCalibrationPercent = 100
    const val deviceBonePasteCalibrationPercent = 100
    const val deviceSparePumpMode = 0
}
