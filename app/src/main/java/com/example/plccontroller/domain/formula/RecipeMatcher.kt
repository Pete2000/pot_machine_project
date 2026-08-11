package com.example.plccontroller.domain.formula

import com.example.plccontroller.domain.FormulaCatalog
import com.example.plccontroller.domain.FormulaPotType

enum class RecipePotMode {
    Small,
    Single,
    Split,
    ThreeGrid,
    FourGrid,
}

data class RecipeMatchResult(
    val potType: FormulaPotType?,
    val matchedBy: RecipeMatchSource,
) {
    val matched: Boolean
        get() = potType != null
}

enum class RecipeMatchSource {
    Code,
    Name,
    None,
}

object RecipeMatcher {
    fun matchPotType(
        catalog: FormulaCatalog?,
        potCode: String?,
        potName: String,
        potTypeCode: String?,
        mode: RecipePotMode,
    ): RecipeMatchResult {
        val sourceCatalog = catalog ?: return RecipeMatchResult(null, RecipeMatchSource.None)
        val normalizedPotCode = potCode?.trim().orEmpty()
        val normalizedPotTypeCode = potTypeCode?.trim().orEmpty()

        val matchedType =
            if (normalizedPotCode.isNotBlank()) {
                if (normalizedPotTypeCode.isNotBlank()) {
                    sourceCatalog.pots
                        .firstOrNull { pot -> pot.potCode == normalizedPotCode }
                        ?.potTypes
                        ?.firstOrNull { type -> type.potTypeCode == normalizedPotTypeCode }
                } else {
                    null
                } ?: sourceCatalog.pots
                    .firstOrNull { pot -> pot.potCode.equals(normalizedPotCode, ignoreCase = true) }
                    ?.bestTypeFor(mode)
            } else {
                null
            }

        if (matchedType != null) {
            return RecipeMatchResult(matchedType, RecipeMatchSource.Code)
        }

        val normalizedName = potName.trim()
        if (normalizedName.isBlank()) {
            return RecipeMatchResult(null, RecipeMatchSource.None)
        }

        val pot =
            sourceCatalog.pots.firstOrNull { formulaPot ->
                val formulaPotCode = formulaPot.potCode.trim()
                val formulaPotName = formulaPot.potName.trim()
                formulaPotCode.equals(normalizedName, ignoreCase = true) ||
                    formulaPotName.equals(normalizedName, ignoreCase = true) ||
                    (formulaPotName.isNotBlank() && normalizedName.contains(formulaPotName, ignoreCase = true)) ||
                    (formulaPotName.isNotBlank() && formulaPotName.contains(normalizedName, ignoreCase = true))
            }

        return if (pot != null) {
            RecipeMatchResult(pot.bestTypeFor(mode), RecipeMatchSource.Name)
        } else {
            RecipeMatchResult(null, RecipeMatchSource.None)
        }
    }

    @Suppress("MaxLineLength")
    fun hasAdditiveDuration(potType: FormulaPotType?): Boolean = potType != null && (potType.addChickenOilSeconds > 0.0 || potType.addBonePasteSeconds > 0.0)

    private fun com.example.plccontroller.domain.FormulaPot.bestTypeFor(mode: RecipePotMode): FormulaPotType? =
        potTypes.firstOrNull {
            it.matchesMode(mode)
        } ?: potTypes.firstOrNull()

    private fun FormulaPotType.matchesMode(mode: RecipePotMode): Boolean =
        when (mode) {
            RecipePotMode.Small -> potTypeName.contains("小锅", ignoreCase = true)
            RecipePotMode.Single -> potTypeName.contains("单锅", ignoreCase = true)
            RecipePotMode.Split -> potTypeName.contains("拼锅", ignoreCase = true)
            RecipePotMode.ThreeGrid ->
                potTypeName.contains("三拼", ignoreCase = true) ||
                    potTypeName.contains("三宫格", ignoreCase = true)
            RecipePotMode.FourGrid -> potTypeName.contains("四宫格", ignoreCase = true)
        }
}
