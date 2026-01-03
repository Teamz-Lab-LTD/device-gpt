package com.teamz.lab.debugger.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.size
import com.teamz.lab.debugger.ui.theme.DesignSystemColors
import com.teamz.lab.debugger.utils.HealthScoreUtils
import com.teamz.lab.debugger.utils.handleError
import com.teamz.lab.debugger.utils.InterstitialAdManager
import com.teamz.lab.debugger.utils.RemoteConfigUtils
import com.teamz.lab.debugger.utils.AnalyticsUtils
import com.teamz.lab.debugger.utils.AnalyticsEvent
import kotlinx.coroutines.launch

@Composable
fun HealthSection(
    onShareClick: (String) -> Unit = {},
    onAIClick: (() -> Unit)? = null,
    onItemAIClick: ((String, String) -> Unit)? = null
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    // State management for loading and data refresh
    var isScanning by remember { mutableStateOf(false) }
    var scanCompleted by remember { mutableStateOf(false) }
    var currentHealthScore by remember {
        mutableIntStateOf(
            HealthScoreUtils.calculateDailyHealthScore(
                context
            )
        )
    }

    // AdMob interstitial ad tracking
    // Note: InterstitialAdManager handles all checks centrally:
    // - RemoteConfig enable/disable flag
    // - Global time-based throttling
    // - Ad loading and showing

    // Native ad state
    val shouldShowNativeAds = remember { RemoteConfigUtils.shouldShowNativeAds() }
    val nativeAds = remember { NativeAdManager.nativeAds }
    var currentNativeAd by remember {
        mutableStateOf<com.google.android.gms.ads.nativead.NativeAd?>(
            null
        )
    }

    // Animation for loading spinner
    val infiniteTransition = rememberInfiniteTransition(label = "scan_loading")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    // Function to perform the actual scan
    val performScan: () -> Unit = {
        if (!isScanning) {
            AnalyticsUtils.logEvent(AnalyticsEvent.HealthScanStarted)
            coroutineScope.launch {
                try {
                    isScanning = true
                    scanCompleted = false

                    // Simulate scanning time for better UX
                    kotlinx.coroutines.delay(2000)

                    // Calculate new health score
                    val newHealthScore = HealthScoreUtils.calculateDailyHealthScore(context)
                    HealthScoreUtils.saveHealthScore(context, newHealthScore)

                    // Update UI state
                    currentHealthScore = newHealthScore
                    scanCompleted = true
                    isScanning = false
                    
                    // Log scan completion
                    AnalyticsUtils.logEvent(AnalyticsEvent.HealthScanCompleted, mapOf(
                        "health_score" to newHealthScore,
                        "streak" to HealthScoreUtils.getDailyStreak(context),
                        "best_score" to HealthScoreUtils.getBestScore(context)
                    ))
                    
                    // Upload to leaderboard after scan
                    com.teamz.lab.debugger.utils.LeaderboardDataUpload.uploadAfterHealthScan(context)
                    
                    // Generate and share health data text
                    val healthShareText = generateHealthShareText(context, newHealthScore)
                    onShareClick(healthShareText)

                    // Reset completion state after a delay
                    kotlinx.coroutines.delay(3000)
                    scanCompleted = false

                } catch (e: Exception) {
                    handleError(e)
                    isScanning = false
                }
            }
        }
    }

    // Generate share text on initial load
    LaunchedEffect(currentHealthScore) {
        val healthShareText = generateHealthShareText(context, currentHealthScore)
        onShareClick(healthShareText)
    }
    
    // Function to handle scan button click with AdMob integration
    // InterstitialAdManager handles everything centrally:
    // - Checks if ads are enabled (RemoteConfig)
    // - Enforces global throttling (time-based)
    // - Shows ad if allowed, otherwise proceeds immediately
    val handleScanClick: () -> Unit = {
        InterstitialAdManager.showAdIfAvailable(context as android.app.Activity) {
            // Ad closed or skipped - proceed with scan
            performScan()
        }
    }


    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Health Score Card
        item {
            HealthScoreCard(
                context = context,
                onScanClick = handleScanClick,
                isScanning = isScanning,
                scanCompleted = scanCompleted,
                rotation = rotation,
                onScoreClick = {
                    AnalyticsUtils.logEvent(AnalyticsEvent.HealthScoreClicked, mapOf(
                        "current_score" to currentHealthScore
                    ))
                    // Scroll to recommendations section
                    coroutineScope.launch {
                        // Find the recommendations item index (after native ad if present)
                        val recommendationsIndex = if (shouldShowNativeAds && nativeAds.isNotEmpty()) 2 else 1
                        listState.animateScrollToItem(recommendationsIndex)
                        AnalyticsUtils.logEvent(AnalyticsEvent.HealthRecommendationsViewed)
                    }
                },
                onAIClick = onAIClick
            )
        }

        // Native Ad (just above Smart Recommendations for better revenue)
        // Policy: Single native ad per screen, adequate spacing, clearly labeled
        if (shouldShowNativeAds && nativeAds.isNotEmpty()) {
            item {
                // Get a native ad from the rotation
                val nativeAd = nativeAds.firstOrNull { it != null }
                if (nativeAd != null) {
                    Spacer(modifier = Modifier.height(8.dp)) // Spacing before ad
                    AdMobNativeAdCard(nativeAd = nativeAd)
                    Spacer(modifier = Modifier.height(8.dp)) // Spacing after ad
                }
            }
        }

        // Intelligent Improvement Suggestions
        item(key = "recommendations") {
            val suggestions =
                HealthScoreUtils.getImprovementSuggestions(context, currentHealthScore)
            if (suggestions.isNotEmpty()) {
                // Add visual connection from score to recommendations
                if (currentHealthScore < 8) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowDownward,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Improvements for your score: $currentHealthScore/10",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                            fontWeight = FontWeight.Medium,
                            fontSize = 12.sp
                        )
                    }
                }
            ImprovementSuggestionsCard(
                suggestions = suggestions, 
                currentScore = currentHealthScore,
                onAIClick = onItemAIClick?.let { handler ->
                    {
                        AnalyticsUtils.logEvent(AnalyticsEvent.FabAIClicked, mapOf(
                            "source" to "health_recommendations",
                            "item_title" to "Smart Recommendations"
                        ))
                        val suggestionsText = suggestions.joinToString("\n") { "• $it" }
                        val content = """
Health Score: $currentHealthScore/10
Improvement Suggestions:
$suggestionsText
                        """.trimIndent()
                        handler("Smart Recommendations", content)
                    }
                }
            )
            }
        }

        // Removed Device Analysis - redundant with Device Info tab

        // Health History
        item {
            LaunchedEffect(Unit) {
                AnalyticsUtils.logEvent(AnalyticsEvent.HealthHistoryViewed)
            }
            HealthHistoryCard(
                context = context,
                onAIClick = onItemAIClick?.let { handler ->
                    {
                        AnalyticsUtils.logEvent(AnalyticsEvent.FabAIClicked, mapOf(
                            "source" to "health_history",
                            "item_title" to "7-Day Health History"
                        ))
                        val history = HealthScoreUtils.getHealthScoreHistory(context, 7)
                        val historyText = if (history.isNotEmpty()) {
                            history.joinToString("\n") { (date, score) -> "$date: $score/10" }
                        } else {
                            "No health history yet. Start scanning to build your history!"
                        }
                        val content = """
7-Day Health History:
$historyText

Current Score: $currentHealthScore/10
Daily Streak: ${HealthScoreUtils.getDailyStreak(context)} days
Best Score: ${HealthScoreUtils.getBestScore(context)}/10
Total Scans: ${HealthScoreUtils.getTotalScans(context)}
                        """.trimIndent()
                        handler("7-Day Health History", content)
                    }
                }
            )
        }
        item {
            Spacer(modifier = Modifier.height(40.dp))
        }

    }
}

@Composable
private fun QuickStatCard(
    title: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier
) {

    Surface(
        modifier = modifier,
        color = DesignSystemColors.NeonGreen,
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(
            2.dp,
            MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = DesignSystemColors.Dark,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                color = DesignSystemColors.Dark,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = DesignSystemColors.DarkII,
            )
        }
    }
}

@Composable
private fun PerformanceInsightsCard(
    insights: String,
    motivationalMessage: String
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(
            2.dp,
            MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
        )
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Psychology,
                    contentDescription = "Insights",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Your Performance",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = insights,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = motivationalMessage,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun ImprovementSuggestionsCard(
    suggestions: List<String>,
    currentScore: Int,
    onAIClick: (() -> Unit)? = null
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(
            2.dp,
            MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
        )
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.Lightbulb,
                    contentDescription = "Suggestions",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Smart Recommendations",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                    if (currentScore < 8) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Based on your device's actual health data",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            fontSize = 11.sp
                        )
                    }
                }
                if (onAIClick != null) {
                    IconButton(
                        onClick = onAIClick,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = com.teamz.lab.debugger.utils.AIIcon.icon,
                            contentDescription = "Get AI insights about recommendations",
                            tint = com.teamz.lab.debugger.utils.AIIcon.color(),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            suggestions.forEach { suggestion ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Tip",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = suggestion,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
            }
        }
    }
}

@Composable
private fun HealthHistoryCard(
    context: android.content.Context,
    onAIClick: (() -> Unit)? = null
) {
    val history = remember {
        HealthScoreUtils.getHealthScoreHistory(context, 7)
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(
            2.dp,
            MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
        )
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.History,
                    contentDescription = "History",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "7-Day History",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.weight(1f))
                if (onAIClick != null) {
                    IconButton(
                        onClick = onAIClick,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = com.teamz.lab.debugger.utils.AIIcon.icon,
                            contentDescription = "Get AI insights about health history",
                            tint = com.teamz.lab.debugger.utils.AIIcon.color(),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (history.isNotEmpty()) {
                history.forEach { (date, score) ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = date,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Surface(
                            color = getScoreColor(score).copy(alpha = 0.1f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = "$score/10",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = getScoreColor(score),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                }
            } else {
                Text(
                    text = "No health history yet. Start scanning to build your history!",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun getScoreColor(score: Int): Color {
    return when {
        score >= 9 -> MaterialTheme.colorScheme.primary
        score >= 7 -> MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
        score >= 5 -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
    }
}

// Device Analysis Card removed - redundant with Device Info tab 

/**
 * Generate health share text for AI and sharing
 */
private fun generateHealthShareText(context: android.content.Context, healthScore: Int): String {
    val streak = HealthScoreUtils.getDailyStreak(context)
    val bestScore = HealthScoreUtils.getBestScore(context)
    val totalScans = HealthScoreUtils.getTotalScans(context)
    val history = HealthScoreUtils.getHealthScoreHistory(context, 7)
    val suggestions = HealthScoreUtils.getImprovementSuggestions(context, healthScore)
    
    return buildString {
        appendLine("📊 DEVICE HEALTH REPORT")
        appendLine("========================")
        appendLine()
        appendLine("🏆 Current Health Score: $healthScore/100")
        appendLine()
        appendLine("📈 Health Stats:")
        appendLine("  • Daily Streak: $streak days")
        appendLine("  • Best Score: $bestScore/100")
        appendLine("  • Total Scans: $totalScans")
        appendLine()
        
        if (history.isNotEmpty()) {
            appendLine("📅 Recent History (Last 7 Days):")
            history.take(7).forEach { (date, score) ->
                appendLine("  • $date: $score/100")
            }
            appendLine()
        }
        
        if (suggestions.isNotEmpty()) {
            appendLine("💡 Improvement Suggestions:")
            suggestions.forEach { suggestion ->
                appendLine("  • $suggestion")
            }
            appendLine()
        }
        
        appendLine("Score Rating: ${getScoreRating(healthScore)}")
    }
}

private fun getScoreRating(score: Int): String {
    return when {
        score >= 90 -> "Excellent! Your device is in top condition."
        score >= 75 -> "Good! Minor improvements possible."
        score >= 60 -> "Fair. Some attention needed."
        score >= 40 -> "Needs work. Several issues detected."
        else -> "Critical. Immediate attention recommended."
    }
}