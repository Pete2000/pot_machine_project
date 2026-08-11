package com.example.plccontroller.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.plccontroller.runtime.NetworkConnectionState
import kotlin.math.abs

/**
 * 指标展示方块卡片
 * 将空间划分为数值区（上半部分）和标签区（下半部分），保证全行对齐
 */
@Composable
internal fun MetricTile(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    isIcon: Boolean = false,
) {
    MetricTile(
        label = label,
        modifier = modifier,
    ) {
        Text(
            text = value,
            color = if (isIcon) Cyan else TextPrimary,
            style = if (isIcon) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * 通用指标展示方块卡片，支持自定义内容
 */
@Composable
internal fun MetricTile(
    label: String,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    Column(
        modifier =
            modifier
                .height(76.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(
                    Brush.linearGradient(
                        listOf(
                            PanelBright,
                            Color(0xFF061B46),
                            Color(0xFF05132F),
                        ),
                    ),
                ).border(1.dp, Cyan.copy(alpha = 0.45f), RoundedCornerShape(16.dp))
                .padding(horizontal = 4.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier =
                Modifier
                    .weight(1.8f)
                    .fillMaxWidth(),
            contentAlignment = Alignment.Center,
            content = content,
        )
        Box(
            modifier =
                Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            contentAlignment = Alignment.TopCenter,
        ) {
            Text(
                text = label,
                color = Color(0xFF8FEAFF),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * 信号强度指示器（5级阶梯柱 + 异常红叉）
 */
@Composable
internal fun SignalStrengthIndicator(
    networkState: NetworkConnectionState,
    modifier: Modifier = Modifier,
) {
    val transition = rememberInfiniteTransition(label = "signalSync")
    val animatedIndex by transition.animateFloat(
        initialValue = -1f,
        targetValue = 5f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(durationMillis = 1200, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
        label = "barIndex",
    )

    Canvas(modifier = modifier.size(40.dp, 36.dp)) {
        val width = size.width
        val height = size.height
        val barCount = 5
        val gap = 4.dp.toPx()
        val barWidth = (width - (barCount - 1) * gap) / barCount

        for (i in 0 until barCount) {
            val barHeight = height * ((i + 1).toFloat() / barCount)
            val color =
                when (networkState) {
                    NetworkConnectionState.Online -> Green
                    NetworkConnectionState.Idle -> Yellow
                    NetworkConnectionState.Syncing -> {
                        val distance = abs(i - animatedIndex)
                        val alpha = (1f - (distance / 1.5f)).coerceIn(0.2f, 1f)
                        Cyan.copy(alpha = alpha)
                    }
                    NetworkConnectionState.Fault -> TextSecondary.copy(alpha = 0.3f)
                }

            drawRect(
                color = color,
                topLeft = Offset(i * (barWidth + gap), height - barHeight),
                size = Size(barWidth, barHeight),
            )
        }

        if (networkState == NetworkConnectionState.Fault) {
            val xSize = 14.dp.toPx()
            val strokeWidth = 2.5.dp.toPx()
            val padding = 2.dp.toPx()

            drawLine(
                color = Red,
                start = Offset(padding, height - padding - xSize),
                end = Offset(padding + xSize, height - padding),
                strokeWidth = strokeWidth,
            )
            drawLine(
                color = Red,
                start = Offset(padding, height - padding),
                end = Offset(padding + xSize, height - padding - xSize),
                strokeWidth = strokeWidth,
            )
        }
    }
}

/**
 * 阶梯式水位展示组件 (4 个横杠) - 按照参考图精简重绘
 */
@Composable
internal fun WaterLevelIndicator(
    levelState: Int?,
    modifier: Modifier = Modifier,
) {
    val activeBars =
        when (levelState) {
            0 -> 1
            1 -> 2
            2 -> 2
            3 -> 3
            4 -> 3
            5 -> 3
            6 -> 2
            7 -> 4
            else -> 0
        }

    Canvas(modifier = modifier.size(48.dp, 36.dp)) {
        val width = size.width
        val height = size.height
        val barCount = 4
        val gap = 3.dp.toPx()
        val barHeight = 6.dp.toPx()
        val corner = CornerRadius(3.dp.toPx(), 3.dp.toPx())

        for (i in 0 until barCount) {
            val isFilled = i < activeBars
            val color = if (isFilled) Color(0xFF129DFF) else Color.White.copy(alpha = 0.82f)
            val y = height - (i + 1) * barHeight - i * gap

            drawRoundRect(
                color = color,
                topLeft = Offset(0f, y),
                size = Size(width, barHeight),
                cornerRadius = corner,
                style = Fill,
            )
        }
    }
}

/**
 * 双加热棒状态指示器 - 按照参考图精简为 "||" 形状
 */
@Composable
internal fun DualHeaterIndicator(
    mainActive: Boolean,
    backupActive: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.height(36.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HeaterRod(isActive = mainActive)
        HeaterRod(isActive = backupActive)
    }
}

@Composable
private fun HeaterRod(isActive: Boolean) {
    Box(
        modifier =
            Modifier
                .width(6.dp)
                .height(28.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(if (isActive) Red else Color.White),
    )
}

/**
 * 计数状态胶囊按钮（用于催单、延时等统计）
 */
@Composable
internal fun CounterPill(
    label: String,
    count: String,
    color: Color,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier =
            modifier
                .height(38.dp)
                .clip(RoundedCornerShape(10.dp))
                .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
                .background(Brush.horizontalGradient(listOf(color, color.copy(alpha = 0.8f))))
                .border(
                    if (selected) 2.5.dp else 1.5.dp,
                    Color.White.copy(alpha = if (selected) 0.9f else 0.4f),
                    RoundedCornerShape(10.dp),
                ).padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = Color(0xFF10151F),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.ExtraBold,
        )
        Text(
            text = count,
            color = Color(0xFF10151F),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Black,
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF020716)
@Composable
private fun IndicatorsPreview() {
    Column(
        modifier = Modifier.padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            SignalStrengthIndicator(networkState = NetworkConnectionState.Online)
            SignalStrengthIndicator(networkState = NetworkConnectionState.Fault)
            SignalStrengthIndicator(networkState = NetworkConnectionState.Syncing)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            WaterLevelIndicator(levelState = 0)
            WaterLevelIndicator(levelState = 1)
            WaterLevelIndicator(levelState = 7)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            DualHeaterIndicator(mainActive = true, backupActive = false)
            DualHeaterIndicator(mainActive = false, backupActive = true)
        }
    }
}
