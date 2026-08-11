package com.example.plccontroller.runtime.aging

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import java.util.concurrent.atomic.AtomicBoolean

enum class PlcAgingScenario(
    val displayName: String,
    val waterSlotMask: Int,
) {
    WaterOutlet1("出水口1", 0b0001),
    WaterOutlet2("出水口2", 0b0010),
    WaterOutlet3("出水口3", 0b0100),
    WaterOutlet4("出水口4", 0b1000),
    AllWaterOutlets("四路同时出水", 0b1111),
    ChickenOilPump("鸡油泵老化", 0b0001),
    BonePastePump("骨膏泵老化", 0b0001),
    SparePumpChicken("备用泵老化(代鸡油)", 0b0001),
    SparePumpBone("备用泵老化(代骨膏)", 0b0001),
    Heating("加热器老化", 0b0000),
}

data class PlcAgingTestConfig(
    val cycles: Int = 20,
    val actionTicks: Int = 10,
    val intervalMs: Long = 5_000L,
    val scenarios: List<PlcAgingScenario> = PlcAgingScenario.entries,
) {
    init {
        require(cycles in 1..1_000) { "老化循环次数必须在 1..1000 之间" }
        require(actionTicks in 1..100) { "单次出水时间必须在 0.1..10 秒之间" }
        require(intervalMs in 1_000L..60_000L) { "循环间隔必须在 1..60 秒之间" }
        require(scenarios.isNotEmpty()) { "至少选择一个老化场景" }
    }
}

data class PlcAgingCycle(
    val index: Int,
    val scenario: PlcAgingScenario,
)

data class PlcAgingPreflight(
    val safe: Boolean,
    val detail: String,
)

data class PlcAgingCycleResult(
    val commandId: Int,
    val detail: String,
)

enum class PlcAgingRunStatus {
    Running,
    Succeeded,
    Failed,
    Stopped,
}

data class PlcAgingProgress(
    val status: PlcAgingRunStatus,
    val requestedCycles: Int,
    val completedCycles: Int,
    val currentCycle: PlcAgingCycle?,
    val message: String,
)

data class PlcAgingReport(
    val status: PlcAgingRunStatus,
    val requestedCycles: Int,
    val completedCycles: Int,
    val failedCycle: Int?,
    val startedAtMs: Long,
    val finishedAtMs: Long,
    val message: String,
) {
    val succeeded: Boolean
        get() = status == PlcAgingRunStatus.Succeeded
}

interface PlcAgingGateway {
    suspend fun preflight(cycle: PlcAgingCycle): PlcAgingPreflight

    suspend fun execute(
        cycle: PlcAgingCycle,
        config: PlcAgingTestConfig,
    ): PlcAgingCycleResult
}

class PlcAgingTestRunner(
    private val gateway: PlcAgingGateway,
    private val clockMs: () -> Long = System::currentTimeMillis,
    private val pause: suspend (Long) -> Unit = { delay(it) },
) {
    private val stopRequested = AtomicBoolean(false)

    fun requestStop() {
        stopRequested.set(true)
    }

    suspend fun run(
        config: PlcAgingTestConfig,
        onProgress: (PlcAgingProgress) -> Unit = {},
    ): PlcAgingReport {
        stopRequested.set(false)
        val startedAtMs = clockMs()
        var completedCycles = 0

        for (cycleIndex in 1..config.cycles) {
            currentCoroutineContext().ensureActive()
            val cycle =
                PlcAgingCycle(
                    index = cycleIndex,
                    scenario = config.scenarios[(cycleIndex - 1) % config.scenarios.size],
                )
            if (stopRequested.get()) {
                return report(
                    status = PlcAgingRunStatus.Stopped,
                    config = config,
                    completedCycles = completedCycles,
                    failedCycle = null,
                    startedAtMs = startedAtMs,
                    message = "测试已按请求停止",
                )
            }

            onProgress(
                PlcAgingProgress(
                    status = PlcAgingRunStatus.Running,
                    requestedCycles = config.cycles,
                    completedCycles = completedCycles,
                    currentCycle = cycle,
                    message = "开始第 $cycleIndex/${config.cycles} 次：${cycle.scenario.displayName}",
                ),
            )

            val preflight =
                try {
                    gateway.preflight(cycle)
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Throwable) {
                    return failedReport(config, completedCycles, cycle, startedAtMs, "预检异常", error)
                }
            if (!preflight.safe) {
                return report(
                    status = PlcAgingRunStatus.Failed,
                    config = config,
                    completedCycles = completedCycles,
                    failedCycle = cycle.index,
                    startedAtMs = startedAtMs,
                    message = "安全预检失败：${preflight.detail}",
                )
            }

            val result =
                try {
                    gateway.execute(cycle, config)
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Throwable) {
                    return failedReport(config, completedCycles, cycle, startedAtMs, "执行失败", error)
                }

            completedCycles += 1
            onProgress(
                PlcAgingProgress(
                    status = PlcAgingRunStatus.Running,
                    requestedCycles = config.cycles,
                    completedCycles = completedCycles,
                    currentCycle = cycle,
                    message = "第 $cycleIndex 次完成：cmd=${result.commandId}, ${result.detail}",
                ),
            )

            if (cycleIndex < config.cycles) {
                pause(config.intervalMs)
            }
        }

        return report(
            status = PlcAgingRunStatus.Succeeded,
            config = config,
            completedCycles = completedCycles,
            failedCycle = null,
            startedAtMs = startedAtMs,
            message = "全部 ${config.cycles} 次测试完成",
        )
    }

    private fun failedReport(
        config: PlcAgingTestConfig,
        completedCycles: Int,
        cycle: PlcAgingCycle,
        startedAtMs: Long,
        prefix: String,
        error: Throwable,
    ): PlcAgingReport =
        report(
            status = PlcAgingRunStatus.Failed,
            config = config,
            completedCycles = completedCycles,
            failedCycle = cycle.index,
            startedAtMs = startedAtMs,
            message = "$prefix：${error.message ?: error::class.java.simpleName}",
        )

    private fun report(
        status: PlcAgingRunStatus,
        config: PlcAgingTestConfig,
        completedCycles: Int,
        failedCycle: Int?,
        startedAtMs: Long,
        message: String,
    ): PlcAgingReport =
        PlcAgingReport(
            status = status,
            requestedCycles = config.cycles,
            completedCycles = completedCycles,
            failedCycle = failedCycle,
            startedAtMs = startedAtMs,
            finishedAtMs = clockMs(),
            message = message,
        )
}
