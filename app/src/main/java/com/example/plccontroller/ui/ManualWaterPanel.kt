package com.example.plccontroller.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.plccontroller.domain.FormulaCatalog
import com.example.plccontroller.runtime.PhaseRotationPrompt
import com.example.plccontroller.ui.potdetail.rotatedLogicalSlots

@Composable
internal fun ManualWaterPage(
    formulaCatalog: FormulaCatalog?,
    formulaSourceLabel: String,
    phaseRotationPrompt: PhaseRotationPrompt?,
    phaseCurrentTopLeftLogicalSlot: Int,
    manualPhaseCompletionToken: Int,
    isWateringActive: Boolean,
    onRequestManualWater: (ManualPotMode, List<ManualRecipeOption>) -> Unit,
    onContinueManualPhase: () -> Unit,
    isCompact: Boolean,
    modifier: Modifier = Modifier,
) {
    var selectedMode by remember { mutableStateOf(ManualPotMode.Single) }
    var activeSlotIndex by remember { mutableStateOf(0) }
    var selectedBottomCodes by remember {
        mutableStateOf(List(ManualPotMode.Single.slotCount()) { "" })
    }
    var completionNoticeVisible by remember { mutableStateOf(false) }
    val availableModes = formulaCatalog.availableManualModes()
    val recipeOptions = formulaCatalog.recipeOptionsFor(selectedMode)
    val displayTopLeftLogicalSlot =
        phaseRotationPrompt?.targetLogicalSlot
            ?: phaseCurrentTopLeftLogicalSlot
    val displayLogicalSlots = selectedMode.rotatedLogicalSlots(displayTopLeftLogicalSlot)
    val activeLogicalSlot =
        displayLogicalSlots
            .getOrNull(activeSlotIndex)
            ?.coerceIn(1, selectedMode.slotCount())
            ?: 1
    val activeLogicalIndex = (activeLogicalSlot - 1).coerceIn(0, selectedMode.slotCount() - 1)
    val activeOption =
        recipeOptions.firstOrNull {
            it.code == selectedBottomCodes.getOrNull(activeLogicalIndex)
        }
    val selectedRecipes =
        List(selectedMode.slotCount()) { index ->
            val code = selectedBottomCodes.getOrNull(index)
            recipeOptions.firstOrNull { it.code == code }
        }
    val allSlotsSelected = selectedRecipes.all { it != null }
    val selectedRecipeOptions = selectedRecipes.filterNotNull()
    val manualWaterEnabled = phaseRotationPrompt == null && allSlotsSelected && !isWateringActive
    val manualWaterButtonText =
        when {
            isWateringActive -> "加水中"
            phaseRotationPrompt != null -> "等待确认"
            allSlotsSelected -> "加水"
            else -> "请选择锅底"
        }

    LaunchedEffect(availableModes) {
        if (selectedMode !in availableModes) {
            selectedMode = availableModes.first()
            activeSlotIndex = 0
        }
    }
    LaunchedEffect(selectedMode, recipeOptions) {
        activeSlotIndex = activeSlotIndex.coerceAtMost(selectedMode.slotCount() - 1)
        selectedBottomCodes =
            List(selectedMode.slotCount()) { index ->
                selectedBottomCodes
                    .getOrNull(index)
                    ?.takeIf { code -> recipeOptions.any { it.code == code } }
                    ?: ""
            }
    }
    LaunchedEffect(manualPhaseCompletionToken) {
        if (manualPhaseCompletionToken > 0) {
            selectedBottomCodes = List(selectedMode.slotCount()) { "" }
            activeSlotIndex = 0
            completionNoticeVisible = true
        }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        HmiPanel {
            SectionTitle(title = "手动加水", code = "MANUAL")
            Spacer(modifier = Modifier.height(14.dp))
            if (isCompact) {
                ManualCompactSelector(
                    availableModes = availableModes,
                    selectedMode = selectedMode,
                    onModeSelect = { mode ->
                        selectedMode = mode
                        activeSlotIndex = 0
                    },
                )
            } else {
                ManualWideModeSelector(
                    availableModes = availableModes,
                    selectedMode = selectedMode,
                    onModeSelect = { mode ->
                        selectedMode = mode
                        activeSlotIndex = 0
                    },
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            if (isCompact) {
                ManualCompactRecipeSelector(
                    selectedMode = selectedMode,
                    recipeOptions = recipeOptions,
                    activeOption = activeOption,
                    activeSlotIndex = activeSlotIndex,
                    activeLogicalIndex = activeLogicalIndex,
                    selectedBottomCodes = selectedBottomCodes,
                    completionNoticeVisible = completionNoticeVisible,
                    formulaSourceLabel = formulaSourceLabel,
                    onCompletionNoticeConsumed = { completionNoticeVisible = false },
                    onActiveSlotIndexChange = { activeSlotIndex = it },
                    onSelectedBottomCodesChange = { selectedBottomCodes = it },
                )
            } else {
                ManualWideRecipeSelector(
                    selectedMode = selectedMode,
                    recipeOptions = recipeOptions,
                    activeOption = activeOption,
                    activeSlotIndex = activeSlotIndex,
                    activeLogicalIndex = activeLogicalIndex,
                    selectedBottomCodes = selectedBottomCodes,
                    completionNoticeVisible = completionNoticeVisible,
                    formulaSourceLabel = formulaSourceLabel,
                    onCompletionNoticeConsumed = { completionNoticeVisible = false },
                    onActiveSlotIndexChange = { activeSlotIndex = it },
                    onSelectedBottomCodesChange = { selectedBottomCodes = it },
                )
            }
        }
        HmiPanel(
            modifier =
                if (isCompact) {
                    Modifier
                        .fillMaxWidth()
                        .height(460.dp)
                } else {
                    Modifier.weight(1f)
                },
        ) {
            SectionTitle(title = "锅底详情", code = "RECIPE")
            Spacer(modifier = Modifier.height(10.dp))
            if (isCompact) {
                ManualCompactPotDetail(
                    selectedMode = selectedMode,
                    selectedRecipes = selectedRecipes,
                    activeSlotIndex = activeSlotIndex,
                    rotationTargetLogicalSlot =
                        phaseRotationPrompt?.targetLogicalSlot
                            ?: phaseCurrentTopLeftLogicalSlot,
                    manualWaterEnabled = manualWaterEnabled,
                    manualWaterButtonText = manualWaterButtonText,
                    selectedRecipeOptions = selectedRecipeOptions,
                    onSelectSlot = { activeSlotIndex = it },
                    onRequestManualWater = onRequestManualWater,
                    onContinueManualPhase = onContinueManualPhase,
                )
            } else {
                ManualWidePotDetail(
                    selectedMode = selectedMode,
                    selectedRecipes = selectedRecipes,
                    activeSlotIndex = activeSlotIndex,
                    rotationTargetLogicalSlot =
                        phaseRotationPrompt?.targetLogicalSlot
                            ?: phaseCurrentTopLeftLogicalSlot,
                    manualWaterEnabled = manualWaterEnabled,
                    manualWaterButtonText = manualWaterButtonText,
                    selectedRecipeOptions = selectedRecipeOptions,
                    onSelectSlot = { activeSlotIndex = it },
                    onRequestManualWater = onRequestManualWater,
                    onContinueManualPhase = onContinueManualPhase,
                )
            }
        }
    }
}
