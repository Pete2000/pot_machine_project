package com.example.plccontroller.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.plccontroller.domain.FormulaCatalog
import com.example.plccontroller.domain.Order
import com.example.plccontroller.domain.hasBlockingStructureIssue
import com.example.plccontroller.domain.structureIssueTitle
import com.example.plccontroller.ui.potdetail.PotDetailPresenter

@Composable
internal fun CurrentOrderPanel(
    order: Order?,
    formulaCatalog: FormulaCatalog?,
    forcedTopLeftLogicalSlot: Int?,
    currentTopLeftLogicalSlot: Int?,
    isWateringActive: Boolean,
    onRequestWater: () -> Unit,
    isCompact: Boolean,
    modifier: Modifier = Modifier,
) {
    HmiPanel(modifier = modifier) {
        if (isCompact) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                SectionTitle(title = "锅底详情", code = "RECIPE")
                PotRecipeLayout(
                    order = order,
                    formulaCatalog = formulaCatalog,
                    forcedTopLeftLogicalSlot = forcedTopLeftLogicalSlot,
                    currentTopLeftLogicalSlot = currentTopLeftLogicalSlot,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .weight(1f),
                )
                ActionColumn(
                    order = order,
                    actionEnabled = order != null && !order.hasBlockingStructureIssue() && !isWateringActive,
                    actionText = if (isWateringActive) "加水中" else order?.structureIssueTitle(),
                    onRequestWater = onRequestWater,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(148.dp),
                )
            }
        } else {
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(
                    modifier =
                        Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    SectionTitle(title = "锅底详情", code = "RECIPE")
                    PotRecipeLayout(
                        order = order,
                        formulaCatalog = formulaCatalog,
                        forcedTopLeftLogicalSlot = forcedTopLeftLogicalSlot,
                        currentTopLeftLogicalSlot = currentTopLeftLogicalSlot,
                        modifier = Modifier.weight(1f),
                    )
                }
                ActionColumn(
                    order = order,
                    actionEnabled = order != null && !order.hasBlockingStructureIssue() && !isWateringActive,
                    actionText = if (isWateringActive) "加水中" else order?.structureIssueTitle(),
                    onRequestWater = onRequestWater,
                    modifier =
                        Modifier
                            .width(140.dp)
                            .fillMaxHeight(),
                )
            }
        }
    }
}

@Composable
private fun PotRecipeLayout(
    order: Order?,
    formulaCatalog: FormulaCatalog?,
    forcedTopLeftLogicalSlot: Int? = null,
    currentTopLeftLogicalSlot: Int? = null,
    modifier: Modifier = Modifier,
) {
    val detailGrid =
        PotDetailPresenter.presentOrder(
            order = order,
            formulaCatalog = formulaCatalog,
            forcedTopLeftLogicalSlot = forcedTopLeftLogicalSlot,
            currentTopLeftLogicalSlot = currentTopLeftLogicalSlot,
        )
    PotDetailGrid(
        layoutMode = detailGrid.layoutMode,
        cards = detailGrid.cards,
        modifier = modifier,
    )
}
