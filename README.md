# DeviceGPT: AI Phone Health 📱

<div align="center">

![DeviceGPT](https://img.shields.io/badge/DeviceGPT%3A%20AI%20Phone%20Health-Android-blue?style=for-the-badge&logo=android)
![License](https://img.shields.io/badge/license-MIT-green?style=for-the-badge)
![Kotlin](https://img.shields.io/badge/Kotlin-2.1-blue?style=for-the-badge&logo=kotlin)
![Compose](https://img.shields.io/badge/Jetpack%20Compose-Material%203-orange?style=for-the-badge)

**AI-Powered Android Device Monitoring • Phone Health Checker • Privacy Guardian**

[Features](#-features) • [Quick Start](#-quick-start) • [Contributing](#-contributing) • [Work with Teamz Lab](#-work-with-teamz-lab--lets-build-your-app)

</div>

---

## 📥 Download DeviceGPT: AI Phone Health

<div align="center">

[![Get it on Google Play](https://img.shields.io/badge/Get%20it%20on-Google%20Play-4285F4?style=for-the-badge&logo=google-play&logoColor=white)](https://play.google.com/store/apps/details?id=com.teamz.lab.debugger)

[![Product Hunt](https://img.shields.io/badge/Product%20Hunt-Upvote-orange?style=for-the-badge&logo=product-hunt&logoColor=white)](https://www.producthunt.com/p/devicegpt-ai-phone-health)
[![XDA Forums](https://img.shields.io/badge/XDA%20Forums-Discuss-FF6900?style=for-the-badge&logo=xda-developers&logoColor=white)](https://xdaforums.com/t/app-devicegpt-ai-powered-android-monitor-real-data-privacy-guardian-global-leaderboard.4773593/)

**⭐ 500+ Downloads • 🔒 Privacy First • 🤖 AI-Powered**

</div>

---

## What DeviceGPT Does

🔍 **DeviceGPT: AI Phone Health** scans your Android device — battery, storage, mic/camera logs, speed & privacy. Get instant phone reports explained in plain English by ChatGPT, Gemini, or Claude.

**No more confusing graphs or technical data. Just scan → share → understand.**

### For Everyone (Non-Technical)

- ✅ **Check phone performance**: See if your phone is slow, hot, or draining battery
- ✅ **Battery health tracker**: Monitor real-time power consumption (watts), charge speed, and battery life
- ✅ **Privacy scanner**: Detect hidden mic/camera usage, tracking apps, and security threats
- ✅ **Generate certified phone report**: Create a device certificate with resale value estimation
- ✅ **Spot fake GPS**: Detect GPS or sensor spoofing tools
- ✅ **Internet speed test**: Run smart speed tests + ISP privacy checks
- ✅ **Export to AI**: Share your scan directly with AI assistants for instant explanations

### For Developers & Researchers (Technical)

- ✅ **Real-time system monitoring**: CPU, RAM, storage, network via foreground service
- ✅ **Power consumption research**: Component-level power measurement (Camera, Display, CPU, Network) with CSV export
- ✅ **Research-grade experiments**: Standardized testing protocols based on latest power consumption research papers
- ✅ **CSV data export**: Export power experiments for academic research
- ✅ **API-based monitoring**: Uses BatteryManager, ActivityManager, and system APIs (no root required)

**Perfect for:**
- Anyone asking: "Why is my phone slow, hot, or weird?"
- Android developers building device-aware apps
- Researchers studying mobile power consumption
- Parents checking kids' phones
- Privacy-conscious users
- Tech enthusiasts using ChatGPT, Gemini, or Claude regularly

<div align="center">

**[📱 Download on Google Play](https://play.google.com/store/apps/details?id=com.teamz.lab.debugger)** • **[🚀 Try It](#-quick-start)** • **[📖 Build It](#setup)** • **[🤝 Contribute](#-contributing)**

</div>

---

## 🤖 Let AI Explain It To You

**This is DeviceGPT's core feature** — making complex device data understandable through AI.

After each scan, simply tap **"Ask AI"**. We prefill a smart prompt for ChatGPT, Gemini, Claude, Perplexity, Copilot & more. Get instant fixes like:

- **"Battery is overheating, try reducing background apps."**
- **"Mic was used last night, consider revoking app permissions."**
- **"Wi-Fi jitter may affect gaming, restart router."**
- **"Your phone's health score is 7/10. Here's how to improve it..."**

### How It Works

1. **Scan your device** — DeviceGPT collects real-time data (battery, CPU, network, privacy)
2. **Tap "Ask AI"** — Choose your AI assistant (ChatGPT, Gemini, Claude, etc.)
3. **Get plain English answers** — AI explains what the data means and how to fix issues
4. **Simple or Detailed mode** — Choose explanation level based on your technical knowledge

**Supported AI Assistants:**
- ChatGPT (OpenAI)
- Gemini (Google, formerly Bard)
- Claude (Anthropic)
- DeepSeek
- Perplexity
- Microsoft Copilot (Bing AI)
- Grok
- You.com AI Chat
- Replika AI Companion

**Implementation**: `ai_assistant_dialog.kt`, `ai_prompt_generator.kt`, `robust_ai_sharing.kt`

---

## ✨ Features

### 📱 Device Information & Performance

**For Users:**
- Check phone performance, battery health, storage & temperature
- See device model, Android version, and hardware specs
- Monitor frame rate (FPS) and performance metrics
- Check security status (root detection, developer mode)

**For Developers:**
- Real-time CPU, RAM, and storage monitoring via ActivityManager API
- Battery health, temperature, and charging status via BatteryManager API
- Frame rate (FPS) and performance metrics via Choreographer API
- Security status detection (root, developer mode, bootloader state)
- Lock screen widget (Android 13+) for home screen monitoring

**Implementation**: `device_info_ui.kt`, `device_utils.kt`, `SystemMonitorService.kt`, `LockScreenMonitorWidget.kt`

### 🔐 Privacy & Security Scanner

**For Users:**
- Detect mic/camera use & background spying (privacy check)
- Spot fake GPS or sensor spoofing tools
- Anti-snoop motion detector — alerts if someone touched your phone while locked
- Check for spyware and tracking apps

**For Developers:**
- **Mic/Camera Detection**: `isMicrophoneBeingUsed()`, `getRecentCameraMicUsageLog()` — detects background mic/camera usage via logcat
- **Spyware Scanner**: `isDeviceBeingMonitored()` — detects screen recording apps, keyloggers, suspicious accessibility services
- **GPS Spoofing Detection**: `detectSensorSpoofing()` — detects fake GPS apps (Mock Location, Fake GPS, etc.)
- **Motion Detection**: `detectMotionWhileLocked()` — detects if phone moved while locked
- **Keylogger Detection**: `detectKeylogger()` — scans for known keylogger apps
- **Privacy Analysis**: Comprehensive privacy score with tracking analysis, data collection breakdown, and protection strategies

**Implementation**: `device_utils.kt` (lines 1553-1996), `device_info_ui.kt`

### 🌐 Network Monitoring & ISP Privacy

**For Users:**
- Run smart internet speed + ISP privacy test
- Check WiFi signal strength and network information
- Verify ISP privacy, DNS safety, and real 5G/WiFi speed measurements

**For Developers:**
- Network type detection (WiFi, Mobile Data, Ethernet)
- IP address (IPv4/IPv6) and connection details
- Real network speed testing (download/upload via HTTP transfers — actually downloads 10MB from Cloudflare)
- WiFi signal strength (RSSI) and network information
- Network latency measurement (ping-based)
- **ISP Privacy Testing**: DNS manipulation detection, SSL certificate hijack detection, Deep Packet Inspection (DPI) detection, ISP tracking analysis

**Implementation**: `network_ui.kt`, `network_utils.kt`, `SystemMonitorService.kt`

### ❤️ Health Tracking & Scoring

**For Users:**
- Device health score calculation (0-10 scale)
- Daily streak tracking and history
- Achievement system for milestones
- Improvement suggestions based on health score

**For Developers:**
- Device health score calculation based on multiple factors (battery, performance, security, etc.)
- Daily streak tracking and history (stored locally)
- Health trends and statistics
- Achievement system for milestones (`power_achievements.kt`)
- Health score recommendations

**Implementation**: `health_section.kt`, `health_score_utils.kt`, `power_achievements.kt`

### ⚡ Power Consumption Analysis

**For Users:**
- Monitor real-time power consumption (watts)
- See which components (Camera, Display, CPU, Network) use the most power
- Get power recommendations and alerts
- Learn about power consumption through educational content

**For Developers:**
- Component-level power measurement:
  - **Camera**: Per-photo energy measurement with real camera preview (uses BatteryManager API, P = V × I formula)
  - **Display**: Brightness curve analysis
  - **CPU**: Micro-benchmark power profiling
  - **Network**: RSSI vs power correlation
- Real-time power consumption tracking via BatteryManager API
- Power consumption history and aggregated statistics
- Power recommendations and alerts
- Educational content about power consumption
- **CSV export** for research data collection (standardized format)

**Implementation**: `power_consumption_card.kt`, `power_consumption_utils.kt`, `PowerConsumptionAggregator.kt`, `power_recommendations.kt`, `power_alerts.kt`, `power_education.kt`

**Research References**: See [docs/latest_power_consumption_research.md](docs/latest_power_consumption_research.md) for methodology and paper citations.

### 🤖 AI Assistant (Core Feature)

**For Users:**
- Tap "Ask AI" on any device metric to get instant explanations
- Choose Simple or Detailed explanation modes
- Share device data with AI apps for analysis
- Get context-aware recommendations

**For Developers:**
- Tab-specific AI prompts for Device, Network, Health, and Power sections
- Simple and Detailed explanation modes (`PromptMode.Simple`, `PromptMode.Detailed`)
- Context-aware recommendations based on device data
- Share device data with AI apps (ChatGPT, Gemini, Claude, DeepSeek, Perplexity, and more) via robust sharing function
- Item-specific AI analysis for individual device metrics
- Pre-filled smart prompts that guide AI to provide actionable advice

**Implementation**: `ai_assistant_dialog.kt`, `ai_prompt_generator.kt`, `robust_ai_sharing.kt`

### 🏆 Additional Features

- **Leaderboard**: Compete on device health metrics with Gmail account linking (global rankings)
- **Device Certificate**: Generate certified phone reports with resale value estimation via AI (boosts resale value on eBay, Swappa, Marketplace)
- **System Monitoring Service**: Background foreground service for continuous monitoring
- **Automatic Sleep Tracker**: Track device sleep/wake patterns for battery optimization (`DeviceSleepTracker.kt`)
- **AI Compatibility Test**: Check if your phone supports on-device LLMs and AI apps
- **Push Notifications**: OneSignal integration for notifications
- **Analytics**: Firebase Analytics with privacy-respecting implementation
- **Material Design 3**: Modern UI with theme support (light/dark mode)
- **Referral System**: Share and track app referrals
- **In-App Review**: Google Play In-App Review API integration

**Implementation**: `LeaderboardSection.kt`, `LeaderboardManager.kt`, `SystemMonitorService.kt`, `Application.kt`, `referral_manager.kt`, `ReviewPromptManager.kt`, `DeviceSleepTracker.kt`

---

## 📋 Feature → Code Mapping

Quick reference for developers exploring the codebase:

| Feature | Main Implementation Files |
|---------|--------------------------|
| Device Information | `ui/device_info_ui.kt`, `utils/device_utils.kt` |
| Privacy & Security Scanner | `utils/device_utils.kt` (spyware, mic/camera, GPS spoofing detection) |
| Network Monitoring | `ui/network_ui.kt`, `utils/network_utils.kt` |
| Power Consumption | `ui/power_consumption_card.kt`, `utils/power_consumption_utils.kt` |
| Health Scoring | `ui/health_section.kt`, `utils/health_score_utils.kt` |
| **AI Assistant** | `ui/ai_assistant_dialog.kt`, `utils/ai_prompt_generator.kt` |
| Background Monitoring | `services/system_monitor_service.kt` |
| Leaderboard | `ui/LeaderboardSection.kt`, `utils/LeaderboardManager.kt` |
| Device Certificate | `MainActivity.kt` (AI-powered certificate generation) |
| Sleep Tracking | `utils/DeviceSleepTracker.kt` |

---

## 📸 Screenshots

<div align="center">

### Device Information & Performance

<img src="docs/images/Screenshot_20251227-055650.png" alt="Device Info Screen" width="200"/>
<img src="docs/images/Screenshot_20251227-055713.png" alt="Device Specifications" width="200"/>
<img src="docs/images/Screenshot_20251227-055743.png" alt="Performance Metrics" width="200"/>

### Privacy & Security Scanner

<img src="docs/images/Screenshot_20251227-055800.png" alt="Privacy Scanner" width="200"/>
<img src="docs/images/Screenshot_20251227-055808.png" alt="Security Check" width="200"/>

### Network & Health

<img src="docs/images/Screenshot_20251227-055921.png" alt="Network Info" width="200"/>
<img src="docs/images/Screenshot_20251227-055942.png" alt="Health Score" width="200"/>

### Power & AI Assistant

<img src="docs/images/Screenshot_20251227-060001.png" alt="Power Consumption" width="200"/>
<img src="docs/images/Screenshot_20251227-060059.png" alt="AI Assistant" width="200"/>

</div>

---

## 🚀 Quick Start

### Prerequisites

- **Android Studio Iguana (2024.1.1)** or later (required for AGP 8.13.0)
- **JDK 8** or higher
- **Android SDK** (API 24+)
- **Gradle 8.13** (included via wrapper)

### Build and Run

```bash
# Clone the repository
git clone https://github.com/Teamz-Lab-LTD/device-gpt.git
cd device-gpt

# Build debug APK
./gradlew assembleDebug

# Or open in Android Studio and click "Run"
```

The app will run with test AdMob IDs and placeholder configurations. For production features, see [Configuration](#configuration) below.

---

<a name="setup"></a>
## ⚙️ Setup

### Configuration

All sensitive configuration is managed via `local_config.properties` (not committed to git). This keeps the repository open-source friendly while allowing you to use your own credentials.

#### 1. Firebase Configuration

**Required for**: Authentication, Firestore, Analytics, Crashlytics, Remote Config

1. Copy the template:
   ```bash
   cp app/google-services.json.template app/google-services.json
   ```

2. Get your Firebase config from [Firebase Console](https://console.firebase.google.com/)

3. Replace all placeholder values in `app/google-services.json`:
   - `YOUR_PROJECT_NUMBER`
   - `YOUR_PROJECT_ID`
   - `YOUR_MOBILE_SDK_APP_ID`
   - `YOUR_OAUTH_CLIENT_ID`
   - `YOUR_FIREBASE_API_KEY`
   - `YOUR_ADMOB_APP_ID`

#### 2. AdMob Configuration (Optional)

**Required for**: Displaying ads

1. Copy the template:
   ```bash
   cp local_config.template local_config.properties
   ```

2. Add your AdMob IDs to `local_config.properties`:
   ```properties
   ADMOB_APP_ID=ca-app-pub-XXXXXXXXXXXXXXXX~XXXXXXXXXX
   APP_OPEN_AD_UNIT_ID=ca-app-pub-XXXXXXXXXXXXXXXX/XXXXXXXXXX
   INTERSTITIAL_AD_UNIT_ID=ca-app-pub-XXXXXXXXXXXXXXXX/XXXXXXXXXX
   NATIVE_AD_UNIT_ID=ca-app-pub-XXXXXXXXXXXXXXXX/XXXXXXXXXX
   REWARDED_AD_UNIT_ID=ca-app-pub-XXXXXXXXXXXXXXXX/XXXXXXXXXX
   ```

3. The app will automatically use these IDs via `AdConfig.kt`. No manual file editing needed.

**Note**: If `local_config.properties` is missing, the app uses Google test ad IDs (debug builds only).

#### 3. OAuth Client ID Configuration (Optional)

**Required for**: Google Sign-In (leaderboard feature)

Add to `local_config.properties`:
```properties
OAUTH_CLIENT_ID=YOUR_CLIENT_ID.apps.googleusercontent.com
```

The OAuth Client ID is automatically injected into `strings.xml` at build time. No manual editing needed.

#### 4. OneSignal Configuration (Optional)

**Required for**: Push notifications

Add to `local_config.properties`:
```properties
ONESIGNAL_APP_ID=your-onesignal-app-id
```

#### 5. Signing Configuration (Optional - for release builds)

1. Copy the template:
   ```bash
   cp key.properties.template key.properties
   ```

2. Generate a keystore:
   ```bash
   keytool -genkey -v -keystore release-key.jks -keyalg RSA -keysize 2048 -validity 10000 -alias release-key
   ```

3. Update `key.properties` with your credentials:
   ```properties
   storePassword=your_store_password
   keyPassword=your_key_password
   keyAlias=release-key
   storeFile=release-key.jks
   ```

**Note**: For debug builds, this step is optional. The app will use debug signing if `key.properties` is missing.

### Configuration Files Summary

| File | Purpose | Required | Template |
|------|---------|----------|----------|
| `app/google-services.json` | Firebase configuration | Yes | `app/google-services.json.template` |
| `local_config.properties` | AdMob, OAuth, OneSignal IDs | Optional | `local_config.template` |
| `key.properties` | Release signing | Optional (release only) | `key.properties.template` |
| `local.properties` | Android SDK path | Auto-generated | N/A |

**⚠️ Important**: Never commit sensitive files. They are already in `.gitignore`.

---

## 🔧 Troubleshooting

### Common Setup Issues

#### `google-services.json` Missing or Invalid

**Symptoms**: Build fails with "File google-services.json is missing" or Firebase initialization errors.

**Solution**:
1. Ensure `app/google-services.json` exists (copy from template)
2. Verify all placeholder values are replaced with actual Firebase credentials
3. Check that `package_name` in `google-services.json` matches `applicationId` in `app/build.gradle.kts` (should be `com.teamz.lab.debugger`)
4. Sync project: `File → Sync Project with Gradle Files` in Android Studio

#### AdMob IDs Showing Placeholders

**Symptoms**: App shows test ads or "YOUR_ADMOB_APP_ID" in logs.

**Solution**:
1. Create `local_config.properties` from `local_config.template`
2. Add your AdMob App ID and Ad Unit IDs to `local_config.properties`
3. Ensure file is in project root (same level as `build.gradle.kts`)
4. Rebuild project: `./gradlew clean assembleDebug`
5. Verify `AdConfig.kt` reads from `BuildConfig` fields (set at build time)

#### OAuth Client ID Mismatch

**Symptoms**: Google Sign-In fails with "OAuth client ID mismatch" error.

**Solution**:
1. Verify OAuth Client ID in `local_config.properties` matches Firebase Console
2. Ensure format is correct: `YOUR_CLIENT_ID.apps.googleusercontent.com` (include `.apps.googleusercontent.com` suffix)
3. Check that OAuth Client ID in Firebase Console is for package name `com.teamz.lab.debugger`
4. Rebuild project to inject ID into `strings.xml` via `resValue` in `build.gradle.kts`

#### Gradle Sync or Version Issues

**Symptoms**: "Gradle sync failed" or "Unsupported class file major version" errors.

**Solution**:
1. **Android Studio Version**: Ensure Android Studio Iguana (2024.1.1) or later
2. **JDK Version**: Use JDK 8 or higher (check: `File → Project Structure → SDK Location → JDK location`)
3. **Gradle Wrapper**: Use included wrapper: `./gradlew --version` should show Gradle 8.13
4. **Clean Build**: 
   ```bash
   ./gradlew clean
   ./gradlew --stop
   ```
   Then sync again in Android Studio
5. **Invalidate Caches**: `File → Invalidate Caches → Invalidate and Restart`

#### Build Fails with "Cannot find symbol" or Missing Dependencies

**Solution**:
1. Sync Gradle: `File → Sync Project with Gradle Files`
2. Check internet connection (Gradle downloads dependencies)
3. Clear Gradle cache: `rm -rf ~/.gradle/caches/` (macOS/Linux) or `%USERPROFILE%\.gradle\caches\` (Windows)
4. Rebuild: `./gradlew clean build`

---

## 🏗️ Architecture

### High-Level Overview

- **Architecture Pattern**: MVVM (Model-View-ViewModel)
- **UI Framework**: Jetpack Compose with Material Design 3
- **Language**: Kotlin 2.1.0
- **Dependency Injection**: Manual (no Hilt/Koin)

### Key Components

- **MainActivity**: Single-activity app with tab-based navigation
- **ViewModels**: `DeviceInfoViewModel`, `PowerConsumptionViewModel` for state management
- **Background Service**: `SystemMonitorService` (foreground service) for continuous monitoring
- **Data Flow**: 
  - UI → ViewModel → Utils/Services
  - Services → SharedPreferences/Flow → UI
- **Firebase Integration**: Auth, Firestore, Analytics, Crashlytics, Remote Config

### Project Structure

```
device-gpt/
├── app/
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/teamz/lab/debugger/
│   │   │   │   ├── MainActivity.kt          # Main activity
│   │   │   │   ├── Application.kt          # App initialization
│   │   │   │   ├── ui/                      # UI components
│   │   │   │   │   ├── device_info_ui.kt
│   │   │   │   │   ├── network_ui.kt
│   │   │   │   │   ├── health_section.kt
│   │   │   │   │   ├── power_consumption_card.kt
│   │   │   │   │   ├── LeaderboardSection.kt
│   │   │   │   │   ├── ai_assistant_dialog.kt
│   │   │   │   │   └── ...
│   │   │   │   ├── utils/                   # Utility classes
│   │   │   │   │   ├── AdConfig.kt          # Centralized ad config
│   │   │   │   │   ├── power_consumption_utils.kt
│   │   │   │   │   ├── device_utils.kt      # Privacy/security detection
│   │   │   │   │   ├── ai_prompt_generator.kt  # AI prompt generation
│   │   │   │   │   ├── LeaderboardManager.kt
│   │   │   │   │   ├── DeviceSleepTracker.kt
│   │   │   │   │   └── ...
│   │   │   │   ├── services/                # Background services
│   │   │   │   │   └── system_monitor_service.kt
│   │   │   │   ├── widgets/                 # App widgets
│   │   │   │   │   └── LockScreenMonitorWidget.kt
│   │   │   │   └── receivers/               # Broadcast receivers
│   │   │   └── res/                         # Resources
│   │   ├── test/                            # Unit tests (31 files)
│   │   └── androidTest/                     # UI tests (17 files)
│   ├── build.gradle.kts
│   └── google-services.json.template
├── docs/                                    # Documentation
│   ├── images/                              # Screenshots
│   ├── latest_power_consumption_research.md
│   └── Bridging the Gap Between Research Papers and Code.pdf
├── gradle/
├── build.gradle.kts
├── settings.gradle.kts
├── README.md
├── LICENSE
└── CONTRIBUTING.md
```

---

## 🧪 Testing

The project includes comprehensive test coverage:

- **Unit Tests**: 31 test files covering utilities, managers, and core functionality
- **Android Tests**: 17 test files for UI components, user flows, and integration testing

### Running Tests

```bash
# Run all unit tests
./gradlew :app:testDebugUnitTest

# Run all UI tests (requires device/emulator)
./gradlew :app:connectedAndroidTest

# Generate coverage report
./gradlew :app:testDebugUnitTest
./gradlew :app:jacocoTestReport
```

Coverage reports are generated in `app/build/reports/jacoco/jacocoTestReport/html/index.html`

See [TESTING_GUIDE.md](TESTING_GUIDE.md) for detailed testing information.

---

## 🔒 Privacy & Data

### Data Collection

**Local Data (Stored on Device):**
- Device information (CPU, RAM, battery, network stats)
- Health scores and streaks
- Power consumption measurements
- App preferences and settings
- Privacy scan results (mic/camera logs, spyware detection)

**Remote Data (Firebase):**
- **Firebase Analytics**: App usage events, feature interactions (anonymized)
- **Firebase Crashlytics**: Crash reports and stack traces
- **Firebase Firestore**: Leaderboard data (health scores, user IDs)
- **Firebase Auth**: Anonymous authentication and optional Gmail linking
- **Firebase Remote Config**: Feature flags and ad configuration

**OneSignal:**
- Push notification tokens and delivery status

### Privacy Features

- **Works Offline**: Most features work without internet (device info, health scoring, power tracking, privacy scans)
- **No Account Required**: Use the app without creating an account
- **Anonymous Authentication**: Leaderboard uses Firebase anonymous auth by default
- **Optional Gmail Linking**: Users can optionally link Gmail for leaderboard persistence
- **No Data Leaves Device**: Unless you explicitly share it (via AI Assistant or export)
- **Analytics Respects Device Settings**: Analytics are not sent when device is in:
  - Battery Saver Mode
  - Do Not Disturb Mode
  - Airplane Mode
  - Doze Mode (deep sleep)

### How to Disable Analytics

Analytics are automatically disabled in restricted device modes (see above). For complete opt-out:

1. Disable Firebase Analytics in your Firebase project console
2. Or modify `AnalyticsUtils.kt` to always return early in `logEvent()`

**Note**: Analytics help improve the app. Consider keeping them enabled to support development.

---

## 🛠️ Tech Stack

- **Language**: Kotlin 2.1.0
- **UI Framework**: Jetpack Compose (Material 3)
- **Architecture**: MVVM with ViewModel
- **Dependency Injection**: Manual (can be migrated to Hilt/Koin)
- **Backend**: 
  - Firebase (Firestore, Analytics, Crashlytics, Remote Config, Auth)
  - OneSignal (Push Notifications)
- **Ads**: Google AdMob
- **Background Tasks**: WorkManager
- **Authentication**: Firebase Auth + Credential Manager API
- **Testing**: JUnit, Robolectric, Espresso, Compose UI Test
- **Build System**: Gradle 8.13 with Kotlin DSL

---

## 🗺️ Roadmap

Future improvements (based on code TODOs and research docs):

- **Enhanced Power Experiments**: Display brightness curve calibration, CPU micro-benchmark improvements
- **ODPM Integration**: On-Device Power Rails Monitor support where available
- **AI Workload Monitoring**: Power analysis for AI/ML inference tasks
- **Comparative Analysis**: Device-to-device power consumption comparisons
- **Research Data Portal**: Web interface for aggregated power research data

See [docs/latest_power_consumption_research.md](docs/latest_power_consumption_research.md) for detailed research roadmap.

---

## ❓ FAQ

### Does it require root access?

**No.** DeviceGPT works on stock Android devices without root access. All monitoring uses standard Android APIs:
- BatteryManager API for power measurements
- ActivityManager for RAM/CPU info
- Network APIs for network testing
- System APIs for device information

Some advanced features (like detailed CPU frequency monitoring) may have limited data on non-root devices, but core functionality works without root.

### Does it work offline?

**Yes, most features work offline:**
- ✅ Device information (CPU, RAM, battery, storage)
- ✅ Health scoring and history
- ✅ Power consumption tracking
- ✅ Privacy scans (mic/camera detection, spyware scanner)
- ✅ Local data viewing

Features that require internet:
- ❌ Network speed testing (download/upload)
- ❌ Network latency measurement
- ❌ Leaderboard sync
- ❌ AI Assistant sharing (needs internet to share with AI apps)
- ❌ Firebase Analytics/Crashlytics

### Can I export data (CSV)?

**Yes.** Power consumption experiments support CSV export:
- Camera power experiments
- CPU micro-benchmark tests
- App power consumption data
- Network RSSI vs power correlation

CSV files are exported via `PowerConsumptionUtils.exportExperimentCSV()` and can be shared through Android's share dialog. See `power_consumption_card.kt` for export UI implementation.

### What device versions are supported?

**Android 7.0 (API 24) and higher.**
- **Minimum SDK**: API 24 (Android 7.0 Nougat)
- **Target SDK**: API 36 (Android 15)
- **Compile SDK**: API 36

Some features have additional requirements:
- Lock screen widget: Android 13+ (API 33+)
- Notification permission: Android 13+ (API 33+)
- Background location: Android 10+ (API 29+) for some network features

### How is power measured?

Power measurement uses the **BatteryManager API** with the physics formula **P = V × I**:
- **Voltage (V)**: From `BatteryManager.EXTRA_VOLTAGE` (real millivolts)
- **Current (I)**: From `BatteryManager.BATTERY_PROPERTY_CURRENT_NOW` (real microamps)

**Methodology** (from `power_consumption_utils.kt`):
- Real system data only (no estimates or simulations)
- Component-level measurements: Camera, Display, CPU, Network
- If real data unavailable, returns 0.0 (no fallback estimates)
- Uses baseline → workload → post-workload delta measurements

For detailed methodology and research references, see [docs/latest_power_consumption_research.md](docs/latest_power_consumption_research.md).

---

## 🤝 Contributing

We welcome contributions! Please see [CONTRIBUTING.md](CONTRIBUTING.md) for detailed guidelines.

### Quick Start for Contributors

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Make your changes
4. Write/update tests (unit tests for utils, UI tests for composables)
5. Ensure all tests pass: `./gradlew :app:testDebugUnitTest`
6. Commit your changes (`git commit -m 'Add amazing feature'`)
7. Push to the branch (`git push origin feature/amazing-feature`)
8. Open a Pull Request

### Code Style

- Follow Kotlin coding conventions
- Use meaningful variable and function names
- Add KDoc comments for public APIs
- Write tests for new features

---

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

---

## 🙏 Acknowledgments

- Research papers and methodologies that inspired the power consumption features (see [docs/latest_power_consumption_research.md](docs/latest_power_consumption_research.md))
- Android community for excellent tools and libraries
- All contributors who help improve this project

---

## 📞 Support

**App Support:**
- 📱 [Google Play Store](https://play.google.com/store/apps/details?id=com.teamz.lab.debugger) - Rate, review, and get app updates
- 📧 **Email**: hello@teamzlab.com
- 📞 **Phone**: +44 7365 602184

**Development Support:**
- **Issues**: [GitHub Issues](https://github.com/Teamz-Lab-LTD/device-gpt/issues)
- **Discussions**: [GitHub Discussions](https://github.com/Teamz-Lab-LTD/device-gpt/discussions)
- **Community**: [XDA Forums](https://xdaforums.com/t/app-devicegpt-ai-powered-android-monitor-real-data-privacy-guardian-global-leaderboard.4773593/)

---

## ⭐ Show Your Support

If you find this project useful:

1. **⭐ Star this repository** on GitHub
2. **📱 [Download DeviceGPT](https://play.google.com/store/apps/details?id=com.teamz.lab.debugger)** on Google Play
3. **⭐ Rate and review** on Google Play Store
4. **🚀 [Upvote on Product Hunt](https://www.producthunt.com/p/devicegpt-ai-phone-health)**
5. **💬 [Join the discussion](https://xdaforums.com/t/app-devicegpt-ai-powered-android-monitor-real-data-guardian-global-leaderboard.4773593/)** on XDA Forums

Your support helps us continue building great open-source tools!

---

<a name="work-with-teamz-lab"></a>
## 🏢 Work with Teamz Lab — Let's Build Your App

<div align="center">

### 💼 **Looking for a Mobile App Development Partner?**

**DeviceGPT is proof of what we can build for you.**

[![Download on Google Play](https://img.shields.io/badge/See%20Our%20Work-Google%20Play-4285F4?style=for-the-badge&logo=google-play&logoColor=white)](https://play.google.com/store/apps/details?id=com.teamz.lab.debugger)

**500+ Downloads • Production-Ready • Open-Source Quality**

[📧 Get Free Consultation](mailto:hello@teamzlab.com?subject=Mobile%20App%20Development%20Inquiry&body=Hi%20Teamz%20Lab%2C%20I%20saw%20DeviceGPT%20and%20I%27d%20like%20to%20discuss%20my%20project.) • [💼 View Upwork Profile](https://www.upwork.com/agencies/1904602719490921565/) • [🌐 Visit Website](https://teamzlab.com/)

</div>

---

### 🎯 **Why Choose Teamz Lab? (Real Results, Not Promises)**

**DeviceGPT is a live example** of our capabilities. This isn't a portfolio piece — it's a **production app** with real users, real downloads, and real code you can inspect right now.

#### ✅ **What You Get When You Work With Us:**

1. **🚀 Faster Time-to-Market**
   - AI-powered development workflows (see DeviceGPT's AI integration)
   - Proven architecture patterns (MVVM, clean code, modular design)
   - **Result**: Your app ships faster without sacrificing quality

2. **🎨 Design That Converts**
   - Material Design 3 implementation (see DeviceGPT's modern UI)
   - User experience optimized for engagement
   - **Result**: Apps that users love and keep using

3. **🔒 Production-Ready Code**
   - 31 unit tests + 17 UI tests (comprehensive coverage)
   - Open-source friendly architecture (you own everything)
   - **Result**: Maintainable, scalable code that grows with your business

4. **🤖 AI Integration Expertise**
   - ChatGPT, Gemini, Claude integration (see DeviceGPT's AI Assistant)
   - AI-powered features that add real value
   - **Result**: Your app stands out with cutting-edge AI capabilities

5. **📱 Full-Stack Mobile Development**
   - Native Android (Kotlin, Jetpack Compose)
   - Native iOS (Swift, SwiftUI)
   - Cross-platform (Flutter, React Native)
   - **Result**: One team, all platforms, consistent quality

6. **🔬 Research-Backed Implementation**
   - Power consumption research integration (see DeviceGPT's research docs)
   - Latest Android best practices
   - **Result**: Your app uses cutting-edge technology, not outdated patterns

7. **🌍 Remote-First, Always-On**
   - Global talent pool
   - Seamless collaboration across time zones
   - **Result**: Faster development cycles, lower costs, better outcomes

8. **✅ Proven Track Record**
   - Multiple apps on Google Play & App Store
   - 500+ downloads on DeviceGPT (and growing)
   - **Result**: We deliver apps that users actually download and use

---

### 💡 **Perfect For:**

- **Startups** launching their first mobile app
- **Businesses** needing to modernize existing apps
- **Entrepreneurs** with an app idea but no technical team
- **Companies** wanting to add AI features to existing apps
- **Developers** needing expert help with complex features
- **Anyone** who wants production-quality code (like DeviceGPT)

---

### 🎁 **What's Included in Every Project:**

✅ **Complete Development** — From concept to App Store/Play Store  
✅ **UI/UX Design** — Modern, conversion-optimized interfaces  
✅ **Quality Assurance** — Comprehensive testing (unit + UI tests)  
✅ **AI Integration** — ChatGPT, Gemini, Claude, or custom AI features  
✅ **Documentation** — Clean code, README, setup guides  
✅ **Deployment** — App Store & Play Store submission support  
✅ **Post-Launch Support** — Bug fixes, updates, maintenance  
✅ **Source Code Ownership** — You own 100% of the code

### 📊 **Our Credentials & Social Proof**

<div align="center">

| Platform | Link | What It Shows |
|----------|------|---------------|
| **🌐 Website** | [teamzlab.com](https://teamzlab.com/) | Our services, portfolio, case studies |
| **💼 Upwork** | [Upwork Agency Profile](https://www.upwork.com/agencies/1904602719490921565/) | Client reviews, ratings, completed projects |
| **📱 Play Store** | [Google Play Portfolio](https://play.google.com/store/apps/dev?id=7194763656319643086) | Published apps, user ratings |
| **🍎 App Store** | [Apple App Store Portfolio](https://apps.apple.com/us/developer/teamz-lab-ltd/id1785282466) | iOS apps, App Store presence |
| **⭐ Clutch** | [Clutch Profile](https://clutch.co/profile/teamz-lab) | Client reviews, verified ratings |
| **⭐ Trustpilot** | [Trustpilot Reviews](https://uk.trustpilot.com/review/teamzlab.com) | Customer satisfaction scores |
| **💼 LinkedIn** | [LinkedIn Company](https://www.linkedin.com/company/teamzlab/posts/?feedView=all) | Team updates, industry insights |
| **🐦 Twitter/X** | [@teamzlabapp](https://x.com/teamzlabapp) | Latest updates, tech insights |
| **📸 Instagram** | [@teamzlab](https://www.instagram.com/teamzlab/) | Behind-the-scenes, team culture |
| **📺 YouTube** | [YouTube Channel](https://www.youtube.com/@teamzlab) | Tutorials, demos, case studies |

</div>

### 🚀 **See Our Work in Action**

**DeviceGPT: AI Phone Health** — This entire repository is our work:

- 📱 **[Download on Google Play](https://play.google.com/store/apps/details?id=com.teamz.lab.debugger)** — See the live app (500+ downloads)
- 🚀 **[Upvote on Product Hunt](https://www.producthunt.com/p/devicegpt-ai-phone-health)** — See community validation
- 💬 **[XDA Forums Discussion](https://xdaforums.com/t/app-devicegpt-ai-powered-android-monitor-real-data-privacy-guardian-global-leaderboard.4773593/)** — See developer feedback
- 📂 **This Repository** — Inspect our code quality, architecture, and documentation

**This is what you get** — production-ready code, comprehensive documentation, and apps that users actually want to download.

---

### 💬 **Let's Talk About Your Project**

<div align="center">

### **Ready to Build Your App?**

**Get a free consultation. No commitment, just a conversation about your project.**

[📧 **Email Us**](mailto:hello@teamzlab.com?subject=Mobile%20App%20Development%20Inquiry&body=Hi%20Teamz%20Lab%2C%20I%20saw%20DeviceGPT%20on%20GitHub%20and%20I%27d%20like%20to%20discuss%20my%20mobile%20app%20project.%0A%0AProject%20Details%3A%0A-%20App%20Type%3A%0A-%20Platform%28s%29%3A%0A-%20Timeline%3A%0A-%20Budget%20Range%3A%0A%0ALooking%20forward%20to%20hearing%20from%20you%21) • [📞 **Call Us**](tel:+447365602184) • [💼 **Upwork**](https://www.upwork.com/agencies/1904602719490921565/)

**📧 hello@teamzlab.com** • **📞 +44 7365 602184** • **🌐 [teamzlab.com](https://teamzlab.com/)**

**Response Time: Within 24 hours (usually same day)**

</div>

---

### 🎯 **What Happens Next?**

1. **You Contact Us** — Email, call, or message on Upwork
2. **Free Consultation** — We discuss your project, goals, and requirements
3. **Proposal & Quote** — Transparent pricing, clear timeline, detailed scope
4. **We Build Your App** — Regular updates, milestone reviews, quality checks
5. **You Launch** — App Store/Play Store submission, marketing support
6. **Ongoing Support** — Updates, maintenance, feature additions

**Simple. Transparent. Results-Driven.**

---

### 💰 **Investment & Value**

**We don't just write code — we build businesses.**

- **Transparent Pricing** — No hidden fees, clear project scope
- **Flexible Engagement** — Fixed-price projects or hourly rates
- **Value-Focused** — We optimize for ROI, not just features
- **Ownership** — You own 100% of the code and IP

**Interested? Let's discuss your project and see if we're a good fit.**

---

<div align="center">

### **🚀 Ready to Build Your App?**

**DeviceGPT proves we can deliver. Let's prove it for your project too.**

[📧 **Get Free Consultation →**](mailto:hello@teamzlab.com?subject=Mobile%20App%20Development%20Inquiry&body=Hi%20Teamz%20Lab%2C%20I%20saw%20DeviceGPT%20on%20GitHub%20and%20I%27d%20like%20to%20discuss%20my%20mobile%20app%20project.)

**Response within 24 hours • Free consultation • No commitment**

</div>

---

<div align="center">

**Made with ❤️ by [Teamz Lab](https://teamzlab.com/)**

[⬆ Back to Top](#devicegpt-ai-phone-health-)

</div>

---

## 📝 Credibility & Accuracy

We strive for accuracy in this README. If you find any mismatch between the documentation and the actual codebase:

1. **Open an issue** with:
   - File path(s) where you found the discrepancy
   - Screenshot or code snippet showing the actual behavior
   - Expected behavior based on README

2. **Submit a PR** if you can fix it:
   - Update the README with accurate information
   - Reference the code files that verify your changes
   - Follow the [Contributing guidelines](#-contributing)

We welcome contributions that improve documentation accuracy and developer experience.

---
