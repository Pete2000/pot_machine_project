package com.example.plccontroller.domain

enum class PotSlot(
    val spoutIndex: Int,
) {
    TopLeft(1),
    TopRight(2),
    BottomLeft(3),
    BottomRight(4),
}

data class PotFlavor(
    val potBottomId: String,
    val baseCode: String,
    val baseName: String,
    val tasteCodes: List<String> = emptyList(),
    val tasteNames: List<String> = emptyList(),
    val remark: String? = null,
    val recipeProfile: PotRecipeProfile = PotRecipeProfile(potBottomId),
    val additives: FlavorAdditives = FlavorAdditives(),
)

data class PotAssignment(
    val slot: PotSlot,
    val flavor: PotFlavor,
)

data class ParsedPotOrder(
    val sourceOrderId: String,
    val mode: PotMode,
    val assignments: List<PotAssignment>,
    val tableCode: String? = null,
    val operatorName: String? = null,
    val deviceCode: String? = null,
)

enum class DispatchAction {
    FillBase,
    Additive,
    PromptManualRotate,
    WaitManualConfirm,
}

data class DispatchStep(
    val action: DispatchAction,
    val targetSlots: List<PotSlot>,
    val recipeCode: String? = null,
    val additiveType: AdditiveType? = null,
    val durationMs: Long? = null,
    val manualRotateTargetSlot: PotSlot? = null,
    val note: String,
)

data class DispatchPlan(
    val requiresRotation: Boolean,
    val steps: List<DispatchStep>,
)

object PotDispatchPlanner {
    private val additivePriority =
        listOf(
            PotSlot.TopLeft,
            PotSlot.BottomLeft,
            PotSlot.TopRight,
            PotSlot.BottomRight,
        )

    fun buildPlan(order: ParsedPotOrder): DispatchPlan {
        validate(order)
        val deferredManualSlots =
            order.assignments
                .filter { it.slot != PotSlot.TopLeft && it.flavor.recipeProfile.requiresTopLeftStation }
                .map { it.slot }
                .toSet()

        val baseSteps =
            when (order.mode) {
                PotMode.Single -> {
                    val flavor = order.assignments.first().flavor
                    listOf(
                        DispatchStep(
                            action = DispatchAction.FillBase,
                            targetSlots = executionSlots(order.mode, PotSlot.TopLeft),
                            recipeCode = flavor.baseCode,
                            durationMs = flavor.recipeProfile.waterDurationMs,
                            note = "Single pot: open all four spouts with the same base.",
                        ),
                    )
                }
                PotMode.Split -> {
                    val leftFlavor = order.assignments.first { it.slot == PotSlot.TopLeft }.flavor
                    val rightFlavor = order.assignments.first { it.slot == PotSlot.TopRight }.flavor
                    buildList {
                        add(
                            DispatchStep(
                                action = DispatchAction.FillBase,
                                targetSlots = executionSlots(order.mode, PotSlot.TopLeft),
                                recipeCode = leftFlavor.baseCode,
                                durationMs = leftFlavor.recipeProfile.waterDurationMs,
                                note = "Split pot left side: spouts 1 and 3.",
                            ),
                        )
                        if (PotSlot.TopRight !in deferredManualSlots) {
                            add(
                                DispatchStep(
                                    action = DispatchAction.FillBase,
                                    targetSlots = executionSlots(order.mode, PotSlot.TopRight),
                                    recipeCode = rightFlavor.baseCode,
                                    durationMs = rightFlavor.recipeProfile.waterDurationMs,
                                    note = "Split pot right side: spouts 2 and 4.",
                                ),
                            )
                        }
                    }
                }
                PotMode.ThreeGrid,
                PotMode.FourGrid,
                -> {
                    additivePriority.mapNotNull { slot ->
                        val assignment = order.assignments.firstOrNull { it.slot == slot } ?: return@mapNotNull null
                        if (slot !in deferredManualSlots) {
                            DispatchStep(
                                action = DispatchAction.FillBase,
                                targetSlots = executionSlots(order.mode, slot),
                                recipeCode = assignment.flavor.baseCode,
                                durationMs = assignment.flavor.recipeProfile.waterDurationMs,
                                note = "Four-grid pot: spout ${slot.spoutIndex} uses its own base.",
                            )
                        } else {
                            null
                        }
                    }
                }
            }

        val additiveSteps = buildAdditiveSteps(order)
        return DispatchPlan(
            requiresRotation = additiveSteps.any { it.action == DispatchAction.PromptManualRotate },
            steps = baseSteps + additiveSteps,
        )
    }

    private fun buildAdditiveSteps(order: ParsedPotOrder): List<DispatchStep> {
        val steps = mutableListOf<DispatchStep>()
        val orderedAssignments = order.assignments.sortedBy { additivePriority.indexOf(it.slot) }

        for (assignment in orderedAssignments) {
            val additiveTypes = assignment.flavor.additives.requestedTypes()
            if (additiveTypes.isEmpty()) {
                continue
            }

            if (assignment.slot != PotSlot.TopLeft) {
                steps +=
                    DispatchStep(
                        action = DispatchAction.PromptManualRotate,
                        targetSlots = executionSlots(order.mode, assignment.slot),
                        manualRotateTargetSlot = assignment.slot,
                        note = "Pause and prompt the operator to rotate the pot so ${assignment.slot.name} reaches the top-left station.",
                    )
                steps +=
                    DispatchStep(
                        action = DispatchAction.WaitManualConfirm,
                        targetSlots = executionSlots(order.mode, assignment.slot),
                        manualRotateTargetSlot = assignment.slot,
                        note = "Resume only after the operator confirms the manual rotation.",
                    )
                steps +=
                    DispatchStep(
                        action = DispatchAction.FillBase,
                        targetSlots = executionSlots(order.mode, assignment.slot),
                        recipeCode = assignment.flavor.baseCode,
                        durationMs = assignment.flavor.recipeProfile.waterDurationMs,
                        note = "After manual rotation, finish the remaining water for ${assignment.slot.name}.",
                    )
            }

            for (additiveType in additiveTypes) {
                steps +=
                    DispatchStep(
                        action = DispatchAction.Additive,
                        targetSlots = executionSlots(order.mode, assignment.slot),
                        additiveType = additiveType,
                        durationMs = assignment.flavor.recipeProfile.durationFor(additiveType),
                        note = "${additiveType.name} is dispensed after the target slot reaches the top-left additive outlet.",
                    )
            }
        }

        return steps
    }

    private fun validate(order: ParsedPotOrder) {
        val assignedSlots = order.assignments.map { it.slot }.toSet()
        when (order.mode) {
            PotMode.Single ->
                require(assignedSlots == setOf(PotSlot.TopLeft)) {
                    "Single pot expects one logical flavor assignment anchored on top-left."
                }
            PotMode.Split ->
                require(assignedSlots == setOf(PotSlot.TopLeft, PotSlot.TopRight)) {
                    "Split pot expects left flavor on top-left and right flavor on top-right."
                }
            PotMode.ThreeGrid ->
                require(
                    assignedSlots == setOf(PotSlot.TopLeft, PotSlot.TopRight, PotSlot.BottomLeft),
                ) {
                    "Three-grid pot expects top-left, top-right, and bottom-center logical assignments."
                }
            PotMode.FourGrid ->
                require(assignedSlots == PotSlot.entries.toSet()) {
                    "Four-grid pot expects all four slots."
                }
        }
    }

    private fun executionSlots(
        mode: PotMode,
        slot: PotSlot,
    ): List<PotSlot> =
        when (mode) {
            PotMode.Single -> PotSlot.entries
            PotMode.Split ->
                when (slot) {
                    PotSlot.TopLeft, PotSlot.BottomLeft -> listOf(PotSlot.TopLeft, PotSlot.BottomLeft)
                    PotSlot.TopRight, PotSlot.BottomRight -> listOf(PotSlot.TopRight, PotSlot.BottomRight)
                }
            PotMode.ThreeGrid -> listOf(slot)
            PotMode.FourGrid -> listOf(slot)
        }
}
