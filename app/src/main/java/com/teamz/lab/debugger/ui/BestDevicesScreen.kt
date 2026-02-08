package com.teamz.lab.debugger.ui

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.teamz.lab.debugger.ui.theme.DesignSystemColors
import com.teamz.lab.debugger.utils.*
import kotlinx.coroutines.launch

/**
 * Best Devices Screen - Shows top 10 devices per category
 * Child-friendly UI with trust indicators
 */
@Composable
fun BestDevicesScreen(
    activity: Activity,
    category: LeaderboardCategory
) {
    val scope = rememberCoroutineScope()
    var bestDevices by remember { mutableStateOf<List<DeviceInsight>>(emptyList()) }
    var categoryEntries by remember { mutableStateOf<List<CategoryLeaderboardEntry>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var hasError by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    
    // Load data from category leaderboard (more reliable than device_insights)
    LaunchedEffect(category) {
        isLoading = true
        hasError = false
        categoryEntries = emptyList() // Clear previous data immediately
        bestDevices = emptyList() // Clear previous data immediately
        scope.launch {
            try {
                // Try to get from category leaderboard first (more reliable)
                categoryEntries = LeaderboardManager.getLeaderboardEntries(category.id, 50)
                
                // Also try device_insights as fallback
                if (categoryEntries.isEmpty()) {
                    bestDevices = LeaderboardManager.getBestDevices(category.id, 50)
                }
            } catch (e: Exception) {
                hasError = true
                errorMessage = e.message
                android.util.Log.e("BestDevicesScreen", "Failed to load best devices", e)
            } finally {
                isLoading = false
            }
        }
    }
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight()
    ) {
        // Header (fixed at top) - compact
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 4.dp)
            ) {
                Text(
                    text = "🏆",
                    fontSize = 20.sp,
                    modifier = Modifier.padding(end = 6.dp)
                )
                Text(
                    text = "Top ${category.displayName} Devices",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 18.sp
                )
            }
            Text(
                text = "These are the best devices based on real measurements from users like you!",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 16.sp,
                fontSize = 12.sp,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }
        
        // Scrollable content area
        when {
            isLoading -> {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(
                        start = 14.dp,
                        end = 14.dp,
                        top = 2.dp,
                        bottom = 12.dp
                    )
                ) {
                    items(5) {
                        ShimmerBestDeviceCard()
                    }
                }
            }
            hasError -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("⚠️", fontSize = 48.sp)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Failed to load data",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = errorMessage ?: "Please try again later",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            categoryEntries.isNotEmpty() -> {
                // Use category leaderboard entries (more reliable)
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(
                        start = 8.dp,
                        end = 8.dp,
                        top = 2.dp,
                        bottom = 12.dp
                    )
                ) {
                    itemsIndexed(categoryEntries) { index, entry ->
                        BestDeviceCardFromEntry(
                            rank = index + 1,
                            entry = entry,
                            category = category
                        )
                    }
                }
            }
            bestDevices.isNotEmpty() -> {
                // Fallback to device_insights
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(
                        start = 8.dp,
                        end = 8.dp,
                        top = 2.dp,
                        bottom = 12.dp
                    )
                ) {
                    itemsIndexed(bestDevices) { index, device ->
                        BestDeviceCard(
                            rank = index + 1,
                            device = device,
                            category = category
                        )
                    }
                }
            }
            else -> {
                // No data
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("📊", fontSize = 48.sp)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "No data yet",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Upload your data to see the best devices!",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun BestDeviceCardFromEntry(
    rank: Int,
    entry: CategoryLeaderboardEntry,
    category: LeaderboardCategory
) {
    val score = entry.avgScore
    val trustBadge = calculateTrustBadge(entry.userCount, entry.dataQuality)
    val isTopThree = rank <= 3
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isTopThree) 
                DesignSystemColors.NeonGreen.copy(alpha = 0.12f)
            else 
                MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isTopThree) 1.dp else 0.dp
        ),
        shape = RoundedCornerShape(10.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 12.dp),
            verticalAlignment = Alignment.Top
        ) {
            // Rank badge with medal for top 3 - properly aligned
            val medal = when(rank) {
                1 -> "🥇"
                2 -> "🥈"
                3 -> "🥉"
                else -> "• $rank"
            }
            Box(
                modifier = Modifier
                    .width(36.dp)
                    .padding(top = 2.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = medal,
                    fontSize = if (isTopThree) 24.sp else 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isTopThree) DesignSystemColors.NeonGreen else MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center
                )
            }
            
            Spacer(modifier = Modifier.width(10.dp))
            
            Column(
                modifier = Modifier.weight(1f)
            ) {
                // Device name - ensure proper wrapping and alignment
                Text(
                    text = entry.displayName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    lineHeight = 20.sp,
                    fontSize = 15.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 6.dp)
                )
                
                // Score - properly aligned in a single row
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.Start
                ) {
                    Text(
                        text = "Score: ",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp
                    )
                    Text(
                        text = "${score.toInt()}/100",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 12.sp
                    )
                    Text(
                        text = " (Avg)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        fontSize = 10.sp,
                        modifier = Modifier.padding(start = 2.dp)
                    )
                }
                
                // Trust indicators - properly aligned row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Start
                ) {
                    TrustBadgeIcon(badge = trustBadge)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (entry.userCount == 1) 
                            "Verified by 1 user" 
                        else 
                            "Verified by ${entry.userCount} users",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 10.sp,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                }
            }
        }
    }
}

@Composable
fun BestDeviceCard(
    rank: Int,
    device: DeviceInsight,
    category: LeaderboardCategory
) {
    val score = device.scores[category.id] ?: 0.0
    val trustBadge = calculateTrustBadge(device.userCount, device.dataQuality)
    val isTopThree = rank <= 3
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isTopThree) 
                DesignSystemColors.NeonGreen.copy(alpha = 0.12f)
            else 
                MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isTopThree) 1.dp else 0.dp
        ),
        shape = RoundedCornerShape(10.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 12.dp),
            verticalAlignment = Alignment.Top
        ) {
            // Rank badge with medal for top 3 - properly aligned
            val medal = when(rank) {
                1 -> "🥇"
                2 -> "🥈"
                3 -> "🥉"
                else -> "• $rank"
            }
            Box(
                modifier = Modifier
                    .width(36.dp)
                    .padding(top = 2.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = medal,
                    fontSize = if (isTopThree) 24.sp else 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isTopThree) DesignSystemColors.NeonGreen else MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center
                )
            }
            
            Spacer(modifier = Modifier.width(10.dp))
            
            Column(
                modifier = Modifier.weight(1f)
            ) {
                // Device name - ensure proper wrapping and alignment
                Text(
                    text = device.displayName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    lineHeight = 20.sp,
                    fontSize = 15.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 6.dp)
                )
                
                // Score - properly aligned in a single row
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.Start
                ) {
                    Text(
                        text = "Score: ",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp
                    )
                    Text(
                        text = "${score.toInt()}/100",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 12.sp
                    )
                }
                
                // Trust indicators - properly aligned row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Start
                ) {
                    TrustBadgeIcon(badge = trustBadge)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (device.userCount == 1) 
                            "Verified by 1 user" 
                        else 
                            "Verified by ${device.userCount} users",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 10.sp,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                }
            }
        }
    }
}

@Composable
private fun ShimmerBestDeviceCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(10.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 12.dp),
            verticalAlignment = Alignment.Top
        ) {
            // Shimmer rank badge - matches actual medal size
            Box(
                modifier = Modifier
                    .width(36.dp)
                    .padding(top = 2.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .shimmerEffect()
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                // Shimmer device name - matches actual font size
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.8f)
                        .height(20.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .shimmerEffect()
                )
                Spacer(modifier = Modifier.height(6.dp))
                // Shimmer score row - matches actual layout
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .width(45.dp)
                            .height(14.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .shimmerEffect()
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Box(
                        modifier = Modifier
                            .width(50.dp)
                            .height(14.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .shimmerEffect()
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                    Box(
                        modifier = Modifier
                            .width(30.dp)
                            .height(12.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .shimmerEffect()
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                // Shimmer trust badge row - matches actual layout
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .shimmerEffect()
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .width(120.dp)
                            .height(12.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .shimmerEffect()
                    )
                }
            }
        }
    }
}

