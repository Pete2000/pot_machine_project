package com.example.plccontroller.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp

/**
 * 副屏 Tab 枚举：待传锅列表与已完成列表
 */
internal enum class SecondaryTab {
    WaitingTransfer,
    Completed,
}

/**
 * 副屏显示主入口 Composable
 *
 * @param viewModel 副屏状态逻辑控制器
 * @param showDebugTools 是否显示调试工具（如演示订单）
 */
@Composable
internal fun SecondaryDisplayScreen(
    viewModel: SecondaryDisplayViewModel,
    showDebugTools: Boolean = false,
) {
    val state by viewModel.uiState.collectAsState()
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.screenWidthDp >= configuration.screenHeightDp
    var selectedTab by remember { mutableStateOf(SecondaryTab.WaitingTransfer) }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(PageBackground)
                .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        SecondaryHeader(
            state = state,
            onRefresh = viewModel::refreshOrders,
            showDebugTools = showDebugTools,
            onLoadDemoOrders = viewModel::loadDemoOrders,
            onClearDemoOrders = viewModel::clearDemoOrders,
        )
        SecondaryTabRow(
            selectedTab = selectedTab,
            onSelect = { selectedTab = it },
        )
        when (selectedTab) {
            SecondaryTab.WaitingTransfer ->
                WaitingTransferDeckPanel(
                    state = state,
                    onConfirmTransfer = viewModel::confirmTransfer,
                    isLandscape = isLandscape,
                    modifier = Modifier.weight(1f),
                )

            SecondaryTab.Completed ->
                CompletedOrdersPanel(
                    completedOrders = state.completedOrders,
                    modifier = Modifier.weight(1f),
                )
        }
    }
}
