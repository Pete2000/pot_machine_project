package com.example.plccontroller.runtime

import com.example.plccontroller.data.AppPersistentConfig
import com.example.plccontroller.data.SettingsStore
import com.example.plccontroller.data.plc.PlcPhaseCommand
import com.example.plccontroller.domain.FormulaCatalog
import com.example.plccontroller.domain.ManualWaterPhaseRequest
import com.example.plccontroller.domain.ManualWaterPhaseSlot
import com.example.plccontroller.domain.ManualWaterPotMode
import com.example.plccontroller.domain.Order
import com.example.plccontroller.domain.PotMode
import com.example.plccontroller.domain.formula.RecipeMatcher
import com.example.plccontroller.domain.formula.RecipePotMode
import com.example.plccontroller.domain.hasBlockingStructureIssue
import com.example.plccontroller.domain.hasPhysicalAction
import com.example.plccontroller.domain.localDefaultFormulaCatalog
import com.example.plccontroller.domain.phase.PhasePlanSource
import com.example.plccontroller.domain.phase.PhasePlanner
import com.example.plccontroller.domain.phase.PlannedPhaseRotationPrompt
import com.example.plccontroller.domain.phase.PlannedPhaseStep
import com.example.plccontroller.domain.phase.PotLayout
import com.example.plccontroller.domain.phase.PotLayoutKind
import com.example.plccontroller.domain.structureIssueSummary
import com.example.plccontroller.domain.phase.PhasePromptKind as PlannedPromptKind

internal class PhaseCommandPlanBuilder(
    private val runtimeStore: MachineRuntimeStore,
    private val configSnapshot: () -> AppPersistentConfig,
    private val cachedFormulaCatalog: () -> FormulaCatalog?,
    private val nextPlcCommandId: () -> Int,
) {
    constructor(
        runtimeStore: MachineRuntimeStore,
        settingsStore: SettingsStore,
    ) : this(
        runtimeStore = runtimeStore,
        configSnapshot = settingsStore::snapshot,
        cachedFormulaCatalog = settingsStore::cachedFormulaCatalog,
        nextPlcCommandId = settingsStore::nextPlcCommandId,
    )

    fun buildManualWaterPhaseCommands(
        request: ManualWaterPhaseRequest,
        currentTopLeftLogicalSlot: Int,
    ): List<PhaseCommandPlan> {
        val config = configSnapshot()
        return PhasePlanner
            .planManual(
                request = request,
                currentTopLeftLogicalSlot = currentTopLeftLogicalSlot,
                phaseNoStart = MANUAL_PHASE_NO,
            ).map { plannedStep ->
                plannedStep.toPhaseCommandPlan(
                    mode = request.mode,
                    config = config,
                )
            }
    }

    fun buildOrderPhaseCommands(
        order: Order,
        currentTopLeftLogicalSlot: Int,
    ): OrderPhaseCommandBuildResult {
        require(!order.hasBlockingStructureIssue()) {
            order.structureIssueSummary() ?: "订单结构异常，禁止下发 PLC"
        }
        val mode = order.potMode.toManualWaterPotMode()
        val allSlots = buildOrderPhaseSlots(order = order, mode = mode)
        val missingSlots = allSlots.filter { it.formulaMissing }
        val matchedSlots = allSlots.filterNot { it.formulaMissing }
        val physicalActionSlots = matchedSlots.filter { it.hasPhysicalAction() }
        val noActionSlots = matchedSlots.filterNot { it.hasPhysicalAction() }
        val config = configSnapshot()
        val plans =
            if (physicalActionSlots.isEmpty()) {
                emptyList()
            } else {
                PhasePlanner
                    .planOrder(
                        mode = mode,
                        slots = physicalActionSlots,
                        currentTopLeftLogicalSlot = currentTopLeftLogicalSlot,
                        orderId = order.id,
                        phaseNoStart = 1,
                    ).map { plannedStep ->
                        plannedStep.toPhaseCommandPlan(
                            mode = mode,
                            config = config,
                        )
                    }
            }
        return OrderPhaseCommandBuildResult(
            plans = plans,
            allSlots = allSlots,
            missingSlots = missingSlots,
            noActionSlots = noActionSlots,
        )
    }

    private fun PlannedPhaseStep.toPhaseCommandPlan(
        mode: ManualWaterPotMode,
        config: AppPersistentConfig,
    ): PhaseCommandPlan {
        val waterTicks =
            mode.waterTicksForSlots(
                slots = waterSlots,
                activeTopLeftLogicalSlot = activeTopLeftLogicalSlot,
                config = config,
            )
        return PhaseCommandPlan(
            source = source.toRuntimeSource(),
            command =
                PlcPhaseCommand(
                    commandId = nextPlcCommandId(),
                    commandType = PHASE_COMMAND_TYPE_EXECUTE,
                    jobSlotMask = waterTicks.toJobSlotMask(),
                    waterDuration1 = waterTicks[0],
                    waterDuration2 = waterTicks[1],
                    waterDuration3 = waterTicks[2],
                    waterDuration4 = waterTicks[3],
                    activeTopLeftLogicalSlot = activeTopLeftLogicalSlot,
                    chickenOilDuration =
                        additiveSlot
                            ?.chickenOilDurationMs
                            ?.toCalibratedPhaseTicks(config.deviceChickenOilCalibrationPercent) ?: 0,
                    bonePasteDuration =
                        additiveSlot
                            ?.bonePasteDurationMs
                            ?.toCalibratedPhaseTicks(config.deviceBonePasteCalibrationPercent) ?: 0,
                    phaseNo = phaseNo,
                ),
            rotationPrompt = rotationPrompt?.toRuntimePrompt(),
        )
    }

    private fun buildOrderPhaseSlots(
        order: Order,
        mode: ManualWaterPotMode,
    ): List<ManualWaterPhaseSlot> {
        val catalog =
            runtimeStore.snapshot().formulaCatalog
                ?: cachedFormulaCatalog()
                ?: localDefaultFormulaCatalog()
        val parsedNames = parseSlotNames(order)
        val parsedCodes = parseSlotCodes(order)
        return (1..mode.slotCount()).map { logicalSlot ->
            val name =
                parsedNames[logicalSlot]
                    ?: order.potBottomName?.takeIf(String::isNotBlank)
                    ?: order.recipeCode
            val potType =
                RecipeMatcher
                    .matchPotType(
                        catalog = catalog,
                        potCode = parsedCodes[logicalSlot],
                        potName = name,
                        potTypeCode = order.rootPosFoodCode ?: order.recipeCode,
                        mode = mode.toRecipePotMode(),
                    ).potType
            ManualWaterPhaseSlot(
                logicalSlot = logicalSlot,
                waterDurationMs = potType?.waterDurationMs ?: 0L,
                chickenOilDurationMs = potType?.chickenOilDurationMs ?: 0L,
                bonePasteDurationMs = potType?.bonePasteDurationMs ?: 0L,
                label = name,
                formulaMissing = potType == null,
            )
        }
    }

    private fun ManualWaterPotMode.waterTicksForSlots(
        slots: List<ManualWaterPhaseSlot>,
        activeTopLeftLogicalSlot: Int,
        config: AppPersistentConfig,
    ): IntArray {
        val waterTicks = IntArray(4)
        val slotsByLogicalSlot = slots.associateBy { it.logicalSlot }
        val physicalLayout = rotatedLogicalSlots(activeTopLeftLogicalSlot)
        when (this) {
            ManualWaterPotMode.Small -> {
                waterTicks[0] =
                    slotsByLogicalSlot[1]
                        ?.waterDurationMs
                        .toCalibratedPhaseTicksOrZero(config.waterOutletCalibrationPercent(0))
            }
            ManualWaterPotMode.Single -> {
                val duration = slots.firstOrNull()?.waterDurationMs
                repeat(4) { index ->
                    waterTicks[index] =
                        duration.toCalibratedPhaseTicksOrZero(
                            config.waterOutletCalibrationPercent(index),
                        )
                }
            }
            ManualWaterPotMode.Split -> {
                val leftLogicalSlot = physicalLayout.getOrElse(0) { 1 }
                val rightLogicalSlot = physicalLayout.getOrElse(1) { 2 }
                val leftDuration = slotsByLogicalSlot[leftLogicalSlot]?.waterDurationMs
                val rightDuration = slotsByLogicalSlot[rightLogicalSlot]?.waterDurationMs
                waterTicks[0] = leftDuration.toCalibratedPhaseTicksOrZero(config.waterOutletCalibrationPercent(0))
                waterTicks[2] = leftDuration.toCalibratedPhaseTicksOrZero(config.waterOutletCalibrationPercent(2))
                waterTicks[1] = rightDuration.toCalibratedPhaseTicksOrZero(config.waterOutletCalibrationPercent(1))
                waterTicks[3] = rightDuration.toCalibratedPhaseTicksOrZero(config.waterOutletCalibrationPercent(3))
            }
            ManualWaterPotMode.ThreeGrid,
            ManualWaterPotMode.FourGrid,
            -> {
                physicalLayout.forEachIndexed { physicalIndex, logicalSlot ->
                    if (physicalIndex in waterTicks.indices) {
                        waterTicks[physicalIndex] =
                            slotsByLogicalSlot[logicalSlot]?.waterDurationMs.toCalibratedPhaseTicksOrZero(
                                config.waterOutletCalibrationPercent(physicalIndex),
                            )
                    }
                }
            }
        }
        return waterTicks
    }

    private fun PhasePlanSource.toRuntimeSource(): PhaseFlowSource =
        when (this) {
            PhasePlanSource.Manual -> PhaseFlowSource.Manual
            PhasePlanSource.Order -> PhaseFlowSource.Order
        }

    private fun PlannedPhaseRotationPrompt.toRuntimePrompt(): PhaseRotationPrompt =
        PhaseRotationPrompt(
            source = source.toRuntimeSource(),
            kind =
                when (kind) {
                    PlannedPromptKind.Rotation -> PhasePromptKind.Rotation
                    PlannedPromptKind.FormulaMissing -> PhasePromptKind.FormulaMissing
                },
            title = title,
            message = message,
            confirmText = confirmText ?: "已转到左上，开始执行",
            cancelText = cancelText ?: "暂不处理",
            targetLogicalSlot = targetLogicalSlot,
        )

    private fun ManualWaterPotMode.toRecipePotMode(): RecipePotMode =
        when (this) {
            ManualWaterPotMode.Small -> RecipePotMode.Small
            ManualWaterPotMode.Single -> RecipePotMode.Single
            ManualWaterPotMode.Split -> RecipePotMode.Split
            ManualWaterPotMode.ThreeGrid -> RecipePotMode.ThreeGrid
            ManualWaterPotMode.FourGrid -> RecipePotMode.FourGrid
        }

    private fun ManualWaterPotMode.toPotLayoutKind(): PotLayoutKind =
        when (this) {
            ManualWaterPotMode.Small -> PotLayoutKind.Small
            ManualWaterPotMode.Single -> PotLayoutKind.Single
            ManualWaterPotMode.Split -> PotLayoutKind.Split
            ManualWaterPotMode.ThreeGrid -> PotLayoutKind.ThreeGrid
            ManualWaterPotMode.FourGrid -> PotLayoutKind.FourGrid
        }

    @Suppress("MaxLineLength")
    private fun ManualWaterPotMode.rotatedLogicalSlots(activeTopLeftLogicalSlot: Int): List<Int> = PotLayout.rotatedLogicalSlots(toPotLayoutKind(), activeTopLeftLogicalSlot)

    private fun parseSlotNames(order: Order): Map<Int, String> {
        val summary = order.slotSummary?.trim().orEmpty()
        if (summary.isBlank()) return emptyMap()

        return summary
            .split("|", "；", ";", "\n")
            .mapNotNull { section ->
                val normalized = section.trim()
                if (normalized.isBlank()) return@mapNotNull null
                val slot = resolveSlotFromText(normalized, order.potMode) ?: return@mapNotNull null
                val rawName = normalized.substringAfter("：", normalized.substringAfter(":", normalized))
                val name =
                    rawName
                        .replace(Regex("（.*?）"), "")
                        .replace(Regex("\\(.*?\\)"), "")
                        .trim()
                slot to name.ifBlank { normalized }
            }.toMap()
    }

    private fun parseSlotCodes(order: Order): Map<Int, String> {
        val summary = order.slotCodeSummary?.trim().orEmpty()
        if (summary.isBlank()) return emptyMap()

        return summary
            .split("|", "；", ";", "\n")
            .mapNotNull { section ->
                val normalized = section.trim()
                if (normalized.isBlank()) return@mapNotNull null
                val slot = resolveSlotFromText(normalized, order.potMode) ?: return@mapNotNull null
                val code =
                    normalized
                        .substringAfter("：", normalized.substringAfter(":", normalized))
                        .trim()
                slot to code
            }.toMap()
    }

    private fun resolveSlotFromText(
        text: String,
        mode: PotMode,
    ): Int? =
        when {
            text.contains("左上") -> 1
            text.contains("右上") -> 2
            text.contains("右下") -> 4
            text.contains("左下") -> 3
            text.contains("下方") || text.contains("下锅") || text.contains("下中") -> 3
            mode == PotMode.Split && text.contains("左") -> 1
            mode == PotMode.Split && text.contains("右") -> 2
            mode == PotMode.Single -> 1
            else -> null
        }

    private fun Long.toPhaseTicks(): Int {
        if (this <= 0L) return 0
        return ((this + PHASE_TICK_MS - 1) / PHASE_TICK_MS)
            .coerceAtMost(Int.MAX_VALUE.toLong())
            .toInt()
    }

    private fun Long.toCalibratedPhaseTicks(percent: Int): Int {
        if (this <= 0L) return 0
        val calibratedMs = ((this * percent.coerceIn(50, 200)) + 99L) / 100L
        return calibratedMs.toPhaseTicks()
    }

    private fun Long?.toCalibratedPhaseTicksOrZero(percent: Int): Int = this?.toCalibratedPhaseTicks(percent) ?: 0

    private fun AppPersistentConfig.waterOutletCalibrationPercent(physicalIndex: Int): Int =
        when (physicalIndex) {
            0 -> deviceWaterOutlet1CalibrationPercent
            1 -> deviceWaterOutlet2CalibrationPercent
            2 -> deviceWaterOutlet3CalibrationPercent
            3 -> deviceWaterOutlet4CalibrationPercent
            else -> 100
        }

    private fun IntArray.toJobSlotMask(): Int {
        var mask = 0
        forEachIndexed { index, duration ->
            if (duration > 0) {
                mask = mask or (1 shl index)
            }
        }
        return mask
    }
}

internal data class PhaseCommandPlan(
    val source: PhaseFlowSource,
    val command: PlcPhaseCommand,
    val rotationPrompt: PhaseRotationPrompt?,
)

internal data class OrderPhaseCommandBuildResult(
    val plans: List<PhaseCommandPlan>,
    val allSlots: List<ManualWaterPhaseSlot>,
    val missingSlots: List<ManualWaterPhaseSlot>,
    val noActionSlots: List<ManualWaterPhaseSlot>,
) {
    val allSlotsMissing: Boolean
        get() = allSlots.isNotEmpty() && missingSlots.size == allSlots.size

    val hasPhysicalCommands: Boolean
        get() = plans.isNotEmpty()
}

internal fun PotMode.toManualWaterPotMode(): ManualWaterPotMode =
    when (this) {
        PotMode.Single -> ManualWaterPotMode.Single
        PotMode.Split -> ManualWaterPotMode.Split
        PotMode.ThreeGrid -> ManualWaterPotMode.ThreeGrid
        PotMode.FourGrid -> ManualWaterPotMode.FourGrid
    }

internal fun ManualWaterPotMode.slotCount(): Int =
    when (this) {
        ManualWaterPotMode.Small,
        ManualWaterPotMode.Single,
        -> 1
        ManualWaterPotMode.Split -> 2
        ManualWaterPotMode.ThreeGrid -> 3
        ManualWaterPotMode.FourGrid -> 4
    }

private const val MANUAL_PHASE_NO = 1
private const val PHASE_TICK_MS = 100L
private const val PHASE_COMMAND_TYPE_EXECUTE = 1
