package com.teamz.lab.debugger.services

import android.app.ActivityManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.HandlerThread
import android.os.PowerManager
import android.os.StatFs
import android.os.SystemClock
import android.util.DisplayMetrics
import android.view.Display
import android.view.Surface
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.teamz.lab.debugger.R
import com.teamz.lab.debugger.utils.LanIpResolver
import com.teamz.lab.debugger.utils.handleError
import fi.iki.elonen.NanoHTTPD
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URL
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.random.Random

/**
 * Local HTTP server for the AI Bridge tab.
 *
 * Scope guardrails (locked in the plan; do NOT relax without new evidence + owner sign-off):
 *   - Every request must carry a matching `X-Bridge-Pin` header — a wrong / missing PIN
 *     returns 401 AND does not reset the parent [BridgeService] idle-timer (so a neighbour
 *     probing the port cannot keep the service alive by spamming requests).
 *   - Every request's remote IP must be an RFC1918 / link-local address ([LanIpResolver.isLanAddress]).
 *     Even though the server binds only to a wlan interface, a hotel WiFi without client-isolation
 *     could route a public source IP to us; reject rather than serve.
 *   - No endpoint may mutate a system setting Google Play scrutinises (SMS, contacts, call log,
 *     wallpapers, WRITE_SETTINGS, accessibility). See plan §1.
 *   - No endpoint may return a "quality/health score" for the camera or any hardware — same
 *     Deceptive-Behavior policy line the app already got hit on (`a44b84b`). Facts only.
 *
 * Callbacks:
 *   - [onRequestServed] fires exactly once per authorised (200 or 501) request so the parent
 *     service can reset its idle-timer and increment its analytics counter.
 *   - Unauthorised (401) / rejected (403) requests do NOT fire the callback — intentional,
 *     see the "keep-alive" note above.
 */
class BridgeHttpServer(
    private val context: Context,
    port: Int,
    private val expectedPin: String,
    private val onRequestServed: (endpoint: String) -> Unit,
) : NanoHTTPD(port) {

    companion object {
        const val PIN_HEADER = "x-bridge-pin"
        const val VERSION = "1.0.0"
    }

    override fun serve(session: IHTTPSession): Response {
        val uri = session.uri ?: "/"
        val method = session.method?.name ?: "GET"

        // NanoHTTPD's remoteHostName is the resolved name (may be "localhost"), remoteIpAddress
        // is the raw string. Loopback via adb-forward reports "127.0.0.1"; localhost sometimes
        // resolves. Check IP first (never DNS), and log what we see so a real rejection can be
        // diagnosed instead of debugged blind.
        val remoteAddr = session.remoteIpAddress ?: session.remoteHostName ?: ""
        if (remoteAddr.isNotEmpty() && !LanIpResolver.isLanAddress(remoteAddr)) {
            android.util.Log.w("BridgeHttpServer", "Rejected non-LAN source: '$remoteAddr' (hostName='${session.remoteHostName}')")
            return json(Response.Status.FORBIDDEN, jsonError("non-LAN source address rejected"))
        }

        // `/health` is intentionally the only unauthenticated endpoint — the MCP wrapper
        // uses it to confirm reachability before prompting the user for the PIN.
        if (uri == "/health" && method == "GET") {
            onRequestServed(uri)
            return json(Response.Status.OK, JSONObject().apply {
                put("ok", true)
                put("version", VERSION)
            })
        }

        val pin = session.headers?.get(PIN_HEADER)
        if (pin != expectedPin) {
            return json(Response.Status.UNAUTHORIZED, jsonError("missing or wrong X-Bridge-Pin"))
        }

        return try {
            val body = when {
                // Original v1 endpoints (a972e63)
                uri == "/device" && method == "GET" -> deviceInfo()
                uri == "/battery" && method == "GET" -> batteryInfo()
                uri == "/storage" && method == "GET" -> storageInfo()
                uri == "/network" && method == "GET" -> networkInfo()
                uri == "/camera" && method == "GET" -> cameraInfo()
                uri == "/sensors" && method == "GET" -> sensorsInfo()
                uri == "/apps" && method == "GET" -> installedApps()
                uri == "/flashlight" && method == "POST" -> toggleFlashlight(session)
                uri == "/launch_app" && method == "POST" -> launchApp(session)

                // Phase A — safe info reads (v2)
                uri == "/sensors_snapshot" && method == "GET" -> sensorsSnapshot()
                uri == "/wifi_scan" && method == "GET" -> wifiScan()
                uri == "/permissions_status" && method == "GET" -> permissionsStatus()
                uri == "/cpu_info" && method == "GET" -> cpuInfo()
                uri == "/thermal_state" && method == "GET" -> thermalState()
                uri == "/memory_info" && method == "GET" -> memoryInfo()
                uri == "/uptime" && method == "GET" -> uptime()
                uri == "/screen_info" && method == "GET" -> screenInfo()

                // Phase B — safe actions (v2)
                uri == "/notify" && method == "POST" -> postLocalNotification(session)
                uri == "/copy_to_clipboard" && method == "POST" -> copyToClipboard(session)
                uri == "/open_url" && method == "POST" -> openUrl(session)
                uri == "/share_text" && method == "POST" -> shareText(session)
                uri == "/dial" && method == "POST" -> dial(session)

                // Phase C — auto-runnable tests (v2)
                uri == "/test_storage_write" && method == "POST" -> testStorageWrite(session)
                uri == "/test_storage_read_latency" && method == "POST" -> testStorageReadLatency(session)
                uri == "/test_dns_latency" && method == "POST" -> testDnsLatency()
                uri == "/test_network_speed" && method == "POST" -> testNetworkSpeed()
                uri == "/test_camera_open" && method == "POST" -> testCameraOpen()
                uri == "/test_battery_drain_rate" && method == "POST" -> testBatteryDrainRate()

                else -> return json(Response.Status.NOT_FOUND, jsonError("no endpoint at $method $uri"))
            }
            onRequestServed(uri)
            json(Response.Status.OK, body)
        } catch (e: Exception) {
            handleError(e, context = "BridgeHttpServer.$uri")
            json(Response.Status.INTERNAL_ERROR, jsonError(e.message ?: e.javaClass.simpleName))
        }
    }

    // ────────────────────────────── endpoints ──────────────────────────────

    private fun deviceInfo(): JSONObject = JSONObject().apply {
        put("model", Build.MODEL)
        put("manufacturer", Build.MANUFACTURER)
        put("brand", Build.BRAND)
        put("device", Build.DEVICE)
        put("product", Build.PRODUCT)
        put("android_version", Build.VERSION.RELEASE)
        put("sdk_int", Build.VERSION.SDK_INT)
        put("board", Build.BOARD)
        put("hardware", Build.HARDWARE)
        put("supported_abis", JSONArray(Build.SUPPORTED_ABIS.toList()))
    }

    private fun batteryInfo(): JSONObject {
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val intent = context.registerReceiver(null, android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val temperatureTenthC = intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1) ?: -1
        val voltageMv = intent?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1) ?: -1
        val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val plugged = intent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1) ?: -1
        val healthCode = intent?.getIntExtra(BatteryManager.EXTRA_HEALTH, -1) ?: -1
        return JSONObject().apply {
            put("percent", bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY))
            put("is_charging", status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL)
            put("plug_type", when (plugged) {
                BatteryManager.BATTERY_PLUGGED_AC -> "ac"
                BatteryManager.BATTERY_PLUGGED_USB -> "usb"
                BatteryManager.BATTERY_PLUGGED_WIRELESS -> "wireless"
                else -> "unplugged"
            })
            put("temperature_celsius", if (temperatureTenthC > 0) temperatureTenthC / 10.0 else JSONObject.NULL)
            put("voltage_mv", if (voltageMv > 0) voltageMv else JSONObject.NULL)
            put("health", when (healthCode) {
                BatteryManager.BATTERY_HEALTH_GOOD -> "good"
                BatteryManager.BATTERY_HEALTH_OVERHEAT -> "overheat"
                BatteryManager.BATTERY_HEALTH_DEAD -> "dead"
                BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "over_voltage"
                BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> "unspecified_failure"
                BatteryManager.BATTERY_HEALTH_COLD -> "cold"
                else -> "unknown"
            })
        }
    }

    private fun storageInfo(): JSONObject {
        val internal = StatFs(Environment.getDataDirectory().path)
        return JSONObject().apply {
            put("internal_total_bytes", internal.blockCountLong * internal.blockSizeLong)
            put("internal_free_bytes", internal.availableBlocksLong * internal.blockSizeLong)
        }
    }

    private fun networkInfo(): JSONObject = JSONObject().apply {
        put("lan_ipv4", LanIpResolver.getLanIpv4() ?: JSONObject.NULL)
        val connectivity = context.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
        val activeNetwork = connectivity.activeNetwork
        val caps = activeNetwork?.let { connectivity.getNetworkCapabilities(it) }
        put("has_internet", caps?.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET) == true &&
            caps.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true)
        put("is_wifi", caps?.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) == true)
        put("is_cellular", caps?.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR) == true)
    }

    private fun cameraInfo(): JSONObject {
        val cm = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val lenses = JSONArray()
        for (id in cm.cameraIdList) {
            val c = cm.getCameraCharacteristics(id)
            val facing = when (c.get(CameraCharacteristics.LENS_FACING)) {
                CameraCharacteristics.LENS_FACING_FRONT -> "front"
                CameraCharacteristics.LENS_FACING_BACK -> "back"
                CameraCharacteristics.LENS_FACING_EXTERNAL -> "external"
                else -> "unknown"
            }
            val focalLengths = c.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)?.toList() ?: emptyList()
            val hasFlash = c.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) ?: false
            lenses.put(JSONObject().apply {
                put("id", id)
                put("facing", facing)
                put("focal_lengths_mm", JSONArray(focalLengths))
                put("has_flash", hasFlash)
            })
        }
        return JSONObject().put("lenses", lenses)
    }

    private fun sensorsInfo(): JSONObject {
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as android.hardware.SensorManager
        val list = JSONArray()
        for (s in sm.getSensorList(android.hardware.Sensor.TYPE_ALL)) {
            list.put(JSONObject().apply {
                put("name", s.name)
                put("vendor", s.vendor)
                put("type", s.type)
                put("power_ma", s.power.toDouble())
            })
        }
        return JSONObject().put("sensors", list)
    }

    private fun installedApps(): JSONObject {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_LAUNCHER) }
        val list = JSONArray()
        for (info in pm.queryIntentActivities(intent, 0)) {
            val pkg = info.activityInfo.packageName
            val label = try {
                pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
            } catch (e: PackageManager.NameNotFoundException) {
                pkg
            }
            list.put(JSONObject().apply {
                put("package", pkg)
                put("label", label)
            })
        }
        return JSONObject().put("apps", list)
    }

    private fun toggleFlashlight(session: IHTTPSession): JSONObject {
        val body = readBody(session)
        val on = body.optBoolean("on", false)
        val cm = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        // Torch requires a back-facing camera that has a flash. First match wins.
        for (id in cm.cameraIdList) {
            val c = cm.getCameraCharacteristics(id)
            val hasFlash = c.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) ?: false
            val facing = c.get(CameraCharacteristics.LENS_FACING)
            if (hasFlash && facing == CameraCharacteristics.LENS_FACING_BACK) {
                cm.setTorchMode(id, on)
                return JSONObject().put("ok", true).put("on", on).put("camera_id", id)
            }
        }
        return JSONObject().put("ok", false).put("reason", "no back camera with flash")
    }

    private fun launchApp(session: IHTTPSession): JSONObject {
        val body = readBody(session)
        val pkg = body.optString("package", "")
        if (pkg.isBlank()) return JSONObject().put("ok", false).put("reason", "package required")
        val launchIntent = context.packageManager.getLaunchIntentForPackage(pkg)
            ?: return JSONObject().put("ok", false).put("reason", "no launcher for $pkg")
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(launchIntent)
        return JSONObject().put("ok", true).put("package", pkg)
    }

    // ────────────────────────────── helpers ──────────────────────────────

    private fun readBody(session: IHTTPSession): JSONObject {
        val map = HashMap<String, String>()
        return try {
            session.parseBody(map)
            val raw = map["postData"] ?: return JSONObject()
            JSONObject(raw)
        } catch (e: Exception) {
            JSONObject()
        }
    }

    private fun json(status: Response.Status, body: JSONObject): Response =
        newFixedLengthResponse(status, "application/json", body.toString())

    private fun jsonError(message: String): JSONObject =
        JSONObject().put("ok", false).put("error", message)

    // ─────────────────────── Phase A — safe info reads ───────────────────────

    /**
     * One-shot latest values of the 6 canonical sensors. Blocks up to 400ms waiting for
     * each requested sensor's first event, then returns whatever arrived. Some sensors
     * (pressure, ambient light) are missing on many phones — absent entries are `null`.
     */
    private fun sensorsSnapshot(): JSONObject {
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val wanted = mapOf(
            "accelerometer" to Sensor.TYPE_ACCELEROMETER,
            "gyroscope" to Sensor.TYPE_GYROSCOPE,
            "magnetometer" to Sensor.TYPE_MAGNETIC_FIELD,
            "light_lux" to Sensor.TYPE_LIGHT,
            "proximity_cm" to Sensor.TYPE_PROXIMITY,
            "pressure_hpa" to Sensor.TYPE_PRESSURE,
        )
        val out = JSONObject()
        val thread = HandlerThread("bridge-sensor-snap").apply { start() }
        val handler = Handler(thread.looper)
        try {
            for ((label, type) in wanted) {
                val sensor = sm.getDefaultSensor(type)
                if (sensor == null) {
                    out.put(label, JSONObject.NULL)
                    continue
                }
                val latch = CountDownLatch(1)
                var captured: FloatArray? = null
                val listener = object : SensorEventListener {
                    override fun onSensorChanged(event: SensorEvent) {
                        captured = event.values.copyOf()
                        latch.countDown()
                    }
                    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
                }
                // SENSOR_DELAY_UI (60ms) not FASTEST — FASTEST needs HIGH_SAMPLING_RATE_SENSORS
                // permission on Android 12+ (throws SecurityException at register time otherwise).
                sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI, handler)
                latch.await(400, TimeUnit.MILLISECONDS)
                sm.unregisterListener(listener)
                val v = captured
                if (v == null) {
                    out.put(label, JSONObject.NULL)
                } else if (v.size == 1) {
                    out.put(label, v[0].toDouble())
                } else {
                    out.put(label, JSONArray().apply { v.forEach { put(it.toDouble()) } })
                }
            }
        } finally {
            thread.quitSafely()
        }
        return out
    }

    /**
     * Cached scan results — does NOT trigger a fresh scan. WifiManager.startScan is
     * deprecated and rate-limited on Android 10+, and forcing a fresh scan for every
     * MCP call would burn the app's per-hour quota. Passive read.
     */
    private fun wifiScan(): JSONObject {
        val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val networks = JSONArray()
        try {
            @Suppress("MissingPermission")
            for (r in wm.scanResults) {
                networks.put(JSONObject().apply {
                    put("ssid", r.SSID.ifBlank { JSONObject.NULL })
                    put("bssid", r.BSSID)
                    put("rssi_dbm", r.level)
                    put("frequency_mhz", r.frequency)
                    put("capabilities", r.capabilities)
                })
            }
        } catch (e: SecurityException) {
            return JSONObject().put("ok", false).put("error", "location permission required for wifi scan")
        }
        val currentInfo = try {
            @Suppress("MissingPermission")
            wm.connectionInfo
        } catch (e: SecurityException) { null }
        return JSONObject().apply {
            put("networks_visible", networks.length())
            put("networks", networks)
            put("current_ssid", currentInfo?.ssid?.trim('"')?.takeIf { it.isNotBlank() && it != "<unknown ssid>" } ?: JSONObject.NULL)
            put("current_rssi_dbm", currentInfo?.rssi ?: JSONObject.NULL)
            put("current_link_speed_mbps", currentInfo?.linkSpeed ?: JSONObject.NULL)
        }
    }

    /**
     * All permissions declared in the manifest, with their current runtime grant state.
     * Useful for the AI to know "you'd need to grant location before wifi_scan can work".
     */
    private fun permissionsStatus(): JSONObject {
        val pm = context.packageManager
        val info: PackageInfo = pm.getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)
        val list = JSONArray()
        info.requestedPermissions?.forEachIndexed { i, name ->
            val flags = info.requestedPermissionsFlags?.getOrNull(i) ?: 0
            val granted = ContextCompat.checkSelfPermission(context, name) == PackageManager.PERMISSION_GRANTED
            list.put(JSONObject().apply {
                put("name", name)
                put("granted", granted)
                put("declared_flags", flags)
            })
        }
        return JSONObject().put("permissions", list)
    }

    /**
     * CPU cores + per-core current scaling frequency. cpufreq files may be perm-denied
     * on newer Android — return null for unreadable cores rather than failing the call.
     */
    private fun cpuInfo(): JSONObject {
        val cores = Runtime.getRuntime().availableProcessors()
        val perCore = JSONArray()
        for (i in 0 until cores) {
            val curKhz = try {
                File("/sys/devices/system/cpu/cpu$i/cpufreq/scaling_cur_freq").readText().trim().toLongOrNull()
            } catch (e: Exception) { null }
            val maxKhz = try {
                File("/sys/devices/system/cpu/cpu$i/cpufreq/cpuinfo_max_freq").readText().trim().toLongOrNull()
            } catch (e: Exception) { null }
            val minKhz = try {
                File("/sys/devices/system/cpu/cpu$i/cpufreq/cpuinfo_min_freq").readText().trim().toLongOrNull()
            } catch (e: Exception) { null }
            perCore.put(JSONObject().apply {
                put("core", i)
                put("current_khz", curKhz ?: JSONObject.NULL)
                put("max_khz", maxKhz ?: JSONObject.NULL)
                put("min_khz", minKhz ?: JSONObject.NULL)
            })
        }
        return JSONObject().apply {
            put("cores", cores)
            put("per_core", perCore)
            put("supported_abis", JSONArray(Build.SUPPORTED_ABIS.toList()))
        }
    }

    private fun thermalState(): JSONObject {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val (status, name) = if (Build.VERSION.SDK_INT >= 29) {
            val s = pm.currentThermalStatus
            s to when (s) {
                PowerManager.THERMAL_STATUS_NONE -> "none"
                PowerManager.THERMAL_STATUS_LIGHT -> "light"
                PowerManager.THERMAL_STATUS_MODERATE -> "moderate"
                PowerManager.THERMAL_STATUS_SEVERE -> "severe"
                PowerManager.THERMAL_STATUS_CRITICAL -> "critical"
                PowerManager.THERMAL_STATUS_EMERGENCY -> "emergency"
                PowerManager.THERMAL_STATUS_SHUTDOWN -> "shutdown"
                else -> "unknown"
            }
        } else {
            -1 to "unavailable_below_api_29"
        }
        return JSONObject().apply {
            put("status_code", status)
            put("status", name)
            put("is_power_save_mode", pm.isPowerSaveMode)
            put("is_device_idle_mode", pm.isDeviceIdleMode)
        }
    }

    private fun memoryInfo(): JSONObject {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val info = ActivityManager.MemoryInfo()
        am.getMemoryInfo(info)
        return JSONObject().apply {
            put("total_mem_bytes", info.totalMem)
            put("available_mem_bytes", info.availMem)
            put("low_memory_threshold_bytes", info.threshold)
            put("is_low_memory", info.lowMemory)
        }
    }

    private fun uptime(): JSONObject = JSONObject().apply {
        put("since_boot_ms", SystemClock.elapsedRealtime())
        put("since_boot_seconds", SystemClock.elapsedRealtime() / 1000)
        put("build_time_epoch_ms", Build.TIME)
        put("build_fingerprint", Build.FINGERPRINT)
    }

    @Suppress("DEPRECATION")
    private fun screenInfo(): JSONObject {
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as android.view.WindowManager
        val display: Display? = wm.defaultDisplay
        val metrics = DisplayMetrics()
        display?.getRealMetrics(metrics)
        val refreshRate = display?.refreshRate ?: 60f
        val rotation = when (display?.rotation) {
            Surface.ROTATION_0 -> "portrait_0"
            Surface.ROTATION_90 -> "landscape_90"
            Surface.ROTATION_180 -> "portrait_180"
            Surface.ROTATION_270 -> "landscape_270"
            else -> "unknown"
        }
        return JSONObject().apply {
            put("width_px", metrics.widthPixels)
            put("height_px", metrics.heightPixels)
            put("density_dpi", metrics.densityDpi)
            put("density_scale", metrics.density.toDouble())
            put("xdpi", metrics.xdpi.toDouble())
            put("ydpi", metrics.ydpi.toDouble())
            put("refresh_rate_hz", refreshRate.toDouble())
            put("rotation", rotation)
        }
    }

    // ─────────────────────── Phase B — safe actions ───────────────────────

    /**
     * Posts a local notification. Deliberately uses its own channel so the user can
     * mute MCP-triggered notifications without silencing the Bridge-running notification.
     */
    private fun postLocalNotification(session: IHTTPSession): JSONObject {
        val body = readBody(session)
        val title = body.optString("title", "").ifBlank {
            return JSONObject().put("ok", false).put("reason", "title required")
        }
        val text = body.optString("body", "").ifBlank { " " }
        val channelId = "ai_bridge_mcp_notifications"
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26 && nm.getNotificationChannel(channelId) == null) {
            nm.createNotificationChannel(NotificationChannel(
                channelId, "AI Bridge — from your AI", NotificationManager.IMPORTANCE_DEFAULT
            ).apply { description = "Notifications your laptop AI posts to your phone" })
        }
        val n = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(text)
            .setAutoCancel(true)
            .build()
        val id = 3000 + Random.nextInt(1000)
        nm.notify(id, n)
        return JSONObject().put("ok", true).put("notification_id", id)
    }

    private fun copyToClipboard(session: IHTTPSession): JSONObject {
        val body = readBody(session)
        val text = body.optString("text", "").ifBlank {
            return JSONObject().put("ok", false).put("reason", "text required")
        }
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("from AI Bridge", text))
        return JSONObject().put("ok", true).put("bytes", text.toByteArray(Charsets.UTF_8).size)
    }

    /**
     * ACTION_VIEW an http(s) URL. Refuses other schemes to prevent the AI from launching
     * arbitrary Intents (e.g. `intent://…` deep-links) that could bypass user choice.
     */
    private fun openUrl(session: IHTTPSession): JSONObject {
        val body = readBody(session)
        val url = body.optString("url", "").ifBlank {
            return JSONObject().put("ok", false).put("reason", "url required")
        }
        val lower = url.lowercase()
        if (!lower.startsWith("http://") && !lower.startsWith("https://")) {
            return JSONObject().put("ok", false).put("reason", "only http/https urls allowed")
        }
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        return JSONObject().put("ok", true).put("url", url)
    }

    private fun shareText(session: IHTTPSession): JSONObject {
        val body = readBody(session)
        val text = body.optString("text", "").ifBlank {
            return JSONObject().put("ok", false).put("reason", "text required")
        }
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        val chooser = Intent.createChooser(send, null).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
        return JSONObject().put("ok", true).put("bytes", text.toByteArray(Charsets.UTF_8).size)
    }

    /**
     * ACTION_DIAL — opens the dialer with the number pre-filled, user still taps call.
     * Never ACTION_CALL — that would dial silently and needs CALL_PHONE permission.
     */
    private fun dial(session: IHTTPSession): JSONObject {
        val body = readBody(session)
        val raw = body.optString("number", "").ifBlank {
            return JSONObject().put("ok", false).put("reason", "number required")
        }
        // Allow digits, +, -, space, parentheses, dot, #. Reject anything else.
        val clean = raw.filter { it.isDigit() || it in "+-() .#" }
        if (clean.isBlank()) {
            return JSONObject().put("ok", false).put("reason", "no dialable digits after sanitisation")
        }
        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$clean")).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        return JSONObject().put("ok", true).put("dialer_prefilled", clean)
    }

    // ─────────────────────── Phase C — auto-runnable tests ───────────────────────

    /**
     * Write `size_mb` (default 10) of random bytes to cacheDir in 1 MB chunks, measure
     * throughput, delete. Caps at 100 MB to prevent an AI-triggered fill-up.
     */
    private fun testStorageWrite(session: IHTTPSession): JSONObject {
        val body = readBody(session)
        val requestedMb = body.optInt("size_mb", 10).coerceIn(1, 100)
        val file = File(context.cacheDir, "bridge_write_probe.bin")
        val chunk = ByteArray(1024 * 1024)
        Random.nextBytes(chunk)
        val start = SystemClock.elapsedRealtime()
        try {
            file.outputStream().use { out ->
                repeat(requestedMb) { out.write(chunk) }
                out.flush()
            }
        } finally {
            file.delete()
        }
        val elapsedMs = (SystemClock.elapsedRealtime() - start).coerceAtLeast(1)
        val mbps = requestedMb * 1000.0 / elapsedMs
        return JSONObject().apply {
            put("size_mb", requestedMb)
            put("elapsed_ms", elapsedMs)
            put("throughput_mb_per_second", mbps)
        }
    }

    /**
     * Write a 4 MB file, then read 200 random 4 KB blocks. Report median + p95 latency.
     * The write cost is not included in the reported numbers.
     */
    private fun testStorageReadLatency(session: IHTTPSession): JSONObject {
        val body = readBody(session)
        val samples = body.optInt("samples", 200).coerceIn(10, 2000)
        val file = File(context.cacheDir, "bridge_read_probe.bin")
        val fileBytes = 4 * 1024 * 1024
        try {
            file.outputStream().use { out ->
                val buf = ByteArray(fileBytes)
                Random.nextBytes(buf)
                out.write(buf)
            }
            val latencies = LongArray(samples)
            val readBuf = ByteArray(4096)
            java.io.RandomAccessFile(file, "r").use { raf ->
                for (i in 0 until samples) {
                    val offset = Random.nextLong(0, fileBytes - readBuf.size.toLong())
                    val t = System.nanoTime()
                    raf.seek(offset)
                    raf.read(readBuf)
                    latencies[i] = System.nanoTime() - t
                }
            }
            latencies.sort()
            val medianUs = latencies[samples / 2] / 1000.0
            val p95Us = latencies[(samples * 0.95).toInt()] / 1000.0
            val maxUs = latencies.last() / 1000.0
            return JSONObject().apply {
                put("samples", samples)
                put("median_microseconds", medianUs)
                put("p95_microseconds", p95Us)
                put("max_microseconds", maxUs)
            }
        } finally {
            file.delete()
        }
    }

    /**
     * Sequential cold DNS resolve of 5 well-known domains. Reports per-domain ms.
     * Sequential (not parallel) so the numbers don't collide on a single resolver socket.
     */
    private fun testDnsLatency(): JSONObject {
        val domains = listOf("google.com", "cloudflare.com", "wikipedia.org", "github.com", "apple.com")
        val results = JSONArray()
        for (d in domains) {
            val t = SystemClock.elapsedRealtime()
            val ok = try {
                InetAddress.getByName(d)
                true
            } catch (e: Exception) { false }
            val ms = SystemClock.elapsedRealtime() - t
            results.put(JSONObject().apply {
                put("domain", d)
                put("resolved", ok)
                put("elapsed_ms", ms)
            })
        }
        return JSONObject().put("results", results)
    }

    /**
     * HTTP HEAD to generate_204 (returns 204 No Content, tiny cost, no cache).
     * Reports full round-trip. Not a bandwidth test — a reachability + latency probe.
     */
    private fun testNetworkSpeed(): JSONObject {
        // HTTPS (not the cleartext connectivitycheck.gstatic.com URL) so Android's
        // cleartext-block policy in networkSecurityConfig cannot silently fail this probe.
        val url = "https://clients3.google.com/generate_204"
        val t = SystemClock.elapsedRealtime()
        val (code, ok) = try {
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.requestMethod = "HEAD"
            conn.connectTimeout = 3000
            conn.readTimeout = 3000
            val c = conn.responseCode
            conn.disconnect()
            c to (c in 200..299)
        } catch (e: Exception) {
            -1 to false
        }
        val ms = SystemClock.elapsedRealtime() - t
        return JSONObject().apply {
            put("url", url)
            put("http_status", code)
            put("ok", ok)
            put("round_trip_ms", ms)
        }
    }

    /**
     * Open each camera ID, wait up to 2s for it to reach CameraDevice.StateCallback.onOpened,
     * report first-open time. Does NOT capture a frame — that would need Surface + preview
     * plumbing per lens, and dead lenses often DO open but fail to stream (the eye check).
     */
    private fun testCameraOpen(): JSONObject {
        // Runtime CAMERA permission check — declaring it in the manifest is not enough on API 23+.
        // Return a clean, machine-readable error the MCP client can show the user without stack-trace.
        val cameraGranted = ContextCompat.checkSelfPermission(
            context, android.Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
        if (!cameraGranted) {
            return JSONObject()
                .put("ok", false)
                .put("error", "camera_permission_not_granted")
                .put("fix", "Open DeviceGPT on the phone, grant Camera permission, then retry.")
        }
        val cm = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val results = JSONArray()
        val thread = HandlerThread("bridge-camera-probe").apply { start() }
        val handler = Handler(thread.looper)
        try {
            for (id in cm.cameraIdList) {
                val latch = CountDownLatch(1)
                var openedMs: Long? = null
                var errorCode: Int? = null
                var device: CameraDevice? = null
                val start = SystemClock.elapsedRealtime()
                try {
                    @Suppress("MissingPermission")
                    cm.openCamera(id, object : CameraDevice.StateCallback() {
                        override fun onOpened(camera: CameraDevice) {
                            openedMs = SystemClock.elapsedRealtime() - start
                            device = camera
                            latch.countDown()
                        }
                        override fun onDisconnected(camera: CameraDevice) {
                            errorCode = -1
                            latch.countDown()
                            camera.close()
                        }
                        override fun onError(camera: CameraDevice, error: Int) {
                            errorCode = error
                            latch.countDown()
                            camera.close()
                        }
                    }, handler)
                    latch.await(2500, TimeUnit.MILLISECONDS)
                } catch (e: SecurityException) {
                    errorCode = -2
                } finally {
                    device?.close()
                }
                results.put(JSONObject().apply {
                    put("camera_id", id)
                    put("opened", openedMs != null)
                    put("open_ms", openedMs ?: JSONObject.NULL)
                    put("error_code", errorCode ?: JSONObject.NULL)
                })
            }
        } finally {
            thread.quitSafely()
        }
        return JSONObject().put("results", results)
    }

    /**
     * Sample BATTERY_PROPERTY_CURRENT_NOW at t=0 and t=+2s. On some OEMs this is signed
     * (positive = charging, negative = discharging). We report the raw µA value AND its
     * absolute mA — the sign is device-specific and we do NOT normalise it.
     */
    private fun testBatteryDrainRate(): JSONObject {
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val t0 = SystemClock.elapsedRealtime()
        val sample1MicroA = bm.getLongProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
        Thread.sleep(2000)
        val sample2MicroA = bm.getLongProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
        val elapsedMs = SystemClock.elapsedRealtime() - t0
        val meanMicroA = (sample1MicroA + sample2MicroA) / 2.0
        return JSONObject().apply {
            put("sample1_micro_amps", sample1MicroA)
            put("sample2_micro_amps", sample2MicroA)
            put("mean_micro_amps", meanMicroA)
            put("mean_absolute_ma", kotlin.math.abs(meanMicroA) / 1000.0)
            put("elapsed_ms", elapsedMs)
            put("sign_note", "sign is OEM-defined; some report positive when discharging, others negative")
        }
    }
}
