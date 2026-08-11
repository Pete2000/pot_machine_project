package com.example.plccontroller.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.plccontroller.R

/**
 * 底部导航栏组件
 *
 * @param selectedTab 当前选中的 Tab
 * @param onSelect 选中 Tab 时的回调
 */
@Composable
internal fun BottomTabBar(
    selectedTab: BottomTab,
    isWateringActive: Boolean = false,
    onSelect: (BottomTab) -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(80.dp)
                .background(Color(0xFF030D22))
                .border(1.dp, Color.White.copy(alpha = 0.08f)),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BottomTabItem("首页", BottomTab.Home, selectedTab, isWateringActive, onSelect, Modifier.weight(1f))
        BottomTabItem("订单", BottomTab.Orders, selectedTab, isWateringActive, onSelect, Modifier.weight(1f))
        BottomTabItem("设置", BottomTab.Settings, selectedTab, isWateringActive, onSelect, Modifier.weight(1f))
    }
}

@Composable
private fun BottomTabItem(
    label: String,
    tab: BottomTab,
    selectedTab: BottomTab,
    isWateringActive: Boolean,
    onSelect: (BottomTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val isSelected = tab == selectedTab
    val contentColor =
        when {
            isSelected -> Cyan
            isWateringActive -> TextSecondary.copy(alpha = 0.4f)
            else -> TextSecondary
        }

    Box(
        modifier =
            modifier
                .fillMaxHeight()
                .clickable(enabled = !isWateringActive) { onSelect(tab) },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            BottomTabIcon(tab, contentColor)
            Text(
                text = label,
                color = contentColor,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            )
        }

        if (isSelected) {
            Box(
                modifier =
                    Modifier
                        .align(Alignment.BottomCenter)
                        .width(48.dp)
                        .height(4.dp)
                        .background(Cyan, RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp)),
            )
        }
    }
}

@Composable
private fun BottomTabIcon(
    tab: BottomTab,
    tint: Color,
) {
    val iconRes =
        when (tab) {
            BottomTab.Home -> R.drawable.ic_tab_home
            BottomTab.Orders -> R.drawable.ic_tab_orders
            BottomTab.Settings -> R.drawable.ic_tab_settings
        }
    Icon(
        painter = painterResource(id = iconRes),
        contentDescription = null,
        modifier = Modifier.size(32.dp),
        tint = tint,
    )
}
