package com.example.plccontroller.domain

data class PosOrderLine(
    val id: String,
    val ikmsOrder: String?,
    val posOrder: String?,
    val mainPosOrder: String?,
    val parentId: String?,
    val operation: String?,
    val operator: String?,
    val operateTime: String?,
    val tableCode: String?,
    val turnTableCode: String?,
    val posFoodCode: String?,
    val potTypeCode: String?,
    val potBottomCode: String?,
    val tasteCode: String?,
    val foodCount: Int,
    val deviceCode: String?,
    val storeCode: String?,
    val createTime: String?,
    val updateTime: String?,
    val flag: Int?,
    val testTime: String?,
    val potSort: Int?,
    val description: String?,
    val standardName: String?,
    val soupMachineCode: String?,
)

data class PotBottomNode(
    val line: PosOrderLine,
    val tastes: List<PosOrderLine>,
)

object PotOrderParser {
    fun parse(
        lines: List<PosOrderLine>,
        recipeProfilesByPotBottomId: Map<String, PotRecipeProfile> = emptyMap(),
    ): List<ParsedPotOrder> {
        val linesByParent = lines.groupBy { it.parentId }
        val rootLines = lines.filter { !it.potTypeCode.isNullOrBlank() && it.parentId.isNullOrBlank() }

        return rootLines.map { root ->
            val bottomLines =
                linesByParent[root.id]
                    .orEmpty()
                    .filter { !it.potBottomCode.isNullOrBlank() }
            val mode = resolveMode(bottomLines = bottomLines)
            val assignments =
                bottomLines.mapIndexed { index, bottom ->
                    val tastes =
                        linesByParent[bottom.id]
                            .orEmpty()
                            .filter { !it.tasteCode.isNullOrBlank() }
                    val slot = resolveSlot(mode = mode, index = index)
                    val tasteNames = tastes.mapNotNull { it.standardName?.takeIf(String::isNotBlank) }
                    val tasteCodes = tastes.mapNotNull { it.tasteCode?.takeIf(String::isNotBlank) }
                    val remark =
                        listOfNotNull(
                            bottom.description?.takeIf(String::isNotBlank),
                            tastes
                                .mapNotNull { it.description?.takeIf(String::isNotBlank) }
                                .joinToString(" ")
                                .ifBlank { null },
                        ).joinToString(" / ").ifBlank { null }
                    val potBottomId = bottom.potBottomCode ?: bottom.posFoodCode.orEmpty()
                    val recipeProfile =
                        recipeProfilesByPotBottomId[potBottomId]
                            ?: PotRecipeProfile(potBottomId = potBottomId)

                    PotAssignment(
                        slot = slot,
                        flavor =
                            PotFlavor(
                                potBottomId = potBottomId,
                                baseCode = potBottomId,
                                baseName = bottom.standardName ?: potBottomId,
                                tasteCodes = tasteCodes,
                                tasteNames = tasteNames,
                                remark = remark,
                                recipeProfile = recipeProfile,
                                additives = recipeProfile.additives(),
                            ),
                    )
                }

            ParsedPotOrder(
                sourceOrderId = root.posOrder ?: root.mainPosOrder ?: root.id,
                mode = mode,
                assignments = normalizeAssignments(mode = mode, assignments = assignments),
                tableCode = root.tableCode,
                operatorName = root.operator,
                deviceCode = root.deviceCode ?: root.soupMachineCode,
            )
        }
    }

    private fun resolveMode(bottomLines: List<PosOrderLine>): PotMode =
        when {
            bottomLines.size >= 4 -> PotMode.FourGrid
            bottomLines.size == 3 -> PotMode.ThreeGrid
            bottomLines.size == 2 -> PotMode.Split
            else -> PotMode.Single
        }

    private fun resolveSlot(
        mode: PotMode,
        index: Int,
    ): PotSlot =
        when (mode) {
            PotMode.Single -> PotSlot.TopLeft
            PotMode.Split ->
                when (index + 1) {
                    1 -> PotSlot.TopLeft
                    2 -> PotSlot.TopRight
                    else -> if (index == 0) PotSlot.TopLeft else PotSlot.TopRight
                }
            PotMode.ThreeGrid ->
                when (index + 1) {
                    1 -> PotSlot.TopLeft
                    2 -> PotSlot.TopRight
                    3 -> PotSlot.BottomLeft
                    else -> PotSlot.entries[index.coerceIn(0, PotSlot.entries.lastIndex)]
                }
            PotMode.FourGrid ->
                when (index + 1) {
                    1 -> PotSlot.TopLeft
                    2 -> PotSlot.TopRight
                    3 -> PotSlot.BottomLeft
                    4 -> PotSlot.BottomRight
                    else -> PotSlot.entries[index.coerceIn(0, PotSlot.entries.lastIndex)]
                }
        }

    private fun normalizeAssignments(
        mode: PotMode,
        assignments: List<PotAssignment>,
    ): List<PotAssignment> {
        if (mode != PotMode.FourGrid) {
            return assignments
        }
        return PotSlot.entries.mapNotNull { slot ->
            assignments.firstOrNull { it.slot == slot }
        }
    }
}
