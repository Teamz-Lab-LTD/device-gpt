package com.teamz.lab.debugger.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.teamz.lab.debugger.R
import com.teamz.lab.debugger.ui.icons.DgText
import com.teamz.lab.debugger.ui.icons.DgIconText
import com.teamz.lab.debugger.ui.icons.DgIcons

@Composable
fun DeviceLeaderboardCard(
    deviceModel: String,
    userScore: Int,
    averageScore: Double,
    percentile: Int,
    topScore: Int,
    totalDevices: Int = 1
) {
    AppCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.EmojiEvents,
                contentDescription = stringResource(R.string.lb_dlc_cd),
                tint = if (percentile >= 90) MaterialTheme.colorScheme.primary else Color.Gray,
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = stringResource(R.string.lb_dlc_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                DgText(
                    text = deviceModel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(horizontalAlignment = Alignment.Start) {
                Text(
                    text = stringResource(R.string.lb_dlc_your_score),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Gray
                )
                DgText(
                    text = "$userScore/10",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = stringResource(R.string.lb_dlc_model_avg),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Gray
                )
                DgText(
                    text = String.format("%.1f/10", averageScore),
                    style = MaterialTheme.typography.titleMedium
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = stringResource(R.string.lb_dlc_top_score),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Gray
                )
                DgText(
                    text = "$topScore/10",
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        Surface(
            color = if (percentile >= 90) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.align(Alignment.CenterHorizontally)
        ) {
            DgIconText(
                icon = when {
                    totalDevices == 1 -> DgIcons.Star
                    percentile >= 90 -> DgIcons.Trophy
                    percentile >= 70 -> DgIcons.Medal
                    percentile >= 50 -> DgIcons.BarChart
                    else -> DgIcons.Chart
                },
                text = when {
                    totalDevices == 1 -> stringResource(R.string.lb_dlc_first, deviceModel)
                    percentile >= 90 -> stringResource(R.string.lb_dlc_top_gold, percentile.toString(), deviceModel)
                    percentile >= 70 -> stringResource(R.string.lb_dlc_top_bronze, percentile.toString(), deviceModel)
                    percentile >= 50 -> stringResource(R.string.lb_dlc_above_avg, deviceModel)
                    else -> stringResource(R.string.lb_dlc_top_plain, percentile.toString(), deviceModel)
                },
                color = MaterialTheme.colorScheme.onPrimary,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
            )
        }
        
        if (totalDevices > 1) {
            Spacer(modifier = Modifier.height(8.dp))
            DgText(
                text = stringResource(R.string.lb_dlc_based_on, totalDevices.toString(), deviceModel),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
        }
    }
} 