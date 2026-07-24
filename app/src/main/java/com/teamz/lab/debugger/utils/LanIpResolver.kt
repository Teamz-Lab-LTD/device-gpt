package com.teamz.lab.debugger.utils

import java.net.Inet4Address
import java.net.NetworkInterface

/**
 * Resolves the phone's LAN IPv4 address for the AI Bridge tab.
 *
 * `getLanIpv4()` returns the current wlan / ethernet RFC1918 address, or null when the
 * device only has cellular (in which case no external client can reach the bridge anyway
 * and the tab should refuse to start).
 *
 * `isLanAddress()` gates every incoming HTTP request — the server binds to 0.0.0.0 so it
 * can accept LAN connections, but must refuse a stray connection carrying a public source
 * IP (e.g. if the phone is on a badly-configured hotspot without NAT isolation).
 */
object LanIpResolver {

    fun getLanIpv4(): String? {
        return try {
            val interfaces = NetworkInterface.getNetworkInterfaces() ?: return null
            for (iface in interfaces) {
                if (iface.isLoopback || !iface.isUp) continue
                for (addr in iface.inetAddresses) {
                    if (addr is Inet4Address && !addr.isLoopbackAddress) {
                        val host = addr.hostAddress ?: continue
                        if (isLanAddress(host)) return host
                    }
                }
            }
            null
        } catch (e: Exception) {
            null
        }
    }

    fun isLanAddress(host: String): Boolean {
        // Loopback is legitimate: same-device MCP wrapper (or adb-forward for local testing) hits us
        // over 127.x.x.x / ::1. Only apps on THIS device can reach loopback anyway, and endpoints
        // are still PIN-gated, so this does not weaken the LAN guard.
        if (host == "127.0.0.1" || host.startsWith("127.") || host == "::1" || host == "0:0:0:0:0:0:0:1") return true
        if (host.startsWith("10.")) return true
        if (host.startsWith("192.168.")) return true
        if (host.startsWith("169.254.")) return true
        if (host.startsWith("172.")) {
            val second = host.substringAfter("172.").substringBefore(".").toIntOrNull() ?: return false
            return second in 16..31
        }
        return false
    }
}
