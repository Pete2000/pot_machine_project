package com.example.plccontroller.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.plccontroller.R
import com.example.plccontroller.domain.PlcCommunicationConfig
import com.example.plccontroller.domain.PlcConnectionState
import com.example.plccontroller.domain.PlcPollingSnapshot

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun DebugSettingsContent(
    plcState: PlcConnectionState,
    communicationConfig: PlcCommunicationConfig?,
    plcPollingSnapshot: PlcPollingSnapshot,
    lastMessage: String,
    onRefreshPlcStatus: () -> Unit,
    onSendHeartbeatPulse: () -> Unit,
    onSendHeaterTestCommand: () -> Unit,
    onSendEmergencyWaterTestCommand: () -> Unit,
    onSendPhaseTestCommand: () -> Unit,
    onOpenSecondaryDisplay: () -> Unit,
    onPreviewSecondaryDisplay: () -> Unit,
    onClearAllLocalOrders: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var debugUnlocked by rememberSaveable { mutableStateOf(false) }
    var showUnlockConfirm by rememberSaveable { mutableStateOf(false) }
    var showClearOrdersConfirm by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            SectionTitle(
                title = if (debugUnlocked) "调试控制舱" else "调试状态查看",
                code = if (debugUnlocked) "DANGER ZONE" else "READ ONLY",
            )
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    (if (debugUnlocked) Red else Cyan).copy(alpha = 0.15f),
                                    Color(0xFF07152F),
                                ),
                            ),
                        ).border(
                            1.dp,
                            (if (debugUnlocked) Red else Cyan).copy(alpha = 0.4f),
                            RoundedCornerShape(8.dp),
                        ).padding(14.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_settings_debug),
                            contentDescription = null,
                            tint = if (debugUnlocked) Red else Cyan,
                            modifier = Modifier.width(20.dp).height(20.dp),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text =
                                if (debugUnlocked) {
                                    "当前已进入手动联调模式，写操作具有物理危险性。"
                                } else {
                                    "当前为只读查看模式，不会向 PLC 写入命令。"
                                },
                            color = if (debugUnlocked) Yellow else Cyan,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Text(
                        text =
                            if (debugUnlocked) {
                                "执行前请确认设备周围无人且机械位点安全。调试动作会抢占后台轮询链路。"
                            } else {
                                "需要测试心跳、加热、应急补水或相位写入时，先进入调试控制并二次确认。"
                            },
                        color = TextSecondary,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text(
                            text = "温度0: ${plcPollingSnapshot.formatTemperature0Cn()}",
                            color = Cyan,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = "温度1: ${plcPollingSnapshot.formatTemperature1Cn()}",
                            color = Cyan,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = "液位: ${plcPollingSnapshot.liquidLevelLabelCn()}",
                            color = Cyan,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text(
                            text = "PLC: ${plcState.shortLabel()}",
                            color = plcState.statusColor(),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = "串口: ${communicationConfig?.serialPortPath ?: "--"}",
                            color = TextSecondary,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = "最近: ${lastMessage.ifBlank { "--" }}",
                            color = TextSecondary,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ControlButton(
                    text = "读取寄存器快照",
                    onClick = onRefreshPlcStatus,
                    modifier = Modifier.weight(1f),
                )
                if (debugUnlocked) {
                    ControlButton(
                        text = "手动触发心跳",
                        onClick = onSendHeartbeatPulse,
                        modifier = Modifier.weight(1f),
                    )
                } else {
                    ControlButton(
                        text = "进入调试控制",
                        onClick = { showUnlockConfirm = true },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        if (debugUnlocked) {
            item {
                SectionTitle(title = "手动下发指令", code = "WRITE ACTIONS")
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    DebugActionButton(
                        text = "写入加热测试参数",
                        detail = "D340~D349 (ID/Type/Selection/Temp/Hyst/Sens)",
                        color = Red,
                        onClick = onSendHeaterTestCommand,
                    )
                    DebugActionButton(
                        text = "写入应急补水参数",
                        detail = "D360~D369 (ID/Type/Mode/Timed/SparePump)",
                        color = Yellow,
                        onClick = onSendEmergencyWaterTestCommand,
                    )
                    DebugActionButton(
                        text = "写入单相位控制流",
                        detail = "D300~D319 (ID/Type/Mask/W1-W4/Logical/Oil/Paste/Phase)",
                        color = Cyan,
                        onClick = onSendPhaseTestCommand,
                    )
                    DebugActionButton(
                        text = "退出调试控制",
                        detail = "隐藏写入按钮，恢复只读查看模式",
                        color = Blue,
                        onClick = { debugUnlocked = false },
                    )
                }
            }
        } else {
            item {
                SettingsFieldCard(
                    model =
                        SettingsFieldModel(
                            label = "安全提示",
                            value = "写入命令已隐藏",
                            detail = "进入调试控制后才显示心跳、加热、应急补水和相位写入按钮。",
                            accent = Yellow,
                        ),
                    isInfoTip = true,
                )
            }
        }

        // ── Section 5: 副屏调试入口 ──
        item {
            GradientDivider()
            SectionTitle(title = "副屏调试入口", code = "SECONDARY_DISPLAY")
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                ControlButton(
                    text = "打开副屏预览 (单屏)",
                    onClick = onPreviewSecondaryDisplay,
                    modifier = Modifier.weight(1f),
                )
                ControlButton(
                    text = "启动物理副屏 (双屏)",
                    onClick = onOpenSecondaryDisplay,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        // ── Section 6: 调试数据维护 ──
        item {
            GradientDivider()
            SectionTitle(title = "调试数据维护", code = "LOCAL_DATA")
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Red.copy(alpha = 0.08f))
                            .border(1.dp, Red.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
                            .clickable { showClearOrdersConfirm = true }
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = "清空全部本地订单",
                            color = Red,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = "仅供调试：删除待加水、延时、待传锅、已完成、已取消及订单事件",
                            color = TextSecondary,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Text(text = "删除", color = Red, fontWeight = FontWeight.Bold)
                }
            }
        }

        // 底部留白
        item {
            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    if (showClearOrdersConfirm) {
        AlertDialog(
            onDismissRequest = { showClearOrdersConfirm = false },
            title = { Text(text = "确认清空全部本地订单？", color = Red) },
            text = {
                Text(
                    text =
                        "该操作会删除本机 Room 中的全部订单、alias 和订单事件，包括待传锅记录。" +
                            "正在制作或存在物理输出时系统会拒绝执行。配方、设备编码和 PLC 设置不会删除。",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearOrdersConfirm = false
                        onClearAllLocalOrders()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = Red),
                ) {
                    Text(text = "确认清空")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearOrdersConfirm = false }) {
                    Text(text = "取消")
                }
            },
            containerColor = Panel,
            titleContentColor = TextPrimary,
            textContentColor = TextSecondary,
        )
    }

    if (showUnlockConfirm) {
        AlertDialog(
            onDismissRequest = { showUnlockConfirm = false },
            title = { Text(text = "进入调试控制？") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(text = "调试命令会直接写入 PLC，可能触发加热、补水或相位动作。")
                    Text(text = "请确认设备周围无人，机械位点安全，且当前操作由调试人员执行。")
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        debugUnlocked = true
                        showUnlockConfirm = false
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = Yellow),
                ) {
                    Text("确认进入")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showUnlockConfirm = false },
                    colors = ButtonDefaults.textButtonColors(contentColor = TextSecondary),
                ) {
                    Text("取消")
                }
            },
            containerColor = Panel,
            titleContentColor = TextPrimary,
            textContentColor = TextSecondary,
        )
    }
}

@Composable
private fun DebugActionButton(
    text: String,
    detail: String,
    color: Color = Red,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(72.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(color.copy(alpha = 0.08f))
                .border(1.dp, color.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
                .clickable { onClick() }
                .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column {
            Text(text = text, color = TextPrimary, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(text = detail, color = TextSecondary, style = MaterialTheme.typography.bodySmall)
        }
        Icon(
            painter = painterResource(id = R.drawable.ic_settings_device),
            contentDescription = null,
            tint = color.copy(alpha = 0.7f),
            modifier = Modifier.width(24.dp).height(24.dp),
        )
    }
}

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
