package com.teamz.lab.debugger.utils

import com.teamz.lab.debugger.BuildConfig
import android.util.Log

/**
 * Centralized logging utility for production-ready apps
 * 
 * All debug logs are automatically gated by BuildConfig.DEBUG to prevent
 * performance and privacy issues in production builds.
 * 
 * Usage:
 * - AppLog.d("Tag", "message") - Debug logs (only in debug builds)
 * - AppLog.i("Tag", "message") - Info logs (always shown)
 * - AppLog.w("Tag", "message") - Warning logs (always shown)
 * - AppLog.e("Tag", "message") - Error logs (always shown)
 */
object AppLog {
    
    /**
     * Debug log - only shown in debug builds
     * Automatically disabled in release builds for performance and privacy
     */
    fun d(tag: String, message: String) {
        if (BuildConfig.DEBUG) {
            Log.d(tag, message)
        }
    }
    
    /**
     * Debug log with throwable - only shown in debug builds
     */
    fun d(tag: String, message: String, throwable: Throwable) {
        if (BuildConfig.DEBUG) {
            Log.d(tag, message, throwable)
        }
    }
    
    /**
     * Info log - always shown (for important information)
     */
    fun i(tag: String, message: String) {
        Log.i(tag, message)
    }
    
    /**
     * Info log with throwable
     */
    fun i(tag: String, message: String, throwable: Throwable) {
        Log.i(tag, message, throwable)
    }
    
    /**
     * Warning log - always shown (for warnings)
     */
    fun w(tag: String, message: String) {
        Log.w(tag, message)
    }
    
    /**
     * Warning log with throwable
     */
    fun w(tag: String, message: String, throwable: Throwable) {
        Log.w(tag, message, throwable)
    }
    
    /**
     * Error log - always shown (for errors)
     */
    fun e(tag: String, message: String) {
        Log.e(tag, message)
    }
    
    /**
     * Error log with throwable
     */
    fun e(tag: String, message: String, throwable: Throwable) {
        Log.e(tag, message, throwable)
    }
    
    /**
     * Verbose log - only shown in debug builds
     */
    fun v(tag: String, message: String) {
        if (BuildConfig.DEBUG) {
            Log.v(tag, message)
        }
    }
    
    /**
     * Verbose log with throwable - only shown in debug builds
     */
    fun v(tag: String, message: String, throwable: Throwable) {
        if (BuildConfig.DEBUG) {
            Log.v(tag, message, throwable)
        }
    }
}
