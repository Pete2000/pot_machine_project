package com.example.plccontroller.runtime

import com.example.plccontroller.data.AppPersistentConfig
import com.example.plccontroller.domain.FormulaCatalog
import com.example.plccontroller.domain.FormulaPot
import com.example.plccontroller.domain.FormulaPotType
import com.example.plccontroller.domain.Order
import com.example.plccontroller.domain.OrderStructureStatus
import com.example.plccontroller.domain.PotMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PhaseCommandPlanBuilderTest {
    @Test
    fun orderPhaseMatchesFormulaByPotCodeAndRotatesSingleAdditiveToTopLeft() {
        val builder = builderWith(catalog = testCatalog())
        val order =
            order(
                slotSummary = "左上:清水锅|右上:番茄锅|左下:菌汤锅|右下:清水锅",
                slotCodeSummary = "左上:CLEAR|右上:TOMATO|左下:MUSHROOM|右下:SANXIAN",
            )

        val result =
            builder.buildOrderPhaseCommands(
                order = order,
                currentTopLeftLogicalSlot = 1,
            )
        val plans = result.plans

        assertEquals(1, plans.size)
        val plan = plans.single()
        val command = plan.command
        assertEquals(PhaseFlowSource.Order, plan.source)
        assertNull(plan.rotationPrompt)
        assertEquals(1, command.commandType)
        assertEquals(1, command.phaseNo)
        assertEquals(4, command.activeTopLeftLogicalSlot)
        assertEquals(15, command.jobSlotMask)
        assertEquals(50, command.waterDuration1)
        assertEquals(25, command.waterDuration2)
        assertEquals(25, command.waterDuration3)
        assertEquals(25, command.waterDuration4)
        assertEquals(40, command.chickenOilDuration)
        assertEquals(30, command.bonePasteDuration)
    }

    @Test
    fun orderPhaseSplitsMultipleAdditiveSlotsAndPromptsFromSecondPhase() {
        val builder = builderWith(catalog = testCatalog())
        val order =
            order(
                slotSummary = "左上:清水锅|右上:三鲜锅|左下:菌汤锅|右下:猪肚鸡火锅",
                slotCodeSummary = "左上:CLEAR|右上:SANXIAN|左下:MUSHROOM|右下:PORK_CHICKEN",
            )

        val result =
            builder.buildOrderPhaseCommands(
                order = order,
                currentTopLeftLogicalSlot = 1,
            )
        val plans = result.plans

        assertEquals(2, plans.size)

        val first = plans[0].command
        assertNull(plans[0].rotationPrompt)
        assertEquals(1, first.phaseNo)
        assertEquals(2, first.activeTopLeftLogicalSlot)
        assertEquals(13, first.jobSlotMask)
        assertEquals(50, first.waterDuration1)
        assertEquals(0, first.waterDuration2)
        assertEquals(25, first.waterDuration3)
        assertEquals(25, first.waterDuration4)
        assertEquals(40, first.chickenOilDuration)
        assertEquals(30, first.bonePasteDuration)

        val second = plans[1].command
        assertEquals("订单需要转锅", plans[1].rotationPrompt?.title)
        assertEquals(4, plans[1].rotationPrompt?.targetLogicalSlot)
        assertEquals(2, second.phaseNo)
        assertEquals(4, second.activeTopLeftLogicalSlot)
        assertEquals(1, second.jobSlotMask)
        assertEquals(60, second.waterDuration1)
        assertEquals(0, second.waterDuration2)
        assertEquals(0, second.waterDuration3)
        assertEquals(0, second.waterDuration4)
        assertEquals(0, second.chickenOilDuration)
        assertEquals(50, second.bonePasteDuration)
    }

    @Test(expected = IllegalArgumentException::class)
    fun orderPhaseRejectsBlockingStructureIssueBeforePlanningPlcCommand() {
        val builder = builderWith(catalog = testCatalog())
        val order =
            order(
                slotSummary = "左上:清水锅|右上:番茄锅|左下:菌汤锅",
                slotCodeSummary = "左上:CLEAR|右上:TOMATO|左下:MUSHROOM",
                structureStatus = OrderStructureStatus.IncompleteFromApi,
            )

        builder.buildOrderPhaseCommands(
            order = order,
            currentTopLeftLogicalSlot = 1,
        )
    }

    @Test
    fun orderWithPartialMissingFormulaSkipsMissingSlotWithoutConfirmationPrompt() {
        val builder = builderWith(catalog = testCatalog())
        val order =
            order(
                slotSummary = "左上:清水锅|右上:番茄锅|左下:未知锅底|右下:清水锅",
                slotCodeSummary = "左上:CLEAR|右上:TOMATO|左下:MISSING_CODE|右下:CLEAR",
            )

        val result =
            builder.buildOrderPhaseCommands(
                order = order,
                currentTopLeftLogicalSlot = 1,
            )
        val plans = result.plans

        assertEquals(1, result.missingSlots.size)
        assertEquals(3, result.missingSlots.single().logicalSlot)
        assertEquals(1, plans.size)
        val plan = plans.single()
        assertEquals(PhaseFlowSource.Order, plan.source)
        assertNull(plan.rotationPrompt)
        assertEquals(11, plan.command.jobSlotMask)
        assertEquals(0, plan.command.waterDuration3)
    }

    @Test
    fun partialMissingFormulaCreatesNonBlockingWarningNotice() {
        val builder = builderWith(catalog = testCatalog())
        val order =
            order(
                slotSummary = "左上:清水锅|右上:番茄锅|左下:未知锅底|右下:清水锅",
                slotCodeSummary = "左上:CLEAR|右上:TOMATO|左下:MISSING_CODE|右下:CLEAR",
            )

        val result =
            builder.buildOrderPhaseCommands(
                order = order,
                currentTopLeftLogicalSlot = 1,
            )
        val notice = result.toOperatorNotice(order)

        assertEquals("formula-missing-partial:order-1", notice?.id)
        assertEquals(OperatorNoticeLevel.Warning, notice?.level)
        assertEquals("order-1", notice?.relatedOrderId)
        assertEquals("部分锅底未匹配配方，已跳过：3号位 未知锅底；其余锅底正常执行", notice?.message)
    }

    @Test
    fun orderWithAllMissingFormulaReturnsNoPhysicalPlans() {
        val builder = builderWith(catalog = testCatalog())
        val order =
            order(
                slotSummary = "左上:未知1|右上:未知2|左下:未知3|右下:未知4",
                slotCodeSummary = "左上:M1|右上:M2|左下:M3|右下:M4",
            )

        val result =
            builder.buildOrderPhaseCommands(
                order = order,
                currentTopLeftLogicalSlot = 1,
            )

        assertTrue(result.allSlotsMissing)
        assertTrue(result.plans.isEmpty())
        assertEquals(listOf(1, 2, 3, 4), result.missingSlots.map { it.logicalSlot })
        assertNull(result.toOperatorNotice(order))
    }

    @Test
    fun orderWithAllZeroMatchedFormulaReturnsNoPhysicalPlansButIsNotMissing() {
        val builder = builderWith(catalog = testCatalog())
        val order =
            order(
                slotSummary = "左上:不用加锅|右上:不用加锅|左下:不用加锅|右下:不用加锅",
                slotCodeSummary = "左上:NO_ACTION|右上:NO_ACTION|左下:NO_ACTION|右下:NO_ACTION",
            )

        val result =
            builder.buildOrderPhaseCommands(
                order = order,
                currentTopLeftLogicalSlot = 1,
            )

        assertFalse(result.allSlotsMissing)
        assertTrue(result.missingSlots.isEmpty())
        assertTrue(result.plans.isEmpty())
        assertEquals(listOf(1, 2, 3, 4), result.noActionSlots.map { it.logicalSlot })

        val notice = result.toOperatorNotice(order)
        assertEquals("formula-no-action:order-1", notice?.id)
        assertEquals(OperatorNoticeLevel.Info, notice?.level)
        assertEquals("该订单配方无需设备执行，已跳过加水", notice?.message)
    }

    @Test
    fun orderWithPartialZeroMatchedFormulaSkipsZeroSlotSilently() {
        val builder = builderWith(catalog = testCatalog())
        val order =
            order(
                slotSummary = "左上:清水锅|右上:不用加锅|左下:菌汤锅|右下:清水锅",
                slotCodeSummary = "左上:CLEAR|右上:NO_ACTION|左下:MUSHROOM|右下:CLEAR",
            )

        val result =
            builder.buildOrderPhaseCommands(
                order = order,
                currentTopLeftLogicalSlot = 1,
            )
        val plans = result.plans

        assertTrue(result.missingSlots.isEmpty())
        assertEquals(listOf(2), result.noActionSlots.map { it.logicalSlot })
        assertEquals(1, plans.size)
        assertNull(plans.single().rotationPrompt)
        assertEquals(13, plans.single().command.jobSlotMask)
        assertEquals(0, plans.single().command.waterDuration2)
        assertNull(result.toOperatorNotice(order))
    }

    @Test
    fun partialMissingFormulaWithOnlyZeroMatchedSlotsCreatesWarningNotice() {
        val builder = builderWith(catalog = testCatalog())
        val order =
            order(
                slotSummary = "左上:不用加锅|右上:未知锅底|左下:不用加锅|右下:不用加锅",
                slotCodeSummary = "左上:NO_ACTION|右上:MISSING_CODE|左下:NO_ACTION|右下:NO_ACTION",
            )

        val result =
            builder.buildOrderPhaseCommands(
                order = order,
                currentTopLeftLogicalSlot = 1,
            )
        val notice = result.toOperatorNotice(order)

        assertTrue(result.plans.isEmpty())
        assertEquals(OperatorNoticeLevel.Warning, notice?.level)
        assertEquals("部分锅底未匹配配方，已跳过：2号位 未知锅底；其余锅底无需设备执行，已跳过加水", notice?.message)
    }

    private fun builderWith(
        catalog: FormulaCatalog,
        config: AppPersistentConfig = testConfig(),
    ): PhaseCommandPlanBuilder {
        var commandId = 10
        val runtimeStore =
            MachineRuntimeStore(
                initialState = MachineRuntimeState(formulaCatalog = catalog),
            )
        return PhaseCommandPlanBuilder(
            runtimeStore = runtimeStore,
            configSnapshot = { config },
            cachedFormulaCatalog = { null },
            nextPlcCommandId = { ++commandId },
        )
    }

    private fun order(
        slotSummary: String,
        slotCodeSummary: String,
        structureStatus: OrderStructureStatus = OrderStructureStatus.Complete,
    ): Order =
        Order(
            id = "order-1",
            recipeCode = POT_TYPE_CODE,
            quantity = 1,
            targetTemperature = 0,
            cookSeconds = 0,
            spiceLevel = 0,
            potMode = PotMode.FourGrid,
            slotSummary = slotSummary,
            rootPosFoodCode = POT_TYPE_CODE,
            slotCodeSummary = slotCodeSummary,
            structureStatus = structureStatus,
            expectedSlotCount = 4,
            attachedBottomCount = slotSummary.split("|").size,
            rawBottomCandidateCount = slotSummary.split("|").size,
        )

    private fun testCatalog(): FormulaCatalog =
        FormulaCatalog(
            formulaCode = "formula-1",
            formulaName = "测试配方",
            whetherDefault = true,
            pots =
                listOf(
                    pot("CLEAR", "清水锅", waterSeconds = 2.5),
                    pot("TOMATO", "番茄锅", waterSeconds = 2.5),
                    pot("MUSHROOM", "菌汤锅", waterSeconds = 2.5),
                    pot("NO_ACTION", "不用加锅", waterSeconds = 0.0),
                    pot("SANXIAN", "三鲜锅", waterSeconds = 5.0, chickenSeconds = 4.0, boneSeconds = 3.0),
                    pot("PORK_CHICKEN", "猪肚鸡火锅", waterSeconds = 6.0, boneSeconds = 5.0),
                ),
        )

    private fun pot(
        code: String,
        name: String,
        waterSeconds: Double,
        chickenSeconds: Double = 0.0,
        boneSeconds: Double = 0.0,
    ): FormulaPot =
        FormulaPot(
            potCode = code,
            potName = name,
            potTypes =
                listOf(
                    FormulaPotType(
                        potTypeCode = POT_TYPE_CODE,
                        potTypeName = "四宫格",
                        addWaterSeconds = waterSeconds,
                        addChickenOilSeconds = chickenSeconds,
                        addBonePasteSeconds = boneSeconds,
                        materials = "",
                        basicMaterials = "",
                    ),
                ),
        )

    private fun testConfig(): AppPersistentConfig =
        AppPersistentConfig(
            businessUrl = "",
            managementUrl = "",
            deviceCode = "",
            plcSerialPortPath = "/dev/ttyS4",
            plcBaudRate = 19_200,
            plcDataBits = 8,
            plcParity = "NONE",
            plcStopBits = 1,
            plcSlaveId = 1,
            readTimeoutMs = 500L,
            writeTimeoutMs = 500L,
            retryCount = 1,
            frameGapMs = 200L,
            registerOnlyMode = false,
            registerOnlyBlockStart = 0,
            registerOnlyBlockCount = 0,
            heartbeatPeriodMs = 1_000L,
            activePollingIntervalMs = 200L,
            idlePollingIntervalMs = 500L,
            orderPollingIntervalMs = 5_000L,
            formulaSyncIntervalSeconds = 30L,
            connectionTestRegister = 200,
            deviceHeaterTargetTemp = 90,
            deviceHeaterHysteresisTemp = 5,
            deviceHeaterSelect = 1,
            deviceHeaterSensorSelect = 0,
            deviceStandaloneWaterMode = 2,
            deviceStandaloneWaterTimedTicks = 100,
            deviceWaterOutlet1CalibrationPercent = 100,
            deviceWaterOutlet2CalibrationPercent = 100,
            deviceWaterOutlet3CalibrationPercent = 100,
            deviceWaterOutlet4CalibrationPercent = 100,
            deviceChickenOilCalibrationPercent = 100,
            deviceBonePasteCalibrationPercent = 100,
            deviceSparePumpMode = 0,
        )

    private companion object {
        const val POT_TYPE_CODE = "POT_TYPE_FOUR_GRID"
    }
}
