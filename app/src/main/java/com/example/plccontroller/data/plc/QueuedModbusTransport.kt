package com.example.plccontroller.data.plc

import com.example.plccontroller.domain.PlcCommunicationConfig
import com.example.plccontroller.domain.PlcSerialDiagnostic
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import java.util.concurrent.atomic.AtomicBoolean

data class ModbusQueueConfig(
    val readTimeoutMs: Long = 800L,
    val writeTimeoutMs: Long = 800L,
    val retries: Int = 2,
    val frameGapMs: Long = 200L,
)

class QueuedModbusTransport(
    private val delegate: ModbusTransport,
    initialConfig: ModbusQueueConfig = ModbusQueueConfig(),
    parentScope: CoroutineScope? = null,
) : ModbusTransport {
    @Volatile
    private var config: ModbusQueueConfig = initialConfig

    private val requests = Channel<QueuedRequest>(capacity = 64)
    private val closed = AtomicBoolean(false)
    private val workerJob: Job

    init {
        val scope = parentScope ?: CoroutineScope(SupervisorJob() + Dispatchers.IO)
        workerJob =
            scope.launch {
                var activeRequest: QueuedRequest? = null
                try {
                    for (request in requests) {
                        activeRequest = request
                        if (!request.response.isCancelled) {
                            try {
                                request.response.complete(executeWithRetry(request.frame))
                            } catch (error: TimeoutCancellationException) {
                                request.response.completeExceptionally(error)
                            } catch (error: CancellationException) {
                                request.response.completeExceptionally(transportClosedError())
                                throw error
                            } catch (error: Throwable) {
                                request.response.completeExceptionally(error)
                            }
                        }
                        activeRequest = null
                        // Keep a quiet interval between complete RTU transactions.
                        delay(config.frameGapMs.coerceAtLeast(10L))
                    }
                } finally {
                    activeRequest?.response?.completeExceptionally(transportClosedError())
                    while (true) {
                        val pending = requests.tryReceive().getOrNull() ?: break
                        pending.response.completeExceptionally(transportClosedError())
                    }
                }
            }
    }

    override suspend fun transact(request: ByteArray): ByteArray {
        check(!closed.get() && workerJob.isActive) { "Modbus queue is not active" }
        val queued =
            QueuedRequest(
                frame = request,
                response = CompletableDeferred(),
            )
        requests.send(queued)
        return queued.response.await()
    }

    override fun serialDiagnostic(): PlcSerialDiagnostic = delegate.serialDiagnostic()

    override fun updateConfig(config: PlcCommunicationConfig) {
        val newQueueConfig =
            ModbusQueueConfig(
                readTimeoutMs = config.readTimeoutMs,
                writeTimeoutMs = config.writeTimeoutMs,
                retries = config.retryCount,
                frameGapMs = config.frameGapMs,
            )
        delegate.updateConfig(config)
        this.config = newQueueConfig
    }

    override fun resetConnection() {
        check(!closed.get() && workerJob.isActive) { "Modbus queue is not active" }
        delegate.resetConnection()
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        requests.close()
        workerJob.cancel(CancellationException("Modbus transport closed"))
        delegate.close()
    }

    private suspend fun executeWithRetry(frame: ByteArray): ByteArray {
        var lastError: Throwable? = null
        repeat(config.retries + 1) { attempt ->
            try {
                return withTimeout(timeoutFor(frame)) {
                    delegate.transact(frame)
                }
            } catch (error: TimeoutCancellationException) {
                lastError = error
                if (attempt < config.retries) {
                    delay(config.frameGapMs)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                lastError = error
                if (attempt < config.retries) {
                    delay(config.frameGapMs)
                }
            }
        }
        throw lastError ?: IllegalStateException("Modbus transaction failed")
    }

    private fun timeoutFor(frame: ByteArray): Long {
        val function = frame.getOrNull(1)?.toInt()?.and(0xFF) ?: 0
        return when (function) {
            FUNCTION_READ_COILS,
            FUNCTION_READ_HOLDING_REGISTERS,
            -> config.readTimeoutMs
            else -> config.writeTimeoutMs
        }
    }

    private data class QueuedRequest(
        val frame: ByteArray,
        val response: CompletableDeferred<ByteArray>,
    )

    private fun transportClosedError(): IllegalStateException = IllegalStateException("Modbus transport closed")
}
