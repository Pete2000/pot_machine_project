package com.example.plccontroller.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.example.plccontroller.R
import com.example.plccontroller.domain.FormulaCatalog
import com.example.plccontroller.domain.FormulaParameterUpdate

@Composable
internal fun FormulaSettingsContent(
    formulaCatalog: FormulaCatalog?,
    availableCatalogs: List<FormulaCatalog>,
    formulaSourceLabel: String,
    formulaSyncIntervalSeconds: Long,
    options: List<FormulaSettingOption>,
    selectedOption: FormulaSettingOption?,
    onSelectCatalog: (String) -> Unit,
    onSelectOption: (String) -> Unit,
    onSaveFormulaParameter: (FormulaParameterUpdate) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (availableCatalogs.isNotEmpty()) {
            SectionTitle(title = "配方版本", code = "LIBRARY")
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                availableCatalogs.forEach { catalog ->
                    CatalogOptionChip(
                        catalog = catalog,
                        selected = catalog.formulaCode == formulaCatalog?.formulaCode,
                        onClick = { onSelectCatalog(catalog.formulaCode) },
                    )
                }
            }

            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(Color.White.copy(alpha = 0.05f)),
            )
        }

        SectionTitle(title = "锅底库", code = "CATALOG")
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            options.forEach { option ->
                FormulaOptionChip(
                    option = option,
                    selected = selectedOption?.code == option.code,
                    onClick = { onSelectOption(option.code) },
                )
            }
        }

        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                Color.Transparent,
                                Cyan.copy(alpha = 0.3f),
                                Cyan.copy(alpha = 0.5f),
                                Cyan.copy(alpha = 0.3f),
                                Color.Transparent,
                            ),
                        ),
                    ),
        )

        AnimatedContent(
            targetState = selectedOption,
            modifier = Modifier.weight(1f),
            transitionSpec = {
                fadeIn(
                    animationSpec =
                        androidx.compose.animation.core
                            .tween(200),
                ) togetherWith
                    fadeOut(
                        animationSpec =
                            androidx.compose.animation.core
                                .tween(150),
                    )
            },
            label = "formula_detail_crossfade",
        ) { option ->
            if (option != null) {
                FormulaDetailPanel(
                    catalog = formulaCatalog,
                    sourceLabel = formulaSourceLabel,
                    option = option,
                    syncIntervalSeconds = formulaSyncIntervalSeconds,
                    onSaveParameter = onSaveFormulaParameter,
                )
            } else {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_settings_formula),
                            contentDescription = null,
                            tint = TextSecondary.copy(alpha = 0.4f),
                            modifier = Modifier.size(40.dp),
                        )
                        Text(
                            text = "请从上方选择一个锅底查看详情",
                            color = TextSecondary.copy(alpha = 0.6f),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
            }
        }
    }
}
