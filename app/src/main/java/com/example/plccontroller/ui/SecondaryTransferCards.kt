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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.plccontroller.domain.Order
import com.example.plccontroller.domain.PotMode
import com.example.plccontroller.runtime.TransferCardModel
import com.example.plccontroller.runtime.TransferCardPosition
import com.example.plccontroller.runtime.TransferDeckState

/**
 * 核心传锅卡位组件
 *
 * @param card 卡位数据模型，包含分配的订单及位置信息
 * @param onConfirmTransfer 点击卡位进行传锅确认的回调
 */
@Composable
internal fun TransferDeckCard(
    card: TransferCardModel?,
    modifier: Modifier = Modifier,
    touchConfirmEnabled: Boolean = false,
    confirmEnabled: Boolean = false,
    blockedReason: String? = null,
    onConfirmTransfer: ((String) -> Unit)? = null,
) {
    val order = card?.order
    val isConfirmSlot = card?.isConfirmSlot == true
    val accent =
        when {
            isConfirmSlot -> Yellow
            order != null -> Cyan
            else -> BorderBlue.copy(alpha = 0.75f)
        }

    Column(
        modifier =
            modifier
                .fillMaxHeight()
                .clip(RoundedCornerShape(10.dp))
                .background(
                    Brush.linearGradient(
                        listOf(
                            accent.copy(alpha = if (order != null) 0.18f else 0.08f),
                            Color(0xFF07152F),
                            PanelDeep,
                        ),
                    ),
                ).border(1.dp, accent.copy(alpha = 0.72f), RoundedCornerShape(10.dp))
                .padding(10.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        if (order == null) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "卡位 ${card?.position?.cardNumber ?: "--"}",
                    color = accent,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "当前空位",
                        color = TextSecondary,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        } else {
            val confirmLabel = if (isConfirmSlot) "物理确认位" else "确认区"
            val headerText =
                buildString {
                    append("卡位 ")
                    append(card.position.cardNumber)
                    append(" · ")
                    append(order.tableCode ?: "--")
                    append(" · ")
                    append(order.listTimeText())
                    append(" · ")
                    append(order.potMode.label())
                }

            val confirmZoneModifier =
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(accent.copy(alpha = 0.10f))
                    .border(
                        1.dp,
                        accent.copy(alpha = 0.30f),
                        RoundedCornerShape(8.dp),
                    ).then(
                        if (touchConfirmEnabled) {
                            Modifier.clickable { onConfirmTransfer?.invoke(order.id) }
                        } else {
                            Modifier
                        },
                    ).padding(horizontal = 10.dp, vertical = 7.dp)

            Column(
                modifier = Modifier.fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    modifier = confirmZoneModifier,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = headerText,
                        modifier = Modifier.weight(1f),
                        color = TextPrimary,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = confirmLabel,
                        color = accent,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                    )
                }

                PotPartitionDiagram(
                    order = order,
                    accent = accent,
                    modifier = Modifier.weight(1f),
                )

                if (!touchConfirmEnabled || (isConfirmSlot && !confirmEnabled)) {
                    Text(
                        text = blockedReason ?: "当前不可确认",
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
}

/**
 * 锅底分区逻辑示意图容器
 */
@Composable
private fun PotPartitionDiagram(
    order: Order,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    val sections =
        remember(order.id, order.slotSummary, order.potBottomName, order.potMode) {
            buildPotSections(order)
        }

    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFF081A3C))
                .border(1.dp, accent.copy(alpha = 0.38f), RoundedCornerShape(10.dp))
                .padding(8.dp),
    ) {
        when (order.potMode) {
            PotMode.Single -> {
                PotSectionBox(
                    section = sections.first(),
                    accent = accent,
                    sectionCount = sections.size,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            PotMode.Split -> {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    sections.forEach { section ->
                        PotSectionBox(
                            section = section,
                            accent = accent,
                            sectionCount = sections.size,
                            modifier =
                                Modifier
                                    .weight(1f)
                                    .fillMaxHeight(),
                        )
                    }
                }
            }

            PotMode.ThreeGrid -> {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        PotSectionBox(
                            section = sections[0],
                            accent = accent,
                            sectionCount = sections.size,
                            modifier =
                                Modifier
                                    .weight(1f)
                                    .fillMaxHeight(),
                        )
                        PotSectionBox(
                            section = sections[1],
                            accent = accent,
                            sectionCount = sections.size,
                            modifier =
                                Modifier
                                    .weight(1f)
                                    .fillMaxHeight(),
                        )
                    }
                    PotSectionBox(
                        section = sections[2],
                        accent = accent,
                        sectionCount = sections.size,
                        modifier =
                            Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                    )
                }
            }

            PotMode.FourGrid -> {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        PotSectionBox(
                            section = sections[0],
                            accent = accent,
                            sectionCount = sections.size,
                            modifier =
                                Modifier
                                    .weight(1f)
                                    .fillMaxHeight(),
                        )
                        PotSectionBox(
                            section = sections[1],
                            accent = accent,
                            sectionCount = sections.size,
                            modifier =
                                Modifier
                                    .weight(1f)
                                    .fillMaxHeight(),
                        )
                    }
                    Row(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        PotSectionBox(
                            section = sections[2],
                            accent = accent,
                            sectionCount = sections.size,
                            modifier =
                                Modifier
                                    .weight(1f)
                                    .fillMaxHeight(),
                        )
                        PotSectionBox(
                            section = sections[3],
                            accent = accent,
                            sectionCount = sections.size,
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

/**
 * 单个分区的显示方块，已移除冗余的 slotLabel（如“左上”）以释放显示空间
 */
@Composable
private fun PotSectionBox(
    section: PotSection,
    accent: Color,
    sectionCount: Int,
    modifier: Modifier = Modifier,
) {
    val baseTextStyle =
        when (sectionCount) {
            1 -> MaterialTheme.typography.headlineLarge.copy(fontSize = 32.sp)
            2 -> MaterialTheme.typography.headlineMedium.copy(fontSize = 26.sp)
            else -> MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp)
        }

    Column(
        modifier =
            modifier
                .clip(RoundedCornerShape(8.dp))
                .background(accent.copy(alpha = 0.12f))
                .border(1.dp, accent.copy(alpha = 0.55f), RoundedCornerShape(8.dp))
                .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(
            modifier = Modifier.weight(1f),
            contentAlignment = Alignment.CenterStart,
        ) {
            Text(
                text = section.baseName,
                color = TextPrimary,
                style = baseTextStyle,
                fontWeight = FontWeight.Bold,
                maxLines = if (sectionCount <= 2) 3 else 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        section.tasteName?.takeIf(String::isNotBlank)?.let { tasteName ->
            Text(
                text = tasteName,
                color = TextSecondary,
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp),
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

internal fun potModeAccent(potMode: PotMode): Color =
    when (potMode) {
        PotMode.Single -> Cyan
        PotMode.Split -> Green
        PotMode.ThreeGrid -> Yellow
        PotMode.FourGrid -> Blue
    }

internal fun TransferDeckState.cardAt(position: TransferCardPosition): TransferCardModel? = cards.firstOrNull { it.position == position }
