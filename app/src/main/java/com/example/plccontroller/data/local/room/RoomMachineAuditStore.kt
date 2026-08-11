package com.example.plccontroller.data.local.room

import com.example.plccontroller.data.local.MachineAuditStore
import com.example.plccontroller.data.local.PlcCommandAuditRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class RoomMachineAuditStore(
    private val database: OrderDatabase,
) : MachineAuditStore {
    private val dao = database.dao()

    override suspend fun beginPlcCommand(request: PlcCommandAuditRequest): Long? =
        runCatching {
            databaseCall {
                val now = System.currentTimeMillis()
                pruneAuditData(now)
                dao.insertPlcCommandAudit(
                    PlcCommandAuditEntity(
                        commandId = request.commandId,
                        orderId = request.orderId,
                        commandType = request.commandType,
                        commandName = request.commandName,
                        requestRegisterStart = request.requestRegisterStart,
                        requestPayloadJson = request.requestPayloadJson,
                        txHex = null,
                        rxHex = null,
                        result = "PENDING",
                        errorMessage = null,
                        createdAt = now,
                        completedAt = null,
                    ),
                )
            }
        }.getOrNull()

    override suspend fun finishPlcCommand(
        auditId: Long?,
        result: String,
        errorMessage: String?,
    ) {
        if (auditId == null) return
        runCatching {
            databaseCall {
                dao.finishPlcCommandAudit(
                    auditId = auditId,
                    result = result,
                    errorMessage = errorMessage,
                    completedAt = System.currentTimeMillis(),
                )
            }
        }
    }

    override suspend fun recordFault(
        faultType: String,
        severity: String,
        message: String,
        snapshotJson: String?,
    ): Long? =
        runCatching {
            databaseCall {
                val now = System.currentTimeMillis()
                pruneAuditData(now)
                dao.insertPlcFaultEvent(
                    PlcFaultEventEntity(
                        faultType = faultType,
                        severity = severity,
                        message = message,
                        snapshotJson = snapshotJson,
                        createdAt = now,
                        resolvedAt = null,
                    ),
                )
            }
        }.getOrNull()

    override suspend fun resolveFault(eventId: Long?) {
        if (eventId == null) return
        runCatching {
            databaseCall {
                dao.resolvePlcFaultEvent(eventId, System.currentTimeMillis())
            }
        }
    }

    private fun pruneAuditData(now: Long) {
        dao.deleteOldPlcCommandAudits(now - PLC_COMMAND_RETENTION_MS)
        dao.deleteOldPlcFaultEvents(now - PLC_FAULT_RETENTION_MS)
    }

    private suspend fun <T> databaseCall(block: () -> T): T = withContext(Dispatchers.IO) { block() }

    private companion object {
        const val DAY_MS = 24L * 60L * 60L * 1_000L
        const val PLC_COMMAND_RETENTION_MS = 7L * DAY_MS
        const val PLC_FAULT_RETENTION_MS = 30L * DAY_MS
    }
}
