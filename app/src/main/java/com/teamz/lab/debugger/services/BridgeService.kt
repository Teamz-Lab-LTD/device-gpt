package com.teamz.lab.debugger.services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.content.pm.ServiceInfo
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.teamz.lab.debugger.MainActivity
import com.teamz.lab.debugger.R
import com.teamz.lab.debugger.utils.AnalyticsEvent
import com.teamz.lab.debugger.utils.AnalyticsUtils
import com.teamz.lab.debugger.utils.BridgePinGenerator
import com.teamz.lab.debugger.utils.ErrorHandler
import com.teamz.lab.debugger.utils.LanIpResolver
import com.teamz.lab.debugger.utils.LocaleManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Foreground service that owns the AI Bridge HTTP server + mDNS advertisement.
 *
 * Lifecycle:
 *   1. UI calls [start] → service comes up, [BridgeState.Starting] flows.
 *   2. Once bound to a LAN interface, PIN generated, NanoHTTPD listening, mDNS registered
 *      → [BridgeState.On] flows.
 *   3. Every authorised request resets [idleHandler]. After [IDLE_TIMEOUT_MS] with no
 *      request, the service self-stops → [BridgeState.Off] flows.
 *   4. UI calls [stop] at any time → service tears down cleanly.
 *
 * Two failure modes the plan explicitly asked to handle honestly:
 *   - **No LAN** (cellular-only): resolve step 2 to [BridgeState.Error] with a plain-English
 *     reason. Do NOT bind to `0.0.0.0` and hope — that would silently accept nothing.
 *   - **Port already in use**: NanoHTTPD throws; catch, surface as Error, do not crash the app.
 *
 * State flow is a companion object because the Compose card composes and disposes independently
 * of the service's lifecycle — a fresh recomposition after a config change must see the current
 * state without re-binding.
 */
class BridgeService : Service() {

    companion object {
        const val BRIDGE_PORT = 8787
        const val MDNS_SERVICE_TYPE = "_devicegpt-bridge._tcp."
        const val MDNS_SERVICE_NAME = "DeviceGPT Bridge"
        const val IDLE_TIMEOUT_MS = 10 * 60 * 1000L // 10 minutes — matches the plan

        private const val CHANNEL_ID = "ai_bridge_channel"
        private const val NOTIFICATION_ID = 2001
        private const val ACTION_STOP = "com.teamz.lab.debugger.ai_bridge.STOP"

        private val _state = MutableStateFlow<BridgeState>(BridgeState.Off)
        val state: StateFlow<BridgeState> = _state.asStateFlow()

        fun start(context: Context) {
            val intent = Intent(context, BridgeService::class.java)
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    ContextCompat.startForegroundService(context, intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                ErrorHandler.handleError(e, context = "BridgeService.start")
                _state.value = BridgeState.Error(
                    reason = LocaleManager.localizedContext(context).getString(R.string.mx_bridge_error_start),
                )
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, BridgeService::class.java))
        }
    }

    sealed class BridgeState {
        data object Off : BridgeState()
        data object Starting : BridgeState()
        data class On(
            val urlDisplay: String,
            val pin: String,
            val startedAtMs: Long,
            val requestCount: Int,
            val lastRequestAtMs: Long?,
        ) : BridgeState()
        data class Error(val reason: String) : BridgeState()
    }

    private var httpServer: BridgeHttpServer? = null
    private var mdnsRegistration: NsdManager.RegistrationListener? = null
    private var currentPin: String? = null
    private var currentUrl: String? = null
    private var startedAtMs: Long = 0L
    private var requestCount: Int = 0
    private var lastRequestAtMs: Long? = null

    private val mainHandler by lazy { Handler(Looper.getMainLooper()) }
    private val idleShutdownRunnable = Runnable {
        AnalyticsUtils.logEvent(AnalyticsEvent.AiBridgeAutoShutdown, mapOf(
            "requests_served" to requestCount,
            "uptime_ms" to (System.currentTimeMillis() - startedAtMs),
        ))
        stopSelf()
    }

    /** Shows this service's notification text in the app language. */
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleManager.wrapContext(newBase))
    }

    /** Follows a language switch made while the service is running. */
    override fun getResources(): Resources {
        val base = baseContext ?: return super.getResources()
        return LocaleManager.localizedResources(base)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        _state.value = BridgeState.Starting

        try {
            createChannel()
            // Declare BOTH types (dataSync for the HTTP+mDNS server, camera for /test_camera_open
            // + future camera diagnostics). ServiceCompat picks the right overload per API level.
            // On Android 14+ camera opens from a service REQUIRE the type mask at startForeground
            // time — declaring in the manifest is not enough.
            val serviceType = if (Build.VERSION.SDK_INT >= 30) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC or
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
            } else {
                0
            }
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                buildNotification(getString(R.string.mx_bridge_status_starting), showStop = false),
                serviceType,
            )
        } catch (e: android.app.ForegroundServiceStartNotAllowedException) {
            _state.value = BridgeState.Error(reason = getString(R.string.mx_bridge_error_open_app))
            stopSelf()
            return
        } catch (e: Exception) {
            ErrorHandler.handleError(e, context = "BridgeService.startForeground")
            _state.value = BridgeState.Error(reason = getString(R.string.mx_bridge_error_notification))
            stopSelf()
            return
        }

        val lanIp = LanIpResolver.getLanIpv4()
        if (lanIp == null) {
            _state.value = BridgeState.Error(reason = getString(R.string.mx_bridge_error_no_wifi))
            stopSelf()
            return
        }

        val pin = BridgePinGenerator.generate()
        val urlDisplay = "http://$lanIp:$BRIDGE_PORT"

        try {
            val server = BridgeHttpServer(
                context = applicationContext,
                port = BRIDGE_PORT,
                expectedPin = pin,
                onRequestServed = { endpoint -> onRequestServed(endpoint) },
            )
            server.start(NanoHttpdStartTimeout, false)
            httpServer = server
        } catch (e: Exception) {
            ErrorHandler.handleError(e, context = "BridgeService.startHttp")
            _state.value = BridgeState.Error(
                reason = getString(R.string.mx_bridge_error_port_busy, BRIDGE_PORT.toString()),
            )
            stopSelf()
            return
        }

        registerMdns()

        currentPin = pin
        currentUrl = urlDisplay
        startedAtMs = System.currentTimeMillis()
        requestCount = 0
        lastRequestAtMs = null
        _state.value = BridgeState.On(urlDisplay, pin, startedAtMs, 0, null)

        updateNotification(getString(R.string.mx_bridge_status_ready))
        resetIdleTimer()

        AnalyticsUtils.logEvent(AnalyticsEvent.AiBridgeToggled, mapOf(
            "on" to true,
            "url_display" to urlDisplay,
        ))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            AnalyticsUtils.logEvent(AnalyticsEvent.AiBridgeToggled, mapOf(
                "on" to false,
                "trigger" to "notification_stop_action",
            ))
            stopSelf()
            return START_NOT_STICKY
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        mainHandler.removeCallbacks(idleShutdownRunnable)
        try {
            httpServer?.stop()
        } catch (e: Exception) {
            android.util.Log.w("BridgeService", "http stop failed: ${e.message}")
        }
        httpServer = null

        try {
            mdnsRegistration?.let { reg ->
                val nsd = getSystemService(Context.NSD_SERVICE) as NsdManager
                nsd.unregisterService(reg)
            }
        } catch (e: Exception) {
            android.util.Log.w("BridgeService", "mdns unregister failed: ${e.message}")
        }
        mdnsRegistration = null

        AnalyticsUtils.logEvent(AnalyticsEvent.AiBridgeToggled, mapOf(
            "on" to false,
            "final_request_count" to requestCount,
            "uptime_ms" to (System.currentTimeMillis() - startedAtMs),
        ))

        _state.value = BridgeState.Off
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(Service.STOP_FOREGROUND_REMOVE)
            } else {
                @Suppress("DEPRECATION") stopForeground(true)
            }
        } catch (_: Exception) { /* best-effort */ }
        super.onDestroy()
    }

    // ─────────────────────── internal ───────────────────────

    private fun onRequestServed(endpoint: String) {
        requestCount++
        lastRequestAtMs = System.currentTimeMillis()
        val urlDisplay = currentUrl ?: return
        val pin = currentPin ?: return
        _state.value = BridgeState.On(urlDisplay, pin, startedAtMs, requestCount, lastRequestAtMs)
        resetIdleTimer()
        AnalyticsUtils.logEvent(AnalyticsEvent.AiBridgeClientConnected, mapOf(
            "endpoint" to endpoint,
            "request_index" to requestCount,
        ))
    }

    private fun resetIdleTimer() {
        mainHandler.removeCallbacks(idleShutdownRunnable)
        mainHandler.postDelayed(idleShutdownRunnable, IDLE_TIMEOUT_MS)
    }

    private fun registerMdns() {
        try {
            val nsd = getSystemService(Context.NSD_SERVICE) as NsdManager
            val info = NsdServiceInfo().apply {
                serviceName = MDNS_SERVICE_NAME
                serviceType = MDNS_SERVICE_TYPE
                port = BRIDGE_PORT
            }
            val listener = object : NsdManager.RegistrationListener {
                override fun onServiceRegistered(info: NsdServiceInfo?) { /* no-op */ }
                override fun onRegistrationFailed(info: NsdServiceInfo?, errorCode: Int) {
                    android.util.Log.w("BridgeService", "mdns register failed: $errorCode")
                }
                override fun onServiceUnregistered(info: NsdServiceInfo?) { /* no-op */ }
                override fun onUnregistrationFailed(info: NsdServiceInfo?, errorCode: Int) { /* no-op */ }
            }
            nsd.registerService(info, NsdManager.PROTOCOL_DNS_SD, listener)
            mdnsRegistration = listener
        } catch (e: Exception) {
            // mDNS is nice-to-have; the URL+PIN card still works via manual entry.
            android.util.Log.w("BridgeService", "mdns register threw: ${e.message}")
        }
    }

    private fun updateNotification(status: String) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID, buildNotification(status, showStop = true))
    }

    private fun buildNotification(status: String, showStop: Boolean): android.app.Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("navigate_to_tab", "ai_bridge")
        }
        val openPending = PendingIntent.getActivity(
            this, 0, openIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.ai_bridge_notification_title))
            .setContentText(status)
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setContentIntent(openPending)
            .setOngoing(true)
            .setSilent(true)

        if (showStop) {
            val stopIntent = Intent(this, BridgeService::class.java).apply { action = ACTION_STOP }
            val stopPending = PendingIntent.getService(
                this, 0, stopIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                getString(R.string.ai_bridge_notification_stop),
                stopPending
            )
        }
        return builder.build()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val existing = nm.getNotificationChannel(CHANNEL_ID)
            if (existing == null) {
                val ch = NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.ai_bridge_channel_name),
                    NotificationManager.IMPORTANCE_LOW,
                ).apply {
                    description = getString(R.string.ai_bridge_channel_description)
                    setShowBadge(false)
                }
                nm.createNotificationChannel(ch)
            }
        }
    }
}

// NanoHTTPD uses -1 to mean "no socket read timeout". The default (5s) can drop a slow
// laptop that opens the connection and pauses to prompt the user for the PIN.
private const val NanoHttpdStartTimeout = -1
