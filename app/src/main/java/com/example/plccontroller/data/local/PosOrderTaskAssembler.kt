package com.example.plccontroller.data.local

import com.example.plccontroller.domain.Order
import com.example.plccontroller.domain.OrderStatus
import com.example.plccontroller.domain.OrderStructureStatus
import com.example.plccontroller.domain.PosOrderLine
import com.example.plccontroller.domain.PotMode

data class PosOrderTask(
    val operation: String,
    val baseHash: String,
    val order: Order,
    val previousOrderAlias: String? = null,
    val currentOrderAliases: List<String> = emptyList(),
    val targetTableCode: String? = null,
    val previousBaseHash: String? = null,
)

object PosOrderTaskAssembler {
    fun assemble(lines: List<PosOrderLine>): List<PosOrderTask> {
        if (lines.isEmpty()) return emptyList()
        val originalIndex = lines.mapIndexed { index, line -> line.id to index }.toMap()
        val childrenByParent = lines.groupBy { it.normalizedParentId() }
        val roots =
            lines.filter { line ->
                line.normalizedParentId() == null && line.isPotTypeCandidate()
            }
        return roots.mapNotNull { root ->
            val descendants =
                collectDescendants(
                    root = root,
                    childrenByParent = childrenByParent,
                    originalIndex = originalIndex,
                )
            val attachedBottomLines = descendants.filter { it.isPotBottomCandidate() }
            val candidateWindow =
                collectRootWindowBottomCandidates(
                    root = root,
                    lines = lines,
                    roots = roots,
                    originalIndex = originalIndex,
                )
            val mode =
                resolveMode(
                    root = root,
                    attachedBottomLines = attachedBottomLines,
                    rawBottomCandidates = candidateWindow,
                )
            val bottomResolution =
                resolveBottomStructure(
                    mode = mode,
                    attachedBottomLines = attachedBottomLines,
                    rawBottomCandidates = candidateWindow,
                    originalIndex = originalIndex,
                )
            val bottomLines = bottomResolution.bottomLines
            val taskLines =
                buildTaskLines(
                    bottomLines = bottomLines,
                    childrenByParent = childrenByParent,
                    originalIndex = originalIndex,
                )
            val identityLines = (descendants + bottomLines).distinctBy { it.id }

            val ikmsOrder =
                firstNonBlank(
                    root.ikmsOrder,
                    root.mainPosOrder,
                    root.posOrder,
                    identityLines.firstNonBlankOf(PosOrderLine::ikmsOrder),
                    identityLines.firstNonBlankOf(PosOrderLine::mainPosOrder),
                    identityLines.firstNonBlankOf(PosOrderLine::posOrder),
                    root.id,
                ) ?: return@mapNotNull null
            val rootFoodCode =
                firstNonBlank(root.posFoodCode, root.potTypeCode, root.id)
                    ?: return@mapNotNull null
            val orderedChildFoodCodes =
                taskLines
                    .mapNotNull { line ->
                        firstNonBlank(line.posFoodCode, line.potBottomCode, line.tasteCode)
                    }
            val baseHash =
                HashUtil.potTaskBaseHash(
                    ikmsOrder = ikmsOrder,
                    rootPosFoodCode = rootFoodCode,
                    orderedChildFoodCodes = orderedChildFoodCodes,
                )
            val operation =
                firstNonBlank(
                    root.operation,
                    identityLines.firstNonBlankOf(PosOrderLine::operation),
                ) ?: "301"
            val currentOrderAliases =
                normalizeAliases(
                    root.ikmsOrder,
                    root.posOrder,
                    identityLines.firstNonBlankOf(PosOrderLine::ikmsOrder),
                    identityLines.firstNonBlankOf(PosOrderLine::posOrder),
                    ikmsOrder,
                )
            val previousOrderAlias =
                if (operation == "304") {
                    firstNonBlank(
                        root.mainPosOrder,
                        identityLines.firstNonBlankOf(PosOrderLine::mainPosOrder),
                    )
                } else {
                    null
                }
            val targetTableCode =
                if (operation == "304") {
                    firstNonBlank(
                        root.turnTableCode,
                        identityLines.firstNonBlankOf(PosOrderLine::turnTableCode),
                        root.tableCode,
                        identityLines.firstNonBlankOf(PosOrderLine::tableCode),
                    )
                } else {
                    null
                }
            val previousBaseHash =
                if (operation == "304" && previousOrderAlias != null) {
                    HashUtil.potTaskBaseHash(
                        ikmsOrder = previousOrderAlias,
                        rootPosFoodCode = rootFoodCode,
                        orderedChildFoodCodes = orderedChildFoodCodes,
                    )
                } else {
                    null
                }
            // The POS root id is the entity identity for a 301 tree. baseHash remains
            // a correlation key for 302/303/304 and may legitimately be shared by
            // multiple identical pots under the same bill.
            val localTaskId = root.id
            PosOrderTask(
                operation = operation,
                baseHash = baseHash,
                previousOrderAlias = previousOrderAlias,
                currentOrderAliases = currentOrderAliases,
                targetTableCode = targetTableCode,
                previousBaseHash = previousBaseHash,
                order =
                    root.toOrder(
                        localTaskId = localTaskId,
                        baseHash = baseHash,
                        ikmsOrder = ikmsOrder,
                        orderAliases = currentOrderAliases,
                        rootFoodCode = rootFoodCode,
                        bottomLines = bottomLines,
                        structure = bottomResolution,
                        childrenByParent = childrenByParent,
                        originalIndex = originalIndex,
                    ),
            )
        }
    }

    private fun collectDescendants(
        root: PosOrderLine,
        childrenByParent: Map<String?, List<PosOrderLine>>,
        originalIndex: Map<String, Int>,
    ): List<PosOrderLine> {
        val result = mutableListOf<PosOrderLine>()

        fun visit(parent: PosOrderLine) {
            childrenByParent[parent.id]
                .orEmpty()
                .sortedBy { originalIndex[it.id] ?: Int.MAX_VALUE }
                .forEach { child ->
                    result += child
                    visit(child)
                }
        }
        visit(root)
        return result
    }

    private fun collectRootWindowBottomCandidates(
        root: PosOrderLine,
        lines: List<PosOrderLine>,
        roots: List<PosOrderLine>,
        originalIndex: Map<String, Int>,
    ): List<PosOrderLine> {
        val rootIndex = originalIndex[root.id] ?: return emptyList()
        val nextRootIndex =
            roots
                .asSequence()
                .filter { candidateRoot ->
                    candidateRoot.id != root.id &&
                        candidateRoot.sharesOrderIdentity(root) &&
                        (originalIndex[candidateRoot.id] ?: Int.MAX_VALUE) > rootIndex
                }.map { originalIndex[it.id] ?: Int.MAX_VALUE }
                .minOrNull() ?: Int.MAX_VALUE

        return lines
            .filter { line ->
                val index = originalIndex[line.id] ?: Int.MAX_VALUE
                line.id != root.id &&
                    index > rootIndex &&
                    index < nextRootIndex &&
                    line.isPotBottomCandidate() &&
                    (line.normalizedParentId() == root.id || line.sharesOrderIdentity(root))
            }.sortedBy { originalIndex[it.id] ?: Int.MAX_VALUE }
    }

    private fun buildTaskLines(
        bottomLines: List<PosOrderLine>,
        childrenByParent: Map<String?, List<PosOrderLine>>,
        originalIndex: Map<String, Int>,
    ): List<PosOrderLine> =
        bottomLines
            .flatMap { bottom ->
                listOf(bottom) +
                    collectDescendants(
                        root = bottom,
                        childrenByParent = childrenByParent,
                        originalIndex = originalIndex,
                    )
            }.distinctBy { it.id }
            .sortedBy { originalIndex[it.id] ?: Int.MAX_VALUE }

    private fun PosOrderLine.normalizedParentId(): String? {
        val normalized = parentId?.trim().orEmpty()
        return normalized.takeIf { value ->
            value.isNotBlank() &&
                !value.equals("null", ignoreCase = true) &&
                !value.equals("undefined", ignoreCase = true) &&
                value != "0" &&
                value != "-1"
        }
    }

    private fun PosOrderLine.isPotTypeCandidate(): Boolean =
        !potTypeCode.isNullOrBlank() ||
            (!posFoodCode.isNullOrBlank() && potBottomCode.isNullOrBlank() && tasteCode.isNullOrBlank())

    private fun PosOrderLine.isPotBottomCandidate(): Boolean =
        !potBottomCode.isNullOrBlank() ||
            (!posFoodCode.isNullOrBlank() && potTypeCode.isNullOrBlank() && tasteCode.isNullOrBlank())

    private fun PosOrderLine.sharesOrderIdentity(other: PosOrderLine): Boolean {
        val left = orderIdentityAliases()
        val right = other.orderIdentityAliases()
        return left.isNotEmpty() && right.isNotEmpty() && left.intersect(right).isNotEmpty()
    }

    private fun PosOrderLine.orderIdentityAliases(): Set<String> = normalizeAliases(ikmsOrder, posOrder, mainPosOrder).toSet()

    private fun PosOrderLine.toOrder(
        localTaskId: String,
        baseHash: String,
        ikmsOrder: String,
        orderAliases: List<String>,
        rootFoodCode: String,
        bottomLines: List<PosOrderLine>,
        structure: BottomResolution,
        childrenByParent: Map<String?, List<PosOrderLine>>,
        originalIndex: Map<String, Int>,
    ): Order {
        val mode = structure.mode
        val slots =
            bottomLines
                .sortedBy { originalIndex[it.id] ?: Int.MAX_VALUE }
                .mapIndexed { index, line ->
                    (index + 1) to line
                }
        val slotSummary =
            slots.joinToString("|") { (slotNo, line) ->
                "${slotLabel(mode, slotNo)}:${line.standardName ?: line.potBottomCode ?: line.posFoodCode.orEmpty()}"
            }
        val slotCodeSummary =
            slots.joinToString("|") { (slotNo, line) ->
                "${slotLabel(mode, slotNo)}:${line.potBottomCode ?: line.posFoodCode.orEmpty()}"
            }
        val tasteSummary =
            slots
                .mapNotNull { (slotNo, line) ->
                    val tastes =
                        childrenByParent[line.id]
                            .orEmpty()
                            .sortedBy { originalIndex[it.id] ?: Int.MAX_VALUE }
                    val tasteNames = tastes.mapNotNull { it.standardName.cleanOrderText() }
                    val remarks =
                        listOfNotNull(
                            line.description.cleanOrderText(),
                            tastes
                                .mapNotNull { it.description.cleanOrderText() }
                                .joinToString(" ")
                                .ifBlank { null },
                        )
                    val tasteText =
                        buildString {
                            append(tasteNames.joinToString("/"))
                            if (remarks.isNotEmpty()) {
                                append("（")
                                append(remarks.joinToString(" / "))
                                append("）")
                            }
                        }.trim()
                    if (tasteText.isBlank()) {
                        null
                    } else {
                        val slotLabel = slotLabel(mode, slotNo)
                        val bottomName = line.standardName ?: line.potBottomCode ?: line.posFoodCode.orEmpty()
                        "$slotLabel:$bottomName:$tasteText"
                    }
                }.joinToString("|")
                .ifBlank { null }

        val potBottomSummary =
            slots.joinToString(",") { (_, line) ->
                line.standardName ?: line.potBottomCode ?: line.posFoodCode.orEmpty()
            }
        val operatorValue =
            firstNonBlank(
                operator,
                bottomLines.firstNonBlankOf(PosOrderLine::operator),
            )
        val operationValue =
            firstNonBlank(
                operation,
                bottomLines.firstNonBlankOf(PosOrderLine::operation),
            ) ?: "301"

        return Order(
            id = localTaskId,
            recipeCode = rootFoodCode,
            quantity = foodCount.coerceAtLeast(1),
            targetTemperature = 180,
            cookSeconds = 60,
            spiceLevel = 0,
            tableCode = tableCode ?: bottomLines.firstNonBlankOf(PosOrderLine::tableCode),
            orderTime =
                firstNonBlank(
                    operateTime,
                    createTime,
                    updateTime,
                    testTime,
                    bottomLines.firstNonBlankOf(PosOrderLine::operateTime),
                    bottomLines.firstNonBlankOf(PosOrderLine::createTime),
                    bottomLines.firstNonBlankOf(PosOrderLine::updateTime),
                    bottomLines.firstNonBlankOf(PosOrderLine::testTime),
                ),
            potMode = mode,
            potBottomName = slots.firstOrNull()?.second?.standardName,
            tasteSummary = tasteSummary,
            slotSummary = slotSummary,
            status = OrderStatus.PendingWater,
            baseHash = baseHash,
            ikmsOrder = ikmsOrder,
            rootPosFoodCode = rootFoodCode,
            slotCodeSummary = slotCodeSummary,
            operator = operatorValue,
            operation = operationValue,
            potBottomSummary = potBottomSummary,
            orderAliases = orderAliases,
            sourceRootId = id,
            structureStatus = structure.status,
            structureMessage = structure.message,
            expectedSlotCount = structure.expectedSlotCount,
            attachedBottomCount = structure.attachedBottomCount,
            rawBottomCandidateCount = structure.rawBottomCandidateCount,
            missingSlotLabels = structure.missingSlotLabels,
        )
    }

    private fun resolveMode(
        root: PosOrderLine,
        attachedBottomLines: List<PosOrderLine>,
        rawBottomCandidates: List<PosOrderLine>,
    ): PotMode {
        val name = root.standardName.orEmpty()
        val candidateCount = maxOf(attachedBottomLines.size, rawBottomCandidates.distinctBy { it.id }.size)
        return when {
            name.contains("四") || candidateCount >= 4 -> PotMode.FourGrid
            name.contains("三") || candidateCount == 3 -> PotMode.ThreeGrid
            name.contains("拼") || candidateCount == 2 -> PotMode.Split
            else -> PotMode.Single
        }
    }

    private fun resolveBottomStructure(
        mode: PotMode,
        attachedBottomLines: List<PosOrderLine>,
        rawBottomCandidates: List<PosOrderLine>,
        originalIndex: Map<String, Int>,
    ): BottomResolution {
        val expected = mode.expectedSlotCount()
        val attached =
            attachedBottomLines
                .distinctBy { it.id }
                .sortedBy { originalIndex[it.id] ?: Int.MAX_VALUE }
        val rawCandidates =
            (attached + rawBottomCandidates)
                .distinctBy { it.id }
                .sortedBy { originalIndex[it.id] ?: Int.MAX_VALUE }
        val status =
            when {
                attached.size > expected -> OrderStructureStatus.Overflow
                attached.size == expected -> OrderStructureStatus.Complete
                rawCandidates.size == expected -> OrderStructureStatus.ParseRecovered
                rawCandidates.size < expected -> OrderStructureStatus.IncompleteFromApi
                else -> OrderStructureStatus.ParseLostSuspected
            }
        val resolvedBottomLines =
            when (status) {
                OrderStructureStatus.ParseRecovered -> rawCandidates
                else -> attached
            }
        val missingSlotLabels =
            if (resolvedBottomLines.size < expected) {
                ((resolvedBottomLines.size + 1)..expected).map { slotLabel(mode, it) }
            } else {
                emptyList()
            }
        return BottomResolution(
            mode = mode,
            bottomLines = resolvedBottomLines,
            status = status,
            expectedSlotCount = expected,
            attachedBottomCount = attached.size,
            rawBottomCandidateCount = rawCandidates.size,
            missingSlotLabels = missingSlotLabels,
            message =
                structureMessage(
                    status = status,
                    expected = expected,
                    attached = attached.size,
                    raw = rawCandidates.size,
                    missingSlotLabels = missingSlotLabels,
                ),
        )
    }

    private fun PotMode.expectedSlotCount(): Int =
        when (this) {
            PotMode.Single -> 1
            PotMode.Split -> 2
            PotMode.ThreeGrid -> 3
            PotMode.FourGrid -> 4
        }

    private fun structureMessage(
        status: OrderStructureStatus,
        expected: Int,
        attached: Int,
        raw: Int,
        missingSlotLabels: List<String>,
    ): String? {
        val missingText = missingSlotLabels.joinToString("、")
        return when (status) {
            OrderStructureStatus.Complete -> null
            OrderStructureStatus.ParseRecovered -> "订单解析缺失已自动重解析恢复：期望 $expected 个，已恢复 $raw 个"
            OrderStructureStatus.IncompleteFromApi -> "接口锅底缺失：期望 $expected 个，接口疑似返回 $raw 个，缺少 $missingText"
            OrderStructureStatus.ParseLostSuspected -> "订单解析异常：期望 $expected 个，已挂载 $attached 个，候选 $raw 个，未自动重解析"
            OrderStructureStatus.Overflow -> "锅底数量异常：期望 $expected 个，已挂载 $attached 个"
        }
    }

    private fun slotLabel(
        mode: PotMode,
        slotNo: Int,
    ): String =
        when (mode) {
            PotMode.Single -> "整锅"
            PotMode.Split -> if (slotNo == 2) "右侧" else "左侧"
            PotMode.ThreeGrid ->
                when (slotNo) {
                    2 -> "右上"
                    3 -> "下方"
                    else -> "左上"
                }
            PotMode.FourGrid ->
                when (slotNo) {
                    2 -> "右上"
                    3 -> "左下"
                    4 -> "右下"
                    else -> "左上"
                }
        }

    @Suppress("MaxLineLength")
    private fun <T> Iterable<T>.firstNonBlankOf(selector: (T) -> String?): String? = firstNotNullOfOrNull { item -> selector(item)?.trim()?.takeIf(String::isNotBlank) }

    private fun normalizeAliases(vararg values: String?): List<String> =
        values
            .mapNotNull { it.cleanOrderText() }
            .distinct()

    private fun String?.cleanOrderText(): String? {
        val value = this?.trim().orEmpty()
        return value.takeIf {
            it.isNotBlank() &&
                !it.equals("null", ignoreCase = true) &&
                !it.equals("undefined", ignoreCase = true)
        }
    }

    private fun firstNonBlank(vararg values: String?): String? =
        values.firstNotNullOfOrNull { value ->
            value?.trim()?.takeIf(String::isNotBlank)
        }

    private data class BottomResolution(
        val mode: PotMode,
        val bottomLines: List<PosOrderLine>,
        val status: OrderStructureStatus,
        val expectedSlotCount: Int,
        val attachedBottomCount: Int,
        val rawBottomCandidateCount: Int,
        val missingSlotLabels: List<String>,
        val message: String?,
    )
}
