package com.teamz.lab.debugger.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.foundation.layout.Box
import com.teamz.lab.debugger.ui.theme.DesignSystemColors
import com.teamz.lab.debugger.utils.HealthScoreUtils
import com.teamz.lab.debugger.utils.handleError
import com.teamz.lab.debugger.utils.InterstitialAdManager
import com.teamz.lab.debugger.utils.RemoteConfigUtils
import com.teamz.lab.debugger.utils.AnalyticsUtils
import com.teamz.lab.debugger.utils.AnalyticsEvent
import com.teamz.lab.debugger.utils.ReviewPromptManager
import kotlinx.coroutines.launch
import com.teamz.lab.debugger.utils.calculatePrivacyScore
import com.teamz.lab.debugger.utils.getPrivacyThreatsToday
import com.teamz.lab.debugger.utils.getRecentCameraMicUsageLog
import com.teamz.lab.debugger.utils.AIIcon

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
    var currentHealthScore by remember { mutableIntStateOf(0) }
    
    // Calculate health score asynchronously since it's a suspend function
    LaunchedEffect(Unit) {
        currentHealthScore = HealthScoreUtils.calculateDailyHealthScore(context)
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
                    
                    // Track meaningful interaction for review prompt (after positive experience)
                    if (context is android.app.Activity) {
                        ReviewPromptManager.trackMeaningfulInteraction(context, "health_scan_completed")
                    }
                    
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
                // Use position-specific ad to ensure proper rotation
                val nativeAd = NativeAdManager.getAdForPosition("health_section_top")
                if (nativeAd != null) {
                    // Logging reduced - only log once
                    LaunchedEffect(nativeAd.hashCode()) {
                        android.util.Log.d("AdDisplay", "📺 Health section ad - " +
                                "Ad hash: ${nativeAd.hashCode()}, Total ads: ${NativeAdManager.nativeAds.filterNotNull().size}")
                    }
                    Spacer(modifier = Modifier.height(8.dp)) // Spacing before ad
                    AdMobNativeAdCard(nativeAd = nativeAd)
                    Spacer(modifier = Modifier.height(8.dp)) // Spacing after ad
                }
            }
        }

        // Privacy Dashboard Card
        item(key = "privacy_dashboard") {
            var privacyScore by remember { mutableIntStateOf(0) }
            var threats by remember { mutableStateOf<List<String>>(emptyList()) }
            var recentUsage by remember { mutableStateOf("") }
            
            LaunchedEffect(Unit) {
                privacyScore = kotlinx.coroutines.runBlocking {
                    calculatePrivacyScore(context)
                }
                threats = getPrivacyThreatsToday(context)
                recentUsage = getRecentCameraMicUsageLog()
            }
            
            PrivacyDashboardCard(
                context = context,
                privacyScore = privacyScore,
                threats = threats,
                recentUsage = recentUsage,
                onAIClick = onItemAIClick?.let { handler ->
                    {
                        val content = """
Privacy Score: $privacyScore/100
Threats Today: ${if (threats.isEmpty()) "None" else threats.joinToString(", ")}
Recent Mic/Camera Usage:
$recentUsage
                        """.trimIndent()
                        handler("Privacy Dashboard", content)
                    }
                }
            )
        }

        // Native Ad (after Privacy Dashboard)
        if (shouldShowNativeAds && nativeAds.isNotEmpty()) {
            item {
                val nativeAd = NativeAdManager.getAdForPosition("health_section_privacy")
                if (nativeAd != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    AdMobNativeAdCard(nativeAd = nativeAd)
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }

        // Today's Tasks Card
        item(key = "daily_tasks") {
            var dailyTasks by remember { mutableStateOf<List<HealthScoreUtils.DailyTask>>(emptyList()) }
            var completedCount by remember { mutableIntStateOf(0) }
            
            LaunchedEffect(currentHealthScore) {
                dailyTasks = HealthScoreUtils.getDailyTasks(context, currentHealthScore)
                completedCount = HealthScoreUtils.getCompletedTasksCount(context)
            }
            
            // Also update when tasks are completed
            LaunchedEffect(Unit) {
                dailyTasks = HealthScoreUtils.getDailyTasks(context, currentHealthScore)
                completedCount = HealthScoreUtils.getCompletedTasksCount(context)
            }
            
            if (dailyTasks.isNotEmpty()) {
                DailyTasksCard(
                    tasks = dailyTasks,
                    completedCount = completedCount,
                    onTaskComplete = { taskId ->
                        HealthScoreUtils.completeTask(context, taskId)
                        completedCount = HealthScoreUtils.getCompletedTasksCount(context)
                        AnalyticsUtils.logEvent(AnalyticsEvent.AchievementUnlocked, mapOf(
                            "task_id" to taskId,
                            "tasks_completed" to completedCount
                        ))
                    },
                    onAIClick = onItemAIClick?.let { handler ->
                        {
                            val tasksText = dailyTasks.joinToString("\n") { 
                                "${if (HealthScoreUtils.isTaskCompleted(context, it.id)) "✅" else "⬜"} ${it.title}"
                            }
                            val content = """
Today's Tasks (${completedCount}/${dailyTasks.size} completed):
$tasksText

Health Score: $currentHealthScore/10
                            """.trimIndent()
                            handler("Today's Tasks", content)
                        }
                    }
                )
            }
        }

        // Native Ad (after Today's Tasks)
        if (shouldShowNativeAds && nativeAds.isNotEmpty()) {
            item {
                val nativeAd = NativeAdManager.getAdForPosition("health_section_tasks")
                if (nativeAd != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    AdMobNativeAdCard(nativeAd = nativeAd)
                    Spacer(modifier = Modifier.height(8.dp))
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

        // Native Ad (after Recommendations)
        if (shouldShowNativeAds && nativeAds.isNotEmpty()) {
            item {
                val nativeAd = NativeAdManager.getAdForPosition("health_section_recommendations")
                if (nativeAd != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    AdMobNativeAdCard(nativeAd = nativeAd)
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }

        // Removed Device Analysis - redundant with Device Info tab

        // Temperature History Card
        item {
            TemperatureHistoryCard(
                context = context,
                onAIClick = onItemAIClick?.let { handler ->
                    {
                        val history = HealthScoreUtils.getTemperatureHistory(context, 7)
                        val peakTemp = HealthScoreUtils.getPeakTemperature(context)
                        val trend = HealthScoreUtils.getTemperatureTrend(context)
                        val historyText = if (history.isNotEmpty()) {
                            history.joinToString("\n") { 
                                "${it.date}: Max ${it.maxTemp.toInt()}°C, Avg ${it.avgTemp.toInt()}°C"
                            }
                        } else {
                            "No temperature history yet."
                        }
                        val content = """
7-Day Temperature History:
$historyText

Peak Temperature: ${peakTemp?.toInt() ?: "N/A"}°C
Trend: $trend
                        """.trimIndent()
                        handler("Temperature History", content)
                    }
                }
            )
        }

        // Native Ad (after Temperature History)
        if (shouldShowNativeAds && nativeAds.isNotEmpty()) {
            item {
                val nativeAd = NativeAdManager.getAdForPosition("health_section_temperature")
                if (nativeAd != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    AdMobNativeAdCard(nativeAd = nativeAd)
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }

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
private fun DailyTasksCard(
    tasks: List<HealthScoreUtils.DailyTask>,
    completedCount: Int,
    onTaskComplete: (String) -> Unit,
    onAIClick: (() -> Unit)? = null
) {
    val context = LocalContext.current
    
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(
            2.dp,
            MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
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
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Tasks",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Today's Tasks",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "$completedCount/${tasks.size} completed",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        fontSize = 11.sp
                    )
                }
                if (onAIClick != null) {
                    IconButton(
                        onClick = onAIClick,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = com.teamz.lab.debugger.utils.AIIcon.icon,
                            contentDescription = "Get AI insights about tasks",
                            tint = com.teamz.lab.debugger.utils.AIIcon.color(),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Progress bar
            LinearProgressIndicator(
                progress = { completedCount.toFloat() / tasks.size.toFloat() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            tasks.forEach { task ->
                val isCompleted = HealthScoreUtils.isTaskCompleted(context, task.id)
                
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Checkbox(
                        checked = isCompleted,
                        onCheckedChange = { checked ->
                            if (checked && !isCompleted) {
                                onTaskComplete(task.id)
                            }
                        },
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = task.icon,
                                fontSize = 16.sp
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = task.title,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (task.priority == HealthScoreUtils.TaskPriority.HIGH) FontWeight.Bold else FontWeight.Normal,
                                color = if (isCompleted) 
                                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                else 
                                    MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        if (!isCompleted) {
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = task.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                fontSize = 11.sp
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
            }
            
            if (completedCount == tasks.size && tasks.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "🎉 All tasks completed! Great job!",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                            .fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

@Composable
private fun TemperatureHistoryCard(
    context: android.content.Context,
    onAIClick: (() -> Unit)? = null
) {
    val history = remember {
        HealthScoreUtils.getTemperatureHistory(context, 7)
    }
    val peakTemp = remember {
        HealthScoreUtils.getPeakTemperature(context)
    }
    val trend = remember {
        HealthScoreUtils.getTemperatureTrend(context)
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
                    imageVector = Icons.Default.Thermostat,
                    contentDescription = "Temperature",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Temperature History",
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
                            contentDescription = "Get AI insights about temperature",
                            tint = com.teamz.lab.debugger.utils.AIIcon.color(),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            if (history.isNotEmpty()) {
                // Peak temperature alert
                peakTemp?.let { peak ->
                    if (peak > 40f) {
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "⚠️ Peak: ${peak.toInt()}°C this week",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
                
                // Trend indicator
                if (trend.isNotEmpty() && trend != "Not enough data") {
                    Text(
                        text = trend,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
                
                // Simple temperature graph (text-based bars)
                history.forEach { dataPoint ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = dataPoint.date,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 11.sp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        // Temperature bar visualization
                        val barWidth = ((dataPoint.avgTemp / 50f) * 100f).coerceIn(0f, 100f).toInt()
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(
                                        when {
                                            dataPoint.avgTemp > 40f -> MaterialTheme.colorScheme.error
                                            dataPoint.avgTemp > 35f -> MaterialTheme.colorScheme.primary
                                            else -> MaterialTheme.colorScheme.primaryContainer
                                        }
                                    )
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "${dataPoint.avgTemp.toInt()}°C",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 11.sp
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                }
            } else {
                Text(
                    text = "No temperature history yet. Temperature will be tracked automatically.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center
                )
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

@Composable
private fun PrivacyDashboardCard(
    context: android.content.Context,
    privacyScore: Int,
    threats: List<String>,
    recentUsage: String,
    onAIClick: (() -> Unit)? = null
) {
    val hasRecentUsage = recentUsage.contains("Recent usage detected")
    
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(
            2.dp,
            MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
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
                    imageVector = Icons.Default.Security,
                    contentDescription = "Privacy",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Privacy Dashboard",
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
                            imageVector = AIIcon.icon,
                            contentDescription = "Get AI insights about privacy",
                            tint = AIIcon.color(),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Privacy Score
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Privacy Score",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Surface(
                    color = when {
                        privacyScore >= 80 -> MaterialTheme.colorScheme.primaryContainer
                        privacyScore >= 60 -> MaterialTheme.colorScheme.secondaryContainer
                        else -> MaterialTheme.colorScheme.errorContainer
                    },
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = "$privacyScore/100",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = when {
                            privacyScore >= 80 -> MaterialTheme.colorScheme.onPrimaryContainer
                            privacyScore >= 60 -> MaterialTheme.colorScheme.onSecondaryContainer
                            else -> MaterialTheme.colorScheme.onErrorContainer
                        },
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Threats Today
            if (threats.isNotEmpty()) {
                Text(
                    text = "Threats Today:",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                threats.take(3).forEach { threat ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "Threat",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = threat,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 11.sp,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                }
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Safe",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "No threats detected today",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp
                    )
                }
            }
            
            // Recent Mic/Camera Usage
            if (hasRecentUsage) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = "Recent usage",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Recent mic/camera access detected",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}