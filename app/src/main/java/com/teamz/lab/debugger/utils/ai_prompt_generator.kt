package com.teamz.lab.debugger.utils

import com.teamz.lab.debugger.ui.PromptMode

/**
 * Generates AI prompts for different tabs and modes
 * This centralizes all prompt generation logic for better maintainability
 */
object AIPromptGenerator {
    
    /**
     * Generate prompt for main tabs (Device, Network, Health, Power)
     * Uses TabOrderManager to determine tab type from index
     */
    fun generateMainPrompt(
        tabIndex: Int,
        promptMode: PromptMode,
        appName: String
    ): String {
        val tabType = TabOrderManager.getTabTypeAt(tabIndex)
        return when (tabType) {
            TabType.DEVICE_INFO -> generateDevicePrompt(promptMode, appName)
            TabType.NETWORK_INFO -> generateNetworkPrompt(promptMode, appName)
            TabType.HEALTH -> generateHealthPrompt(promptMode, appName)
            TabType.POWER -> generatePowerPrompt(promptMode, appName)
            TabType.LEADERBOARD -> "" // Leaderboard doesn't have AI prompts
            null -> ""
        }
    }
    
    /**
     * Generate device info prompt
     */
    private fun generateDevicePrompt(promptMode: PromptMode, appName: String): String {
        return if (promptMode == PromptMode.Simple) {
            """
        Hi! I'm using **$appName** to check if my phone is working well. I'm not very technical, so please explain things simply!

        📱 **What $appName Found:**
        (The detailed technical data is below - but here's what I understand so far)

        🧠 **Phone Performance:** Running normally (like a car engine - seems fine)  
        🔋 **Battery:** Health is decent, but phone feels a bit warm (like when you use it a lot)  
        💾 **Storage:** Almost full (like a closet that's getting crowded)  
        👁️ **Camera/Mic:** Used recently (apps accessed these features)  
        👣 **Motion:** Phone moved while locked (maybe I bumped it, or someone touched it?)  
        🔐 **Security:** No major risks found (seems safe)

        **What I Need Help With:**
        • Can you explain what each of these means in simple, everyday language?  
        • Is my phone healthy overall, or should I be worried?  
        • What are 1-2 easy things I can do right now to make it better?  
        • Should I delete apps or photos to free up space?  
        • Is the warm battery normal or a problem?

        **Please talk to me like you're explaining to a friend who doesn't know much about phones!** 😊

        Made with **$appName** - my phone health checker 📱
        """.trimIndent()
        } else {
            """
        Hi! I'm using **$appName** to scan my phone and understand what's going on. I want to learn more, but please keep explanations simple and relatable!

        📱 **About My Phone:**
        $appName collected real data from my Android phone. All the technical details are below, but I need help understanding what they mean in everyday terms.

        🔍 **What I'd Like to Understand (In Simple Terms):**

        **Performance Questions:**
        • Is my phone running slow or performing well? (Like: Is it a fast car or slow car?)  
        • Should I be worried about anything, or is everything normal?  
        • What does "CPU" and "RAM" mean in simple terms? (I know they're important but not sure why)

        **Battery Questions:**
        • Is my battery healthy, or is it draining too fast?  
        • Is anything overheating or using too much power? (Like: Is my phone working too hard?)  
        • What's a normal battery temperature vs. too hot?

        **Storage Questions:**
        • Do I need to clean up storage? How urgent is it?  
        • What's the difference between "used space" and "available space"?  
        • Should I delete photos, apps, or something else first?

        **Privacy & Security Questions:**
        • Is there any privacy or security risk I should know about?  
        • Are there apps using my mic/camera without me knowing? (This worries me!)  
        • What does "root" or "developer mode" mean, and should I be concerned?

        **Other Questions:**
        • Can I run on-device AI models like ChatGPT on this phone?  
        • Did my phone move when it was locked? (Could someone have touched it?)  
        • What are "frame drops" and why do they matter?

        📊 **What $appName Measured (Technical Data Below):**
        - **CPU** (processor - like the phone's brain speed)  
        - **RAM** (memory - like how many things the phone can think about at once)  
        - **Battery** (health, temperature, charging status)  
        - **Storage** (how much space is used vs. available)  
        - **Frame drops** (how smooth videos/games run)  
        - **Motion sensors** (if phone moved while locked)  
        - **Root/Developer mode** (advanced settings status)  
        - **Mic/Camera logs** (which apps used these features)  
        - **AI support test** (can phone run AI apps)  
        - **App tracking checks** (privacy monitoring)

        💡 **Please Explain:**
        • Use simple analogies (like comparing to a car, house, or everyday things)  
        • Tell me what's normal vs. what I should worry about  
        • Give me specific, actionable steps I can do right now  
        • Explain any technical terms in plain English

        ✅ **Quick Tips I'd Love:**
        • How to clear app cache (I've heard this helps but don't know how)  
        • Which apps to uninstall first if I need space  
        • How to turn off background sync (and what that even means)  
        • How to reduce screen brightness or use dark mode  
        • How to lock apps with sensitive permissions

        **Remember:** I'm not technical, so please explain everything like you're talking to a friend! 😊

        Generated by **$appName** - your daily Android health & privacy guide 📱🧠
        """.trimIndent()
        }
    }
    
    /**
     * Generate network info prompt
     */
    private fun generateNetworkPrompt(promptMode: PromptMode, appName: String): String {
        return if (promptMode == PromptMode.Simple) {
            """
        Hi! I'm using **$appName** to check if my internet is working properly. Please explain things simply - I'm not very technical!

        📱 **What $appName Found:**
        (The detailed technical data is below - but here's what I understand so far)

        📶 **Connection Type:** Wi-Fi or mobile data (like: am I using home internet or phone data?)  
        ⚡ **Speed:** Looks normal, but might not be consistent (like: sometimes fast, sometimes slow?)  
        🌀 **Stability:** Some jitter or small delay detected (like: videos might pause or lag?)  
        🔍 **Privacy:** No major risks seen (but please double-check!)

        **What I Need Help With:**
        • Can you explain what "download speed" and "upload speed" mean in simple terms?  
        • Is my internet fast enough for watching videos, video calls, or gaming?  
        • What does "latency" or "ping" mean? (I hear gamers talk about this)  
        • Is there anything slowing down my internet that I can fix?  
        • Should I be worried about privacy or security with my connection?  
        • What are 1-2 easy things I can do to make my internet faster or more stable?

        **Please explain like you're helping a friend who doesn't know much about internet stuff!** 😊

        Made with **$appName** - my daily internet health checker 🌐📱
        """.trimIndent()
        } else {
            """
        Hi, I'm using an app called **$appName** to check my phone's internet performance and privacy.

        Can you review this full network report and help me understand:

        🔍 What I'd like to know:
        • Is my internet fast, or is there something slowing it down?
        • Are there signs of ISP throttling, packet loss, or unstable ping?
        • Is my connection secure from spying or fake DNS?
        • Can I improve my mobile data or Wi-Fi experience?
        • Any privacy settings I should fix?

        📊 This includes:
        - Speed test: Download, upload, latency, jitter
        - Packet loss and connection stability
        - Public/local IP, DNS, gateway
        - Surveillance & spoofing detection
        - Streaming CDN presence (YouTube, Netflix, etc.)
        - MTU size, captive portal, usage stats

        ✅ Pro Tips (if possible):
        • Try changing your DNS to Cloudflare (1.1.1.1) for better speed & privacy  
        • Avoid Wi-Fi networks with login pages in public areas  
        • Restart your router or switch bands if you notice lag  
        • Enable Data Saver in Android settings to reduce background usage  

        Report from **$appName** — your internet & privacy guard 📡🔐
        """.trimIndent()
        }
    }
    
    /**
     * Generate health report prompt
     */
    private fun generateHealthPrompt(promptMode: PromptMode, appName: String): String {
        return if (promptMode == PromptMode.Simple) {
            """
I'm using **$appName** to check my phone's overall health. Please analyze this health report and help me understand what it means in simple, everyday language.

📱 **My Health Report:**
(The detailed data is below)

**Please provide a clear, structured response with:**

1. **Quick Summary** (2-3 sentences)
   - Is my phone healthy, okay, or needs attention?
   - Overall condition assessment (good/bad/average)

2. **What My Health Score Means** (explain simply)
   - What is a health score? (like a grade for my phone)
   - Is my score good or bad? (what's normal?)
   - What does this number tell me about my phone?

3. **What I Should Know**
   - Are there any concerning issues? (battery, storage, performance, etc.)
   - What's working well?
   - What needs improvement?

4. **Actionable Tips** (1-2 specific things I can do)
   - Easy steps to improve my phone's health
   - What should I do first?
   - How often should I check my phone's health?

**Important:** Use simple analogies and avoid technical jargon. Explain everything as if talking to someone who doesn't know much about phones.

Generated by **$appName** 📱
        """.trimIndent()
        } else {
            """
You are a **Phone Health Expert**. Analyze this health report from **$appName** and provide a comprehensive, structured assessment that balances technical accuracy with practical insights.

📱 **Health Report Data:**
(The detailed technical data is below)

**Please provide a detailed analysis with the following structure:**

1. **Health Score Assessment** (Current Status)
   - Score interpretation: What does the number mean?
   - Overall condition: Excellent, Good, Fair, Poor, or Critical
   - Comparison to history: Is it improving, stable, or declining?
   - Key factors affecting the score

2. **Component Analysis** (What's Working & What's Not)
   - Battery health: Status, capacity, charging behavior
   - Storage: Available space, usage patterns, recommendations
   - Performance: Speed, responsiveness, app behavior
   - Security: Updates, vulnerabilities, protection status
   - Other critical components

3. **Trend Analysis** (Historical Context)
   - Daily streak: What does this indicate?
   - Score history: Patterns, improvements, or declines
   - Best score: How does current compare?
   - Predictive insights: What to expect if trends continue

4. **Improvement Recommendations** (Prioritized Action Plan)
   - **Urgent** (if any): Issues requiring immediate attention
   - **High Priority**: Quick wins that will improve score significantly
   - **Medium Priority**: Maintenance tasks for long-term health
   - **Low Priority**: Optimizations for best performance

5. **Maintenance Schedule** (Ongoing Care)
   - How often to scan/check health
   - When to perform maintenance tasks
   - Signs to watch for that indicate problems
   - Preventive measures to maintain good health

6. **Comparison & Context** (Market Standards)
   - How does this device compare to similar devices?
   - Is this score typical for this device age/model?
   - What's the expected lifespan based on current health?

**Format:** Use clear headings, bullet points, and specific numbers/metrics where relevant. Balance technical depth with practical, actionable insights.

Generated by **$appName** 📱🏥
        """.trimIndent()
        }
    }
    
    /**
     * Generate power consumption prompt
     */
    private fun generatePowerPrompt(promptMode: PromptMode, appName: String): String {
        return if (promptMode == PromptMode.Simple) {
            """
I'm using **$appName** to check how much battery my phone uses. Please analyze this power consumption data and help me understand what it means in simple, everyday language.

📱 **My Power Consumption Data:**
(The detailed technical data is below)

**Please provide a clear, structured response with:**

1. **Quick Summary** (2-3 sentences)
   - Is my phone using battery normally, or too much?
   - Overall power usage assessment (good/bad/average)

2. **What the Numbers Mean** (explain simply)
   - What is "Watts"? (like how much electricity my phone uses)
   - Is my power usage high or low? (what's normal?)
   - Which component uses the most battery? (screen, apps, Wi-Fi, etc.)

3. **What I Should Know**
   - Is my power usage concerning? (too high, draining battery fast, etc.)
   - What's using the most battery and why?
   - What does "power trend" mean? (going up, down, or staying same?)

4. **Actionable Tips** (1-2 specific things I can do right now)
   - Easy steps to save battery immediately
   - What should I turn off or change?
   - Should I close apps, reduce brightness, or something else?

**Important:** Use simple analogies (like comparing to a car's fuel consumption) and avoid technical jargon. Explain everything as if talking to someone who doesn't know much about phones.

Generated by **$appName** 🔋
        """.trimIndent()
        } else {
            """
You are a **Power Consumption & Battery Life Expert**. Analyze this power consumption data from **$appName** and provide a comprehensive, structured assessment that balances technical accuracy with practical insights.

📱 **Power Consumption Data:**
(The detailed technical data is below)

**Please provide a detailed analysis with the following structure:**

1. **Power Usage Assessment** (Current Status)
   - Total power consumption: Is it normal, high, or low?
   - Power level classification: Low (<2W), Medium (2-5W), High (5-8W), Very High (>8W)
   - Battery drain rate: How fast is battery draining? (percentage per hour)
   - Comparison to typical usage: How does this compare to average phones?

2. **Component Breakdown Analysis** (What's Draining Battery)
   - **Top power consumers**: Which components use the most power and why
   - **Component efficiency**: Which components are using more power than expected?
   - **Component status**: Active, idle, or problematic states
   - **Power distribution**: Percentage breakdown of total power usage

3. **Power Statistics Analysis** (Trends & Patterns)
   - **Average power**: Typical usage patterns and what this means
   - **Peak power**: Highest usage moments and what caused them
   - **Minimum power**: Lowest usage and what this indicates
   - **Power trend**: Increasing, decreasing, or stable - and what this means
   - **Sample count**: Data reliability and confidence level

4. **Battery Life Implications** (Real-World Impact)
   - **Expected battery life**: How long will battery last at current usage?
   - **Screen-on time estimate**: How many hours of active use?
   - **Standby time estimate**: How long will battery last when idle?
   - **Battery health impact**: Is current usage affecting battery longevity?

5. **Optimization Recommendations** (Prioritized Action Plan)
   - **Immediate actions** (high impact, easy to do):
     * Turn off unused features (Bluetooth, GPS, Wi-Fi when not needed)
     * Reduce screen brightness or enable dark mode
     * Close power-hungry background apps
   - **Settings changes** (medium impact):
     * Enable battery saver mode (when appropriate)
     * Adjust app power restrictions
     * Optimize display settings
   - **Long-term optimizations** (maintenance):
     * Identify and limit consistently power-hungry apps
     * Review and optimize app permissions
     * System-level power optimizations

6. **Component-Specific Guidance** (Detailed Recommendations)
   - **Display**: Brightness impact, dark mode benefits, refresh rate considerations
   - **CPU**: App management, background processes, performance vs. battery trade-offs
   - **Network**: Wi-Fi vs. mobile data efficiency, signal strength impact
   - **Camera**: Power usage patterns, when to close camera apps
   - **Other components**: Specific guidance for each active component

7. **Warning Signs & Troubleshooting** (When to Worry)
   - **Red flags**: Unusual power spikes, components using excessive power
   - **Problem indicators**: Power trend increasing unexpectedly, battery draining too fast
   - **Troubleshooting steps**: What to check if power usage seems abnormal
   - **When to seek help**: Signs that indicate hardware or software issues

**Format:** Use clear headings, bullet points, and specific numbers/metrics where relevant. Balance technical depth with practical, actionable insights. Use analogies where helpful (e.g., comparing to car fuel consumption).

Generated by **$appName** ⚡🔋
        """.trimIndent()
        }
    }
    
    /**
     * Generate certificate prompt for device certification
     */
    fun generateCertificatePrompt(
        appName: String,
        inputPrice: String,
        inputCurrency: String,
        scanDate: String
    ): String {
        // Calculate valid until date (30 days from scan date)
        val validUntil = try {
            val datePart = scanDate.split(" ")[0]
            val parsedDate = java.time.LocalDate.parse(datePart)
            parsedDate.plusDays(30).format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd"))
        } catch (e: Exception) {
            // Fallback if date parsing fails
            java.time.LocalDate.now().plusDays(30).format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd"))
        }
        
        return """
Hi! I'm using **$appName** (https://play.google.com/store/apps/details?id=com.teamz.lab.debugger) to certify my device for resale, health check, and value estimation.

📱 Original Price: $inputPrice $inputCurrency  
🗓️ Scan Date: $scanDate  

Please help me with the following:

---

🔍 **Device Analysis**
- Analyze the full device information and health (see scan data below)
- Support all types of devices: iPhones, Android phones, tablets, foldables, etc.
- Use model number, brand, storage size, RAM, OS version, battery info, and manufacture/launch date

---

💰 **Resale Price Estimation**
- Estimate a fair resale price in my local currency
- Use real-world data from platforms like Swappa, SellCell, BackMarket, OLX, and Facebook Marketplace (2024–2025)
- Factor in:
  - Device brand & launch year
  - Battery health and charging cycles
  - Cosmetic condition (if available)
  - RAM/storage size
  - Common resale trends

- Use dynamic depreciation logic:
  - **Apple (iPhone)**:  
    • 0–1 yr: 80–85%, 1–2 yr: 65–75%, 2–3 yr: 50–60%, 3+ yr: 35–45%
  - **Samsung Flagships**:  
    • 0–1 yr: 70–80%, 1–2 yr: 50–60%, 2–3 yr: 35–50%, 3+ yr: 20–35%
  - **Xiaomi / Oppo / Vivo / Realme**:  
    • 0–1 yr: 60–70%, 1–2 yr: 40–55%, 2–3 yr: 25–40%, 3+ yr: 10–25%
  - **Google Pixel / OnePlus**:  
    • 0–1 yr: 70–80%, 1–2 yr: 55–65%, 2–3 yr: 35–50%, 3+ yr: 20–35%
  - **Chinese Android Brands** (e.g. Xiaomi, Realme, Vivo, Oppo, Huawei, Honor, Infinix, Tecno, Lenovo, ZTE, Nubia, Meizu, etc.):  
    • 0–1 yr: 60–70%, 1–2 yr: 40–55%, 2–3 yr: 25–40%, 3+ yr: 10–25%
(Important: If the brand is not listed, infer depreciation logic based on spec tier and market trend from similar Chinese OEMs)
(Important: If any of these percentages are outdated or inaccurate, please use the most realistic current market data to adjust them in the certificate. Use your best judgment based on real-world resale pricing trends.)

---

📊 **Condition Rating**
- Provide a clear **device score out of 10**
- Explain what the score means:
  - 9–10: Excellent (like new)
  - 7–8: Good
  - 5–6: Fair
  - Below 5: Needs repair or not ideal for resale

---

📄 **Certificate Generation (Important!)**
✅ Your **first response must be a certificate layout** — do not describe, explain, or summarize anything before the certificate.

- Generate a clean, professional, and trustworthy **resale certificate**
- Preferably in **PDF** or **image format**
  - If that's not possible, output a text layout that mimics a real certificate for rendering or screenshotting

The certificate must:
- Visually **look like an official certificate** (with border, title, structured info, layout blocks, sections, seal/badge/watermark)
- Include:
  - Device name, brand, model, specs, OS version
  - Battery health and storage
  - Estimated resale value
  - Condition rating
  - A **Certificate ID** (e.g. CERT-XYZ-1234)
  - A footer message:
    > _Certified with $appName – check your phone's value and health for free using $appName (https://play.google.com/store/apps/details?id=com.teamz.lab.debugger)_

- Also include:
  - "Valid until: $validUntil"
  - "Scan again next month to track resale trends."

---

🔁 Ensure your output:
- Starts **immediately with the certificate**
- Is clear, professional, and trustworthy
- Is easy to copy/share with potential buyers or friends

---

[Insert scan data here]

Thank you! I want to share this certificate with others so they can try $appName too 🚀
""".trimIndent()
    }
    
    /**
     * Detect category from item title
     */
    fun detectItemCategory(itemTitle: String): String {
        return when {
            // Network Privacy Report - must check before generic network/privacy matches
            itemTitle.contains("Privacy Report", ignoreCase = true) ||
            itemTitle.contains("Privacy Report Card", ignoreCase = true) -> "privacy_report"
            // Network Reachability - must check before generic network
            itemTitle.contains("Reachability", ignoreCase = true) ||
            itemTitle.contains("Network Reachability", ignoreCase = true) -> "reachability"
            // Zero Trust / Security Dashboard - must check before generic security
            itemTitle.contains("Zero Trust", ignoreCase = true) ||
            itemTitle.contains("Security Dashboard", ignoreCase = true) ||
            itemTitle.contains("Trust Score", ignoreCase = true) -> "zero_trust"
            // Power-related items - must check first before generic category checks
            itemTitle.contains("Power", ignoreCase = true) || 
            itemTitle.contains("Battery", ignoreCase = true) && itemTitle.contains("Usage", ignoreCase = true) ||
            itemTitle.contains("Camera Power", ignoreCase = true) ||
            itemTitle.contains("Display Power", ignoreCase = true) ||
            itemTitle.contains("CPU.*Power", ignoreCase = true) ||
            itemTitle.contains("Network.*Power", ignoreCase = true) ||
            itemTitle.contains("App Power", ignoreCase = true) ||
            itemTitle.contains("Component Breakdown", ignoreCase = true) ||
            itemTitle.contains("Power Statistics", ignoreCase = true) ||
            itemTitle.contains("Total Power", ignoreCase = true) ||
            itemTitle.contains("Device Sleep", ignoreCase = true) ||
            itemTitle.contains("Sleep Tracker", ignoreCase = true) ||
            itemTitle.contains("Network RSSI", ignoreCase = true) -> "power"
            // Device specs - must check first before generic "Device" check
            itemTitle.contains("Specification", ignoreCase = true) -> "device_specs"
            // CPU/Processor
            itemTitle.contains("CPU", ignoreCase = true) || itemTitle.contains("Processor", ignoreCase = true) -> "cpu"
            // GPU/Graphics
            itemTitle.contains("GPU", ignoreCase = true) || itemTitle.contains("Graphics", ignoreCase = true) -> "gpu"
            // Battery
            itemTitle.contains("Battery", ignoreCase = true) || itemTitle.contains("Charging", ignoreCase = true) -> "battery"
            // Storage/Memory
            itemTitle.contains("Memory", ignoreCase = true) || itemTitle.contains("Storage", ignoreCase = true) -> "storage"
            // Display/Screen
            itemTitle.contains("Display", ignoreCase = true) || itemTitle.contains("Screen", ignoreCase = true) -> "display"
            // Camera/Mic/Speaker
            itemTitle.contains("Camera", ignoreCase = true) || itemTitle.contains("Mic", ignoreCase = true) || 
            itemTitle.contains("Speaker", ignoreCase = true) || itemTitle.contains("Flashlight", ignoreCase = true) -> "camera"
            // GPS/Location
            itemTitle.contains("GPS", ignoreCase = true) || itemTitle.contains("Location", ignoreCase = true) || 
            itemTitle.contains("Navigation", ignoreCase = true) -> "location"
            // Network/SIM/Internet/Wi-Fi/DNS/IP
            itemTitle.contains("Network", ignoreCase = true) || itemTitle.contains("SIM", ignoreCase = true) || 
            itemTitle.contains("Mobile", ignoreCase = true) || itemTitle.contains("Internet", ignoreCase = true) ||
            itemTitle.contains("Wi-Fi", ignoreCase = true) || itemTitle.contains("Wifi", ignoreCase = true) ||
            itemTitle.contains("DNS", ignoreCase = true) || itemTitle.contains("IP Address", ignoreCase = true) ||
            itemTitle.contains("ISP", ignoreCase = true) || itemTitle.contains("Gateway", ignoreCase = true) ||
            itemTitle.contains("Router", ignoreCase = true) || itemTitle.contains("Latency", ignoreCase = true) ||
            itemTitle.contains("Ping", ignoreCase = true) || itemTitle.contains("Packet Loss", ignoreCase = true) ||
            itemTitle.contains("Jitter", ignoreCase = true) || itemTitle.contains("Download Speed", ignoreCase = true) ||
            itemTitle.contains("Upload Speed", ignoreCase = true) || itemTitle.contains("Connection", ignoreCase = true) ||
            itemTitle.contains("Captive Portal", ignoreCase = true) || itemTitle.contains("MTU", ignoreCase = true) ||
            itemTitle.contains("Streaming", ignoreCase = true) || itemTitle.contains("CDN", ignoreCase = true) -> "network"
            // Security/Privacy (check before generic security terms)
            itemTitle.contains("Spyware", ignoreCase = true) || itemTitle.contains("Tracking", ignoreCase = true) || 
            itemTitle.contains("Ad Tracking", ignoreCase = true) -> "privacy"
            itemTitle.contains("Security", ignoreCase = true) || itemTitle.contains("Privacy", ignoreCase = true) || 
            itemTitle.contains("Hackable", ignoreCase = true) || itemTitle.contains("Hackability", ignoreCase = true) || 
            itemTitle.contains("Face Unlock", ignoreCase = true) || itemTitle.contains("Voice Clone", ignoreCase = true) -> "security"
            // Developer/Root
            itemTitle.contains("Root", ignoreCase = true) || itemTitle.contains("Superuser", ignoreCase = true) || 
            itemTitle.contains("Developer", ignoreCase = true) || itemTitle.contains("USB Debugging", ignoreCase = true) -> "developer"
            // Performance/FPS
            itemTitle.contains("FPS", ignoreCase = true) || itemTitle.contains("Frame", ignoreCase = true) || 
            itemTitle.contains("Frame Rate", ignoreCase = true) -> "performance"
            // Temperature/Heat
            itemTitle.contains("Temperature", ignoreCase = true) || itemTitle.contains("Heat", ignoreCase = true) || 
            itemTitle.contains("Cooling", ignoreCase = true) || itemTitle.contains("Temps", ignoreCase = true) -> "temperature"
            // Sensors
            itemTitle.contains("Sensor", ignoreCase = true) || itemTitle.contains("Spoofing", ignoreCase = true) -> "sensors"
            // AI/Neural
            itemTitle.contains("AI", ignoreCase = true) || itemTitle.contains("Neural", ignoreCase = true) || 
            itemTitle.contains("Inference", ignoreCase = true) -> "ai"
            // Health-related items
            itemTitle.contains("Health", ignoreCase = true) || 
            itemTitle.contains("Health Score", ignoreCase = true) ||
            itemTitle.contains("Smart Recommendations", ignoreCase = true) ||
            itemTitle.contains("Health History", ignoreCase = true) ||
            itemTitle.contains("Daily Streak", ignoreCase = true) -> "health"
            // General device info (catch-all for "Device" that's not specs)
            itemTitle.contains("Device", ignoreCase = true) -> "device_specs"
            // Text/Font/Media formats/Logs/Sync - use general
            else -> "general"
        }
    }
    
    /**
     * Generate prompt for device info items
     */
    fun generateItemPrompt(
        itemTitle: String,
        itemContent: String,
        appName: String,
        promptMode: PromptMode
    ): String {
        val category = detectItemCategory(itemTitle)
        
        return if (promptMode == PromptMode.Simple) {
            generateSimpleItemPrompt(category, itemTitle, itemContent, appName)
        } else {
            generateAdvancedItemPrompt(category, itemTitle, itemContent, appName)
        }
    }
    
    /**
     * Generate simple mode prompt for item
     */
    private fun generateSimpleItemPrompt(
        category: String,
        itemTitle: String,
        itemContent: String,
        appName: String
    ): String {
        return when (category) {
            "device_specs" -> """
I'm using **$appName** to understand my phone's specifications. Please analyze this data and help me understand what it means in simple, everyday language.

📱 **My Device Information:**
$itemContent

**Please provide a clear, structured response with:**

1. **Quick Summary** (2-3 sentences)
   - Is my phone new, mid-range, or older?
   - Overall quality assessment (good/bad/average)

2. **What Each Thing Means** (explain in simple terms)
   - Model name and brand - what does this tell me?
   - Android version - is it up-to-date?
   - RAM - what is this and why does it matter?
   - Storage - how much space do I actually have?
   - Any other important numbers

3. **What I Should Know**
   - Are any of these specs concerning? (too old, too little storage, etc.)
   - What can my phone handle well? (gaming, photos, videos, etc.)
   - What might it struggle with?

4. **Actionable Tips** (if needed)
   - Should I update anything?
   - Do I need to free up space?
   - Any settings I should change?

**Important:** Use simple analogies (like comparing to a car, computer, or house) and avoid technical jargon. Explain everything as if talking to someone who doesn't know much about phones.

Generated by **$appName** 📱
""".trimIndent()
            
            "cpu" -> """
I'm using **$appName** to understand my phone's processor (CPU). Please analyze this data and explain what it means in simple terms.

🧠 **My CPU Information:**
$itemContent

**Please provide a clear, structured response with:**

1. **Quick Summary** (2-3 sentences)
   - Is my processor fast, average, or slow?
   - Overall performance level (good for everyday use, gaming, etc.)

2. **What the Numbers Mean** (explain simply)
   - What is a CPU and why does it matter? (use a simple analogy)
   - Cores - what are they and how many do I need?
   - Speed/Clock speed - what does this number mean?
   - Architecture - what is this in simple terms?

3. **Performance Assessment**
   - What can my phone handle well? (multitasking, gaming, video editing, etc.)
   - What might it struggle with?
   - Is my processor outdated or still good?

4. **Real-World Impact**
   - How does this affect my daily phone use?
   - Will apps run smoothly?
   - Should I be concerned about anything?

5. **Actionable Tips** (if needed)
   - Any settings to optimize performance?
   - Should I avoid certain apps or tasks?

**Important:** Use simple analogies (like comparing CPU to a car's engine or a person's brain) and explain everything in everyday language.

Generated by **$appName** 📱
""".trimIndent()
            
            "battery" -> """
I'm using **$appName** to check my phone's battery health. Please analyze this data and help me understand if my battery is healthy and what I should do.

🔋 **My Battery Information:**
$itemContent

**Please provide a clear, structured response with:**

1. **Health Status** (immediate assessment)
   - Is my battery healthy, okay, or concerning?
   - What percentage of original capacity remains?
   - Should I be worried right now?

2. **Temperature Analysis**
   - What's my current battery temperature?
   - Is this normal, warm, or too hot?
   - What's a safe temperature range?
   - Should I let it cool down?

3. **Charging Status & Behavior**
   - How is my battery charging? (fast, slow, not charging?)
   - Is the charging behavior normal?
   - Any warning signs I should watch for?

4. **Battery Life Tips** (actionable advice)
   - How can I make my battery last longer?
   - Should I change how I charge my phone?
   - What settings should I adjust?
   - When should I consider replacing the battery?

5. **Warning Signs** (what to watch for)
   - What indicates my battery is failing?
   - When should I take action?
   - Any immediate concerns?

**Important:** Use simple language and explain what each number means. Tell me if I need to take action now or if everything is fine.

Generated by **$appName** 📱
""".trimIndent()
            
            "storage" -> """
I'm using **$appName** to check my phone's storage. Please analyze this data and help me understand if I need to free up space and how to do it safely.

💾 **My Storage Information:**
$itemContent

**Please provide a clear, structured response with:**

1. **Storage Status** (immediate assessment)
   - How much space do I have left?
   - Is this urgent, getting low, or fine?
   - What percentage of storage is used?
   - Should I be concerned right now?

2. **What's Using Space** (breakdown)
   - What's taking up the most space? (apps, photos, videos, system files, etc.)
   - How much space does each category use?
   - What's normal vs. what's unusual?

3. **Cleanup Priority** (what to delete first)
   - What can I safely delete? (rank by priority)
   - Should I delete photos, apps, cache, or something else first?
   - What should I NEVER delete?
   - How much space will I free up?

4. **Step-by-Step Cleanup Guide**
   - Specific steps to free up space (with instructions)
   - How to clear app cache safely
   - How to move photos to cloud storage
   - How to uninstall unused apps
   - How to find and delete large files

5. **Prevention Tips**
   - How to prevent storage from filling up again
   - Best practices for managing storage
   - When to use cloud storage vs. local storage

**Important:** Give me specific, actionable steps I can do right now. Explain what each number means and prioritize what's most urgent.

Generated by **$appName** 📱
""".trimIndent()
            
            "security" -> """
I'm using **$appName** to check my phone's security. Please analyze this data and help me understand if my phone is safe and what I need to do.

🔐 **My Security Information:**
$itemContent

**Please provide a clear, structured response with:**

1. **Security Status** (immediate assessment)
   - Overall: Is my phone secure, at risk, or vulnerable?
   - What's the main security concern (if any)?
   - Should I be worried right now?

2. **What Each Check Means** (explain simply)
   - What security checks were performed?
   - What does each result mean? (pass/fail/warning)
   - What are the risks if something failed?

3. **Specific Risks Identified**
   - Are there any vulnerabilities found?
   - What could happen if I don't fix them?
   - How urgent is each issue?
   - Are there any suspicious activities?

4. **How to Improve Security** (actionable steps)
   - Specific settings to change
   - Apps or features to disable
   - Updates to install
   - Security features to enable
   - Step-by-step instructions

5. **Ongoing Protection**
   - Best practices to stay secure
   - What to watch for in the future
   - How often to check security
   - Warning signs of security issues

**Important:** Prioritize urgent issues first. Explain risks in simple terms (like "someone could access your data" rather than technical jargon). Give me clear steps to fix problems.

Generated by **$appName** 📱
""".trimIndent()
            
            "gpu" -> """
I'm using **$appName** to understand my phone's graphics processor (GPU). Please analyze this data and explain what it means for gaming, videos, and overall visual quality.

🎮 **My GPU Information:**
$itemContent

**Please provide a clear, structured response with:**

1. **Graphics Performance Level** (quick assessment)
   - Is my GPU good, average, or outdated?
   - Overall capability: Can it handle modern games/videos?
   - How does it compare to current standards?

2. **What the Numbers Mean** (explain simply)
   - What is a GPU and why does it matter? (use simple analogy)
   - What do the GPU specifications mean?
   - What's the difference between GPU and CPU?

3. **What My Phone Can Handle**
   - What games can I run smoothly? (high/medium/low settings)
   - Can I watch 4K videos without lag?
   - Will video editing work well?
   - How does it affect camera quality and photo processing?

4. **Real-World Impact**
   - How does this affect my daily use?
   - Will apps with graphics run smoothly?
   - Should I expect any visual issues?

5. **Optimization Tips** (if needed)
   - Settings to improve graphics performance
   - Games/apps to avoid if GPU is weak
   - How to get better performance

**Important:** Use simple analogies (like comparing GPU to a graphics card in a computer or a video game console). Explain what games and tasks my phone can handle.

Generated by **$appName** 📱
""".trimIndent()
            
            "display" -> """
I'm using **$appName** to understand my phone's screen and display settings. Please analyze this data and help me optimize my display for quality, battery life, and eye comfort.

📺 **My Display Information:**
$itemContent

**Please provide a clear, structured response with:**

1. **Screen Quality Assessment**
   - Is my screen good quality? (resolution, clarity, color accuracy)
   - How does it compare to other phones?
   - What are the strengths and limitations?

2. **What Each Setting Means** (explain simply)
   - Resolution - what does this number mean?
   - Refresh rate - what is this and why does it matter?
   - Brightness - what's optimal?
   - Color settings - what should I use?

3. **Battery Impact**
   - How does screen brightness affect battery life?
   - Which display settings drain battery most?
   - How much battery can I save with different settings?

4. **Optimal Settings for Different Needs**
   - Best settings for battery saving
   - Best settings for visual quality
   - Best settings for eye comfort (reduce eye strain)
   - Best settings for outdoor visibility

5. **Step-by-Step Optimization Guide**
   - Specific settings to change
   - How to enable dark mode (if available)
   - How to adjust brightness automatically
   - How to reduce blue light for better sleep

**Important:** Give me specific numbers or settings to use (like "set brightness to 50%" or "enable 60Hz refresh rate"). Explain the trade-offs between quality and battery life.

Generated by **$appName** 📱
""".trimIndent()
            
            "camera" -> """
I'm using **$appName** to check which apps are accessing my camera, microphone, speaker, and flashlight. Please analyze this data and help me understand if there are any privacy concerns.

📷 **My Camera/Microphone Access Log:**
$itemContent

**Please provide a clear, structured response with:**

1. **Privacy Assessment** (immediate concern)
   - Are there any suspicious apps accessing my camera/mic?
   - Is this normal or concerning?
   - Should I be worried about any specific apps?

2. **Access Log Breakdown** (what each app did)
   - Which apps accessed the camera? When and why?
   - Which apps accessed the microphone? When and why?
   - Which apps used the speaker or flashlight?
   - Are these legitimate uses or suspicious?

3. **Normal vs. Suspicious Activity**
   - What's normal app behavior? (e.g., camera app using camera)
   - What's suspicious? (e.g., calculator app using camera)
   - Red flags to watch for

4. **How to Control Access** (actionable steps)
   - Step-by-step: How to revoke camera/mic permissions
   - How to check which apps have permissions
   - How to set permissions to "ask every time"
   - Which apps should I remove permissions from?

5. **Privacy Best Practices**
   - How to protect my privacy going forward
   - What to watch for in the future
   - How often to check permissions
   - Warning signs of privacy violations

**Important:** Prioritize any suspicious activity. Give me specific app names to watch or revoke permissions from. Explain what each access means in simple terms.

Generated by **$appName** 📱
""".trimIndent()
            
            "location" -> """
I'm using **$appName** to check my phone's GPS and location services. Please analyze this data and help me understand privacy, accuracy, and battery impact.

📍 **My Location Information:**
$itemContent

**Please provide a clear, structured response with:**

1. **GPS Accuracy & Performance**
   - How accurate is my GPS? (good, average, poor)
   - How many satellites is it connected to?
   - Will navigation apps work well?
   - Any issues with location accuracy?

2. **Privacy Assessment**
   - Which apps are using my location?
   - Should I be concerned about location privacy?
   - Are there any suspicious location access patterns?
   - What information can apps learn from my location?

3. **Battery Impact**
   - How much does location services drain battery?
   - Should I turn off location to save battery?
   - What's the trade-off between privacy/battery and functionality?

4. **Location Settings Optimization**
   - Which apps should have location access? (which don't need it)
   - How to set location to "only while using app"
   - How to disable location for specific apps
   - Step-by-step permission management

5. **Best Practices**
   - When to keep location on vs. off
   - How to balance privacy, battery, and functionality
   - What to watch for regarding location privacy
   - How to use location services safely

**Important:** Give me specific app names that should/shouldn't have location access. Explain the privacy implications in simple terms. Help me balance battery life with functionality.

Generated by **$appName** 📱
""".trimIndent()
            
            "network" -> """
I'm using **$appName** to check my phone's network connection. Please analyze this data and help me understand connection quality, speed, and security.

📶 **My Network Information:**
$itemContent

**Please provide a clear, structured response with:**

1. **Connection Quality** (immediate assessment)
   - Is my network connection good, average, or poor?
   - What type of connection do I have? (4G, 5G, Wi-Fi, etc.)
   - How fast is my internet? (good for streaming, video calls, etc.)
   - Any connection issues or instability?
   - Should I be concerned about my connection right now?

2. **What the Numbers Mean** (explain simply)
   - Signal strength - what does this number mean? (use simple analogy like "bars" or "how close to a router")
   - Network type (4G/5G/Wi-Fi) - what's the difference? (explain like comparing speeds to different roads)
   - Download/Upload speed - what do these numbers mean? (what's good vs. bad?)
   - Latency/Ping - what is this and why does it matter? (explain like "response time")
   - Packet loss - what does this mean? (explain simply)
   - Any technical terms explained in simple, everyday language

3. **Network Performance** (what can I actually do?)
   - Can I stream videos without buffering? (YouTube, Netflix, etc.)
   - Will video calls work smoothly? (Zoom, WhatsApp, FaceTime)
   - Is it fast enough for online gaming? (what games will work well?)
   - Can I download large files quickly?
   - Will multiple devices work well on this connection?
   - Any limitations I should know about?

4. **Security & Privacy** (is my connection safe?)
   - Is my network connection secure? (Wi-Fi encryption, public vs. private)
   - Should I be concerned about network security?
   - Any risks with public Wi-Fi? (what could happen?)
   - How to protect myself on public networks (specific steps)
   - Should I use a VPN? (when and why)
   - What information could be exposed on an insecure network?

5. **Connection Issues** (if any problems found)
   - What specific issues were detected? (slow speed, dropped connections, etc.)
   - What's causing the problems? (signal strength, network congestion, etc.)
   - How serious are these issues?
   - Can I fix them or is it the network provider's problem?

6. **How to Improve Connection** (actionable tips)
   - Settings to optimize network performance (specific Android settings)
   - How to improve signal strength (positioning, Wi-Fi band selection)
   - When to switch between Wi-Fi and mobile data
   - Troubleshooting steps for connection issues (step-by-step)
   - When to contact my internet provider
   - How to test if changes helped

7. **Best Practices** (ongoing network management)
   - When to use Wi-Fi vs. mobile data
   - How to save mobile data
   - How to stay secure on different networks
   - What to watch for regarding network performance
   - How often to check network settings

**Important:** Explain network types (4G/5G) using simple analogies (like comparing to different speed highways). Give me specific steps to improve connection if needed. Prioritize any security concerns. Use everyday language and avoid technical jargon.

Generated by **$appName** 📱
""".trimIndent()
            
            "developer" -> """
I'm using **$appName** to check my phone's developer settings. Please analyze this data and help me understand if these settings are safe or if I should change them.

⚙️ **My Developer Settings Information:**
$itemContent

**Please provide a clear, structured response with:**

1. **Security Assessment** (immediate concern)
   - Are developer options enabled? Is this safe?
   - Is USB debugging on? What are the risks?
   - Is my phone rooted? What does this mean?
   - Should I be worried about any of these?

2. **What Each Setting Means** (explain simply)
   - Developer options - what are they and who needs them?
   - USB debugging - what is this and why is it risky?
   - Root access - what does this mean? Is it dangerous?
   - Other developer settings - what do they do?

3. **Risks & Benefits**
   - What are the security risks of having these enabled?
   - What are the benefits? (who actually needs these?)
   - When is it safe to have them on?
   - When should they definitely be off?

4. **What I Should Do** (actionable steps)
   - Should I turn these off? (yes/no and why)
   - Step-by-step: How to disable developer options
   - Step-by-step: How to disable USB debugging
   - How to check if my phone is rooted
   - What to do if my phone is rooted (if I didn't do it)

5. **Best Practices**
   - When to enable developer options (if ever)
   - How to stay safe if I need these features
   - Warning signs of security issues
   - How to protect my phone

**Important:** Prioritize security risks. If these settings are enabled and I'm not a developer, tell me to turn them off immediately. Explain risks in simple terms (like "someone could access your phone" rather than technical jargon).

Generated by **$appName** 📱
""".trimIndent()
            
            "performance" -> """
I'm using **$appName** to check my phone's performance and smoothness. Please analyze this data and help me understand if my phone is running well or if I need to optimize it.

⚡ **My Performance Information:**
$itemContent

**Please provide a clear, structured response with:**

1. **Performance Status** (quick assessment)
   - Is my phone running smoothly or laggy?
   - Overall performance: good, average, or poor?
   - Are there any performance issues?

2. **What the Numbers Mean** (explain simply)
   - Frame rate (FPS) - what is this and why does it matter?
   - What's a good FPS vs. bad FPS?
   - Frame drops - what are these and should I worry?
   - How does this affect my phone's smoothness?

3. **Real-World Impact**
   - Will apps run smoothly?
   - Will games play without lag?
   - Will scrolling and animations be smooth?
   - Any noticeable performance problems?

4. **Performance Issues** (if any)
   - What's causing performance problems? (if applicable)
   - Is it a hardware limitation or software issue?
   - Can I fix it or is it just how my phone is?

5. **How to Improve Performance** (actionable tips)
   - Settings to optimize performance
   - Apps or features to disable
   - How to reduce lag and improve smoothness
   - When to restart or clear cache
   - What to do if performance is poor

**Important:** Explain FPS and frame drops using simple analogies (like comparing to video smoothness). Give me specific steps to improve performance if needed. Tell me if issues are fixable or just hardware limitations.

Generated by **$appName** 📱
""".trimIndent()
            
            "temperature" -> """
I'm using **$appName** to check my phone's temperature. Please analyze this data and help me understand if my phone is overheating and what I should do.

🌡️ **My Temperature Information:**
$itemContent

**Please provide a clear, structured response with:**

1. **Temperature Status** (immediate concern)
   - Is my phone too hot, warm, or normal?
   - Should I be worried right now?
   - What's the current temperature of CPU, battery, and GPU?
   - Is any component dangerously hot?

2. **Temperature Ranges** (what's normal vs. dangerous)
   - What's a normal temperature range?
   - What's considered warm but okay?
   - What's dangerously hot?
   - At what temperature should I take action?

3. **What's Causing Heat** (if applicable)
   - What's making my phone hot? (apps, charging, environment, etc.)
   - Is it normal heat or unusual?
   - Which component is hottest and why?

4. **Immediate Actions** (if phone is hot)
   - What should I do right now to cool it down?
   - Should I stop using certain apps?
   - Should I remove the case?
   - Should I stop charging?
   - When is it safe to use again?

5. **Prevention & Long-term Care**
   - How to prevent overheating
   - Apps or activities to avoid
   - Charging habits that cause heat
   - Environmental factors (direct sunlight, etc.)
   - Warning signs of permanent damage

**Important:** Prioritize immediate safety. If my phone is dangerously hot, tell me to stop using it immediately. Give me specific temperature numbers and what they mean. Explain risks in simple terms.

Generated by **$appName** 📱
""".trimIndent()
            
            "sensors" -> """
I'm using **$appName** to check my phone's sensors. Please analyze this data and help me understand what sensors I have, what they do, and if any are missing or broken.

📡 **My Sensor Information:**
$itemContent

**Please provide a clear, structured response with:**

1. **Sensor Status** (quick assessment)
   - What sensors does my phone have?
   - Are all important sensors present?
   - Are any sensors missing or not working?
   - Overall: Is my phone's sensor setup good?

2. **What Each Sensor Does** (explain simply)
   - List each sensor and what it's used for
   - Which sensors are essential? (for basic phone functions)
   - Which sensors are nice-to-have? (for advanced features)
   - How do sensors affect my daily phone use?

3. **Missing or Broken Sensors** (if any)
   - Which sensors are missing? Why does this matter?
   - Which sensors aren't working? What features will be affected?
   - Can I fix broken sensors or is it a hardware issue?
   - Should I be concerned about missing sensors?

4. **How Sensors Enable Features**
   - Which phone features depend on sensors?
   - How do sensors improve my phone experience?
   - What features won't work if sensors are missing?
   - Examples: auto-brightness, rotation, step counting, etc.

5. **What I Should Know**
   - Is my sensor setup normal for my phone model?
   - Are there any issues I should address?
   - Can I test sensors to verify they work?
   - When to seek repair (if sensors are broken)

**Important:** Explain what each sensor does in simple terms (like "proximity sensor turns off screen during calls"). Tell me which sensors are essential vs. optional. If sensors are missing or broken, explain what features will be affected.

Generated by **$appName** 📱
""".trimIndent()
            
            "ai" -> """
I'm using **$appName** to check if my phone can run AI apps and features. Please analyze this data and help me understand my phone's AI capabilities.

🤖 **My AI Support Information:**
$itemContent

**Please provide a clear, structured response with:**

1. **AI Capability Assessment** (quick summary)
   - Can my phone run AI apps efficiently?
   - Overall AI support: good, limited, or poor?
   - Is my phone good for AI tasks?

2. **What AI Features I Can Use**
   - What AI apps can I run? (ChatGPT, image generators, voice assistants, etc.)
   - What AI features are available on my phone?
   - Which AI tasks will work well vs. struggle?
   - Examples of AI apps I can use

3. **What the Numbers Mean** (explain simply)
   - Neural acceleration - what is this and why does it matter?
   - AI processing units - what are these?
   - What makes a phone "AI-capable"?
   - How does this compare to other phones?

4. **Real-World AI Performance**
   - Will AI apps run smoothly or lag?
   - Can I use on-device AI (works offline) or only cloud AI?
   - How fast will AI tasks complete?
   - Any limitations I should know about?

5. **Upgrade Considerations** (if applicable)
   - Should I upgrade for better AI support?
   - What would I gain with a better AI-capable phone?
   - Is my current phone sufficient for basic AI tasks?
   - When is AI capability important?

**Important:** Explain AI terms in simple language (like "neural acceleration helps AI run faster on your phone"). Give me specific examples of AI apps I can use. Help me understand if my phone is good enough or if I need an upgrade.

Generated by **$appName** 📱
""".trimIndent()
            
            "power" -> {
                // Special handling for Camera Power Test
                if (itemTitle.contains("Camera Power", ignoreCase = true)) {
                    generateSimpleCameraPowerTestPrompt(itemTitle, itemContent, appName)
                } else if (itemTitle.contains("Component Breakdown", ignoreCase = true)) {
                    """
Hi! I'm using **$appName** to check which parts of my phone are using the most battery. Please explain this in simple terms - I'm not very technical!

📱 **What $appName Found:**
(The detailed technical data is below - but here's what I understand so far)

⚡ **Component Power Usage:** Different parts of my phone (like screen, Wi-Fi, apps, etc.) are using battery power. Some use more than others.

**What I Need Help With:**
• Can you explain what "power consumption" means in simple, everyday language? (Like: how much electricity each part uses?)
• Which component is using the most battery? Why?
• Is this normal, or should I be worried?
• What does "Watts" mean? (I see this number but don't understand it)
• How does this affect my battery life? (Will my battery drain fast?)
• What are 1-2 easy things I can do right now to save battery?
• Should I turn off certain features or apps?

**Please talk to me like you're explaining to a friend who doesn't know much about phones!** 😊

Made with **$appName** - my battery usage checker 📱
                    """.trimIndent()
                } else if (itemTitle.contains("Power Statistics", ignoreCase = true) || itemTitle.contains("Total Power", ignoreCase = true)) {
                    """
Hi! I'm using **$appName** to check how much battery my phone is using. Please explain this in simple terms - I'm not very technical!

📱 **What $appName Found:**
(The detailed technical data is below - but here's what I understand so far)

⚡ **Power Usage:** My phone is using battery power. The numbers show average, peak, and minimum power usage over time.

**What I Need Help With:**
• Can you explain what "power consumption" means in simple terms? (Like: how much electricity my phone uses?)
• What does "Watts" mean? (I see this number but don't understand it)
• Is my power usage normal, high, or low? Should I be worried?
• What does "power trend" mean? (Is it going up, down, or staying the same?)
• How does this affect my battery life? (Will my battery drain fast or slow?)
• What are 1-2 easy things I can do right now to save battery?
• Should I change any settings?

**Please talk to me like you're explaining to a friend who doesn't know much about phones!** 😊

Made with **$appName** - my battery usage checker 📱
                    """.trimIndent()
                } else if (itemContent.contains("Component:", ignoreCase = true) || itemContent.contains("Power Consumption:", ignoreCase = true)) {
                    // Individual component power data
                    """
Hi! I'm using **$appName** to check how much battery a specific part of my phone is using. Please explain this in simple terms - I'm not very technical!

📱 **What $appName Found:**
(The detailed technical data is below)

⚡ **Component Power Usage:** This specific part of my phone (like screen, Wi-Fi, CPU, etc.) is using battery power.

**What I Need Help With:**
• Can you explain what this component does and why it uses battery?
• Is this component using too much battery, or is it normal?
• What does "Watts" mean? (I see this number but don't understand it)
• How does this affect my battery life? (Will my battery drain fast if this uses a lot?)
• What are 1-2 easy things I can do to reduce this component's battery usage?
• Should I turn this feature off, or just use it less?

**Please talk to me like you're explaining to a friend who doesn't know much about phones!** 😊

Made with **$appName** - my battery usage checker 📱
                    """.trimIndent()
                } else {
                    """
Hi! I'm using **$appName** to understand my phone's power consumption. Please analyze this data and help me understand how much battery different components and activities use.

⚡ **My Power Consumption Information:**
$itemContent

**Please provide a clear, structured response with:**

1. **Quick Summary** (2-3 sentences)
   - How much power is my phone using overall?
   - Is this normal, high, or low?
   - Which components are using the most power?

2. **What the Numbers Mean** (explain simply)
   - What does "power consumption" mean? (use simple analogy like comparing to electricity usage)
   - What does "Watts" mean?
   - How does power consumption relate to battery drain?
   - What's a normal power usage range?

3. **Battery Impact**
   - How much battery does this consume per hour?
   - How long will my battery last at this power usage?
   - Which activities or components drain battery fastest?
   - What's the biggest battery drainer?

4. **Optimization Tips** (actionable advice)
   - How can I reduce power consumption?
   - Which settings should I change to save battery?
   - What activities should I avoid to preserve battery?
   - Tips for extending battery life

**Important:** Focus on power consumption and battery impact. Use simple analogies and avoid technical jargon. Explain everything as if talking to someone who doesn't know much about phone power.

Generated by **$appName** 📱
""".trimIndent()
                }
            }
            
            "health" -> {
                if (itemTitle.contains("Smart Recommendations", ignoreCase = true) || itemTitle.contains("Improvement", ignoreCase = true)) {
                    """
Hi! I'm using **$appName** to get suggestions on how to improve my phone's health. Please explain this in simple terms - I'm not very technical!

📱 **What $appName Found:**
(The detailed technical data is below - but here's what I understand so far)

🏥 **Health Recommendations:** The app has suggestions for improving my phone's health score.

**What I Need Help With:**
• Can you explain what these recommendations mean in simple, everyday language?
• Which suggestions are most important? (What should I do first?)
• Are these suggestions urgent, or can I do them later?
• How will following these suggestions help my phone?
• What are 1-2 easy things I can do right now?
• How often should I check my phone's health?

**Please talk to me like you're explaining to a friend who doesn't know much about phones!** 😊

Made with **$appName** - my phone health checker 📱
                    """.trimIndent()
                } else if (itemTitle.contains("Health History", ignoreCase = true) || itemTitle.contains("Daily Streak", ignoreCase = true)) {
                    """
Hi! I'm using **$appName** to check my phone's health history. Please explain this in simple terms - I'm not very technical!

📱 **What $appName Found:**
(The detailed technical data is below - but here's what I understand so far)

📊 **Health History:** My phone's health score over the past week, plus my daily streak and best score.

**What I Need Help With:**
• Can you explain what a "health score" means? (Like: is it a grade for my phone?)
• Is my health score getting better, worse, or staying the same?
• What does my "daily streak" mean? (Is it good to check my phone's health every day?)
• Is my best score good? (What's a good health score?)
• Should I be worried if my score is going down?
• What can I learn from this history?

**Please talk to me like you're explaining to a friend who doesn't know much about phones!** 😊

Made with **$appName** - my phone health checker 📱
                    """.trimIndent()
                } else {
                    """
Hi! I'm using **$appName** to check my phone's overall health. Please analyze this health report and help me understand what it means in simple, everyday language.

📱 **My Health Report:**
$itemContent

**Please provide a clear, structured response with:**

1. **Quick Summary** (2-3 sentences)
   - Is my phone healthy, okay, or needs attention?
   - Overall condition assessment (good/bad/average)

2. **What My Health Score Means** (explain simply)
   - What is a health score? (like a grade for my phone)
   - Is my score good or bad? (what's normal?)
   - What does this number tell me about my phone?

3. **What I Should Know**
   - Are there any concerning issues? (battery, storage, performance, etc.)
   - What's working well?
   - What needs improvement?

4. **Actionable Tips** (1-2 specific things I can do)
   - Easy steps to improve my phone's health
   - What should I do first?
   - How often should I check my phone's health?

**Important:** Use simple analogies and avoid technical jargon. Explain everything as if talking to someone who doesn't know much about phones.

Generated by **$appName** 📱
                    """.trimIndent()
                }
            }
            
            "privacy" -> """
I'm using **$appName** to check my phone's privacy and tracking status. Please analyze this data and help me understand if I'm being tracked and how to protect my privacy.

🔒 **My Privacy Information:**
$itemContent

**Please provide a clear, structured response with:**

1. **Privacy Risk Assessment** (immediate concern)
   - Am I being tracked or monitored?
   - Overall privacy status: safe, at risk, or vulnerable?
   - Are there any immediate privacy threats?
   - Should I be worried right now?

2. **What Was Detected** (explain simply)
   - What tracking or monitoring was found?
   - What does "spyware" mean? (in simple terms)
   - What does "ad tracking" mean?
   - Are these normal or suspicious?

3. **Specific Privacy Risks** (if any)
   - What information is being collected?
   - Who might be tracking me? (apps, advertisers, etc.)
   - What can they do with this information?
   - How serious are these risks?

4. **How to Protect My Privacy** (actionable steps)
   - Specific settings to change
   - Apps to uninstall or restrict
   - How to disable tracking
   - Step-by-step privacy protection guide
   - How to remove spyware (if found)

5. **Ongoing Privacy Protection**
   - Best practices to stay private
   - What to watch for in the future
   - How often to check privacy settings
   - Warning signs of privacy violations
   - Apps or behaviors to avoid

**Important:** Prioritize any serious privacy threats. If spyware or malicious tracking is found, tell me to take immediate action. Explain risks in simple terms (like "this app can see your location" rather than technical jargon). Give me clear steps to protect my privacy.

Generated by **$appName** 📱
""".trimIndent()

            "privacy_report" -> """
I'm using **$appName** to check my network's privacy and security. My phone just ran an automated privacy audit on my current network connection. Please analyze these results and help me understand what they mean.

🔐 **My Network Privacy Report:**
$itemContent

**Please provide a clear, structured response with:**

1. **Privacy Summary** (2-3 sentences)
   - How private and secure is my current network connection?
   - Am I at risk? Rate my privacy: Excellent / Good / Fair / Poor

2. **What Each Check Means** (explain in simple terms)
   - DNS Integrity - what is DNS and why does it matter for privacy?
   - SSL Certificate - what is this checking and should I worry?
   - Proxy Detection - what is a transparent proxy and why is it bad?
   - Deep Packet Inspection - what does this mean in everyday terms?
   - ISP Behavior - what could my ISP be doing?
   - Captive Portal - what is this and when should I be concerned?
   - Private DNS - what is this and how do I enable it?

3. **Most Important Findings** (prioritize)
   - Which warnings or failures are most serious?
   - What are the real-world risks? (e.g., "someone could see which websites you visit")
   - Is my ISP doing anything unusual compared to others?

4. **What I Should Do Right Now** (actionable steps)
   - Step-by-step instructions to fix any issues
   - Which settings to change on my phone
   - Should I use a VPN? Which free/paid options are good?
   - How to enable Private DNS (step by step)

5. **Fun Fact or Tip**
   - One interesting thing about network privacy most people don't know

**Important:** Explain everything as if talking to a friend who isn't technical. Use analogies like "it's like sending a postcard vs. a sealed letter" to explain privacy concepts. Be honest about risks but don't be overly alarming.

Generated by **$appName** 📱
""".trimIndent()

            "reachability" -> """
I'm using **$appName** to check which popular services my network can reach. Please analyze these reachability test results and help me understand what they mean.

🌐 **Network Reachability Results:**
$itemContent

**Please provide a clear, structured response with:**

1. **Summary** (2-3 sentences) - Can my network reach most services? Is anything blocked?
2. **Blocked Services** - For any service that failed, explain the likely cause (DNS filtering, firewall, ISP restriction, network policy?)
3. **What to Do** - 3 safe, legal steps I can try (change DNS, switch networks, contact ISP)
4. **DNS Privacy** - Is my DNS setup protecting my privacy?

**Important:** Only suggest safe, legal troubleshooting (change DNS settings, switch network, contact ISP). Do NOT suggest circumventing network policies. Explain simply.

Generated by **$appName** 📱
""".trimIndent()

            "zero_trust" -> """
I'm using **$appName** to audit my phone's security posture. Please analyze this security dashboard report and help me understand what it means.

🛡️ **Security Dashboard Results:**
$itemContent

**Please provide a clear, structured response with:**

1. **Security Summary** (2-3 sentences) - How secure is my phone overall?
2. **Most Important Findings** - Which warnings should I address first?
3. **App Privacy** - Are my apps behaving well? Any concerning permissions?
4. **Network Trust** - Is my network connection private and secure?
5. **Device Integrity** - Are my device settings secure?
6. **Action Plan** - Top 3 things I should do right now, with step-by-step instructions

**Important:** Explain everything in simple, everyday language. Use analogies. Be honest about risks but don't be overly alarming. Focus on actionable steps.

Generated by **$appName** 📱
""".trimIndent()
            
            else -> """
I'm using **$appName** to understand this information about my phone. Please analyze this data and explain what it means in simple terms.

📱 **Category:** $itemTitle

**My Data:**
$itemContent

**Please provide a clear, structured response with:**

1. **Quick Summary** (2-3 sentences)
   - What is this information about?
   - Overall status: good, normal, or concerning?
   - Key takeaway

2. **What This Information Means** (explain simply)
   - What does each piece of data mean?
   - What are the important numbers or values?
   - What should I pay attention to?
   - Any technical terms explained in plain language

3. **Assessment**
   - Is this normal or unusual?
   - Should I be concerned about anything?
   - Are there any red flags?
   - How does this compare to what's expected?

4. **What I Can Do With This Information**
   - How can I use this information?
   - Does this affect my phone's performance or security?
   - What does this tell me about my phone?

5. **Actionable Steps** (if needed)
   - Are there any actions I should take?
   - Settings to change or check?
   - When to seek help or repair?
   - How to monitor this in the future?

**Important:** Explain everything in simple, everyday language. Use analogies if helpful. Prioritize any concerns or actions needed. Make it easy to understand even if I'm not technical.

Generated by **$appName** 📱
""".trimIndent()
        }
    }
    
    /**
     * Generate advanced mode prompt for item
     */
    private fun generateAdvancedItemPrompt(
        category: String,
        itemTitle: String,
        itemContent: String,
        appName: String
    ): String {
        return when (category) {
            "device_specs" -> """
You are an **Android Device Specifications Expert**. Analyze this device specification data from **$appName** and provide a comprehensive, structured assessment that balances technical accuracy with practical insights.

📱 **Device Specifications Data:**
$itemContent

**Please provide a detailed analysis with the following structure:**

1. **Device Tier Classification**
   - Market tier: Flagship, Mid-range, or Budget
   - Year/Generation: When was this device released?
   - Market positioning: How does it compare to current standards?
   - Value assessment: Is this good value for its tier?

2. **Specification Breakdown** (Technical Analysis)
   - **Model & Brand**: What does this tell us about build quality and support?
   - **Android Version**: Is it current, outdated, or future-proof?
   - **RAM**: Capacity, type (LPDDR4/5), and real-world implications
   - **Storage**: Type (UFS/eMMC), speed, and expandability
   - **Processor**: Architecture, performance tier, efficiency
   - **Other key specs**: Display, camera, connectivity, etc.

3. **Performance Expectations** (Real-World Analysis)
   - What tasks will this device handle excellently?
   - What tasks will it handle adequately?
   - What tasks might it struggle with?
   - Gaming capabilities: What games/settings can it run?
   - Multitasking: How many apps can run smoothly?
   - Media: Video playback, editing, streaming capabilities

4. **Strengths & Limitations**
   - **Strengths**: What are the standout features?
   - **Limitations**: What are the bottlenecks or weak points?
   - **Trade-offs**: What did the manufacturer prioritize/sacrifice?

5. **Comparison & Context**
   - How does it compare to similar devices in its price range?
   - How does it compare to current flagship/mid-range standards?
   - Is it still competitive today, or outdated?
   - What devices would be an upgrade/downgrade?

6. **Optimization Recommendations**
   - Settings to optimize for performance
   - Settings to optimize for battery life
   - Apps or features to enable/disable
   - Maintenance tips for this specific device

**Format:** Use clear headings, bullet points, and specific numbers/metrics where relevant. Balance technical depth with practical, actionable insights.

Generated by **$appName** 📱
""".trimIndent()
            
            "cpu" -> """
You are an **Android CPU & Performance Expert**. Analyze this processor data from **$appName** and provide detailed technical insights with practical implications.

🧠 **CPU Data:**
$itemContent

**Please provide a comprehensive analysis with the following structure:**

1. **Processor Identification & Tier**
   - CPU model, architecture (ARM v8/v9, x86, etc.)
   - Performance tier: Flagship, Mid-range, or Entry-level
   - Manufacturing process (nm) and its impact
   - Release date and current market position

2. **Architecture Analysis** (Technical Deep Dive)
   - **Core Configuration**: Big.LITTLE setup, core counts, types
   - **Clock Speeds**: Base, boost, and sustained frequencies
   - **Instruction Set**: ARM extensions (NEON, etc.), 64-bit support
   - **Cache Hierarchy**: L1, L2, L3 cache sizes and impact
   - **Memory Support**: RAM types, speeds, bandwidth

3. **Performance Characteristics**
   - **Single-Core Performance**: What this means for app responsiveness
   - **Multi-Core Performance**: Parallel processing capabilities
   - **GPU Integration**: Integrated graphics performance
   - **AI/NPU**: Neural processing capabilities (if applicable)
   - **Benchmark Context**: How it compares to known benchmarks

4. **Real-World Performance Analysis**
   - **Gaming**: What games/settings can it handle? Frame rate expectations
   - **Multitasking**: How many apps can run simultaneously?
   - **Content Creation**: Video editing, photo processing capabilities
   - **AI Workloads**: On-device AI model inference performance
   - **Daily Use**: App launch times, UI smoothness, responsiveness

5. **Thermal & Power Efficiency**
   - **Power Consumption**: Typical and peak power draw
   - **Thermal Management**: How well does it handle heat?
   - **Battery Impact**: How does CPU usage affect battery life?
   - **Throttling Behavior**: When and why does it throttle?
   - **Efficiency**: Performance per watt analysis

6. **Comparison & Context**
   - How does it compare to similar-tier processors?
   - How does it compare to current flagship processors?
   - What are the main competitors?
   - Is it still competitive or outdated?

7. **Optimization Recommendations**
   - CPU governor settings (if accessible)
   - Apps/tasks that will stress the CPU
   - How to maximize performance when needed
   - How to improve efficiency for battery life
   - Thermal management tips

**Format:** Use technical terms with brief explanations. Include specific numbers, comparisons, and practical implications. Balance depth with clarity.

Generated by **$appName** 📱
""".trimIndent()
            
            "battery" -> """
You are an **Android Battery & Power Management Expert**. Analyze this battery data from **$appName** and provide comprehensive technical analysis with actionable recommendations.

🔋 **Battery Data:**
$itemContent

**Please provide a detailed analysis with the following structure:**

1. **Battery Health Assessment** (Current Status)
   - **Capacity**: Current mAh vs. original design capacity
   - **Health Percentage**: Remaining capacity and degradation level
   - **Cycle Count**: Estimated charge cycles (if available)
   - **Age**: How old is the battery? Expected lifespan remaining
   - **Overall Status**: Excellent, Good, Fair, Poor, or Critical

2. **Temperature Analysis** (Thermal Assessment)
   - **Current Temperature**: CPU, battery, and ambient readings
   - **Temperature Ranges**: Normal (20-35°C), Warm (35-40°C), Hot (40-45°C), Critical (>45°C)
   - **Thermal Behavior**: Is it operating within safe ranges?
   - **Heat Sources**: What's causing elevated temperatures (if any)?
   - **Impact on Health**: How temperature affects battery degradation

3. **Charging Analysis** (Behavior & Patterns)
   - **Charging Status**: Current state (charging, discharging, full)
   - **Charging Speed**: Fast charging, standard, or slow?
   - **Charging Health**: Voltage, current, and charging efficiency
   - **Charging Patterns**: Optimal vs. current charging habits
   - **Battery Technology**: Li-ion, Li-poly, and specific characteristics

4. **Power Consumption Analysis**
   - **Current Draw**: How much power is being consumed?
   - **Power-Hungry Components**: What's draining battery most?
   - **Standby Drain**: Background power consumption
   - **Screen-On Time**: Expected battery life under typical use
   - **Battery Efficiency**: How efficiently is power being used?

5. **Optimization Strategies** (Actionable Recommendations)
   - **Charging Best Practices**: Optimal charging patterns, when to charge
   - **Power-Saving Settings**: Specific settings to enable/disable
   - **App Management**: Which apps to restrict/optimize
   - **Usage Patterns**: How to adjust usage for better battery life
   - **Maintenance**: Battery calibration, cache clearing, etc.

6. **Warning Signs & Maintenance**
   - **Red Flags**: Signs of battery failure or degradation
   - **When to Replace**: Indicators that battery needs replacement
   - **Safety Concerns**: Any immediate safety issues?
   - **Preventive Measures**: How to slow degradation
   - **Monitoring**: What to watch for going forward

7. **Technical Specifications** (If Available)
   - Battery chemistry and technology
   - Design capacity and current capacity
   - Voltage characteristics
   - Charging protocols supported
   - Fast charging capabilities

**Format:** Include specific numbers, temperature ranges, percentages, and time estimates. Provide clear action items prioritized by urgency. Balance technical accuracy with practical advice.

Generated by **$appName** 📱
""".trimIndent()
            
            "storage" -> """
You are an **Android Storage Management Expert**. Analyze this storage data from **$appName** and provide comprehensive storage analysis with optimization strategies.

💾 **Storage Data:**
$itemContent

**Please provide a detailed analysis with the following structure:**

1. **Storage Status Assessment** (Current State)
   - **Total Capacity**: Device storage size (GB/TB)
   - **Used Space**: Current usage and percentage
   - **Available Space**: Free space remaining
   - **Urgency Level**: Critical (<10%), Low (<30%), Adequate (>30%)
   - **Storage Type**: UFS, eMMC, or other (if available)

2. **Storage Breakdown** (Detailed Analysis)
   - **Apps**: Space used by installed applications
   - **Media**: Photos, videos, music, downloads
   - **System**: OS, system files, reserved space
   - **Cache**: Temporary files and app caches
   - **Other**: Miscellaneous files and data
   - **Largest Consumers**: Top 10 space-consuming items

3. **Performance Impact Analysis**
   - **Storage Speed**: Read/write speeds (if available)
   - **Performance Degradation**: How low storage affects speed
   - **Fragmentation**: Impact on performance (if applicable)
   - **Storage Type Impact**: How storage technology affects performance
   - **Optimal Usage**: Recommended free space percentage

4. **Cleanup Priority & Strategy**
   - **Immediate Actions**: What to delete/clear right now (ranked by priority)
   - **Safe to Delete**: Cache, temporary files, old downloads
   - **Review Before Deleting**: Large apps, media files, backups
   - **Never Delete**: System files, critical app data
   - **Expected Space Recovery**: How much space each action will free

5. **Optimization Strategies** (Actionable Steps)
   - **Cache Management**: How to clear app caches safely
   - **App Management**: Uninstall unused apps, clear app data
   - **Media Organization**: Move photos/videos to cloud or external storage
   - **Download Cleanup**: Remove old downloads and files
   - **System Optimization**: Storage optimization features to use

6. **Cloud & External Storage Solutions**
   - **When to Use Cloud**: Photos, videos, documents
   - **Cloud Services**: Google Photos, Drive, OneDrive recommendations
   - **External Storage**: SD card options and considerations
   - **Hybrid Approach**: What to keep local vs. cloud
   - **Cost-Benefit**: Free vs. paid storage options

7. **Long-Term Storage Management**
   - **Maintenance Schedule**: How often to clean storage
   - **Prevention Strategies**: How to prevent storage from filling up
   - **Automation**: Auto-backup, auto-delete settings
   - **Monitoring**: How to track storage usage over time
   - **Best Practices**: Ongoing storage management habits

**Format:** Include specific sizes (GB/MB), percentages, and space recovery estimates. Prioritize actions by urgency and impact. Provide step-by-step instructions where helpful.

Generated by **$appName** 📱
""".trimIndent()
            
            "security" -> """
You are an **Android Security & Privacy Expert**. Analyze this security data from **$appName** and provide a comprehensive security assessment with prioritized recommendations.

🔐 **Security Data:**
$itemContent

**Please provide a detailed security analysis with the following structure:**

1. **Overall Security Posture** (Risk Assessment)
   - **Security Score**: Overall rating (Excellent/Good/Fair/Poor/Critical)
   - **Risk Level**: Low, Medium, High, or Critical
   - **Key Strengths**: What security measures are working well
   - **Key Weaknesses**: Main security concerns
   - **Immediate Threats**: Any urgent security issues requiring immediate action

2. **Vulnerability Analysis** (Detailed Assessment)
   - **System Vulnerabilities**: Android version, security patches, known CVEs
   - **Root/Jailbreak Status**: Is device rooted? Security implications
   - **Developer Options**: USB debugging, OEM unlock status and risks
   - **Bootloader Status**: Locked vs. unlocked and security impact
   - **App Vulnerabilities**: Any apps with known security issues
   - **Network Security**: Wi-Fi, VPN, DNS security status

3. **Privacy & Data Protection Analysis**
   - **App Permissions**: Overly permissive apps, suspicious permissions
   - **Data Collection**: Apps collecting excessive data
   - **Tracking**: Ad tracking, analytics, location tracking
   - **Spyware/Malware**: Any detected malicious software
   - **Privacy Settings**: Current privacy configuration status
   - **Data Exposure**: Risk of data leakage or unauthorized access

4. **Security Features Status**
   - **Screen Lock**: Type and strength (PIN, pattern, biometric)
   - **Encryption**: Device encryption status
   - **Google Play Protect**: Status and effectiveness
   - **Two-Factor Authentication**: Enabled services
   - **Secure Boot**: Boot security status
   - **App Verification**: Unknown sources, app signing

5. **Hardening Recommendations** (Prioritized Actions)
   - **Critical (Do Immediately)**: Urgent security fixes
   - **High Priority**: Important security improvements
   - **Medium Priority**: Recommended security enhancements
   - **Low Priority**: Nice-to-have security optimizations
   - **Step-by-Step Instructions**: How to implement each recommendation

6. **Specific Security Fixes** (Actionable Steps)
   - **System Updates**: Install security patches and OS updates
   - **App Management**: Remove risky apps, update apps
   - **Permission Review**: Revoke unnecessary permissions
   - **Settings Changes**: Security settings to enable/disable
   - **Network Security**: VPN, secure Wi-Fi, DNS settings
   - **Account Security**: Enable 2FA, review account access

7. **Ongoing Security Monitoring**
   - **What to Monitor**: Key indicators of security issues
   - **How Often**: Recommended security check frequency
   - **Warning Signs**: Red flags to watch for
   - **Best Practices**: Daily/weekly/monthly security habits
   - **Tools & Resources**: Security apps and services to use

**Format:** Prioritize by urgency (Critical → Low). Include specific CVE numbers, patch levels, and version information where relevant. Provide clear, actionable steps with explanations of why each action matters.

Generated by **$appName** 📱
""".trimIndent()
            
            "gpu" -> """
You are an **Android GPU & Graphics Expert**. Analyze this graphics processor data from **$appName** and provide comprehensive technical analysis with gaming and performance insights.

🎮 **GPU Data:**
$itemContent

**Please provide a detailed analysis with the following structure:**

1. **GPU Identification & Tier**
   - GPU model and architecture (Adreno, Mali, PowerVR, etc.)
   - Performance tier: Flagship, Mid-range, or Entry-level
   - Manufacturing process and generation
   - Release date and current market position

2. **GPU Architecture Analysis** (Technical Specifications)
   - **Core Configuration**: Shader cores, compute units, execution units
   - **Clock Speeds**: Base, boost, and sustained GPU frequencies
   - **Memory**: VRAM type, bandwidth, shared vs. dedicated
   - **API Support**: OpenGL ES, Vulkan, DirectX compatibility
   - **Features**: Ray tracing, variable rate shading, AI upscaling (if applicable)

3. **Performance Characteristics**
   - **Gaming Performance**: Expected frame rates for different game genres
   - **Resolution Support**: 1080p, 1440p, 4K capabilities
   - **Video Playback**: Codec support (H.264, H.265, VP9, AV1)
   - **UI Rendering**: Smoothness, frame times, refresh rate support
   - **Compute Performance**: GPU compute capabilities for non-gaming tasks

4. **Real-World Gaming Analysis**
   - **Game Compatibility**: What games can it run?
   - **Settings Recommendations**: Low/Medium/High/Ultra settings for popular games
   - **Frame Rate Expectations**: 30/60/90/120 FPS capabilities
   - **Game Genres**: Best suited for casual, mid-range, or demanding games
   - **Limitations**: What games/settings will struggle

5. **Video & Media Performance**
   - **Video Playback**: 4K, 8K, HDR support and performance
   - **Video Encoding**: Recording and streaming capabilities
   - **Photo Processing**: Image editing, filters, AI enhancements
   - **AR/VR**: Augmented and virtual reality capabilities (if applicable)
   - **Display Output**: External display support and capabilities

6. **Comparison & Context**
   - How does it compare to similar-tier GPUs?
   - How does it compare to current flagship GPUs?
   - What are the main competitors?
   - Is it still competitive or outdated?
   - Performance per watt efficiency

7. **Optimization Recommendations**
   - **Game Settings**: Optimal graphics settings for performance vs. quality
   - **System Settings**: GPU-related system optimizations
   - **Thermal Management**: How to prevent GPU throttling
   - **Battery Impact**: How to balance graphics quality with battery life
   - **Driver Updates**: Importance of keeping GPU drivers updated

**Format:** Include specific frame rates, resolutions, and game examples. Use technical terms with brief explanations. Provide practical gaming recommendations with specific titles and settings.

Generated by **$appName** 📱
""".trimIndent()
            
            "display" -> """
You are an **Android Display & Screen Expert**. Analyze this display data from **$appName** and provide comprehensive display analysis with optimization recommendations.

📺 **Display Data:**
$itemContent

**Please provide a detailed analysis with the following structure:**

1. **Display Specifications** (Technical Details)
   - **Resolution**: Native resolution (e.g., 1080p, 1440p, 4K) and pixel count
   - **Pixel Density**: PPI (pixels per inch) and sharpness assessment
   - **Refresh Rate**: Hz (60/90/120/144) and adaptive refresh capabilities
   - **Panel Type**: LCD, OLED, AMOLED, IPS, etc. and characteristics
   - **Size**: Screen dimensions and aspect ratio
   - **Brightness**: Peak and typical brightness (nits)

2. **Color & Image Quality**
   - **Color Gamut**: sRGB, DCI-P3, Adobe RGB coverage
   - **Color Accuracy**: Delta-E values, color calibration status
   - **Contrast Ratio**: Static and dynamic contrast
   - **HDR Support**: HDR10, HDR10+, Dolby Vision capabilities
   - **Color Temperature**: Warm/cool/neutral calibration

3. **Quality Assessment** (Comparative Analysis)
   - **Overall Quality**: How does it compare to current market standards?
   - **Strengths**: What are the display's best features?
   - **Weaknesses**: What are the limitations?
   - **Viewing Angles**: How well does it perform at angles?
   - **Outdoor Visibility**: Brightness and reflectivity in sunlight

4. **Battery Impact Analysis**
   - **Power Consumption**: How much battery does the display use?
   - **Refresh Rate Impact**: Battery drain at 60Hz vs. 120Hz
   - **Brightness Impact**: Battery consumption at different brightness levels
   - **OLED vs. LCD**: Power efficiency characteristics
   - **Always-On Display**: Battery impact (if applicable)
   - **Estimated Battery Savings**: How much battery different settings save

5. **Optimization Recommendations** (Settings Guide)
   - **For Battery Life**: Optimal settings to maximize battery
     - Refresh rate: 60Hz vs. higher rates
     - Brightness: Auto vs. manual, recommended levels
     - Resolution: Lower resolution options (if available)
     - Dark mode: When to use for battery savings
   - **For Visual Quality**: Settings for best image quality
     - Color mode: Vivid, Natural, sRGB recommendations
     - Brightness: Optimal levels for different environments
     - HDR: When to enable/disable
   - **For Eye Comfort**: Settings to reduce eye strain
     - Blue light filter: Night mode, blue light reduction
     - Brightness: Comfortable levels for different times
     - Dark mode: Benefits for eye health
     - Font size and scaling: Readability optimization

6. **Display Features & Capabilities**
   - **Adaptive Features**: Auto-brightness, adaptive refresh rate
   - **Special Modes**: Reading mode, outdoor mode, etc.
   - **Touch Sensitivity**: Touch response and accuracy
   - **Protection**: Screen protection type (Gorilla Glass, etc.)
   - **Notch/Hole-Punch**: Impact on display area and usage

7. **Comparison & Context**
   - How does it compare to similar-tier devices?
   - How does it compare to current flagship displays?
   - Market positioning: Is it competitive or outdated?
   - Value assessment: Quality for price point

**Format:** Include specific numbers (PPI, nits, Hz, etc.) and percentages. Provide clear setting recommendations with expected battery impact. Balance technical accuracy with practical advice.

Generated by **$appName** 📱
""".trimIndent()
            
            "camera" -> """
You are an **Android Privacy & Permissions Expert**. Analyze this camera/microphone access data from **$appName** and provide comprehensive privacy and security assessment.

📷 **Camera/Microphone Access Log:**
$itemContent

**Please provide a detailed analysis with the following structure:**

1. **Access Log Analysis** (Detailed Review)
   - **Camera Access**: Which apps accessed the camera, when, and frequency
   - **Microphone Access**: Which apps accessed the mic, when, and frequency
   - **Speaker/Flashlight**: Apps using audio output or flashlight
   - **Access Patterns**: Normal usage vs. suspicious patterns
   - **Timeline**: Recent access history and frequency

2. **Privacy Risk Assessment** (Security Analysis)
   - **Risk Level**: Overall privacy risk (Low/Medium/High/Critical)
   - **Suspicious Apps**: Apps with unusual or excessive access
   - **Legitimate vs. Questionable**: Which accesses are normal vs. concerning
   - **Background Access**: Apps accessing camera/mic when not in use
   - **Permission Abuse**: Apps requesting permissions they don't need

3. **App-by-App Analysis** (Detailed Breakdown)
   - **System Apps**: Camera, phone, messaging apps (expected access)
   - **Social Media**: Instagram, Snapchat, TikTok (expected but monitor)
   - **Video Calling**: Zoom, Teams, Meet (expected during calls)
   - **Suspicious Apps**: Apps that shouldn't need camera/mic access
   - **Unknown Apps**: Apps with unclear reasons for access

4. **Privacy Implications** (What This Means)
   - **Data Collection**: What information can apps collect?
   - **Potential Misuse**: How could this data be misused?
   - **Location Tracking**: Can camera/mic access reveal location?
   - **Surveillance Risks**: Risk of unauthorized monitoring
   - **Data Sharing**: Could this data be shared with third parties?

5. **Permission Management** (Actionable Steps)
   - **Review Permissions**: How to check current permissions
   - **Revoke Access**: Step-by-step to remove permissions
   - **Permission Types**: Always, Only while using app, Ask every time
   - **Recommended Settings**: Optimal permission settings for each app
   - **System Settings**: How to manage permissions globally

6. **Best Practices** (Privacy Protection)
   - **Permission Principles**: Only grant permissions when necessary
   - **Regular Audits**: How often to review permissions
   - **App Selection**: How to choose privacy-respecting apps
   - **Physical Security**: Camera/mic covers and when to use them
   - **Monitoring**: How to detect unauthorized access

7. **Warning Signs & Red Flags**
   - **Suspicious Patterns**: What indicates malicious behavior
   - **Unauthorized Access**: Signs of hacking or spyware
   - **Excessive Access**: Apps accessing too frequently
   - **Background Access**: Camera/mic active when app is closed
   - **When to Take Action**: Immediate steps if suspicious activity detected

8. **Immediate Actions** (If Concerns Found)
   - **Critical Issues**: Steps to take if spyware detected
   - **Permission Revocation**: Which apps to immediately restrict
   - **App Removal**: Apps to uninstall if suspicious
   - **Security Scan**: Additional security measures to take
   - **Data Protection**: How to protect existing data

**Format:** Prioritize by risk level. List specific app names and access patterns. Provide clear, step-by-step instructions for permission management. Explain privacy implications in clear terms.

Generated by **$appName** 📱
""".trimIndent()
            
            "location" -> """
You are an **Android Location Services Expert**. Analyze this GPS and location data from **$appName** and provide comprehensive location services analysis with privacy and optimization insights.

📍 **Location Services Data:**
$itemContent

**Please provide a detailed analysis with the following structure:**

1. **GPS Performance Assessment** (Accuracy & Reliability)
   - **GPS Accuracy**: Current accuracy level (meters/feet)
   - **Satellite Connection**: Number of satellites connected
   - **Signal Strength**: GPS signal quality
   - **Location Methods**: GPS, Wi-Fi, Cell tower, Bluetooth (which are active)
   - **Reliability**: How consistently accurate is location tracking?
   - **Indoor vs. Outdoor**: Performance in different environments

2. **Location Access Analysis** (Privacy Review)
   - **Apps Using Location**: Complete list of apps with location access
   - **Access Frequency**: How often each app accesses location
   - **Access Type**: Always, Only while using app, Ask every time
   - **Background Access**: Apps accessing location when not in use
   - **Location History**: Is location history being saved?

3. **Privacy Risk Assessment** (Security Analysis)
   - **Risk Level**: Overall privacy risk (Low/Medium/High)
   - **Data Collection**: What location data is being collected?
   - **Third-Party Sharing**: Could location be shared with advertisers?
   - **Tracking Capabilities**: Can apps track movements over time?
   - **Sensitive Locations**: Home, work, frequent places exposure
   - **Stalking Risks**: Potential for location-based stalking

4. **Battery Impact Analysis** (Power Consumption)
   - **Battery Drain**: How much battery does location services use?
   - **High Accuracy vs. Battery Saver**: Trade-offs
   - **Background Location**: Impact of background location access
   - **GPS vs. Network Location**: Power consumption comparison
   - **Estimated Battery Savings**: How much battery different settings save

5. **App-by-App Location Analysis** (Detailed Breakdown)
   - **Maps/Navigation**: Google Maps, Waze (expected, but review settings)
   - **Weather Apps**: Weather services (may not need precise location)
   - **Social Media**: Instagram, Facebook, Twitter (review necessity)
   - **Shopping Apps**: Retail apps (often unnecessary)
   - **Fitness Apps**: Step counters, workout apps (may need location)
   - **Suspicious Apps**: Apps that shouldn't need location

6. **Optimization Recommendations** (Settings Guide)
   - **Location Mode**: High accuracy vs. Battery saver vs. Device only
   - **App Permissions**: Recommended settings for each app category
   - **Background Location**: When to allow/restrict background access
   - **Location History**: Whether to enable/disable location history
   - **Google Location Services**: Settings for Google's location features
   - **Emergency Location**: How to maintain emergency location services

7. **Privacy Best Practices** (Protection Strategies)
   - **Permission Principles**: Only grant location when necessary
   - **Regular Audits**: How often to review location permissions
   - **Location Sharing**: How to safely share location with trusted contacts
   - **Public Wi-Fi**: Location privacy on public networks
   - **Location Spoofing**: When and why to use (if needed)

8. **Security Considerations** (Safety Measures)
   - **Stalking Protection**: How to prevent location-based stalking
   - **Home Address Privacy**: Protecting sensitive location data
   - **Travel Privacy**: Location privacy while traveling
   - **Children's Privacy**: Location settings for family devices
   - **Emergency Services**: Maintaining 911/location services for emergencies

**Format:** Include specific app names, accuracy measurements, and battery impact percentages. Prioritize privacy concerns. Provide clear, step-by-step instructions for optimizing settings. Balance privacy with functionality needs.

Generated by **$appName** 📱
""".trimIndent()
            
            "network" -> """
You are an **Android Network & Connectivity Expert**. Analyze this network data from **$appName** and provide comprehensive network analysis with performance and security insights.

📶 **Network Data:**
$itemContent

**Please provide a detailed analysis with the following structure:**

1. **Network Connection Assessment** (Current Status)
   - **Connection Type**: Wi-Fi, 4G LTE, 5G, 3G, etc.
   - **Network Standard**: Wi-Fi (802.11 a/b/g/n/ac/ax), Cellular (LTE-A, 5G SA/NSA)
   - **Signal Strength**: Current signal quality (dBm, bars, percentage)
   - **Connection Stability**: Is connection stable or dropping?
   - **Speed Test Results**: Download/upload speeds, latency, jitter (if available)

2. **Network Performance Analysis** (Quality Metrics)
   - **Download Speed**: Current speed and what it's good for
   - **Upload Speed**: Current speed and implications
   - **Latency/Ping**: Response time and impact on gaming/video calls
   - **Jitter**: Connection consistency and stability
   - **Packet Loss**: Data loss percentage (if available)
   - **Throughput**: Actual usable bandwidth

3. **Network Capabilities** (What It Can Handle)
   - **Streaming**: Can it handle 4K, 1080p, 720p video streaming?
   - **Video Calls**: Quality of video calls (HD, SD, etc.)
   - **Gaming**: Online gaming performance and latency
   - **File Downloads**: Speed for large file downloads
   - **Multiple Devices**: Can it handle multiple connected devices?

4. **Security Analysis** (Network Safety)
   - **Wi-Fi Security**: WPA2, WPA3, WEP, or open network
   - **Encryption**: Is traffic encrypted?
   - **VPN Status**: Is VPN active? Recommendations
   - **DNS Security**: DNS server security (default vs. secure DNS)
   - **Public Wi-Fi Risks**: Security concerns if on public network
   - **Man-in-the-Middle**: Risk of interception attacks

5. **Network Configuration** (Technical Details)
   - **IP Address**: Public and private IP addresses
   - **DNS Servers**: Current DNS configuration
   - **Gateway**: Router/gateway information
   - **Subnet**: Network configuration
   - **MAC Address**: Device network identifier
   - **MTU Size**: Maximum transmission unit (if available)

6. **Connectivity Issues** (Troubleshooting)
   - **Identified Problems**: Any connection issues detected?
   - **Speed Issues**: Slow speeds, causes and solutions
   - **Connection Drops**: Intermittent connectivity problems
   - **Range Issues**: Wi-Fi range and signal strength problems
   - **Interference**: Sources of network interference

7. **Optimization Recommendations** (Performance Improvements)
   - **Wi-Fi Optimization**: Channel selection, band switching (2.4GHz vs. 5GHz)
   - **DNS Optimization**: Switching to faster DNS (Cloudflare, Google DNS)
   - **Network Settings**: Android network settings to optimize
   - **Router Settings**: Router configuration recommendations
   - **Positioning**: Optimal device/router positioning
   - **Bandwidth Management**: Prioritizing important traffic

8. **Security Hardening** (Protection Measures)
   - **Secure DNS**: How to enable DNS-over-HTTPS (DoH)
   - **VPN Recommendations**: When and which VPN to use
   - **Public Wi-Fi Safety**: How to stay safe on public networks
   - **Network Monitoring**: How to detect suspicious network activity
   - **Firewall**: Network firewall settings and recommendations

9. **Comparison & Context**
   - How does this network compare to typical speeds?
   - Is this connection good for my usage needs?
   - What are the limitations of this network?
   - Should I upgrade my plan or equipment?

**Format:** Include specific speeds (Mbps), latency (ms), signal strength (dBm), and percentages. Provide clear troubleshooting steps. Prioritize security concerns. Balance technical accuracy with practical advice.

Generated by **$appName** 📱
""".trimIndent()
            
            "developer" -> """
You are an **Android Developer Tools Expert**. Analyze this developer options and root status data from **$appName** and provide comprehensive security and functionality analysis.

⚙️ **Developer Settings Data:**
$itemContent

**Please provide a detailed analysis with the following structure:**

1. **Current Status Assessment** (What's Enabled)
   - **Developer Options**: Enabled or disabled?
   - **USB Debugging**: Status and security implications
   - **Root Access**: Is device rooted? Bootloader unlocked?
   - **OEM Unlock**: Is bootloader unlock allowed?
   - **Other Developer Features**: ADB, mock locations, etc.

2. **Feature Explanation** (What Each Does)
   - **Developer Options**: What is this menu and who needs it?
   - **USB Debugging**: What it does, ADB access, security risks
   - **Root Access**: What rooting means, superuser permissions
   - **Bootloader Unlock**: What this means, custom ROM support
   - **ADB (Android Debug Bridge)**: Developer tool access
   - **Other Settings**: Mock locations, stay awake, etc.

3. **Security Risk Analysis** (Detailed Assessment)
   - **Risk Level**: Overall security risk (Low/Medium/High/Critical)
   - **USB Debugging Risks**: 
     - Unauthorized computer access to device
     - Data extraction without permission
     - Malicious ADB commands
     - When connected to untrusted computers
   - **Root Access Risks**:
     - Bypass of Android security model
     - Malware with system-level access
     - Banking app incompatibility
     - SafetyNet/Play Integrity failures
     - Warranty voiding
   - **Bootloader Unlock Risks**:
     - Ability to flash custom firmware
     - Potential for malicious firmware
     - Security feature bypass

4. **Benefits & Use Cases** (When It's Needed)
   - **Developer Options**: Performance monitoring, debugging, advanced settings
   - **USB Debugging**: App development, device management, advanced troubleshooting
   - **Root Access**: System customization, advanced apps, removing bloatware
   - **Bootloader Unlock**: Custom ROMs, system modifications
   - **Who Needs These**: Developers, power users, specific use cases

5. **Recommendations** (Actionable Guidance)
   - **For Regular Users**: Should these be enabled? (Generally: NO)
   - **For Developers**: Safe usage practices
   - **For Power Users**: When it's acceptable and how to stay safe
   - **Immediate Actions**: What to disable if not needed
   - **Conditional Enablement**: When to temporarily enable features

6. **Security Hardening** (If Enabled)
   - **USB Debugging Safety**: Only enable when needed, disable after use
   - **Authorized Computers**: How to manage trusted devices
   - **Root Safety**: If rooted, how to maintain security
   - **App Restrictions**: Apps that won't work with root/unlock
   - **Monitoring**: How to detect unauthorized access

7. **Step-by-Step Security Actions**
   - **Disable Developer Options**: How to turn off if not needed
   - **Disable USB Debugging**: How to secure ADB access
   - **Remove Root**: How to unroot if security is concern
   - **Relock Bootloader**: How to secure bootloader (if possible)
   - **Verify Security**: How to check if device is secure

8. **Best Practices** (Safe Usage Guidelines)
   - **Principle of Least Privilege**: Only enable what you need
   - **Temporary Enablement**: Enable only when actively using
   - **Trusted Sources**: Only connect to trusted computers
   - **Regular Audits**: Periodically review developer settings
   - **Security Updates**: Keep device updated if using developer features
   - **Backup**: Always backup before making system changes

9. **Impact on Device Functionality**
   - **App Compatibility**: Apps that won't work (banking, payment apps)
   - **Security Features**: SafetyNet, Play Integrity, device attestation
   - **Warranty**: Manufacturer warranty implications
   - **Updates**: How developer features affect system updates
   - **Performance**: Impact on device performance and stability

**Format:** Prioritize security risks. Clearly distinguish between "safe for developers" vs. "risky for regular users." Provide specific, actionable steps to secure the device. Explain technical terms clearly.

Generated by **$appName** 📱
""".trimIndent()
            
            "performance" -> """
You are an **Android Performance Expert**. Analyze this FPS and frame drop data from **$appName** and provide comprehensive performance analysis with optimization strategies.

⚡ **Performance Data:**
$itemContent

**Please provide a detailed analysis with the following structure:**

1. **Performance Metrics Assessment** (Current Status)
   - **Frame Rate (FPS)**: Current average, min, max frame rates
   - **Frame Drops**: Frequency and severity of frame drops
   - **Smoothness**: Overall UI smoothness rating (Excellent/Good/Fair/Poor)
   - **Frame Time**: Average frame rendering time (ms)
   - **Jank**: Stuttering and lag incidents
   - **Performance Score**: Overall performance rating

2. **Performance Analysis** (Detailed Breakdown)
   - **UI Performance**: Home screen, app switching, scrolling smoothness
   - **App Launch Times**: How quickly apps open
   - **Multitasking**: Performance with multiple apps running
   - **Gaming Performance**: Frame rates in games (if applicable)
   - **Video Playback**: Smoothness of video playback
   - **Animations**: Transition and animation smoothness

3. **Issue Identification** (Problem Analysis)
   - **Performance Bottlenecks**: What's causing slowdowns?
   - **Frame Drop Causes**: CPU throttling, GPU limitations, memory issues
   - **Thermal Throttling**: Is device overheating and reducing performance?
   - **Background Processes**: Apps consuming resources in background
   - **Memory Pressure**: RAM usage and memory constraints
   - **Storage Impact**: How low storage affects performance

4. **Comparison & Context** (Benchmarking)
   - **Optimal Performance**: What should performance be for this device?
   - **Hardware Capabilities**: What is this device capable of?
   - **Current vs. Optimal**: Gap between current and ideal performance
   - **Similar Devices**: How does it compare to similar phones?
   - **Age Impact**: How device age affects performance

5. **Hardware vs. Software Limitations** (Root Cause Analysis)
   - **Hardware Limits**: What can't be improved (CPU, GPU, RAM limitations)
   - **Software Issues**: What can be fixed (app optimization, system settings)
   - **Thermal Constraints**: How heat limits sustained performance
   - **Battery Impact**: How battery health affects performance
   - **Upgrade Considerations**: When hardware upgrade is needed

6. **Optimization Strategies** (Actionable Improvements)
   - **System Settings**: Performance mode, battery optimization settings
   - **App Management**: Close background apps, restrict battery usage
   - **Memory Management**: Clear RAM, manage app memory usage
   - **Storage Optimization**: Free up storage, clear cache
   - **Thermal Management**: Reduce heat, prevent throttling
   - **Developer Options**: Performance monitoring and GPU rendering settings

7. **Specific Performance Fixes** (Step-by-Step)
   - **Immediate Actions**: Quick fixes to improve performance now
   - **App Optimization**: Which apps to optimize or uninstall
   - **System Optimization**: Settings to change for better performance
   - **Cache Clearing**: How to clear system and app caches
   - **Factory Reset Consideration**: When reset might help (last resort)

8. **Maintenance & Monitoring** (Ongoing Care)
   - **Regular Maintenance**: How often to optimize performance
   - **Performance Monitoring**: How to track performance over time
   - **Warning Signs**: Indicators of performance degradation
   - **Preventive Measures**: How to maintain good performance
   - **Update Impact**: How system updates affect performance

**Format:** Include specific FPS numbers, frame drop percentages, and performance scores. Distinguish between fixable software issues and hardware limitations. Provide clear, prioritized action items.

Generated by **$appName** 📱
""".trimIndent()
            
            "temperature" -> """
You are an **Android Thermal Management Expert**. Analyze this temperature data from **$appName** and provide comprehensive thermal analysis with cooling strategies.

🌡️ **Temperature Data:**
$itemContent

**Please provide a detailed analysis with the following structure:**

1. **Temperature Status Assessment** (Current State)
   - **CPU Temperature**: Current reading and status
   - **Battery Temperature**: Current reading and status
   - **GPU Temperature**: Current reading and status (if available)
   - **Overall Thermal State**: Normal, Warm, Hot, or Critical
   - **Ambient Temperature**: Environmental temperature impact

2. **Temperature Range Analysis** (Normal vs. Dangerous)
   - **Normal Ranges**: 
     - CPU: 30-45°C (idle), 45-60°C (load)
     - Battery: 20-35°C (optimal), 35-40°C (warm)
     - GPU: 40-55°C (normal load)
   - **Concerning Ranges**:
     - CPU: >70°C (throttling), >80°C (dangerous)
     - Battery: >40°C (warm), >45°C (hot), >50°C (critical)
     - GPU: >70°C (throttling), >85°C (dangerous)
   - **Critical Thresholds**: When immediate action is required

3. **Heat Source Analysis** (What's Causing Heat)
   - **CPU Load**: High processing tasks causing heat
   - **GPU Usage**: Graphics-intensive apps or games
   - **Charging**: Fast charging, wireless charging heat generation
   - **Background Processes**: Apps running in background
   - **Environmental**: Direct sunlight, hot environment
   - **Case/Protection**: Phone case trapping heat
   - **Network Activity**: Intensive Wi-Fi/cellular data usage

4. **Thermal Throttling Analysis** (Performance Impact)
   - **Is Throttling Active**: Is device reducing performance due to heat?
   - **Throttling Impact**: How much performance is being reduced?
   - **Sustained Performance**: Can device maintain performance under load?
   - **Cooling Effectiveness**: How well is device managing heat?

5. **Immediate Cooling Actions** (If Device Is Hot)
   - **Stop Intensive Tasks**: Close games, video apps, etc.
   - **Remove Case**: Take off phone case to improve heat dissipation
   - **Stop Charging**: Pause charging if device is hot
   - **Reduce Brightness**: Lower screen brightness
   - **Move to Cooler Location**: Avoid direct sunlight, move to shade
   - **Airflow**: Improve ventilation around device
   - **Cool Down Period**: How long to wait before using again

6. **Cooling Strategies** (Prevention & Management)
   - **App Management**: Close background apps, restrict intensive apps
   - **Charging Habits**: Avoid fast charging when device is warm
   - **Usage Patterns**: Take breaks during intensive tasks
   - **Environmental Control**: Avoid hot environments, direct sunlight
   - **Case Selection**: Use cases that don't trap heat
   - **Cooling Accessories**: External cooling solutions (if applicable)

7. **Risk Assessment** (Dangers of Overheating)
   - **Battery Degradation**: How heat accelerates battery aging
   - **Performance Impact**: Throttling and reduced performance
   - **Component Damage**: Risk to CPU, battery, display
   - **Safety Risks**: Battery swelling, fire risk (extreme cases)
   - **Data Loss**: Risk of crashes and data corruption
   - **Long-term Effects**: Permanent damage from sustained overheating

8. **Prevention Tips** (Long-term Care)
   - **Usage Guidelines**: How to prevent overheating during normal use
   - **Charging Best Practices**: Optimal charging to minimize heat
   - **App Selection**: Avoid apps known to cause overheating
   - **Regular Maintenance**: Keep device clean, update software
   - **Monitoring**: How to track temperature over time
   - **Warning Signs**: Early indicators of thermal issues

9. **Technical Thermal Management** (Advanced)
   - **Thermal Design**: Device's cooling system capabilities
   - **Heat Dissipation**: How device transfers heat
   - **Thermal Interface**: Heat transfer efficiency
   - **Cooling Solutions**: Active vs. passive cooling
   - **Manufacturer Limits**: Built-in thermal protection features

**Format:** Include specific temperature readings (°C/°F), temperature ranges, and time estimates. Prioritize immediate safety concerns. Provide clear action items ranked by urgency. Explain risks in practical terms.

Generated by **$appName** 📱
""".trimIndent()
            
            "sensors" -> """
You are an **Android Sensors Expert**. Analyze this sensor data from **$appName** and provide comprehensive sensor analysis with functionality and troubleshooting insights.

📡 **Sensor Data:**
$itemContent

**Please provide a detailed analysis with the following structure:**

1. **Sensor Inventory** (Complete List)
   - **Available Sensors**: Complete list of detected sensors
   - **Sensor Types**: Accelerometer, gyroscope, magnetometer, proximity, light, etc.
   - **Sensor Count**: How many of each type (if multiple)
   - **Sensor Status**: Working, not detected, or malfunctioning
   - **Sensor Specifications**: Accuracy, range, resolution (if available)

2. **Sensor Functions** (What Each Does)
   - **Motion Sensors**: Accelerometer, gyroscope, rotation vector
     - Screen rotation, step counting, gesture recognition
   - **Environmental Sensors**: Light, pressure, humidity, temperature
     - Auto-brightness, weather apps, environmental monitoring
   - **Position Sensors**: Magnetometer, GPS, proximity
     - Compass, navigation, screen on/off during calls
   - **Health Sensors**: Heart rate, SpO2 (if available)
     - Fitness tracking, health monitoring
   - **Other Sensors**: Fingerprint, face recognition, etc.

3. **Feature Impact Analysis** (How Sensors Enable Features)
   - **Screen Rotation**: Auto-rotate functionality
   - **Step Counting**: Fitness and health apps
   - **Gaming**: Motion controls, tilt steering
   - **Navigation**: Compass, GPS accuracy enhancement
   - **Camera**: Image stabilization, orientation
   - **Smart Features**: Auto-brightness, proximity detection
   - **AR/VR**: Augmented and virtual reality capabilities
   - **Security**: Biometric authentication

4. **Missing or Malfunctioning Sensors** (Issues Identified)
   - **Missing Sensors**: Which sensors are not detected?
   - **Impact**: What features won't work without these sensors?
   - **Malfunctioning Sensors**: Which sensors aren't working properly?
   - **Causes**: Hardware failure, software issues, calibration problems
   - **Severity**: Critical, important, or minor impact

5. **Sensor Quality Assessment** (Performance Analysis)
   - **Accuracy**: How accurate are sensor readings?
   - **Calibration**: Do sensors need calibration?
   - **Response Time**: How quickly do sensors respond?
   - **Stability**: Are readings consistent or erratic?
   - **Comparison**: How do sensors compare to typical devices?

6. **Troubleshooting** (Problem Resolution)
   - **Calibration**: How to calibrate sensors (compass, gyroscope, etc.)
   - **Software Fixes**: App updates, system updates, cache clearing
   - **Hardware Issues**: When sensor problems indicate hardware failure
   - **Reset Options**: Factory reset consideration (last resort)
   - **Diagnostic Tools**: How to test sensor functionality

7. **Sensor Best Practices** (Optimization)
   - **Calibration Schedule**: When to recalibrate sensors
   - **App Permissions**: Which apps need sensor access
   - **Battery Impact**: How sensors affect battery life
   - **Privacy Considerations**: What data sensors can reveal
   - **Maintenance**: How to keep sensors working optimally

8. **Advanced Sensor Features** (Capabilities)
   - **Sensor Fusion**: How multiple sensors work together
   - **Machine Learning**: AI-enhanced sensor features
   - **Context Awareness**: How sensors enable smart features
   - **Power Efficiency**: Low-power sensor modes
   - **Future Sensors**: Emerging sensor technologies

**Format:** List specific sensor names and their functions. Clearly identify missing or broken sensors and their impact. Provide step-by-step troubleshooting instructions. Explain sensor functions in practical terms.

Generated by **$appName** 📱
""".trimIndent()
            
            "ai" -> """
You are an **Android AI & Neural Processing Expert**. Analyze this AI inference support data from **$appName** and provide comprehensive AI capability analysis with practical insights.

🤖 **AI Support Data:**
$itemContent

**Please provide a detailed analysis with the following structure:**

1. **AI Capability Assessment** (Current Status)
   - **On-Device AI**: Can device run AI models locally?
   - **Neural Processing Unit (NPU)**: Presence and capabilities
   - **AI Acceleration**: Hardware AI acceleration support
   - **AI Framework Support**: TensorFlow Lite, ONNX, etc.
   - **Overall AI Tier**: Flagship AI, Mid-range AI, or Limited AI support

2. **Hardware AI Features** (Technical Specifications)
   - **NPU/DSP**: Neural processing unit or digital signal processor
   - **AI Cores**: Dedicated AI processing cores
   - **Tensor Cores**: GPU-based AI acceleration (if applicable)
   - **Memory**: AI model memory requirements and support
   - **Performance**: AI inference speed and efficiency
   - **Power Efficiency**: AI processing power consumption

3. **AI Feature Support** (Available Capabilities)
   - **On-Device AI Models**: Can run AI without internet
   - **Real-Time AI**: Live AI processing (camera, voice, etc.)
   - **AI Photography**: Scene detection, night mode, portrait mode
   - **AI Voice**: Voice assistants, speech recognition
   - **AI Translation**: Real-time language translation
   - **AI Gaming**: Game enhancement, upscaling
   - **AI Productivity**: Text generation, summarization, etc.

4. **Performance Analysis** (How Well AI Works)
   - **Inference Speed**: How fast AI tasks complete
   - **Model Support**: What AI model sizes can run?
   - **Accuracy**: AI processing quality and accuracy
   - **Battery Impact**: How AI processing affects battery life
   - **Thermal Impact**: Does AI processing cause overheating?
   - **Limitations**: What AI tasks struggle or can't run?

5. **AI App Compatibility** (What Apps Work)
   - **ChatGPT/Claude**: Can run on-device versions?
   - **Image Generation**: AI image creation apps
   - **Voice Assistants**: Google Assistant, Bixby, etc.
   - **Camera AI**: AI-enhanced photography apps
   - **Productivity AI**: AI writing, translation, summarization apps
   - **Gaming AI**: AI-enhanced games
   - **Specific Recommendations**: Apps that work well on this device

6. **Comparison & Context** (Market Position)
   - **Similar Devices**: How does it compare to similar phones?
   - **Flagship Comparison**: How does it compare to top AI phones?
   - **Market Standards**: Is AI support competitive or outdated?
   - **Generation**: Is this current-gen or older AI hardware?
   - **Value Assessment**: AI capabilities for price point

7. **Use Cases & Recommendations** (Practical Applications)
   - **Best Use Cases**: What AI tasks work well on this device
   - **Limited Use Cases**: What AI tasks are slow or limited
   - **Not Recommended**: What AI tasks won't work well
   - **App Recommendations**: Specific AI apps to try
   - **Optimization Tips**: How to get best AI performance

8. **Upgrade Considerations** (Future Planning)
   - **Current Sufficiency**: Is AI support good enough for needs?
   - **Upgrade Benefits**: What would better AI hardware provide?
   - **When to Upgrade**: Signs that upgrade is needed
   - **Future-Proofing**: How long will current AI support last?
   - **Cost-Benefit**: Is upgrading for AI worth it?

9. **AI Development & Testing** (For Developers)
   - **Development Support**: Tools and frameworks available
   - **Model Deployment**: How to deploy AI models
   - **Performance Testing**: How to benchmark AI performance
   - **Optimization**: How to optimize AI models for this device

**Format:** Include specific AI model support, inference speeds, and performance metrics. Provide clear examples of AI apps that work well. Balance technical accuracy with practical recommendations. Help users understand what AI features they can actually use.

Generated by **$appName** 📱
""".trimIndent()
            
            "power" -> {
                // Special handling for Camera Power Test
                if (itemTitle.contains("Camera Power", ignoreCase = true)) {
                    generateAdvancedCameraPowerTestPrompt(itemTitle, itemContent, appName)
                } else {
                    """
You are an **Android Power Consumption & Battery Efficiency Expert**. Analyze this power consumption data from **$appName** and provide comprehensive power analysis with optimization strategies.

⚡ **Power Consumption Data:**
$itemContent

**Please provide a detailed analysis with the following structure:**

1. **Power Consumption Assessment** (Current Status)
   - **Total Power**: Overall device power consumption (Watts)
   - **Component Breakdown**: Power consumption by component (CPU, GPU, display, network, etc.)
   - **Activity Impact**: Power consumption by activity or app
   - **Overall Efficiency**: Is power usage efficient, average, or high?

2. **Technical Breakdown** (Detailed Analysis)
   - **Baseline Power**: Device power consumption when idle
   - **Peak Power**: Maximum power consumption under load
   - **Power Trends**: Is power consumption increasing, stable, or decreasing?
   - **Power per Component**: Detailed breakdown of each component's consumption

3. **Battery Impact Analysis**
   - **Estimated Battery Drain**: Percentage of battery consumed per hour
   - **Battery Life Estimate**: Expected battery life at current consumption
   - **Factors Increasing Drain**: What activities/components increase power usage most?
   - **Comparison**: How does this compare to typical power usage on similar devices?

4. **Optimization Strategies** (Actionable Recommendations)
   - **System Settings**:
     - Performance mode vs. battery saver mode
     - Display settings (brightness, refresh rate, resolution)
     - Background app restrictions
   - **Component-Specific Tips**:
     - CPU/GPU optimization
     - Network optimization (Wi-Fi vs. cellular)
     - Display optimization
   - **Usage Patterns**:
     - Activities to avoid for battery preservation
     - Optimal usage patterns for power efficiency

5. **Performance vs. Power Trade-offs**
   - Explain the balance between performance and battery life
   - When is it acceptable to prioritize power over performance?
   - Settings to maximize battery life without significant performance loss

6. **Long-Term Considerations**
   - How does power consumption affect overall battery health?
   - Monitoring power usage over time
   - Warning signs of excessive power consumption

**Format:** Use clear headings, bullet points, and specific numbers/metrics (Watts, percentages, hours). Balance technical depth with practical, actionable insights.

Generated by **$appName** 📱
""".trimIndent()
                }
            }
            
            "health" -> {
                if (itemTitle.contains("Smart Recommendations", ignoreCase = true) || itemTitle.contains("Improvement", ignoreCase = true)) {
                    """
You are a **Phone Health Optimization Expert**. Analyze these health improvement recommendations from **$appName** and provide a comprehensive, prioritized action plan.

🏥 **Health Recommendations Data:**
$itemContent

**Please provide a detailed analysis with the following structure:**

1. **Recommendation Priority Assessment**
   - **Urgent**: Issues requiring immediate attention (if any)
   - **High Priority**: Quick wins that will significantly improve health score
   - **Medium Priority**: Important maintenance tasks
   - **Low Priority**: Optimizations for best performance
   - **Priority Ranking**: Order recommendations by impact and urgency

2. **Detailed Recommendation Analysis**
   - **Each Recommendation**: What it means, why it matters, and expected impact
   - **Health Score Impact**: How much will each recommendation improve the score?
   - **Difficulty Level**: Easy, Moderate, or Advanced
   - **Time Required**: How long will each action take?
   - **Risk Assessment**: Any potential downsides or risks?

3. **Implementation Strategy** (Step-by-Step Plan)
   - **Phase 1** (Do First): Immediate actions for urgent issues
   - **Phase 2** (This Week): High-priority recommendations
   - **Phase 3** (This Month): Medium-priority maintenance
   - **Phase 4** (Ongoing): Long-term optimizations
   - **Dependencies**: Which recommendations should be done together or in order?

4. **Expected Outcomes**
   - **Health Score Improvement**: Projected score after implementing recommendations
   - **Performance Impact**: How will this affect phone performance?
   - **Battery Impact**: Will this improve or affect battery life?
   - **User Experience**: How will this affect daily phone usage?

5. **Monitoring & Verification**
   - How to verify each recommendation was successful
   - When to re-scan to check improvements
   - Signs that recommendations are working
   - When to adjust or try alternative approaches

**Format:** Use clear headings, prioritize by urgency and impact, provide specific step-by-step instructions, and explain expected outcomes.

Generated by **$appName** 📱🏥
                    """.trimIndent()
                } else if (itemTitle.contains("Health History", ignoreCase = true) || itemTitle.contains("Daily Streak", ignoreCase = true)) {
                    """
You are a **Phone Health Trend Analyst**. Analyze this health history data from **$appName** and provide comprehensive trend analysis with predictive insights.

📊 **Health History Data:**
$itemContent

**Please provide a detailed analysis with the following structure:**

1. **Historical Trend Analysis**
   - **Score Progression**: How has the health score changed over time?
   - **Trend Direction**: Improving, declining, stable, or fluctuating?
   - **Rate of Change**: Fast improvement/decline, gradual, or steady?
   - **Pattern Recognition**: Any recurring patterns or cycles?
   - **Best vs. Current**: How does current score compare to best score?

2. **Streak Analysis**
   - **Daily Streak**: Current streak length and significance
   - **Streak Impact**: Does maintaining a streak correlate with better health?
   - **Streak Sustainability**: Is the current streak sustainable?
   - **Streak Benefits**: What are the benefits of maintaining daily checks?

3. **Score Context & Interpretation**
   - **Score Range**: What does the score range indicate?
   - **Score Stability**: Is the score consistent or highly variable?
   - **Score Trajectory**: Where is the score heading based on trends?
   - **Comparison**: How does this compare to typical device health scores?

4. **Predictive Insights** (Future Projections)
   - **Short-Term Forecast**: What to expect in the next week/month?
   - **Long-Term Projection**: Where will health be in 3-6 months if trends continue?
   - **Risk Factors**: What could cause health to decline?
   - **Improvement Potential**: What's the realistic improvement potential?

5. **Actionable Insights** (Based on History)
   - **What Worked**: Which periods showed improvement? What was different?
   - **What Didn't Work**: Which periods showed decline? What might have caused it?
   - **Lessons Learned**: Key insights from the history
   - **Recommendations**: What should be done differently based on trends?

6. **Health Maintenance Strategy**
   - **Optimal Check Frequency**: How often should health be checked?
   - **Maintenance Schedule**: When to perform maintenance tasks
   - **Warning Signs**: What trends indicate problems ahead?
   - **Preventive Measures**: How to prevent health decline

**Format:** Use clear headings, include specific dates and scores, identify clear trends, and provide actionable recommendations based on historical patterns.

Generated by **$appName** 📱🏥
                    """.trimIndent()
                } else {
                    """
You are a **Phone Health Expert**. Analyze this health report from **$appName** and provide a comprehensive, structured assessment that balances technical accuracy with practical insights.

📱 **Health Report Data:**
$itemContent

**Please provide a detailed analysis with the following structure:**

1. **Health Score Assessment** (Current Status)
   - Score interpretation: What does the number mean?
   - Overall condition: Excellent, Good, Fair, Poor, or Critical
   - Comparison to history: Is it improving, stable, or declining?
   - Key factors affecting the score

2. **Component Analysis** (What's Working & What's Not)
   - Battery health: Status, capacity, charging behavior
   - Storage: Available space, usage patterns, recommendations
   - Performance: Speed, responsiveness, app behavior
   - Security: Updates, vulnerabilities, protection status
   - Other critical components

3. **Trend Analysis** (Historical Context)
   - Daily streak: What does this indicate?
   - Score history: Patterns, improvements, or declines
   - Best score: How does current compare?
   - Predictive insights: What to expect if trends continue

4. **Improvement Recommendations** (Prioritized Action Plan)
   - **Urgent** (if any): Issues requiring immediate attention
   - **High Priority**: Quick wins that will improve score significantly
   - **Medium Priority**: Maintenance tasks for long-term health
   - **Low Priority**: Optimizations for best performance

5. **Maintenance Schedule** (Ongoing Care)
   - How often to scan/check health
   - When to perform maintenance tasks
   - Signs to watch for that indicate problems
   - Preventive measures to maintain good health

6. **Comparison & Context** (Market Standards)
   - How does this device compare to similar devices?
   - Is this score typical for this device age/model?
   - What's the expected lifespan based on current health?

**Format:** Use clear headings, bullet points, and specific numbers/metrics where relevant. Balance technical depth with practical, actionable insights.

Generated by **$appName** 📱🏥
                    """.trimIndent()
                }
            }
            
            "privacy" -> """
You are an **Android Privacy & Tracking Expert**. Analyze this privacy and tracking data from **$appName** and provide comprehensive privacy assessment with protection strategies.

🔒 **Privacy Data:**
$itemContent

**Please provide a detailed analysis with the following structure:**

1. **Privacy Risk Assessment** (Overall Status)
   - **Risk Level**: Low, Medium, High, or Critical
   - **Tracking Status**: Is device being tracked? By whom?
   - **Spyware Status**: Any malicious monitoring detected?
   - **Data Collection**: What data is being collected?
   - **Overall Privacy Score**: Excellent, Good, Fair, Poor, or Critical

2. **Tracking Analysis** (Detailed Breakdown)
   - **Ad Tracking**: Advertising ID, tracking cookies, cross-app tracking
   - **Analytics Tracking**: App analytics, usage statistics
   - **Location Tracking**: Location-based tracking and history
   - **Behavioral Tracking**: App usage patterns, browsing habits
   - **Cross-Platform Tracking**: Tracking across devices and services
   - **Third-Party Tracking**: Data sharing with advertisers, data brokers

3. **Spyware & Malicious Monitoring** (Security Threats)
   - **Spyware Detection**: Any malicious monitoring software found?
   - **Keyloggers**: Software recording keystrokes
   - **Screen Recording**: Unauthorized screen capture
   - **Call Recording**: Unauthorized call monitoring
   - **Data Theft**: Unauthorized data access or exfiltration
   - **Remote Access**: Unauthorized remote control capabilities

4. **Data Collection Analysis** (What's Being Collected)
   - **Personal Information**: Name, email, phone number collection
   - **Device Information**: Device ID, IMEI, serial number
   - **Usage Data**: App usage, browsing history, search queries
   - **Location Data**: GPS location, location history
   - **Biometric Data**: Fingerprint, face recognition data
   - **Communication Data**: Contacts, messages, call logs
   - **Media**: Photos, videos accessed by apps

5. **Privacy Violations Identified** (Specific Issues)
   - **Over-Permissive Apps**: Apps requesting unnecessary permissions
   - **Background Data Collection**: Apps collecting data when not in use
   - **Data Sharing**: Apps sharing data with third parties
   - **Unknown Tracking**: Tracking from unknown sources
   - **Privacy Policy Violations**: Apps not following stated privacy policies

6. **Protection Strategies** (Actionable Steps)
   - **Immediate Actions** (Critical):
     - Remove spyware/malicious apps immediately
     - Revoke suspicious permissions
     - Disable tracking IDs
   - **High Priority**:
     - Review and restrict app permissions
     - Disable ad personalization
     - Enable privacy-focused settings
   - **Medium Priority**:
     - Use privacy-focused apps and services
     - Enable VPN for additional protection
     - Review privacy settings regularly
   - **Ongoing Protection**:
     - Regular privacy audits
     - Keep apps and system updated
     - Use privacy-focused alternatives

7. **Specific Privacy Fixes** (Step-by-Step)
   - **Disable Tracking**: How to disable advertising ID, tracking
   - **App Permissions**: Review and restrict app permissions
   - **Location Privacy**: Limit location access and history
   - **Data Sharing**: Disable data sharing with third parties
   - **Privacy Settings**: Enable Android privacy features
   - **App Removal**: Remove privacy-invasive apps
   - **VPN Setup**: How to use VPN for additional privacy

8. **Privacy Best Practices** (Long-term Protection)
   - **App Selection**: Choose privacy-respecting apps
   - **Permission Principles**: Only grant necessary permissions
   - **Regular Audits**: How often to review privacy settings
   - **Data Minimization**: Limit data sharing and collection
   - **Privacy Tools**: Privacy-focused browsers, search engines, email
   - **Education**: Understanding privacy risks and protection

9. **Legal & Compliance** (Privacy Rights)
   - **GDPR Compliance**: European privacy regulations
   - **CCPA Compliance**: California privacy laws
   - **Data Rights**: Right to access, delete, port data
   - **Privacy Policies**: Understanding app privacy policies
   - **Reporting**: How to report privacy violations

**Format:** Prioritize by risk level (Critical → Low). List specific apps, tracking methods, and data types. Provide clear, step-by-step instructions for privacy protection. Explain risks in practical terms users can understand.

Generated by **$appName** 📱
""".trimIndent()

            "privacy_report" -> """
You are a **Cybersecurity & Network Privacy Expert**. Analyze this network privacy audit report from **$appName** and provide a comprehensive security assessment with actionable recommendations.

🔐 **Network Privacy Audit Report:**
$itemContent

**Provide a comprehensive analysis covering:**

1. **Executive Summary** (Brief Overview)
   - Overall privacy posture assessment (Critical / High Risk / Moderate / Low Risk / Secure)
   - Key findings and most concerning results
   - Comparison to typical consumer network environments

2. **Detailed Check Analysis** (For Each Privacy Check)
   - **DNS Integrity**: Analyze DNS manipulation findings, explain DNS hijacking/poisoning risks, ISP DNS interception
   - **SSL/TLS Certificate**: MITM attack risk assessment, certificate pinning status, HSTS compliance
   - **Transparent Proxy**: HTTP proxy detection, traffic interception analysis, corporate vs ISP proxy distinction
   - **Deep Packet Inspection**: DPI methodology assessment, ISP traffic analysis capabilities, protocol-level inspection
   - **ISP Behavior**: Traffic monitoring, HTTP 204 redirect analysis, behavioral advertising injection
   - **Captive Portal**: Network authentication analysis, session hijacking risks on public networks
   - **Private DNS (DoT/DoH)**: DNS encryption status, recommended DNS providers, privacy benefits of encrypted DNS

3. **Threat Assessment** (Risk Analysis)
   - **Threat Level**: Based on combined findings
   - **Attack Surface**: What an attacker on this network could see/do
   - **ISP Surveillance**: What your ISP can monitor based on findings
   - **Government Surveillance**: Potential government-level monitoring indicators
   - **Data Exposure**: Types of data potentially visible to third parties

4. **ISP-Specific Analysis**
   - Known practices of the detected ISP
   - Regional privacy regulations that apply
   - ISP transparency report availability
   - Comparison with privacy-respecting ISPs

5. **Actionable Remediation** (Priority Order)
   - **Immediate** (Do now): Critical fixes for any FAIL/WARNING results
   - **Short-term** (This week): Enable Private DNS, review DNS settings
   - **Medium-term**: VPN considerations, DNS-over-HTTPS setup
   - **Long-term**: Privacy-focused network configuration

6. **Technical Deep Dive** (For Power Users)
   - Protocol-level analysis of each check
   - Network topology implications
   - Advanced configuration recommendations
   - Monitoring and continuous assessment strategies

**Format:** Use clear sections with technical depth. Include specific commands, settings paths, and DNS server addresses where applicable. Provide both consumer-friendly explanations and technical details for advanced users.

Generated by **$appName** 📱
""".trimIndent()
            
            "reachability" -> """
You are a **Network Diagnostics Expert**. Analyze this network reachability test from **$appName** and provide a comprehensive connectivity assessment.

🌐 **Reachability Test Results:**
$itemContent

**Provide analysis covering:**
1. **Connectivity Summary**: Which services are reachable/blocked? Overall network openness.
2. **Block Analysis**: For each blocked service, identify the likely blocking mechanism (DNS filtering, TCP reset, TLS interception, IP blocking).
3. **DNS Analysis**: Evaluate the DNS configuration -- are queries being filtered or redirected?
4. **QUIC/UDP Assessment**: If UDP/443 data is present, assess whether QUIC/HTTP3 traffic is allowed.
5. **Captive Portal**: If detected, explain the implications and how to handle it.
6. **Remediation Steps**: Provide specific, safe, legal troubleshooting steps (change DNS to 8.8.8.8 or 1.1.1.1, enable Private DNS, try different network, contact ISP).
7. **Privacy Assessment**: Based on the DNS and blocking patterns, assess the network's privacy posture.

**Important:** Only suggest safe, legal troubleshooting. Do NOT suggest circumventing network policies or using unauthorized tools. Provide step-by-step Android settings paths.

Generated by **$appName** 📱
""".trimIndent()

            "zero_trust" -> """
You are a **Mobile Security Expert**. Analyze this security dashboard audit from **$appName** and provide a comprehensive security posture assessment.

🛡️ **Security Dashboard Audit:**
$itemContent

**Provide analysis covering:**
1. **Executive Summary**: Overall security posture rating and key findings.
2. **App Privacy Risk Analysis**: Assess permission patterns, spyware indicators, camera/mic activity, accessibility service usage.
3. **Network Trust Analysis**: Evaluate DNS integrity, SSL certificates, proxy/VPN detection, ISP behavior.
4. **Device Integrity Analysis**: Assess root status, USB debugging, encryption, overlay permissions, notification listeners, sideloading settings.
5. **Cross-Section Correlations**: Are there patterns that suggest a coordinated threat (e.g., accessibility service + screen recorder + no encryption)?
6. **Priority Remediation**: Rank the top 5 issues by severity and provide step-by-step fixes with Android Settings paths.
7. **Ongoing Protection**: Recommend a security maintenance routine (monthly checks, settings to monitor, apps to review).

**Format:** Use clear sections with both consumer-friendly explanations and technical details. Provide specific Android Settings paths for each recommendation.

Generated by **$appName** 📱
""".trimIndent()

            else -> """
You are an **Android Device Expert**. Analyze this device information from **$appName** and provide comprehensive analysis with practical insights.

📱 **Category:** $itemTitle

**Device Data:**
$itemContent

**Please provide a detailed analysis with the following structure:**

1. **Data Explanation** (What This Means)
   - **Overview**: What is this information about?
   - **Key Metrics**: Important numbers, values, or statuses
   - **Technical Terms**: Explanation of any technical terminology
   - **Data Breakdown**: What each piece of data represents
   - **Context**: How this data relates to device functionality

2. **Status Assessment** (Normal vs. Concerning)
   - **Current Status**: Overall assessment (Good/Normal/Concerning/Critical)
   - **Normal Ranges**: What values are expected for this category?
   - **Comparison**: How do current values compare to normal?
   - **Red Flags**: Any concerning or abnormal findings?
   - **Severity**: How serious are any issues found?

3. **Performance & Functionality Impact** (Real-World Effects)
   - **Performance Impact**: How does this affect device speed and responsiveness?
   - **Feature Impact**: What device features are affected?
   - **Battery Impact**: How does this affect battery life?
   - **Security Impact**: Any security implications?
   - **User Experience**: How does this affect daily usage?

4. **Technical Analysis** (Deep Dive)
   - **Root Causes**: What's causing current status (if applicable)
   - **Hardware vs. Software**: Is this a hardware or software issue?
   - **System Integration**: How this fits into overall device system
   - **Dependencies**: What other components does this affect?
   - **Limitations**: What are the constraints or limitations?

5. **Actionable Recommendations** (What to Do)
   - **Immediate Actions**: Steps to take right now (if needed)
   - **Settings Changes**: Configuration changes to make
   - **Optimization**: How to improve or optimize this aspect
   - **Maintenance**: Ongoing care and monitoring
   - **When to Seek Help**: When professional assistance is needed

6. **Comparison & Context** (Market Position)
   - **Device Comparison**: How does this compare to similar devices?
   - **Market Standards**: How does it compare to current standards?
   - **Age Consideration**: How device age affects this aspect
   - **Value Assessment**: Is this good for the device's price point?

7. **Best Practices** (Long-term Care)
   - **Maintenance**: How to maintain this aspect of the device
   - **Monitoring**: How to track this over time
   - **Prevention**: How to prevent issues
   - **Optimization**: Ongoing optimization strategies

**Format:** Use clear headings and structure. Explain technical terms. Prioritize actionable recommendations. Balance technical accuracy with practical advice. Help users understand both what the data means and what they should do about it.

Generated by **$appName** 📱
""".trimIndent()
        }
    }
    
    /**
     * Generate simple mode prompt specifically for Camera Power Test
     */
    private fun generateSimpleCameraPowerTestPrompt(itemTitle: String, itemContent: String, appName: String): String {
        return """
I'm using **$appName** to understand how much battery my camera uses. Please analyze this data and explain what it means in simple terms.

📸 **My Camera Power Test Results:**
$itemContent

**Please provide a clear, structured response with:**

1. **Quick Summary** (2-3 sentences)
   - How much power does my camera use when taking photos?
   - Is this normal, high, or low?
   - Overall assessment of camera power efficiency.

2. **What the Numbers Mean** (explain simply)
   - What does "power difference" mean?
   - What does "capture duration" mean?
   - How do these numbers relate to battery drain?

3. **Battery Impact**
   - How much battery does taking a photo typically consume?
   - What activities (e.g., video recording, flash) might increase this?
   - Is my camera a significant battery drainer?

4. **Optimization Tips** (actionable advice)
   - How can I reduce my camera's battery usage?
   - Are there any settings I should change (e.g., resolution, flash usage)?
   - Tips for extending battery life when using the camera.

**Important:** Focus on power consumption and battery impact. Use simple analogies and avoid technical jargon. Explain everything as if talking to someone who doesn't know much about phone power.

Generated by **$appName** 📱
""".trimIndent()
    }
    
    /**
     * Generate advanced mode prompt specifically for Camera Power Test
     */
    private fun generateAdvancedCameraPowerTestPrompt(itemTitle: String, itemContent: String, appName: String): String {
        return """
You are an **Android Camera Power Efficiency Expert**. Analyze this camera power test data from **$appName** and provide a comprehensive, structured assessment focusing on power consumption and optimization.

📸 **Camera Power Test Data:**
$itemContent

**Please provide a detailed analysis with the following structure:**

1. **Power Consumption Assessment** (Current Status)
   - **Average Power Difference**: Baseline vs. capture power (Watts)
   - **Energy Per Photo**: Estimated energy consumption per capture (Joules)
   - **Capture Duration**: Time taken for photo capture (milliseconds)
   - **Overall Efficiency**: Is the camera power usage efficient, average, or high?

2. **Technical Breakdown** (Detailed Analysis)
   - **Baseline Power**: Device power consumption when camera is idle but active.
   - **Capture Power**: Peak power consumption during photo capture.
   - **Power Difference**: The actual power overhead of taking a photo.
   - **Impact of Duration**: How longer capture times affect total energy.

3. **Battery Impact Analysis**
   - **Estimated Battery Drain**: Percentage of battery consumed per 100 photos.
   - **Factors Increasing Drain**: Flash usage, high resolution, video recording, continuous shooting.
   - **Comparison**: How does this compare to typical camera power usage on similar devices?

4. **Optimization Strategies** (Actionable Recommendations)
   - **Camera Settings**:
     - Recommended resolution/quality settings for power saving.
     - When to use/avoid flash.
     - Impact of video recording settings (frame rate, resolution).
   - **Usage Patterns**:
     - Tips for reducing power consumption during camera use.
     - Managing background camera access (if applicable).
   - **App-Specific Tips**:
     - General advice for camera apps (e.g., close when not in use).

5. **Performance vs. Power Trade-offs**
   - Explain the balance between photo quality/features and battery life.
   - When is it acceptable to prioritize power over performance?

6. **Long-Term Considerations**
   - How does frequent camera use impact overall battery health?
   - Monitoring camera power usage over time.

**Format:** Use clear headings, bullet points, and specific numbers/metrics (Watts, Joules, milliseconds, percentages). Balance technical depth with practical, actionable insights.

Generated by **$appName** 📱
""".trimIndent()
    }
}

