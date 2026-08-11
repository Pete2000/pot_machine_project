package com.example.plccontroller.domain.phase

import com.example.plccontroller.domain.ManualWaterPhaseRequest
import com.example.plccontroller.domain.ManualWaterPhaseSlot
import com.example.plccontroller.domain.ManualWaterPotMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PhasePlannerTest {
    @Test
    fun manualSingleAdditiveAwayFromTopLeftPromptsAndIncludesOrdinaryWaterOnce() {
        val plans =
            PhasePlanner.planManual(
                request =
                    ManualWaterPhaseRequest(
                        mode = ManualWaterPotMode.FourGrid,
                        slots =
                            listOf(
                                waterSlot(1, "清汤锅"),
                                waterSlot(2, "番茄锅"),
                                waterSlot(3, "菌汤锅"),
                                additiveSlot(4, "三鲜锅"),
                            ),
                    ),
                currentTopLeftLogicalSlot = 1,
                phaseNoStart = 7,
            )

        assertEquals(1, plans.size)
        val plan = plans.single()
        assertEquals(PhasePlanSource.Manual, plan.source)
        assertEquals(7, plan.phaseNo)
        assertEquals(4, plan.activeTopLeftLogicalSlot)
        assertEquals(4, plan.additiveSlot?.logicalSlot)
        assertEquals(listOf(1, 2, 3, 4), plan.waterSlots.map { it.logicalSlot })
        assertEquals("手动加水需要转锅", plan.rotationPrompt?.title)
        assertEquals(PhasePromptKind.Rotation, plan.rotationPrompt?.kind)
        assertEquals(4, plan.rotationPrompt?.targetLogicalSlot)
        assertTrue(
            plan.rotationPrompt
                ?.message
                .orEmpty()
                .contains("右下工位（出水口4）"),
        )
        assertTrue(
            plan.rotationPrompt
                ?.message
                .orEmpty()
                .contains("左上工位（出水口1）"),
        )
    }

    @Test
    fun orderSingleAdditiveAwayFromTopLeftPlansFirstPhaseWithoutPrompt() {
        val plans =
            PhasePlanner.planOrder(
                mode = ManualWaterPotMode.FourGrid,
                slots =
                    listOf(
                        waterSlot(1, "清汤锅"),
                        waterSlot(2, "番茄锅"),
                        waterSlot(3, "菌汤锅"),
                        additiveSlot(4, "三鲜锅"),
                    ),
                currentTopLeftLogicalSlot = 1,
                orderId = "order-1",
            )

        assertEquals(1, plans.size)
        val plan = plans.single()
        assertEquals(PhasePlanSource.Order, plan.source)
        assertEquals(4, plan.activeTopLeftLogicalSlot)
        assertEquals(4, plan.additiveSlot?.logicalSlot)
        assertEquals(listOf(1, 2, 3, 4), plan.waterSlots.map { it.logicalSlot })
        assertNull(plan.rotationPrompt)
    }

    @Test
    fun manualMultipleAdditivesAreSplitByClockwiseDistanceAndOrdinaryWaterRunsOnce() {
        val plans =
            PhasePlanner.planManual(
                request =
                    ManualWaterPhaseRequest(
                        mode = ManualWaterPotMode.FourGrid,
                        slots =
                            listOf(
                                waterSlot(1, "清汤锅"),
                                additiveSlot(2, "三鲜锅"),
                                waterSlot(3, "菌汤锅"),
                                additiveSlot(4, "猪肚鸡锅"),
                            ),
                    ),
                currentTopLeftLogicalSlot = 1,
            )

        assertEquals(2, plans.size)
        assertEquals(1, plans[0].phaseNo)
        assertEquals(2, plans[0].activeTopLeftLogicalSlot)
        assertEquals(2, plans[0].additiveSlot?.logicalSlot)
        assertEquals(listOf(1, 3, 2), plans[0].waterSlots.map { it.logicalSlot })
        assertEquals("手动加水需要转锅", plans[0].rotationPrompt?.title)

        assertEquals(2, plans[1].phaseNo)
        assertEquals(4, plans[1].activeTopLeftLogicalSlot)
        assertEquals(4, plans[1].additiveSlot?.logicalSlot)
        assertEquals(listOf(4), plans[1].waterSlots.map { it.logicalSlot })
        assertEquals("手动加水需要转锅", plans[1].rotationPrompt?.title)
    }

    @Test
    fun orderMultipleAdditivesPromptsFromSecondAdditivePhase() {
        val plans =
            PhasePlanner.planOrder(
                mode = ManualWaterPotMode.FourGrid,
                slots =
                    listOf(
                        waterSlot(1, "清汤锅"),
                        additiveSlot(2, "三鲜锅"),
                        waterSlot(3, "菌汤锅"),
                        additiveSlot(4, "猪肚鸡锅"),
                    ),
                currentTopLeftLogicalSlot = 1,
                orderId = "order-2",
            )

        assertEquals(2, plans.size)
        assertEquals(2, plans[0].activeTopLeftLogicalSlot)
        assertNull(plans[0].rotationPrompt)
        assertEquals(listOf(1, 3, 2), plans[0].waterSlots.map { it.logicalSlot })

        assertEquals(4, plans[1].activeTopLeftLogicalSlot)
        assertEquals("订单需要转锅", plans[1].rotationPrompt?.title)
        assertEquals(4, plans[1].rotationPrompt?.targetLogicalSlot)
        assertEquals(listOf(4), plans[1].waterSlots.map { it.logicalSlot })
    }

    @Test
    fun missingFormulaAddsPromptButKeepsExecutableSlots() {
        val plans =
            PhasePlanner.planManual(
                request =
                    ManualWaterPhaseRequest(
                        mode = ManualWaterPotMode.FourGrid,
                        slots =
                            listOf(
                                waterSlot(1, "清汤锅"),
                                waterSlot(2, "番茄锅", formulaMissing = true),
                                waterSlot(3, "菌汤锅"),
                            ),
                    ),
                currentTopLeftLogicalSlot = 1,
            )

        assertEquals(1, plans.size)
        val plan = plans.single()
        assertEquals(listOf(1, 3), plan.waterSlots.map { it.logicalSlot })
        assertNotNull(plan.rotationPrompt)
        assertEquals("手动配方缺失", plan.rotationPrompt?.title)
        assertEquals(PhasePromptKind.FormulaMissing, plan.rotationPrompt?.kind)
        assertEquals("继续执行可用锅底", plan.rotationPrompt?.confirmText)
        assertTrue(
            plan.rotationPrompt
                ?.message
                .orEmpty()
                .contains("2号位 番茄锅"),
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun allMissingFormulaThrows() {
        PhasePlanner.planManual(
            request =
                ManualWaterPhaseRequest(
                    mode = ManualWaterPotMode.FourGrid,
                    slots =
                        listOf(
                            waterSlot(1, "清汤锅", formulaMissing = true),
                            waterSlot(2, "番茄锅", formulaMissing = true),
                        ),
                ),
            currentTopLeftLogicalSlot = 1,
        )
    }

    private fun waterSlot(
        logicalSlot: Int,
        label: String,
        formulaMissing: Boolean = false,
    ): ManualWaterPhaseSlot =
        ManualWaterPhaseSlot(
            logicalSlot = logicalSlot,
            waterDurationMs = 2_500L,
            label = label,
            formulaMissing = formulaMissing,
        )

    private fun additiveSlot(
        logicalSlot: Int,
        label: String,
    ): ManualWaterPhaseSlot =
        ManualWaterPhaseSlot(
            logicalSlot = logicalSlot,
            waterDurationMs = 5_000L,
            chickenOilDurationMs = 4_000L,
            bonePasteDurationMs = 3_000L,
            label = label,
        )
}
