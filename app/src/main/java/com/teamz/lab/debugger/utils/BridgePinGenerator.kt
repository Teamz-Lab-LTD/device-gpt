package com.teamz.lab.debugger.utils

import java.security.SecureRandom

/**
 * 6-digit PIN generator for the AI Bridge pairing handshake.
 *
 * `SecureRandom` (not `Random`) so an attacker on the LAN cannot predict the PIN by
 * observing the phone clock. Leading-zero range (100000..999999) is chosen so every
 * generated PIN is exactly 6 characters — a leading-zero PIN like "049283" is easy for
 * a user to mistype on a laptop keyboard as "49283" and get 401 back.
 */
object BridgePinGenerator {
    private val random = SecureRandom()

    fun generate(): String {
        val value = 100_000 + random.nextInt(900_000)
        return value.toString()
    }
}
