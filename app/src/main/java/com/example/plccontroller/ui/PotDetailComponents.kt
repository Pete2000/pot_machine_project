package com.example.plccontroller.ui

import android.graphics.Matrix
import androidx.compose.animation.animateColor
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.plccontroller.domain.Order
import android.graphics.SweepGradient as AndroidSweepGradient

@Composable
internal fun ActionColumn(
    order: Order?,
    heading: String? = null,
    subheading: String? = null,
    actionEnabled: Boolean = order != null,
    actionText: String? = null,
    onRequestWater: () -> Unit,
    manualPhasePrompt: String? = null,
    onContinueManualPhase: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .clip(RoundedCornerShape(12.dp))
                .background(Brush.verticalGradient(listOf(Color(0xFF0B66FF), Color(0xFF061A55))))
                .border(1.dp, Cyan.copy(alpha = 0.65f), RoundedCornerShape(12.dp))
                .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = heading ?: order?.tableCode ?: "--",
                color = TextPrimary,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = subheading ?: order?.potMode?.label() ?: "待接单",
                color = TextPrimary,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
        }
        if (manualPhasePrompt != null) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF05122C))
                        .border(1.dp, Yellow.copy(alpha = 0.8f), RoundedCornerShape(10.dp))
                        .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = manualPhasePrompt,
                    color = Yellow,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                Button(
                    onClick = onContinueManualPhase,
                    colors = ButtonDefaults.buttonColors(containerColor = Yellow),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = "已转锅，继续加料",
                        color = Color(0xFF061024),
                        fontWeight = FontWeight.ExtraBold,
                    )
                }
            }
        }
        val isWatering = actionText == "加水中"

        val gradientBrush =
            if (isWatering) {
                val infiniteTransition = rememberInfiniteTransition(label = "gradient_anim")
                val animColor1 by infiniteTransition.animateColor(
                    initialValue = Color(0xFF00223E),
                    targetValue = Color(0xFF00495C),
                    animationSpec =
                        infiniteRepeatable(
                            animation = tween(durationMillis = 2200, easing = LinearEasing),
                            repeatMode = RepeatMode.Reverse,
                        ),
                    label = "color1",
                )
                val animColor2 by infiniteTransition.animateColor(
                    initialValue = Color(0xFF010A1A),
                    targetValue = Color(0xFF002235),
                    animationSpec =
                        infiniteRepeatable(
                            animation = tween(durationMillis = 1800, easing = LinearEasing),
                            repeatMode = RepeatMode.Reverse,
                        ),
                    label = "color2",
                )
                Brush.verticalGradient(listOf(animColor1, animColor2))
            } else {
                null
            }

        val borderBrush =
            if (isWatering) {
                val infiniteTransition = rememberInfiniteTransition(label = "border_anim")
                val rotationAngle by infiniteTransition.animateFloat(
                    initialValue = 0f,
                    targetValue = 360f,
                    animationSpec =
                        infiniteRepeatable(
                            animation = tween(durationMillis = 2000, easing = LinearEasing),
                            repeatMode = RepeatMode.Restart,
                        ),
                    label = "rotationAngle",
                )
                remember(rotationAngle) {
                    object : ShaderBrush() {
                        override fun createShader(size: Size): androidx.compose.ui.graphics.Shader {
                            val shader =
                                AndroidSweepGradient(
                                    size.width / 2f,
                                    size.height / 2f,
                                    intArrayOf(
                                        Green.toArgb(),
                                        Cyan.toArgb(),
                                        Blue.toArgb(),
                                        Color.Transparent.toArgb(),
                                        Color.Transparent.toArgb(),
                                        Blue.copy(alpha = 0.15f).toArgb(),
                                        Cyan.copy(alpha = 0.5f).toArgb(),
                                        Green.toArgb(),
                                    ),
                                    floatArrayOf(
                                        0.0f,
                                        0.05f,
                                        0.15f,
                                        0.3f,
                                        0.7f,
                                        0.85f,
                                        0.95f,
                                        1.0f,
                                    ),
                                )
                            val matrix = Matrix()
                            matrix.postRotate(rotationAngle, size.width / 2f, size.height / 2f)
                            shader.setLocalMatrix(matrix)
                            return shader
                        }
                    }
                }
            } else {
                null
            }

        Button(
            onClick = onRequestWater,
            enabled = actionEnabled,
            colors =
                ButtonDefaults.buttonColors(
                    containerColor = if (isWatering) Color.Transparent else Color(0xFF021029),
                    disabledContainerColor = if (isWatering) Color.Transparent else Color(0xFF1A2B52),
                ),
            shape = RoundedCornerShape(12.dp),
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(84.dp)
                    .then(
                        if (isWatering && gradientBrush != null && borderBrush != null) {
                            Modifier
                                .background(gradientBrush, shape = RoundedCornerShape(12.dp))
                                .border(2.dp, borderBrush, shape = RoundedCornerShape(12.dp))
                        } else {
                            Modifier
                        },
                    ),
        ) {
            if (isWatering) {
                Text(
                    text = "加水中",
                    color = Cyan,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
            } else {
                Text(
                    text = actionText ?: if (manualPhasePrompt == null) "加水" else "等待转锅",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
internal fun PotDetailGrid(
    layoutMode: PotDetailLayoutMode,
    cards: List<PotDetailCardModel>,
    modifier: Modifier = Modifier,
) {
    when (layoutMode) {
        PotDetailLayoutMode.Small,
        PotDetailLayoutMode.Single,
        -> {
            PotDetailCard(
                model = cards.first(),
                modifier = modifier.fillMaxSize(),
            )
        }
        PotDetailLayoutMode.Split -> {
            Row(
                modifier = modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                cards.forEach { card ->
                    PotDetailCard(
                        model = card,
                        modifier =
                            Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                    )
                }
            }
        }
        PotDetailLayoutMode.ThreeGrid -> {
            Column(
                modifier = modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    repeat(2) { index ->
                        PotDetailCard(
                            model = cards[index],
                            modifier =
                                Modifier
                                    .weight(1f)
                                    .fillMaxHeight(),
                        )
                    }
                }
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .weight(0.8f),
                    contentAlignment = Alignment.TopCenter,
                ) {
                    PotDetailCard(
                        model = cards[2],
                        modifier =
                            Modifier
                                .fillMaxWidth(0.56f)
                                .fillMaxHeight(),
                    )
                }
            }
        }
        PotDetailLayoutMode.FourGrid -> {
            Column(
                modifier = modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                repeat(2) { row ->
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        repeat(2) { column ->
                            val index = row * 2 + column
                            PotDetailCard(
                                model = cards[index],
                                modifier =
                                    Modifier
                                        .weight(1f)
                                        .fillMaxHeight(),
                            )
                        }
                    }
                }
            }
        }
    }
}
