package com.teamz.lab.debugger.ui

import android.app.Activity
import android.content.Context
import android.net.ConnectivityManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.teamz.lab.debugger.utils.calculateInternetHealthScore
import com.teamz.lab.debugger.utils.string
import com.teamz.lab.debugger.R
import com.teamz.lab.debugger.utils.checkISPStreamingServers
import com.teamz.lab.debugger.utils.checkInternetPrivacyAndSurveillance
import com.teamz.lab.debugger.utils.getCaptivePortalStatus
import com.teamz.lab.debugger.utils.getDnsServers
import com.teamz.lab.debugger.utils.getGatewayAddress
import com.teamz.lab.debugger.utils.getIPv4v6Support
import com.teamz.lab.debugger.utils.getISPDetails
import com.teamz.lab.debugger.utils.getInternetUptime
import com.teamz.lab.debugger.utils.getJitter
import com.teamz.lab.debugger.utils.getLocalIPAddress
import com.teamz.lab.debugger.utils.getMobileSpeed
import com.teamz.lab.debugger.utils.getMtu
import com.teamz.lab.debugger.utils.getNetworkDownloadSpeed
import com.teamz.lab.debugger.utils.getNetworkType
import com.teamz.lab.debugger.utils.getNetworkUploadSpeed
import com.teamz.lab.debugger.utils.getNetworkUsageStatsThrottled
import com.teamz.lab.debugger.utils.getPacketLoss
import com.teamz.lab.debugger.utils.getPublicIPAddressFromIPInfo
import com.teamz.lab.debugger.utils.getWiFiInformation
import com.teamz.lab.debugger.utils.pingPopularServers
import com.teamz.lab.debugger.utils.testNetworkLatency
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun NetworkInfoSection(
    activity: Activity, 
    onShareClick: (String) -> Unit,
    onItemAIClick: ((String, String) -> Unit)? = null
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val loadingText = context.string(R.string.loading)
    
    // Network capabilities must be retrieved asynchronously to prevent ANR
    // ConnectivityManager.getActiveNetwork() is a blocking Binder call
    // We'll retrieve it in the async block and cache it

    var ipAddress by remember { mutableStateOf(loadingText) }
    var mobileSpeed by remember { mutableStateOf(loadingText) }
    var ipvSupport by remember { mutableStateOf(loadingText) }
    var packetLoss by remember { mutableStateOf(loadingText) }
    var jitter by remember { mutableStateOf(loadingText) }
    var captivePortalStatus by remember { mutableStateOf(loadingText) }
    var ispDetails by remember { mutableStateOf(loadingText) }
    var ispStreamingServers by remember { mutableStateOf(loadingText) }
    var govSurveillance by remember { mutableStateOf(loadingText) }
    var downloadSpeed by remember { mutableStateOf(loadingText) }
    var uploadSpeed by remember { mutableStateOf(loadingText) }
    var gatewayAddress by remember { mutableStateOf(loadingText) }
    var dnsServers by remember { mutableStateOf(loadingText) }
    var mtuSize by remember { mutableStateOf(loadingText) }
    var internetUptime by remember { mutableStateOf(loadingText) }
    var localIpAddress
            by remember { mutableStateOf(loadingText) }
    var wifiInfo by remember { mutableStateOf(loadingText) }
    var pingResults by remember { mutableStateOf(loadingText) }
    var networkUsageStats by remember { mutableStateOf(loadingText) }
    var healthScore by remember { mutableStateOf(loadingText) }
    var latencyText by remember { mutableStateOf(loadingText) }
    var networkType by remember { mutableStateOf(loadingText) }
    var isRefreshing by remember { mutableStateOf(false) }

    fun refreshNetworkInfo() {
        // Prevent concurrent refreshes
        if (isRefreshing) {
            return
        }
        
        // Launch on IO dispatcher to avoid blocking main thread
        coroutineScope.launch(Dispatchers.IO) {
            // Set refreshing flag on main thread
            withContext(Dispatchers.Main) {
                isRefreshing = true
            }
            
            try {
            // Get network capabilities asynchronously to prevent ANR
            // ConnectivityManager.getActiveNetwork() is a blocking Binder call that must not run on main thread
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val networkCapabilitiesDeferred = async(Dispatchers.IO) {
                try {
                    connectivityManager.activeNetwork?.let { activeNetwork ->
                        connectivityManager.getNetworkCapabilities(activeNetwork)
                    }
                } catch (e: Exception) {
                    // If network capabilities retrieval fails, continue without it
                    android.util.Log.w("NetworkInfoSection", "Failed to get network capabilities: ${e.message}", e)
                    null
                }
            }
            
            // Await network capabilities before using it
            val networkCapabilities = networkCapabilitiesDeferred.await()
            
            // All async calls explicitly use Dispatchers.IO to prevent blocking main thread
            val mobileSpeedDeferred = async(Dispatchers.IO) { getMobileSpeed(networkCapabilities) }
            val networkTypeDeferred = async(Dispatchers.IO) { getNetworkType(context) }
            val ipvSupportDeferred = async(Dispatchers.IO) { getIPv4v6Support() }
            val packetLossDeferred = async(Dispatchers.IO) { getPacketLoss() }
            val jitterDeferred = async(Dispatchers.IO) { getJitter() }
            val captivePortalStatusDeferred = async(Dispatchers.IO) { getCaptivePortalStatus(context) }
            val gatewayAddressDeferred = async(Dispatchers.IO) { getGatewayAddress() }
            val dnsServersDeferred = async(Dispatchers.IO) { getDnsServers(context) }
            val mtuSizeDeferred = async(Dispatchers.IO) { getMtu() }
            val internetUptimeDeferred = async(Dispatchers.IO) { getInternetUptime() }
            val localIpAddressDeferred = async(Dispatchers.IO) { getLocalIPAddress(context) }
            val ipAddressDeferred = async(Dispatchers.IO) { getPublicIPAddressFromIPInfo() }
            val govSurveillanceDeferred = async(Dispatchers.IO) { checkInternetPrivacyAndSurveillance() }
            val downloadSpeedDeferred = async(Dispatchers.IO) { getNetworkDownloadSpeed() }
            val uploadSpeedDeferred = async(Dispatchers.IO) { getNetworkUploadSpeed() }
            val wifiInfoDeferred = async(Dispatchers.IO) { getWiFiInformation(context) }
            val ispDetailsDeferred = async(Dispatchers.IO) { getISPDetails() }
            val streamingServersDeferred = async(Dispatchers.IO) { checkISPStreamingServers() }

            // Await these on IO thread (non-blocking for main thread)
            val packetLossText = packetLossDeferred.await()
            val jitterText = jitterDeferred.await()
            val downloadSpeedText = downloadSpeedDeferred.await()
            val uploadSpeedText = uploadSpeedDeferred.await()

            // Ensure testNetworkLatency runs on IO dispatcher to prevent blocking main thread
            val latencyDeferred = async(Dispatchers.IO) { testNetworkLatency() }
            // All async calls explicitly use Dispatchers.IO to prevent blocking main thread
            val latencyMsDeferred = async(Dispatchers.IO) {
                val regex = Regex("time=(\\d+(\\.\\d+)?)")
                val match = regex.find(latencyDeferred.await())
                match?.groups?.get(1)?.value?.toDoubleOrNull() ?: 0.0
            }

            val jitterMsDeferred = async(Dispatchers.IO) {
                val value = Regex("([\\d.]+)").find(jitterText)?.groups?.get(1)?.value?.toDoubleOrNull()
                value ?: 0.0
            }

            val packetLossPercentDeferred = async(Dispatchers.IO) {
                Regex("(\\d+)%").find(packetLossText)?.groupValues?.get(1)?.toIntOrNull() ?: 0
            }

            val downloadMbpsDeferred = async(Dispatchers.IO) {
                Regex("([\\d.]+)").find(downloadSpeedText)?.groups?.get(1)?.value?.toDoubleOrNull() ?: 0.0
            }

            val uploadMbpsDeferred = async(Dispatchers.IO) {
                Regex("([\\d.]+)").find(uploadSpeedText)?.groups?.get(1)?.value?.toDoubleOrNull() ?: 0.0
            }

            val pingDeferred = async(Dispatchers.IO) { pingPopularServers() }

            // Await all deferred values on IO thread (non-blocking for main thread)
            val mobileSpeedResult = mobileSpeedDeferred.await()
            val ipvSupportResult = ipvSupportDeferred.await()
            val packetLossResult = packetLossDeferred.await()
            val jitterResult = jitterDeferred.await()
            val captivePortalStatusResult = captivePortalStatusDeferred.await()
            val gatewayAddressResult = gatewayAddressDeferred.await()
            val dnsServersResult = dnsServersDeferred.await()
            val mtuSizeResult = mtuSizeDeferred.await()
            val internetUptimeResult = internetUptimeDeferred.await()
            val localIpAddressResult = localIpAddressDeferred.await()
            val ipAddressResult = ipAddressDeferred.await()
            val govSurveillanceResult = govSurveillanceDeferred.await()
            val downloadSpeedResult = downloadSpeedDeferred.await()
            val uploadSpeedResult = uploadSpeedDeferred.await()
            val wifiInfoResult = wifiInfoDeferred.await()
            val ispDetailsResult = ispDetailsDeferred.await()
            val ispStreamingServersResult = streamingServersDeferred.await()
            val pingResultsResult = pingDeferred.await()
            val latencyTextResult = latencyDeferred.await()
            val networkTypeResult = networkTypeDeferred.await()
            
            val latencyMsResult = latencyMsDeferred.await()
            val jitterMsResult = jitterMsDeferred.await()
            val packetLossPercentResult = packetLossPercentDeferred.await()
            val downloadMbpsResult = downloadMbpsDeferred.await()
            val uploadMbpsResult = uploadMbpsDeferred.await()
            
            val healthScoreResult = calculateInternetHealthScore(
                latencyMsResult,
                jitterMsResult,
                packetLossPercentResult,
                downloadMbpsResult,
                uploadMbpsResult
            )

            // Update all UI state in a single main thread context (non-blocking)
            withContext(Dispatchers.Main) {
                mobileSpeed = mobileSpeedResult
                ipvSupport = ipvSupportResult
                packetLoss = packetLossResult
                jitter = jitterResult
                captivePortalStatus = captivePortalStatusResult
                gatewayAddress = gatewayAddressResult
                dnsServers = dnsServersResult
                mtuSize = mtuSizeResult
                internetUptime = internetUptimeResult
                localIpAddress = localIpAddressResult
                ipAddress = ipAddressResult
                govSurveillance = govSurveillanceResult
                downloadSpeed = downloadSpeedResult
                uploadSpeed = uploadSpeedResult
                wifiInfo = wifiInfoResult
                ispDetails = ispDetailsResult
                ispStreamingServers = ispStreamingServersResult
                pingResults = pingResultsResult
                latencyText = latencyTextResult
                networkType = networkTypeResult
                healthScore = healthScoreResult
            }

            // Update network usage stats separately (may take time)
            launch(Dispatchers.IO) {
                val resultBuilder = StringBuilder()
                getNetworkUsageStatsThrottled(context) { usage ->
                    resultBuilder.append("${usage.period}\nWi-Fi: ${usage.wifiUsage} | Mobile: ${usage.mobileUsage}\n\n")
                }
                withContext(Dispatchers.Main) {
                    networkUsageStats = resultBuilder.toString().trim()
                }
            }
            } finally {
                // Reset refreshing flag
                withContext(Dispatchers.Main) {
                    isRefreshing = false
                }
            }
        }
    }


    LaunchedEffect(Unit) {
        refreshNetworkInfo()
    }

    val networkInfo = listOf(
        "Network Usage Breakdown" to networkUsageStats,
        "ISP Details" to ispDetails, // ✅ ISP Name & ASN
        "ISP Streaming/CDN Servers" to ispStreamingServers,
        "Government & ISP Surveillance Test" to govSurveillance,
        "Internet Health Score" to healthScore,
        "Mobile Data Speed" to mobileSpeed,
        "Download Speed" to downloadSpeed,
        "Upload Speed" to uploadSpeed,
        "Network Packet Loss" to packetLoss,
        "Connection Stability (Jitter)" to jitter,
        "Response Speed (Latency)" to latencyText,
        "Ping Test to Popular Servers" to pingResults,
        "Internet Protocol Support" to ipvSupport,
        "Local IP Addresses" to localIpAddress,
        "Public IP Address" to ipAddress,
        "Router IP Address (Gateway)" to gatewayAddress,
        "Complete Wi-Fi Information" to wifiInfo,
        "Connected Network" to networkType,
        "Requires Login to Use Internet (Captive Portal)" to captivePortalStatus, // Non-Tech: Redirect on connect, Tech: Captive Portal
        "DNS Servers" to dnsServers, // ✅ Shows DNS Details
        "Data Packet Limit (MTU)" to mtuSize, // Tech: MTU, Non-Tech: Packet Size Limit
        "Internet Active Time" to internetUptime, // Non-Tech: How long the internet has been active
    )

    // Check if all network data is fully loaded
    // All items should have data (not equal to loadingText)
    val isFullyLoaded = remember(
        networkUsageStats, ispDetails, ispStreamingServers, govSurveillance,
        healthScore, mobileSpeed, downloadSpeed, uploadSpeed, packetLoss,
        jitter, pingResults, ipvSupport, localIpAddress, ipAddress,
        gatewayAddress, wifiInfo, captivePortalStatus, dnsServers,
        mtuSize, internetUptime
    ) {
        networkInfo.all { (_, content) -> 
            content.isNotEmpty() && content != loadingText
        }
    }

    // Generate share content only when ALL data is fully loaded
    val shareContent = if (isFullyLoaded) {
        networkInfo.joinToString("\n\n") { (title, content) ->
            "$title\n$content"
        }
    } else {
        loadingText
    }

    // Only call onShareClick when ALL data is fully loaded
    LaunchedEffect(isFullyLoaded, shareContent) {
        if (isFullyLoaded && shareContent != loadingText) {
            onShareClick(shareContent)
        }
    }

    ExpandableInfoList(
        infoList = networkInfo, 
        activity = activity,
        onItemAIClick = if (isFullyLoaded) onItemAIClick else null,
        headerContent = {
            NetworkPrivacyReportCard(
                onShareClick = { /* Sharing handled internally via ViralShareDialog */ },
                onAIClick = onItemAIClick
            )
            NetworkReachabilityCard(
                onAIClick = onItemAIClick
            )
        }
    )
}
