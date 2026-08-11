package com.example.plccontroller.data.local

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class MachineAuditStoreTest {
    @Test
    fun auditFailureDoesNotBlockSuccessfulPlcCommand() =
        runTest {
            val result = ThrowingAuditStore.auditPlcCommand(request()) { "command-result" }

            assertEquals("command-result", result)
        }

    @Test
    fun auditFailureDoesNotReplacePlcCommandFailure() =
        runTest {
            try {
                ThrowingAuditStore.auditPlcCommand(request()) {
                    throw IllegalStateException("real PLC failure")
                }
                fail("Expected PLC failure")
            } catch (error: IllegalStateException) {
                assertEquals("real PLC failure", error.message)
            }
        }

    private fun request() =
        PlcCommandAuditRequest(
            commandId = 1,
            commandType = "TEST",
            commandName = "test command",
        )

    private object ThrowingAuditStore : MachineAuditStore {
        override suspend fun beginPlcCommand(request: PlcCommandAuditRequest): Long? {
            error("audit unavailable")
        }

        override suspend fun finishPlcCommand(
            auditId: Long?,
            result: String,
            errorMessage: String?,
        ) {
            error("audit unavailable")
        }

        override suspend fun recordFault(
            faultType: String,
            severity: String,
            message: String,
            snapshotJson: String?,
        ): Long? = error("audit unavailable")

        override suspend fun resolveFault(eventId: Long?) {
            error("audit unavailable")
        }
    }
}
