# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Preserve line number information for debugging stack traces
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Keep Firebase classes
-keep class com.google.firebase.** { *; }
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.firebase.**
-dontwarn com.google.android.gms.**

# Keep AdMob classes
-keep class com.google.android.gms.ads.** { *; }
-dontwarn com.google.android.gms.ads.**

# Keep OneSignal classes
-keep class com.onesignal.** { *; }
-dontwarn com.onesignal.**

# Keep Kotlin coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.** {
    volatile <fields>;
}

# Keep Compose runtime
-keep class androidx.compose.runtime.** { *; }
-keep class androidx.compose.ui.** { *; }
-keep class androidx.compose.foundation.** { *; }
-keep class androidx.compose.material3.** { *; }

# Keep data classes used in Parcelable
-keep class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator *;
}

# Keep native methods
-keepclasseswithmembernames class * {
    native <methods>;
}

# Keep ViewModels
-keep class * extends androidx.lifecycle.ViewModel { *; }

# Keep WorkManager
-keep class androidx.work.** { *; }
-dontwarn androidx.work.**

# Keep Crashlytics
-keepattributes *Annotation*
-keepattributes SourceFile,LineNumberTable
-keep public class * extends java.lang.Exception
-keep class com.google.firebase.crashlytics.** { *; }
-dontwarn com.google.firebase.crashlytics.**

# Keep application class
-keep class com.teamz.lab.debugger.MyApplication { *; }
-keep class com.teamz.lab.debugger.MainActivity { *; }

# Keep all model/data classes
-keep class com.teamz.lab.debugger.** { *; }
-dontwarn com.teamz.lab.debugger.**

# Remove logging in release (optional but recommended for performance)
# Note: AppLog.d() and AppLog.v() are already gated by BuildConfig.DEBUG,
# but this ensures any remaining Log calls are removed in release builds
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
}

# Remove AppLog debug/verbose calls in release (already gated by BuildConfig.DEBUG, but extra safety)
-assumenosideeffects class com.teamz.lab.debugger.utils.AppLog {
    public static *** d(...);
    public static *** v(...);
}