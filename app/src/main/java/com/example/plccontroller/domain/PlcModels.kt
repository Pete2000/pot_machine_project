package com.example.plccontroller.domain

enum class PlcConnectionState {
    Disconnected,
    Connecting,
    Connected,
    Fault,
}

enum class PlcParity {
    None,
    Even,
    Odd,
}

data class PlcSerialSettings(
    val protocol: String = "Modbus RTU",
    val transport: String = "RS485",
    val baudRate: Int = 19_200,
    val dataBits: Int = 8,
    val parity: PlcParity = PlcParity.None,
    val stopBits: Int = 1,
    val slaveId: Int = 1,
)

data class PlcReadButtonMap(
    val emergencyStop: Int = 0x012C,
    val addWater: Int = 0x012D,
    val transferPotComplete: Int = 0x012E,
    val emergencyWater: Int = 0x012F,
    val emergencyBonePaste: Int = 0x0130,
    val emergencyChickenOil: Int = 0x0131,
    // The sheet clearly shows the button group continues after X05, but the
    // extracted notes do not yet pin every button name to every address.
    val manualOutputValveButtonsBase: Int = 0x0132,
    val manualOutputValveButtonCount: Int = 4,
)

data class PlcOutputStatusMap(
    val mainHeater: Int = 0x0140,
    val backupHeater: Int = 0x0141,
    val outletValve0: Int = 0x0142,
    val outletValve1: Int = 0x0143,
    val outletValve2: Int = 0x0144,
    val outletValve3: Int = 0x0145,
    val inletValve: Int = 0x0146,
    val singleOutletValve: Int = 0x0147,
    val sprayValve: Int = 0x0148,
    val chickenOilPump: Int = 0x0149,
    val bonePastePump: Int = 0x014A,
) {
    fun outletValves(): List<Int> =
        listOf(
            outletValve0,
            outletValve1,
            outletValve2,
            outletValve3,
        )
}

data class PlcReadHoldingRegisterMap(
    val temperatureSensor0: Int = 0x00C8,
    val temperatureSensor1: Int = 0x00C9,
    val liquidLevelState: Int = 0x00D2,
)

data class PlcWriteCoilMap(
    val mainHeater: Int = 0x0064,
    val backupHeater: Int = 0x0065,
    val outletValve0: Int = 0x0066,
    val outletValve1: Int = 0x0067,
    val outletValve2: Int = 0x0068,
    val outletValve3: Int = 0x0069,
    val inletValve: Int = 0x006A,
    val singleOutletValve: Int = 0x006B,
    val sprayValve: Int = 0x006C,
    val chickenOilPump: Int = 0x006D,
    val bonePastePump: Int = 0x006E,
) {
    fun outletValves(): List<Int> =
        listOf(
            outletValve0,
            outletValve1,
            outletValve2,
            outletValve3,
        )
}

data class PlcEmergencyDurationRegisterMap(
    val chickenOilDuration: Int = 0x00D4,
    val bonePasteDuration: Int = 0x00D6,
)

data class PlcPrototypeDispatchRegisterMap(
    val recipeCodeRegister: Int = 0x0064,
    val quantityRegister: Int = 0x0065,
    val targetTemperatureRegister: Int = 0x0066,
    val cookSecondsRegister: Int = 0x0067,
    val spiceLevelRegister: Int = 0x0068,
    val startCommandRegister: Int = 0x006E,
    val machineStateRegister: Int = 0x0078,
    val alarmCodeRegister: Int = 0x0079,
)

data class PlcRegisterMap(
    val serial: PlcSerialSettings = PlcSerialSettings(),
    val readButtons: PlcReadButtonMap = PlcReadButtonMap(),
    val readOutputStatus: PlcOutputStatusMap = PlcOutputStatusMap(),
    val readHoldingRegisters: PlcReadHoldingRegisterMap = PlcReadHoldingRegisterMap(),
    val writeCoils: PlcWriteCoilMap = PlcWriteCoilMap(),
    val emergencyDurationRegisters: PlcEmergencyDurationRegisterMap = PlcEmergencyDurationRegisterMap(),
    val heartbeatCoil: Int = 0x0190,
    // Kept only so the early demo flow can continue to compile before we
    // replace the prototype order-dispatch path with real control actions.
    val prototypeDispatchRegisters: PlcPrototypeDispatchRegisterMap = PlcPrototypeDispatchRegisterMap(),
)

data class PlcWriteResult(
    val success: Boolean,
    val message: String,
)

data class PlcSerialDiagnostic(
    val configured: Boolean = false,
    val rawModeOk: Boolean? = null,
    val method: String = "未配置",
    val summary: String = "串口尚未打开",
    val detail: String = "等待首次 Modbus 通讯后生成串口 RAW 诊断。",
    val updatedAtMs: Long? = null,
)

data class PlcCommunicationConfig(
    val serialPortPath: String,
    val baudRate: Int,
    val dataBits: Int,
    val parityName: String,
    val stopBits: Int,
    val slaveId: Int,
    val readTimeoutMs: Long,
    val writeTimeoutMs: Long,
    val retryCount: Int,
    val frameGapMs: Long,
    val registerOnlyMode: Boolean,
    val registerOnlyBlockStart: Int,
    val registerOnlyBlockCount: Int,
    val heartbeatPeriodMs: Long,
    val activePollingIntervalMs: Long,
    val idlePollingIntervalMs: Long,
    val orderPollingIntervalMs: Long,
    val connectionTestRegister: Int,
) {
    val normalizedRetryCount: Int
        get() = retryCount.coerceAtLeast(0)

    val normalizedReadTimeoutMs: Long
        get() = readTimeoutMs.coerceAtLeast(100L)

    val normalizedWriteTimeoutMs: Long
        get() = writeTimeoutMs.coerceAtLeast(100L)

    val normalizedFrameGapMs: Long
        get() = frameGapMs.coerceAtLeast(10L)
}

data class PlcPollingSnapshot(
    val mirrorBits: List<Boolean> = emptyList(),
    val holdingRegisters: List<Int> = emptyList(),
    val phaseStatusRegisters: List<Int> = emptyList(),
    val heaterStatusRegisters: List<Int> = emptyList(),
    val emergencyStatusRegisters: List<Int> = emptyList(),
    val pollWarnings: List<String> = emptyList(),
    val serialDiagnostic: PlcSerialDiagnostic = PlcSerialDiagnostic(),
    val lastHeartbeatValue: Boolean = false,
    val lastSuccessfulPollAtMs: Long? = null,
) {
    val inputMirrorBits: List<Boolean>
        get() = mirrorBits.take(20)

    val outputMirrorBits: List<Boolean>
        get() = mirrorBits.drop(20).take(20)

    val liquidLevelState: Int?
        get() = holdingRegisters.getOrNull(10)

    val temperatureSensor0: Int?
        get() = holdingRegisters.getOrNull(0)

    val temperatureSensor1: Int?
        get() = holdingRegisters.getOrNull(1)

    val emergencyStopInput: Boolean
        get() = inputMirrorBits.getOrElse(0) { false }

    // X00/M300 is a normally-closed safety loop: 1=healthy, 0=pressed or open circuit.
    val emergencyStopActive: Boolean
        get() = !emergencyStopInput

    val addWaterInput: Boolean
        get() = inputMirrorBits.getOrElse(1) { false }

    val transferCompleteInput: Boolean
        get() = inputMirrorBits.getOrElse(2) { false }

    val emergencyWaterInput: Boolean
        get() = inputMirrorBits.getOrElse(3) { false }

    val emergencyBonePasteInput: Boolean
        get() = inputMirrorBits.getOrElse(4) { false }

    val emergencyChickenOilInput: Boolean
        get() = inputMirrorBits.getOrElse(5) { false }

    val mainHeaterOutput: Boolean
        get() = outputMirrorBits.getOrElse(0) { false }

    val backupHeaterOutput: Boolean
        get() = outputMirrorBits.getOrElse(1) { false }

    val waterValve0Output: Boolean
        get() = outputMirrorBits.getOrElse(2) { false }

    val waterValve1Output: Boolean
        get() = outputMirrorBits.getOrElse(3) { false }

    val waterValve2Output: Boolean
        get() = outputMirrorBits.getOrElse(4) { false }

    val waterValve3Output: Boolean
        get() = outputMirrorBits.getOrElse(5) { false }

    val levelFillValveOutput: Boolean
        get() = outputMirrorBits.getOrElse(6) { false }

    val singleWaterValveOutput: Boolean
        get() = outputMirrorBits.getOrElse(7) { false }

    val sprayValveOutput: Boolean
        get() = outputMirrorBits.getOrElse(8) { false }

    val chickenOilPumpOutput: Boolean
        get() = outputMirrorBits.getOrElse(9) { false }

    val bonePastePumpOutput: Boolean
        get() = outputMirrorBits.getOrElse(10) { false }

    val sparePumpOutput: Boolean
        get() = outputMirrorBits.getOrElse(11) { false }

    val phaseActionStateCode: Int?
        get() = phaseStatusRegisters.getOrNull(3)

    val phaseResultCode: Int?
        get() = phaseStatusRegisters.getOrNull(4)

    val heaterActionStateCode: Int?
        get() = heaterStatusRegisters.getOrNull(3)

    val heaterResultCode: Int?
        get() = heaterStatusRegisters.getOrNull(4)

    val emergencyActionStateCode: Int?
        get() = emergencyStatusRegisters.getOrNull(0)

    val emergencyResultCode: Int?
        get() = emergencyStatusRegisters.getOrNull(2)

    val inputSummary: String
        get() =
            buildList {
                if (emergencyStopActive) add("ESTOP")
                if (addWaterInput) add("ADD_WATER")
                if (transferCompleteInput) add("TRANSFER_OK")
                if (emergencyWaterInput) add("EMG_WATER")
                if (emergencyBonePasteInput) add("EMG_BONE")
                if (emergencyChickenOilInput) add("EMG_CHICKEN")
            }.ifEmpty { listOf("none") }.joinToString(",")

    val outputSummary: String
        get() =
            buildList {
                if (mainHeaterOutput) add("MAIN_HEATER")
                if (backupHeaterOutput) add("BACKUP_HEATER")
                if (waterValve0Output) add("WATER0")
                if (waterValve1Output) add("WATER1")
                if (waterValve2Output) add("WATER2")
                if (waterValve3Output) add("WATER3")
                if (levelFillValveOutput) add("LEVEL_FILL")
                if (singleWaterValveOutput) add("SINGLE_WATER")
                if (sprayValveOutput) add("SPRAY")
                if (chickenOilPumpOutput) add("CHICKEN")
                if (bonePastePumpOutput) add("BONE")
                if (sparePumpOutput) add("SPARE")
            }.ifEmpty { listOf("none") }.joinToString(",")
}
