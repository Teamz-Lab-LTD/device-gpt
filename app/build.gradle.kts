import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.jetbrains.kotlin.android)
    alias(libs.plugins.google.gms.google.services)
    alias(libs.plugins.google.firebase.crashlytics)
    alias(libs.plugins.kotlin.compose)
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

    defaultConfig {
        applicationId = "com.teamz.lab.debugger"
        minSdk = 24
        targetSdk = 36
        versionCode = 9
        versionName = "3.0.2"
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
        }
        getByName("debug") {

        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.1"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(libs.coil.compose)
    implementation(libs.play.services.ads)
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
    implementation("androidx.credentials:credentials:1.3.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.3.0")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.1.0")
    implementation("com.google.android.gms:play-services-auth:21.0.0")
    // WorkManager for reliable background notification scheduling
    implementation("androidx.work:work-runtime-ktx:2.9.0")
    implementation(libs.androidx.uiautomator)
    testImplementation(libs.junit)
    testImplementation("org.robolectric:robolectric:4.13")
    testImplementation("androidx.test:core:1.5.0")
    testImplementation("androidx.test.ext:junit:1.1.5")
    testImplementation("org.mockito:mockito-core:5.1.1")
    testImplementation("org.mockito.kotlin:mockito-kotlin:5.1.0")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    // UiAutomator for real device notification testing
    androidTestImplementation("androidx.test.uiautomator:uiautomator:2.3.0")
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