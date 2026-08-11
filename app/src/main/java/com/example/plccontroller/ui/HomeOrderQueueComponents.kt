package com.example.plccontroller.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.plccontroller.domain.Order
import com.example.plccontroller.domain.operationLabel
import com.example.plccontroller.domain.structureIssueTitle

@Composable
internal fun OrderQueuePanel(
    orders: List<Order>,
    onOrderClick: (Order) -> Unit,
    modifier: Modifier = Modifier,
) {
    HmiPanel(modifier = modifier) {
        QueueHeader()
        if (orders.isEmpty()) {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(top = 6.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF0D2A66)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "暂无待加工订单",
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        } else {
            LazyColumn(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(top = 4.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(orders, key = { it.id }) { order ->
                    QueueRow(
                        order = order,
                        onClick = { onOrderClick(order) },
                    )
                }
            }
        }
    }
}

@Composable
internal fun DelayedOrdersDialog(
    delayedOrders: List<Order>,
    onDismiss: () -> Unit,
    onStartProcess: (Order) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "延时订单池 (${delayedOrders.size})",
                color = TextPrimary,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleLarge,
            )
        },
        text = {
            if (delayedOrders.isEmpty()) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(120.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "暂无延时中的订单",
                        color = TextSecondary,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            } else {
                LazyColumn(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(260.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(delayedOrders, key = { it.id }) { order ->
                        DelayedOrderRow(
                            order = order,
                            onClick = { onStartProcess(order) },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismiss,
                colors = ButtonDefaults.textButtonColors(contentColor = TextSecondary),
            ) {
                Text(
                    text = "关闭",
                    fontWeight = FontWeight.Bold,
                )
            }
        },
        containerColor = Panel,
        titleContentColor = TextPrimary,
        textContentColor = TextSecondary,
        shape = RoundedCornerShape(12.dp),
    )
}

@Composable
internal fun DelayOrderConfirmDialog(
    order: Order,
    onDismiss: () -> Unit,
    onConfirmDelay: (Order) -> Unit,
) {
    val potBottomText =
        order.potBottomSummary?.takeIf { it.isNotBlank() }
            ?: order.potBottomName
            ?: order.recipeCode
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "加入延时？",
                color = TextPrimary,
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Text(
                text = "是否将 ${order.tableCode ?: "--"} 号桌订单加入延时？\n锅底：$potBottomText",
                color = TextSecondary,
                style = MaterialTheme.typography.bodyLarge,
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirmDelay(order) },
                colors = ButtonDefaults.textButtonColors(contentColor = Red),
            ) {
                Text(text = "加入延时")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                colors = ButtonDefaults.textButtonColors(contentColor = TextSecondary),
            ) {
                Text(text = "取消")
            }
        },
        containerColor = Panel,
        titleContentColor = TextPrimary,
        textContentColor = TextSecondary,
    )
}

@Composable
private fun QueueHeader() {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(36.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(PanelDeep)
                .border(1.dp, Cyan.copy(alpha = 0.45f), RoundedCornerShape(8.dp))
                .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        QueueCell("桌号", 0.7f, TextSecondary)
        QueueCell("接收时间", 1.2f, TextSecondary)
        QueueCell("锅型", 0.8f, TextSecondary)
        QueueCell("锅底", 3.2f, TextSecondary)
        QueueCell("营业员", 0.9f, TextSecondary)
        QueueCell("变更", 0.8f, TextSecondary)
    }
}

@Composable
private fun QueueRow(
    order: Order,
    onClick: () -> Unit,
) {
    val isDispatching = order.status == com.example.plccontroller.domain.OrderStatus.Dispatching
    val operationLabel = order.operationLabel()
    val potBottomText =
        order.structureIssueTitle()
            ?: order.potBottomSummary?.takeIf { it.isNotBlank() }
            ?: order.potBottomName
            ?: order.recipeCode

    val colorUrge = Color(0xFFFF5A79)
    val colorTransfer = Color(0xFFFFAB40)

    val rowBackground =
        when {
            isDispatching -> Brush.horizontalGradient(listOf(Cyan.copy(alpha = 0.85f), Color(0xFF051C3F)))
            order.isUrged -> Brush.horizontalGradient(listOf(Color(0xFF380D1A), Color(0xFF1D060D)))
            order.operation == "304" -> Brush.horizontalGradient(listOf(Color(0xFF332007), Color(0xFF1B1103)))
            else -> Brush.horizontalGradient(listOf(Color(0xFF134EE8), Color(0xFF0E2F8A)))
        }

    val rowBorderModifier =
        when {
            isDispatching -> Modifier
            order.isUrged -> Modifier.border(1.dp, colorUrge.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
            order.operation == "304" -> Modifier.border(1.dp, colorTransfer.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
            else -> Modifier
        }

    val cellColor = if (isDispatching) Cyan else TextPrimary
    val displayOperationLabel = if (isDispatching) "加水中" else operationLabel

    val operationColor =
        when {
            isDispatching -> Yellow
            order.isUrged -> colorUrge
            order.operation == "304" -> colorTransfer
            else -> cellColor
        }

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(44.dp)
                .padding(top = 4.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(rowBackground)
                .then(rowBorderModifier)
                .clickable(enabled = !isDispatching) { onClick() }
                .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        QueueCell(order.tableCode ?: "--", 0.7f, cellColor)
        QueueCell(order.listTimeText(), 1.2f, cellColor)
        QueueCell(order.potMode.label(), 0.8f, cellColor)
        QueueCell(potBottomText, 3.2f, cellColor)
        QueueCell(order.operator ?: "--", 0.9f, cellColor)
        QueueCell(displayOperationLabel, 0.8f, operationColor)
    }
}

@Composable
private fun DelayedOrderRow(
    order: Order,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(PanelDeep)
                .border(1.dp, Cyan.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                .clickable { onClick() }
                .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "${order.tableCode ?: "--"}号桌",
                color = TextPrimary,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.bodyLarge,
            )
            Spacer(modifier = Modifier.height(2.dp))
            val potBottomText =
                order.structureIssueTitle()
                    ?: order.potBottomSummary?.takeIf { it.isNotBlank() }
                    ?: order.potBottomName
                    ?: order.recipeCode
            Text(
                text = "锅底: $potBottomText | 锅型: ${order.potMode.label()}",
                color = TextSecondary,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun RowScope.QueueCell(
    text: String,
    weight: Float,
    color: Color,
) {
    Text(
        text = text,
        color = color,
        style = MaterialTheme.typography.bodyLarge,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.weight(weight),
    )
}

@Composable
internal fun UrgedOrdersDialog(
    urgedOrders: List<Order>,
    pinnedOrderIds: Set<String>,
    onDismiss: () -> Unit,
    onPinOrder: (String) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "催单订单池 (${urgedOrders.size})",
                color = TextPrimary,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleLarge,
            )
        },
        text = {
            if (urgedOrders.isEmpty()) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(120.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "暂无被催单的订单",
                        color = TextSecondary,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            } else {
                LazyColumn(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(260.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(urgedOrders, key = { it.id }) { order ->
                        UrgedOrderRow(
                            order = order,
                            isPinned = order.id in pinnedOrderIds,
                            onClick = { onPinOrder(order.id) },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismiss,
                colors = ButtonDefaults.textButtonColors(contentColor = TextSecondary),
            ) {
                Text("关闭", style = MaterialTheme.typography.bodyLarge)
            }
        },
        containerColor = Panel,
        tonalElevation = 6.dp,
    )
}

@Composable
private fun UrgedOrderRow(
    order: Order,
    isPinned: Boolean,
    onClick: () -> Unit,
) {
    val borderColor = if (isPinned) Cyan else Red.copy(alpha = 0.45f)
    val borderWidth = if (isPinned) 1.5.dp else 1.dp
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(PanelDeep)
                .border(borderWidth, borderColor, RoundedCornerShape(8.dp))
                .clickable { onClick() }
                .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "${order.tableCode ?: "--"}号桌 (已催单)",
                    color = Red,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodyLarge,
                )
                if (isPinned) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier =
                            Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(Cyan.copy(alpha = 0.15f))
                                .border(1.dp, Cyan.copy(alpha = 0.8f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                    ) {
                        Text(
                            text = "📌 队列置顶中",
                            color = Cyan,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(2.dp))
            val potBottomText =
                order.structureIssueTitle()
                    ?: order.potBottomSummary?.takeIf { it.isNotBlank() }
                    ?: order.potBottomName
                    ?: order.recipeCode
            Text(
                text = "锅底: $potBottomText | 锅型: ${order.potMode.label()}",
                color = TextSecondary,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
