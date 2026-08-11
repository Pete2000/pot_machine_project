package com.example.plccontroller.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.plccontroller.data.AppPersistentConfig
import com.example.plccontroller.domain.PlcCommunicationConfig
import com.example.plccontroller.domain.PlcPollingSnapshot

@OptIn(ExperimentalFoundationApi::class)
private fun communicationSummaryShort(config: PlcCommunicationConfig?): String {
    if (config == null) return "--"
    return "${config.baudRate}, ${config.dataBits}${config.parityName.firstOrNull() ?: 'N'}${config.stopBits}"
}

@Composable
internal fun DeviceSettingsContent(
    communicationConfig: PlcCommunicationConfig?,
    formulaSyncIntervalSeconds: Long,
    deviceConfig: AppPersistentConfig?,
    plcPollingSnapshot: PlcPollingSnapshot,
    onUpdateSettings: ((com.example.plccontroller.data.SettingsStore) -> Unit) -> Unit,
    onBeginHeaterActuatorConfigEdit: () -> Unit,
    onCancelHeaterActuatorConfigEdit: () -> Unit,
    onSaveHeaterActuatorConfig: ((com.example.plccontroller.data.SettingsStore) -> Unit) -> Unit,
    modifier: Modifier = Modifier,
) {
    val config = communicationConfig
    val device = deviceConfig
    val heaterActuatorBusy = plcPollingSnapshot.isHeaterActuatorConfigBusy()
    val pumpActuatorBusy = plcPollingSnapshot.isPumpActuatorConfigBusy()
    var editing by remember(config) { mutableStateOf<DeviceSettingEditor?>(null) }

    val communicationGroup = communicationParameterEditors(config)
    val pollingGroup = pollingParameterEditors(config, formulaSyncIntervalSeconds)
    val timeoutGroup = timeoutParameterEditors(config)
    val runningGroup = runningParameterEditors(device)
    val calibrationGroup = calibrationParameterEditors(device)
    val actuatorGroup =
        actuatorConfigEditors(
            config = device,
            heaterConfigBusy = heaterActuatorBusy,
            pumpConfigBusy = pumpActuatorBusy,
        )

    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            DeviceSettingsSection("运行参数", "RUNNING") {
                DeviceSettingsGridGroup(items = runningGroup, columns = 2, onEdit = { editing = it })
            }
        }
        item {
            DeviceSettingsSection("执行器配置", "ACTUATOR") {
                DeviceSettingsGridGroup(
                    items = actuatorGroup,
                    columns = 2,
                    onEdit = { editor ->
                        if (editor.enabled) {
                            editing = editor
                            if (editor.key == DeviceSettingKey.DeviceHeaterConfigComposite) {
                                onBeginHeaterActuatorConfigEdit()
                            }
                        }
                    },
                )
            }
        }
        item { DeviceInfoSection("本机显示", "DISPLAY", displayParameterCards()) }
        item {
            CollapsibleSection(title = "校准参数", code = "CALIBRATION", initiallyExpanded = false) {
                DeviceSettingsSection("", "") {
                    DeviceSettingsGridGroup(items = calibrationGroup, columns = 1, onEdit = { editing = it })
                }
            }
        }
        item {
            CollapsibleSection(title = "高级设置", code = "ADVANCED") {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    DevicePollingSettingsBlock(
                        pollingItems = pollingGroup,
                        onEdit = { editing = it },
                    )
                    DeviceSettingsSection("PLC 通信参数", "COMMUNICATION") {
                        DeviceSettingsGridGroup(items = communicationGroup, onEdit = { editing = it })
                    }
                    DeviceSettingsSection("容错机制", "TIMEOUT") {
                        DeviceSettingsGridGroup(items = timeoutGroup, onEdit = { editing = it })
                    }
                }
            }
        }
    }

    editing?.let { editor ->
        DeviceSettingEditDialog(
            editor = editor,
            onDismiss = {
                if (editor.key == DeviceSettingKey.DeviceHeaterConfigComposite) {
                    onCancelHeaterActuatorConfigEdit()
                }
                editing = null
            },
            onConfirm = { newValue ->
                val updateAction: (com.example.plccontroller.data.SettingsStore) -> Unit = { store ->
                    applyDeviceSettingUpdateToStore(editor, newValue.trim(), store)
                }
                if (editor.key == DeviceSettingKey.DeviceHeaterConfigComposite) {
                    onSaveHeaterActuatorConfig(updateAction)
                } else {
                    applyDeviceSettingUpdate(editor, newValue, onUpdateSettings)
                }
                editing = null
            },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
private fun DeviceSettingEditDialog(
    editor: DeviceSettingEditor,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    val compositeParts = remember(editor) { editor.value.split("|") }
    val fieldLabels =
        when (editor.key) {
            DeviceSettingKey.DeviceHeaterComposite -> listOf("目标温度 (°C)", "回差温度 (°C)")
            DeviceSettingKey.DeviceStandaloneWaterComposite -> listOf("加水模式", "时控时间 (秒)")
            DeviceSettingKey.DeviceWaterCalibrationComposite -> listOf("出水口1 (%)", "出水口2 (%)", "出水口3 (%)", "出水口4 (%)")
            DeviceSettingKey.DevicePumpCorrectionComposite -> listOf("鸡油修正 (%)", "骨膏修正 (%)")
            DeviceSettingKey.DeviceHeaterConfigComposite -> listOf("加热管选择", "传感器选择")
            else -> null
        }

    var inputs by remember(editor) {
        mutableStateOf(
            if (fieldLabels != null) {
                List(fieldLabels.size) { compositeParts.getOrNull(it) ?: "" }
            } else {
                listOf(editor.value)
            },
        )
    }

    var errorText by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(inputs) {
        errorText =
            when (editor.key) {
                DeviceSettingKey.DeviceHeaterComposite -> {
                    val temp = inputs.getOrNull(0)?.toIntOrNull()
                    val hyst = inputs.getOrNull(1)?.toIntOrNull()
                    when {
                        temp == null || temp !in 0..120 -> "目标温度必须在 0 ~ 120 ℃ 之间"
                        hyst == null || hyst !in 0..30 -> "回差温度必须在 0 ~ 30 ℃ 之间"
                        else -> null
                    }
                }
                DeviceSettingKey.DeviceStandaloneWaterComposite -> {
                    val mode = inputs.getOrNull(0)?.toIntOrNull()
                    val secStr = inputs.getOrNull(1) ?: ""
                    val secVal = secStr.toDoubleOrNull()
                    when {
                        mode == null || mode !in 0..2 -> "未选择合法的加水模式"
                        mode == 2 && (secVal == null || secVal !in 0.1..360.0) -> "时控时间必须在 0.1 ~ 360.0 秒之间"
                        else -> null
                    }
                }
                DeviceSettingKey.DeviceWaterCalibrationComposite -> {
                    val errIdx = inputs.indexOfFirst { it.toIntOrNull()?.let { p -> p !in 50..200 } ?: true }
                    if (errIdx >= 0) "出水口 ${errIdx + 1} 校准系数必须在 50% ~ 200% 之间" else null
                }
                DeviceSettingKey.DevicePumpCorrectionComposite -> {
                    val oil = inputs.getOrNull(0)?.toIntOrNull()
                    val paste = inputs.getOrNull(1)?.toIntOrNull()
                    when {
                        oil == null || oil !in 50..200 -> "鸡油修正系数必须在 50% ~ 200% 之间"
                        paste == null || paste !in 50..200 -> "骨膏修正系数必须在 50% ~ 200% 之间"
                        else -> null
                    }
                }
                DeviceSettingKey.SlaveId -> {
                    val id = inputs.getOrNull(0)?.toIntOrNull()
                    if (id == null || id !in 1..247) "PLC 站号必须在 1 ~ 247 之间" else null
                }
                DeviceSettingKey.BaudRate -> {
                    val br = inputs.getOrNull(0)?.toIntOrNull()
                    if (br == null || br !in listOf(9600, 19200, 38400, 57600, 115200)) "波特率不合法" else null
                }
                DeviceSettingKey.DataBits -> {
                    val db = inputs.getOrNull(0)?.toIntOrNull()
                    if (db == null || db !in listOf(7, 8)) "数据位只能是 7 或 8" else null
                }
                DeviceSettingKey.StopBits -> {
                    val sb = inputs.getOrNull(0)?.toIntOrNull()
                    if (sb == null || sb !in listOf(1, 2)) "停止位只能是 1 或 2" else null
                }
                DeviceSettingKey.ReadTimeoutMs,
                DeviceSettingKey.WriteTimeoutMs,
                DeviceSettingKey.HeartbeatPeriodMs,
                DeviceSettingKey.ActivePollingIntervalMs,
                DeviceSettingKey.IdlePollingIntervalMs,
                DeviceSettingKey.OrderPollingIntervalMs -> {
                    val ms = inputs.getOrNull(0)?.toLongOrNull()
                    if (ms == null || ms < 10) "周期/超时时间不能低于 10ms" else null
                }
                DeviceSettingKey.FrameGapMs -> {
                    val ms = inputs.getOrNull(0)?.toLongOrNull()
                    if (ms == null || ms < 10) "帧间隔不能低于 10ms" else null
                }
                DeviceSettingKey.FormulaSyncIntervalSeconds -> {
                    val sec = inputs.getOrNull(0)?.toLongOrNull()
                    if (sec == null || sec < 0) "配方同步周期不能小于 0s" else null
                }
                DeviceSettingKey.RetryCount -> {
                    val count = inputs.getOrNull(0)?.toIntOrNull()
                    if (count == null || count !in 0..10) "重试次数必须在 0 ~ 10 之间" else null
                }
                DeviceSettingKey.RegisterOnlyBlockCount -> {
                    val count = inputs.getOrNull(0)?.toIntOrNull()
                    if (count == null || count !in 0..125) "测试数量必须在 0 ~ 125 之间" else null
                }
                else -> null
            }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "修改${editor.label}",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(
                    text = editor.detail,
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodyMedium,
                )

                if (fieldLabels != null) {
                    // 复合字段模式
                    fieldLabels.forEachIndexed { index, label ->
                        val currentValue = inputs[index]
                        val options =
                            when {
                                editor.key == DeviceSettingKey.DeviceStandaloneWaterComposite && index == 0 ->
                                    listOf("关闭", "点动", "时间控制")
                                editor.key == DeviceSettingKey.DeviceHeaterConfigComposite && index == 0 ->
                                    listOf("主加热管", "备加热管")
                                editor.key == DeviceSettingKey.DeviceHeaterConfigComposite && index == 1 ->
                                    listOf("温度0", "温度1", "温度1优先")
                                else -> null
                            }

                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = label,
                                color = TextPrimary.copy(alpha = 0.9f),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                            )
                            if (options != null) {
                                FlowRow(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    options.forEach { option ->
                                        val optValue =
                                            when (editor.key) {
                                                DeviceSettingKey.DeviceStandaloneWaterComposite ->
                                                    when (option) {
                                                        "关闭" -> "0"
                                                        "点动" -> "1"
                                                        "时间控制" -> "2"
                                                        else -> "0"
                                                    }
                                                DeviceSettingKey.DeviceHeaterConfigComposite ->
                                                    if (index == 0) {
                                                        (if (option == "主加热管") "1" else "2")
                                                    } else {
                                                        when (option) {
                                                            "温度0" -> "0"
                                                            "温度1" -> "1"
                                                            "温度1优先" -> "2"
                                                            else -> "0"
                                                        }
                                                    }
                                                else -> option
                                            }
                                        val isSelected = currentValue == optValue
                                        FilterChip(
                                            selected = isSelected,
                                            onClick = {
                                                inputs = inputs.toMutableList().apply { set(index, optValue) }
                                            },
                                            label = { Text(option) },
                                            colors =
                                                FilterChipDefaults.filterChipColors(
                                                    containerColor = Color(0xFF07152F),
                                                    labelColor = TextSecondary,
                                                    selectedContainerColor = editor.accent.copy(alpha = 0.2f),
                                                    selectedLabelColor = editor.accent,
                                                ),
                                            border =
                                                FilterChipDefaults.filterChipBorder(
                                                    borderColor = Cyan.copy(alpha = 0.3f),
                                                    selectedBorderColor = editor.accent,
                                                    borderWidth = 1.dp,
                                                    selectedBorderWidth = 1.dp,
                                                    enabled = true,
                                                    selected = isSelected,
                                                ),
                                        )
                                    }
                                }
                            } else {
                                OutlinedTextField(
                                    value =
                                        if (editor.key == DeviceSettingKey.DeviceStandaloneWaterComposite && index == 1) {
                                            currentValue.toIntOrNull()?.ticksToSecondsText() ?: currentValue
                                        } else {
                                            currentValue
                                        },
                                    onValueChange = {
                                        val newValue =
                                            if (editor.key == DeviceSettingKey.DeviceStandaloneWaterComposite && index == 1) {
                                                (it.toDoubleOrNull()?.let { (it * 10).toInt() }?.toString() ?: it)
                                            } else {
                                                it
                                            }
                                        inputs = inputs.toMutableList().apply { set(index, newValue) }
                                    },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
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
                                )
                            }
                        }
                    }
                } else if (editor.options != null) {
                    // 选项模式：平铺按钮或网格
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        editor.options.forEach { option ->
                            val input = inputs[0]
                            val isSelected =
                                input == option ||
                                    (
                                        editor.key == DeviceSettingKey.RegisterOnlyMode &&
                                            (
                                                (input in listOf("true", "1", "开启") && option == "开启") ||
                                                    (input in listOf("false", "0", "关闭") && option == "关闭")
                                            )
                                    ) ||
                                    (
                                        editor.key == DeviceSettingKey.RegisterOnlyBlockStart &&
                                            ((input == "0" && option == "全部") || (input == option))
                                    )

                            FilterChip(
                                selected = isSelected,
                                onClick = { inputs = listOf(option) },
                                label = { Text(option) },
                                colors =
                                    FilterChipDefaults.filterChipColors(
                                        containerColor = Color(0xFF07152F),
                                        labelColor = TextSecondary,
                                        selectedContainerColor = editor.accent.copy(alpha = 0.2f),
                                        selectedLabelColor = editor.accent,
                                    ),
                                border =
                                    FilterChipDefaults.filterChipBorder(
                                        borderColor = Cyan.copy(alpha = 0.3f),
                                        selectedBorderColor = editor.accent,
                                        borderWidth = 1.dp,
                                        selectedBorderWidth = 1.dp,
                                        enabled = true,
                                        selected = isSelected,
                                    ),
                            )
                        }
                    }
                } else {
                    // 输入模式
                    val hintText =
                        when (editor.key) {
                            DeviceSettingKey.SerialPortPath -> "例: /dev/ttyS4"
                            DeviceSettingKey.BaudRate -> "例: 19200"
                            DeviceSettingKey.DataBits -> "例: 8"
                            DeviceSettingKey.Parity -> "NONE / EVEN / ODD"
                            DeviceSettingKey.StopBits -> "例: 1"
                            DeviceSettingKey.SlaveId -> "例: 1"
                            DeviceSettingKey.ReadTimeoutMs,
                            DeviceSettingKey.WriteTimeoutMs,
                            DeviceSettingKey.HeartbeatPeriodMs,
                            DeviceSettingKey.ActivePollingIntervalMs,
                            DeviceSettingKey.IdlePollingIntervalMs,
                            DeviceSettingKey.OrderPollingIntervalMs,
                            DeviceSettingKey.FrameGapMs,
                            -> "毫秒 (ms)"
                            DeviceSettingKey.RegisterOnlyMode -> "1/0 或 true/false"
                            DeviceSettingKey.RegisterOnlyBlockStart -> "0=全部，或 200/320/350/370"
                            DeviceSettingKey.RegisterOnlyBlockCount -> "0=标准数量，或 1~125"
                            DeviceSettingKey.FormulaSyncIntervalSeconds -> "秒，0=本地"
                            DeviceSettingKey.RetryCount -> "例: 3"
                            DeviceSettingKey.ConnectionTestRegister -> "例: 450"
                            else -> ""
                        }
                    OutlinedTextField(
                        value = inputs[0],
                        onValueChange = { inputs = listOf(it) },
                        singleLine = true,
                        label = { Text(editor.label) },
                        placeholder = { Text(hintText, color = TextSecondary.copy(alpha = 0.5f)) },
                        colors =
                            OutlinedTextFieldDefaults.colors(
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary,
                                focusedContainerColor = Color(0xFF09245A),
                                unfocusedContainerColor = Color(0xFF09245A),
                                cursorColor = Yellow,
                                focusedBorderColor = Yellow,
                                unfocusedBorderColor = Cyan.copy(alpha = 0.68f),
                                focusedLabelColor = Yellow,
                                unfocusedLabelColor = TextSecondary,
                            ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                errorText?.let {
                    Text(text = it, color = Red, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = errorText == null,
                onClick = { onConfirm(inputs.joinToString("|")) },
                colors =
                    ButtonDefaults.textButtonColors(
                        contentColor = Cyan,
                        disabledContentColor = TextSecondary.copy(alpha = 0.4f)
                    ),
            ) { Text("保存") }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                colors = ButtonDefaults.textButtonColors(contentColor = TextSecondary),
            ) { Text("取消") }
        },
        containerColor = Panel,
        titleContentColor = TextPrimary,
        textContentColor = TextSecondary,
    )
}
