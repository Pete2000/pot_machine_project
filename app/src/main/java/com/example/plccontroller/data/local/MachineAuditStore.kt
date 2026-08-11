package com.example.plccontroller.data.local

data class PlcCommandAuditRequest(
    val commandId: Int,
    val orderId: String? = null,
    val commandType: String,
    val commandName: String,
    val requestRegisterStart: Int? = null,
    val requestPayloadJson: String? = null,
)

interface MachineAuditStore {
    suspend fun beginPlcCommand(request: PlcCommandAuditRequest): Long?

    suspend fun finishPlcCommand(
        auditId: Long?,
        result: String,
        errorMessage: String? = null,
    )

    suspend fun recordFault(
        faultType: String,
        severity: String,
        message: String,
        snapshotJson: String? = null,
    ): Long?

    suspend fun resolveFault(eventId: Long?)
}

object NoOpMachineAuditStore : MachineAuditStore {
    override suspend fun beginPlcCommand(request: PlcCommandAuditRequest): Long? = null

    override suspend fun finishPlcCommand(
        auditId: Long?,
        result: String,
        errorMessage: String?,
    ) = Unit

    override suspend fun recordFault(
        faultType: String,
        severity: String,
        message: String,
        snapshotJson: String?,
    ): Long? = null

    override suspend fun resolveFault(eventId: Long?) = Unit
}

suspend fun <T> MachineAuditStore.auditPlcCommand(
    request: PlcCommandAuditRequest,
    block: suspend () -> T,
): T {
    // Auditing is diagnostic only. A full/unavailable database must never block
    // a PLC command or replace the command's real failure with an audit failure.
    val auditId = runCatching { beginPlcCommand(request) }.getOrNull()
    return try {
        block().also {
            runCatching { finishPlcCommand(auditId, result = "SUCCESS") }
        }
    } catch (error: Throwable) {
        val result =
            if (error.message.orEmpty().contains("超时") ||
                error.message.orEmpty().contains("timeout", ignoreCase = true)
            ) {
                "TIMEOUT"
            } else {
                "FAILED"
            }
        runCatching { finishPlcCommand(auditId, result = result, errorMessage = error.message) }
        throw error
    }
}
