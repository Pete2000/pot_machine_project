package com.example.plccontroller.domain.phase

import com.example.plccontroller.domain.ManualWaterPhaseRequest
import com.example.plccontroller.domain.ManualWaterPhaseSlot
import com.example.plccontroller.domain.ManualWaterPotMode
import com.example.plccontroller.domain.hasPhysicalAction

enum class PhasePlanSource {
    Manual,
    Order,
}

enum class PhasePromptKind {
    Rotation,
    FormulaMissing,
}

data class PlannedPhaseStep(
    val source: PhasePlanSource,
    val phaseNo: Int,
    val activeTopLeftLogicalSlot: Int,
    val waterSlots: List<ManualWaterPhaseSlot>,
    val additiveSlot: ManualWaterPhaseSlot?,
    val rotationPrompt: PlannedPhaseRotationPrompt?,
)

data class PlannedPhaseRotationPrompt(
    val source: PhasePlanSource,
    val kind: PhasePromptKind,
    val title: String,
    val message: String,
    val targetLogicalSlot: Int?,
    val confirmText: String? = null,
    val cancelText: String? = null,
)

object PhasePlanner {
    fun planManual(
        request: ManualWaterPhaseRequest,
        currentTopLeftLogicalSlot: Int,
        phaseNoStart: Int = 1,
    ): List<PlannedPhaseStep> {
        require(request.slots.isNotEmpty()) { "Manual water phase requires at least one selected pot bottom" }

        val missingSlots = request.slots.filter { it.formulaMissing }
        val executableSlots = request.slots.filterNot { it.formulaMissing }
        require(executableSlots.isNotEmpty()) {
            "所选锅底配方均缺失，无法执行手动加水"
        }

        val plans = mutableListOf<PlannedPhaseStep>()
        var phaseNo = phaseNoStart
        val activeTopLeftSlot = executableSlots.firstOrNull { it.logicalSlot == currentTopLeftLogicalSlot }
        val waterOnlySlots = executableSlots.filterNot { it.hasAdditiveDuration() }
        val rotatedAdditiveSlots =
            RotationPlanner.sortByClockwiseDistance(
                currentTopLeftLogicalSlot = currentTopLeftLogicalSlot,
                items = executableSlots.filter { it.logicalSlot != currentTopLeftLogicalSlot && it.hasAdditiveDuration() },
                logicalSlotOf = ManualWaterPhaseSlot::logicalSlot,
            )

        if (rotatedAdditiveSlots.isEmpty()) {
            require(executableSlots.any { it.hasPhysicalAction() }) {
                "Manual water phase has no positive water/additive duration"
            }
            plans +=
                PlannedPhaseStep(
                    source = PhasePlanSource.Manual,
                    phaseNo = phaseNo++,
                    activeTopLeftLogicalSlot = currentTopLeftLogicalSlot,
                    waterSlots = executableSlots,
                    additiveSlot = activeTopLeftSlot?.takeIf { it.hasAdditiveDuration() },
                    rotationPrompt = null,
                )
            return plans.withFormulaMissingPrompt(
                source = PhasePlanSource.Manual,
                missingSlots = missingSlots,
            )
        }

        if (activeTopLeftSlot?.hasAdditiveDuration() == true) {
            plans +=
                additiveSlotStep(
                    source = PhasePlanSource.Manual,
                    slot = activeTopLeftSlot,
                    extraWaterSlots = waterOnlySlots,
                    phaseNo = phaseNo++,
                    rotationPrompt = null,
                )
        }

        var ordinaryWaterIncluded = activeTopLeftSlot?.hasAdditiveDuration() == true
        var promptCurrentTopLeftLogicalSlot = currentTopLeftLogicalSlot
        rotatedAdditiveSlots.forEach { slot ->
            val extraWaterSlots =
                if (!ordinaryWaterIncluded) {
                    ordinaryWaterIncluded = true
                    waterOnlySlots
                } else {
                    emptyList()
                }
            plans +=
                additiveSlotStep(
                    source = PhasePlanSource.Manual,
                    slot = slot,
                    extraWaterSlots = extraWaterSlots,
                    phaseNo = phaseNo++,
                    rotationPrompt =
                        buildRotationPrompt(
                            source = PhasePlanSource.Manual,
                            mode = request.mode,
                            slot = slot,
                            extraWaterSlots = extraWaterSlots,
                            currentTopLeftLogicalSlot = promptCurrentTopLeftLogicalSlot,
                            titlePrefix = "手动加水需要转锅",
                        ),
                )
            promptCurrentTopLeftLogicalSlot = slot.logicalSlot
        }

        return plans.withFormulaMissingPrompt(
            source = PhasePlanSource.Manual,
            missingSlots = missingSlots,
        )
    }

    fun planOrder(
        mode: ManualWaterPotMode,
        slots: List<ManualWaterPhaseSlot>,
        currentTopLeftLogicalSlot: Int,
        orderId: String,
        phaseNoStart: Int = 1,
    ): List<PlannedPhaseStep> {
        require(slots.isNotEmpty()) { "Order $orderId has no pot slots to dispatch" }
        val missingSlots = slots.filter { it.formulaMissing }
        val executableSlots = slots.filterNot { it.formulaMissing }
        require(executableSlots.isNotEmpty()) {
            "订单 $orderId 的锅底配方均缺失，无法执行"
        }

        val plans = mutableListOf<PlannedPhaseStep>()
        var phaseNo = phaseNoStart
        val activeTopLeftSlot = executableSlots.firstOrNull { it.logicalSlot == currentTopLeftLogicalSlot }
        val additiveSlots = executableSlots.filter { it.hasAdditiveDuration() }
        val orderedAdditiveSlots =
            buildList {
                activeTopLeftSlot
                    ?.takeIf { it.hasAdditiveDuration() }
                    ?.let(::add)
                addAll(
                    RotationPlanner.sortByClockwiseDistance(
                        currentTopLeftLogicalSlot = currentTopLeftLogicalSlot,
                        items = additiveSlots.filter { it.logicalSlot != currentTopLeftLogicalSlot },
                        logicalSlotOf = ManualWaterPhaseSlot::logicalSlot,
                    ),
                )
            }

        if (orderedAdditiveSlots.isEmpty()) {
            require(executableSlots.any { it.hasPhysicalAction() }) { "Order $orderId has no positive water/additive duration" }
            plans +=
                PlannedPhaseStep(
                    source = PhasePlanSource.Order,
                    phaseNo = phaseNo,
                    activeTopLeftLogicalSlot = currentTopLeftLogicalSlot,
                    waterSlots = executableSlots,
                    additiveSlot = null,
                    rotationPrompt = null,
                )
            return plans.withFormulaMissingPrompt(
                source = PhasePlanSource.Order,
                missingSlots = missingSlots,
            )
        }

        val waterOnlySlots = executableSlots.filterNot { it.hasAdditiveDuration() }
        var ordinaryWaterIncluded = false
        var promptCurrentTopLeftLogicalSlot = currentTopLeftLogicalSlot
        orderedAdditiveSlots.forEachIndexed { index, slot ->
            val extraWaterSlots =
                if (!ordinaryWaterIncluded) {
                    ordinaryWaterIncluded = true
                    waterOnlySlots
                } else {
                    emptyList()
                }
            val rotationPrompt =
                if (index == 0) {
                    null
                } else {
                    buildRotationPrompt(
                        source = PhasePlanSource.Order,
                        mode = mode,
                        slot = slot,
                        extraWaterSlots = extraWaterSlots,
                        currentTopLeftLogicalSlot = promptCurrentTopLeftLogicalSlot,
                        titlePrefix = "订单需要转锅",
                    )
                }
            plans +=
                additiveSlotStep(
                    source = PhasePlanSource.Order,
                    slot = slot,
                    extraWaterSlots = extraWaterSlots,
                    phaseNo = phaseNo++,
                    rotationPrompt = rotationPrompt,
                )
            promptCurrentTopLeftLogicalSlot = slot.logicalSlot
        }

        return plans.withFormulaMissingPrompt(
            source = PhasePlanSource.Order,
            missingSlots = missingSlots,
        )
    }

    private fun additiveSlotStep(
        source: PhasePlanSource,
        slot: ManualWaterPhaseSlot,
        extraWaterSlots: List<ManualWaterPhaseSlot>,
        phaseNo: Int,
        rotationPrompt: PlannedPhaseRotationPrompt?,
    ): PlannedPhaseStep =
        PlannedPhaseStep(
            source = source,
            phaseNo = phaseNo,
            activeTopLeftLogicalSlot = slot.logicalSlot,
            waterSlots = extraWaterSlots + slot,
            additiveSlot = slot,
            rotationPrompt = rotationPrompt,
        )

    private fun buildRotationPrompt(
        source: PhasePlanSource,
        mode: ManualWaterPotMode,
        slot: ManualWaterPhaseSlot,
        extraWaterSlots: List<ManualWaterPhaseSlot> = emptyList(),
        currentTopLeftLogicalSlot: Int,
        titlePrefix: String,
    ): PlannedPhaseRotationPrompt {
        val waterSeconds = slot.waterDurationMs.toSecondsText()
        val chickenSeconds = slot.chickenOilDurationMs.toSecondsText()
        val boneSeconds = slot.bonePasteDurationMs.toSecondsText()
        val sourcePosition =
            PotLayout.physicalLabelForLogicalSlot(
                kind = mode.toPotLayoutKind(),
                logicalSlot = slot.logicalSlot,
                activeTopLeftLogicalSlot = currentTopLeftLogicalSlot,
                includeOutlet = true,
            )
        val targetPosition =
            PotLayout.physicalLabelForLogicalSlot(
                kind = mode.toPotLayoutKind(),
                logicalSlot = slot.logicalSlot,
                activeTopLeftLogicalSlot = slot.logicalSlot,
                includeOutlet = true,
            )
        val extraWaterText =
            extraWaterSlots
                .filter { it.waterDurationMs > 0L }
                .takeIf { it.isNotEmpty() }
                ?.joinToString(
                    separator = "、",
                    prefix = "\n同时普通锅底加水：",
                ) { extraSlot ->
                    val physicalPosition =
                        PotLayout.physicalLabelForLogicalSlot(
                            kind = mode.toPotLayoutKind(),
                            logicalSlot = extraSlot.logicalSlot,
                            activeTopLeftLogicalSlot = slot.logicalSlot,
                            includeOutlet = true,
                        )
                    "$physicalPosition ${extraSlot.label.ifBlank { "锅底" }} ${extraSlot.waterDurationMs.toSecondsText()}"
                }.orEmpty()
        return PlannedPhaseRotationPrompt(
            source = source,
            kind = PhasePromptKind.Rotation,
            title = titlePrefix,
            message =
                "请将当前 $sourcePosition 的 ${slot.label.ifBlank { "锅底" }} 转到 $targetPosition。\n" +
                    "确认后将执行：加水 $waterSeconds + 鸡油 $chickenSeconds + 骨膏 ${boneSeconds}$extraWaterText",
            targetLogicalSlot = slot.logicalSlot,
        )
    }

    private fun List<PlannedPhaseStep>.withFormulaMissingPrompt(
        source: PhasePlanSource,
        missingSlots: List<ManualWaterPhaseSlot>,
    ): List<PlannedPhaseStep> {
        if (missingSlots.isEmpty()) return this
        val firstPlan = firstOrNull() ?: return this
        val missingText =
            missingSlots.joinToString("、") { slot ->
                "${slot.logicalSlot}号位 ${slot.label.ifBlank { "锅底" }}"
            }
        val warning = "以下锅底配方缺失，本次将跳过：$missingText。其余配方完整的锅底将继续执行。"
        val originalPrompt = firstPlan.rotationPrompt
        val prompt =
            if (originalPrompt == null) {
                PlannedPhaseRotationPrompt(
                    source = source,
                    kind = PhasePromptKind.FormulaMissing,
                    title = if (source == PhasePlanSource.Order) "订单配方缺失" else "手动配方缺失",
                    message = warning,
                    confirmText = "继续执行可用锅底",
                    cancelText = "暂不处理",
                    targetLogicalSlot = null,
                )
            } else {
                originalPrompt.copy(
                    message = "$warning\n\n${originalPrompt.message}",
                )
            }
        return listOf(firstPlan.copy(rotationPrompt = prompt)) + drop(1)
    }

    private fun ManualWaterPhaseSlot.hasAdditiveDuration(): Boolean = chickenOilDurationMs > 0L || bonePasteDurationMs > 0L

    private fun ManualWaterPotMode.toPotLayoutKind(): PotLayoutKind =
        when (this) {
            ManualWaterPotMode.Small -> PotLayoutKind.Small
            ManualWaterPotMode.Single -> PotLayoutKind.Single
            ManualWaterPotMode.Split -> PotLayoutKind.Split
            ManualWaterPotMode.ThreeGrid -> PotLayoutKind.ThreeGrid
            ManualWaterPotMode.FourGrid -> PotLayoutKind.FourGrid
        }

    private fun Long.toSecondsText(): String {
        if (this <= 0L) return "0s"
        val wholeSeconds = this / 1_000L
        val remainderMs = this % 1_000L
        return if (remainderMs == 0L) {
            "${wholeSeconds}s"
        } else {
            "%.1fs".format(this / 1_000.0)
        }
    }
}
