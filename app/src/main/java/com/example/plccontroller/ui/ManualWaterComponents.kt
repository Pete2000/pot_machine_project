package com.example.plccontroller.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.plccontroller.ui.potdetail.PotDetailPresenter

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ManualCompactSelector(
    availableModes: List<ManualPotMode>,
    selectedMode: ManualPotMode,
    onModeSelect: (ManualPotMode) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ManualFieldTitle(text = "选择锅型")
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                availableModes.forEach { mode ->
                    ManualOptionButton(
                        text = mode.label(),
                        selected = selectedMode == mode,
                        onClick = { onModeSelect(mode) },
                        height = 60.dp,
                        modifier = Modifier.width(96.dp),
                    )
                }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ManualFieldTitle(text = "出水关系")
            ManualOutletSummary(mode = selectedMode)
        }
    }
}

@Composable
internal fun ManualWideModeSelector(
    availableModes: List<ManualPotMode>,
    selectedMode: ManualPotMode,
    onModeSelect: (ManualPotMode) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(
            modifier = Modifier.weight(1.1f),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ManualFieldTitle(text = "选择锅型")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                availableModes.forEach { mode ->
                    ManualOptionButton(
                        text = mode.label(),
                        selected = selectedMode == mode,
                        onClick = { onModeSelect(mode) },
                        height = 72.dp,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
        Column(
            modifier = Modifier.weight(0.7f),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ManualFieldTitle(text = "出水关系")
            ManualOutletSummary(mode = selectedMode)
        }
    }
}

@Composable
internal fun ManualCompactRecipeSelector(
    selectedMode: ManualPotMode,
    recipeOptions: List<ManualRecipeOption>,
    activeOption: ManualRecipeOption?,
    activeSlotIndex: Int,
    activeLogicalIndex: Int,
    selectedBottomCodes: List<String>,
    completionNoticeVisible: Boolean,
    formulaSourceLabel: String,
    onCompletionNoticeConsumed: () -> Unit,
    onActiveSlotIndexChange: (Int) -> Unit,
    onSelectedBottomCodesChange: (List<String>) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        ManualRecipeOptions(
            rowSize = 2,
            selectedMode = selectedMode,
            recipeOptions = recipeOptions,
            activeOption = activeOption,
            activeSlotIndex = activeSlotIndex,
            activeLogicalIndex = activeLogicalIndex,
            selectedBottomCodes = selectedBottomCodes,
            onCompletionNoticeConsumed = onCompletionNoticeConsumed,
            onActiveSlotIndexChange = onActiveSlotIndexChange,
            onSelectedBottomCodesChange = onSelectedBottomCodesChange,
        )
        ManualCurrentEdit(
            selectedMode = selectedMode,
            activeSlotIndex = activeSlotIndex,
            activeOption = activeOption,
            completionNoticeVisible = completionNoticeVisible,
            formulaSourceLabel = formulaSourceLabel,
        )
    }
}

@Composable
internal fun ManualWideRecipeSelector(
    selectedMode: ManualPotMode,
    recipeOptions: List<ManualRecipeOption>,
    activeOption: ManualRecipeOption?,
    activeSlotIndex: Int,
    activeLogicalIndex: Int,
    selectedBottomCodes: List<String>,
    completionNoticeVisible: Boolean,
    formulaSourceLabel: String,
    onCompletionNoticeConsumed: () -> Unit,
    onActiveSlotIndexChange: (Int) -> Unit,
    onSelectedBottomCodesChange: (List<String>) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        ManualRecipeOptions(
            rowSize = 3,
            selectedMode = selectedMode,
            recipeOptions = recipeOptions,
            activeOption = activeOption,
            activeSlotIndex = activeSlotIndex,
            activeLogicalIndex = activeLogicalIndex,
            selectedBottomCodes = selectedBottomCodes,
            onCompletionNoticeConsumed = onCompletionNoticeConsumed,
            onActiveSlotIndexChange = onActiveSlotIndexChange,
            onSelectedBottomCodesChange = onSelectedBottomCodesChange,
            modifier = Modifier.weight(1f),
        )
        ManualCurrentEdit(
            selectedMode = selectedMode,
            activeSlotIndex = activeSlotIndex,
            activeOption = activeOption,
            completionNoticeVisible = completionNoticeVisible,
            formulaSourceLabel = formulaSourceLabel,
            modifier = Modifier.width(220.dp),
        )
    }
}

@Composable
private fun ManualRecipeOptions(
    rowSize: Int,
    selectedMode: ManualPotMode,
    recipeOptions: List<ManualRecipeOption>,
    activeOption: ManualRecipeOption?,
    activeSlotIndex: Int,
    activeLogicalIndex: Int,
    selectedBottomCodes: List<String>,
    onCompletionNoticeConsumed: () -> Unit,
    onActiveSlotIndexChange: (Int) -> Unit,
    onSelectedBottomCodesChange: (List<String>) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ManualFieldTitle(text = "选择锅底")
        recipeOptions.chunked(rowSize).forEach { rowItems ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                rowItems.forEach { option ->
                    ManualOptionButton(
                        text = option.name,
                        selected = activeOption?.code == option.code,
                        onClick = {
                            onSelectedBottomCodesChange(
                                selectedBottomCodes.toMutableList().also {
                                    it[activeLogicalIndex] = option.code
                                },
                            )
                            onCompletionNoticeConsumed()
                            onActiveSlotIndexChange((activeSlotIndex + 1) % selectedMode.slotCount())
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
                repeat(rowSize - rowItems.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun ManualCurrentEdit(
    selectedMode: ManualPotMode,
    activeSlotIndex: Int,
    activeOption: ManualRecipeOption?,
    completionNoticeVisible: Boolean,
    formulaSourceLabel: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ManualFieldTitle(text = "当前编辑")
        ManualEditSummary(
            slotLabel = selectedMode.slotLabels()[activeSlotIndex],
            bottom = activeOption?.name ?: "未选择锅底",
            detail =
                activeOption?.let { "$formulaSourceLabel / ${it.timingText()}" }
                    ?: if (completionNoticeVisible) {
                        "上次手动加水已完成，请重新选择锅底"
                    } else {
                        "请选择当前锅位锅底"
                    },
        )
    }
}

@Composable
internal fun ManualCompactPotDetail(
    selectedMode: ManualPotMode,
    selectedRecipes: List<ManualRecipeOption?>,
    activeSlotIndex: Int,
    rotationTargetLogicalSlot: Int?,
    manualWaterEnabled: Boolean,
    manualWaterButtonText: String,
    selectedRecipeOptions: List<ManualRecipeOption>,
    onSelectSlot: (Int) -> Unit,
    onRequestManualWater: (ManualPotMode, List<ManualRecipeOption>) -> Unit,
    onContinueManualPhase: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ManualPotDetailLayout(
            mode = selectedMode,
            selectedRecipes = selectedRecipes,
            activeSlotIndex = activeSlotIndex,
            rotationTargetLogicalSlot = rotationTargetLogicalSlot,
            onSelectSlot = onSelectSlot,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .weight(1f),
        )
        ManualActionColumn(
            selectedMode = selectedMode,
            manualWaterEnabled = manualWaterEnabled,
            manualWaterButtonText = manualWaterButtonText,
            selectedRecipeOptions = selectedRecipeOptions,
            onRequestManualWater = onRequestManualWater,
            onContinueManualPhase = onContinueManualPhase,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(148.dp),
        )
    }
}

@Composable
internal fun ManualWidePotDetail(
    selectedMode: ManualPotMode,
    selectedRecipes: List<ManualRecipeOption?>,
    activeSlotIndex: Int,
    rotationTargetLogicalSlot: Int?,
    manualWaterEnabled: Boolean,
    manualWaterButtonText: String,
    selectedRecipeOptions: List<ManualRecipeOption>,
    onSelectSlot: (Int) -> Unit,
    onRequestManualWater: (ManualPotMode, List<ManualRecipeOption>) -> Unit,
    onContinueManualPhase: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ManualPotDetailLayout(
            mode = selectedMode,
            selectedRecipes = selectedRecipes,
            activeSlotIndex = activeSlotIndex,
            rotationTargetLogicalSlot = rotationTargetLogicalSlot,
            onSelectSlot = onSelectSlot,
            modifier =
                Modifier
                    .weight(1f)
                    .fillMaxHeight(),
        )
        ManualActionColumn(
            selectedMode = selectedMode,
            manualWaterEnabled = manualWaterEnabled,
            manualWaterButtonText = manualWaterButtonText,
            selectedRecipeOptions = selectedRecipeOptions,
            onRequestManualWater = onRequestManualWater,
            onContinueManualPhase = onContinueManualPhase,
            modifier =
                Modifier
                    .width(140.dp)
                    .fillMaxHeight(),
        )
    }
}

@Composable
private fun ManualActionColumn(
    selectedMode: ManualPotMode,
    manualWaterEnabled: Boolean,
    manualWaterButtonText: String,
    selectedRecipeOptions: List<ManualRecipeOption>,
    onRequestManualWater: (ManualPotMode, List<ManualRecipeOption>) -> Unit,
    onContinueManualPhase: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ActionColumn(
        order = null,
        heading = selectedMode.label(),
        subheading = "加水",
        actionEnabled = manualWaterEnabled,
        actionText = manualWaterButtonText,
        onRequestWater = {
            if (manualWaterEnabled) {
                onRequestManualWater(selectedMode, selectedRecipeOptions)
            }
        },
        manualPhasePrompt = null,
        onContinueManualPhase = onContinueManualPhase,
        modifier = modifier,
    )
}

@Composable
private fun ManualOutletSummary(mode: ManualPotMode) {
    Row(
        modifier =
            Modifier
                .height(52.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF061735))
                .border(1.dp, Cyan.copy(alpha = 0.32f), RoundedCornerShape(8.dp))
                .padding(horizontal = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "${mode.slotCount()} 个锅底区",
            color = TextPrimary,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = mode.outletSummary(),
            color = Cyan,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ManualEditSummary(
    slotLabel: String,
    bottom: String,
    detail: String,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(114.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF061735))
                .border(1.dp, Yellow.copy(alpha = 0.62f), RoundedCornerShape(8.dp))
                .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = slotLabel,
            color = Yellow,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = bottom,
            color = TextPrimary,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = detail,
            color = TextSecondary,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ManualPotDetailLayout(
    mode: ManualPotMode,
    selectedRecipes: List<ManualRecipeOption?>,
    activeSlotIndex: Int,
    rotationTargetLogicalSlot: Int?,
    onSelectSlot: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val detailGrid =
        PotDetailPresenter.presentManual(
            mode = mode,
            selectedRecipes = selectedRecipes,
            activePhysicalIndex = activeSlotIndex,
            displayTopLeftLogicalSlot = rotationTargetLogicalSlot,
            onSelectPhysicalIndex = onSelectSlot,
        )
    PotDetailGrid(
        layoutMode = detailGrid.layoutMode,
        cards = detailGrid.cards,
        modifier = modifier,
    )
}

@Composable
private fun ManualOptionButton(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    height: Dp = 52.dp,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .height(height)
                .clip(RoundedCornerShape(8.dp))
                .background(if (selected) Cyan.copy(alpha = 0.18f) else Color(0xFF061735))
                .border(
                    1.dp,
                    if (selected) Cyan.copy(alpha = 0.9f) else Cyan.copy(alpha = 0.28f),
                    RoundedCornerShape(8.dp),
                ).clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = if (selected) Cyan else TextPrimary,
            style = if (height > 52.dp) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ManualFieldTitle(text: String) {
    Text(
        text = text,
        color = TextPrimary,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
    )
}
