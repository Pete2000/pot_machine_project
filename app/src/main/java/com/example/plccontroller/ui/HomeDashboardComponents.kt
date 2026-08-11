package com.example.plccontroller.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.plccontroller.domain.Order
import com.example.plccontroller.domain.PlcConnectionState
import com.example.plccontroller.domain.PlcPollingSnapshot
import com.example.plccontroller.runtime.NetworkConnectionState
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
internal fun TopStatusPanel(
    plcState: PlcConnectionState,
    networkState: NetworkConnectionState,
    homeMode: HomeMode,
    pendingCount: Int,
    onHomeModeChange: (HomeMode) -> Unit,
    isCompact: Boolean,
) {
    var currentTimeText by remember { mutableStateOf(currentDateText()) }
    LaunchedEffect(Unit) {
        while (true) {
            currentTimeText = currentDateText()
            delay(1000L)
        }
    }

    HmiPanel {
        if (isCompact) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                NetworkStatusBlock(networkState = networkState)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ModeToggle(
                        homeMode = homeMode,
                        onSelect = onHomeModeChange,
                        compact = true,
                    )
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = currentTimeText,
                            color = TextPrimary,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Row(
                            modifier = Modifier.padding(top = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            StatusCapsule(text = plcState.shortLabel(), color = plcState.statusColor())
                            StatusCapsule(text = "待加工 $pendingCount", color = Cyan)
                        }
                    }
                }
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                NetworkStatusBlock(networkState = networkState)
                ModeToggle(
                    homeMode = homeMode,
                    onSelect = onHomeModeChange,
                )
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = currentTimeText,
                        color = TextPrimary,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Row(
                        modifier = Modifier.padding(top = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        StatusCapsule(text = plcState.shortLabel(), color = plcState.statusColor())
                        StatusCapsule(text = "待加工 $pendingCount", color = Cyan)
                    }
                }
            }
        }
    }
}

@Composable
private fun NetworkStatusBlock(networkState: NetworkConnectionState) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(networkState.statusColor().copy(alpha = 0.12f))
                    .border(1.dp, networkState.statusColor().copy(alpha = 0.4f), RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center,
        ) {
            SignalStrengthIndicator(networkState = networkState)
        }

        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = networkState.statusLabel(),
                color = networkState.statusColor(),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.ExtraBold,
            )
            Text(
                text = "ORDERS API",
                color = TextSecondary,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun ModeToggle(
    homeMode: HomeMode,
    onSelect: (HomeMode) -> Unit,
    compact: Boolean = false,
) {
    Row(
        modifier =
            Modifier
                .height(44.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(Color(0xFF061024))
                .border(1.dp, Yellow.copy(alpha = 0.75f), RoundedCornerShape(999.dp))
                .padding(3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ModeToggleSegment(
            text = "接单模式",
            selected = homeMode == HomeMode.Receive,
            color = Green,
            onClick = { onSelect(HomeMode.Receive) },
            modifier = Modifier.width(if (compact) 82.dp else 92.dp),
        )
        ModeToggleSegment(
            text = "手动模式",
            selected = homeMode == HomeMode.Manual,
            color = Yellow,
            onClick = { onSelect(HomeMode.Manual) },
            modifier = Modifier.width(if (compact) 82.dp else 92.dp),
        )
    }
}

@Composable
private fun ModeToggleSegment(
    text: String,
    selected: Boolean,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .fillMaxHeight()
                .clip(RoundedCornerShape(999.dp))
                .clickable { onClick() }
                .background(if (selected) color.copy(alpha = 0.2f) else Color.Transparent),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = if (selected) color else TextSecondary,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
    }
}

@Composable
internal fun EnhancedDeviceMetricsPanel(
    plcState: PlcConnectionState,
    snapshot: PlcPollingSnapshot,
    pendingOrders: List<Order>,
    delayedOrders: List<Order>,
    urgedOrders: List<Order>,
    onDelayPillClick: () -> Unit,
    onPendingPillClick: () -> Unit,
    onUrgePillClick: () -> Unit,
    lastMessage: String,
    isCompact: Boolean,
) {
    HmiPanel {
        val metricItems =
            listOf(
                Triple(snapshot.formatTemperature0Cn(), "水箱温度", "TEMP"),
                Triple("", "液位", "LEVEL"),
                Triple("", "加热状态", "HEATER"),
                Triple("0", "用电 Kwh", "POWER"),
            )

        if (isCompact) {
            metricItems.chunked(2).forEachIndexed { index, rowItems ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    rowItems.forEach { (_, label, type) ->
                        MetricTile(label = label, modifier = Modifier.weight(1f)) {
                            when (type) {
                                "LEVEL" -> WaterLevelIndicator(levelState = snapshot.liquidLevelState)
                                "HEATER" ->
                                    DualHeaterIndicator(
                                        mainActive = snapshot.mainHeaterOutput,
                                        backupActive = snapshot.backupHeaterOutput,
                                    )
                                "TEMP" ->
                                    Text(
                                        text = snapshot.formatTemperature0Cn(),
                                        color = TextPrimary,
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.ExtraBold,
                                        textAlign = TextAlign.Center,
                                    )
                                else ->
                                    Text(
                                        text = "0",
                                        color = TextPrimary,
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.ExtraBold,
                                        textAlign = TextAlign.Center,
                                    )
                            }
                        }
                    }
                }
                if (index == 0) Spacer(modifier = Modifier.height(8.dp))
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                metricItems.forEach { (_, label, type) ->
                    MetricTile(
                        label = label,
                        modifier = Modifier.weight(1f),
                    ) {
                        when (type) {
                            "LEVEL" -> WaterLevelIndicator(levelState = snapshot.liquidLevelState)
                            "HEATER" ->
                                DualHeaterIndicator(
                                    mainActive = snapshot.mainHeaterOutput,
                                    backupActive = snapshot.backupHeaterOutput,
                                )
                            "TEMP" ->
                                Text(
                                    text = snapshot.formatTemperature0Cn(),
                                    color = TextPrimary,
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.ExtraBold,
                                    textAlign = TextAlign.Center,
                                )
                            else ->
                                Text(
                                    text = "0",
                                    color = TextPrimary,
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.ExtraBold,
                                    textAlign = TextAlign.Center,
                                )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            CounterPill(
                label = "催单",
                count = urgedOrders.size.toString(),
                color = Color(0xFFFF5A79),
                modifier = Modifier.weight(1f),
                onClick = onUrgePillClick,
            )
            CounterPill(
                label = "延时",
                count = delayedOrders.size.toString(),
                color = Color(0xFFFFAB40),
                modifier = Modifier.weight(1f),
                onClick = onDelayPillClick,
            )
            CounterPill(
                label = "待加工",
                count = pendingOrders.size.toString(),
                color = Blue,
                modifier = Modifier.weight(1f),
                onClick = onPendingPillClick,
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = plcFaultSummaryCn(plcState, snapshot, lastMessage),
            color = TextSecondary,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun currentDateText(): String = SimpleDateFormat("yyyy/M/d HH:mm:ss", Locale.CHINA).format(Date())
