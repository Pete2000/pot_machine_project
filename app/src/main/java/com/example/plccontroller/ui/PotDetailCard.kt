package com.example.plccontroller.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
internal fun PotDetailCard(
    model: PotDetailCardModel,
    modifier: Modifier = Modifier,
) {
    val isBottomAligned =
        model.labelAlignment == Alignment.BottomStart ||
            model.labelAlignment == Alignment.BottomEnd ||
            model.labelAlignment == Alignment.BottomCenter

    val tabAlignment =
        if (isBottomAligned) {
            when (model.labelAlignment) {
                Alignment.BottomStart -> Alignment.BottomStart
                Alignment.BottomEnd -> Alignment.BottomEnd
                else -> Alignment.BottomCenter
            }
        } else {
            when (model.labelAlignment) {
                Alignment.TopStart -> Alignment.TopStart
                Alignment.TopEnd -> Alignment.TopEnd
                else -> Alignment.TopCenter
            }
        }

    val tabShape =
        if (isBottomAligned) {
            when (tabAlignment) {
                Alignment.BottomStart -> RoundedCornerShape(bottomStart = 8.dp, topEnd = 8.dp)
                Alignment.BottomEnd -> RoundedCornerShape(bottomEnd = 8.dp, topStart = 8.dp)
                else -> RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp)
            }
        } else {
            when (tabAlignment) {
                Alignment.TopStart -> RoundedCornerShape(topStart = 8.dp, bottomEnd = 8.dp)
                Alignment.TopEnd -> RoundedCornerShape(topEnd = 8.dp, bottomStart = 8.dp)
                else -> RoundedCornerShape(bottomStart = 8.dp, bottomEnd = 8.dp)
            }
        }

    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(if (model.selected) PanelBright else Panel)
                .border(
                    1.dp,
                    if (model.selected) Cyan else BorderBlue.copy(alpha = 0.3f),
                    RoundedCornerShape(8.dp),
                ).then(if (model.onClick != null) Modifier.clickable { model.onClick.invoke() } else Modifier),
    ) {
        val contentPaddingTop = if (isBottomAligned) 12.dp else 44.dp
        val contentPaddingBottom = if (isBottomAligned) 44.dp else 12.dp

        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 12.dp, top = contentPaddingTop, bottom = contentPaddingBottom)
                    .align(Alignment.CenterStart),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            model.highlight?.takeIf(String::isNotBlank)?.let { highlight ->
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = "口味",
                        color = Yellow.copy(alpha = 0.9f),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Black,
                    )
                    Text(
                        text = highlight,
                        color = Yellow,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Text(
                text = model.content,
                color = TextSecondary.copy(alpha = 0.9f),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = if (model.highlight == null) 4 else 5,
                overflow = TextOverflow.Ellipsis,
            )
            if (model.footer != null) {
                Text(
                    text = model.footer,
                    color = TextSecondary.copy(alpha = 0.6f),
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }

        Box(
            modifier = Modifier.align(tabAlignment),
        ) {
            Box(
                modifier =
                    Modifier
                        .clip(tabShape)
                        .background(Brush.horizontalGradient(listOf(Color(0xFF128CFF), Color(0xFF0B66FF))))
                        .border(1.dp, Cyan.copy(alpha = 0.6f), tabShape)
                        .padding(vertical = 7.dp, horizontal = 16.dp),
            ) {
                Text(
                    text = model.title,
                    color = TextPrimary,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Black,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        if (model.selected) {
            val indicatorAlignment =
                if (tabAlignment == Alignment.TopEnd || tabAlignment == Alignment.BottomEnd) {
                    Alignment.TopStart
                } else {
                    Alignment.TopEnd
                }
            Box(
                modifier =
                    Modifier
                        .padding(8.dp)
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(Cyan)
                        .align(indicatorAlignment),
            )
        }
    }
}
