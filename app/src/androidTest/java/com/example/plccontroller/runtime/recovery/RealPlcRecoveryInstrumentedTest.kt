package com.example.plccontroller.runtime.recovery

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.plccontroller.PlcControllerApplication
import com.example.plccontroller.test.RealHardwareTest
import kotlinx.coroutines.test.runTest
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
@RealHardwareTest
class RealPlcRecoveryInstrumentedTest {
    @Test
    fun captureBaselineBeforeRestart() =
        runTest {
            val application = recoveryApplication()
            val container = application.appContainer
            container.plcPollingService.stop()
            val reportFile = File(application.filesDir, REPORT_FILE)
            val checkpointFile = File(application.filesDir, CHECKPOINT_FILE)

            runCatching {
                var heartbeat = false
                container.machineCoordinator.withCriticalMaintenance("PLC recovery baseline") {
                    val snapshot = container.plcController.pollSnapshotStableClean()
                    heartbeat = snapshot.lastHeartbeatValue
                }
                val commandId = container.settingsStore.nextPlcCommandId()
                checkpointFile.writeText(
                    buildString {
                        appendLine("commandId=$commandId")
                        appendLine("heartbeat=$heartbeat")
                        appendLine("createdAtMs=${System.currentTimeMillis()}")
                    },
                )
                reportFile.writeRecoveryReport(
                    status = STATUS_BASELINE_SUCCEEDED,
                    stage = "baseline",
                    message = "PLC baseline captured before Android process restart",
                    details = mapOf("commandId" to commandId.toString()),
                )
                Log.i(TAG_RECOVERY, "baseline succeeded commandId=$commandId heartbeat=$heartbeat")
            }.getOrElse { error ->
                reportFile.writeRecoveryReport(
                    status = STATUS_FAILED,
                    stage = "baseline",
                    message = error.message ?: error::class.java.simpleName,
                )
                throw error
            }
            Unit
        }

    @Test
    fun verifyAfterProcessRestart() =
        runTest {
            val application = recoveryApplication()
            val container = application.appContainer
            container.plcPollingService.stop()
            val reportFile = File(application.filesDir, REPORT_FILE)
            val checkpoint = File(application.filesDir, CHECKPOINT_FILE).readKeyValues()

            runCatching {
                val baselineCommandId = checkpoint.getValue("commandId").toInt()
                val storedCommandId = container.settingsStore.currentPlcCommandId()
                val persistedAdvance = commandIdDistance(baselineCommandId, storedCommandId)
                check(persistedAdvance in 0..10) {
                    "PLC command id did not persist across restart: before=$baselineCommandId, after=$storedCommandId"
                }
                val nextCommandId = container.settingsStore.nextPlcCommandId()
                check(nextCommandId == nextCommandIdAfter(storedCommandId)) {
                    "PLC command id is not sequential: stored=$storedCommandId, next=$nextCommandId"
                }

                var originalHeartbeat = false
                var serialMethodAfterReset = ""
                container.machineCoordinator.withCriticalMaintenance("PLC automated recovery verification") {
                    val beforeReset = container.plcController.pollSnapshotStableClean()
                    originalHeartbeat = beforeReset.lastHeartbeatValue

                    container.plcController.resetCommunicationConnection()
                    check(!container.plcController.serialDiagnostic().configured) {
                        "Serial connection did not enter the closed state during reset"
                    }
                    val afterReset = container.plcController.pollSnapshotStableClean()
                    serialMethodAfterReset = afterReset.serialDiagnostic.method
                    check(afterReset.lastSuccessfulPollAtMs != null) {
                        "PLC poll did not recover after serial reset"
                    }

                    val toggledHeartbeat = !originalHeartbeat
                    try {
                        container.plcController.writeHeartbeat(toggledHeartbeat)
                        val toggledSnapshot = container.plcController.pollSnapshotStableClean()
                        check(toggledSnapshot.lastHeartbeatValue == toggledHeartbeat) {
                            "Heartbeat readback mismatch after restart"
                        }
                    } finally {
                        container.plcController.writeHeartbeat(originalHeartbeat)
                    }
                    val restoredSnapshot = container.plcController.pollSnapshotStableClean()
                    check(restoredSnapshot.lastHeartbeatValue == originalHeartbeat) {
                        "Heartbeat was not restored to its original value"
                    }
                }

                reportFile.writeRecoveryReport(
                    status = STATUS_SUCCEEDED,
                    stage = "verification",
                    message = "Android restart, command id, serial reset, polling, and heartbeat checks passed",
                    details =
                        mapOf(
                            "baselineCommandId" to baselineCommandId.toString(),
                            "storedCommandId" to storedCommandId.toString(),
                            "nextCommandId" to nextCommandId.toString(),
                            "serialMethod" to serialMethodAfterReset,
                            "heartbeatRestored" to originalHeartbeat.toString(),
                        ),
                )
                Log.i(
                    TAG_RECOVERY,
                    "verification succeeded baseline=$baselineCommandId stored=$storedCommandId next=$nextCommandId " +
                        "serial=$serialMethodAfterReset heartbeat=$originalHeartbeat",
                )
            }.getOrElse { error ->
                reportFile.writeRecoveryReport(
                    status = STATUS_FAILED,
                    stage = "verification",
                    message = error.message ?: error::class.java.simpleName,
                )
                throw error
            }
            Unit
        }

    private fun recoveryApplication(): PlcControllerApplication {
        val arguments = InstrumentationRegistry.getArguments()
        assumeTrue(
            "真实 PLC 恢复测试必须显式传入 plcRecoveryEnabled=true",
            arguments.getString(ARG_ENABLED).toBoolean(),
        )
        return InstrumentationRegistry
            .getInstrumentation()
            .targetContext.applicationContext as PlcControllerApplication
    }

    private fun File.writeRecoveryReport(
        status: String,
        stage: String,
        message: String,
        details: Map<String, String> = emptyMap(),
    ) {
        writeText(
            buildString {
                appendLine("status=$status")
                appendLine("stage=$stage")
                appendLine("updatedAtMs=${System.currentTimeMillis()}")
                appendLine("message=${message.replace('\n', ' ')}")
                details.forEach { (key, value) -> appendLine("$key=$value") }
            },
        )
    }

    private fun File.readKeyValues(): Map<String, String> {
        check(isFile) { "Recovery checkpoint is missing after Android restart" }
        return readLines()
            .mapNotNull { line ->
                val separator = line.indexOf('=')
                if (separator <= 0) null else line.substring(0, separator) to line.substring(separator + 1)
            }.toMap()
    }

    private fun commandIdDistance(
        from: Int,
        to: Int,
    ): Int = if (to >= from) to - from else PLC_COMMAND_ID_MAX - from + to

    private fun nextCommandIdAfter(current: Int): Int = if (current >= PLC_COMMAND_ID_MAX || current < 0) 1 else current + 1
}

private const val ARG_ENABLED = "plcRecoveryEnabled"
private const val REPORT_FILE = "plc-recovery-report.txt"
private const val CHECKPOINT_FILE = "plc-recovery-checkpoint.txt"
private const val TAG_RECOVERY = "PlcRecovery"
private const val STATUS_BASELINE_SUCCEEDED = "BaselineSucceeded"
private const val STATUS_SUCCEEDED = "Succeeded"
private const val STATUS_FAILED = "Failed"
private const val PLC_COMMAND_ID_MAX = 65_535
