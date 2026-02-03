package com.teamz.lab.debugger.utils

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.core.content.FileProvider
import com.teamz.lab.debugger.utils.ErrorHandler
import java.io.File
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import kotlin.math.ceil

/**
 * Data class to hold file diagnostics information
 */
data class FileDiagnostics(
    val filePath: String,
    val fileSize: Long,
    val sha256Hash: String,
    val timestamp: Long,
    val preview: String,
    val exists: Boolean,
    val readable: Boolean,
    val encoding: String = "UTF-8"
)

/**
 * Result of sharing operation
 */
sealed class ShareResult {
    data class Success(val diagnostics: FileDiagnostics?) : ShareResult()
    data class PartialSuccess(val message: String, val diagnostics: FileDiagnostics?) : ShareResult()
    data class Failure(val error: String, val fallbackUsed: Boolean) : ShareResult()
}

/**
 * Configuration for sharing behavior
 */
data class ShareConfig(
    val maxTextLength: Int = 10000, // If content exceeds this, send FILE ONLY (no text chunking)
    val enableFileAttachment: Boolean = true,
    val enableClipboardFallback: Boolean = true,
    val enableChunking: Boolean = false, // Disabled by default - use file for large content instead
    val logDiagnostics: Boolean = true
)

/**
 * Calculate SHA-256 hash of a string
 */
private fun calculateSHA256(content: String): String {
    return try {
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(content.toByteArray(StandardCharsets.UTF_8))
        hashBytes.joinToString("") { "%02x".format(it) }
    } catch (e: Exception) {
        "ERROR: ${e.message}"
    }
}

/**
 * Generate file diagnostics
 */
private fun generateFileDiagnostics(
    file: File?,
    content: String,
    encoding: String = "UTF-8"
): FileDiagnostics {
    val exists = file?.exists() ?: false
    val readable = exists && (file?.canRead() ?: false)
    val fileSize = file?.length() ?: 0L
    val sha256Hash = calculateSHA256(content)
    val preview = content.take(500)
    
    return FileDiagnostics(
        filePath = file?.absolutePath ?: "N/A",
        fileSize = fileSize,
        sha256Hash = sha256Hash,
        timestamp = System.currentTimeMillis(),
        preview = preview,
        exists = exists,
        readable = readable,
        encoding = encoding
    )
}

/**
 * Log diagnostics information
 */
private fun logDiagnostics(diagnostics: FileDiagnostics, tag: String = "RobustAIShare") {
    Log.d(tag, "=== FILE DIAGNOSTICS ===")
    Log.d(tag, "Path: ${diagnostics.filePath}")
    Log.d(tag, "Size: ${diagnostics.fileSize} bytes (${diagnostics.fileSize / 1024.0} KB)")
    Log.d(tag, "SHA-256: ${diagnostics.sha256Hash}")
    Log.d(tag, "Timestamp: ${diagnostics.timestamp}")
    Log.d(tag, "Encoding: ${diagnostics.encoding}")
    Log.d(tag, "Exists: ${diagnostics.exists}")
    Log.d(tag, "Readable: ${diagnostics.readable}")
    Log.d(tag, "Preview (first 500 chars):\n${diagnostics.preview}")
    Log.d(tag, "========================")
}

/**
 * Clean text content: remove null bytes, ensure UTF-8, normalize line endings
 */
fun sanitizeTextContent(content: String): String {
    return content
        .replace("\u0000", "") // Remove null bytes
        .replace("\r\n", "\n") // Normalize line endings
        .replace("\r", "\n")
        .trim()
}

/**
 * Split content into chunks with headers
 */
fun chunkContent(content: String, maxChunkSize: Int): List<String> {
    if (content.length <= maxChunkSize) {
        return listOf(content)
    }
    
    val chunks = mutableListOf<String>()
    val totalChunks = ceil(content.length / maxChunkSize.toDouble()).toInt()
    var offset = 0
    
    for (i in 1..totalChunks) {
        val chunk = content.substring(
            offset,
            (offset + maxChunkSize).coerceAtMost(content.length)
        )
        val header = "\n--- Part $i/$totalChunks ---\n\n"
        chunks.add(header + chunk)
        offset += maxChunkSize
    }
    
    return chunks
}

/**
 * Write content to file with UTF-8 encoding and verification
 */
private fun writeFileSafely(file: File, content: String): Boolean {
    return try {
        // Ensure parent directory exists
        file.parentFile?.mkdirs()
        
        // Write with UTF-8 encoding explicitly
        file.writeText(content, StandardCharsets.UTF_8)
        
        // Verify file was written correctly
        val writtenContent = file.readText(StandardCharsets.UTF_8)
        val matches = writtenContent == content
        
        if (!matches) {
            Log.e("RobustAIShare", "File content verification failed!")
            return false
        }
        
        true
    } catch (e: Exception) {
        Log.e("RobustAIShare", "Error writing file: ${e.message}", e)
        false
    }
}

/**
 * Copy text to clipboard (robust version with error handling)
 */
fun copyToClipboardRobust(context: Context, text: String, label: String = "Device Report"): Boolean {
    return try {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(label, text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
        Log.d("RobustAIShare", "Content copied to clipboard (${text.length} chars)")
        true
    } catch (e: Exception) {
        Log.e("RobustAIShare", "Error copying to clipboard: ${e.message}", e)
        false
    }
}

/**
 * Create FileProvider URI for a file
 */
private fun createFileUri(context: Context, file: File): Uri? {
    return try {
        val authority = "${context.packageName}.fileprovider"
        FileProvider.getUriForFile(context, authority, file)
    } catch (e: Exception) {
        Log.e("RobustAIShare", "Error creating file URI: ${e.message}", e)
        null
    }
}

/**
 * Grant URI permissions to target app
 */
private fun grantUriPermissions(
    context: Context,
    uri: Uri,
    targetPackage: String
): Boolean {
    return try {
        context.grantUriPermission(
            targetPackage,
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
        )
        Log.d("RobustAIShare", "Granted URI permission to: $targetPackage")
        true
    } catch (e: Exception) {
        Log.e("RobustAIShare", "Error granting URI permission: ${e.message}", e)
        false
    }
}

/**
 * ROBUST AI SHARING FUNCTION
 * 
 * Strategy:
 * 1. PRIMARY: Send text content directly in message body (EXTRA_TEXT)
 * 2. SECONDARY: Attach file (EXTRA_STREAM) if enabled and file creation succeeds
 * 3. FALLBACK: Copy to clipboard if sharing fails
 * 
 * This ensures ChatGPT ALWAYS receives the content, even if file expires.
 */
fun shareWithAIAppRobust(
    context: Context,
    content: String,
    fileName: String,
    aiAppPackageName: String,
    aiAppName: String,
    config: ShareConfig = ShareConfig()
): ShareResult {
    val tag = "RobustAIShare"
    val sanitizedContent = sanitizeTextContent(content)
    
    Log.d(tag, "Starting robust share: ${sanitizedContent.length} chars to $aiAppName")
    
    // Generate diagnostics
    var diagnostics: FileDiagnostics? = null
    var file: File? = null
    var fileUri: Uri? = null
    
    // Step 1: Create file (if enabled)
    if (config.enableFileAttachment) {
        try {
            val timestamp = System.currentTimeMillis()
            val uniqueFileName = fileName.replace(".txt", "_$timestamp.txt")
            file = File(context.filesDir, uniqueFileName)
            
            if (writeFileSafely(file, sanitizedContent)) {
                diagnostics = generateFileDiagnostics(file, sanitizedContent)
                
                if (config.logDiagnostics) {
                    logDiagnostics(diagnostics, tag)
                }
                
                // Verify file exists and is readable
                if (!diagnostics.exists || !diagnostics.readable) {
                    Log.w(tag, "File verification failed, but continuing with text-only share")
                } else {
                    fileUri = createFileUri(context, file)
                    if (fileUri != null) {
                        grantUriPermissions(context, fileUri, aiAppPackageName)
                    }
                }
            } else {
                Log.w(tag, "File creation failed, continuing with text-only share")
            }
        } catch (e: Exception) {
            Log.e(tag, "Error in file creation: ${e.message}", e)
            handleError(e, context = "RobustAIShare.fileCreation")
        }
    }
    
    // Step 2: Prepare share intent
    // STRATEGY: Always send text in EXTRA_TEXT as PRIMARY (never expires)
    // File attachment is SECONDARY (convenience/backup, but may expire)
    // This ensures ChatGPT ALWAYS has the content, even if file expires
    val shareIntent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        
        // PRIMARY: Always send text in message body (this never expires)
        // ChatGPT can read this directly, even if file expires later
        putExtra(Intent.EXTRA_TEXT, sanitizedContent)
        Log.d(tag, "Setting EXTRA_TEXT with ${sanitizedContent.length} chars (PRIMARY - never expires)")
        
        // SECONDARY: File attachment (convenience/backup, but may expire)
        // Some apps prefer file attachments, but text is the reliable fallback
        if (fileUri != null && config.enableFileAttachment) {
            putExtra(Intent.EXTRA_STREAM, fileUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            Log.d(tag, "Also attaching file as SECONDARY (may expire, but text is always available)")
        }
        
        setPackage(aiAppPackageName)
    }
    
    // Step 4: Attempt to share
    return try {
        context.startActivity(shareIntent)
        Log.d(tag, "Successfully shared with $aiAppName (text + ${if (fileUri != null) "file" else "no file"})")
        
        ShareResult.Success(diagnostics)
    } catch (e: android.content.ActivityNotFoundException) {
        // App not installed - this is expected and not an error
        Log.d(tag, "$aiAppName is not installed on this device")
        
        // FALLBACK: Copy to clipboard
        if (config.enableClipboardFallback) {
            val clipboardSuccess = copyToClipboardRobust(context, sanitizedContent, "Device Report")
            if (clipboardSuccess) {
                Toast.makeText(
                    context,
                    "$aiAppName is not installed. Content copied to clipboard. Please paste manually.",
                    Toast.LENGTH_LONG
                ).show()
                return ShareResult.PartialSuccess("Shared via clipboard", diagnostics)
            }
        }
        
        // Last resort: Try chooser
        try {
            val chooserIntent = Intent.createChooser(shareIntent, "Share with $aiAppName")
            context.startActivity(chooserIntent)
            ShareResult.PartialSuccess("Opened share chooser", diagnostics)
        } catch (e2: android.content.ActivityNotFoundException) {
            // No apps available to handle the share - this is expected
            ShareResult.Failure("No apps available to share. Content may be copied to clipboard.", fallbackUsed = true)
        } catch (e2: Exception) {
            // Only log unexpected errors
            ErrorHandler.handleError(e2, context = "RobustAIShare.chooser-$aiAppName")
            ShareResult.Failure("All sharing methods failed: ${e2.message}", fallbackUsed = true)
        }
    } catch (e: Exception) {
        // Handle other unexpected errors (not ActivityNotFoundException)
        Log.e(tag, "Error sharing with $aiAppName: ${e.message}", e)
        ErrorHandler.handleError(e, context = "RobustAIShare.share-$aiAppName")
        
        // FALLBACK: Copy to clipboard
        if (config.enableClipboardFallback) {
            val clipboardSuccess = copyToClipboardRobust(context, sanitizedContent, "Device Report")
            if (clipboardSuccess) {
                Toast.makeText(
                    context,
                    "Sharing failed. Content copied to clipboard. Please paste manually.",
                    Toast.LENGTH_LONG
                ).show()
                return ShareResult.PartialSuccess("Shared via clipboard", diagnostics)
            }
        }
        
        // Last resort: Try chooser
        try {
            val chooserIntent = Intent.createChooser(shareIntent, "Share with $aiAppName")
            context.startActivity(chooserIntent)
            ShareResult.PartialSuccess("Opened share chooser", diagnostics)
        } catch (e2: android.content.ActivityNotFoundException) {
            // No apps available to handle the share - this is expected
            ShareResult.Failure("No apps available to share. Content may be copied to clipboard.", fallbackUsed = true)
        } catch (e2: Exception) {
            // Only log unexpected errors
            ErrorHandler.handleError(e2, context = "RobustAIShare.chooser-$aiAppName")
            ShareResult.Failure("All sharing methods failed: ${e2.message}", fallbackUsed = true)
        }
    }
}

/**
 * Convenience function to share device report with ChatGPT
 */
fun shareDeviceReportToChatGPT(
    context: Context,
    reportContent: String,
    reportType: String = "device_info"
): ShareResult {
    val fileName = "${reportType}_${System.currentTimeMillis()}.txt"
    return shareWithAIAppRobust(
        context = context,
        content = reportContent,
        fileName = fileName,
        aiAppPackageName = "com.openai.chatgpt", // ChatGPT Android package
        aiAppName = "ChatGPT",
        config = ShareConfig(
            maxTextLength = 10000,
            enableFileAttachment = true,
            enableClipboardFallback = true,
            enableChunking = false, // Disabled - use file for large content
            logDiagnostics = true
        )
    )
}

