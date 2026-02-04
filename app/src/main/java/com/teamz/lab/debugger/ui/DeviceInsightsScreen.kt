package com.teamz.lab.debugger.ui

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
 * Device Insights Screen - Comprehensive device analysis
 * Shows all scores across categories with visual indicators
 */
@Composable
fun DeviceInsightsScreen(
    activity: Activity,
    normalizedDeviceId: String
) {
    val scope = rememberCoroutineScope()
    var deviceInsight by remember { mutableStateOf<DeviceInsight?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    
    LaunchedEffect(normalizedDeviceId) {
        isLoading = true
        deviceInsight = null // Clear previous data immediately
        scope.launch {
            try {
                deviceInsight = LeaderboardManager.getDeviceInsights(normalizedDeviceId)
            } catch (e: Exception) {
                // Handle error
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
        when {
            isLoading -> {
                // Filter out APP_POWER_MONITORING from shimmer loading too
                val deviceCategoriesCount = LeaderboardCategory.entries.filter { it != LeaderboardCategory.APP_POWER_MONITORING }.size
                
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(
                        start = 12.dp,
                        end = 12.dp,
                        top = 12.dp,
                        bottom = 16.dp
                    )
                ) {
                    item {
                        ShimmerDeviceInsightHeader()
                    }
                    item {
                        Text(
                            text = "Performance Scores",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 18.sp,
                            modifier = Modifier.padding(
                                start = 0.dp,
                                end = 0.dp,
                                top = 8.dp,
                                bottom = 4.dp
                            )
                        )
                    }
                    items(deviceCategoriesCount) {
                        ShimmerCategoryScoreCard()
                    }
                }
            }
            deviceInsight == null -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 32.dp, vertical = 24.dp)
                    ) {
                        Text(
                            text = "📊",
                            fontSize = 56.sp,
                            modifier = Modifier.padding(bottom = 20.dp),
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = "No insights available",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp),
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = "Device data is being collected. Check back soon!",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            lineHeight = 20.sp,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
            else -> {
                val insight = deviceInsight!!
                val trustBadge = calculateTrustBadge(insight.userCount, insight.dataQuality)
                
                // Filter out APP_POWER_MONITORING - it's for app rankings, not device insights
                val deviceCategories = LeaderboardCategory.entries.filter { it != LeaderboardCategory.APP_POWER_MONITORING }
                
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(
                        start = 12.dp,
                        end = 12.dp,
                        top = 12.dp,
                        bottom = 16.dp
                    )
                ) {
                    // Device header
                    item {
                        DeviceInsightHeader(insight = insight, trustBadge = trustBadge)
                    }
                    
                    // All category scores
                    item {
                        Text(
                            text = "Performance Scores",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 18.sp,
                            modifier = Modifier.padding(
                                start = 0.dp,
                                end = 0.dp,
                                top = 8.dp,
                                bottom = 4.dp
                            )
                        )
                    }
                    
                    items(deviceCategories.size) { index ->
                        val category = deviceCategories[index]
                        val score = if (category == LeaderboardCategory.BEST_DEVICE) {
                            // Calculate composite score for BEST_DEVICE category
                            BestDeviceCalculator.calculateCompositeScoreFromScores(
                                scores = insight.scores,
                                userCount = insight.userCount,
                                dataQuality = insight.dataQuality,
                                normalizedDeviceId = insight.normalizedDeviceId,
                                displayName = insight.displayName,
                                normalizedBrand = insight.normalizedBrand
                            )?.compositeScore ?: 0.0
                        } else {
                            insight.scores[category.id] ?: 0.0
                        }
                        CategoryScoreCard(
                            category = category,
                            score = score
                        )
                    }
                    
                    // Bottom padding for last item
                    item {
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun DeviceInsightHeader(
    insight: DeviceInsight,
    trustBadge: TrustBadge
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (MaterialTheme.colorScheme.background == DesignSystemColors.Dark) {
                DesignSystemColors.DarkII
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(
            0.5.dp,
            MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)
        )
    ) {
        Column(
            modifier = Modifier.padding(
                start = 12.dp,
                end = 12.dp,
                top = 12.dp,
                bottom = 12.dp
            )
        ) {
            // Device name - smaller
            Text(
                text = insight.displayName,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                lineHeight = 24.sp,
                fontSize = 18.sp,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            
            // Clarification text about aggregated data
            Text(
                text = "📊 Aggregated data from ${insight.userCount} ${if (insight.userCount == 1) "user" else "users"} with this device model",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                fontSize = 11.sp,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            
            // Trust badge - compact layout (TrustBadgeIcon already shows the text, so just show user count)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TrustBadgeIcon(badge = trustBadge)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (insight.userCount == 1) 
                        "Verified by 1 user" 
                    else 
                        "Verified by ${insight.userCount} users",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    fontSize = 11.sp
                )
            }
            
            // Data quality - smaller
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Data Quality:",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(end = 10.dp)
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    repeat(5) { index ->
                        Text(
                            text = if (index < insight.dataQuality) "⭐" else "☆",
                            fontSize = 16.sp,
                            color = if (index < insight.dataQuality) 
                                MaterialTheme.colorScheme.onSurface
                            else 
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                            modifier = Modifier.padding(end = 2.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun CategoryScoreCard(
    category: LeaderboardCategory,
    score: Double
) {
    val progress = (score / 100.0).coerceIn(0.0, 1.0)
    // Use design system text color for all scores
    val scoreColor = MaterialTheme.colorScheme.onSurface
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (MaterialTheme.colorScheme.background == DesignSystemColors.Dark) {
                DesignSystemColors.DarkII
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(
            0.5.dp,
            MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)
        )
    ) {
        Column(
            modifier = Modifier.padding(
                start = 12.dp,
                end = 12.dp,
                top = 12.dp,
                bottom = 12.dp
            )
        ) {
            // Top row: Icon, Category name, and Score
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                // Left side: Icon and Category name
                Row(
                    verticalAlignment = Alignment.Top,
                    modifier = Modifier.weight(1f)
                ) {
                    // Category icon - smaller
                    Text(
                        text = category.icon,
                        fontSize = 22.sp,
                        modifier = Modifier.padding(end = 10.dp, top = 1.dp)
                    )
                    // Category name - smaller font, allow wrapping
                    Text(
                        text = category.displayName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        lineHeight = 18.sp,
                        fontSize = 15.sp,
                        modifier = Modifier.weight(1f)
                    )
                }
                
                // Score - smaller, on the right
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End,
                    modifier = Modifier.padding(start = 8.dp)
                ) {
                    Text(
                        text = "${score.toInt()}",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = scoreColor,
                        lineHeight = 24.sp,
                        fontSize = 20.sp
                    )
                    Text(
                        text = "/100",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Normal,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(start = 1.dp, top = 2.dp)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Description - full width, smaller font, allow multiple lines
            Text(
                text = category.childFriendlyExplanation,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 16.sp,
                fontSize = 12.sp,
                modifier = Modifier.fillMaxWidth()
            )
            
            Spacer(modifier = Modifier.height(10.dp))
            
            // Progress bar - thinner, use primary color for consistency
            LinearProgressIndicator(
                progress = progress.toFloat(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
            )
        }
    }
}

@Composable
private fun ShimmerDeviceInsightHeader() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(
                start = 12.dp,
                end = 12.dp,
                top = 12.dp,
                bottom = 12.dp
            )
        ) {
            // Shimmer device name
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.7f)
                    .height(24.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .shimmerEffect()
            )
            Spacer(modifier = Modifier.height(12.dp))
            // Shimmer trust badge row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .shimmerEffect()
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Box(
                        modifier = Modifier
                            .width(80.dp)
                            .height(14.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .shimmerEffect()
                    )
                    Spacer(modifier = Modifier.height(4.dp))
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
            Spacer(modifier = Modifier.height(10.dp))
            // Shimmer data quality
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .width(80.dp)
                        .height(12.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .shimmerEffect()
                )
                Spacer(modifier = Modifier.width(10.dp))
                repeat(5) {
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .shimmerEffect()
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                }
            }
        }
    }
}

@Composable
private fun ShimmerCategoryScoreCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        shape = RoundedCornerShape(10.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp)
        ) {
            // Top row: Icon, Category name, and Score - matches actual layout
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Row(
                    verticalAlignment = Alignment.Top,
                    modifier = Modifier.weight(1f)
                ) {
                    // Category icon - matches actual size
                    Box(
                        modifier = Modifier
                            .size(22.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .shimmerEffect()
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    // Category name - matches actual font size
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.6f)
                            .height(18.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .shimmerEffect()
                    )
                }
                // Score - matches actual layout
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(start = 8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .width(35.dp)
                            .height(24.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .shimmerEffect()
                    )
                    Spacer(modifier = Modifier.width(1.dp))
                    Box(
                        modifier = Modifier
                            .width(30.dp)
                            .height(14.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .shimmerEffect()
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            // Description - matches actual font size
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .height(16.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .shimmerEffect()
            )
            Spacer(modifier = Modifier.height(10.dp))
            // Progress bar - matches actual size
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .shimmerEffect()
            )
        }
    }
}

