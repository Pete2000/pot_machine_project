package com.example.plccontroller.data.local

import com.example.plccontroller.domain.OrderStructureStatus
import com.example.plccontroller.domain.PosOrderLine
import com.example.plccontroller.domain.PotMode
import org.junit.Assert.assertEquals
import org.junit.Test

class PosOrderTaskAssemblerTest {
    @Test
    fun identicalPotsUnderSameBillKeepDistinctRootIds() {
        val tasks =
            PosOrderTaskAssembler.assemble(
                listOf(
                    line("root-1", null, "POT_TYPE", "四宫格", potTypeCode = "POT_TYPE"),
                    line("r1-b1", "root-1", "CLEAR", "清水", potBottomCode = "CLEAR"),
                    line("r1-b2", "root-1", "CLEAR", "清水", potBottomCode = "CLEAR"),
                    line("r1-b3", "root-1", "TOMATO", "番茄", potBottomCode = "TOMATO"),
                    line("r1-b4", "root-1", "SANXIAN", "三鲜", potBottomCode = "SANXIAN"),
                    line("root-2", null, "POT_TYPE", "四宫格", potTypeCode = "POT_TYPE"),
                    line("r2-b1", "root-2", "CLEAR", "清水", potBottomCode = "CLEAR"),
                    line("r2-b2", "root-2", "CLEAR", "清水", potBottomCode = "CLEAR"),
                    line("r2-b3", "root-2", "TOMATO", "番茄", potBottomCode = "TOMATO"),
                    line("r2-b4", "root-2", "SANXIAN", "三鲜", potBottomCode = "SANXIAN"),
                ),
            )

        assertEquals(listOf("root-1", "root-2"), tasks.map { it.order.id })
        assertEquals(1, tasks.map(PosOrderTask::baseHash).distinct().size)
    }

    @Test
    fun assembleUsesApiArrayOrderInsteadOfPotSort() {
        val tasks =
            PosOrderTaskAssembler.assemble(
                listOf(
                    line(
                        id = "root",
                        parentId = null,
                        posFoodCode = "POT_TYPE_FOUR_GRID",
                        potTypeCode = "POT_TYPE_FOUR_GRID",
                        standardName = "四宫格",
                    ),
                    line(
                        id = "bottom-1",
                        parentId = "root",
                        posFoodCode = "CLEAR",
                        potBottomCode = "CLEAR",
                        standardName = "清水锅",
                        potSort = 4,
                    ),
                    line(
                        id = "bottom-2",
                        parentId = "root",
                        posFoodCode = "TOMATO",
                        potBottomCode = "TOMATO",
                        standardName = "番茄锅",
                        potSort = 1,
                    ),
                    line(
                        id = "bottom-3",
                        parentId = "root",
                        posFoodCode = "SANXIAN",
                        potBottomCode = "SANXIAN",
                        standardName = "三鲜锅",
                        potSort = 2,
                    ),
                    line(
                        id = "bottom-4",
                        parentId = "root",
                        posFoodCode = "MUSHROOM",
                        potBottomCode = "MUSHROOM",
                        standardName = "菌汤锅",
                        potSort = 3,
                    ),
                ),
            )

        assertEquals(1, tasks.size)
        val order = tasks.single().order
        assertEquals(PotMode.FourGrid, order.potMode)
        assertEquals("左上:清水锅|右上:番茄锅|左下:三鲜锅|右下:菌汤锅", order.slotSummary)
        assertEquals("左上:CLEAR|右上:TOMATO|左下:SANXIAN|右下:MUSHROOM", order.slotCodeSummary)
        assertEquals("清水锅,番茄锅,三鲜锅,菌汤锅", order.potBottomSummary)
    }

    @Test
    fun assembleMarksIncompleteFromApiWhenFourGridBottomCandidatesAreNotEnough() {
        val tasks =
            PosOrderTaskAssembler.assemble(
                listOf(
                    line(
                        id = "root",
                        parentId = null,
                        posFoodCode = "POT_TYPE_FOUR_GRID",
                        potTypeCode = "POT_TYPE_FOUR_GRID",
                        standardName = "四宫格",
                    ),
                    line("bottom-1", "root", "CLEAR", "清水锅", potBottomCode = "CLEAR"),
                    line("bottom-2", "root", "TOMATO", "番茄锅", potBottomCode = "TOMATO"),
                    line("bottom-3", "root", "SANXIAN", "三鲜锅", potBottomCode = "SANXIAN"),
                ),
            )

        val order = tasks.single().order
        assertEquals(PotMode.FourGrid, order.potMode)
        assertEquals(OrderStructureStatus.IncompleteFromApi, order.structureStatus)
        assertEquals(4, order.expectedSlotCount)
        assertEquals(3, order.attachedBottomCount)
        assertEquals(3, order.rawBottomCandidateCount)
        assertEquals(listOf("右下"), order.missingSlotLabels)
    }

    @Test
    fun assembleRecoversWhenBottomExistsInApiWindowButParentIdIsMissing() {
        val tasks =
            PosOrderTaskAssembler.assemble(
                listOf(
                    line(
                        id = "root",
                        parentId = null,
                        posFoodCode = "POT_TYPE_FOUR_GRID",
                        potTypeCode = "POT_TYPE_FOUR_GRID",
                        standardName = "四宫格",
                    ),
                    line("bottom-1", "root", "CLEAR", "清水锅", potBottomCode = "CLEAR"),
                    line("bottom-2", "root", "TOMATO", "番茄锅", potBottomCode = "TOMATO"),
                    line("bottom-3", "root", "SANXIAN", "三鲜锅", potBottomCode = "SANXIAN"),
                    line("bottom-4", null, "MUSHROOM", "菌汤锅", potBottomCode = "MUSHROOM"),
                ),
            )

        val order = tasks.single().order
        assertEquals(OrderStructureStatus.ParseRecovered, order.structureStatus)
        assertEquals(4, order.expectedSlotCount)
        assertEquals(3, order.attachedBottomCount)
        assertEquals(4, order.rawBottomCandidateCount)
        assertEquals("左上:清水锅|右上:番茄锅|左下:三鲜锅|右下:菌汤锅", order.slotSummary)
        assertEquals(emptyList<String>(), order.missingSlotLabels)
    }

    @Test
    fun assembleMarksParseLostWhenRecoveryCandidatesAreAmbiguous() {
        val tasks =
            PosOrderTaskAssembler.assemble(
                listOf(
                    line(
                        id = "root",
                        parentId = null,
                        posFoodCode = "POT_TYPE_FOUR_GRID",
                        potTypeCode = "POT_TYPE_FOUR_GRID",
                        standardName = "四宫格",
                    ),
                    line("bottom-1", "root", "CLEAR", "清水锅", potBottomCode = "CLEAR"),
                    line("bottom-2", "root", "TOMATO", "番茄锅", potBottomCode = "TOMATO"),
                    line("bottom-3", "root", "SANXIAN", "三鲜锅", potBottomCode = "SANXIAN"),
                    line("bottom-4", null, "MUSHROOM", "菌汤锅", potBottomCode = "MUSHROOM"),
                    line("bottom-5", null, "PORK", "猪肚鸡锅", potBottomCode = "PORK"),
                ),
            )

        val order = tasks.single().order
        assertEquals(OrderStructureStatus.ParseLostSuspected, order.structureStatus)
        assertEquals(4, order.expectedSlotCount)
        assertEquals(3, order.attachedBottomCount)
        assertEquals(5, order.rawBottomCandidateCount)
        assertEquals(listOf("右下"), order.missingSlotLabels)
    }

    private fun line(
        id: String,
        parentId: String?,
        posFoodCode: String?,
        standardName: String,
        potTypeCode: String? = null,
        potBottomCode: String? = null,
        potSort: Int? = null,
    ): PosOrderLine =
        PosOrderLine(
            id = id,
            ikmsOrder = "IKMS-1",
            posOrder = "POS-1",
            mainPosOrder = "POS-1",
            parentId = parentId,
            operation = "301",
            operator = "tester",
            operateTime = "2026-06-14 10:00:00",
            tableCode = "11",
            turnTableCode = null,
            posFoodCode = posFoodCode,
            potTypeCode = potTypeCode,
            potBottomCode = potBottomCode,
            tasteCode = null,
            foodCount = 1,
            deviceCode = null,
            storeCode = "5002",
            createTime = "2026-06-14 10:00:00",
            updateTime = "2026-06-14 10:00:00",
            flag = 0,
            testTime = "2026-06-14 10:00:00",
            potSort = potSort,
            description = null,
            standardName = standardName,
            soupMachineCode = null,
        )
}
