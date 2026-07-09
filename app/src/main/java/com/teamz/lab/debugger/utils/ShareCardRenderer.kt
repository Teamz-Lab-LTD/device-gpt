package com.teamz.lab.debugger.utils

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.Build
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * v3.2.0 — Device Score share card v2 (2026-07-10 growth synthesis, Phase 2).
 *
 * Renders a screenshot-quality PNG: score, grade, device model, Android version,
 * scan date, wordmark, and SELF-DESCRIBING microcopy listing exactly what was
 * measured (policy: a broadcastable artifact must explain its own claim).
 *
 * Hard gate honored by the CALLER: this ships only on the real multi-signal
 * score (R1). No percentile/comparison line — no data source exists for one
 * at this scale (verifier ruling 2026-07-10).
 *
 * RC-gated: `share_card_v2_enabled` (default OFF). Plain-text share remains the
 * fallback path.
 */
object ShareCardRenderer {

    private const val W = 1080
    private const val H = 1350
    private const val PLAY_LINK = "https://play.google.com/store/apps/details?id=com.teamz.lab.debugger"

    /** Render + fire ACTION_SEND. Returns true when the share sheet launched. */
    fun shareScoreCard(context: Context, score: Int): Boolean {
        return try {
            val file = renderToFile(context, score)
            val uri = FileProvider.getUriForFile(
                context, "${context.packageName}.fileprovider", file
            )
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(
                    Intent.EXTRA_TEXT,
                    "My phone scored $score/100 on DeviceGPT. Run yours: $PLAY_LINK"
                )
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooser = Intent.createChooser(intent, "Share your Device Score")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)
            try {
                AnalyticsUtils.logEvent(
                    AnalyticsEvent.ShareCardSent,
                    mapOf("score" to score, "format" to "png_card_v2")
                )
            } catch (_: Throwable) { }
            true
        } catch (t: Throwable) {
            android.util.Log.w("ShareCardRenderer", "share card failed: ${t.message}")
            false
        }
    }

    private fun renderToFile(context: Context, score: Int): File {
        val bitmap = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Background — dark slate.
        canvas.drawColor(Color.parseColor("#101418"))

        val (gradeLabel, gradeColor) = when {
            score >= 90 -> "Excellent" to Color.parseColor("#4CAF50")
            score >= 75 -> "Great" to Color.parseColor("#8BC34A")
            score >= 60 -> "Good" to Color.parseColor("#FFC107")
            score >= 40 -> "Fair" to Color.parseColor("#FF9800")
            else -> "Needs attention" to Color.parseColor("#F44336")
        }

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // Header wordmark
        paint.color = Color.parseColor("#D9FE06")
        paint.textSize = 56f
        paint.isFakeBoldText = true
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText("DeviceGPT", W / 2f, 140f, paint)

        paint.color = Color.parseColor("#8A9199")
        paint.textSize = 40f
        paint.isFakeBoldText = false
        canvas.drawText("Device Score", W / 2f, 300f, paint)

        // Score ring
        val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 28f
            strokeCap = Paint.Cap.ROUND
        }
        val ringRect = RectF(W / 2f - 260f, 380f, W / 2f + 260f, 900f)
        ringPaint.color = Color.parseColor("#2A2F35")
        canvas.drawArc(ringRect, -90f, 360f, false, ringPaint)
        ringPaint.color = gradeColor
        canvas.drawArc(ringRect, -90f, 360f * (score.coerceIn(0, 100) / 100f), false, ringPaint)

        // Big number
        paint.color = Color.WHITE
        paint.textSize = 220f
        paint.isFakeBoldText = true
        canvas.drawText("$score", W / 2f, 700f, paint)

        paint.color = gradeColor
        paint.textSize = 64f
        canvas.drawText(gradeLabel, W / 2f, 990f, paint)

        // Device facts
        paint.color = Color.parseColor("#C6CCD2")
        paint.textSize = 44f
        paint.isFakeBoldText = false
        val dateStr = SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(Date())
        canvas.drawText("${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE}", W / 2f, 1090f, paint)
        canvas.drawText("Scanned $dateStr", W / 2f, 1150f, paint)

        // Self-describing microcopy — the artifact explains its own claim.
        paint.color = Color.parseColor("#6A7178")
        paint.textSize = 32f
        canvas.drawText("Measured: battery condition · memory · storage · network", W / 2f, 1240f, paint)
        canvas.drawText("Weights 40/25/20/15 · This device, at scan time", W / 2f, 1290f, paint)

        val dir = File(context.cacheDir, "share").apply { mkdirs() }
        val file = File(dir, "device_score_card.png")
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        bitmap.recycle()
        try {
            AnalyticsUtils.logEvent(
                AnalyticsEvent.ShareCardRendered,
                mapOf("score" to score)
            )
        } catch (_: Throwable) { }
        return file
    }
}
