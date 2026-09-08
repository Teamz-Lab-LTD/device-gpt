package com.teamz.lab.debugger.utils

import android.annotation.SuppressLint
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext
import kotlin.math.log10
import kotlin.math.sqrt

/**
 * Real microphone capture, playback, and level measurement.
 *
 * Why this exists: the Play listing promises "Mic test — input level, noise floor,
 * instant playback" and the title is literally "Battery, Mic Test: DeviceGPT", but
 * before 2026-08-26 the app contained no AudioRecord at all. The only mic code was
 * `hasMic` (a PackageManager availability flag) and `isMicrophoneBeingUsed()` (which
 * detects OTHER apps using the mic). Neither tests the user's own microphone. That
 * gap is a Deceptive Behavior exposure on an app that already carries one strike for
 * exactly this pattern — a claimed capability the app did not have.
 *
 * DELIBERATELY NOT SCORED. This reports what was measured (dBFS) and what the USER
 * heard on playback. It never grades the microphone, because "is this mic good" is
 * not answerable without a calibrated reference signal and a known acoustic
 * environment — the same impossible-claim shape as the per-app battery attribution
 * that got the app rejected. The user's ear is the detector; the app records their
 * verdict.
 */
object MicTestUtils {

    const val SAMPLE_RATE = 44_100
    private const val CHANNEL_IN = AudioFormat.CHANNEL_IN_MONO
    private const val ENCODING = AudioFormat.ENCODING_PCM_16BIT

    /** Full-scale for signed 16-bit PCM. */
    private const val FULL_SCALE = 32_768.0

    /**
     * Floor for the dBFS conversion. True digital silence is -inf dB, which cannot be
     * rendered or averaged; clamping keeps the UI and the maths finite.
     */
    const val MIN_DBFS = -90.0

    enum class Verdict { SILENT, VERY_QUIET, OK, LOUD, CLIPPING }

    data class LevelReading(val rms: Double, val dbfs: Double, val peak: Int)

    data class MicTestResult(
        val noiseFloorDbfs: Double,
        val peakDbfs: Double,
        val averageDbfs: Double,
        val clipped: Boolean,
        val sampleCount: Int,
        /** Null until the user answers the playback question. */
        val userHeardPlayback: Boolean? = null
    ) {
        /**
         * Signal-to-noise headroom: how far the spoken peak rose above the room's own
         * noise. This is the number that actually distinguishes "mic works" from "mic
         * is dead but the room is loud" — a raw peak alone cannot.
         */
        val headroomDb: Double get() = peakDbfs - noiseFloorDbfs
    }

    // ---- Pure maths (unit-tested without any audio hardware) -------------------

    /** RMS of a PCM-16 frame. Returns 0.0 for an empty buffer rather than NaN. */
    fun rms(buffer: ShortArray, length: Int = buffer.size): Double {
        if (length <= 0) return 0.0
        var sum = 0.0
        for (i in 0 until length) {
            val v = buffer[i].toDouble()
            sum += v * v
        }
        return sqrt(sum / length)
    }

    /** RMS -> dBFS, clamped at [MIN_DBFS] so digital silence is finite. */
    fun toDbfs(rmsValue: Double): Double {
        if (rmsValue <= 0.0) return MIN_DBFS
        val db = 20.0 * log10(rmsValue / FULL_SCALE)
        return if (db < MIN_DBFS) MIN_DBFS else db
    }

    /** True when any sample sits at or beyond full scale — the input was overdriven. */
    fun isClipping(buffer: ShortArray, length: Int = buffer.size): Boolean {
        for (i in 0 until length) {
            if (buffer[i] >= Short.MAX_VALUE || buffer[i] <= Short.MIN_VALUE + 1) return true
        }
        return false
    }

    /**
     * Classify a reading. Thresholds are deliberately coarse and described in plain
     * terms; a finer scale would imply a precision this measurement does not have.
     *
     * [headroomDb] is what decides SILENT vs quiet: a dead mic in a noisy room can
     * still show a high absolute level, so absolute dBFS alone would call it working.
     */
    /** What the mic card should offer the user next, once permission state is known. */
    enum class PermissionNextStep { RUN_TEST, ASK_AGAIN, OPEN_SETTINGS }

    /**
     * The line between "tap the button again" and "only Settings can grant this now".
     *
     * MicTestCard used to set permanentlyDenied on the FIRST denial, so one tap of Deny sent
     * the user to a Settings deep link when tapping Allow again would have worked — on the
     * feature the app title leads with, in session one. Android's own signal for this is
     * shouldShowRequestPermissionRationale: true after a first denial, false once the user has
     * denied twice (Android 11+ treats the second denial as "don't ask again").
     *
     * @param canShowRationale null when there is no Activity to ask. Unknown resolves to
     *   OPEN_SETTINGS on purpose: Settings always works, whereas a re-prompt that Android
     *   silently refuses is a button that does nothing — the worse of the two failures.
     */
    fun permissionNextStep(granted: Boolean, canShowRationale: Boolean?): PermissionNextStep = when {
        granted -> PermissionNextStep.RUN_TEST
        canShowRationale == true -> PermissionNextStep.ASK_AGAIN
        else -> PermissionNextStep.OPEN_SETTINGS
    }

    fun classify(peakDbfs: Double, headroomDb: Double, clipped: Boolean): Verdict = when {
        clipped -> Verdict.CLIPPING
        // Nothing rose above the room. Either the mic is dead or it is fully muted.
        headroomDb < 3.0 -> Verdict.SILENT
        headroomDb < 10.0 -> Verdict.VERY_QUIET
        peakDbfs > -3.0 -> Verdict.LOUD
        else -> Verdict.OK
    }

    // ---- Capture ---------------------------------------------------------------

    /**
     * Build a recorder, or null if the platform refuses (permission denied at the
     * framework level, mic held exclusively by another app, or no input device).
     * Callers must render the not-available state rather than dead-ending.
     */
    @SuppressLint("MissingPermission")
    private fun newRecorder(): Pair<AudioRecord, Int>? {
        return try {
            val minBuf = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_IN, ENCODING)
            if (minBuf <= 0) return null
            val bufSize = minBuf * 2
            val rec = AudioRecord(
                MediaRecorder.AudioSource.MIC, SAMPLE_RATE, CHANNEL_IN, ENCODING, bufSize
            )
            if (rec.state != AudioRecord.STATE_INITIALIZED) {
                rec.release()
                null
            } else {
                rec to bufSize
            }
        } catch (_: Throwable) {
            null
        }
    }

    /**
     * Record for [durationMs], reporting each frame's level through [onLevel] so the
     * UI can show a live meter, and returning the captured PCM for playback.
     *
     * Returns null when the mic could not be opened. Always releases the recorder,
     * including on cancellation — a leaked AudioRecord keeps the mic indicator lit
     * and blocks every other app on the device from recording.
     */
    suspend fun record(
        durationMs: Int,
        onLevel: (LevelReading) -> Unit = {}
    ): Pair<ShortArray, Boolean>? = withContext(Dispatchers.IO) {
        val (rec, bufSize) = newRecorder() ?: return@withContext null
        val frame = ShortArray(bufSize / 2)
        val captured = ArrayList<Short>(SAMPLE_RATE * durationMs / 1000)
        var clipped = false
        try {
            rec.startRecording()
            val deadline = System.currentTimeMillis() + durationMs
            while (System.currentTimeMillis() < deadline && coroutineContext.isActive) {
                val read = rec.read(frame, 0, frame.size)
                if (read <= 0) continue
                if (isClipping(frame, read)) clipped = true
                val r = rms(frame, read)
                var peak = 0
                for (i in 0 until read) {
                    val a = kotlin.math.abs(frame[i].toInt())
                    if (a > peak) peak = a
                }
                onLevel(LevelReading(r, toDbfs(r), peak))
                for (i in 0 until read) captured.add(frame[i])
            }
        } catch (_: Throwable) {
            return@withContext null
        } finally {
            try { rec.stop() } catch (_: Throwable) { /* already stopped */ }
            rec.release()
        }
        captured.toShortArray() to clipped
    }

    /**
     * Play captured PCM back through the media stream. Blocking; caller runs it off
     * the main thread. Released in a finally for the same reason as the recorder.
     */
    suspend fun playback(pcm: ShortArray) = withContext(Dispatchers.IO) {
        if (pcm.isEmpty()) return@withContext
        val minBuf = AudioTrack.getMinBufferSize(
            SAMPLE_RATE, AudioFormat.CHANNEL_OUT_MONO, ENCODING
        )
        if (minBuf <= 0) return@withContext
        val track = try {
            AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(ENCODING)
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(maxOf(minBuf, pcm.size * 2))
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()
        } catch (_: Throwable) {
            return@withContext
        }
        try {
            track.write(pcm, 0, pcm.size)
            track.play()
            // MODE_STATIC plays the whole buffer; wait it out rather than cutting off.
            val ms = (pcm.size * 1000L) / SAMPLE_RATE
            kotlinx.coroutines.delay(ms + 250)
        } catch (_: Throwable) {
            /* playback failure is reported by the user answering "no, I heard nothing" */
        } finally {
            try { track.stop() } catch (_: Throwable) { }
            track.release()
        }
    }

    /** Convenience: is anything else currently holding the mic. */
    fun isMicBusy(audioManager: AudioManager?): Boolean = try {
        audioManager?.mode == AudioManager.MODE_IN_COMMUNICATION ||
            audioManager?.mode == AudioManager.MODE_IN_CALL
    } catch (_: Throwable) {
        false
    }
}
