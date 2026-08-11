package com.example.plccontroller.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.plccontroller.R
import com.example.plccontroller.domain.FormulaCatalog
import com.example.plccontroller.domain.FormulaParameterUpdate
import com.example.plccontroller.domain.FormulaPotType

@Composable
internal fun FormulaOptionChip(
    option: FormulaSettingOption,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val bgColor = if (selected) Blue.copy(alpha = 0.18f) else Panel
    val borderColor = if (selected) Blue else Color.White.copy(alpha = 0.08f)
    val textColor = if (selected) TextPrimary else TextSecondary

    Row(
        modifier =
            modifier
                .clip(RoundedCornerShape(10.dp))
                .background(bgColor)
                .border(1.dp, borderColor, RoundedCornerShape(10.dp))
                .clickable { onClick() }
                .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (selected) {
            Box(
                modifier =
                    Modifier
                        .width(3.dp)
                        .height(20.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Blue),
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = option.name,
                color = textColor,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "编码: ${option.code}  •  ${option.typeDetails.size} 类锅型",
                color = if (selected) Blue.copy(alpha = 0.8f) else TextSecondary.copy(alpha = 0.6f),
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
            )
        }
    }
}

@Composable
internal fun FormulaDetailPanel(
    catalog: FormulaCatalog?,
    sourceLabel: String,
    option: FormulaSettingOption,
    syncIntervalSeconds: Long,
    onSaveParameter: (FormulaParameterUpdate) -> Unit,
) {
    var editingType by remember { mutableStateOf<FormulaPotType?>(null) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionTitle(title = "${option.name} (${option.code})", code = "RECIPE_DETAIL")

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            FormulaDetailBlock("数据来源", sourceLabel, modifier = Modifier.weight(1f))
            FormulaDetailBlock("同步周期", "${syncIntervalSeconds}s", modifier = Modifier.weight(1f))
            FormulaDetailBlock("锅型覆盖", "${option.typeDetails.size} 类", modifier = Modifier.weight(1f))
        }

        HmiPanel(modifier = Modifier.weight(1f)) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 16.dp),
            ) {
                items(option.typeDetails) { type ->
                    FormulaTypeCard(
                        type = type,
                        onClick = { editingType = type },
                    )
                }
            }
        }
    }

    editingType?.let { type ->
        FormulaParameterEditDialog(
            formulaCode = catalog?.formulaCode ?: "LOCAL_DEFAULT",
            potCode = option.code,
            type = type,
            onDismiss = { editingType = null },
            onConfirm = { update ->
                onSaveParameter(update)
                editingType = null
            },
        )
    }
}

@Composable
private fun FormulaTypeCard(
    type: FormulaPotType,
    onClick: () -> Unit,
) {
    val accent = if (type.addWaterSeconds > 0) Green else Yellow

    Box(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier =
                Modifier
                    .fillMaxSize()
                    .clickable { onClick() }
                    .clip(RoundedCornerShape(8.dp))
                    .border(1.dp, accent.copy(alpha = 0.38f), RoundedCornerShape(8.dp)),
        ) {
            Box(
                modifier =
                    Modifier
                        .width(4.dp)
                        .fillMaxHeight()
                        .background(
                            Brush.verticalGradient(
                                listOf(accent, accent.copy(alpha = 0.6f)),
                            ),
                        ),
            )
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(Color(0xFF07152F))
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = type.potTypeName,
                        color = accent,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = type.materials.ifBlank { "无配料" },
                        color = TextSecondary.copy(alpha = 0.6f),
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FormulaParamCell(
                        label = "水",
                        value = "${type.addWaterSeconds}s",
                        accent = Cyan,
                        modifier = Modifier.weight(1f),
                    )
                    FormulaParamCell(
                        label = "油",
                        value = "${type.addChickenOilSeconds}s",
                        accent = Yellow,
                        modifier = Modifier.weight(1f),
                    )
                    FormulaParamCell(
                        label = "膏",
                        value = "${type.addBonePasteSeconds}s",
                        accent = Green,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        Icon(
            painter = painterResource(id = R.drawable.ic_settings_device),
            contentDescription = null,
            tint = accent.copy(alpha = 0.4f),
            modifier =
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(14.dp),
        )
    }
}

@Composable
private fun FormulaParamCell(
    label: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .clip(RoundedCornerShape(6.dp))
                .background(accent.copy(alpha = 0.08f))
                .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = label,
            color = accent.copy(alpha = 0.7f),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = value,
            color = TextPrimary,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun FormulaParameterEditDialog(
    formulaCode: String,
    potCode: String,
    type: FormulaPotType,
    onDismiss: () -> Unit,
    onConfirm: (FormulaParameterUpdate) -> Unit,
) {
    var waterSeconds by remember { mutableStateOf(type.addWaterSeconds.toString()) }
    var chickenOilSeconds by remember { mutableStateOf(type.addChickenOilSeconds.toString()) }
    var bonePasteSeconds by remember { mutableStateOf(type.addBonePasteSeconds.toString()) }

    val waterVal = waterSeconds.toDoubleOrNull()
    val oilVal = chickenOilSeconds.toDoubleOrNull()
    val pasteVal = bonePasteSeconds.toDoubleOrNull()

    val isWaterValid = waterVal != null && waterVal in 0.0..60.0
    val isOilValid = oilVal != null && oilVal in 0.0..30.0
    val isPasteValid = pasteVal != null && pasteVal in 0.0..30.0
    val canSave = isWaterValid && isOilValid && isPasteValid

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "编辑 ${type.potTypeName} 参数",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            FormulaParameterEditFields(
                potCode = potCode,
                waterSeconds = waterSeconds,
                chickenOilSeconds = chickenOilSeconds,
                bonePasteSeconds = bonePasteSeconds,
                onWaterSecondsChange = { waterSeconds = it },
                onChickenOilSecondsChange = { chickenOilSeconds = it },
                onBonePasteSecondsChange = { bonePasteSeconds = it },
            )
        },
        confirmButton = {
            TextButton(
                enabled = canSave,
                onClick = {
                    onConfirm(
                        FormulaParameterUpdate(
                            formulaCode = formulaCode,
                            potCode = potCode,
                            potTypeCode = type.potTypeCode,
                            addWaterSeconds = waterVal ?: type.addWaterSeconds,
                            addChickenOilSeconds = oilVal ?: type.addChickenOilSeconds,
                            addBonePasteSeconds = pasteVal ?: type.addBonePasteSeconds,
                        ),
                    )
                },
                colors =
                    ButtonDefaults.textButtonColors(
                        contentColor = Cyan,
                        disabledContentColor = TextSecondary.copy(alpha = 0.4f),
                    ),
            ) { Text("保存") }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                colors = ButtonDefaults.textButtonColors(contentColor = TextSecondary),
            ) { Text("取消") }
        },
        containerColor = Panel,
        titleContentColor = TextPrimary,
        textContentColor = TextSecondary,
    )
}

private fun formatDoubleClean(value: Double): String {
    val formatted = String.format(java.util.Locale.US, "%.2f", value)
    return when {
        formatted.endsWith(".00") -> formatted.substring(0, formatted.length - 3)
        formatted.endsWith("0") -> formatted.substring(0, formatted.length - 1)
        else -> formatted
    }
}

@Composable
internal fun FormulaParameterField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    minVal: Double,
    maxVal: Double,
    step: Double = 0.1,
) {
    val doubleVal = value.toDoubleOrNull()
    val isError = doubleVal == null || doubleVal < minVal || doubleVal > maxVal
    val errorMsg =
        when {
        doubleVal == null -> "请输入合法的数值"
        doubleVal < minVal -> "不能低于 ${minVal}s"
        doubleVal > maxVal -> "不能超过 ${maxVal}s"
        else -> null
    }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = label,
            color = TextPrimary.copy(alpha = 0.9f),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Button(
                onClick = {
                    val current = doubleVal ?: minVal
                    val newVal = (current - step).coerceIn(minVal, maxVal)
                    onValueChange(formatDoubleClean(newVal))
                },
                colors = ButtonDefaults.buttonColors(containerColor = PanelBright),
                contentPadding = PaddingValues(0.dp),
                modifier = Modifier.size(44.dp),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("-", color = TextPrimary, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }

            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                isError = isError,
                modifier = Modifier.weight(1f),
                colors =
                    OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedContainerColor = Color(0xFF09245A),
                        unfocusedContainerColor = Color(0xFF09245A),
                        cursorColor = Yellow,
                        focusedBorderColor = Yellow,
                        unfocusedBorderColor = Cyan.copy(alpha = 0.5f),
                        focusedLabelColor = Yellow,
                        unfocusedLabelColor = TextSecondary,
                    ),
            )

            Button(
                onClick = {
                    val current = doubleVal ?: minVal
                    val newVal = (current + step).coerceIn(minVal, maxVal)
                    onValueChange(formatDoubleClean(newVal))
                },
                colors = ButtonDefaults.buttonColors(containerColor = PanelBright),
                contentPadding = PaddingValues(0.dp),
                modifier = Modifier.size(44.dp),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("+", color = TextPrimary, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
        }
        errorMsg?.let {
            Text(text = it, color = Red, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun FormulaDetailBlock(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .clip(RoundedCornerShape(8.dp))
                .background(Panel)
                .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(text = label, color = TextSecondary, style = MaterialTheme.typography.labelSmall)
        Text(text = value, color = Cyan, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
    }
}

@Composable
internal fun CatalogOptionChip(
    catalog: FormulaCatalog,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val bgColor = if (selected) Cyan.copy(alpha = 0.18f) else Panel
    val borderColor = if (selected) Cyan else Color.White.copy(alpha = 0.08f)
    val textColor = if (selected) TextPrimary else TextSecondary

    Row(
        modifier =
            modifier
                .clip(RoundedCornerShape(10.dp))
                .background(bgColor)
                .border(1.dp, borderColor, RoundedCornerShape(10.dp))
                .clickable { onClick() }
                .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (selected) {
            Box(
                modifier =
                    Modifier
                        .width(3.dp)
                        .height(20.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Cyan),
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = catalog.formulaName.ifBlank { catalog.formulaCode },
                color = textColor,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = if (catalog.whetherDefault) "默认版本" else "可选版本",
                color = if (selected) Cyan.copy(alpha = 0.8f) else TextSecondary.copy(alpha = 0.6f),
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
            )
        }
    }
}
