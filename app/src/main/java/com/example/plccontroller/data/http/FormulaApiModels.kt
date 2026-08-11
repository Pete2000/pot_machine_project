package com.example.plccontroller.data.http

import com.example.plccontroller.domain.FormulaCatalog
import com.example.plccontroller.domain.FormulaPot
import com.example.plccontroller.domain.FormulaPotType
import org.json.JSONArray
import org.json.JSONObject

fun JSONObject.toFormulaCatalogs(): List<FormulaCatalog> {
    val data = optJSONArray("data") ?: JSONArray()
    return buildList {
        for (index in 0 until data.length()) {
            add(data.getJSONObject(index).toFormulaCatalog())
        }
    }
}

fun String.toFormulaCatalogOrNull(): FormulaCatalog? =
    runCatching {
        JSONObject(this).toFormulaCatalog()
    }.getOrNull()

fun FormulaCatalog.toJsonString(): String = toJsonObject().toString()

private fun JSONObject.toFormulaCatalog(): FormulaCatalog {
    val potsJson = optJSONArray("pots") ?: JSONArray()
    return FormulaCatalog(
        formulaCode = optString("formulaCode"),
        formulaName = optString("formulaName"),
        whetherDefault = optInt("whetherDefault", 0) == 1,
        pots =
            buildList {
                for (index in 0 until potsJson.length()) {
                    add(potsJson.getJSONObject(index).toFormulaPot())
                }
            },
    )
}

private fun JSONObject.toFormulaPot(): FormulaPot {
    val potTypesJson = optJSONArray("potTypes") ?: JSONArray()
    return FormulaPot(
        potCode = optString("potCode"),
        potName = optString("potName"),
        potTypes =
            buildList {
                for (index in 0 until potTypesJson.length()) {
                    add(potTypesJson.getJSONObject(index).toFormulaPotType())
                }
            },
    )
}

private fun JSONObject.toFormulaPotType(): FormulaPotType =
    FormulaPotType(
        potTypeCode = optString("potTypeCode"),
        potTypeName = optString("potTypeName"),
        addWaterSeconds = optDouble("addWater", 0.0),
        addChickenOilSeconds = optDouble("addChickenOil", 0.0),
        addBonePasteSeconds = optDouble("boneOil", optDouble("bone_oil", 0.0)),
        materials = optString("materials"),
        basicMaterials = optString("basicMaterials"),
    )

private fun FormulaCatalog.toJsonObject(): JSONObject =
    JSONObject()
        .put("formulaCode", formulaCode)
        .put("formulaName", formulaName)
        .put("whetherDefault", if (whetherDefault) 1 else 0)
        .put(
            "pots",
            JSONArray().also { array ->
                pots.forEach { array.put(it.toJsonObject()) }
            },
        )

private fun FormulaPot.toJsonObject(): JSONObject =
    JSONObject()
        .put("potCode", potCode)
        .put("potName", potName)
        .put(
            "potTypes",
            JSONArray().also { array ->
                potTypes.forEach { array.put(it.toJsonObject()) }
            },
        )

private fun FormulaPotType.toJsonObject(): JSONObject =
    JSONObject()
        .put("potTypeCode", potTypeCode)
        .put("potTypeName", potTypeName)
        .put("addWater", addWaterSeconds)
        .put("addChickenOil", addChickenOilSeconds)
        .put("boneOil", addBonePasteSeconds)
        .put("materials", materials)
        .put("basicMaterials", basicMaterials)

fun List<FormulaCatalog>.toJsonString(): String =
    JSONArray()
        .apply {
            forEach { put(it.toJsonObject()) }
        }.toString()

fun String.toFormulaCatalogsListOrNull(): List<FormulaCatalog>? =
    runCatching {
        val array = JSONArray(this)
        buildList {
            for (i in 0 until array.length()) {
                add(array.getJSONObject(i).toFormulaCatalog())
            }
        }
    }.getOrNull()
