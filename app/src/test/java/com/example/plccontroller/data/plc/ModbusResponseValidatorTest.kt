package com.example.plccontroller.data.plc

import org.junit.Assert.assertThrows
import org.junit.Test

class ModbusResponseValidatorTest {
    @Test
    fun validResponseIsAccepted() {
        val payload = byteArrayOf(1, 3, 2, 0, 15)
        val response = payload + crc16(payload)

        ModbusResponseValidator.validate(
            response = response,
            expectedSlaveId = 1,
            expectedFunction = 3,
            expectedSize = 7,
            context = "register",
        )
    }

    @Test
    fun crcMutationIsRejected() {
        val payload = byteArrayOf(1, 3, 2, 0, 15)
        val response = payload + crc16(payload)
        response[3] = 0x20

        assertThrows(IllegalArgumentException::class.java) {
            ModbusResponseValidator.validate(response, 1, 3, 7, "register")
        }
    }

    @Test
    fun exceptionResponseIsReported() {
        val payload = byteArrayOf(1, 0x83.toByte(), 2)
        val response = payload + crc16(payload)

        assertThrows(IllegalStateException::class.java) {
            ModbusResponseValidator.validate(response, 1, 3, 5, "register")
        }
    }
}
