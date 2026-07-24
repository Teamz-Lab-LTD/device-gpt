package com.teamz.lab.debugger.utils

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.PowerManager
import android.os.StatFs
import android.os.SystemClock
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Composes a compact plain-text prompt the user can paste into any AI chat
 * (Claude, ChatGPT, Gemini, Perplexity, etc.) to get instant, contextual help
 * about their phone. Zero setup — no MCP, no PIN, no config.
 *
 * Target length: <2000 chars so it fits in a single message input on any AI app.
 *
 * What this must NEVER include (policy line — same as [BridgeHttpServer]):
 *   - Any "quality / health / grade / score %" claim about camera or sensors
 *   - Any invented value we did not read from an API
 *
 * The prompt asks the AI to reply in plain language and to be honest about
 * uncertainty — mirrors the doc/AI_ONBOARDING_PROMPT.md rules.
 */
object AiShareTextGenerator {

    /**
     * Builds the text-prompt string. Reads Android APIs directly — no network,
     * no HTTP round-trip through the Bridge.
     *
     * When [bridgeUrl] + [bridgePin] are provided (Bridge is on), the prompt also
     * includes an "optional live access" block so the receiving AI can be pointed
     * at the MCP endpoint for follow-up questions on the same conversation.
     */
    fun buildPrompt(context: Context, bridgeUrl: String? = null, bridgePin: String? = null): String {
        val ts = SimpleDateFormat("yyyy-MM-dd HH:mm z", Locale.US).apply {
            timeZone = TimeZone.getDefault()
        }.format(Date())

        val sb = StringBuilder(2048)
        sb.append("Please help me understand my Android phone. Here is a live snapshot as of ")
            .append(ts).append(".\n\n")

        sb.append("=== Device ===\n")
        sb.append("Model: ${Build.MANUFACTURER} ${Build.MODEL}\n")
        sb.append("Android: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})\n")
        sb.append("Hardware: ${Build.HARDWARE}, board=${Build.BOARD}\n")
        sb.append("ABIs: ${Build.SUPPORTED_ABIS.joinToString(", ")}\n")

        sb.append("\n=== Battery ===\n")
        appendBattery(context, sb)

        sb.append("\n=== Storage ===\n")
        appendStorage(sb)

        sb.append("\n=== Memory ===\n")
        appendMemory(context, sb)

        sb.append("\n=== Network + WiFi ===\n")
        appendNetwork(context, sb)

        sb.append("\n=== Cameras (facts only, no quality score) ===\n")
        appendCameras(context, sb)

        sb.append("\n=== CPU + Thermal ===\n")
        appendCpu(sb)
        appendThermal(context, sb)

        sb.append("\n=== Uptime ===\n")
        val bootedAgoMs = SystemClock.elapsedRealtime()
        sb.append("Since last reboot: ").append(formatDuration(bootedAgoMs)).append("\n")

        if (bridgeUrl != null && bridgePin != null) {
            sb.append("\n=== Optional: live access via MCP ===\n")
            sb.append("If you want to read live data as it changes (battery draining, apps ")
            sb.append("launching, sensor values, etc.) instead of only this snapshot, my DeviceGPT ")
            sb.append("app is currently running a Bridge on my WiFi:\n")
            sb.append("  URL: ").append(bridgeUrl).append("\n")
            sb.append("  PIN: ").append(bridgePin).append("\n")
            sb.append("Both change every session. Setup guide is in the DeviceGPT app on my phone ")
            sb.append("under AI Bridge → \"Set up live connection (advanced)\".\n")
        }

        sb.append("\n=== How you should behave ===\n")
        sb.append("- FIRST: check if you have tools named `devicegpt_*` (like ")
        sb.append("`devicegpt_battery`, `devicegpt_capture_photo`). If yes, use them for ")
        sb.append("anything I ask beyond this snapshot — the URL + PIN above wire you to ")
        sb.append("live data. If no, work only from the numbers above and tell me honestly ")
        sb.append("when I'm asking for something not in the snapshot.\n")
        sb.append("- Do NOT tell me which mode you are in unless I ask.\n")
        sb.append("- Plain, everyday language. Short sentences. No jargon my mum couldn't read.\n")
        sb.append("- Do NOT invent any value that is not in the numbers above (or a tool response).\n")
        sb.append("- Do NOT rate the camera quality — that is not measurable from these APIs.\n")
        sb.append("- If a tool response has a `user_message` field, relay it VERBATIM. Do ")
        sb.append("not paraphrase or invent your own recovery step.\n")
        sb.append("- If something is missing (a null or 'unknown'), just say so — don't guess.\n")

        sb.append("\n=== If I say my camera is broken ===\n")
        sb.append("Follow this checklist IN ORDER, stop when you find the cause:\n")
        sb.append("  1. Ask which app is failing (Camera / WhatsApp / Zoom / etc.).\n")
        sb.append("  2. Check Camera permission for that app. If MCP: call ")
        sb.append("`devicegpt_permissions_status`. If not: tell me the exact fix path.\n")
        sb.append("  3. If MCP: call `devicegpt_test_camera_open` — see if hardware opens.\n")
        sb.append("  4. Check thermal state — camera auto-disables when hot.\n")
        sb.append("  5. If MCP: call `devicegpt_apps` — look for VPNs / privacy apps / ")
        sb.append("screen recorders that hijack the camera.\n")
        sb.append("  6. Check storage — a full disk can make camera refuse to save.\n")
        sb.append("  7. If MCP + steps 2-6 clear: ask me to Allow one `devicegpt_capture_photo`, ")
        sb.append("look at the actual picture (all black? green tint? blurry? dust spot?).\n")
        sb.append("  8. Only recommend a repair shop if step 3 or 7 shows a clear hardware signal. ")
        sb.append("Otherwise suggest force-close + clear cache + reinstall the app.\n")

        sb.append("\n=== General ask ===\n")
        sb.append("Read the numbers above and tell me if anything looks unhealthy or unusual, ")
        sb.append("then end with one concrete next step I could try.\n")

        sb.append("\n(Snapshot generated by DeviceGPT for Android — not affiliated with any AI vendor.)\n")
        return sb.toString()
    }

    /** Copies the prompt to the clipboard AND opens the system share chooser. */
    fun copyAndShare(context: Context, prompt: String) {
        val clip = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        clip.setPrimaryClip(android.content.ClipData.newPlainText("DeviceGPT AI prompt", prompt))
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, prompt)
            putExtra(Intent.EXTRA_SUBJECT, "My phone info — please help")
        }
        val chooser = Intent.createChooser(send, "Send to your AI").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    }

    // ─────────────────────── section builders ───────────────────────

    private fun appendBattery(context: Context, sb: StringBuilder) {
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val pct = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        val temp = (intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1) ?: -1) / 10.0
        val volt = intent?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1) ?: -1
        val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val plugged = intent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1) ?: -1
        val healthCode = intent?.getIntExtra(BatteryManager.EXTRA_HEALTH, -1) ?: -1
        val plug = when (plugged) {
            BatteryManager.BATTERY_PLUGGED_AC -> "AC"
            BatteryManager.BATTERY_PLUGGED_USB -> "USB"
            BatteryManager.BATTERY_PLUGGED_WIRELESS -> "wireless"
            else -> "unplugged"
        }
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
        val health = when (healthCode) {
            BatteryManager.BATTERY_HEALTH_GOOD -> "good"
            BatteryManager.BATTERY_HEALTH_OVERHEAT -> "overheat"
            BatteryManager.BATTERY_HEALTH_DEAD -> "dead"
            BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "over_voltage"
            BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> "unspecified_failure"
            BatteryManager.BATTERY_HEALTH_COLD -> "cold"
            else -> "unknown"
        }
        sb.append("Level: ${pct}%\n")
        sb.append("Charging: ${if (charging) "yes" else "no"} ($plug)\n")
        if (temp > 0) sb.append("Temperature: ${"%.1f".format(temp)} °C\n")
        if (volt > 0) sb.append("Voltage: $volt mV\n")
        sb.append("Reported health: $health\n")
    }

    private fun appendStorage(sb: StringBuilder) {
        val st = StatFs(Environment.getDataDirectory().path)
        val total = st.blockCountLong * st.blockSizeLong
        val free = st.availableBlocksLong * st.blockSizeLong
        val used = total - free
        val pctUsed = if (total > 0) (used * 100.0 / total) else 0.0
        sb.append("Internal total: ${bytesToGb(total)} GB\n")
        sb.append("Internal free: ${bytesToGb(free)} GB\n")
        sb.append("Used: ${"%.1f".format(pctUsed)}%\n")
    }

    private fun appendMemory(context: Context, sb: StringBuilder) {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val info = ActivityManager.MemoryInfo()
        am.getMemoryInfo(info)
        sb.append("Total RAM: ${bytesToGb(info.totalMem)} GB\n")
        sb.append("Available RAM: ${bytesToGb(info.availMem)} GB\n")
        sb.append("Low-memory state: ${if (info.lowMemory) "YES (OS is under memory pressure)" else "no"}\n")
    }

    private fun appendNetwork(context: Context, sb: StringBuilder) {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
        val active = cm.activeNetwork
        val caps = active?.let { cm.getNetworkCapabilities(it) }
        val isWifi = caps?.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) == true
        val isCell = caps?.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR) == true
        val hasNet = caps?.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true
        val lanIp = LanIpResolver.getLanIpv4() ?: "not on WiFi"
        sb.append("Transport: ${if (isWifi) "WiFi" else if (isCell) "cellular" else "none/off"}\n")
        sb.append("Internet reachable: ${if (hasNet) "yes" else "no"}\n")
        sb.append("LAN IPv4: $lanIp\n")
    }

    private fun appendCameras(context: Context, sb: StringBuilder) {
        try {
            val cm = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val ids = cm.cameraIdList
            sb.append("Third-party-visible lens count: ${ids.size}\n")
            for (id in ids) {
                val c = cm.getCameraCharacteristics(id)
                val facing = when (c.get(CameraCharacteristics.LENS_FACING)) {
                    CameraCharacteristics.LENS_FACING_FRONT -> "front"
                    CameraCharacteristics.LENS_FACING_BACK -> "back"
                    CameraCharacteristics.LENS_FACING_EXTERNAL -> "external"
                    else -> "unknown"
                }
                val focals = c.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)?.joinToString(", ") { "${it}mm" } ?: "?"
                val hasFlash = c.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) ?: false
                sb.append("- Lens $id: $facing, focal $focals, flash=${if (hasFlash) "yes" else "no"}\n")
            }
        } catch (e: Exception) {
            sb.append("(camera info unavailable: ${e.message})\n")
        }
    }

    private fun appendCpu(sb: StringBuilder) {
        sb.append("CPU cores: ${Runtime.getRuntime().availableProcessors()}\n")
        val maxKhz = try {
            java.io.File("/sys/devices/system/cpu/cpu0/cpufreq/cpuinfo_max_freq").readText().trim().toLongOrNull()
        } catch (e: Exception) { null }
        if (maxKhz != null) sb.append("Core 0 max freq: ${"%.2f".format(maxKhz / 1_000_000.0)} GHz\n")
    }

    private fun appendThermal(context: Context, sb: StringBuilder) {
        if (Build.VERSION.SDK_INT < 29) {
            sb.append("Thermal state: unavailable (Android <10)\n")
            return
        }
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val name = when (pm.currentThermalStatus) {
            PowerManager.THERMAL_STATUS_NONE -> "none (normal)"
            PowerManager.THERMAL_STATUS_LIGHT -> "light (warm)"
            PowerManager.THERMAL_STATUS_MODERATE -> "moderate (throttling starting)"
            PowerManager.THERMAL_STATUS_SEVERE -> "severe (heavy throttling)"
            PowerManager.THERMAL_STATUS_CRITICAL -> "critical (near-shutdown)"
            PowerManager.THERMAL_STATUS_EMERGENCY -> "emergency"
            PowerManager.THERMAL_STATUS_SHUTDOWN -> "shutdown imminent"
            else -> "unknown"
        }
        sb.append("Thermal state: $name\n")
        if (pm.isPowerSaveMode) sb.append("Power-save mode: ON\n")
        if (pm.isDeviceIdleMode) sb.append("Device idle (doze) mode: ON\n")
    }

    // ─────────────────────── helpers ───────────────────────

    private fun bytesToGb(bytes: Long): String = "%.2f".format(bytes / 1_073_741_824.0)

    private fun formatDuration(ms: Long): String {
        val totalSec = ms / 1000
        val d = totalSec / 86400
        val h = (totalSec % 86400) / 3600
        val m = (totalSec % 3600) / 60
        return buildString {
            if (d > 0) append("${d}d ")
            if (h > 0 || d > 0) append("${h}h ")
            append("${m}m")
        }
    }
}
