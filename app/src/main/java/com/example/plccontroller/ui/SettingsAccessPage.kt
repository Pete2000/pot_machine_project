package com.example.plccontroller.ui

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.plccontroller.data.SettingsStore
import com.example.plccontroller.domain.PlcConnectionState
import com.example.plccontroller.runtime.NetworkConnectionState

@Composable
internal fun AccessSettingsContent(
    networkState: NetworkConnectionState,
    plcState: PlcConnectionState,
    businessUrl: String,
    managementUrl: String,
    deviceCode: String,
    registrationState: RegistrationState,
    onStartRegistration: () -> Unit,
    onSelectDeviceType: (String) -> Unit,
    onSelectEquipment: (com.example.plccontroller.data.http.EquipmentDto) -> Unit,
    onResetRegistrationState: () -> Unit,
    onUpdateSettings: ((SettingsStore) -> Unit) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var editing by remember { mutableStateOf<AccessEditTarget?>(null) }
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        SectionTitle(title = "连接拓扑", code = "TOPOLOGY")
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Panel)
                    .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(16.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                TopologyNode(label = "云端业务", active = networkState == NetworkConnectionState.Online)
                TopologyLink(active = networkState == NetworkConnectionState.Online)
                TopologyNode(label = "本机控制器", active = true, isMain = true)
                TopologyLink(active = plcState == PlcConnectionState.Connected)
                TopologyNode(label = "PLC 硬件", active = plcState == PlcConnectionState.Connected)
            }
        }

        SectionTitle(title = "身份与接口", code = "IDENTITY")
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SettingsFieldCard(
                model =
                    SettingsFieldModel(
                        label = "设备编码 (Device Code)",
                        value = deviceCode.ifBlank { "未生成/未绑定" },
                        detail = "本机硬件唯一识别码",
                        accent = Cyan,
                    ),
                onClick = {
                    editing =
                        AccessEditTarget(
                            title = "设备编码",
                            initialValue = deviceCode,
                            onSave = { value -> onUpdateSettings { it.updateDeviceCode(value.trim()) } },
                        )
                },
            )
            SettingsFieldCard(
                model =
                    SettingsFieldModel(
                        label = "业务接口 (Business API)",
                        value = businessUrl,
                        detail = "生产订单数据同步地址",
                        accent = Blue,
                    ),
                onClick = {
                    editing =
                        AccessEditTarget(
                            title = "业务接口",
                            initialValue = businessUrl,
                            onSave = { value -> onUpdateSettings { it.updateBusinessUrl(value.trim()) } },
                        )
                },
            )
            SettingsFieldCard(
                model =
                    SettingsFieldModel(
                        label = "管理后台 (Management)",
                        value = managementUrl,
                        detail = "设备远程运维与参数配置平台",
                        accent = Blue,
                    ),
                onClick = {
                    editing =
                        AccessEditTarget(
                            title = "管理后台",
                            initialValue = managementUrl,
                            onSave = { value -> onUpdateSettings { it.updateManagementUrl(value.trim()) } },
                        )
                },
            )
            Button(
                onClick = onStartRegistration,
                colors = ButtonDefaults.buttonColors(containerColor = Blue),
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(52.dp),
            ) {
                Text(text = "注册并绑定设备 (两步走流程)", fontWeight = FontWeight.Bold)
            }
        }
    }

    editing?.let { target ->
        AccessEditDialog(
            target = target,
            onDismiss = { editing = null },
        )
    }

    RegistrationWizard(
        state = registrationState,
        onSelectDeviceType = onSelectDeviceType,
        onSelectEquipment = onSelectEquipment,
        onReset = onResetRegistrationState,
    )
}

private data class AccessEditTarget(
    val title: String,
    val initialValue: String,
    val onSave: (String) -> Unit,
)

@Composable
private fun AccessEditDialog(
    target: AccessEditTarget,
    onDismiss: () -> Unit,
) {
    var value by remember(target.title, target.initialValue) {
        mutableStateOf(target.initialValue)
    }
    var errorText by remember { mutableStateOf<String?>(null) }
    val isUrl = target.title == "业务接口" || target.title == "管理后台"

    androidx.compose.runtime.LaunchedEffect(value) {
        val trimmed = value.trim()
        errorText = when {
            trimmed.isEmpty() -> "${target.title}不能为空"
            isUrl && !trimmed.matches(Regex("^https?://.*$")) && !trimmed.matches(Regex("^[0-9a-zA-Z.:/-]+$")) -> "地址格式不正确，应为合法的网络路径"
            else -> null
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "编辑${target.title}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    singleLine = true,
                    label = { Text(text = target.title) },
                    isError = errorText != null,
                    colors =
                        OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedContainerColor = Color(0xFF09245A),
                            unfocusedContainerColor = Color(0xFF09245A),
                            cursorColor = Yellow,
                            focusedBorderColor = Yellow,
                            unfocusedBorderColor = Cyan.copy(alpha = 0.5f),
                            focusedLabelColor = Yellow,
                            unfocusedLabelColor = TextSecondary,
                        ),
                    modifier = Modifier.fillMaxWidth()
                )
                errorText?.let {
                    Text(text = it, color = Red, style = MaterialTheme.typography.bodySmall)
                }
                if (isUrl && errorText == null && !value.trim().startsWith("http://") && !value.trim().startsWith("https://")) {
                    Text(text = "提示：保存时将自动补全 http:// 前缀", color = Yellow, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = errorText == null,
                onClick = {
                    val trimmed = value.trim()
                    val finalValue = if (isUrl && !trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
                        "http://$trimmed"
                    } else {
                        trimmed
                    }
                    target.onSave(finalValue)
                    onDismiss()
                },
            ) {
                Text(text = "保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = "取消")
            }
        },
        containerColor = Panel,
        titleContentColor = TextPrimary,
        textContentColor = TextSecondary,
    )
}

@Composable
private fun TopologyNode(
    label: String,
    active: Boolean,
    isMain: Boolean = false,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(
            modifier =
                Modifier
                    .size(if (isMain) 56.dp else 44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (active) (if (isMain) Blue else Green).copy(alpha = 0.2f) else Color.White.copy(alpha = 0.05f))
                    .border(2.dp, if (active) (if (isMain) Blue else Green) else Color.Gray.copy(alpha = 0.3f), RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier =
                    Modifier
                        .size(
                            if (isMain) 20.dp else 14.dp,
                        ).clip(RoundedCornerShape(99.dp))
                        .background(if (active) (if (isMain) Blue else Green) else Color.Gray),
            )
        }
        Text(
            text = label,
            color = if (active) TextPrimary else TextSecondary,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun TopologyLink(active: Boolean) {
    Box(
        modifier =
            Modifier
                .width(40.dp)
                .height(2.dp)
                .background(if (active) Green else Color.Gray.copy(alpha = 0.2f)),
    )
}

@Composable
private fun RegistrationWizard(
    state: RegistrationState,
    onSelectDeviceType: (String) -> Unit,
    onSelectEquipment: (com.example.plccontroller.data.http.EquipmentDto) -> Unit,
    onReset: () -> Unit,
) {
    Log.d("RegistrationUI", "RegistrationWizard recomposed with state: $state")
    when (state) {
        RegistrationState.Idle -> { /* Do nothing */ }

        RegistrationState.LoadingTypes -> {
            AlertDialog(
                onDismissRequest = {},
                confirmButton = {},
                title = { Text("获取设备类型", fontWeight = FontWeight.Bold) },
                text = {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(color = Cyan)
                        Text("正在从云端读取设备类型，请稍候...", color = TextSecondary)
                    }
                },
                containerColor = Panel,
                titleContentColor = TextPrimary,
                textContentColor = TextSecondary,
            )
        }

        is RegistrationState.SelectingType -> {
            AlertDialog(
                onDismissRequest = onReset,
                title = { Text("请选择设备类型", fontWeight = FontWeight.Bold) },
                text = {
                    Column(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .heightIn(max = 300.dp)
                                .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        state.types.forEach { type ->
                            Box(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(0xFF061735))
                                        .border(1.dp, Cyan.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                                        .clickable { onSelectDeviceType(type.deviceTypeCode) }
                                        .padding(16.dp),
                            ) {
                                Text(
                                    text = type.deviceTypeName,
                                    color = TextPrimary,
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.bodyLarge,
                                )
                            }
                        }
                    }
                },
                confirmButton = {},
                dismissButton = {
                    TextButton(onClick = onReset) {
                        Text("取消", color = TextSecondary)
                    }
                },
                containerColor = Panel,
                titleContentColor = TextPrimary,
            )
        }

        RegistrationState.LoadingEquipment -> {
            AlertDialog(
                onDismissRequest = {},
                confirmButton = {},
                title = { Text("获取设备列表", fontWeight = FontWeight.Bold) },
                text = {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(color = Cyan)
                        Text("正在读取该类型下的物理机器列表，请稍候...", color = TextSecondary)
                    }
                },
                containerColor = Panel,
                titleContentColor = TextPrimary,
                textContentColor = TextSecondary,
            )
        }

        is RegistrationState.SelectingEquipment -> {
            AlertDialog(
                onDismissRequest = onReset,
                title = { Text("请选择物理设备", fontWeight = FontWeight.Bold) },
                text = {
                    Column(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .heightIn(max = 300.dp)
                                .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        state.equipments.forEach { equipment ->
                            Box(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(0xFF061735))
                                        .border(1.dp, Cyan.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                                        .clickable { onSelectEquipment(equipment) }
                                        .padding(16.dp),
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(
                                        text = equipment.equipmentName,
                                        color = TextPrimary,
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.bodyLarge,
                                    )
                                    Text(
                                        text = "编码: ${equipment.deviceCode}",
                                        color = TextSecondary,
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                }
                            }
                        }
                    }
                },
                confirmButton = {},
                dismissButton = {
                    TextButton(onClick = onReset) {
                        Text("取消", color = TextSecondary)
                    }
                },
                containerColor = Panel,
                titleContentColor = TextPrimary,
            )
        }

        RegistrationState.Saving -> {
            AlertDialog(
                onDismissRequest = {},
                confirmButton = {},
                title = { Text("绑定设备", fontWeight = FontWeight.Bold) },
                text = {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(color = Cyan)
                        Text("正在持久化设备身份并同步配方参数...", color = TextSecondary)
                    }
                },
                containerColor = Panel,
                titleContentColor = TextPrimary,
                textContentColor = TextSecondary,
            )
        }

        is RegistrationState.Success -> {
            AlertDialog(
                onDismissRequest = onReset,
                title = { Text("注册成功", fontWeight = FontWeight.Bold, color = Green) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("设备已成功与本地上位机系统绑定！", color = TextPrimary)
                        Column(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFF061735))
                                    .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text("绑定机器: ${state.equipmentName}", color = TextPrimary, fontWeight = FontWeight.Bold)
                            Text("设备编码: ${state.deviceCode}", color = Cyan, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = onReset) {
                        Text("确定", fontWeight = FontWeight.Bold, color = Cyan)
                    }
                },
                containerColor = Panel,
                titleContentColor = TextPrimary,
                textContentColor = TextSecondary,
            )
        }

        is RegistrationState.Error -> {
            AlertDialog(
                onDismissRequest = onReset,
                title = { Text("绑定失败", fontWeight = FontWeight.Bold, color = Red) },
                text = {
                    Text(state.message, color = TextPrimary)
                },
                confirmButton = {
                    TextButton(onClick = onReset) {
                        Text("关闭", fontWeight = FontWeight.Bold, color = TextSecondary)
                    }
                },
                containerColor = Panel,
                titleContentColor = TextPrimary,
                textContentColor = TextSecondary,
            )
        }
    }
}
