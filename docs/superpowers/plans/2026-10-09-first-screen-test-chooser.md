# First Screen Test Chooser + Test-Done Card — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Run an A/B test. Group B users see Camera / Mic / Screen test buttons on the score screen.
After their first completed test they get a one-time "Done ✓" card that offers the widget and a
weekly check-up (a notification permission request).

**Architecture:**
- Two pure policy objects in `utils/`:
  - `FirstScreenExperiment`: picks the arm, persists it, stamps the GA4 property, maps tabs.
  - `TestDoneCard`: decides when the card shows and exposes a `StateFlow` for the UI.
- UI changes, all guarded by arm B:
  - `FirstScanGateScreen` (score phase): the chooser buttons.
  - `MainActivity` (routing; the widget prompt is skipped in B).
  - `DeviceGptNavExperience` (renders the card).
- The three existing test-completion call sites notify `TestDoneCard`.

**Tech Stack:** Kotlin, Jetpack Compose, Firebase Remote Config, GA4 (`AnalyticsUtils`), JUnit +
Robolectric (`./gradlew :app:testDebugUnitTest`).

**Spec:** `docs/superpowers/specs/2026-10-09-first-screen-test-chooser-design.md`

## Global Constraints
- RC key: `first_screen_test_chooser`, boolean, bundled default `false`.
- Arm prefs key: `fs_arm`, values `"A"` or `"B"`. Read RC once, at the first score reveal, then
  keep the stored value forever.
- GA4 user property: `fs_arm`.
- Events:
  - `fs_test_chosen` (param `test`: camera / mic / screen / report)
  - `fs_done_card_shown`
  - `fs_done_card_action` (param `action`: widget / weekly / dismiss)
  - `fs_notif_permission_result` (param `granted`)
- Routes:
  - camera → `navigate_to_tab=camera`
  - mic and screen → `screen_test`
  - report → `health`
- Group A is unchanged. QuietPeriod, paywalls and ads are untouched.
- Copy:
  - Chooser heading: "What do you want to test?"
  - Buttons: "Camera", "Mic", "Screen". Link: "See full health report".
  - Card title: "Done ✓"
  - Card body: "Want to keep an eye on your phone's health?"
  - Card buttons: "Add widget", "Weekly check-up", "Not now".
- Every new test must be seen failing before its code is written. Run all tests with
  `./gradlew :app:testDebugUnitTest`; the baseline is 738 passing.

---

### Task 1: FirstScreenExperiment (arm choice, persistence, GA4 stamp, routes) + events

**Files:**
- Create: `app/src/main/java/com/teamz/lab/debugger/utils/FirstScreenExperiment.kt`
- Modify: `app/src/main/java/com/teamz/lab/debugger/utils/RemoteConfigUtils.kt`. Add the
  bundled default next to `"widget_pin_prompt_enabled" to false`, and add an accessor next to
  `fun isWidgetPinPromptEnabled()`.
- Modify: `app/src/main/java/com/teamz/lab/debugger/utils/analytics_utils.kt`. Add the enum
  entries after `MicTestPlaybackAnswered("mic_test_playback_answered"),`.
- Test: `app/src/test/java/com/teamz/lab/debugger/quality/FirstScreenExperimentTest.kt`

**Interfaces:**
- Produces:
  - `FirstScreenExperiment.chooseArm(rcFlag: Boolean, stored: String?): String` (pure)
  - `FirstScreenExperiment.arm(context: Context): String` (persists, then stamps)
  - `FirstScreenExperiment.isB(context): Boolean`
  - `FirstScreenExperiment.tabFor(choice: String): String`
  - `RemoteConfigUtils.isFirstScreenTestChooserEnabled(): Boolean`
  - `AnalyticsEvent.FsTestChosen`, `FsDoneCardShown`, `FsDoneCardAction`,
    `FsNotifPermissionResult`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.teamz.lab.debugger.quality

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.teamz.lab.debugger.utils.FirstScreenExperiment
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FirstScreenExperimentTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before fun clear() {
        context.getSharedPreferences(FirstScreenExperiment.PREFS, Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test fun `arm follows the flag the first time`() {
        assertEquals("B", FirstScreenExperiment.chooseArm(rcFlag = true, stored = null))
        assertEquals("A", FirstScreenExperiment.chooseArm(rcFlag = false, stored = null))
    }

    @Test fun `a stored arm never changes`() {
        assertEquals("A", FirstScreenExperiment.chooseArm(rcFlag = true, stored = "A"))
        assertEquals("B", FirstScreenExperiment.chooseArm(rcFlag = false, stored = "B"))
    }

    @Test fun `arm is persisted on first read`() {
        val first = FirstScreenExperiment.arm(context)          // RC default false -> A
        assertEquals("A", first)
        context.getSharedPreferences(FirstScreenExperiment.PREFS, Context.MODE_PRIVATE)
            .edit().putString(FirstScreenExperiment.KEY_ARM, "B").commit()
        assertEquals("B", FirstScreenExperiment.arm(context))   // stored wins
    }

    @Test fun `chooser routes to the existing tabs`() {
        assertEquals("camera", FirstScreenExperiment.tabFor("camera"))
        assertEquals("screen_test", FirstScreenExperiment.tabFor("mic"))
        assertEquals("screen_test", FirstScreenExperiment.tabFor("screen"))
        assertEquals("health", FirstScreenExperiment.tabFor("report"))
        assertEquals("health", FirstScreenExperiment.tabFor("anything else"))
    }
}
```

- [ ] **Step 2: Run the test and confirm it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.teamz.lab.debugger.quality.FirstScreenExperimentTest" -q`
Expected: FAIL. Compile error, "Unresolved reference 'FirstScreenExperiment'".

- [ ] **Step 3: Write the implementation**

`FirstScreenExperiment.kt`:
```kotlin
package com.teamz.lab.debugger.utils

import android.content.Context

/**
 * A/B test: Camera / Mic / Screen chooser on the score screen (arm B) vs today's flow (arm A).
 * Spec: docs/superpowers/specs/2026-10-09-first-screen-test-chooser-design.md
 * The arm is read from RC once, at the first score reveal, then fixed for this install.
 */
object FirstScreenExperiment {
    internal const val PREFS = "first_screen_experiment"
    internal const val KEY_ARM = "fs_arm"

    fun chooseArm(rcFlag: Boolean, stored: String?): String =
        stored ?: if (rcFlag) "B" else "A"

    fun arm(context: Context): String {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        p.getString(KEY_ARM, null)?.let { return it }
        val chosen = chooseArm(RemoteConfigUtils.isFirstScreenTestChooserEnabled(), null)
        p.edit().putString(KEY_ARM, chosen).apply()
        try { AnalyticsUtils.setUserProperty("fs_arm", chosen) } catch (_: Throwable) { }
        return chosen
    }

    fun isB(context: Context): Boolean = arm(context) == "B"

    fun tabFor(choice: String): String = when (choice) {
        "camera" -> "camera"
        "mic", "screen" -> "screen_test"
        else -> "health"
    }
}
```

`RemoteConfigUtils.kt`. Add after `"widget_pin_prompt_enabled" to false` (keep the trailing
comma rules of the surrounding map):
```kotlin
                "first_screen_test_chooser" to false,       // A/B: test chooser on score screen (spec 2026-10-09)
```
and after `fun isWidgetPinPromptEnabled(): Boolean = remoteConfig.getBoolean("widget_pin_prompt_enabled")`:
```kotlin
    fun isFirstScreenTestChooserEnabled(): Boolean = remoteConfig.getBoolean("first_screen_test_chooser")
```

`analytics_utils.kt`. Add after `MicTestPlaybackAnswered("mic_test_playback_answered"),`:
```kotlin
    FsTestChosen("fs_test_chosen"),                       // param: test
    FsDoneCardShown("fs_done_card_shown"),
    FsDoneCardAction("fs_done_card_action"),              // param: action
    FsNotifPermissionResult("fs_notif_permission_result"),// param: granted
```

- [ ] **Step 4: Run the test and confirm it passes**

Run the Step 2 command. Expected: 4 tests, 0 failures.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/teamz/lab/debugger/utils/FirstScreenExperiment.kt app/src/main/java/com/teamz/lab/debugger/utils/RemoteConfigUtils.kt app/src/main/java/com/teamz/lab/debugger/utils/analytics_utils.kt app/src/test/java/com/teamz/lab/debugger/quality/FirstScreenExperimentTest.kt
git commit -m "feat(fs-ab): FirstScreenExperiment arm + routes + events"
```

---

### Task 2: TestDoneCard policy + notify from the three completion sites

**Files:**
- Create: `app/src/main/java/com/teamz/lab/debugger/utils/TestDoneCard.kt`
- Modify: `app/src/main/java/com/teamz/lab/debugger/ui/MicTestCard.kt`, right after the
  `AnalyticsEvent.MicTestCompleted` logEvent block (around line 202)
- Modify: `app/src/main/java/com/teamz/lab/debugger/ui/CameraHealthViewModel.kt`, after the
  `AnalyticsEvent.CameraHealthCheckCompleted` logEvent (around line 122)
- Modify: `app/src/main/java/com/teamz/lab/debugger/ui/ScreenTestViewModel.kt`, after the
  `AnalyticsEvent.ScreenPixelTestCompleted` logEvent (around line 54)
- Test: `app/src/test/java/com/teamz/lab/debugger/quality/TestDoneCardTest.kt`

**Interfaces:**
- Consumes: `FirstScreenExperiment.isB(context)` (Task 1)
- Produces:
  - `TestDoneCard.shouldShow(isB: Boolean, alreadyShown: Boolean): Boolean` (pure)
  - `TestDoneCard.onTestCompleted(context: Context)`
  - `TestDoneCard.visible: StateFlow<Boolean>`
  - `TestDoneCard.dismiss()`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.teamz.lab.debugger.quality

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.teamz.lab.debugger.utils.FirstScreenExperiment
import com.teamz.lab.debugger.utils.TestDoneCard
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TestDoneCardTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before fun clear() {
        for (p in listOf(TestDoneCard.PREFS, FirstScreenExperiment.PREFS))
            context.getSharedPreferences(p, Context.MODE_PRIVATE).edit().clear().commit()
        TestDoneCard.dismiss()
    }

    @Test fun `only arm B, only once`() {
        assertTrue(TestDoneCard.shouldShow(isB = true, alreadyShown = false))
        assertFalse(TestDoneCard.shouldShow(isB = true, alreadyShown = true))
        assertFalse(TestDoneCard.shouldShow(isB = false, alreadyShown = false))
    }

    @Test fun `first completed test in arm B shows the card once`() {
        context.getSharedPreferences(FirstScreenExperiment.PREFS, Context.MODE_PRIVATE)
            .edit().putString(FirstScreenExperiment.KEY_ARM, "B").commit()
        TestDoneCard.onTestCompleted(context)
        assertTrue(TestDoneCard.visible.value)
        TestDoneCard.dismiss()
        TestDoneCard.onTestCompleted(context)
        assertFalse("second test must not show it again", TestDoneCard.visible.value)
    }

    @Test fun `arm A never sees it`() {
        context.getSharedPreferences(FirstScreenExperiment.PREFS, Context.MODE_PRIVATE)
            .edit().putString(FirstScreenExperiment.KEY_ARM, "A").commit()
        TestDoneCard.onTestCompleted(context)
        assertFalse(TestDoneCard.visible.value)
    }

    @Test fun `all three completion sites notify the card`() {
        var dir = File(System.getProperty("user.dir") ?: ".")
        while (!File(dir, "settings.gradle.kts").exists() && dir.parentFile != null) dir = dir.parentFile
        val base = "app/src/main/java/com/teamz/lab/debugger/ui/"
        for ((file, event) in listOf(
            "MicTestCard.kt" to "AnalyticsEvent.MicTestCompleted",
            "CameraHealthViewModel.kt" to "AnalyticsEvent.CameraHealthCheckCompleted",
            "ScreenTestViewModel.kt" to "AnalyticsEvent.ScreenPixelTestCompleted",
        )) {
            val s = File(dir, base + file).readText()
            val at = s.indexOf(event)
            assertTrue("$file: $event", at >= 0)
            assertTrue("$file must call TestDoneCard.onTestCompleted after $event",
                s.indexOf("TestDoneCard.onTestCompleted(", at) in at until at + 800)
        }
    }
}
```

- [ ] **Step 2: Run the test and confirm it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.teamz.lab.debugger.quality.TestDoneCardTest" -q`
Expected: FAIL. Compile error, "Unresolved reference 'TestDoneCard'".

- [ ] **Step 3: Write the implementation**

`TestDoneCard.kt`:
```kotlin
package com.teamz.lab.debugger.utils

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** One-time "Done ✓" card after the first completed test, arm B only. See spec 2026-10-09. */
object TestDoneCard {
    internal const val PREFS = "test_done_card"
    private const val KEY_SHOWN = "shown"

    private val _visible = MutableStateFlow(false)
    val visible: StateFlow<Boolean> = _visible

    fun shouldShow(isB: Boolean, alreadyShown: Boolean): Boolean = isB && !alreadyShown

    fun onTestCompleted(context: Context) {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!shouldShow(FirstScreenExperiment.isB(context), p.getBoolean(KEY_SHOWN, false))) return
        p.edit().putBoolean(KEY_SHOWN, true).apply()
        _visible.value = true
        try { AnalyticsUtils.logEvent(AnalyticsEvent.FsDoneCardShown) } catch (_: Throwable) { }
    }

    fun dismiss() { _visible.value = false }
}
```

Call sites. Add this line right after each listed `logEvent(...)` call closes:
```kotlin
com.teamz.lab.debugger.utils.TestDoneCard.onTestCompleted(context)
```
- MicTestCard: inside the same coroutine, after the `MicTestCompleted` logEvent call. `context`
  is the composable's `LocalContext.current`. If the block has no `context` in scope, capture
  `val context = LocalContext.current` at the top of the composable and use that.
- CameraHealthViewModel: after the `CameraHealthCheckCompleted` logEvent, before
  `onComplete(result)`. It uses the `context` already in scope there.
- ScreenTestViewModel: after the `ScreenPixelTestCompleted` logEvent, using the `context` in
  scope.

- [ ] **Step 4: Run the test and confirm it passes**

Run the Step 2 command. Expected: 4 tests, 0 failures.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/teamz/lab/debugger/utils/TestDoneCard.kt app/src/main/java/com/teamz/lab/debugger/ui/MicTestCard.kt app/src/main/java/com/teamz/lab/debugger/ui/CameraHealthViewModel.kt app/src/main/java/com/teamz/lab/debugger/ui/ScreenTestViewModel.kt app/src/test/java/com/teamz/lab/debugger/quality/TestDoneCardTest.kt
git commit -m "feat(fs-ab): TestDoneCard policy, notified by the three test completions"
```

---

### Task 3: Score screen chooser (arm B) + MainActivity routing; no score-phase widget prompt in B

**Files:**
- Modify: `app/src/main/java/com/teamz/lab/debugger/ui/FirstScanGateScreen.kt`
  - `FirstScanGateScreen(...)`: add param `onChooseTest: ((String) -> Unit)? = null`
  - the `Phase.SCORED -> ScoredUi(...)` call: pass `showChooser` and `onChoose`
  - `ScoredUi`: render the chooser when `showChooser`
- Modify: `app/src/main/java/com/teamz/lab/debugger/MainActivity.kt`, at the
  `FirstScanGateScreen(` call: add `onChooseTest`, and skip
  `WidgetPinPrompt.maybePrompt` in arm B in `onShareScore` / `onDismiss`
- Test: `app/src/test/java/com/teamz/lab/debugger/quality/FirstScreenChooserWiringTest.kt`

**Interfaces:**
- Consumes: `FirstScreenExperiment.isB(context)`, `FirstScreenExperiment.tabFor(choice)`,
  `AnalyticsEvent.FsTestChosen` (Task 1)
- Produces: `FirstScanGateScreen(onShareScore, onDismiss, onChooseTest)`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.teamz.lab.debugger.quality

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class FirstScreenChooserWiringTest {
    private fun src(rel: String): String {
        var dir = File(System.getProperty("user.dir") ?: ".")
        while (!File(dir, "settings.gradle.kts").exists() && dir.parentFile != null) dir = dir.parentFile
        return File(dir, "app/src/main/java/com/teamz/lab/debugger/$rel").readText()
    }

    @Test fun `score screen shows the chooser only in arm B, with the approved copy`() {
        val s = src("ui/FirstScanGateScreen.kt")
        assertTrue(s.contains("onChooseTest: ((String) -> Unit)? = null"))
        assertTrue(s.contains("FirstScreenExperiment.isB(context)"))
        for (copy in listOf("What do you want to test?", "\"Camera\"", "\"Mic\"", "\"Screen\"", "See full health report"))
            assertTrue(copy, s.contains(copy))
    }

    @Test fun `MainActivity routes the choice and skips the score-phase widget prompt in B`() {
        val m = src("MainActivity.kt")
        assertTrue(m.contains("onChooseTest = { choice ->"))
        assertTrue(m.contains("FirstScreenExperiment.tabFor(choice)"))
        assertTrue(m.contains("AnalyticsEvent.FsTestChosen"))
        val prompts = Regex("""if \(!com\.teamz\.lab\.debugger\.utils\.FirstScreenExperiment\.isB\(this@MainActivity\)\)\s*com\.teamz\.lab\.debugger\.utils\.WidgetPinPrompt\.maybePrompt""").findAll(m).count()
        assertTrue("both score-phase prompts must be guarded (found $prompts)", prompts >= 2)
    }
}
```

- [ ] **Step 2: Run the test and confirm it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.teamz.lab.debugger.quality.FirstScreenChooserWiringTest" -q`
Expected: FAIL. AssertionError on `onChooseTest`.

- [ ] **Step 3: Write the implementation**

`FirstScanGateScreen.kt`. Change the signature:
```kotlin
fun FirstScanGateScreen(
    onShareScore: (Int) -> Unit = {},
    onDismiss: () -> Unit = {},
    onChooseTest: ((String) -> Unit)? = null,
) {
```
In `Phase.SCORED -> ScoredUi(`, add two arguments after `onDetails = { ... },`:
```kotlin
                showChooser = onChooseTest != null && com.teamz.lab.debugger.utils.FirstScreenExperiment.isB(context),
                onChoose = { choice ->
                    FirstScanGate.markCompleted(context, finalScore, scanResult)
                    onChooseTest?.invoke(choice)
                },
```
Change the `ScoredUi` signature to add `showChooser: Boolean = false, onChoose: (String) -> Unit = {},`
after `onDetails: () -> Unit,`. Replace the final share/details block (from
`Spacer(Modifier.height(32.dp))` to the end of the `OutlinedButton { Text("See details") }`)
with:
```kotlin
        Spacer(Modifier.height(24.dp))
        if (showChooser) {
            Text(
                text = "What do you want to test?",
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                for ((label, key) in listOf("Camera" to "camera", "Mic" to "mic", "Screen" to "screen")) {
                    Button(
                        onClick = { onChoose(key) },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                    ) { Text(label, fontSize = 16.sp, fontWeight = FontWeight.SemiBold) }
                }
            }
            Spacer(Modifier.height(16.dp))
            OutlinedButton(onClick = onShare, modifier = Modifier.fillMaxWidth()) {
                Text("Share my score", fontSize = 16.sp)
            }
            TextButton(onClick = { onChoose("report") }, modifier = Modifier.fillMaxWidth()) {
                Text("See full health report", fontSize = 14.sp)
            }
        } else {
            Button(
                onClick = onShare,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            ) {
                Text("Share my score", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                onClick = onDetails,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("See details", fontSize = 16.sp)
            }
        }
```
If they are missing, add imports `androidx.compose.material3.TextButton` and
`androidx.compose.foundation.layout.Row`.

`MainActivity.kt`. In the `FirstScanGateScreen(` call, add after the `onDismiss = { ... },`
block:
```kotlin
                                    onChooseTest = { choice ->
                                        try {
                                            com.teamz.lab.debugger.utils.AnalyticsUtils.logEvent(
                                                com.teamz.lab.debugger.utils.AnalyticsEvent.FsTestChosen,
                                                mapOf("test" to choice)
                                            )
                                        } catch (_: Throwable) { }
                                        intent.putExtra("navigate_to_tab", com.teamz.lab.debugger.utils.FirstScreenExperiment.tabFor(choice))
                                        gateState.value =
                                            com.teamz.lab.debugger.ui.FirstScanGate.State.COMPLETED
                                    },
```
In both the `onShareScore` and `onDismiss` lambdas, replace
`com.teamz.lab.debugger.utils.WidgetPinPrompt.maybePrompt(this@MainActivity)` with:
```kotlin
if (!com.teamz.lab.debugger.utils.FirstScreenExperiment.isB(this@MainActivity)) com.teamz.lab.debugger.utils.WidgetPinPrompt.maybePrompt(this@MainActivity)
```

- [ ] **Step 4: Run the tests and confirm they pass**

Run the Step 2 command (2 tests, 0 failures), then `./gradlew :app:testDebugUnitTest -q`.
Everything must be green.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/teamz/lab/debugger/ui/FirstScanGateScreen.kt app/src/main/java/com/teamz/lab/debugger/MainActivity.kt app/src/test/java/com/teamz/lab/debugger/quality/FirstScreenChooserWiringTest.kt
git commit -m "feat(fs-ab): Camera/Mic/Screen chooser on the score screen (arm B)"
```

---

### Task 4: "Done ✓" card UI: widget + weekly check-up permission

**Files:**
- Modify: `app/src/main/java/com/teamz/lab/debugger/utils/WidgetPinPrompt.kt`. Add
  `requestNow(context)` (a user-initiated pin that skips the RC flag and the once-flag).
- Create: `app/src/main/java/com/teamz/lab/debugger/ui/TestDoneSheet.kt`
- Modify: `app/src/main/java/com/teamz/lab/debugger/ui/adaptive/DeviceGptNavExperience.kt`.
  Render `TestDoneSheet()` right before `if (showRewardedReportOffer) {`.
- Test: `app/src/test/java/com/teamz/lab/debugger/quality/TestDoneSheetWiringTest.kt`

**Interfaces:**
- Consumes: `TestDoneCard.visible`, `TestDoneCard.dismiss()` (Task 2), and the
  `AnalyticsEvent.FsDoneCardAction` / `FsNotifPermissionResult` events (Task 1)
- Produces: `WidgetPinPrompt.requestNow(context: Context): Boolean` and a
  `TestDoneSheet()` composable

- [ ] **Step 1: Write the failing test**

```kotlin
package com.teamz.lab.debugger.quality

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class TestDoneSheetWiringTest {
    private fun src(rel: String): String {
        var dir = File(System.getProperty("user.dir") ?: ".")
        while (!File(dir, "settings.gradle.kts").exists() && dir.parentFile != null) dir = dir.parentFile
        return File(dir, "app/src/main/java/com/teamz/lab/debugger/$rel").readText()
    }

    @Test fun `sheet has the approved copy and three actions`() {
        val s = src("ui/TestDoneSheet.kt")
        for (c in listOf("Done ✓", "Want to keep an eye on your phone's health?", "Add widget", "Weekly check-up", "Not now"))
            assertTrue(c, s.contains(c))
        assertTrue(s.contains("WidgetPinPrompt.requestNow("))
        assertTrue(s.contains("Manifest.permission.POST_NOTIFICATIONS"))
        assertTrue(s.contains("AnalyticsEvent.FsNotifPermissionResult"))
        assertTrue(s.contains("TestDoneCard.visible.collectAsState()"))
    }

    @Test fun `main screen renders the sheet`() {
        assertTrue(src("ui/adaptive/DeviceGptNavExperience.kt").contains("TestDoneSheet()"))
    }

    @Test fun `user-initiated widget pin does not depend on the prompt flags`() {
        val w = src("utils/WidgetPinPrompt.kt")
        val start = w.indexOf("fun requestNow(")
        assertTrue(start >= 0)
        val body = w.substring(start, w.indexOf("\n    }", start))
        assertTrue(!body.contains("isWidgetPinPromptEnabled") && !body.contains("KEY_PROMPTED"))
    }
}
```

- [ ] **Step 2: Run the test and confirm it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.teamz.lab.debugger.quality.TestDoneSheetWiringTest" -q`
Expected: FAIL. `TestDoneSheet.kt` is missing (FileNotFoundException), or the AssertionError on
`requestNow`.

- [ ] **Step 3: Write the implementation**

`WidgetPinPrompt.kt`. Add inside the object, after `maybePrompt`:
```kotlin
    /** The user tapped "Add widget": ask the launcher now. No RC flag, no once-per-install. */
    fun requestNow(context: Context): Boolean = try {
        val awm = context.getSystemService<AppWidgetManager>()
        if (awm == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.O || !awm.isRequestPinAppWidgetSupported) false
        else {
            val successIntent = PendingIntent.getBroadcast(
                context, PIN_SUCCESS_REQUEST_CODE,
                Intent(context, WidgetPinResultReceiver::class.java).setAction(ACTION_WIDGET_PIN_SUCCESS),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            awm.requestPinAppWidget(ComponentName(context, LockScreenMonitorWidget::class.java), null, successIntent)
        }
    } catch (t: Throwable) { false }
```

`TestDoneSheet.kt`:
```kotlin
package com.teamz.lab.debugger.ui

import android.Manifest
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import com.teamz.lab.debugger.utils.AnalyticsEvent
import com.teamz.lab.debugger.utils.AnalyticsUtils
import com.teamz.lab.debugger.utils.TestDoneCard
import com.teamz.lab.debugger.utils.WidgetPinPrompt

/** One-time card after the first completed test (arm B). Spec 2026-10-09. */
@Composable
fun TestDoneSheet() {
    val visible by TestDoneCard.visible.collectAsState()
    if (!visible) return
    val context = LocalContext.current
    fun action(a: String) = try {
        AnalyticsUtils.logEvent(AnalyticsEvent.FsDoneCardAction, mapOf("action" to a))
    } catch (_: Throwable) { }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        try { AnalyticsUtils.logEvent(AnalyticsEvent.FsNotifPermissionResult, mapOf("granted" to granted)) } catch (_: Throwable) { }
        if (granted) Toast.makeText(context, "Weekly check-up is on", Toast.LENGTH_SHORT).show()
        TestDoneCard.dismiss()
    }
    AlertDialog(
        onDismissRequest = { action("dismiss"); TestDoneCard.dismiss() },
        title = { Text("Done ✓") },
        text = { Text("Want to keep an eye on your phone's health?") },
        confirmButton = {
            TextButton(onClick = {
                action("widget")
                WidgetPinPrompt.requestNow(context)
                TestDoneCard.dismiss()
            }) { Text("Add widget") }
        },
        dismissButton = {
            TextButton(onClick = {
                action("weekly")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    Toast.makeText(context, "Weekly check-up is on", Toast.LENGTH_SHORT).show()
                    TestDoneCard.dismiss()
                }
            }) { Text("Weekly check-up") }
            TextButton(onClick = { action("dismiss"); TestDoneCard.dismiss() }) { Text("Not now") }
        },
    )
}
```

`DeviceGptNavExperience.kt`. Insert immediately before the line `if (showRewardedReportOffer) {`:
```kotlin
    com.teamz.lab.debugger.ui.TestDoneSheet()
```

- [ ] **Step 4: Run the tests and confirm they pass**

Run the Step 2 command (3 tests, 0 failures), then `./gradlew :app:testDebugUnitTest -q`
(all green).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/teamz/lab/debugger/utils/WidgetPinPrompt.kt app/src/main/java/com/teamz/lab/debugger/ui/TestDoneSheet.kt app/src/main/java/com/teamz/lab/debugger/ui/adaptive/DeviceGptNavExperience.kt app/src/test/java/com/teamz/lab/debugger/quality/TestDoneSheetWiringTest.kt
git commit -m "feat(fs-ab): Done card with widget + weekly check-up permission (arm B)"
```

---

### Task 5: Device verification, mutation check, vc52 rebuild

**Files:** none new (verification only). If you find a defect, add a failing test for it, then
fix it.

- [ ] **Step 1: Force arm B on the emulator (debug build)**

```bash
./gradlew :app:assembleDebug -q
adb -s emulator-5554 uninstall com.teamz.lab.debugger
adb -s emulator-5554 install -g app/build/outputs/apk/debug/app-debug.apk
adb -s emulator-5554 shell "run-as com.teamz.lab.debugger sh -c 'mkdir -p shared_prefs && echo \"<?xml version=\\\"1.0\\\" encoding=\\\"utf-8\\\" standalone=\\\"yes\\\" ?><map><string name=\\\"fs_arm\\\">B</string></map>\" > shared_prefs/first_screen_experiment.xml'"
adb -s emulator-5554 shell monkey -p com.teamz.lab.debugger -c android.intent.category.LAUNCHER 1
```
Expected:
- The score screen shows "What do you want to test?" with Camera / Mic / Screen.
- Tapping Mic opens the "Mic & Screen" tab.
- Finishing a mic test shows "Done ✓" once.
- "Weekly check-up" shows the Android notification permission prompt.
- No paywall and no full-screen ad (QuietPeriod).

- [ ] **Step 2: Arm A on a fresh install shows the old screen**

Uninstall, install, launch, and don't write the prefs file. Expected: "Share my score" and
"See details", and the widget prompt after "See details" (same as today).

- [ ] **Step 3: Mutation check**

For each guard, remove it, run its test class and expect a FAIL, then restore it from a file
copy. NEVER restore with `git checkout` while there are uncommitted changes.
- the `isB` guard in `TestDoneCard.shouldShow`
- the `KEY_SHOWN` check
- `tabFor("mic")`
- the arm-B guard on the widget prompt in MainActivity

- [ ] **Step 4: Full suite, then rebuild the vc52 bundle**

```bash
./gradlew :app:testDebugUnitTest -q
./gradlew :app:bundleRelease :app:uploadCrashlyticsMappingFileRelease -q
```
Expected: everything green. AAB versionCode 52.

- [ ] **Step 5: Commit and push**

```bash
git push origin main
```

---

### Release-time step (not part of the build; needs the owner's yes)
- After vc52 is live, add the Remote Config parameter `first_screen_test_chooser`:
  - a condition `fs_ab_50` with percentile ≤ 50 (`percent` operator, its own seed)
  - value `true` for `fs_ab_50`, default `false`
- Use the REST flow in `docs/retention/TRACKING-vc50-vc51.md`: ETag via `Accept-Encoding: gzip`,
  `validateOnly` first, back up the current template.
- Until then every user is arm A (bundled default `false`), so shipping vc52 without the
  condition is safe.
