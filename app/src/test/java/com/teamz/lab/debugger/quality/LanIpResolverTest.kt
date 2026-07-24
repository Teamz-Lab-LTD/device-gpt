package com.teamz.lab.debugger.quality

import com.teamz.lab.debugger.utils.LanIpResolver
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the LAN-only source-IP check that gates every AI Bridge HTTP request.
 *
 * This function is the second line of defence after the wlan-interface bind:
 * a hotel/enterprise WiFi without client-isolation can route a public source IP
 * to a device that binds 0.0.0.0. If [LanIpResolver.isLanAddress] ever admits a
 * public IP as "LAN", every endpoint (including capture_photo) becomes reachable
 * from the internet under only the 6-digit PIN — an unacceptable exposure.
 *
 * These tests exist so a future refactor of the RFC1918 table cannot silently
 * regress that boundary without at least one red bar.
 */
class LanIpResolverTest {

    // ─────────────────────── loopback ───────────────────────

    @Test fun `loopback IPv4 is LAN`() {
        assertTrue(LanIpResolver.isLanAddress("127.0.0.1"))
        assertTrue(LanIpResolver.isLanAddress("127.0.0.5"))
        assertTrue(LanIpResolver.isLanAddress("127.255.255.254"))
    }

    @Test fun `loopback IPv6 is LAN, both compressed and expanded forms`() {
        assertTrue(LanIpResolver.isLanAddress("::1"))
        assertTrue(LanIpResolver.isLanAddress("0:0:0:0:0:0:0:1"))
    }

    // ─────────────────────── RFC1918 accept ───────────────────────

    @Test fun `10-slash-8 is LAN`() {
        assertTrue(LanIpResolver.isLanAddress("10.0.0.1"))
        assertTrue(LanIpResolver.isLanAddress("10.255.255.254"))
    }

    @Test fun `192_168-slash-16 is LAN`() {
        assertTrue(LanIpResolver.isLanAddress("192.168.0.1"))
        assertTrue(LanIpResolver.isLanAddress("192.168.1.100"))
        assertTrue(LanIpResolver.isLanAddress("192.168.255.254"))
    }

    @Test fun `link-local 169_254 is LAN`() {
        // APIPA — a phone with a dropped DHCP lease will self-assign 169.254.x.x
        // and a same-network laptop can still reach it. Must be treated as LAN.
        assertTrue(LanIpResolver.isLanAddress("169.254.1.1"))
        assertTrue(LanIpResolver.isLanAddress("169.254.255.254"))
    }

    // ─────────────────────── 172.16/12 boundary (the classic bug) ───────────────────────

    @Test fun `172_16 through 172_31 is LAN (boundaries)`() {
        assertTrue(LanIpResolver.isLanAddress("172.16.0.1"))
        assertTrue(LanIpResolver.isLanAddress("172.16.255.254"))
        assertTrue(LanIpResolver.isLanAddress("172.24.1.1"))
        assertTrue(LanIpResolver.isLanAddress("172.31.0.1"))
        assertTrue(LanIpResolver.isLanAddress("172.31.255.254"))
    }

    @Test fun `172_0 through 172_15 is NOT LAN`() {
        // Common mis-implementation: startsWith("172.") returns true here.
        // Guard against that regression.
        for (second in 0..15) {
            val host = "172.$second.1.1"
            assertFalse("172.$second/16 is a PUBLIC block (owned by outside orgs) — must reject", LanIpResolver.isLanAddress(host))
        }
    }

    @Test fun `172_32 through 172_255 is NOT LAN`() {
        for (second in listOf(32, 33, 100, 200, 255)) {
            val host = "172.$second.1.1"
            assertFalse("172.$second/16 is a PUBLIC block — must reject", LanIpResolver.isLanAddress(host))
        }
    }

    // ─────────────────────── public reject (the security-critical bar) ───────────────────────

    @Test fun `common public IPs are NOT LAN`() {
        // 8.8.8.8 (Google DNS), 1.1.1.1 (Cloudflare), a random Amazon IP, and a random
        // .co.uk mail server. Any one of these succeeding = the guard is broken.
        val publicIps = listOf(
            "8.8.8.8",
            "1.1.1.1",
            "1.0.0.1",
            "52.94.236.248",     // aws.amazon.com
            "185.199.108.153",   // github pages
            "142.250.190.14",    // google.com
        )
        for (ip in publicIps) {
            assertFalse("$ip is public — must be rejected", LanIpResolver.isLanAddress(ip))
        }
    }

    @Test fun `IPs that look-alike RFC1918 but are not are rejected`() {
        // startsWith("10") would falsely admit 100.x.x.x (carrier-grade NAT is not
        // reachable from a laptop on the same LAN as the phone) and 101.x/102.x.
        assertFalse("100.64.0.0/10 is CGNAT, not LAN", LanIpResolver.isLanAddress("100.64.0.1"))
        assertFalse(LanIpResolver.isLanAddress("101.10.10.10"))
        // startsWith("192.16") vs "192.168" — historical typo
        assertFalse(LanIpResolver.isLanAddress("192.169.1.1"))
        assertFalse(LanIpResolver.isLanAddress("192.167.1.1"))
        // startsWith("169.") — 169.1.1.1 is a public IANA-reserved block, NOT link-local
        assertFalse(LanIpResolver.isLanAddress("169.1.1.1"))
        assertFalse(LanIpResolver.isLanAddress("169.253.1.1"))
    }

    @Test fun `malformed input is NOT LAN (fail closed)`() {
        // A parser bug in the 172-second-octet path once threw NumberFormatException
        // and was caught, returning false. Verify empty / garbage / not-an-IP all
        // return false, not true, not throw.
        assertFalse(LanIpResolver.isLanAddress(""))
        assertFalse(LanIpResolver.isLanAddress("not.an.ip"))
        assertFalse(LanIpResolver.isLanAddress("172.abc.1.1"))
        assertFalse(LanIpResolver.isLanAddress("172."))
        assertFalse(LanIpResolver.isLanAddress("::"))
        assertFalse(LanIpResolver.isLanAddress("garbage"))
    }
}
