package com.example.plccontroller.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.plccontroller.domain.Order
import com.example.plccontroller.runtime.TransferCardPosition
import com.example.plccontroller.runtime.TransferDisplayMode

/**
 * 待传锅核心甲板面板
 * 包含 5 个传锅卡位，以及一个可向上滑动的详细订单队列列表
 */
@Composable
internal fun WaitingTransferDeckPanel(
    state: SecondaryDisplayUiState,
    onConfirmTransfer: (String) -> Unit,
    isLandscape: Boolean,
    modifier: Modifier = Modifier,
) {
    val confirmOrder = state.transferDeck.confirmOrder
    val touchConfirmEnabled = !state.maintenanceState.active
    val confirmEnabled = touchConfirmEnabled && confirmOrder != null
    val collapsedSheetHeight = 38.dp // 底部列表折叠时显示的高度
    val expandedSheetHeight = if (isLandscape) 600.dp else 760.dp // 优化后的大视野展开高度
    var listExpanded by remember { mutableStateOf(false) }
    var dragAccumulator by remember { mutableFloatStateOf(0f) }

    // 根据当前传锅模式（最老优先/最新优先）对列表进行排序
    val orderedWaitingOrders =
        remember(state.transferDeck.mode, state.waitingTransferOrders) {
            when (state.transferDeck.mode) {
                TransferDisplayMode.OldestFirst -> state.waitingTransferOrders
                TransferDisplayMode.LatestFirst -> state.waitingTransferOrders.asReversed()
            }
        }

    // 当前已分配到卡位上的订单 ID 集合
    val visibleOrderIds =
        remember(state.transferDeck.cards) {
            state.transferDeck.cards
                .mapNotNull { it.order?.id }
                .toSet()
        }

    // 列表面板高度动画
    val sheetHeight by animateDpAsState(
        targetValue = if (listExpanded) expandedSheetHeight else collapsedSheetHeight,
        animationSpec =
            spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessMediumLow,
            ),
        label = "waiting-transfer-sheet-height",
    )

    // 手势拖拽状态记录
    val sheetDraggableState =
        rememberDraggableState { delta ->
            dragAccumulator += delta
        }

    Box(modifier = modifier) {
        // 背景层：HMI 卡位显示区
        HmiPanel(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(bottom = collapsedSheetHeight + 2.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp), // 紧凑型垂直间距
            ) {
                // 上排三个卡位：1, 2, 3 号位
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .weight(if (isLandscape) 0.85f else 0.95f),
                    // 调大权重以提升上方卡片高度
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TransferDeckCard(
                        card = state.transferDeck.cardAt(TransferCardPosition.TopLeft),
                        modifier = Modifier.weight(1f),
                        touchConfirmEnabled = touchConfirmEnabled,
                        blockedReason = state.maintenanceState.reason,
                        onConfirmTransfer = onConfirmTransfer,
                    )
                    TransferDeckCard(
                        card = state.transferDeck.cardAt(TransferCardPosition.TopCenter),
                        modifier = Modifier.weight(1f),
                        touchConfirmEnabled = touchConfirmEnabled,
                        blockedReason = state.maintenanceState.reason,
                        onConfirmTransfer = onConfirmTransfer,
                    )
                    TransferDeckCard(
                        card = state.transferDeck.cardAt(TransferCardPosition.TopRight),
                        modifier = Modifier.weight(1f),
                        touchConfirmEnabled = touchConfirmEnabled,
                        blockedReason = state.maintenanceState.reason,
                        onConfirmTransfer = onConfirmTransfer,
                    )
                }
                // 下排两个卡位：4, 5 号位（包含确认位）
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TransferDeckCard(
                        card = state.transferDeck.cardAt(TransferCardPosition.BottomLeft),
                        modifier = Modifier.weight(1f),
                        touchConfirmEnabled = touchConfirmEnabled,
                        confirmEnabled = confirmEnabled,
                        blockedReason = state.maintenanceState.reason,
                        onConfirmTransfer = onConfirmTransfer,
                    )
                    TransferDeckCard(
                        card = state.transferDeck.cardAt(TransferCardPosition.BottomRight),
                        modifier = Modifier.weight(1f),
                        touchConfirmEnabled = touchConfirmEnabled,
                        blockedReason = state.maintenanceState.reason,
                        onConfirmTransfer = onConfirmTransfer,
                    )
                }
            }
        }

        // 前景层：可滑动的订单列表详情面板
        Column(
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(sheetHeight)
                    .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                    .background(Color(0xFF04112A))
                    .border(
                        1.dp,
                        Cyan.copy(alpha = 0.52f),
                        RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
                    ).draggable(
                        state = sheetDraggableState,
                        orientation = Orientation.Vertical,
                        onDragStopped = {
                            // 根据拖动距离决定展开还是折叠
                            when {
                                dragAccumulator < -56f -> listExpanded = true
                                dragAccumulator > 56f -> listExpanded = false
                            }
                            dragAccumulator = 0f
                        },
                    ),
        ) {
            // 列表句柄与统计信息
            WaitingTransferSheetHandle(
                expanded = listExpanded,
                totalCount = orderedWaitingOrders.size,
                overflowCount = state.transferDeck.overflowCount,
                onToggle = { listExpanded = !listExpanded },
            )
            // 展开状态下的列表内容
            if (listExpanded) {
                Spacer(modifier = Modifier.height(6.dp))
                if (orderedWaitingOrders.isEmpty()) {
                    Box(
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF07152F))
                                .border(
                                    1.dp,
                                    BorderBlue.copy(alpha = 0.38f),
                                    RoundedCornerShape(12.dp),
                                ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "当前没有待传锅订单。",
                            color = TextSecondary,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                } else {
                    LazyColumn(
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .padding(horizontal = 12.dp, vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        itemsIndexed(
                            items = orderedWaitingOrders,
                            key = { _, order -> order.id },
                        ) { index, order ->
                            WaitingTransferQueueRow(
                                order = order,
                                sequence = index + 1,
                                marker =
                                    when {
                                        order.id == state.transferDeck.confirmOrder?.id -> "左下确认位"
                                        order.id in visibleOrderIds -> "主卡片"
                                        else -> "列表查看"
                                    },
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 列表面板顶部的拉拽句柄及订单汇总文字
 */
@Composable
private fun WaitingTransferSheetHandle(
    expanded: Boolean,
    totalCount: Int,
    overflowCount: Int,
    onToggle: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable { onToggle() }
                .padding(horizontal = 14.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 视觉句柄条
            Box(
                modifier =
                    Modifier
                        .width(46.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(99.dp))
                        .background(Cyan.copy(alpha = 0.72f)),
            )
            Text(
                text = if (expanded) "收起列表" else "展开列表",
                color = TextPrimary,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
            )
        }
        Text(
            text =
                if (overflowCount > 0) {
                    "共 $totalCount 单，额外 $overflowCount 单"
                } else {
                    "共 $totalCount 单"
                },
            color = Cyan,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
        )
    }
}

/**
 * 待传锅详细列表中的单行样式
 */
@Composable
private fun WaitingTransferQueueRow(
    order: Order,
    sequence: Int,
    marker: String,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(Brush.horizontalGradient(listOf(Color(0xFF08265F), Color(0xFF061A42))))
                .border(1.dp, Cyan.copy(alpha = 0.28f), RoundedCornerShape(10.dp))
                .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = "第 $sequence 单 · $marker",
                color = Cyan,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text =
                    buildString {
                        append(order.tableCode ?: "--")
                        append(" · ")
                        append(order.listTimeText())
                    },
                color = TextSecondary,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = queueTitle(order),
                color = TextPrimary,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = order.potMode.label(),
                color = potModeAccent(order.potMode),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = order.tasteText(),
                color = TextSecondary,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
