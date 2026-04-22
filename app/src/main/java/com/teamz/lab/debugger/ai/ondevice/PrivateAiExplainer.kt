package com.teamz.lab.debugger.ai.ondevice

import android.content.Context
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.mlkit.genai.common.DownloadCallback
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.common.GenAiException
import com.google.mlkit.genai.summarization.Summarization
import com.google.mlkit.genai.summarization.SummarizationRequest
import com.google.mlkit.genai.summarization.Summarizer
import com.google.mlkit.genai.summarization.SummarizerOptions
import com.teamz.lab.debugger.ai.ondevice.OnDeviceAiAvailability.Status
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Post-scan explainer powered by on-device Gemini Nano via ML Kit GenAI.
 *
 * Public contract:
 *   1. [probeAvailability] — check whether this device can run it right now.
 *   2. [ensureModelReady] — if DOWNLOADABLE, download the model (one-time, ~300MB).
 *   3. [explain] — feed a plain-text prompt, get a short plain-English explanation.
 *
 * Branding: every string produced here must be shown with a "Private AI · on-device"
 * badge in the UI. This is the visible wow moment that differentiates DeviceGPT from
 * the cloud AI chooser (ChatGPT/Gemini/Claude/etc.).
 */
object PrivateAiExplainer {

    @Volatile
    private var cachedSummarizer: Summarizer? = null

    /** Synthetic packageName used to represent "on-device AI" inside the existing AIApp list. */
    const val SYNTHETIC_PACKAGE: String = "ondevice://gemini-nano"

    /** Human-readable display name for the chooser row. */
    const val DISPLAY_NAME: String = "Private AI (on-device)"

    /** One-line subtitle for the chooser row. */
    const val TAGLINE: String = "Your phone only · offline · powered by Gemini Nano"

    internal fun probeAvailability(context: Context): Status {
        val summarizer = getOrCreate(context)
        return when (checkStatusBlocking(summarizer)) {
            FeatureStatus.AVAILABLE -> Status.READY
            FeatureStatus.DOWNLOADABLE -> Status.DOWNLOADABLE
            else -> Status.UNSUPPORTED
        }
    }

    /**
     * Ensures the Gemini Nano feature model is present. If already downloaded, returns
     * fast. If DOWNLOADABLE, triggers the system-managed download (no UI of our own).
     */
    suspend fun ensureModelReady(context: Context): Status = withContext(Dispatchers.IO) {
        val summarizer = getOrCreate(context)
        val status = checkStatusBlocking(summarizer)
        when (status) {
            FeatureStatus.AVAILABLE -> Status.READY
            FeatureStatus.DOWNLOADABLE -> {
                runCatching { downloadFeatureBlocking(summarizer) }
                    .onFailure { FirebaseCrashlytics.getInstance().recordException(it) }
                val after = runCatching { checkStatusBlocking(summarizer) }
                    .getOrDefault(FeatureStatus.UNAVAILABLE)
                if (after == FeatureStatus.AVAILABLE) Status.READY else Status.DOWNLOADABLE
            }
            else -> Status.UNSUPPORTED
        }
    }

    /**
     * Produces a short plain-English explanation from the provided scan text.
     * The ML Kit summarization model turns the structured scan dump into one
     * conversational bullet — fast enough to feel instant and small enough to
     * fit under the existing result card.
     */
    suspend fun explain(
        context: Context,
        scanSubject: String,
        scanText: String,
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val summarizer = getOrCreate(context)
            val ready = checkStatusBlocking(summarizer)
            if (ready != FeatureStatus.AVAILABLE) {
                error("On-device AI not ready (status=$ready)")
            }
            val prompt = buildPrompt(scanSubject, scanText)
            val request = SummarizationRequest.builder(prompt).build()
            val result = summarizer.runInference(request).get()
            result.summary
        }
    }

    private fun buildPrompt(subject: String, scanText: String): String = """
        DeviceGPT scan: $subject.
        Please summarise the result below in 2 short friendly sentences a normal
        phone user can understand. End with one concrete action tip if anything
        looks wrong.

        Result:
        $scanText
    """.trimIndent()

    @Synchronized
    private fun getOrCreate(context: Context): Summarizer {
        cachedSummarizer?.let { return it }
        val options = SummarizerOptions.builder(context)
            .setInputType(SummarizerOptions.InputType.ARTICLE)
            .setOutputType(SummarizerOptions.OutputType.ONE_BULLET)
            .setLanguage(SummarizerOptions.Language.ENGLISH)
            .build()
        val s = Summarization.getClient(options)
        cachedSummarizer = s
        return s
    }

    private fun checkStatusBlocking(summarizer: Summarizer): Int =
        summarizer.checkFeatureStatus().get()

    private fun downloadFeatureBlocking(summarizer: Summarizer) {
        val callback = object : DownloadCallback {
            override fun onDownloadStarted(bytesToDownload: Long) {}
            override fun onDownloadFailed(e: GenAiException) {
                FirebaseCrashlytics.getInstance().recordException(e)
            }
            override fun onDownloadProgress(totalBytesDownloaded: Long) {}
            override fun onDownloadCompleted() {}
        }
        summarizer.downloadFeature(callback).get()
    }
}
