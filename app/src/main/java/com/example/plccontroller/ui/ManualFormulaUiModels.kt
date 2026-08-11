package com.example.plccontroller.ui

import com.example.plccontroller.domain.FormulaCatalog
import com.example.plccontroller.domain.FormulaPotType
import com.example.plccontroller.domain.localDefaultFormulaCatalog
import java.util.Locale

internal enum class ManualPotMode {
    Small,
    Single,
    Split,
    ThreeGrid,
    FourGrid,
}

internal data class ManualRecipeOption(
    val code: String,
    val name: String,
    val potType: FormulaPotType?,
) {
    fun timingText(): String =
        potType?.let {
            "加水 ${it.addWaterSeconds.formatSeconds()} 秒"
        } ?: "本地默认"
}

internal data class FormulaSettingOption(
    val code: String,
    val name: String,
    val typeDetails: List<FormulaPotType>,
    val sourceLabel: String,
)

internal enum class PotDetailLayoutMode {
    Small,
    Single,
    Split,
    ThreeGrid,
    FourGrid,
}

@Suppress("unused")
private val ManualPotBottoms =
    listOf(
        "清汤锅",
        "麻辣锅",
        "番茄锅",
        "菌汤锅",
        "冬阴功锅",
        "三鲜锅",
    )

internal fun FormulaCatalog?.availableManualModes(): List<ManualPotMode> {
    val modes =
        this
            ?.pots
            ?.flatMap { pot -> pot.potTypes.mapNotNull { it.potTypeName.toManualPotMode() } }
            ?.distinct()
            .orEmpty()
    return modes.ifEmpty { ManualPotMode.entries }
}

internal fun FormulaCatalog?.recipeOptionsFor(mode: ManualPotMode): List<ManualRecipeOption> {
    val options =
        this
            ?.pots
            ?.mapNotNull { pot ->
                val potType =
                    pot.potTypes.firstOrNull { it.potTypeName.toManualPotMode() == mode }
                        ?: return@mapNotNull null
                ManualRecipeOption(
                    code = "${pot.potCode}:${potType.potTypeCode}",
                    name = pot.potName.ifBlank { pot.potCode },
                    potType = potType,
                )
            }.orEmpty()

    return options.ifEmpty {
        localDefaultFormulaCatalog().recipeOptionsFor(mode)
    }
}

internal fun FormulaCatalog?.settingOptions(): List<FormulaSettingOption> {
    val options =
        this
            ?.pots
            ?.map { pot ->
                FormulaSettingOption(
                    code = pot.potCode,
                    name = pot.potName.ifBlank { pot.potCode },
                    typeDetails = pot.potTypes,
                    sourceLabel = this.formulaName.ifBlank { this.formulaCode },
                )
            }.orEmpty()

    return options.ifEmpty {
        localDefaultFormulaCatalog().pots.map { pot ->
            FormulaSettingOption(
                code = pot.potCode,
                name = pot.potName.ifBlank { pot.potCode },
                typeDetails = pot.potTypes,
                sourceLabel = "本地默认",
            )
        }
    }
}

internal fun FormulaPotType.toManualPotModeOrNull(): ManualPotMode? = potTypeName.toManualPotMode()

private fun String.toManualPotMode(): ManualPotMode? =
    when (trim()) {
        "小锅", "small", "Small", "SMALL" -> ManualPotMode.Small
        "单锅", "single", "Single", "SINGLE" -> ManualPotMode.Single
        "拼锅", "split", "Split", "SPLIT" -> ManualPotMode.Split
        "三拼锅", "三宫格", "threeGrid", "ThreeGrid", "THREE_GRID" -> ManualPotMode.ThreeGrid
        "四宫格", "fourGrid", "FourGrid", "FOUR_GRID" -> ManualPotMode.FourGrid
        else -> null
    }

internal fun ManualPotMode.label(): String =
    when (this) {
        ManualPotMode.Small -> "小锅"
        ManualPotMode.Single -> "单锅"
        ManualPotMode.Split -> "拼锅"
        ManualPotMode.ThreeGrid -> "三拼锅"
        ManualPotMode.FourGrid -> "四宫格"
    }

internal fun ManualPotMode.slotCount(): Int =
    when (this) {
        ManualPotMode.Small,
        ManualPotMode.Single,
        -> 1
        ManualPotMode.Split -> 2
        ManualPotMode.ThreeGrid -> 3
        ManualPotMode.FourGrid -> 4
    }

internal fun ManualPotMode.outletSummary(): String =
    when (this) {
        ManualPotMode.Small -> "左上=出水口1"
        ManualPotMode.Single -> "整锅=出水口1/2/3/4"
        ManualPotMode.Split -> "左侧=1/3，右侧=2/4"
        ManualPotMode.ThreeGrid -> "左上=1，右上=2，下方=3"
        ManualPotMode.FourGrid -> "左上=1，右上=2，左下=3，右下=4"
    }

internal fun ManualPotMode.slotLabels(): List<String> =
    when (this) {
        ManualPotMode.Small -> listOf("小锅锅底")
        ManualPotMode.Single -> listOf("整锅锅底")
        ManualPotMode.Split -> listOf("左侧锅底", "右侧锅底")
        ManualPotMode.ThreeGrid -> listOf("左上锅底", "右上锅底", "下方锅底")
        ManualPotMode.FourGrid -> listOf("左上锅底", "右上锅底", "左下锅底", "右下锅底")
    }

internal fun ManualPotMode.slotOutlets(): List<String> =
    when (this) {
        ManualPotMode.Small -> listOf("对应出水口：1")
        ManualPotMode.Single -> listOf("对应出水口：1 / 2 / 3 / 4")
        ManualPotMode.Split -> listOf("对应出水口：1 / 3", "对应出水口：2 / 4")
        ManualPotMode.ThreeGrid -> listOf("对应出水口：1", "对应出水口：2", "对应出水口：3")
        ManualPotMode.FourGrid -> listOf("对应出水口：1", "对应出水口：2", "对应出水口：3", "对应出水口：4")
    }

internal fun ManualPotMode.toDetailLayoutMode(): PotDetailLayoutMode =
    when (this) {
        ManualPotMode.Small -> PotDetailLayoutMode.Small
        ManualPotMode.Single -> PotDetailLayoutMode.Single
        ManualPotMode.Split -> PotDetailLayoutMode.Split
        ManualPotMode.ThreeGrid -> PotDetailLayoutMode.ThreeGrid
        ManualPotMode.FourGrid -> PotDetailLayoutMode.FourGrid
    }

internal fun Double.formatSeconds(): String =
    if (this % 1.0 == 0.0) {
        toInt().toString()
    } else {
        String.format(Locale.CHINA, "%.2f", this)
    }
