package com.example.plccontroller.runtime.aging

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.plccontroller.PlcControllerApplication
import com.example.plccontroller.test.RealHardwareTest
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
@RealHardwareTest
class RealPlcAgingInstrumentedTest {
    @Test
    fun runWaterOnlyAgingAgainstConnectedPlc() =
        runTest {
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            val arguments = InstrumentationRegistry.getArguments()
            assumeTrue(
                "真实 PLC 老化测试必须显式传入 plcAgingEnabled=true",
                arguments.getString(ARG_ENABLED).toBoolean(),
            )

            val config =
                PlcAgingTestConfig(
                    cycles = arguments.intValue(ARG_CYCLES, 20),
                    actionTicks = arguments.intValue(ARG_ACTION_TICKS, 10),
                    intervalMs = arguments.longValue(ARG_INTERVAL_MS, 5_000L),
                    scenarios = arguments.scenarioList(),
                )
            val application = instrumentation.targetContext.applicationContext as PlcControllerApplication
            val reportFile = File(application.filesDir, AGING_REPORT_FILE)

            Log.i(TAG_PLC_AGING, "instrumentation start config=$config")
            reportFile.writeAgingStatus(
                status = PlcAgingRunStatus.Running,
                requestedCycles = config.cycles,
                completedCycles = 0,
                message = "instrumentation started",
            )
            val report =
                try {
                    application.appContainer.machineCoordinator.runPlcAgingTest(config) { progress ->
                        Log.i(
                            TAG_PLC_AGING,
                            "progress status=${progress.status} completed=${progress.completedCycles}/" +
                                "${progress.requestedCycles} message=${progress.message}",
                        )
                        reportFile.writeAgingStatus(
                            status = progress.status,
                            requestedCycles = progress.requestedCycles,
                            completedCycles = progress.completedCycles,
                            message = progress.message,
                        )
                    }
                } catch (error: Throwable) {
                    reportFile.writeAgingStatus(
                        status = PlcAgingRunStatus.Failed,
                        requestedCycles = config.cycles,
                        completedCycles = 0,
                        message = error.message ?: error::class.java.simpleName,
                    )
                    throw error
                }
            reportFile.writeAgingStatus(
                status = report.status,
                requestedCycles = report.requestedCycles,
                completedCycles = report.completedCycles,
                message = report.message,
            )
            Log.i(TAG_PLC_AGING, "instrumentation report=$report")

            assertTrue("真实 PLC 老化测试失败：${report.message}", report.succeeded)
        }

    private fun File.writeAgingStatus(
        status: PlcAgingRunStatus,
        requestedCycles: Int,
        completedCycles: Int,
        message: String,
    ) {
        writeText(
            buildString {
                appendLine("status=${status.name}")
                appendLine("requestedCycles=$requestedCycles")
                appendLine("completedCycles=$completedCycles")
                appendLine("updatedAtMs=${System.currentTimeMillis()}")
                appendLine("message=${message.replace('\n', ' ')}")
            },
        )
    }

    private fun android.os.Bundle.intValue(
        key: String,
        defaultValue: Int,
    ): Int = getString(key)?.toIntOrNull() ?: defaultValue

    private fun android.os.Bundle.longValue(
        key: String,
        defaultValue: Long,
    ): Long = getString(key)?.toLongOrNull() ?: defaultValue

    private fun android.os.Bundle.scenarioList(): List<PlcAgingScenario> {
        val names =
            getString(ARG_SCENARIOS)
                ?.split(',')
                ?.map(String::trim)
                ?.filter(String::isNotEmpty)
                .orEmpty()
        if (names.isEmpty()) return PlcAgingScenario.entries
        return names.map { name ->
            requireNotNull(PlcAgingScenario.entries.firstOrNull { it.name == name }) {
                "未知老化场景：$name"
            }
        }
    }
}

private const val ARG_ENABLED = "plcAgingEnabled"
private const val ARG_CYCLES = "cycles"
private const val ARG_ACTION_TICKS = "actionTicks"
private const val ARG_INTERVAL_MS = "intervalMs"
private const val ARG_SCENARIOS = "scenarios"
private const val TAG_PLC_AGING = "PlcAging"
private const val AGING_REPORT_FILE = "plc-aging-report.txt"
