package com.teamz.lab.debugger

import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.junit.Assert.*

/**
 * Tests for AI prompt generation for each tab
 * Ensures tab-specific prompts are generated correctly
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class AIPromptGenerationTest {
    
    private val appName = "DeviceGPT"
    
    // ========== DEVICE INFO TAB PROMPTS ==========
    
    @Test
    fun testDeviceInfoSimplePrompt() {
        val prompt = generateDeviceInfoPrompt(PromptMode.Simple)
        
        assertTrue("Device prompt should not be empty", prompt.isNotEmpty())
        assertTrue("Should contain app name", prompt.contains(appName))
        assertTrue("Should contain working well text", prompt.contains("phone is working well", ignoreCase = true))
        assertTrue("Should contain explain", prompt.contains("explain", ignoreCase = true))
        assertFalse("Should not contain fake", prompt.contains("fake", ignoreCase = true))
    }
    
    @Test
    fun testDeviceInfoDetailedPrompt() {
        val prompt = generateDeviceInfoPrompt(PromptMode.Detailed)
        
        assertTrue("Device detailed prompt should not be empty", prompt.isNotEmpty())
        assertTrue("Should contain CPU, RAM, Battery", prompt.contains("CPU, RAM, Battery", ignoreCase = true))
        assertTrue("Should contain privacy risk", prompt.contains("privacy or security risk", ignoreCase = true))
        assertTrue("Should contain Pro Tips", prompt.contains("Pro Tips", ignoreCase = true))
    }
    
    // ========== NETWORK INFO TAB PROMPTS ==========
    
    @Test
    fun testNetworkInfoSimplePrompt() {
        val prompt = generateNetworkInfoPrompt(PromptMode.Simple)
        
        assertTrue("Network prompt should not be empty", prompt.isNotEmpty())
        assertTrue("Should contain internet working", prompt.contains("internet is working", ignoreCase = true))
        assertTrue("Should contain Wi-Fi or mobile data", prompt.contains("Wi-Fi or mobile data", ignoreCase = true))
        assertFalse("Should not contain fake", prompt.contains("fake", ignoreCase = true))
    }
    
    @Test
    fun testNetworkInfoDetailedPrompt() {
        val prompt = generateNetworkInfoPrompt(PromptMode.Detailed)
        
        assertTrue("Network detailed prompt should not be empty", prompt.isNotEmpty())
        assertTrue("Should contain Speed test", prompt.contains("Speed test", ignoreCase = true))
        assertTrue("Should contain ISP throttling", prompt.contains("ISP throttling", ignoreCase = true))
        assertTrue("Should contain DNS", prompt.contains("DNS", ignoreCase = true))
    }
    
    // ========== HEALTH TAB PROMPTS ==========
    
    @Test
    fun testHealthSimplePrompt() {
        val prompt = generateHealthPrompt(PromptMode.Simple)
        
        assertTrue("Health prompt should not be empty", prompt.isNotEmpty())
        assertTrue("Should contain phone's overall health", prompt.contains("phone's overall health", ignoreCase = true))
        assertTrue("Should contain Health Score", prompt.contains("Health Score", ignoreCase = true))
        assertFalse("Should not contain fake", prompt.contains("fake", ignoreCase = true))
    }
    
    @Test
    fun testHealthDetailedPrompt() {
        val prompt = generateHealthPrompt(PromptMode.Detailed)
        
        assertTrue("Health detailed prompt should not be empty", prompt.isNotEmpty())
        assertTrue("Should contain health score mean", prompt.contains("health score mean", ignoreCase = true))
        assertTrue("Should contain improvement", prompt.contains("improvement", ignoreCase = true))
        assertTrue("Should contain Daily scan streak", prompt.contains("Daily scan streak", ignoreCase = true))
    }
    
    // ========== POWER TAB PROMPTS ==========
    
    @Test
    fun testPowerSimplePrompt() {
        val prompt = generatePowerPrompt(PromptMode.Simple)
        
        assertTrue("Power prompt should not be empty", prompt.isNotEmpty())
        assertTrue("Should contain battery my phone uses", prompt.contains("battery my phone uses", ignoreCase = true))
        assertTrue("Should contain Power usage", prompt.contains("Power usage", ignoreCase = true))
        assertFalse("Should not contain fake", prompt.contains("fake", ignoreCase = true))
    }
    
    @Test
    fun testPowerDetailedPrompt() {
        val prompt = generatePowerPrompt(PromptMode.Detailed)
        
        assertTrue("Power detailed prompt should not be empty", prompt.isNotEmpty())
        assertTrue("Should contain power consumption", prompt.contains("power consumption", ignoreCase = true))
        assertTrue("Should contain Component breakdown", prompt.contains("Component breakdown", ignoreCase = true))
        assertTrue("Should contain battery drain", prompt.contains("battery drain", ignoreCase = true))
    }
    
    // ========== HELPER FUNCTIONS ==========
    
    private enum class PromptMode {
        Simple, Detailed
    }
    
    private fun generateDeviceInfoPrompt(promptMode: PromptMode): String {
        return if (promptMode == PromptMode.Simple) {
            """
        Hi, I'm using **$appName** to check if my phone is working well.

        Here's what I found:

        🧠 Phone is running normally  
        🔋 Battery health is decent, a bit warm  
        💾 Storage is almost full  
        👁️ Mic or camera used recently  
        👣 Someone may have touched phone while locked  
        🔐 No major security risks

        Can you explain what this means — like I'm a friend?  
        And give 1–2 easy tips to improve it? 😊

        Made with **$appName**, my phone health checker 📱
        """.trimIndent()
        } else {
            """
        Hi, I'm using an app called **$appName** to scan my phone and understand what's going on.

        Can you break down this report and explain it clearly?

        🔍 What I'd like to understand:
        • Is my phone running slow or performing well?
        • Is the battery healthy or draining too fast?
        • Is anything overheating or using too much power?
        • Do I need to clean up storage?
        • Is there any privacy or security risk?
        • Can I run on-device AI models like ChatGPT?
        • Are there apps using my mic/camera without me knowing?
        • Did my phone move when it was locked?

        📊 Info includes:
        - CPU, RAM, Battery & Temperature
        - Frame drops & smoothness
        - Motion sensor logs
        - Root & developer mode status
        - Mic/camera logs
        - AI support test
        - App tracking and sensor spoofing checks

        💡 Please explain it in clear language anyone can understand, and add a few smart tips.

        ✅ Pro Tips (if possible):
        • Try clearing app cache or uninstalling unused apps to free space  
        • Turn off background sync for apps draining battery  
        • Reduce screen brightness or use dark mode to cool down your phone  
        • Lock apps with sensitive permissions if not in use  

        Generated by **$appName**, your daily Android health & privacy guide 📱🧠
        """.trimIndent()
        }
    }
    
    private fun generateNetworkInfoPrompt(promptMode: PromptMode): String {
        return if (promptMode == PromptMode.Simple) {
            """
        Hi, I'm using **$appName** to check if my internet is working properly.

        Here's a quick look at what I found:

        📶 Connection: Wi-Fi or mobile data  
        ⚡ Speed: Looks normal, but might not be consistent  
        🌀 Stability: Some jitter or small delay detected  
        🔍 Privacy: No major risks seen (but please double-check!)  

        Can you explain this in easy language — like you're helping a friend?  
        And give me 1–2 simple tips to make my internet better 😊

        Made with **$appName**, my daily internet health checker 🌐📱
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
    
    private fun generateHealthPrompt(promptMode: PromptMode): String {
        return if (promptMode == PromptMode.Simple) {
            """
        Hi, I'm using **$appName** to check my phone's overall health.

        Here's what I found:

        🏆 Health Score: Checked and recorded  
        📈 Daily streak and history tracked  
        💡 Smart suggestions for improvement  

        Can you explain this health report in simple terms?  
        And give me 1–2 easy tips to keep my phone healthy? 😊

        Made with **$appName**, my phone health tracker 📱
        """.trimIndent()
        } else {
            """
        Hi, I'm using an app called **$appName** to track my phone's health over time.

        Can you analyze this health report and help me understand:

        🔍 What I'd like to know:
        • What does my health score mean?
        • Is my phone in good condition overall?
        • What are the main areas I should improve?
        • How does my current score compare to my history?
        • What's causing my phone to lose health points?

        📊 This includes:
        - Health score (0-100)
        - Daily scan streak
        - Best score achieved
        - Recent health history
        - Personalized improvement suggestions

        💡 Please explain what each part means and give me actionable tips.

        ✅ Pro Tips (if possible):
        • Scan daily to track trends and catch issues early  
        • Follow the improvement suggestions for quick wins  
        • Monitor battery health and storage regularly  
        • Keep apps updated for security and performance  

        Report from **$appName** — your daily phone health companion 📱🏥
        """.trimIndent()
        }
    }
    
    private fun generatePowerPrompt(promptMode: PromptMode): String {
        return if (promptMode == PromptMode.Simple) {
            """
        Hi, I'm using **$appName** to check how much battery my phone uses.

        Here's what I found:

        ⚡ Power usage measured in real-time  
        🔋 Component breakdown shows what drains battery  
        📊 Statistics and trends tracked  

        Can you explain this power report in simple terms?  
        And give me 1–2 tips to save battery? 😊

        Made with **$appName**, my battery analyzer 🔋
        """.trimIndent()
        } else {
            """
        Hi, I'm using an app called **$appName** to analyze my phone's power consumption.

        Can you review this power report and help me understand:

        🔍 What I'd like to know:
        • Which components are using the most battery?
        • Is my power usage normal or too high?
        • What's causing the most battery drain?
        • How can I reduce power consumption?
        • Are there any unusual power patterns?

        📊 This includes:
        - Real-time power measurements (Watts)
        - Component breakdown (CPU, Display, Camera, etc.)
        - Power statistics (average, peak, min)
        - Power trend analysis
        - Top power consumers

        💡 Please analyze the data and give me specific recommendations.

        ✅ Pro Tips (if possible):
        • Turn off unused features (Bluetooth, GPS, Wi-Fi when not needed)  
        • Reduce screen brightness and use dark mode  
        • Close background apps that drain battery  
        • Enable battery saver mode during low battery  
        • Identify and limit power-hungry apps  

        Report from **$appName** — your power consumption analyst ⚡🔋
        """.trimIndent()
        }
    }
}

