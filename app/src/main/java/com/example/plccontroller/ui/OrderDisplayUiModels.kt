package com.example.plccontroller.ui

import androidx.compose.ui.Alignment
import com.example.plccontroller.domain.Order
import com.example.plccontroller.domain.PotMode

internal data class PotDetailCardModel(
    val title: String,
    val subtitle: String,
    val content: String,
    val highlight: String? = null,
    val footer: String? = null,
    val selected: Boolean = false,
    val labelAlignment: Alignment = Alignment.TopStart,
    val onClick: (() -> Unit)? = null,
)

internal fun PotMode.toDetailLayoutMode(): PotDetailLayoutMode =
    when (this) {
        PotMode.Single -> PotDetailLayoutMode.Single
        PotMode.Split -> PotDetailLayoutMode.Split
        PotMode.ThreeGrid -> PotDetailLayoutMode.ThreeGrid
        PotMode.FourGrid -> PotDetailLayoutMode.FourGrid
    }

internal fun PotMode.label(): String =
    when (this) {
        PotMode.Single -> "单锅"
        PotMode.Split -> "拼锅"
        PotMode.ThreeGrid -> "三拼锅"
        PotMode.FourGrid -> "四宫格"
    }

internal fun Order.tasteText(): String = tasteSummary ?: "辣度 $spiceLevel"

internal fun Order.listTimeText(): String {
    val raw = (statusUpdatedAt ?: orderTime)?.trim().orEmpty()
    if (raw.isBlank()) {
        return "--:--"
    }
    return when {
        raw.contains("T") -> raw.substringAfterLast("T").take(8)
        raw.contains(" ") -> raw.substringAfterLast(" ").take(8)
        else -> raw.take(8)
    }
}

internal fun Order.fullOrderTimeText(): String = orderTime.toFullDateTimeText()

internal fun String?.toFullDateTimeText(): String {
    val raw = this?.trim().orEmpty()
    if (raw.isBlank()) {
        return "--"
    }
    val normalized =
        raw
            .replace('T', ' ')
            .substringBefore(".")
            .substringBefore("+")
            .trim()
    return when {
        normalized.length >= 19 -> normalized.take(19)
        else -> normalized
    }
}
