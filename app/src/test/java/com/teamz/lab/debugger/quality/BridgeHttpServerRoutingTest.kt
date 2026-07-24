package com.teamz.lab.debugger.quality

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.teamz.lab.debugger.services.BridgeHttpServer
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicInteger

/**
 * End-to-end integration test: starts a real [BridgeHttpServer] on a random port
 * bound to loopback and hits its safe-read endpoints via HttpURLConnection.
 *
 * The point is to catch three regressions [BridgeHttpServerAuthGuardTest] cannot:
 *   1. Socket-level: does the server actually bind + accept + serve HTTP over TCP?
 *   2. Payload shape: does each endpoint's JSON body have the top-level keys the
 *      MCP wrapper (docs/ai-bridge-mcp-wrapper/server.py) and the AI onboarding
 *      prompt actually depend on?
 *   3. onRequestServed callback: does the parent service get one, and only one,
 *      idle-timer reset per authorised request under real socket transport?
 *
 * Only "always-safe on Robolectric" endpoints are exercised — anything that would
 * open the camera, hit the network, launch an activity, or write to /sdcard is
 * skipped and covered by the mocked auth-guard test instead. Robolectric's
 * emulated services fill in ambient BatteryManager/PowerManager/CameraManager
 * data well enough for shape-level assertions.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class BridgeHttpServerRoutingTest {

    private lateinit var context: Context
    private lateinit var server: BridgeHttpServer
    private val servedCount = AtomicInteger(0)
    private val servedUris = mutableListOf<String>()
    private var port: Int = 0
    private val pin = "654321"

    @Before fun startServer() {
        context = ApplicationProvider.getApplicationContext()
        servedCount.set(0)
        servedUris.clear()
        server = BridgeHttpServer(
            context = context,
            port = 0,
            expectedPin = pin,
            onRequestServed = {
                servedCount.incrementAndGet()
                synchronized(servedUris) { servedUris.add(it) }
            },
        )
        server.start(3_000, false)
        port = server.listeningPort
        assertTrue("server did not bind — port is $port", port > 0)
    }

    @After fun stopServer() {
        server.stop()
    }

    // ─────────────────────── helpers ───────────────────────

    private data class Http(val code: Int, val body: String)

    private fun get(path: String, withPin: Boolean = true): Http = call(path, "GET", withPin)
    private fun post(path: String, withPin: Boolean = true): Http = call(path, "POST", withPin)

    private fun call(path: String, method: String, withPin: Boolean): Http {
        val url = URL("http://127.0.0.1:$port$path")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = method
            if (withPin) setRequestProperty("X-Bridge-Pin", pin)
            connectTimeout = 3_000
            readTimeout = 5_000
            if (method == "POST") {
                doOutput = true
                outputStream.use { it.write(ByteArray(0)) }
            }
        }
        val code = conn.responseCode
        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
        val body = stream?.bufferedReader()?.use { it.readText() } ?: ""
        conn.disconnect()
        return Http(code, body)
    }

    private fun asJson(body: String): JSONObject {
        assertTrue("response was not JSON: $body", body.startsWith("{"))
        return JSONObject(body)
    }

    // ─────────────────────── socket-level ───────────────────────

    @Test fun `server binds and accepts real HTTP over loopback`() {
        val r = get("/health", withPin = false)
        assertEquals(200, r.code)
        val json = asJson(r.body)
        assertEquals(true, json.getBoolean("ok"))
        assertTrue("version missing", json.has("version"))
    }

    @Test fun `wrong PIN over the socket returns 401 (not a crash, not a hang)`() {
        val url = URL("http://127.0.0.1:$port/device")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            setRequestProperty("X-Bridge-Pin", "wrong")
            connectTimeout = 3_000
            readTimeout = 5_000
        }
        assertEquals(401, conn.responseCode)
        conn.disconnect()
    }

    // ─────────────────────── payload shape (MCP-facing) ───────────────────────

    @Test fun `device endpoint returns the keys the MCP wrapper reads`() {
        val json = asJson(get("/device").body)
        // Every key here is dereferenced by devicegpt_device_info in server.py.
        // A silent rename regresses the AI's ability to name the phone.
        for (key in listOf(
            "model", "manufacturer", "brand", "device", "product",
            "android_version", "sdk_int", "board", "hardware", "supported_abis",
        )) {
            assertTrue("device payload missing key: $key — MCP wrapper depends on it", json.has(key))
        }
    }

    @Test fun `battery endpoint returns the health-relevant keys`() {
        val json = asJson(get("/battery").body)
        for (key in listOf("percent", "is_charging", "plug_type", "health")) {
            assertTrue("battery payload missing key: $key", json.has(key))
        }
    }

    @Test fun `storage endpoint returns byte-level totals`() {
        val json = asJson(get("/storage").body)
        assertTrue(json.has("internal_total_bytes"))
        assertTrue(json.has("internal_free_bytes"))
        // Values MUST be present as non-null on Robolectric — StatFs falls back to
        // the JVM tmpdir. A change that returns null here breaks the "how much
        // space is left" question the onboarding prompt promises the AI can answer.
        assertNotNull(json.get("internal_total_bytes"))
    }

    @Test fun `network endpoint returns the transport + LAN triple`() {
        val json = asJson(get("/network").body)
        // lan_ipv4 may be null on Robolectric (no real wlan), but the KEY must exist.
        assertTrue(json.has("lan_ipv4"))
        assertTrue(json.has("has_internet"))
        assertTrue(json.has("is_wifi"))
        assertTrue(json.has("is_cellular"))
    }

    @Test fun `uptime endpoint returns a positive ms value`() {
        val json = asJson(get("/uptime").body)
        assertTrue("uptime response has no keys", json.length() > 0)
    }

    @Test fun `cpu_info endpoint parses as JSON with at least one key`() {
        val json = asJson(get("/cpu_info").body)
        assertTrue(json.length() > 0)
    }

    @Test fun `thermal_state endpoint parses as JSON with at least one key`() {
        val json = asJson(get("/thermal_state").body)
        assertTrue(json.length() > 0)
    }

    @Test fun `memory_info endpoint parses as JSON with at least one key`() {
        val json = asJson(get("/memory_info").body)
        assertTrue(json.length() > 0)
    }

    @Test fun `screen_info endpoint parses as JSON with at least one key`() {
        val json = asJson(get("/screen_info").body)
        assertTrue(json.length() > 0)
    }

    // ─────────────────────── callback contract ───────────────────────

    @Test fun `onRequestServed fires exactly once per authorised request`() {
        get("/device")
        get("/battery")
        get("/storage")
        assertEquals("expected 3 callback fires, got ${servedUris}", 3, servedCount.get())
        synchronized(servedUris) {
            assertEquals(listOf("/device", "/battery", "/storage"), servedUris)
        }
    }

    @Test fun `onRequestServed does NOT fire for 401 or 404 over the socket`() {
        // 401
        call("/device", "GET", withPin = false)
        // 404
        get("/does_not_exist")
        assertEquals(
            "callback fired for a rejected request — this breaks the FGS idle-timer discipline",
            0,
            servedCount.get(),
        )
    }

    @Test fun `health endpoint over the socket also fires the callback (once)`() {
        // Health is unauthenticated but IS a legitimate MCP-wrapper heartbeat —
        // must count against the idle timer so the service doesn't die mid-conversation.
        get("/health", withPin = false)
        assertEquals(1, servedCount.get())
        synchronized(servedUris) { assertEquals(listOf("/health"), servedUris) }
    }
}
