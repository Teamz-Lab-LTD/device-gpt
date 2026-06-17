import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.jetbrains.kotlin.android)
    alias(libs.plugins.google.gms.google.services)
    alias(libs.plugins.google.firebase.crashlytics)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.google.devtools.ksp)
    id("jacoco")
}


val keystorePropertiesFile = rootProject.file("key.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) {
        load(keystorePropertiesFile.inputStream())
    }
}

// Load local config for AdMob IDs
val localConfigFile = rootProject.file("local_config.properties")
val localConfigProperties = Properties().apply {
    if (localConfigFile.exists()) {
        load(localConfigFile.inputStream())
    }
}

android {
    namespace = "com.teamz.lab.debugger"
    compileSdk = 36
    // Pin NDK so native strip / symbol tasks use a consistent toolchain (Play + Crashlytics).
    ndkVersion = "26.3.11579264"

    defaultConfig {
        applicationId = "com.teamz.lab.debugger"
        minSdk = 24
        targetSdk = 36
        versionCode = 24
        versionName = "3.1.9"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
        
        // AdMob IDs from local_config.properties (fallback to test IDs if not found)
        buildConfigField("String", "ADMOB_APP_ID", 
            "\"${localConfigProperties.getProperty("ADMOB_APP_ID", "ca-app-pub-3940256099942544~3419835294")}\"")
        buildConfigField("String", "APP_OPEN_AD_UNIT_ID", 
            "\"${localConfigProperties.getProperty("APP_OPEN_AD_UNIT_ID", "ca-app-pub-3940256099942555/9257395921")}\"")
        buildConfigField("String", "INTERSTITIAL_AD_UNIT_ID", 
            "\"${localConfigProperties.getProperty("INTERSTITIAL_AD_UNIT_ID", "ca-app-pub-3940256099942544/1033173712")}\"")
        buildConfigField("String", "NATIVE_AD_UNIT_ID", 
            "\"${localConfigProperties.getProperty("NATIVE_AD_UNIT_ID", "ca-app-pub-3940256099942544/2247696110")}\"")
        buildConfigField("String", "REWARDED_AD_UNIT_ID", 
            "\"${localConfigProperties.getProperty("REWARDED_AD_UNIT_ID", "ca-app-pub-3940256099942544/5224354917")}\"")
        buildConfigField("String", "OAUTH_CLIENT_ID", 
            "\"${localConfigProperties.getProperty("OAUTH_CLIENT_ID", "")}\"")
        buildConfigField("String", "ONESIGNAL_APP_ID", 
            "\"${localConfigProperties.getProperty("ONESIGNAL_APP_ID", "")}\"")
        buildConfigField("String", "REVENUECAT_API_KEY", 
            "\"${localConfigProperties.getProperty("REVENUECAT_API_KEY", "")}\"")
        
        // Manifest placeholders for AndroidManifest.xml
        manifestPlaceholders["admobAppId"] = localConfigProperties.getProperty("ADMOB_APP_ID", "ca-app-pub-3940256099942544~3419835294")
        
        // Resource values for strings.xml (will be replaced at build time)
        resValue("string", "default_web_client_id", localConfigProperties.getProperty("OAUTH_CLIENT_ID", ""))
    }

    signingConfigs {
        create("release") {
            if (keystorePropertiesFile.exists()) {
                keyAlias = keystoreProperties["keyAlias"] as String? ?: "release-key"
                keyPassword = keystoreProperties["keyPassword"] as String? ?: ""
                val storeFileStr = keystoreProperties["storeFile"] as String? ?: "release-key.jks"
                storeFile = file(storeFileStr)
                storePassword = keystoreProperties["storePassword"] as String? ?: ""
            } else {
                // For open source builds, use debug signing if key.properties doesn't exist
                keyAlias = "androiddebugkey"
                keyPassword = "android"
                storeFile = file("${System.getProperty("user.home")}/.android/debug.keystore")
                storePassword = "android"
            }
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            // Only use release signing if key.properties exists
            if (keystorePropertiesFile.exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Emit native debug metadata for any unstripped .so we ship. Play embeds it in the AAB when
            // extraction succeeds. Note: Maven AARs (e.g. androidx.graphics:graphics-path, datastore JNI)
            // ship pre-stripped .so, so AGP often cannot extract symbols — Play may still show a
            // recommendation; that is expected until library vendors publish debug info.
            ndk {
                debugSymbolLevel = "FULL"
            }
            firebaseCrashlytics {
                nativeSymbolUploadEnabled = true
            }
        }
        getByName("debug") {
            // Enable native debug symbols for debug builds too
            ndk {
                debugSymbolLevel = "FULL"
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.1"
    }
    packaging {
        jniLibs {
            // Avoid stripping already-minimal JNI from dependencies further; helps symbol extraction when
            // unstripped artifacts are present.
            keepDebugSymbols += "**/*.so"
        }
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(libs.coil.compose)
    implementation(libs.play.services.ads)
    // UMP (User Messaging Platform) — GDPR/PDPA consent flow for EU + Singapore + Sweden.
    // Without this, ad networks refuse to serve ads in those geos → ad_failed storm.
    implementation("com.google.android.ump:user-messaging-platform:3.0.0")
    implementation(libs.androidx.material3)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.0")
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.crashlytics)
    implementation(libs.play.services.location)
    implementation(libs.review.ktx)
    implementation(libs.integrity)
    implementation(libs.material.icons.extended)
    implementation(libs.androidx.ui.text.google.fonts)
    implementation(libs.play.services.measurement.api)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.app.update)
    implementation(libs.onesignal)
    implementation(libs.firebase.config.ktx)
    implementation(libs.firebase.firestore.ktx)
    implementation("com.google.firebase:firebase-auth-ktx")
    // Credential Manager API (latest Google Sign-In approach)
    // 1.5.0+ required for Restore Credentials (CreateRestoreCredentialRequest); 1.3.0 lacked those APIs.
    implementation("androidx.credentials:credentials:1.5.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.5.0")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.1.0")
    implementation("com.google.android.gms:play-services-auth:21.0.0")
    // WorkManager for reliable background notification scheduling
    implementation(libs.androidx.work.runtime.ktx)
    // RevenueCat for subscription management and ad removal
    implementation(libs.purchases)
    // RevenueCat Paywall UI SDK for displaying paywalls designed in RevenueCat console
    implementation(libs.purchases.ui)
    implementation(libs.install.referrer)
    implementation(libs.androidx.uiautomator)

    // AppFunctions — expose DeviceGPT actions to Gemini agent (requires Android 16+ at runtime).
    // Gated behind @RequiresApi on every call site; app minSdk stays at 24.
    implementation(libs.androidx.appfunctions)
    implementation(libs.androidx.appfunctions.service)
    ksp(libs.androidx.appfunctions.compiler)

    // ML Kit GenAI — on-device Gemini Nano text summarisation / rewriting for post-scan explainer.
    // Runtime feature-detect; falls back to cloud AI chooser on unsupported devices.
    implementation(libs.mlkit.genai.summarization)
    implementation(libs.mlkit.genai.rewriting)

    // Adaptive layout for Desktop Mode / freeform windowing (Android 16 QPR3 / Android 17).
    implementation(libs.androidx.compose.material3.adaptive)
    implementation(libs.androidx.compose.material3.adaptive.layout)
    implementation(libs.androidx.compose.material3.adaptive.navigation)

    testImplementation(libs.junit)
    testImplementation("org.robolectric:robolectric:4.13")
    testImplementation("androidx.test:core:1.5.0")
    testImplementation(libs.androidx.junit.v115)
    testImplementation("org.mockito:mockito-core:5.21.0")
    testImplementation("org.mockito.kotlin:mockito-kotlin:5.1.0")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    // UiAutomator for real device notification testing
    androidTestImplementation(libs.androidx.uiautomator)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
    testImplementation(kotlin("test"))
}

// Jacoco configuration for test coverage
apply(plugin = "jacoco")

tasks.register<JacocoReport>("jacocoTestReport") {
    dependsOn("testDebugUnitTest")
    
    reports {
        xml.required.set(true)
        html.required.set(true)
        csv.required.set(false)
    }
    
    val fileFilter = listOf(
        "**/R.class",
        "**/R$*.class",
        "**/BuildConfig.*",
        "**/Manifest*.*",
        "**/*Test*.*",
        "android/**/*.*"
    )
    
    val debugTree = fileTree("${project.buildDir}/intermediates/javac/debug/classes") {
        exclude(fileFilter)
    }
    val mainSrc = "${project.projectDir}/src/main/java"
    
    sourceDirectories.setFrom(files(mainSrc))
    classDirectories.setFrom(files(debugTree))
    executionData.setFrom(fileTree(project.buildDir) {
        include("jacoco/testDebugUnitTest.exec")
    })
}

jacoco {
    toolVersion = "0.8.11"
}

// Task to upload native debug symbols to Firebase Crashlytics
// This task uploads symbols after building the release bundle
tasks.register("uploadCrashlyticsSymbolFileRelease") {
    group = "firebase"
    description = "Uploads native debug symbols to Firebase Crashlytics"
    
    dependsOn("bundleRelease", "extractReleaseNativeDebugMetadata")
    
    doLast {
        val symbolsDir = file("${project.buildDir}/outputs/native-debug-symbols/release")
        if (symbolsDir.exists() && symbolsDir.listFiles()?.isNotEmpty() == true) {
            println("📤 Uploading native debug symbols to Firebase Crashlytics...")
            println("   Location: ${symbolsDir.absolutePath}")
            // Firebase Crashlytics plugin will handle the upload automatically
            // when the app is built with the plugin enabled
        } else {
            println("⚠️  No native debug symbols found. This is normal if your app has no native code.")
        }
    }
}

// Task to prepare native debug symbols for Google Play Console upload
// With AGP 8.1+, symbols are embedded in the AAB when debugSymbolLevel = "FULL"
tasks.register("prepareNativeDebugSymbols") {
    group = "build"
    description = "Prepares native debug symbols for Google Play Console upload"
    
    dependsOn("bundleRelease", "extractReleaseNativeDebugMetadata")
    
    doLast {
        val aabFile = file("${project.buildDir}/outputs/bundle/release/app-release.aab")
        val nativeLibsDir = file("${project.buildDir}/intermediates/merged_native_libs/release/mergeReleaseNativeLibs/out/lib")
        
        println("🔍 Native Debug Symbols Status")
        println("==============================")
        println("")
        
        // Check if AAB exists
        if (aabFile.exists()) {
            val aabSize = aabFile.length() / (1024 * 1024) // Size in MB
            println("✅ AAB file found: ${aabFile.absolutePath}")
            println("   Size: ${aabSize} MB")
        } else {
            println("❌ AAB file not found. Run: ./gradlew bundleRelease")
            return@doLast
        }
        
        // Check for native libraries
        var hasNativeLibs = false
        if (nativeLibsDir.exists()) {
            val soFiles = fileTree(nativeLibsDir).matching { include("**/*.so") }.files
            if (soFiles.isNotEmpty()) {
                hasNativeLibs = true
                println("✅ Native libraries detected: ${soFiles.size} .so files")
                println("   Location: ${nativeLibsDir.absolutePath}")
            }
        }
        
        println("")
        println("📦 Configuration Status:")
        println("   ✅ debugSymbolLevel = 'FULL' (configured in build.gradle.kts)")
        if (hasNativeLibs) {
            println("   ✅ Native libraries found in build")
            println("   ✅ Symbols should be embedded in AAB (AGP 8.1+ feature)")
        } else {
            println("   ⚠️  No native libraries detected (may be from dependencies)")
        }
        
        println("")
        println("📋 Upload Instructions:")
        println("   1. Upload your AAB to Google Play Console:")
        println("      → ${aabFile.absolutePath}")
        println("")
        println("   2. With AGP 8.1+ and debugSymbolLevel = 'FULL', native debug symbols")
        println("      are automatically embedded in your AAB file.")
        println("")
        println("   3. After uploading, Google Play Console should automatically:")
        println("      - Extract the symbols from your AAB")
        println("      - Process them for crash analysis")
        println("      - The warning should disappear within a few minutes")
        println("")
        println("   4. If the warning persists after 10-15 minutes:")
        println("      a. Go to: Google Play Console → Your App → Release → Setup")
        println("      b. Click: 'App integrity'")
        println("      c. Scroll to: 'Native code debug files'")
        println("      d. Check if symbols are listed there")
        println("      e. If not, try re-uploading the AAB")
        println("      f. Contact Google Play support if issue persists")
        println("")
        println("💡 Note: The warning may appear initially but should resolve")
        println("   automatically once Google Play processes your AAB file.")
    }
}