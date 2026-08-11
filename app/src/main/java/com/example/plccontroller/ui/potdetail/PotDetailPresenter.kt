package com.example.plccontroller.ui.potdetail

import androidx.compose.ui.Alignment
import com.example.plccontroller.domain.FormulaCatalog
import com.example.plccontroller.domain.FormulaPotType
import com.example.plccontroller.domain.Order
import com.example.plccontroller.domain.PotMode
import com.example.plccontroller.domain.formula.RecipeMatcher
import com.example.plccontroller.domain.formula.RecipePotMode
import com.example.plccontroller.domain.hasBlockingStructureIssue
import com.example.plccontroller.domain.phase.PotLayout
import com.example.plccontroller.domain.phase.PotLayoutKind
import com.example.plccontroller.domain.phase.RotationPlanner
import com.example.plccontroller.domain.structureIssueSummary
import com.example.plccontroller.domain.structureIssueTitle
import com.example.plccontroller.ui.ManualPotMode
import com.example.plccontroller.ui.ManualRecipeOption
import com.example.plccontroller.ui.PotDetailCardModel
import com.example.plccontroller.ui.PotDetailLayoutMode
import com.example.plccontroller.ui.slotCount
import com.example.plccontroller.ui.slotLabels
import com.example.plccontroller.ui.toDetailLayoutMode

internal data class PresentedPotDetailGrid(
    val layoutMode: PotDetailLayoutMode,
    val cards: List<PotDetailCardModel>,
)

internal object PotDetailPresenter {
    fun presentManual(
        mode: ManualPotMode,
        selectedRecipes: List<ManualRecipeOption?>,
        activePhysicalIndex: Int,
        displayTopLeftLogicalSlot: Int?,
        onSelectPhysicalIndex: (Int) -> Unit,
    ): PresentedPotDetailGrid {
        val slots =
            (1..mode.slotCount()).map { logicalSlot ->
                val recipe = selectedRecipes.getOrNull(logicalSlot - 1)
                val potType = recipe?.potType
                PotDetailDisplaySlot(
                    logicalSlot = logicalSlot,
                    title = recipe?.name ?: "未选择锅底",
                    basicMaterials = potType.displayBasicMaterials(recipe?.code),
                    materials = potType.displayFreshMaterials(recipe?.code),
                )
            }
        return presentSlots(
            mode = mode,
            displayTopLeftLogicalSlot = displayTopLeftLogicalSlot,
            slots = slots,
            selectedPhysicalIndex = activePhysicalIndex,
            onSelectPhysicalIndex = onSelectPhysicalIndex,
        )
    }

    fun presentOrder(
        order: Order?,
        formulaCatalog: FormulaCatalog?,
        forcedTopLeftLogicalSlot: Int?,
        currentTopLeftLogicalSlot: Int?,
    ): PresentedPotDetailGrid {
        val mode = order?.potMode?.toManualPotMode() ?: ManualPotMode.Single
        val slotModels =
            order
                ?.let {
                    buildOrderDisplaySlots(
                        order = it,
                        formulaCatalog = formulaCatalog,
                        mode = mode,
                    )
                }.orEmpty()
        val currentTopLeft =
            currentTopLeftLogicalSlot
                ?.coerceIn(1, mode.slotCount())
                ?: 1
        val displayTopLeftLogicalSlot =
            forcedTopLeftLogicalSlot
                ?.coerceIn(1, mode.slotCount())
                ?: preferredAdditiveTopLeftSlot(slotModels, currentTopLeft)
                ?: currentTopLeft

        return presentSlots(
            mode = mode,
            displayTopLeftLogicalSlot = displayTopLeftLogicalSlot,
            slots = slotModels,
            selectedPhysicalIndex = null,
            onSelectPhysicalIndex = null,
        )
    }

    private fun presentSlots(
        mode: ManualPotMode,
        displayTopLeftLogicalSlot: Int?,
        slots: List<PotDetailDisplaySlot>,
        selectedPhysicalIndex: Int?,
        onSelectPhysicalIndex: ((Int) -> Unit)?,
    ): PresentedPotDetailGrid {
        val physicalSlots = mode.physicalDetailSlots()
        val rotatedLogicalSlots = mode.rotatedLogicalSlots(displayTopLeftLogicalSlot)
        val cards =
            physicalSlots.mapIndexed { physicalIndex, physicalSlot ->
                val logicalSlot = rotatedLogicalSlots.getOrNull(physicalIndex) ?: (physicalIndex + 1)
                val slot = slots.firstOrNull { it.logicalSlot == logicalSlot }
                PotDetailCardModel(
                    title = slot?.title ?: "未选择锅底",
                    subtitle = physicalSlot.title,
                    content = slot?.contentText ?: "请选择该锅位锅底",
                    highlight = slot?.tasteText,
                    selected = selectedPhysicalIndex == physicalIndex,
                    labelAlignment = mode.toDetailLayoutMode().labelAlignmentForPhysicalIndex(physicalIndex),
                    onClick =
                        onSelectPhysicalIndex?.let { handler ->
                            { handler(physicalIndex) }
                        },
                )
            }
        return PresentedPotDetailGrid(
            layoutMode = mode.toDetailLayoutMode(),
            cards = cards,
        )
    }

    private fun buildOrderDisplaySlots(
        order: Order,
        formulaCatalog: FormulaCatalog?,
        mode: ManualPotMode,
    ): List<PotDetailDisplaySlot> {
        val parsedSlotNames = parseOrderSlotNames(order)
        val parsedSlotCodes = parseOrderSlotCodes(order)
        val parsedSlotTastes = parseOrderSlotTastes(order)
        val logicalLabels = mode.slotLabels()
        val hasStructureIssue = order.hasBlockingStructureIssue()
        return (1..mode.slotCount()).map { logicalSlot ->
            val parsedName = parsedSlotNames[logicalSlot]
            if (hasStructureIssue && parsedName == null) {
                return@map PotDetailDisplaySlot(
                    logicalSlot = logicalSlot,
                    title = order.structureIssueTitle() ?: "订单结构异常",
                    tasteText = order.structureIssueSummary(),
                    basicMaterials = "暂不执行",
                    materials = "等待接口补齐或重新解析",
                )
            }
            val fallbackName =
                if (logicalSlot == 1) {
                    order.potBottomName ?: order.recipeCode
                } else {
                    logicalLabels.getOrNull(logicalSlot - 1) ?: "${logicalSlot}号锅底"
                }
            val name = parsedName ?: fallbackName
            val code = parsedSlotCodes[logicalSlot]
            val potType =
                RecipeMatcher
                    .matchPotType(
                        catalog = formulaCatalog,
                        potCode = code,
                        potName = name,
                        potTypeCode = order.rootPosFoodCode ?: order.recipeCode,
                        mode = mode.toRecipePotMode(),
                    ).potType
            PotDetailDisplaySlot(
                logicalSlot = logicalSlot,
                title = name,
                tasteText = parsedSlotTastes[logicalSlot],
                basicMaterials = potType.displayBasicMaterials(code),
                materials = potType.displayFreshMaterials(code),
                potType = potType,
            )
        }
    }

    private fun preferredAdditiveTopLeftSlot(
        slots: List<PotDetailDisplaySlot>,
        currentTopLeftLogicalSlot: Int,
    ): Int? =
        RotationPlanner.preferredTopLeftSlot(
            currentTopLeftLogicalSlot = currentTopLeftLogicalSlot,
            candidateLogicalSlots =
                slots
                    .filter { RecipeMatcher.hasAdditiveDuration(it.potType) }
                    .map { it.logicalSlot },
        )
}

private data class PotDetailDisplaySlot(
    val logicalSlot: Int,
    val title: String,
    val tasteText: String? = null,
    val basicMaterials: String,
    val materials: String,
    val potType: FormulaPotType? = null,
) {
    val contentText: String
        get() = "底料信息：$basicMaterials\n鲜辅料：$materials"
}

private data class ManualPhysicalSlot(
    val title: String,
)

@Suppress("MaxLineLength")
internal fun ManualPotMode.rotatedLogicalSlots(targetLogicalSlot: Int?): List<Int> = PotLayout.rotatedLogicalSlots(toPotLayoutKind(), targetLogicalSlot)

private fun ManualPotMode.physicalDetailSlots(): List<ManualPhysicalSlot> =
    (0 until slotCount()).map { physicalIndex ->
        ManualPhysicalSlot(
            title =
                PotLayout.physicalLabel(
                    kind = toPotLayoutKind(),
                    physicalIndex = physicalIndex,
                    includeOutlet = false,
                ),
        )
    }

private fun parseOrderSlotNames(order: Order): Map<Int, String> {
    val summary = order.slotSummary?.trim().orEmpty()
    if (summary.isBlank()) return emptyMap()
    return summary
        .split("|", "，", "；", ";", "\n")
        .mapNotNull { section ->
            val normalized = section.trim()
            if (normalized.isBlank()) return@mapNotNull null
            val slot = resolveOrderSlotFromText(normalized, order.potMode) ?: return@mapNotNull null
            val name =
                normalized
                    .substringAfter("：", normalized.substringAfter(":", normalized))
                    .replace(Regex("（.*?）"), "")
                    .replace(Regex("\\(.*?\\)"), "")
                    .trim()
            slot to name.ifBlank { normalized }
        }.toMap()
}

private fun parseOrderSlotCodes(order: Order): Map<Int, String> {
    val summary = order.slotCodeSummary?.trim().orEmpty()
    if (summary.isBlank()) return emptyMap()
    return summary
        .split("|", "，", "；", ";", "\n")
        .mapNotNull { section ->
            val normalized = section.trim()
            if (normalized.isBlank()) return@mapNotNull null
            val slot = resolveOrderSlotFromText(normalized, order.potMode) ?: return@mapNotNull null
            val code =
                normalized
                    .substringAfter("：", normalized.substringAfter(":", normalized))
                    .trim()
            slot to code
        }.toMap()
}

private fun parseOrderSlotTastes(order: Order): Map<Int, String> {
    val summary = order.tasteSummary?.trim().orEmpty()
    if (summary.isBlank()) return emptyMap()
    val parsed =
        summary
            .split("|", "，", "；", ";", "\n")
            .mapNotNull { section ->
                val normalized = section.trim()
                if (normalized.isBlank()) return@mapNotNull null
                val slot = resolveOrderSlotFromText(normalized, order.potMode) ?: return@mapNotNull null
                val rawTaste =
                    normalized
                        .afterSlotSeparators(2)
                        .ifBlank { normalized.afterSlotSeparators(1) }
                val taste = rawTaste.cleanTasteDisplay() ?: return@mapNotNull null
                slot to taste
            }.toMap()
    if (parsed.isNotEmpty()) return parsed

    val fallbackTaste = summary.cleanTasteDisplay() ?: return emptyMap()
    return if (order.potMode == PotMode.Single) {
        mapOf(1 to fallbackTaste)
    } else {
        emptyMap()
    }
}

private fun resolveOrderSlotFromText(
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

private fun String.afterSlotSeparators(count: Int): String {
    var seen = 0
    for (index in indices) {
        val char = this[index]
        if (char == ':' || char == '：') {
            seen += 1
            if (seen == count) {
                return substring(index + 1).trim()
            }
        }
    }
    return ""
}

private fun String.cleanTasteDisplay(): String? {
    val normalized =
        trim()
            .removePrefix("口味")
            .trim()
            .trimStart(':', '：')
            .trim()
    return normalized.takeIf { value ->
        value.isNotBlank() &&
            !value.equals("无", ignoreCase = true) &&
            !value.equals("口味无", ignoreCase = true) &&
            !value.equals("null", ignoreCase = true) &&
            value != "--" &&
            value != "-" &&
            value != "辣度 0"
    }
}

private fun FormulaPotType?.displayBasicMaterials(code: String?): String {
    if (this?.basicMaterials?.isNotBlank() == true) {
        return this.basicMaterials
    }
    val codeSuffix = if (!code.isNullOrBlank()) " (锅底码: $code)" else ""
    return "未匹配配方$codeSuffix"
}

private fun FormulaPotType?.displayFreshMaterials(code: String?): String {
    if (this?.materials?.isNotBlank() == true) {
        return this.materials
    }
    val codeSuffix = if (!code.isNullOrBlank()) " (锅底码: $code)" else ""
    return "未匹配配方$codeSuffix"
}

private fun ManualPotMode.toRecipePotMode(): RecipePotMode =
    when (this) {
        ManualPotMode.Small -> RecipePotMode.Small
        ManualPotMode.Single -> RecipePotMode.Single
        ManualPotMode.Split -> RecipePotMode.Split
        ManualPotMode.ThreeGrid -> RecipePotMode.ThreeGrid
        ManualPotMode.FourGrid -> RecipePotMode.FourGrid
    }

private fun ManualPotMode.toPotLayoutKind(): PotLayoutKind =
    when (this) {
        ManualPotMode.Small -> PotLayoutKind.Small
        ManualPotMode.Single -> PotLayoutKind.Single
        ManualPotMode.Split -> PotLayoutKind.Split
        ManualPotMode.ThreeGrid -> PotLayoutKind.ThreeGrid
        ManualPotMode.FourGrid -> PotLayoutKind.FourGrid
    }

private fun PotMode.toManualPotMode(): ManualPotMode =
    when (this) {
        PotMode.Single -> ManualPotMode.Single
        PotMode.Split -> ManualPotMode.Split
        PotMode.ThreeGrid -> ManualPotMode.ThreeGrid
        PotMode.FourGrid -> ManualPotMode.FourGrid
    }

private fun PotDetailLayoutMode.labelAlignmentForPhysicalIndex(index: Int): Alignment =
    when (this) {
        PotDetailLayoutMode.Small,
        PotDetailLayoutMode.Single,
        -> Alignment.TopStart
        PotDetailLayoutMode.Split -> if (index == 0) Alignment.TopStart else Alignment.TopEnd
        PotDetailLayoutMode.ThreeGrid ->
            when (index) {
                1 -> Alignment.TopEnd
                2 -> Alignment.BottomCenter
                else -> Alignment.TopStart
            }
        PotDetailLayoutMode.FourGrid ->
            when (index) {
                1 -> Alignment.TopEnd
                2 -> Alignment.BottomStart
                3 -> Alignment.BottomEnd
                else -> Alignment.TopStart
            }
    }
