package com.example.plccontroller.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

/**
 * Shared field content for editing one pot type's dosing durations.
 *
 * Keeping this content outside the dialog keeps the dialog responsible for
 * state and saving, while this composable owns its presentation only.
 */
@Composable
internal fun FormulaParameterEditFields(
    potCode: String,
    waterSeconds: String,
    chickenOilSeconds: String,
    bonePasteSeconds: String,
    onWaterSecondsChange: (String) -> Unit,
    onChickenOilSecondsChange: (String) -> Unit,
    onBonePasteSecondsChange: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(
            text = "当前锅底编码: $potCode\n针对当前锅型微调加料时长（秒）",
            color = TextSecondary,
            style = MaterialTheme.typography.bodyMedium,
        )

        FormulaParameterField(
            label = "加水时长 (s) [范围: 0 ~ 60]",
            value = waterSeconds,
            onValueChange = onWaterSecondsChange,
            minVal = 0.0,
            maxVal = 60.0,
        )
        FormulaParameterField(
            label = "加鸡油时长 (s) [范围: 0 ~ 30]",
            value = chickenOilSeconds,
            onValueChange = onChickenOilSecondsChange,
            minVal = 0.0,
            maxVal = 30.0,
        )
        FormulaParameterField(
            label = "加骨膏时长 (s) [范围: 0 ~ 30]",
            value = bonePasteSeconds,
            onValueChange = onBonePasteSecondsChange,
            minVal = 0.0,
            maxVal = 30.0,
        )
    }
}
