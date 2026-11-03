package com.shikkhasathi.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Critical user journeys for the profile: cold start to the home dashboard (the startup profile), then the bottom-bar
 * tabs a student opens first. Run with `./gradlew :android:generateReleaseBaselineProfile`.
 *
 * The journey needs a signed-in debug session on the device (sign in once with the dev OTP from a local server),
 * otherwise it profiles the sign-in screen, which is also a valid cold-start path.
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {
    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generate() =
        rule.collect(packageName = TARGET_PACKAGE, includeInStartupProfile = true) {
            pressHome()
            startActivityAndWait()
            device.wait(Until.hasObject(By.text("হোম")), TIMEOUT_MS)
            listOf("ক্লাসরুম", "কোর্স", "Exam", "প্রোফাইল", "হোম").forEach { tab ->
                device.findObject(By.text(tab))?.click()
                device.waitForIdle()
            }
        }

    companion object {
        const val TARGET_PACKAGE = "com.shikkhasathi.app"
        const val TIMEOUT_MS = 5_000L
    }
}
