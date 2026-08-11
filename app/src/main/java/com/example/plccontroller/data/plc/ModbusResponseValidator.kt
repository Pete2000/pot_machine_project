package com.example.plccontroller.data.plc

internal object ModbusResponseValidator {
    fun validate(
        response: ByteArray,
        expectedSlaveId: Int,
        expectedFunction: Int,
        expectedSize: Int? = null,
        context: String = "response",
    ) {
        require(response.size >= MODBUS_MIN_RESPONSE_SIZE) {
            "Modbus $context response is too short: ${response.size} bytes"
        }
        if (!hasValidCrc(response)) {
            val payload = response.copyOfRange(0, response.size - 2)
            val expectedCrc = crc16(payload)
            val hex = response.toHexString()
            val expectedHex = expectedCrc.toHexString()

            throw IllegalArgumentException(
                "Invalid Modbus $context CRC (size=${response.size}). Got: $hex; expected CRC $expectedHex",
            )
        }

        val responseSlaveId = response[0].toInt() and 0xFF
        require(responseSlaveId == expectedSlaveId) {
            "Unexpected Modbus $context slave ID: $responseSlaveId (expected $expectedSlaveId)"
        }
        val function = response[1].toInt() and 0xFF
        if (function == (expectedFunction or 0x80)) {
            val exceptionCode = response[2].toInt() and 0xFF
            throw IllegalStateException(
                "Modbus $context exception 0x%02X for function 0x%02X".format(exceptionCode, expectedFunction),
            )
        }
        require(function == expectedFunction) {
            "Unexpected Modbus $context function: 0x%02X (expected 0x%02X)".format(function, expectedFunction)
        }
        if (expectedSize != null) {
            require(response.size == expectedSize) {
                "Unexpected Modbus $context response size: ${response.size} (expected $expectedSize)"
            }
        }
    }
}

private const val MODBUS_MIN_RESPONSE_SIZE = 5
