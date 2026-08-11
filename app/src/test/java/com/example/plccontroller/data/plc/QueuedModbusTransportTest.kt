package com.example.plccontroller.data.plc

import com.example.plccontroller.domain.PlcCommunicationConfig
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QueuedModbusTransportTest {
    @Test
    fun updateConfigPropagatesToDelegate() {
        val delegate = ConfigRecordingTransport()
        val transport = QueuedModbusTransport(delegate)
        val config = communicationConfig(frameGapMs = 345L)

        transport.updateConfig(config)

        assertEquals(config, delegate.lastConfig)
        transport.close()
    }

    @Test
    fun resetConnectionKeepsQueueUsable() =
        runTest {
            val delegate = ConfigRecordingTransport { byteArrayOf(7, 8) }
            val transport = QueuedModbusTransport(delegate, parentScope = this)

            transport.resetConnection()
            val response = transport.transact(byteArrayOf(1, 3))

            assertEquals(1, delegate.resetCount)
            assertTrue(response.contentEquals(byteArrayOf(7, 8)))
            transport.close()
        }

    @Test
    fun closeCompletesInFlightRequestAndClosesDelegate() =
        runTest {
            val started = CompletableDeferred<Unit>()
            val neverCompletes = CompletableDeferred<ByteArray>()
            val delegate =
                ConfigRecordingTransport { _ ->
                    started.complete(Unit)
                    neverCompletes.await()
                }
            val transport =
                QueuedModbusTransport(
                    delegate = delegate,
                    initialConfig = ModbusQueueConfig(retries = 0),
                    parentScope = this,
                )
            val result =
                async {
                    runCatching { transport.transact(byteArrayOf(1, 3)) }
                }
            started.await()

            transport.close()

            val completed = withTimeout(1_000L) { result.await() }
            assertTrue(completed.isFailure)
            assertTrue(delegate.closed)
        }

    @Test
    fun timeoutDoesNotCrashQueueAndAllowsRetry() =
        runTest {
            var attempts = 0
            val delegate =
                ConfigRecordingTransport { _ ->
                    attempts++
                    if (attempts < 2) {
                        throwTimeout()
                    }
                    byteArrayOf(4, 5)
                }
            val transport =
                QueuedModbusTransport(
                    delegate = delegate,
                    initialConfig = ModbusQueueConfig(retries = 2, frameGapMs = 10L),
                    parentScope = this,
                )

            val result = transport.transact(byteArrayOf(1, 3))
            assertEquals(2, attempts)
            assertTrue(result.contentEquals(byteArrayOf(4, 5)))
            transport.close()
        }

    @Test
    fun completeTimeoutDoesNotCrashQueueForSubsequentRequests() =
        runTest {
            var shouldTimeout = true
            val delegate =
                ConfigRecordingTransport { _ ->
                    if (shouldTimeout) {
                        throwTimeout()
                    } else {
                        byteArrayOf(9, 9)
                    }
                }
            val transport =
                QueuedModbusTransport(
                    delegate = delegate,
                    initialConfig = ModbusQueueConfig(retries = 0, frameGapMs = 10L),
                    parentScope = this,
                )

            val result1 = runCatching { transport.transact(byteArrayOf(1, 3)) }
            assertTrue(result1.isFailure)
            assertTrue(result1.exceptionOrNull() is kotlinx.coroutines.TimeoutCancellationException)

            shouldTimeout = false
            val result2 = transport.transact(byteArrayOf(1, 3))
            assertTrue(result2.contentEquals(byteArrayOf(9, 9)))
            transport.close()
        }

    private suspend fun throwTimeout(): Nothing {
        withTimeout(1L) {
            delay(100L)
        }
        error("unreachable")
    }

    private class ConfigRecordingTransport(
        private var responder: suspend (ByteArray) -> ByteArray = { it.copyOf() },
    ) : ModbusTransport {
        var lastConfig: PlcCommunicationConfig? = null
        var closed: Boolean = false
        var resetCount: Int = 0

        override suspend fun transact(request: ByteArray): ByteArray = responder(request)

        override fun updateConfig(config: PlcCommunicationConfig) {
            lastConfig = config
        }

        override fun resetConnection() {
            resetCount += 1
        }

        override fun close() {
            closed = true
        }
    }

    private fun communicationConfig(frameGapMs: Long): PlcCommunicationConfig =
        PlcCommunicationConfig(
            serialPortPath = "mock://plc",
            baudRate = 19_200,
            dataBits = 8,
            parityName = "None",
            stopBits = 1,
            slaveId = 1,
            readTimeoutMs = 1_500L,
            writeTimeoutMs = 1_000L,
            retryCount = 1,
            frameGapMs = frameGapMs,
            registerOnlyMode = false,
            registerOnlyBlockStart = 0,
            registerOnlyBlockCount = 0,
            heartbeatPeriodMs = 5_000L,
            activePollingIntervalMs = 1_000L,
            idlePollingIntervalMs = 1_000L,
            orderPollingIntervalMs = 30_000L,
            connectionTestRegister = 200,
        )
}
