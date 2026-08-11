package com.example.plccontroller.ui

import com.example.plccontroller.domain.Order
import com.example.plccontroller.domain.PotMode
import com.example.plccontroller.domain.hasBlockingStructureIssue
import com.example.plccontroller.domain.structureIssueTitle

internal data class PotSection(
    val slotLabel: String,
    val baseName: String,
    val tasteName: String? = null,
)

private data class PotSectionValue(
    val baseName: String,
    val tasteName: String? = null,
)

internal fun buildPotSections(order: Order): List<PotSection> {
    val labels = slotLabels(order.potMode)
    val values = extractSectionValues(order, labels.size)

    val tasteMap = mutableMapOf<String, String>()
    order.tasteSummary?.split("|")?.forEach { part ->
        val colonIndex1 = part.indexOf(":")
        if (colonIndex1 != -1) {
            val label = part.substring(0, colonIndex1).trim()
            val remaining = part.substring(colonIndex1 + 1)
            val colonIndex2 = remaining.indexOf(":")
            if (colonIndex2 != -1) {
                val tasteText = remaining.substring(colonIndex2 + 1).trim()
                if (tasteText.isNotBlank()) {
                    tasteMap[label] = tasteText
                }
            }
        }
    }

    return labels.mapIndexed { index, label ->
        val value = values.getOrElse(index) { PotSectionValue(baseName = "待同步") }
        val taste = tasteMap[label] ?: value.tasteName
        PotSection(
            slotLabel = label,
            baseName = value.baseName,
            tasteName = taste,
        )
    }
}

internal fun cardTitle(order: Order): String {
    val sections = buildPotSections(order).map(PotSection::baseName).distinct()
    return when {
        order.potMode == PotMode.Single -> sections.firstOrNull() ?: order.recipeCode
        sections.size == 1 -> sections.first()
        else -> "分区锅底"
    }
}

internal fun queueTitle(order: Order): String =
    if (order.potMode == PotMode.Single) {
        cardTitle(order)
    } else {
        "${order.potMode.label()} · ${cardTitle(order)}"
    }

private fun extractSectionValues(
    order: Order,
    expectedCount: Int,
): List<PotSectionValue> {
    val missingValue =
        PotSectionValue(
            baseName = order.structureIssueTitle() ?: "待同步",
        )
    val fallbackSingle =
        PotSectionValue(
            baseName = order.potBottomName?.takeIf(String::isNotBlank) ?: order.recipeCode,
            tasteName = order.tasteSummary?.takeIf(String::isNotBlank),
        )

    if (order.potMode == PotMode.Single) {
        val raw = order.slotSummary?.trim().orEmpty()
        return if (raw.isNotBlank()) {
            listOf(parseSectionValue(raw, order.tasteSummary, allowFallbackTaste = true))
        } else {
            listOf(fallbackSingle)
        }
    }

    val rawSummary = order.slotSummary?.trim().orEmpty()
    val buildFallbackList = {
        List(expectedCount) { index ->
            if (order.hasBlockingStructureIssue()) {
                missingValue
            } else if (index == 0) {
                fallbackSingle.copy(tasteName = null)
            } else {
                PotSectionValue("待同步")
            }
        }
    }

    if (rawSummary.isBlank()) {
        return buildFallbackList()
    }

    val directional = extractDirectionalSections(order.potMode, rawSummary)
    if (directional != null) {
        return directional.map { parseSectionValue(it) }
    }

    val genericParts =
        rawSummary
            .split(Regex("[\\n|；;]+"))
            .map { part -> part.substringAfter("：", part).trim() }
            .filter { it.isNotBlank() }

    return if (genericParts.isEmpty()) {
        buildFallbackList()
    } else {
        List(expectedCount) { index ->
            genericParts.getOrNull(index)?.let { parseSectionValue(it) } ?: run {
                if (order.hasBlockingStructureIssue()) {
                    missingValue
                } else if (index == 0) {
                    fallbackSingle.copy(tasteName = null)
                } else {
                    PotSectionValue("待同步")
                }
            }
        }
    }
}

private fun parseSectionValue(
    rawValue: String,
    fallbackTaste: String? = null,
    allowFallbackTaste: Boolean = false,
): PotSectionValue {
    val cleanValue = rawValue.trim()
    if (cleanValue.isBlank()) {
        return PotSectionValue(
            baseName = "待同步",
            tasteName = fallbackTaste?.takeIf { allowFallbackTaste && it.isNotBlank() },
        )
    }

    val match = Regex("""^(.*?)[（(]([^（）()]+)[）)]$""").find(cleanValue)
    if (match != null) {
        return PotSectionValue(
            baseName = match.groupValues[1].trim().ifBlank { "待同步" },
            tasteName = match.groupValues[2].trim().ifBlank { null },
        )
    }

    return PotSectionValue(
        baseName = cleanValue,
        tasteName = fallbackTaste?.takeIf { allowFallbackTaste && it.isNotBlank() },
    )
}

private fun extractDirectionalSections(
    potMode: PotMode,
    rawSummary: String,
): List<String>? {
    val aliases =
        when (potMode) {
            PotMode.Single -> listOf(listOf("整锅", "单锅"))
            PotMode.Split -> listOf(listOf("左锅", "左"), listOf("右锅", "右"))
            PotMode.ThreeGrid -> listOf(listOf("左上"), listOf("右上"), listOf("下锅", "下"))
            PotMode.FourGrid ->
                listOf(
                    listOf("左上"),
                    listOf("右上"),
                    listOf("左下"),
                    listOf("右下"),
                )
        }

    val results = mutableListOf<String>()
    for (slotAliases in aliases) {
        val aliasPattern = slotAliases.joinToString("|") { Regex.escape(it) }
        val regex = Regex("""(?:^|[|\n；;])\s*(?:$aliasPattern)\s*[:：]\s*([^|\n；;]+)""")
        val match = regex.find(rawSummary) ?: return null
        results += match.groupValues[1].trim()
    }
    return results
}

private fun slotLabels(potMode: PotMode): List<String> =
    when (potMode) {
        PotMode.Single -> listOf("整锅")
        PotMode.Split -> listOf("左锅", "右锅")
        PotMode.ThreeGrid -> listOf("左上", "右上", "下锅")
        PotMode.FourGrid -> listOf("左上", "右上", "左下", "右下")
    }
