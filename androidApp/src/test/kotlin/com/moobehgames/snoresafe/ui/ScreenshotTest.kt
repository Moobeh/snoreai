package com.moobehgames.snoresafe.ui

import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.moobehgames.snoresafe.billing.TipJar
import com.moobehgames.snoresafe.core.analysis.NightSound
import com.moobehgames.snoresafe.core.analysis.SnoreBand
import com.moobehgames.snoresafe.core.settings.AppSettings
import com.moobehgames.snoresafe.recording.RecordingState
import com.moobehgames.snoresafe.ui.theme.SnoreSafeTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import kotlin.test.assertTrue

/**
 * Renders every main screen with sample data. Doubles as a smoke test (each screen must compose
 * and draw) and produces 1080×1920 Play Store screenshots in build/screenshots/.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h640dp-port-night-xxhdpi")
class ScreenshotTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private val outDir = File("build/screenshots").apply { mkdirs() }
    private val noop: () -> Unit = {}

    private fun shoot(name: String, content: @Composable () -> Unit) {
        compose.setContent { SnoreSafeTheme(forceNight = true) { content() } }
        compose.waitForIdle()
        // Draw the window straight into a bitmap (Compose's capture API times out under Robolectric).
        val view = compose.activity.window.decorView
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        assertTrue(bitmap.width > 0 && bitmap.height > 0)
        File(outDir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private val night = SampleData.night(0, intensity = 0.9f, seed = 11)

    @Test fun home() = shoot("01-home") {
        HomeScreen(SampleData.history(), false, 0, false, noop, {}, noop, noop)
    }

    @Test fun results() = shoot("02-results") {
        ResultsScreen(night, SampleData.audio(night), ClipPlayer(), false, { File("x") }, noop, { _, _ -> }, {}, {}, {}, noop, noop)
    }

    /** Whole results page in one tall image, to check the lower sections render. Not a store screenshot. */
    @Test @Config(qualifiers = "w360dp-h2000dp-port-night-xxhdpi")
    fun resultsFullPage() = shoot("x-results-full") {
        ResultsScreen(night, SampleData.audio(night), ClipPlayer(), true, { File("x") }, noop, { _, _ -> }, {}, {}, {}, noop, noop)
    }

    @Test fun trends() = shoot("03-trends") {
        TrendsScreen(SampleData.history(), {}, noop)
    }

    @Test fun recording() = shoot("04-recording") {
        RecordingScreen(
            RecordingState.Active(System.currentTimeMillis() - 9_000_000, 9_013_000, false, false, SnoreBand.QUIET, 0f, 23, NightSound.FAN_OR_AC),
            noop, noop, noop,
        )
    }

    @Test fun settings() = shoot("05-settings") {
        SettingsScreen(AppSettings(onboardingDone = true), {}, SettingsActions(noop, noop, noop, noop, noop, noop, noop, noop, noop, noop))
    }

    @Test fun onboarding() = shoot("06-onboarding") {
        OnboardingScreen(false, noop, noop)
    }

    @Test fun support() = shoot("07-support") {
        SupportScreen(TipJar.State.Ready(mapOf(TipJar.Tip.COFFEE to "£1.99", TipJar.Tip.LUNCH to "£4.99", TipJar.Tip.THANKS to "£9.99")), false, {}, noop, noop)
    }

    @Test fun storage() = shoot("08-storage") {
        val h = SampleData.history()
        StorageScreen(AppSettings(), 184_000_000, 21_000_000_000, h, h.take(6).map { SampleData.audio(it) }, {}, noop, {}, noop)
    }
}
