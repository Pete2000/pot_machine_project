package com.example.plccontroller.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.plccontroller.R

@OptIn(ExperimentalFoundationApi::class)
private fun settingsTabLabel(tab: SettingsTab): String =
    when (tab) {
        SettingsTab.Access -> "接入"
        SettingsTab.Formula -> "配方"
        SettingsTab.Device -> "设备"
        SettingsTab.Maintenance -> "维护"
        SettingsTab.Debug -> "调试"
    }

private fun settingsTabIconRes(tab: SettingsTab): Int =
    when (tab) {
        SettingsTab.Access -> R.drawable.ic_settings_access
        SettingsTab.Formula -> R.drawable.ic_settings_formula
        SettingsTab.Device -> R.drawable.ic_settings_device
        SettingsTab.Maintenance -> R.drawable.ic_settings_maintenance
        SettingsTab.Debug -> R.drawable.ic_settings_debug
    }

@Composable
internal fun SettingsTabs(
    selectedTab: SettingsTab,
    onSelect: (SettingsTab) -> Unit,
) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SettingsTab.entries.forEach { tab ->
            SettingsTabItem(tab = tab, selected = tab == selectedTab, onClick = { onSelect(tab) }, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun SettingsTabItem(
    tab: SettingsTab,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val bgColor = if (selected) Cyan.copy(alpha = 0.08f) else Color.Transparent
    val iconBgColor = if (selected) Cyan.copy(alpha = 0.14f) else Color(0xFF07152F)
    val iconBorderColor = if (selected) Cyan.copy(alpha = 0.72f) else Cyan.copy(alpha = 0.18f)
    val iconTint = if (selected) Cyan else TextSecondary
    val textColor = if (selected) Cyan else TextSecondary
    Column(
        modifier =
            modifier
                .clip(RoundedCornerShape(10.dp))
                .clickable {
                    onClick()
                }.background(
                    bgColor,
                ).padding(
                    top = 8.dp,
                    bottom = 4.dp,
                ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier =
                Modifier
                    .width(
                        34.dp,
                    ).height(34.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(iconBgColor)
                    .border(1.dp, iconBorderColor, RoundedCornerShape(9.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(id = settingsTabIconRes(tab)),
                contentDescription = settingsTabLabel(tab),
                tint = iconTint,
                modifier = Modifier.width(20.dp).height(20.dp),
            )
        }
        Text(
            text = settingsTabLabel(tab),
            color = textColor,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(
                        3.dp,
                    ).clip(
                        RoundedCornerShape(99.dp),
                    ).background(
                        if (selected) {
                            Brush.horizontalGradient(
                                listOf(Cyan.copy(alpha = 0.3f), Cyan, Cyan.copy(alpha = 0.3f)),
                            )
                        } else {
                            Brush.horizontalGradient(listOf(Color.Transparent, Color.Transparent, Color.Transparent))
                        },
                    ),
        )
    }
}

@Composable
internal fun SettingsOverviewCard(
    model: SettingsOverviewCardModel,
    modifier: Modifier = Modifier,
    height: androidx.compose.ui.unit.Dp = 108.dp,
) {
    Row(
        modifier =
            modifier
                .height(
                    height,
                ).clip(RoundedCornerShape(8.dp))
                .border(1.dp, model.accent.copy(alpha = 0.55f), RoundedCornerShape(8.dp)),
    ) {
        Box(modifier = Modifier.width(4.dp).fillMaxHeight().background(model.accent))
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.linearGradient(listOf(model.accent.copy(alpha = 0.2f), Panel.copy(alpha = 0.96f), PanelDeep)),
                    ).padding(12.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = model.label,
                color = model.accent,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
            val valueStyle =
                when {
                    model.value.length > 24 -> MaterialTheme.typography.bodyMedium
                    model.value.length > 14 -> MaterialTheme.typography.titleMedium
                    else -> MaterialTheme.typography.headlineSmall
                }
            val valueMaxLines = if (model.value.length > 14) 2 else 1

            Text(
                text = model.value,
                color = TextPrimary,
                style = valueStyle,
                fontWeight = FontWeight.Bold,
                maxLines = valueMaxLines,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = model.detail,
                color = TextSecondary,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
internal fun SettingsFieldCard(
    model: SettingsFieldModel,
    onClick: (() -> Unit)? = null,
    isInfoTip: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val isPlaceholder = model.value == "待接入"
    val alpha = if (isPlaceholder) 0.5f else 1f

    Box(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier =
                Modifier
                    .fillMaxSize()
                    .then(if (onClick != null && !isPlaceholder) Modifier.clickable { onClick() } else Modifier)
                    .clip(RoundedCornerShape(8.dp))
                    .border(
                        1.dp,
                        model.accent.copy(
                            alpha =
                                if (isInfoTip) {
                                    0.15f
                                } else if (isPlaceholder) {
                                    0.2f
                                } else {
                                    0.38f
                                },
                        ),
                        RoundedCornerShape(8.dp),
                    ),
        ) {
            Box(
                modifier =
                    Modifier
                        .width(4.dp)
                        .fillMaxHeight()
                        .background(
                            if (isInfoTip) {
                                Brush.verticalGradient(
                                    listOf(
                                        model.accent.copy(alpha = 0.5f * alpha),
                                        model.accent.copy(
                                            alpha =
                                                0.2f * alpha,
                                        ),
                                    ),
                                )
                            } else {
                                Brush.verticalGradient(listOf(model.accent.copy(alpha = alpha), model.accent.copy(alpha = 0.6f * alpha)))
                            },
                        ),
            )
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(if (isInfoTip || isPlaceholder) Color(0xFF0A1630) else Color(0xFF07152F))
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = model.label,
                    color = model.accent.copy(alpha = alpha),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = model.value,
                    color = TextPrimary.copy(alpha = if (isPlaceholder) 0.4f else 1f),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                if (!model.detail.isNullOrBlank()) {
                    Text(
                        text = model.detail,
                        color = TextSecondary.copy(alpha = 0.6f * alpha),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        // 如果是可点击项且不是占位符，右下角显示一个编辑提示
        if (onClick != null && !isPlaceholder) {
            Icon(
                painter = painterResource(id = R.drawable.ic_settings_device),
                contentDescription = null,
                tint = model.accent.copy(alpha = 0.4f),
                modifier =
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .size(14.dp),
            )
        }
    }
}

@Composable
internal fun CollapsibleSection(
    title: String,
    code: String,
    initiallyExpanded: Boolean = false,
    content: @Composable () -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(initiallyExpanded) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .pointerInput(Unit) {
                        detectTapGestures {
                            expanded = !expanded
                        }
                    }.padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SectionTitle(title = title, code = code, modifier = Modifier.weight(1f))
            Text(
                text = if (expanded) "\u25BC" else "\u25B6",
                color = Cyan.copy(alpha = 0.6f),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(start = 4.dp),
            )
        }
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(),
            exit = shrinkVertically(),
        ) {
            content()
        }
    }
}
