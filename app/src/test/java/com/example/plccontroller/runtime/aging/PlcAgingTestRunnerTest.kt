package com.example.plccontroller.runtime.aging

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlcAgingTestRunnerTest {
    @Test
    fun runCompletesConfiguredCyclesAndRotatesScenarios() =
        runTest {
            val executed = mutableListOf<PlcAgingCycle>()
            val gateway =
                FakeGateway(
                    onExecute = { cycle, _ ->
                        executed += cycle
                        PlcAgingCycleResult(commandId = cycle.index, detail = "ok")
                    },
                )
            val runner = PlcAgingTestRunner(gateway, pause = {})

            val report =
                runner.run(
                    PlcAgingTestConfig(
                        cycles = 5,
                        actionTicks = 10,
                        intervalMs = 1_000,
                        scenarios =
                            listOf(
                                PlcAgingScenario.WaterOutlet1,
                                PlcAgingScenario.WaterOutlet2,
                            ),
                    ),
                )

            assertTrue(report.succeeded)
            assertEquals(5, report.completedCycles)
            assertEquals(
                listOf(
                    PlcAgingScenario.WaterOutlet1,
                    PlcAgingScenario.WaterOutlet2,
                    PlcAgingScenario.WaterOutlet1,
                    PlcAgingScenario.WaterOutlet2,
                    PlcAgingScenario.WaterOutlet1,
                ),
                executed.map { it.scenario },
            )
        }

    @Test
    fun unsafePreflightStopsBeforePhysicalExecution() =
        runTest {
            var executeCount = 0
            val gateway =
                FakeGateway(
                    onPreflight = { PlcAgingPreflight(false, "急停输入有效") },
                    onExecute = { cycle, _ ->
                        executeCount += 1
                        PlcAgingCycleResult(cycle.index, "should not run")
                    },
                )

            val report = PlcAgingTestRunner(gateway, pause = {}).run(validConfig())

            assertEquals(PlcAgingRunStatus.Failed, report.status)
            assertEquals(0, report.completedCycles)
            assertEquals(1, report.failedCycle)
            assertTrue(report.message.contains("急停"))
            assertEquals(0, executeCount)
        }

    @Test
    fun executionFailureStopsRemainingCycles() =
        runTest {
            var executeCount = 0
            val gateway =
                FakeGateway(
                    onExecute = { cycle, _ ->
                        executeCount += 1
                        if (cycle.index == 2) error("PLC completion timeout")
                        PlcAgingCycleResult(cycle.index, "ok")
                    },
                )

            val report = PlcAgingTestRunner(gateway, pause = {}).run(validConfig(cycles = 4))

            assertEquals(PlcAgingRunStatus.Failed, report.status)
            assertEquals(1, report.completedCycles)
            assertEquals(2, report.failedCycle)
            assertEquals(2, executeCount)
            assertTrue(report.message.contains("timeout"))
        }

    @Test
    fun stopRequestEndsRunBeforeNextCycle() =
        runTest {
            lateinit var runner: PlcAgingTestRunner
            val gateway = FakeGateway()
            runner =
                PlcAgingTestRunner(
                    gateway = gateway,
                    pause = { runner.requestStop() },
                )

            val report = runner.run(validConfig(cycles = 5))

            assertEquals(PlcAgingRunStatus.Stopped, report.status)
            assertEquals(1, report.completedCycles)
            assertFalse(report.succeeded)
        }

    @Test(expected = IllegalArgumentException::class)
    fun actionDurationOverSafetyLimitIsRejected() {
        PlcAgingTestConfig(actionTicks = 101)
    }

    private fun validConfig(cycles: Int = 3) =
        PlcAgingTestConfig(
            cycles = cycles,
            actionTicks = 10,
            intervalMs = 1_000,
            scenarios = listOf(PlcAgingScenario.WaterOutlet1),
        )

    private class FakeGateway(
        private val onPreflight: suspend (PlcAgingCycle) -> PlcAgingPreflight = {
            PlcAgingPreflight(true, "safe")
        },
        private val onExecute: suspend (
            PlcAgingCycle,
            PlcAgingTestConfig,
        ) -> PlcAgingCycleResult = { cycle, _ ->
            PlcAgingCycleResult(cycle.index, "ok")
        },
    ) : PlcAgingGateway {
        override suspend fun preflight(cycle: PlcAgingCycle): PlcAgingPreflight = onPreflight(cycle)

        override suspend fun execute(
            cycle: PlcAgingCycle,
            config: PlcAgingTestConfig,
        ): PlcAgingCycleResult = onExecute(cycle, config)
    }
}
