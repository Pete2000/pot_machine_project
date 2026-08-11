package com.example.plccontroller.data.plc

import com.example.plccontroller.domain.PlcRegisterMap
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.SocketTimeoutException

class ModbusRtuTest {
    @Test
    fun crc16MatchesKnownFieldFrames() {
        assertArrayEquals(
            hex("85 F3"),
            crc16(hex("01 03 00 C8 00 0B")),
        )
        assertArrayEquals(
            hex("8D EB"),
            crc16(hex("01 05 01 90 FF 00")),
        )
    }

    @Test
    fun crcValidationAcceptsCompleteFrameAndRejectsMutatedFrame() {
        val frame = hex("01 03 00 C8 00 0B 85 F3")

        assertTrue(hasValidCrc(frame))

        val mutated = frame.copyOf()
        mutated[3] = 0xC9.toByte()
        assertFalse(hasValidCrc(mutated))
    }

    @Test
    fun pollRegisterSnapshotBuildsExpectedReadFrameAndParsesRegisters() =
        runTest {
            val registers = listOf(52, 51, 0, 0, 0, 0, 0, 0, 0, 0, 3)
            val transport =
                RecordingTransport(
                    responses = mutableListOf(registerResponse(registers)),
                )
            val controller =
                PlcController(
                    initialSlaveId = 1,
                    registerMap = PlcRegisterMap(),
                    transport = transport,
                )

            val snapshot = controller.pollRegisterSnapshotStable(blockStart = 200, blockCount = 11)

            assertArrayEquals(
                hex("01 03 00 C8 00 0B 85 F3"),
                transport.requests.single(),
            )
            assertEquals(registers, snapshot.holdingRegisters)
        }

    @Test
    fun writeHeartbeatBuildsExpectedWriteCoilFrameAndAcceptsEcho() =
        runTest {
            val transport = RecordingTransport { request -> request.copyOf() }
            val controller =
                PlcController(
                    initialSlaveId = 1,
                    registerMap = PlcRegisterMap(),
                    transport = transport,
                )

            controller.writeHeartbeat(enabled = true)

            assertArrayEquals(
                hex("01 05 01 90 FF 00 8D EB"),
                transport.requests.single(),
            )
        }

    @Test
    fun failedHeartbeatWriteDoesNotAdvanceCachedHeartbeatValue() =
        runTest {
            var invocation = 0
            val transport =
                RecordingTransport { request ->
                    invocation += 1
                    if (invocation == 1) {
                        throw IllegalStateException("simulated write failure")
                    }
                    request.copyOf()
                }
            val controller =
                PlcController(
                    initialSlaveId = 1,
                    registerMap = PlcRegisterMap(),
                    transport = transport,
                )

            assertTrue(runCatching { controller.writeHeartbeat(enabled = true) }.isFailure)

            assertTrue(controller.toggleHeartbeat())
            assertArrayEquals(
                hex("01 05 01 90 FF 00 8D EB"),
                transport.requests.last(),
            )
        }

    @Test
    fun registerReadRejectsValidCrcResponseWithTooFewRegisters() =
        runTest {
            val transport =
                RecordingTransport(
                    responses = mutableListOf(registerResponse(List(10) { it })),
                )
            val controller =
                PlcController(
                    initialSlaveId = 1,
                    registerMap = PlcRegisterMap(),
                    transport = transport,
                )

            val error =
                runCatching {
                    controller.pollRegisterSnapshotStable(blockStart = 200, blockCount = 11)
                }.exceptionOrNull()

            assertTrue(error is IllegalStateException)
            assertTrue(
                error
                    ?.cause
                    ?.message
                    .orEmpty()
                    .contains("response size"),
            )
        }

    @Test
    fun stablePollFastFailsWhenCriticalCommunicationBlocksAreUnavailable() =
        runTest {
            val transport =
                RecordingTransport {
                    throw SocketTimeoutException("simulated timeout")
                }
            val controller =
                PlcController(
                    initialSlaveId = 1,
                    registerMap = PlcRegisterMap(),
                    transport = transport,
                )

            val error =
                runCatching {
                    controller.pollSnapshotStable()
                }.exceptionOrNull()

            assertTrue(error is PlcCommunicationUnavailableException)
            assertEquals(2, transport.requests.size)
        }

    @Test
    fun mockPhaseCommandMirrorsStatusRegistersForFastLocalVerification() =
        runTest {
            val controller =
                PlcController(
                    initialSlaveId = 1,
                    registerMap = PlcRegisterMap(),
                    transport = MockModbusTransport(),
                )

            controller.writePhaseCommand(
                PlcPhaseCommand(
                    commandId = 77,
                    jobSlotMask = 15,
                    waterDuration1 = 10,
                    waterDuration2 = 20,
                    waterDuration3 = 30,
                    waterDuration4 = 40,
                    activeTopLeftLogicalSlot = 1,
                    chickenOilDuration = 5,
                    bonePasteDuration = 6,
                    phaseNo = 2,
                ),
            )

            val snapshot = controller.pollPhaseStatusClean()

            assertEquals(listOf(77, 77, 77, 3, 0), snapshot.phaseStatusRegisters.take(5))
        }

    private class RecordingTransport(
        private val responses: MutableList<ByteArray> = mutableListOf(),
        private val responder: ((ByteArray) -> ByteArray)? = null,
    ) : ModbusTransport {
        val requests = mutableListOf<ByteArray>()

        constructor(responder: (ByteArray) -> ByteArray) : this(
            responses = mutableListOf(),
            responder = responder,
        )

        override suspend fun transact(request: ByteArray): ByteArray {
            requests += request.copyOf()
            return responder?.invoke(request) ?: responses.removeAt(0)
        }
    }

    private fun registerResponse(values: List<Int>): ByteArray {
        val registerBytes =
            values
                .flatMap { value ->
                    listOf(
                        ((value shr 8) and 0xFF).toByte(),
                        (value and 0xFF).toByte(),
                    )
                }.toByteArray()
        val payload =
            byteArrayOf(
                0x01,
                0x03,
                registerBytes.size.toByte(),
            ) + registerBytes
        return payload + crc16(payload)
    }

    private fun hex(value: String): ByteArray =
        value
            .trim()
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() }
            .map { it.toInt(16).toByte() }
            .toByteArray()
}
