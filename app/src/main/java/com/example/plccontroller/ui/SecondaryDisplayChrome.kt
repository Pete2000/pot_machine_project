package com.example.plccontroller.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.plccontroller.runtime.TransferDisplayMode

/**
 * 副屏顶部状态栏
 */
@Composable
internal fun SecondaryHeader(
    state: SecondaryDisplayUiState,
    onRefresh: () -> Unit,
    showDebugTools: Boolean,
    onLoadDemoOrders: () -> Unit,
    onClearDemoOrders: () -> Unit,
) {
    HmiPanel(padding = 6.dp) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CompactStatusCapsule(
                    text = state.transferDeck.mode.statusLabel(),
                    color = Cyan,
                )
                CompactStatusCapsule(
                    text = "待传锅 ${state.transferDeck.totalWaitingCount}",
                    color = Yellow,
                )
                if (state.demoModeEnabled) {
                    CompactStatusCapsule(
                        text = "演示模式",
                        color = Red,
                    )
                }
                if (state.transferDeck.overflowCount > 0) {
                    CompactStatusCapsule(
                        text = "超出 ${state.transferDeck.overflowCount}",
                        color = Red,
                    )
                }
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (showDebugTools) {
                    CompactActionButton(
                        text = "演示7单",
                        onClick = onLoadDemoOrders,
                    )
                    CompactActionButton(
                        text = "清空演示",
                        onClick = onClearDemoOrders,
                        enabled = state.demoModeEnabled,
                    )
                }
                CompactActionButton(
                    text = "刷新",
                    onClick = onRefresh,
                )
            }
        }
    }
}

/**
 * 副屏 Tab 切换行
 */
@Composable
internal fun SecondaryTabRow(
    selectedTab: SecondaryTab,
    onSelect: (SecondaryTab) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SecondaryTabItem(
            iconRes = OrdersPageTab.WaitingTransfer.iconRes(),
            label = "待传锅",
            selected = selectedTab == SecondaryTab.WaitingTransfer,
            onClick = { onSelect(SecondaryTab.WaitingTransfer) },
            modifier = Modifier.weight(1f),
        )
        SecondaryTabItem(
            iconRes = OrdersPageTab.Completed.iconRes(),
            label = "已完成",
            selected = selectedTab == SecondaryTab.Completed,
            onClick = { onSelect(SecondaryTab.Completed) },
            modifier = Modifier.weight(1f),
        )
    }
}

/**
 * 副屏 Tab 单项，采用极致压缩高度布局
 */
@Composable
private fun SecondaryTabItem(
    iconRes: Int,
    label: String,
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
                .padding(top = 2.dp, bottom = 1.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Box(
            modifier =
                Modifier
                    .width(28.dp)
                    .height(28.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (selected) Cyan.copy(alpha = 0.14f) else Color(0xFF07152F))
                    .border(
                        1.dp,
                        if (selected) Cyan.copy(alpha = 0.72f) else Cyan.copy(alpha = 0.18f),
                        RoundedCornerShape(10.dp),
                    ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(id = iconRes),
                contentDescription = label,
                tint = if (selected) Cyan else TextSecondary,
                modifier =
                    Modifier
                        .width(16.dp)
                        .height(16.dp),
            )
        }
        Text(
            text = label,
            color = if (selected) Cyan else TextSecondary,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
        )
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .clip(RoundedCornerShape(99.dp))
                    .background(if (selected) Cyan else Color.Transparent),
        )
    }
}

@Composable
private fun CompactStatusCapsule(
    text: String,
    color: Color,
) {
    Text(
        text = text,
        color = color,
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.Bold,
        modifier =
            Modifier
                .clip(RoundedCornerShape(999.dp))
                .background(color.copy(alpha = 0.12f))
                .border(1.dp, color.copy(alpha = 0.75f), RoundedCornerShape(999.dp))
                .padding(horizontal = 10.dp, vertical = 5.dp),
    )
}

@Composable
private fun CompactActionButton(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    Box(
        modifier =
            Modifier
                .height(32.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(if (enabled) Color(0xFF062E58) else Color(0xFF172642))
                .border(
                    1.dp,
                    if (enabled) Cyan.copy(alpha = 0.18f) else BorderBlue.copy(alpha = 0.24f),
                    RoundedCornerShape(8.dp),
                ).then(if (enabled) Modifier.clickable { onClick() } else Modifier)
                .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = if (enabled) TextPrimary else TextSecondary,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
        )
    }
}

private fun TransferDisplayMode.statusLabel(): String =
    when (this) {
        TransferDisplayMode.OldestFirst -> "左下最老"
        TransferDisplayMode.LatestFirst -> "左下最新"
    }
