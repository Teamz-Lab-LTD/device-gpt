package com.teamz.lab.debugger.services

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import com.teamz.lab.debugger.utils.LanIpResolver
import com.teamz.lab.debugger.utils.handleError
import fi.iki.elonen.NanoHTTPD
import org.json.JSONArray
import org.json.JSONObject

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
                uri == "/device" && method == "GET" -> deviceInfo()
                uri == "/battery" && method == "GET" -> batteryInfo()
                uri == "/storage" && method == "GET" -> storageInfo()
                uri == "/network" && method == "GET" -> networkInfo()
                uri == "/camera" && method == "GET" -> cameraInfo()
                uri == "/sensors" && method == "GET" -> sensorsInfo()
                uri == "/apps" && method == "GET" -> installedApps()
                uri == "/flashlight" && method == "POST" -> toggleFlashlight(session)
                uri == "/launch_app" && method == "POST" -> launchApp(session)
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
}
