// SPDX-License-Identifier: GPL-3.0-or-later
package com.example.transversa

import android.content.ContentValues
import android.content.Intent
import android.os.Environment
import android.provider.MediaStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject
import androidx.test.uiautomator.UiScrollable
import androidx.test.uiautomator.UiSelector
import androidx.test.uiautomator.Until
import androidx.test.uiautomator.UiObjectNotFoundException
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import java.util.UUID
import java.util.concurrent.TimeUnit

/** End-to-end smoke coverage for the user-facing Android flows. Run on an arm64 emulator. */
@RunWith(AndroidJUnit4::class)
class TransversaEndToEndTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val targetContext get() = instrumentation.targetContext
    private val device by lazy { UiDevice.getInstance(instrumentation) }
    private var fixtureUri: android.net.Uri? = null
    private var fixtureName: String? = null

    @Before
    fun resetAppDataAndWaitForStartup() {
        targetContext.getSharedPreferences("transversa_preferences", 0).edit().clear().commit()
        targetContext.getSharedPreferences("transversa_user_preferences", 0).edit().clear().commit()
        targetContext.getSharedPreferences("transversa_conversion_history", 0).edit().clear().commit()
        targetContext.getSharedPreferences("transversa_runtime_state", 0).edit().clear().commit()
        val launch = targetContext.packageManager.getLaunchIntentForPackage(targetContext.packageName)
            ?: error("Could not find the Transversa launch activity")
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        targetContext.startActivity(launch)
        assertText("Get Started", "Convert screen did not finish starting")
    }

    @After
    fun removeTestMedia() {
        fixtureUri?.let { targetContext.contentResolver.delete(it, null, null) }
        fixtureUri = null
    }

    @Test
    fun navigationHistoryThemeDonateAndLicenseFlowsWork() {
        click("History")
        assertText("Your conversions will appear here.", "Empty history state is missing")
        val historySwitch = device.findObject(UiSelector().checkable(true))
        assertNotNull("History enabled switch is missing", historySwitch.takeIf { it.exists() })
        historySwitch.click()
        assertText("History is disabled. Turn it on to keep completed conversions here.", "Disabling history did not update its empty state")
        historySwitch.click()
        assertText("Your conversions will appear here.", "Enabling history did not restore the empty state")

        click("Info")
        click("Light Mode")
        val notificationSwitch = device.findObject(UiSelector().checkable(true).instance(3))
        assertNotNull("Notifications switch is missing", notificationSwitch.takeIf { it.exists() })
        assertFalse("Notifications should be opt-in", notificationSwitch.isChecked)
        notificationSwitch.click()
        val allowButton = device.wait(Until.findObject(By.text("Allow")), 3_000)
        allowButton?.click()
        device.waitForIdle()
        assertTrue("Selecting Notifications in Info should enable notifications", notificationSwitch.isChecked)
        notificationSwitch.click()
        assertFalse("Notifications switch should turn off", notificationSwitch.isChecked)
        scrollTo("Donate")
        click("Donate")
        assertText("Support Transversa", "Donation sheet did not open")
        assertText("Buy me a beer", "Beer donation option is missing")
        assertText("Fatten me up with a Donut", "Donut donation option is missing")
        assertText("Keep me warm with some new socks", "Socks donation option is missing")
        click("Close")

        scrollTo("Software licenses")
        clickDescription("Expand Software licenses")
        scrollTo("GNU GPL version 3")
        click("GNU GPL version 3")
        assertNotNull(
            "License text did not open",
            device.wait(Until.findObject(By.textContains("Version 3, 29 June 2007")), 10_000),
        )
    }

    @Test
    fun customPresetCanBeCreatedEditedAndDeleted() {
        click("Presets")
        click("Images")
        scrollTo("Add preset")
        click("Add preset")
        assertText("Preset name", "Preset creation dialog did not open")
        val nameField = device.findObject(UiSelector().className("android.widget.EditText"))
        assertNotNull("Preset name text field is missing", nameField.takeIf { it.exists() })
        nameField.setText("E2E Image")
        assertText("E2E Image", "Preset name was not entered")
        device.waitForIdle()
        Thread.sleep(350)
        val createButton = device.findObject(UiSelector().text("Create preset"))
        assertTrue("Create preset should be enabled after entering a name", createButton.isEnabled)
        click("Create preset")

        assertText("Format and quality", "New preset did not open in its editor")
        assertText("E2E Image", "New preset name is not shown in the editor")
        scrollTo("Save changes")
        click("Save changes")
        click("Presets")
        device.wait(Until.findObject(By.desc("Expand Images presets")), 1_000)?.click()
        assertText("E2E Image", "Saved custom preset is missing from the Images list")
        clickDescription("Edit")
        assertText("Format and quality", "Saved preset did not reopen in its editor")
        clickDescription("Delete preset")
        assertText("Delete preset?", "Preset deletion confirmation did not open")
        click("Delete")
        assertGone(By.text("E2E Image"), "Deleted preset is still displayed")
    }

    @Test
    fun selectedImageCanBeConvertedAndSharedThroughAndroidChooser() {
        fixtureUri = createImageFixture()
        val name = fixtureName ?: error("Fixture name missing")

        click("Get Started")
        assertText(name, "Test image was not listed in Downloads")
        device.findObject(By.text(name)).click()

        assertText("Share", "Selected image did not open the conversion screen")
        assertText("Save", "Save action is missing for the selected image")
        assertText("Simple", "Default image preset is missing")
        device.findObject(By.text("Share")).click()

        assertText(
            "Transversa",
            "Image conversion did not finish and open Android's share chooser",
            timeoutMs = TimeUnit.SECONDS.toMillis(120),
        )
        device.pressBack()
        assertText("Opening Android share options…", "Completed share status is missing")
        click("History")
        val outputName = "${name.substringBeforeLast('.')}.png"
        assertText(outputName, "Converted image was not recorded in history")
        click("Clear")
        assertText("Your conversions will appear here.", "History clear did not remove the test conversion")
    }

    private fun click(text: String) {
        val matches = device.findObjects(By.text(text))
        assertTrue("Could not find '$text'", matches.isNotEmpty())
        matches.last().click()
        device.waitForIdle()
    }

    private fun clickDescription(description: String) {
        val selector = By.desc(description)
        assertNotNull("Could not find '$description'", device.wait(Until.findObject(selector), 10_000))
        val matches = device.findObjects(selector)
        assertTrue("Could not find clickable '$description'", matches.isNotEmpty())
        matches.last().click()
        device.waitForIdle()
    }

    private fun scrollTo(text: String) {
        if (device.wait(Until.findObject(By.text(text)), 500) != null) return
        try {
            UiScrollable(UiSelector().scrollable(true)).setAsVerticalList()
                .scrollIntoView(UiSelector().text(text))
        } catch (_: UiObjectNotFoundException) {
            val centerX = device.displayWidth / 2
            repeat(5) {
                if (device.wait(Until.findObject(By.text(text)), 250) != null) return
                device.swipe(centerX, device.displayHeight * 4 / 5, centerX, device.displayHeight / 3, 10)
            }
        }
        assertText(text, "Could not scroll to '$text'")
    }

    private fun assertText(text: String, message: String, timeoutMs: Long = 10_000): UiObject {
        val selector = By.text(text)
        assertNotNull(message, device.wait(Until.findObject(selector), timeoutMs))
        return textObject(text)
    }

    private fun assertGone(selector: androidx.test.uiautomator.BySelector, message: String) {
        val gone = device.wait(Until.gone(selector), 5_000)
        if (!gone) throw AssertionError(message)
    }

    private fun textObject(text: String): UiObject = device.findObject(UiSelector().text(text))

    private fun createImageFixture(): android.net.Uri {
        val name = "transversa-e2e-${UUID.randomUUID().toString().takeLast(8)}.jpg"
        fixtureName = name
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val resolver = targetContext.contentResolver
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: error("Could not create test image in Downloads")
        try {
            resolver.openOutputStream(uri)?.use { output ->
                targetContext.assets.open("source.jpg").use { input -> input.copyTo(output) }
            } ?: error("Could not write test image")
            values.clear()
            values.put(MediaStore.MediaColumns.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        } catch (failure: Throwable) {
            resolver.delete(uri, null, null)
            throw failure
        }
        return uri
    }
}
