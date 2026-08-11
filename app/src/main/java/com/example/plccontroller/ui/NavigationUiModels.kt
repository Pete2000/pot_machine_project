package com.example.plccontroller.ui

import androidx.compose.ui.graphics.Color
import com.example.plccontroller.R

internal enum class BottomTab {
    Home,
    Orders,
    Settings,
}

internal enum class OrdersPageTab {
    Completed,
    PendingWater,
    WaitingTransfer,
    Cancelled,
}

internal enum class SettingsTab {
    Access,
    Formula,
    Device,
    Maintenance,
    Debug,
}

internal enum class HomeMode {
    Receive,
    Manual,
}

internal fun BottomTab.iconRes(): Int =
    when (this) {
        BottomTab.Home -> R.drawable.ic_tab_home
        BottomTab.Orders -> R.drawable.ic_tab_orders
        BottomTab.Settings -> R.drawable.ic_tab_settings
    }

internal fun BottomTab.label(): String =
    when (this) {
        BottomTab.Home -> "首页"
        BottomTab.Orders -> "订单"
        BottomTab.Settings -> "设置"
    }

internal fun OrdersPageTab.label(): String =
    when (this) {
        OrdersPageTab.Completed -> "已完成"
        OrdersPageTab.PendingWater -> "待加水"
        OrdersPageTab.WaitingTransfer -> "待传锅"
        OrdersPageTab.Cancelled -> "已取消"
    }

internal fun OrdersPageTab.emptyText(): String =
    when (this) {
        OrdersPageTab.Completed -> "暂无已完成订单"
        OrdersPageTab.PendingWater -> "暂无待加水订单"
        OrdersPageTab.WaitingTransfer -> "暂无待传锅订单"
        OrdersPageTab.Cancelled -> "暂无已取消订单"
    }

internal fun OrdersPageTab.actionText(): String =
    when (this) {
        OrdersPageTab.Completed -> "查看"
        OrdersPageTab.PendingWater -> "取消"
        OrdersPageTab.WaitingTransfer -> "重新加水"
        OrdersPageTab.Cancelled -> "重新加水"
    }

internal fun OrdersPageTab.actionColor(): Color =
    when (this) {
        OrdersPageTab.Completed -> Green
        OrdersPageTab.PendingWater -> Red
        OrdersPageTab.WaitingTransfer -> Cyan
        OrdersPageTab.Cancelled -> Cyan
    }

internal fun OrdersPageTab.iconRes(): Int =
    when (this) {
        OrdersPageTab.Completed -> R.drawable.ic_order_completed
        OrdersPageTab.PendingWater -> R.drawable.ic_order_pending_water
        OrdersPageTab.WaitingTransfer -> R.drawable.ic_order_waiting_transfer
        OrdersPageTab.Cancelled -> R.drawable.ic_order_cancelled
    }

internal fun SettingsTab.label(): String =
    when (this) {
        SettingsTab.Access -> "接入"
        SettingsTab.Formula -> "配方"
        SettingsTab.Device -> "设备"
        SettingsTab.Maintenance -> "维护"
        SettingsTab.Debug -> "调试"
    }

internal fun SettingsTab.iconRes(): Int =
    when (this) {
        SettingsTab.Access -> R.drawable.ic_settings_access
        SettingsTab.Formula -> R.drawable.ic_settings_formula
        SettingsTab.Device -> R.drawable.ic_settings_device
        SettingsTab.Maintenance -> R.drawable.ic_settings_maintenance
        SettingsTab.Debug -> R.drawable.ic_settings_debug
    }
