package com.example.plccontroller.data.plc

import android.util.Log
import com.example.plccontroller.domain.PlcCommunicationConfig
import com.example.plccontroller.domain.PlcSerialDiagnostic

/**
 * Owns Modbus frame construction and response validation.
 *
 * Higher-level polling and device commands stay in [PlcController], while this
 * client keeps the protocol boundary small and independently reviewable.
 */
internal class ModbusFrameClient(
    initialSlaveId: Int,
    private val transport: ModbusTransport,
) {
    private var slaveId: Int = initialSlaveId

    fun updateSlaveId(newId: Int) {
        slaveId = newId
    }

    fun updateCommunicationConfig(config: PlcCommunicationConfig) {
        updateSlaveId(config.slaveId)
        transport.updateConfig(config)
    }

    fun serialDiagnostic(): PlcSerialDiagnostic = transport.serialDiagnostic()

    fun resetConnection() {
        transport.resetConnection()
    }

    suspend fun readCoils(
        startAddress: Int,
        count: Int,
    ): List<Boolean> {
        require(count in 1..2000) { "Invalid coil count: $count" }
        val payload =
            byteArrayOf(
                slaveId.toByte(),
                FUNCTION_READ_COILS.toByte(),
                ((startAddress shr 8) and 0xFF).toByte(),
                (startAddress and 0xFF).toByte(),
                ((count shr 8) and 0xFF).toByte(),
                (count and 0xFF).toByte(),
            )
        val response = transport.transact(payload + crc16(payload))
        val expectedByteCount = (count + 7) / 8
        ModbusResponseValidator.validate(response, slaveId, FUNCTION_READ_COILS, 3 + expectedByteCount + 2, "coil")
        require((response[2].toInt() and 0xFF) == expectedByteCount) { "Unexpected Modbus coil byte count" }
        return List(count) { index ->
            val byteIndex = 3 + (index / 8)
            val bitIndex = index % 8
            ((response[byteIndex].toInt() shr bitIndex) and 0x01) == 1
        }
    }

    suspend fun readHoldingRegisters(
        startRegisterAddress: Int,
        count: Int,
    ): List<Int> {
        require(count in 1..125) { "Invalid register count: $count" }
        val payload =
            byteArrayOf(
                slaveId.toByte(),
                FUNCTION_READ_HOLDING_REGISTERS.toByte(),
                ((startRegisterAddress shr 8) and 0xFF).toByte(),
                (startRegisterAddress and 0xFF).toByte(),
                ((count shr 8) and 0xFF).toByte(),
                (count and 0xFF).toByte(),
            )
        val response = transport.transact(payload + crc16(payload))
        val expectedByteCount = count * 2
        ModbusResponseValidator.validate(
            response = response,
            expectedSlaveId = slaveId,
            expectedFunction = FUNCTION_READ_HOLDING_REGISTERS,
            expectedSize = 3 + expectedByteCount + 2,
            context = "register",
        )
        val byteCount = response[2].toInt() and 0xFF
        require(byteCount == expectedByteCount) {
            "Unexpected Modbus register byte count: $byteCount (expected $expectedByteCount)"
        }
        return List(count) { index ->
            val high = response[3 + index * 2].toInt() and 0xFF
            val low = response[4 + index * 2].toInt() and 0xFF
            (high shl 8) or low
        }
    }

    suspend fun writeSingleCoil(
        address: Int,
        enabled: Boolean,
        tolerateCrcFailure: Boolean = false,
    ) {
        val value = if (enabled) MODBUS_TRUE_WORD else MODBUS_FALSE_WORD
        val payload =
            byteArrayOf(
                slaveId.toByte(),
                FUNCTION_WRITE_SINGLE_COIL.toByte(),
                ((address shr 8) and 0xFF).toByte(),
                (address and 0xFF).toByte(),
                ((value shr 8) and 0xFF).toByte(),
                (value and 0xFF).toByte(),
            )
        val response = transport.transact(payload + crc16(payload))
        try {
            ModbusResponseValidator.validate(response, slaveId, FUNCTION_WRITE_SINGLE_COIL, 8, "write-coil")
        } catch (error: IllegalArgumentException) {
            val responseEchoMatches = response.size >= 6 && response.copyOfRange(0, 6).contentEquals(payload)
            val isCrcError = error.message.orEmpty().contains("CRC", ignoreCase = true)
            if (tolerateCrcFailure && responseEchoMatches && isCrcError) {
                Log.w(
                    TAG_SERIAL,
                    "Heartbeat coil response CRC invalid but payload echo matched; accepting as compatibility workaround.",
                )
                return
            }
            throw error
        }
        require(response.copyOfRange(0, 6).contentEquals(payload)) { "Modbus write-coil response is not an echo" }
    }

    suspend fun writeSingleRegister(
        registerAddress: Int,
        value: Int,
    ) {
        val payload =
            byteArrayOf(
                slaveId.toByte(),
                FUNCTION_WRITE_SINGLE_REGISTER.toByte(),
                ((registerAddress shr 8) and 0xFF).toByte(),
                (registerAddress and 0xFF).toByte(),
                ((value shr 8) and 0xFF).toByte(),
                (value and 0xFF).toByte(),
            )
        val response = transport.transact(payload + crc16(payload))
        ModbusResponseValidator.validate(response, slaveId, FUNCTION_WRITE_SINGLE_REGISTER, 8, "write-register")
        require(response.copyOfRange(0, 6).contentEquals(payload)) { "Modbus write-register response is not an echo" }
    }

    suspend fun writeMultipleRegisters(
        startRegisterAddress: Int,
        values: List<Int>,
    ) {
        require(values.isNotEmpty()) { "At least one register value is required" }
        require(values.size <= MAX_WRITE_MULTIPLE_REGISTER_COUNT) {
            "Cannot write more than $MAX_WRITE_MULTIPLE_REGISTER_COUNT registers in one Modbus frame"
        }
        val registerBytes =
            values
                .flatMap { value ->
                    listOf(
                        ((value shr 8) and 0xFF).toByte(),
                        (value and 0xFF).toByte(),
                    )
                }.toByteArray()
        val registerCount = values.size
        val payload =
            byteArrayOf(
                slaveId.toByte(),
                FUNCTION_WRITE_MULTIPLE_REGISTERS.toByte(),
                ((startRegisterAddress shr 8) and 0xFF).toByte(),
                (startRegisterAddress and 0xFF).toByte(),
                ((registerCount shr 8) and 0xFF).toByte(),
                (registerCount and 0xFF).toByte(),
                registerBytes.size.toByte(),
            ) + registerBytes
        val response = transport.transact(payload + crc16(payload))
        ModbusResponseValidator.validate(response, slaveId, FUNCTION_WRITE_MULTIPLE_REGISTERS, 8, "write-multiple-registers")
        val expectedPrefix =
            byteArrayOf(
                slaveId.toByte(),
                FUNCTION_WRITE_MULTIPLE_REGISTERS.toByte(),
                ((startRegisterAddress shr 8) and 0xFF).toByte(),
                (startRegisterAddress and 0xFF).toByte(),
                ((registerCount shr 8) and 0xFF).toByte(),
                (registerCount and 0xFF).toByte(),
            )
        require(response.copyOfRange(0, 6).contentEquals(expectedPrefix)) { "Modbus write-multiple-registers response mismatch" }
    }
}
