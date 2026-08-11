package com.example.plccontroller.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.plccontroller.domain.Order
import com.example.plccontroller.domain.OrderStatus
import com.example.plccontroller.domain.operationLabel
import com.example.plccontroller.domain.structureIssueTitle
import com.example.plccontroller.runtime.OrderQueueOrderingPolicy
import com.example.plccontroller.runtime.TransferDisplayMode
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
@Suppress("LongParameterList", "LongMethod")
@Composable
internal fun OrdersScreen(
    initialTab: OrdersPageTab = OrdersPageTab.PendingWater,
    pendingOrders: List<Order>,
    waitingTransferOrders: List<Order>,
    currentOrder: Order?,
    completedOrders: List<Order>,
    cancelledOrders: List<Order>,
    onRefreshOrders: () -> Unit,
    onCancelPendingOrder: (Order) -> Unit,
    onRewaterOrder: (Order) -> Unit,
    emptyReason: String? = null,
    formulaCatalog: com.example.plccontroller.domain.FormulaCatalog? = null,
    modifier: Modifier = Modifier,
) {
    val tabs = OrdersPageTab.entries
    val initialPage = remember(initialTab) { tabs.indexOf(initialTab).coerceAtLeast(0) }
    val pagerState = rememberPagerState(initialPage = initialPage, pageCount = { tabs.size })
    val coroutineScope = rememberCoroutineScope()
    val selectedTab = tabs[pagerState.currentPage]
    var contentReady by rememberSaveable { mutableStateOf(false) }
    var waitingTransferSortMode by rememberSaveable {
        mutableStateOf(TransferDisplayMode.OldestFirst)
    }
    var viewingDetailOrder by remember { mutableStateOf<Order?>(null) }

    LaunchedEffect(Unit) {
        if (!contentReady) {
            withFrameNanos { }
            contentReady = true
        }
    }

    LaunchedEffect(initialTab) {
        pagerState.scrollToPage(tabs.indexOf(initialTab).coerceAtLeast(0))
    }

    HmiPanel(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SectionTitle(
                title = "订单列表",
                code = "ORDERS",
                modifier = Modifier.weight(1f),
            )
            ControlButton(text = "刷新", onClick = onRefreshOrders)
        }
        Spacer(modifier = Modifier.height(12.dp))
        OrdersPageTabs(
            selectedTab = selectedTab,
            onSelect = { tab ->
                coroutineScope.launch {
                    pagerState.scrollToPage(tabs.indexOf(tab))
                }
            },
        )
        Spacer(modifier = Modifier.height(12.dp))
        if (selectedTab == OrdersPageTab.WaitingTransfer) {
            WaitingTransferSortToggle(
                mode = waitingTransferSortMode,
                onModeChange = { waitingTransferSortMode = it },
            )
            Spacer(modifier = Modifier.height(10.dp))
        }
        OrderListHeader()
        Spacer(modifier = Modifier.height(8.dp))
        if (!contentReady) {
            PageWarmupPlaceholder(
                title = "正在准备订单页",
                detail = "先显示页面框架，再补齐列表内容",
                modifier = Modifier.weight(1f),
            )
        } else {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f),
            ) { page ->
                val tab = tabs[page]
                val filteredOrders =
                    remember(
                        tab,
                        pendingOrders,
                        waitingTransferOrders,
                        completedOrders,
                        cancelledOrders,
                        waitingTransferSortMode,
                    ) {
                        when (tab) {
                            OrdersPageTab.Completed -> OrderQueueOrderingPolicy.completedHistory(completedOrders)
                            OrdersPageTab.PendingWater ->
                                OrderQueueOrderingPolicy.pendingWater(
                                    pendingOrders.filter { it.status.isPendingWaterBucket() },
                                )
                            OrdersPageTab.WaitingTransfer ->
                                OrderQueueOrderingPolicy.waitingTransfer(
                                    waitingTransferOrders,
                                    waitingTransferSortMode,
                                )
                            OrdersPageTab.Cancelled -> OrderQueueOrderingPolicy.cancelledHistory(cancelledOrders)
                        }
                    }
                if (filteredOrders.isEmpty()) {
                    Box(
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF071B3E)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(
                                text = tab.emptyText(),
                                color = TextSecondary,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            emptyReason?.takeIf(String::isNotBlank)?.let { reason ->
                                Text(
                                    text = reason,
                                    color = Yellow,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    textAlign = TextAlign.Center,
                                )
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        items(filteredOrders, key = { "${tab.name}-${it.id}" }) { order ->
                            OrderListRow(
                                order = order,
                                selectedTab = tab,
                                isCurrent = currentOrder?.id == order.id,
                                onCancelPendingOrder = onCancelPendingOrder,
                                onRewaterOrder = { o ->
                                    onRewaterOrder(o)
                                },
                                onViewDetailOrder = { o ->
                                    viewingDetailOrder = o
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    if (viewingDetailOrder != null) {
        OrderDetailDialog(
            order = viewingDetailOrder!!,
            formulaCatalog = formulaCatalog,
            onDismiss = { viewingDetailOrder = null }
        )
    }
}

@Composable
private fun OrderListHeader() {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(36.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(PanelDeep)
                .border(1.dp, Cyan.copy(alpha = 0.45f), RoundedCornerShape(8.dp))
                .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OrderListCell("桌号", 0.7f, TextSecondary)
        OrderListCell("时间", 1.65f, TextSecondary)
        OrderListCell("锅型", 0.8f, TextSecondary)
        OrderListCell("锅底", 2.6f, TextSecondary)
        OrderListCell("营业员", 0.75f, TextSecondary)
        OrderListCell("变更", 0.65f, TextSecondary)
        OrderListCell("操作", 1.75f, TextSecondary, TextAlign.End)
    }
}

@Suppress("CyclomaticComplexMethod", "LongMethod")
@Composable
private fun OrderListRow(
    order: Order,
    selectedTab: OrdersPageTab,
    isCurrent: Boolean,
    onCancelPendingOrder: (Order) -> Unit,
    onRewaterOrder: (Order) -> Unit,
    onViewDetailOrder: (Order) -> Unit,
) {
    val rowColor = if (isCurrent) Color(0xFF0F5BEE) else Color(0xFF08265F)
    val operationLabel = order.operationLabel()
    val structureIssue = order.structureIssueTitle()
    val potBottomLines = order.potBottomListLines(structureIssue)
    val timeDetails = order.timeDetailsFor(selectedTab)

    val colorUrge = Color(0xFFFF5A79)
    val colorTransfer = Color(0xFFFFAB40)
    val colorRefund = Color(0xFFE040FB)

    val operationColor =
        when {
            order.isUrged -> colorUrge
            order.operation == "304" -> colorTransfer
            order.operation == "303" -> colorRefund
            else -> TextSecondary
        }

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(82.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Brush.horizontalGradient(listOf(rowColor, Color(0xFF061A42))))
                .border(1.dp, Cyan.copy(alpha = if (isCurrent) 0.8f else 0.28f), RoundedCornerShape(8.dp))
                .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OrderListCell(order.tableCode ?: "--", 0.7f, TextPrimary)
        OrderTimeCell(timeDetails, 1.65f)
        OrderListCell(order.potMode.label(), 0.8f, TextPrimary)
        OrderPotBottomCell(potBottomLines, 2.6f, structureIssue != null)
        OrderListCell(order.operator ?: "--", 0.75f, TextPrimary)
        OrderOperationBadge(operationLabel, operationColor, 0.65f)
        Box(
            modifier = Modifier.weight(1.75f),
            contentAlignment = Alignment.CenterEnd,
        ) {
            OrderActionChip(
                text = selectedTab.actionText(),
                color = selectedTab.actionColor(),
                enabled =
                    selectedTab == OrdersPageTab.PendingWater ||
                        selectedTab == OrdersPageTab.WaitingTransfer ||
                        selectedTab == OrdersPageTab.Cancelled ||
                        selectedTab == OrdersPageTab.Completed,
                onClick = {
                    when (selectedTab) {
                        OrdersPageTab.PendingWater -> onCancelPendingOrder(order)
                        OrdersPageTab.WaitingTransfer,
                        OrdersPageTab.Cancelled,
                        -> onRewaterOrder(order)
                        OrdersPageTab.Completed -> onViewDetailOrder(order)
                    }
                },
            )
        }
    }
}

@Composable
private fun RowScope.OrderPotBottomCell(
    lines: List<String>,
    weight: Float,
    warning: Boolean,
) {
    Column(
        modifier = Modifier.weight(weight),
        verticalArrangement = Arrangement.Center,
    ) {
        lines.take(4).forEach { line ->
            Text(
                text = line,
                color = if (warning) Yellow else TextPrimary,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun RowScope.OrderTimeCell(
    details: OrderTimeDetails,
    weight: Float,
) {
    Column(
        modifier = Modifier.weight(weight),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = details.primary.date,
            color = TextSecondary,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(modifier = Modifier.height(3.dp))
        OrderTimestampLineView(details.primary, primary = true)
        details.secondary?.let { secondary ->
            Spacer(modifier = Modifier.height(2.dp))
            OrderTimestampLineView(secondary, primary = false)
        }
    }
}

@Composable
private fun OrderTimestampLineView(
    block: OrderTimestampBlockModel,
    primary: Boolean,
) {
    val accent = if (primary) TextPrimary else Cyan
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Text(
            text = block.label,
            color = if (primary) TextSecondary else Cyan.copy(alpha = 0.82f),
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.width(26.dp),
        )
        Text(
            text = block.time,
            color = accent,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun RowScope.OrderListCell(
    text: String,
    weight: Float,
    color: Color,
    textAlign: TextAlign = TextAlign.Start,
) {
    Text(
        text = text,
        color = color,
        style = MaterialTheme.typography.bodyLarge,
        fontWeight = FontWeight.SemiBold,
        textAlign = textAlign,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.weight(weight),
    )
}

@Composable
private fun RowScope.OrderOperationBadge(
    text: String,
    color: Color,
    weight: Float,
) {
    Box(
        modifier =
            Modifier
                .weight(weight),
        contentAlignment = Alignment.CenterStart,
    ) {
        val displayColor =
            if (text == "下单" || text.isBlank() || text == "--") {
                TextSecondary
            } else {
                color
            }
        if (text.contains("/")) {
            val lines = text.split("/")
            Column(
                verticalArrangement = Arrangement.spacedBy(2.dp),
                horizontalAlignment = Alignment.Start,
            ) {
                lines.forEach { line ->
                    val lineColor = when (line) {
                        "转台" -> Color(0xFFFFAB40)
                        "催单" -> Color(0xFFFF5A79)
                        "退单" -> Color(0xFFE040FB)
                        "下单" -> TextSecondary
                        else -> displayColor
                    }
                    Text(
                        text = line,
                        color = lineColor,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                    )
                }
            }
        } else {
            Text(
                text = text.ifBlank { "--" },
                color = displayColor,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun OrdersPageTabs(
    selectedTab: OrdersPageTab,
    onSelect: (OrdersPageTab) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        OrdersPageTab.entries.forEach { tab ->
            OrdersPageTabItem(
                tab = tab,
                selected = tab == selectedTab,
                onClick = { onSelect(tab) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun OrdersPageTabItem(
    tab: OrdersPageTab,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .clip(RoundedCornerShape(10.dp))
                .clickable { onClick() }
                .background(if (selected) Cyan.copy(alpha = 0.08f) else Color.Transparent)
                .padding(top = 8.dp, bottom = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier =
                Modifier
                    .width(34.dp)
                    .height(34.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(if (selected) Cyan.copy(alpha = 0.14f) else Color(0xFF07152F))
                    .border(
                        1.dp,
                        if (selected) Cyan.copy(alpha = 0.72f) else Cyan.copy(alpha = 0.18f),
                        RoundedCornerShape(9.dp),
                    ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(id = tab.iconRes()),
                contentDescription = tab.label(),
                tint = if (selected) Cyan else TextSecondary,
                modifier =
                    Modifier
                        .width(20.dp)
                        .height(20.dp),
            )
        }
        Text(
            text = tab.label(),
            color = if (selected) Cyan else TextSecondary,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold,
        )
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .clip(RoundedCornerShape(99.dp))
                    .background(if (selected) Cyan else Color.Transparent),
        )
    }
}

@Composable
private fun WaitingTransferSortToggle(
    mode: TransferDisplayMode,
    onModeChange: (TransferDisplayMode) -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(PanelDeep)
                .border(1.dp, Cyan.copy(alpha = 0.28f), RoundedCornerShape(10.dp))
                .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "待传锅排序",
            color = TextSecondary,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SortModeSegment(
                text = "老单优先",
                selected = mode == TransferDisplayMode.OldestFirst,
                onClick = { onModeChange(TransferDisplayMode.OldestFirst) },
            )
            SortModeSegment(
                text = "新单优先",
                selected = mode == TransferDisplayMode.LatestFirst,
                onClick = { onModeChange(TransferDisplayMode.LatestFirst) },
            )
        }
    }
}

@Composable
private fun SortModeSegment(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .height(32.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(if (selected) Cyan.copy(alpha = 0.18f) else Color.Transparent)
                .border(
                    1.dp,
                    if (selected) Cyan.copy(alpha = 0.85f) else Cyan.copy(alpha = 0.28f),
                    RoundedCornerShape(999.dp),
                ).clickable { onClick() }
                .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = if (selected) Cyan else TextSecondary,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun OrderActionChip(
    text: String,
    color: Color,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .height(34.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(color.copy(alpha = if (enabled) 0.18f else 0.1f))
                .border(
                    1.dp,
                    color.copy(alpha = if (enabled) 0.9f else 0.38f),
                    RoundedCornerShape(999.dp),
                ).clickable(enabled = enabled) { onClick() }
                .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = color,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold,
        )
    }
}

private data class OrderTimeDetails(
    val primary: OrderTimestampBlockModel,
    val secondary: OrderTimestampBlockModel? = null,
)

private data class OrderTimestampBlockModel(
    val label: String,
    val date: String,
    val time: String,
)

private fun Order.timeDetailsFor(tab: OrdersPageTab): OrderTimeDetails {
    val primary = timestampBlock("下单", fullOrderTimeText())
    val secondary =
        when (tab) {
            OrdersPageTab.PendingWater -> null
            OrdersPageTab.WaitingTransfer -> timestampBlock("加水", waterCompletedAt.toFullDateTimeText())
            OrdersPageTab.Cancelled -> timestampBlock("取消", cancelledAt.toFullDateTimeText())
            OrdersPageTab.Completed -> timestampBlock("传锅", transferCompletedAt.toFullDateTimeText())
        }
    return OrderTimeDetails(primary = primary, secondary = secondary)
}

private fun timestampBlock(
    label: String,
    value: String,
): OrderTimestampBlockModel {
    if (value == "--") {
        return OrderTimestampBlockModel(label = label, date = "--", time = "--:--:--")
    }
    val date =
        value
            .substringBeforeLast(" ", missingDelimiterValue = value)
            .replace("-", "/")
    val time =
        value
            .substringAfterLast(" ", missingDelimiterValue = "")
            .ifBlank { "--:--:--" }
    return OrderTimestampBlockModel(label = label, date = date, time = time)
}

private fun Order.potBottomListLines(structureIssue: String?): List<String> {
    val source =
        structureIssue
            ?: potBottomSummary?.takeIf { it.isNotBlank() }
            ?: potBottomName
            ?: recipeCode
            ?: "--"
    if (structureIssue != null) {
        return listOf(source)
    }
    return source
        .split(Regex("[,，、\\r\\n]+"))
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .ifEmpty { listOf("--") }
}

@Suppress("LongMethod")
@Composable
private fun OrderDetailDialog(
    order: Order,
    formulaCatalog: com.example.plccontroller.domain.FormulaCatalog?,
    onDismiss: () -> Unit,
) {
    val presentedGrid = remember(order, formulaCatalog) {
        com.example.plccontroller.ui.potdetail.PotDetailPresenter.presentOrder(
            order = order,
            formulaCatalog = formulaCatalog,
            forcedTopLeftLogicalSlot = null,
            currentTopLeftLogicalSlot = null,
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "订单详情 - 桌号 ${order.tableCode ?: "--"}",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                )
                Text(
                    text = "单号: ${order.id}",
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.width(600.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "锅型: ${order.potMode.label()}",
                            color = TextPrimary,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = "操作员: ${order.operator ?: "--"}",
                            color = TextPrimary,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        val operationLabel = order.operationLabel()
                        if (operationLabel.isNotBlank() &&
                            operationLabel != "下单" &&
                            operationLabel != "--"
                        ) {
                            Text(
                                text = "变更状态: $operationLabel",
                                color = Yellow,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        val timeText = when (order.status) {
                            com.example.plccontroller.domain.OrderStatus.Completed -> {
                                val time = order.transferCompletedAt ?: order.statusUpdatedAt ?: "--"
                                "完成时间: $time"
                            }
                            com.example.plccontroller.domain.OrderStatus.Cancelled -> {
                                val time = order.cancelledAt ?: order.statusUpdatedAt ?: "--"
                                "取消时间: $time"
                            }
                            else -> "更新时间: ${order.statusUpdatedAt ?: "--"}"
                        }
                        Text(timeText, color = TextPrimary, style = MaterialTheme.typography.bodyMedium)
                    }
                }

                Spacer(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(BorderBlue.copy(alpha = 0.2f))
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(280.dp)
                ) {
                    PotDetailGrid(
                        layoutMode = presentedGrid.layoutMode,
                        cards = presentedGrid.cards,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("关闭", color = Cyan, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
        },
        shape = RoundedCornerShape(16.dp),
        containerColor = PanelDeep,
    )
}

@Preview(widthDp = 800, heightDp = 1280, showBackground = true)
@Composable
private fun OrdersScreenPreview() {
    val mockOrders =
        listOf(
            Order(
                id = "1001",
                recipeCode = "R1",
                quantity = 1,
                targetTemperature = 90,
                cookSeconds = 60,
                spiceLevel = 1,
                tableCode = "A01",
                potBottomName = "番茄锅",
                status = com.example.plccontroller.domain.OrderStatus.PendingWater,
                operator = "尚鹤杰",
                operation = "301",
                potBottomSummary = "海底捞双料真香锅,清水锅,清水锅,浓浓浓菌汤锅",
            ),
            Order(
                id = "1002",
                recipeCode = "R2",
                quantity = 1,
                targetTemperature = 90,
                cookSeconds = 60,
                spiceLevel = 1,
                tableCode = "B05",
                potBottomName = "麻辣锅",
                status = com.example.plccontroller.domain.OrderStatus.PendingWater,
                operator = "董盼盼",
                operation = "301",
                potBottomSummary = "猪肚鸡火锅,清水锅,清水锅,清水锅",
            ),
        )
    MaterialTheme {
        Box(modifier = Modifier.background(PageBackground)) {
            OrdersScreen(
                pendingOrders = mockOrders,
                waitingTransferOrders = emptyList(),
                currentOrder = null,
                completedOrders = emptyList(),
                cancelledOrders = emptyList(),
                onRefreshOrders = {},
                onCancelPendingOrder = {},
                onRewaterOrder = {},
            )
        }
    }
}
