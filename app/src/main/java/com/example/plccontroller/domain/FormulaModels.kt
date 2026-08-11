package com.example.plccontroller.domain

enum class AdditiveType {
    ChickenOil,
    BonePaste,
}

data class FlavorAdditives(
    val chickenOil: Boolean = false,
    val bonePaste: Boolean = false,
) {
    fun requestedTypes(): List<AdditiveType> =
        buildList {
            if (chickenOil) add(AdditiveType.ChickenOil)
            if (bonePaste) add(AdditiveType.BonePaste)
        }
}

data class PotRecipeProfile(
    val potBottomId: String,
    val waterDurationMs: Long = 0L,
    val chickenOilDurationMs: Long = 0L,
    val bonePasteDurationMs: Long = 0L,
) {
    val requiresTopLeftStation: Boolean
        get() = chickenOilDurationMs > 0L || bonePasteDurationMs > 0L

    fun additives(): FlavorAdditives =
        FlavorAdditives(
            chickenOil = chickenOilDurationMs > 0L,
            bonePaste = bonePasteDurationMs > 0L,
        )

    fun durationFor(additiveType: AdditiveType): Long =
        when (additiveType) {
            AdditiveType.ChickenOil -> chickenOilDurationMs
            AdditiveType.BonePaste -> bonePasteDurationMs
        }
}

data class FormulaCatalog(
    val formulaCode: String,
    val formulaName: String,
    val whetherDefault: Boolean,
    val pots: List<FormulaPot>,
)

data class FormulaPot(
    val potCode: String,
    val potName: String,
    val potTypes: List<FormulaPotType>,
)

data class FormulaPotType(
    val potTypeCode: String,
    val potTypeName: String,
    val addWaterSeconds: Double,
    val addChickenOilSeconds: Double,
    val addBonePasteSeconds: Double,
    val materials: String,
    val basicMaterials: String,
) {
    val waterDurationMs: Long
        get() = secondsToMillis(addWaterSeconds)

    val chickenOilDurationMs: Long
        get() = secondsToMillis(addChickenOilSeconds)

    val bonePasteDurationMs: Long
        get() = secondsToMillis(addBonePasteSeconds)

    private fun secondsToMillis(seconds: Double): Long = (seconds * 1000).toLong()
}

data class FormulaParameterUpdate(
    val formulaCode: String,
    val potCode: String,
    val potTypeCode: String,
    val addWaterSeconds: Double,
    val addChickenOilSeconds: Double,
    val addBonePasteSeconds: Double,
)

fun localDefaultFormulaCatalog(): FormulaCatalog =
    FormulaCatalog(
        formulaCode = LOCAL_DEFAULT_FORMULA_CODE,
        formulaName = "本地默认",
        whetherDefault = true,
        pots =
            LOCAL_DEFAULT_POT_BOTTOMS.map { potName ->
                FormulaPot(
                    potCode = potName,
                    potName = potName,
                    potTypes =
                        LOCAL_DEFAULT_POT_TYPES.map { potType ->
                            FormulaPotType(
                                potTypeCode = potType.code,
                                potTypeName = potType.name,
                                addWaterSeconds = LOCAL_DEFAULT_WATER_SECONDS,
                                addChickenOilSeconds = 0.0,
                                addBonePasteSeconds = 0.0,
                                materials = "本地默认，可编辑",
                                basicMaterials = "本地默认，可编辑",
                            )
                        },
                )
            },
    )

fun FormulaCatalog.withUpdatedParameter(update: FormulaParameterUpdate): FormulaCatalog =
    copy(
        pots =
            pots.map { pot ->
                if (pot.potCode != update.potCode) {
                    pot
                } else {
                    pot.copy(
                        potTypes =
                            pot.potTypes.map { type ->
                                if (type.potTypeCode != update.potTypeCode) {
                                    type
                                } else {
                                    type.copy(
                                        addWaterSeconds = update.addWaterSeconds,
                                        addChickenOilSeconds = update.addChickenOilSeconds,
                                        addBonePasteSeconds = update.addBonePasteSeconds,
                                    )
                                }
                            },
                    )
                }
            },
    )

private data class LocalDefaultPotType(
    val code: String,
    val name: String,
)

private const val LOCAL_DEFAULT_FORMULA_CODE = "LOCAL_DEFAULT"
private const val LOCAL_DEFAULT_WATER_SECONDS = 10.0

private val LOCAL_DEFAULT_POT_BOTTOMS =
    listOf(
        "清汤锅",
        "麻辣锅",
        "番茄锅",
        "菌汤锅",
        "冬阴功锅",
        "三鲜锅",
    )

private val LOCAL_DEFAULT_POT_TYPES =
    listOf(
        LocalDefaultPotType(code = "LOCAL_SMALL", name = "小锅"),
        LocalDefaultPotType(code = "LOCAL_SINGLE", name = "单锅"),
        LocalDefaultPotType(code = "LOCAL_SPLIT", name = "拼锅"),
        LocalDefaultPotType(code = "LOCAL_THREE_GRID", name = "三拼锅"),
        LocalDefaultPotType(code = "LOCAL_FOUR_GRID", name = "四宫格"),
    )
