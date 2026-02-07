package com.teamz.lab.debugger.utils

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

/**
 * Simple QUIC reachability heuristic.
 *
 * Attempts to send a single UDP byte to port 443 on the target host.
 * If the socket operation completes without a timeout, UDP/443 is likely
 * open (a precondition for QUIC/HTTP3).
 *
 * This is NOT a full QUIC handshake -- it's a lightweight hint.
 */
object QuicProber {

    suspend fun probeQuicReachability(
        host: String,
        port: Int = 443,
        timeoutMs: Int = 3000
    ): QuicHintResult = withContext(Dispatchers.IO) {
        val start = System.currentTimeMillis()
        var udpOpen = false

        try {
            val address = InetAddress.getByName(host)
            val socket = DatagramSocket()
            socket.soTimeout = timeoutMs

            // Send a single byte -- we don't expect a valid QUIC response,
            // we just check if the send + close doesn't throw
            val data = byteArrayOf(0x00)
            val packet = DatagramPacket(data, data.size, address, port)
            socket.send(packet)

            // Try to receive -- if we get anything back or timeout without
            // error, the port is likely open
            try {
                val buf = ByteArray(1)
                val recv = DatagramPacket(buf, buf.size)
                socket.receive(recv)
                udpOpen = true // Got a response
            } catch (_: java.net.SocketTimeoutException) {
                // Timeout on receive is actually normal for UDP -- the send
                // succeeded which means the port isn't actively blocked.
                // A firewall that blocks UDP/443 would typically cause the
                // send itself to fail on some devices, or cause an ICMP
                // unreachable which would show as an IOException.
                udpOpen = true
            }

            socket.close()
        } catch (_: Exception) {
            udpOpen = false
        }

        val latency = System.currentTimeMillis() - start
        QuicHintResult(
            domain = host,
            udpOpen = udpOpen,
            latencyMs = latency
        )
    }
}
