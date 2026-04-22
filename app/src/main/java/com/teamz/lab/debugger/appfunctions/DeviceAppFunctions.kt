package com.teamz.lab.debugger.appfunctions

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.appfunctions.AppFunctionContext
import androidx.appfunctions.service.AppFunction
import com.teamz.lab.debugger.utils.getCompactCpuInfo
import com.teamz.lab.debugger.utils.getCpuArchitecture
import com.teamz.lab.debugger.utils.getCpuModel
import com.teamz.lab.debugger.utils.getDeviceInfoString
import com.teamz.lab.debugger.utils.getDisplayInfo
import com.teamz.lab.debugger.utils.getMaxCpuClockSpeedMHz
import com.teamz.lab.debugger.utils.getMemoryAndStorageInfo
import com.teamz.lab.debugger.utils.getRamUsage
import com.teamz.lab.debugger.utils.getSensorList
import com.teamz.lab.debugger.utils.getThermalZoneTemperatures

class DeviceAppFunctions {

    /**
     * Returns a full device snapshot: manufacturer, model, Android version, CPU, RAM,
     * storage, display, sensor list. One-shot "about this phone" summary.
     *
     * Use this when the user asks: "what phone is this?", "device info",
     * "tell me about my phone hardware".
     */
    @RequiresApi(36)
    @AppFunction(isDescribedByKDoc = true)
    suspend fun getDeviceInfo(
        appFunctionContext: AppFunctionContext,
    ): DeviceInfoReport {
        val context = appFunctionContext.context
        val manufacturer = Build.MANUFACTURER
        val model = Build.MODEL
        val androidVersion = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"
        val cpu = getCompactCpuInfo()
        val ram = runCatching { getRamUsage(context) }.getOrDefault("")
        val storage = runCatching { getMemoryAndStorageInfo(context) }.getOrDefault("")
        val display = runCatching { getDisplayInfo(context) }.getOrDefault(emptyMap()).entries
            .joinToString(" · ") { (k, v) -> "$k=$v" }
        val gpu = runCatching { getDeviceInfoString(context) }.getOrDefault("")
            .lines().firstOrNull { it.contains("GPU", ignoreCase = true) }.orEmpty()
        val sensors = runCatching { getSensorList(context) }.getOrDefault(emptyList())

        return deviceInfoReport(
            manufacturer = manufacturer,
            model = model,
            androidVersion = androidVersion,
            cpu = cpu,
            ramUsage = ram,
            storage = storage,
            display = display,
            gpu = gpu,
            sensors = sensors,
            details = mapOf(
                "cpuArch" to getCpuArchitecture(),
                "cpuModel" to getCpuModel(),
                "maxCpuClockMHz" to getMaxCpuClockSpeedMHz().toString(),
                "buildFingerprint" to Build.FINGERPRINT,
                "product" to Build.PRODUCT,
                "brand" to Build.BRAND,
            ).toDetailEntries(),
        )
    }

    /**
     * Returns detailed CPU info: architecture, model, max clock speed, number of cores,
     * per-core frequencies. Use when user asks about the phone's processor.
     */
    @RequiresApi(36)
    @AppFunction(isDescribedByKDoc = true)
    suspend fun getCpuReport(
        appFunctionContext: AppFunctionContext,
    ): ScanResult {
        val compact = getCompactCpuInfo()
        return scanResult(
            title = "CPU",
            summary = compact,
            details = mapOf(
                "architecture" to getCpuArchitecture(),
                "model" to getCpuModel(),
                "maxClockMHz" to getMaxCpuClockSpeedMHz().toString(),
            ).toDetailEntries(),
        )
    }

    /**
     * Returns RAM usage and storage status in human-readable form.
     * Use when user asks: "how much RAM do I have?", "free storage?".
     */
    @RequiresApi(36)
    @AppFunction(isDescribedByKDoc = true)
    suspend fun getMemoryReport(
        appFunctionContext: AppFunctionContext,
    ): ScanResult {
        val context = appFunctionContext.context
        val ram = runCatching { getRamUsage(context) }.getOrDefault("")
        val storage = runCatching { getMemoryAndStorageInfo(context) }.getOrDefault("")
        return scanResult(
            title = "Memory & Storage",
            summary = "$ram · $storage",
            details = mapOf("ram" to ram, "storage" to storage).toDetailEntries(),
        )
    }

    /**
     * Returns the device's sensor list: accelerometer, gyroscope, proximity, magnetometer,
     * barometer, etc. with vendor + resolution where reported.
     *
     * Use when user asks: "what sensors does my phone have?", "does my phone have a barometer?".
     */
    @RequiresApi(36)
    @AppFunction(isDescribedByKDoc = true)
    suspend fun getSensorInventory(
        appFunctionContext: AppFunctionContext,
    ): ScanResult {
        val context = appFunctionContext.context
        val sensors = runCatching { getSensorList(context) }.getOrDefault(emptyList())
        return scanResult(
            title = "Sensors",
            summary = "${sensors.size} sensors detected.",
            details = sensors.withIndex()
                .associate { (i, s) -> "sensor_${i + 1}" to s }
                .toDetailEntries(),
        )
    }

    /**
     * Returns live thermal-zone temperatures from the kernel: battery, CPU, modem, skin, etc.
     * Useful for diagnosing overheating.
     */
    @RequiresApi(36)
    @AppFunction(isDescribedByKDoc = true)
    suspend fun getThermalReport(
        appFunctionContext: AppFunctionContext,
    ): ScanResult {
        val context = appFunctionContext.context
        val zones = runCatching { getThermalZoneTemperatures(context) }.getOrDefault("")
        return scanResult(
            title = "Thermal Zones",
            summary = zones.ifBlank { "No thermal zone data available on this device." },
            details = emptyList(),
        )
    }
}
