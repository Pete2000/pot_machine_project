package com.example.plccontroller.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.plccontroller.runtime.OperatorNotice
import com.example.plccontroller.runtime.OperatorNoticeLevel

@Composable
internal fun OperatorNoticeBanner(
    notice: OperatorNotice,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = notice.level.accentColor()
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(accent.copy(alpha = 0.14f))
                .border(1.dp, accent.copy(alpha = 0.55f), RoundedCornerShape(8.dp))
                .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = notice.level.labelText(),
            color = accent,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = notice.message,
            color = TextPrimary,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onDismiss) {
            Text(
                text = "关闭",
                color = accent,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

private fun OperatorNoticeLevel.accentColor(): Color =
    when (this) {
        OperatorNoticeLevel.Info -> Cyan
        OperatorNoticeLevel.Warning -> Yellow
        OperatorNoticeLevel.Error -> Red
    }

private fun OperatorNoticeLevel.labelText(): String =
    when (this) {
        OperatorNoticeLevel.Info -> "提示"
        OperatorNoticeLevel.Warning -> "注意"
        OperatorNoticeLevel.Error -> "异常"
    }
