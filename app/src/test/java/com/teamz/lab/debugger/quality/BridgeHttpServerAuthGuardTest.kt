package com.teamz.lab.debugger.quality

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.teamz.lab.debugger.services.BridgeHttpServer
import fi.iki.elonen.NanoHTTPD
import fi.iki.elonen.NanoHTTPD.Method
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Guards the two "security-critical, always-on" branches in [BridgeHttpServer.serve]:
 *   1. non-LAN source IP → 403 immediately, [onRequestServed] does NOT fire.
 *   2. missing/wrong X-Bridge-Pin → 401, [onRequestServed] does NOT fire.
 *   3. `/health` is the ONLY unauthenticated endpoint, fires callback.
 *   4. Correct PIN on a real endpoint → 200 and fires callback.
 *
 * The callback semantics are load-bearing: [BridgeService] resets its 10-min idle
 * timer on every callback fire. A rejected request MUST NOT keep the service
 * alive — otherwise a same-LAN attacker who never knows the PIN can spam the
 * port and hold the phone's foreground service open indefinitely.
 *
 * Tests use a mocked [NanoHTTPD.IHTTPSession] rather than opening a real socket
 * so we can drive the `remoteIpAddress` field arbitrarily (impossible with a
 * loopback socket, which always reports 127.0.0.1).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class BridgeHttpServerAuthGuardTest {

    private lateinit var context: Context
    private lateinit var server: BridgeHttpServer
    private val servedCallback = mutableListOf<String>()

    @Before fun setup() {
        context = ApplicationProvider.getApplicationContext()
        servedCallback.clear()
        server = BridgeHttpServer(
            context = context,
            port = 0,
            expectedPin = "123456",
            onRequestServed = { servedCallback.add(it) },
        )
        // Do NOT call server.start() — we drive serve(session) directly.
    }

    // ─────────────────────── helpers ───────────────────────

    private fun session(
        uri: String,
        method: Method = Method.GET,
        pin: String? = null,
        remoteIp: String = "192.168.1.5",
    ): NanoHTTPD.IHTTPSession {
        val s: NanoHTTPD.IHTTPSession = mock()
        whenever(s.uri).thenReturn(uri)
        whenever(s.method).thenReturn(method)
        whenever(s.remoteIpAddress).thenReturn(remoteIp)
        whenever(s.remoteHostName).thenReturn(remoteIp)
        val headers = if (pin != null) mapOf("x-bridge-pin" to pin) else emptyMap()
        whenever(s.headers).thenReturn(headers)
        return s
    }

    private fun bodyText(r: NanoHTTPD.Response): String =
        r.data.readBytes().toString(Charsets.UTF_8)

    // ─────────────────────── LAN guard ───────────────────────

    @Test fun `non-LAN source IP is rejected with 403 and callback does NOT fire`() {
        val response = server.serve(session("/device", pin = "123456", remoteIp = "8.8.8.8"))
        assertEquals(NanoHTTPD.Response.Status.FORBIDDEN, response.status)
        assertTrue(bodyText(response).contains("non-LAN"))
        assertTrue(
            "callback fired for a rejected non-LAN request — this would keep the FGS alive under a public-source attack",
            servedCallback.isEmpty(),
        )
    }

    @Test fun `non-LAN source IP is rejected even for the health endpoint`() {
        // Health is unauthenticated, but the LAN guard must still apply first.
        val response = server.serve(session("/health", pin = null, remoteIp = "1.1.1.1"))
        assertEquals(NanoHTTPD.Response.Status.FORBIDDEN, response.status)
        assertTrue(servedCallback.isEmpty())
    }

    @Test fun `LAN source IP passes the first gate`() {
        // Doesn't matter what the endpoint is — we just want to prove the LAN
        // check itself doesn't reject a valid RFC1918 IP.
        for (ip in listOf("192.168.0.1", "10.0.0.1", "172.16.5.5", "127.0.0.1")) {
            val response = server.serve(session("/health", pin = null, remoteIp = ip))
            assertEquals("source $ip should NOT be rejected as non-LAN", NanoHTTPD.Response.Status.OK, response.status)
        }
    }

    // ─────────────────────── PIN guard ───────────────────────

    @Test fun `missing PIN returns 401 on a protected endpoint and callback does NOT fire`() {
        val response = server.serve(session("/device", pin = null))
        assertEquals(NanoHTTPD.Response.Status.UNAUTHORIZED, response.status)
        assertTrue(bodyText(response).contains("X-Bridge-Pin"))
        assertTrue(
            "callback fired for a 401 — a same-LAN attacker without the PIN could keep the FGS alive by spamming",
            servedCallback.isEmpty(),
        )
    }

    @Test fun `wrong PIN returns 401 and callback does NOT fire`() {
        val response = server.serve(session("/device", pin = "000000"))
        assertEquals(NanoHTTPD.Response.Status.UNAUTHORIZED, response.status)
        assertTrue(servedCallback.isEmpty())
    }

    @Test fun `correct PIN allows the endpoint and callback fires exactly once`() {
        val response = server.serve(session("/device", pin = "123456"))
        assertEquals(NanoHTTPD.Response.Status.OK, response.status)
        val body = bodyText(response)
        assertTrue("body should be JSON with model field: $body", body.contains("\"model\""))
        assertEquals(listOf("/device"), servedCallback)
    }

    @Test fun `health endpoint is reachable WITHOUT the PIN and fires the callback`() {
        // Per BridgeHttpServer contract: /health is the pre-auth reachability probe.
        val response = server.serve(session("/health", pin = null))
        assertEquals(NanoHTTPD.Response.Status.OK, response.status)
        val body = bodyText(response)
        assertTrue("health should report ok=true: $body", body.contains("\"ok\":true"))
        assertTrue("health should report a version: $body", body.contains("\"version\""))
        assertEquals(listOf("/health"), servedCallback)
    }

    // ─────────────────────── endpoint dispatch ───────────────────────

    @Test fun `unknown URI with valid PIN returns 404, not 401 or 200`() {
        val response = server.serve(session("/does_not_exist", pin = "123456"))
        assertEquals(NanoHTTPD.Response.Status.NOT_FOUND, response.status)
        assertTrue(bodyText(response).contains("no endpoint"))
        assertTrue(
            "callback fired for a 404 — the callback contract is 'authorised + dispatched', not just 'authorised'",
            servedCallback.isEmpty(),
        )
    }

    @Test fun `wrong HTTP method on a known URI returns 404`() {
        // /flashlight is POST-only. A GET on it should not dispatch and not fire callback.
        val response = server.serve(session("/flashlight", method = Method.GET, pin = "123456"))
        assertEquals(NanoHTTPD.Response.Status.NOT_FOUND, response.status)
        assertTrue(servedCallback.isEmpty())
    }

    // ─────────────────────── attack surface (composition) ───────────────────────

    @Test fun `non-LAN wins over wrong-PIN — 403 not 401`() {
        // Sanity check: an attacker probing from a public IP with a wrong PIN
        // should see 403 (LAN guard first), not 401 (which would let them enumerate
        // the endpoints by leaking that the URI exists).
        val response = server.serve(session("/device", pin = "wrong", remoteIp = "185.199.108.153"))
        assertEquals(NanoHTTPD.Response.Status.FORBIDDEN, response.status)
    }

    @Test fun `empty PIN string is not accepted as 'no PIN needed'`() {
        // A common off-by-one: `if (pin.isNullOrEmpty()) return 401` OK, but
        // `if (pin != expectedPin)` correctly rejects "". Guard the actual behaviour.
        val response = server.serve(session("/device", pin = ""))
        assertEquals(NanoHTTPD.Response.Status.UNAUTHORIZED, response.status)
        assertTrue(servedCallback.isEmpty())
    }
}
