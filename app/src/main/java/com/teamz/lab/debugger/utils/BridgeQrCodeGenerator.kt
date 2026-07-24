package com.teamz.lab.debugger.utils

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/**
 * QR bitmap for the AI Bridge pairing card.
 *
 * Encodes a JSON blob the MCP wrapper (or a paste-in-terminal command) can parse:
 *   `{"url":"http://192.168.1.42:8787","pin":"483920"}`
 *
 * The QR is intentionally rendered black-on-white **regardless of the app's dark-mode
 * setting** — inverted QRs (white modules on dark bg) break most consumer QR scanners
 * that only handle the standard polarity.
 */
object BridgeQrCodeGenerator {

    fun buildPayload(url: String, pin: String): String {
        return """{"url":"$url","pin":"$pin"}"""
    }

    fun generateBitmap(payload: String, sizePx: Int = 512): Bitmap? {
        return try {
            val hints = mapOf(
                EncodeHintType.MARGIN to 1,
                EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
            )
            val matrix = QRCodeWriter().encode(payload, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)
            val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
            for (x in 0 until sizePx) {
                for (y in 0 until sizePx) {
                    bmp.setPixel(x, y, if (matrix.get(x, y)) Color.BLACK else Color.WHITE)
                }
            }
            bmp
        } catch (e: Exception) {
            null
        }
    }
}
