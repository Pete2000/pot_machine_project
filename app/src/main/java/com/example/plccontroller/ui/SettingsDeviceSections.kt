package com.example.plccontroller.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
internal fun DevicePollingSettingsBlock(
    pollingItems: List<DeviceSettingEditor>,
    onEdit: (DeviceSettingEditor) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionTitle(title = "轮询控制", code = "POLLING")
        DeviceSettingsGridGroup(items = pollingItems, onEdit = onEdit)
    }
}

@Composable
internal fun DeviceInfoSection(
    title: String,
    code: String,
    items: List<SettingsFieldModel>,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle(title = title, code = code)
        DeviceInfoGrid(items)
    }
}

@Composable
private fun DeviceInfoGrid(items: List<SettingsFieldModel>) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items.chunked(3).forEach { rowItems ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                rowItems.forEach { item ->
                    SettingsFieldCard(
                        model = item,
                        modifier = Modifier.weight(1f).height(112.dp),
                    )
                }
                if (rowItems.size < 3) {
                    repeat(3 - rowItems.size) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
internal fun DeviceSettingsSection(
    title: String,
    code: String,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle(title = title, code = code)
        content()
    }
}

@Composable
internal fun DeviceSettingsGridGroup(
    items: List<DeviceSettingEditor>,
    columns: Int = 3,
    onEdit: (DeviceSettingEditor) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items.chunked(columns).forEach { rowItems ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                rowItems.forEach { item ->
                    SettingsFieldCard(
                        model =
                            SettingsFieldModel(
                                label = item.label,
                                value = item.displayValue().ifBlank { "--" },
                                detail = item.detail,
                                accent = item.accent,
                            ),
                        onClick =
                            if (item.enabled) {
                                { onEdit(item) }
                            } else {
                                null
                            },
                        modifier = Modifier.weight(1f).height(120.dp),
                    )
                }
                if (rowItems.size < columns) {
                    repeat(columns - rowItems.size) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}
