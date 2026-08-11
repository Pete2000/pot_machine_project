package com.example.plccontroller.data.plc

data class PlcPhaseCommand(
    val commandId: Int,
    val commandType: Int = 1,
    val jobSlotMask: Int,
    val waterDuration1: Int,
    val waterDuration2: Int,
    val waterDuration3: Int,
    val waterDuration4: Int,
    val activeTopLeftLogicalSlot: Int,
    val chickenOilDuration: Int,
    val bonePasteDuration: Int,
    val phaseNo: Int,
    val reservedRegisters: List<Int> = listOf(1, 0, 0, 0, 0),
) {
    fun toRegisters(): List<Int> =
        buildList {
            add(commandId)
            add(commandType)
            add(jobSlotMask)
            add(waterDuration1)
            add(waterDuration2)
            add(waterDuration3)
            add(waterDuration4)
            add(activeTopLeftLogicalSlot)
            add(chickenOilDuration)
            add(bonePasteDuration)
            add(phaseNo)
            addAll((reservedRegisters + List(5) { 0 }).take(5))
        }
}

data class PlcHeaterCommand(
    val commandId: Int,
    val commandType: Int,
    val heaterSelect: Int,
    val targetTemp: Int,
    val hysteresis: Int,
    val sensorSelect: Int = 0,
    val reservedRegisters: List<Int> = emptyList(),
) {
    fun toRegisters(): List<Int> =
        buildList {
            add(commandId)
            add(commandType)
            add(heaterSelect)
            add(targetTemp)
            add(hysteresis)
            add(sensorSelect)
            addAll(reservedRegisters)
        }
}

data class PlcEmergencyWaterCommand(
    val mode: Int,
    val timedDuration: Int,
    val sparePumpMode: Int,
    val reservedRegisters: List<Int> = emptyList(),
) {
    fun toRegisters(): List<Int> =
        buildList {
            add(mode)
            add(timedDuration)
            add(sparePumpMode)
            addAll((reservedRegisters + List(7) { 0 }).take(7))
        }
}
