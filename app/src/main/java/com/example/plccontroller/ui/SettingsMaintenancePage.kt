package com.example.plccontroller.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.plccontroller.data.SettingsStore
import com.example.plccontroller.domain.Order
import com.example.plccontroller.domain.PlcCommunicationConfig
import com.example.plccontroller.domain.PlcConnectionState
import com.example.plccontroller.domain.PlcPollingSnapshot
import com.example.plccontroller.runtime.NetworkConnectionState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
internal fun MaintenanceReadOnlyContent(
    networkState: NetworkConnectionState,
    plcState: PlcConnectionState,
    pendingOrders: List<Order>,
    waitingTransferOrders: List<Order>,
    completedOrders: List<Order>,
    cancelledOrders: List<Order>,
    lastMessage: String,
    logCount: Int,
    logs: List<String>,
    communicationConfig: PlcCommunicationConfig?,
    plcPollingSnapshot: PlcPollingSnapshot,
    onBeginHeaterActuatorConfigEdit: () -> Unit = {},
    onCancelHeaterActuatorConfigEdit: () -> Unit = {},
    onSaveHeaterActuatorConfig: ((SettingsStore) -> Unit) -> Unit = {},
    onRefreshPlcStatus: () -> Unit = {},
    onSendHeartbeatPulse: () -> Unit = {},
    onSendHeaterTestCommand: () -> Unit = {},
    onSendEmergencyWaterTestCommand: () -> Unit = {},
    onSendPhaseTestCommand: () -> Unit = {},
    onUpdateSettings: ((SettingsStore) -> Unit) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val diagnostics =
        listOf(
            SettingsFieldModel(
                label = "连接状态",
                value = plcState.shortLabel(),
                detail = "网络 ${networkState.statusLabel()} / 从站 ${communicationConfig?.slaveId ?: "--"}",
                accent = plcState.statusColor(),
            ),
            SettingsFieldModel(
                label = "最近成功轮询",
                value = plcPollingSnapshot.lastPollTimeText(),
                detail = "距今 ${plcPollingSnapshot.lastPollAgeText()}",
                accent = Cyan,
            ),
            SettingsFieldModel(
                label = "故障摘要",
                value = plcFaultSummaryCn(plcState, plcPollingSnapshot, lastMessage),
                detail = "日志 $logCount 条 / 队列 ${pendingOrders.size}/${waitingTransferOrders.size}/${completedOrders.size}/${cancelledOrders.size}",
                accent = plcState.statusColor(),
            ),
            SettingsFieldModel(
                label = "相位状态",
                value = plcActionStateLabelCn(plcPollingSnapshot.phaseActionStateCode),
                detail = "结果：${plcResultCodeLabelCn(plcPollingSnapshot.phaseResultCode)}",
                accent = Cyan,
            ),
            SettingsFieldModel(
                label = "加热状态",
                value = plcPollingSnapshot.heaterDisplayLabelCn(plcState),
                detail = "动作：${plcActionStateLabelCn(
                    plcPollingSnapshot.heaterActionStateCode,
                )} / 结果：${plcResultCodeLabelCn(plcPollingSnapshot.heaterResultCode)}",
                accent = Blue,
            ),
            SettingsFieldModel(
                label = "应急状态",
                value = plcActionStateLabelCn(plcPollingSnapshot.emergencyActionStateCode),
                detail = "结果：${plcResultCodeLabelCn(plcPollingSnapshot.emergencyResultCode)}",
                accent = Yellow,
            ),
        )

    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // ── Section 1: 维护总览 ──
        item {
            SectionTitle(title = "维护总览", code = "MAINTENANCE")
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                diagnostics.chunked(3).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        row.forEach { item ->
                            SettingsFieldCard(model = item, modifier = Modifier.weight(1f))
                        }
                        // 不足3个时补空位
                        repeat(3 - row.size) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }

        // ── Section 2: 模拟量监控 ──
        item {
            GradientDivider()
            SectionTitle(title = "模拟量监控", code = "ANALOG")
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SettingsFieldCard(
                    model =
                        SettingsFieldModel(
                            label = "温度探头 1",
                            value = plcPollingSnapshot.formatTemperature0Cn(),
                            detail = "原始值: ${plcPollingSnapshot.temperatureSensor0 ?: "--"}",
                            accent = temperatureAccentColor(plcPollingSnapshot.temperatureSensor0),
                        ),
                    modifier = Modifier.weight(1f),
                )
                SettingsFieldCard(
                    model =
                        SettingsFieldModel(
                            label = "温度探头 2",
                            value = plcPollingSnapshot.formatTemperature1Cn(),
                            detail = "原始值: ${plcPollingSnapshot.temperatureSensor1 ?: "--"}",
                            accent = temperatureAccentColor(plcPollingSnapshot.temperatureSensor1),
                        ),
                    modifier = Modifier.weight(1f),
                )
                SettingsFieldCard(
                    model =
                        SettingsFieldModel(
                            label = "液位状态",
                            value = plcPollingSnapshot.liquidLevelLabelCn(),
                            detail = "寄存器值: ${plcPollingSnapshot.liquidLevelState ?: "--"}",
                            accent = liquidLevelAccentColor(plcPollingSnapshot.liquidLevelState),
                        ),
                    modifier = Modifier.weight(1f),
                )
            }
        }

        // ── Section 3: 实时 IO 监控（可折叠） ──
        item {
            GradientDivider()
            CollapsibleSection(title = "实时 IO 监控", code = "IO_MONITOR") {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    IoIndicatorGrid(
                        title = "数字量输入 (DI) × 20",
                        columns = 5,
                        bits = plcPollingSnapshot.inputMirrorBits,
                        labels =
                            listOf(
                                "急停",
                                "加水",
                                "传锅",
                                "应急水",
                                "应急骨",
                                "应急油",
                                "出水0",
                                "出水1",
                                "出水2",
                                "出水3",
                                "单加水",
                                "主加热",
                                "备加热",
                                "液位0",
                                "液位1",
                                "液位2",
                                "DI20",
                                "DI21",
                                "DI22",
                                "DI23",
                            ),
                    )
                    IoIndicatorGrid(
                        title = "数字量输出 (DO) × 20",
                        columns = 5,
                        bits = plcPollingSnapshot.outputMirrorBits,
                        labels =
                            listOf(
                                "主加热",
                                "备加热",
                                "气动0",
                                "气动1",
                                "气动2",
                                "气动3",
                                "进水阀",
                                "单出水",
                                "喷雾阀",
                                "鸡油泵",
                                "骨膏泵",
                                "备用泵",
                                "DO14",
                                "DO15",
                                "DO16",
                                "DO17",
                                "DO20",
                                "DO21",
                                "DO22",
                                "DO23",
                            ),
                    )
                }
            }
        }

        // ── Section 4: 通讯质量（可折叠） ──
        item {
            GradientDivider()
            CollapsibleSection(title = "通讯质量", code = "COMM_QUALITY") {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SettingsFieldCard(
                        model =
                            SettingsFieldModel(
                                label = "串口诊断",
                                value = plcPollingSnapshot.serialDiagnostic.summary,
                                detail = plcPollingSnapshot.serialDiagnostic.method,
                                accent = if (plcPollingSnapshot.serialDiagnostic.rawModeOk == true) Green else Yellow,
                            ),
                        modifier = Modifier.weight(1f),
                    )
                    SettingsFieldCard(
                        model =
                            SettingsFieldModel(
                                label = "心跳信号",
                                value = if (plcPollingSnapshot.lastHeartbeatValue) "HIGH" else "LOW",
                                detail = "D400 翻转值",
                                accent = if (plcPollingSnapshot.lastHeartbeatValue) Green else TextSecondary,
                            ),
                        modifier = Modifier.weight(1f),
                    )
                    SettingsFieldCard(
                        model =
                            SettingsFieldModel(
                                label = "轮询警告",
                                value = "${plcPollingSnapshot.pollWarnings.size} 条",
                                detail =
                                    if (plcPollingSnapshot.pollWarnings.isEmpty()) {
                                        "无异常"
                                    } else {
                                        plcPollingSnapshot.pollWarnings.first().take(
                                            20,
                                        )
                                    },
                                accent = if (plcPollingSnapshot.pollWarnings.isEmpty()) Green else Yellow,
                            ),
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        // ── Section 5: 动作诊断 ──
        item {
            GradientDivider()
            SectionTitle(title = "动作诊断", code = "DIAGNOSTIC")
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                val phaseCode = plcPollingSnapshot.phaseStatusRegisters.getOrNull(3) ?: 0
                val phaseResult = plcPollingSnapshot.phaseStatusRegisters.getOrNull(4) ?: 0
                val faultReg = plcPollingSnapshot.holdingRegisters.getOrNull(310) ?: 0

                MetricPillEnhanced(
                    label = "相位代码",
                    value = phaseCode.toString(),
                    subtitle = plcActionStateLabelCn(phaseCode),
                    accent = Cyan,
                    modifier = Modifier.weight(1f),
                )
                MetricPillEnhanced(
                    label = "相位结果",
                    value = phaseResult.toString(),
                    subtitle = plcResultCodeLabelCn(phaseResult),
                    accent = Cyan,
                    modifier = Modifier.weight(1f),
                )
                MetricPillEnhanced(
                    label = "故障寄存器",
                    value = faultReg.toString(),
                    subtitle = if (faultReg == 0) "无故障" else "故障代码",
                    accent = if (faultReg == 0) Green else Red,
                    modifier = Modifier.weight(1.2f),
                )
            }
        }

        // ── Section 7: 本地日志（可折叠） ──
        item {
            GradientDivider()
            CollapsibleSection(title = "本地日志", code = "LOCAL_LOG") {
                if (logs.isEmpty()) {
                    SettingsFieldCard(
                        model =
                            SettingsFieldModel(
                                label = "日志内容",
                                value = "暂无日志",
                                detail = "应用运行后会在这里显示最近的本地事件",
                                accent = TextSecondary,
                            ),
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        logs.forEachIndexed { index, entry ->
                            LocalLogRow(
                                index = index + 1,
                                entry = entry,
                            )
                        }
                    }
                }
            }
        }

        // 底部留白
        item {
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun LocalLogRow(
    index: Int,
    entry: String,
) {
    val (timeText, message) = formatLocalLogEntry(entry)

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(Panel)
                .border(1.dp, BorderBlue.copy(alpha = 0.18f), RoundedCornerShape(8.dp))
                .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = "#${index.toString().padStart(2, '0')}",
            color = Yellow,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(36.dp),
        )
        Text(
            text = timeText,
            color = Cyan,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(64.dp),
        )
        Text(
            text = message,
            color = TextPrimary,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

// ── 渐变分隔线 ──

@Composable
private fun GradientDivider() {
    Spacer(modifier = Modifier.height(4.dp))
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            Color.Transparent,
                            Cyan.copy(alpha = 0.25f),
                            Cyan.copy(alpha = 0.4f),
                            Cyan.copy(alpha = 0.25f),
                            Color.Transparent,
                        ),
                    ),
                ),
    )
    Spacer(modifier = Modifier.height(4.dp))
}

// ── IO 指示器（固定列网格） ──

@Composable
private fun IoIndicatorGrid(
    title: String,
    columns: Int,
    bits: List<Boolean>,
    labels: List<String>,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(text = title, style = MaterialTheme.typography.labelMedium, color = TextSecondary)
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            labels.chunked(columns).forEach { rowLabels ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    rowLabels.forEachIndexed { colIndex, label ->
                        val globalIndex = labels.indexOf(label)
                        IoIndicator(
                            label = label,
                            active = bits.getOrElse(globalIndex) { false },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    // 不足列数时补空位
                    repeat(columns - rowLabels.size) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun IoIndicator(
    label: String,
    active: Boolean,
    modifier: Modifier = Modifier,
) {
    val ledColor = if (active) Green else Color.Gray

    Row(
        modifier =
            modifier
                .clip(RoundedCornerShape(6.dp))
                .background(if (active) Green.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.05f))
                .border(1.dp, if (active) Green.copy(alpha = 0.5f) else Color.White.copy(alpha = 0.12f), RoundedCornerShape(6.dp))
                .padding(horizontal = 6.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        // LED 圆点，激活时带发光效果
        Box(
            modifier =
                Modifier
                    .size(10.dp)
                    .then(
                        if (active) {
                            Modifier.drawBehind {
                                drawCircle(
                                    color = Green.copy(alpha = 0.3f),
                                    radius = size.minDimension * 0.9f,
                                )
                            }
                        } else {
                            Modifier
                        },
                    ).clip(CircleShape)
                    .background(ledColor),
        )
        Text(
            text = label,
            color = if (active) Green else TextSecondary,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

// ── 增强版 MetricPill（带语义解释） ──

@Composable
private fun MetricPillEnhanced(
    label: String,
    value: String,
    subtitle: String,
    accent: Color = Cyan,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .clip(RoundedCornerShape(8.dp))
                .background(Panel)
                .border(1.dp, accent.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                color = TextSecondary,
                style = MaterialTheme.typography.labelSmall,
            )
            Text(
                text = value,
                color = accent,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
            )
        }
        Text(
            text = subtitle,
            color = accent.copy(alpha = 0.6f),
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

// ── 辅助函数 ──

private fun temperatureAccentColor(rawValue: Int?): Color {
    val value = rawValue ?: return TextSecondary
    val tempC = value / 10.0
    return when {
        tempC >= 100.0 -> Red
        tempC >= 60.0 -> Yellow
        tempC > 0.0 -> Green
        else -> TextSecondary
    }
}

private fun liquidLevelAccentColor(state: Int?): Color =
    when (state) {
        null -> TextSecondary
        0 -> Red // 超低
        1 -> Yellow // 低
        2 -> Red // 低异常
        3 -> Cyan // 中
        7 -> Green // 高
        else -> Yellow // 异常组合
    }

private fun formatLocalLogEntry(entry: String): Pair<String, String> {
    val parts = entry.split("  ", limit = 2)
    val timestamp = parts.firstOrNull()?.toLongOrNull()
    val message = parts.getOrNull(1)?.ifBlank { entry } ?: entry
    val timeText =
        timestamp?.let {
            SimpleDateFormat("HH:mm:ss", Locale.CHINA).format(Date(it))
        } ?: "--:--:--"
    return timeText to message
}
