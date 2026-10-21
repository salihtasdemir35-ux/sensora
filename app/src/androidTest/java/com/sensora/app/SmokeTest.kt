package com.sensora.app

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Launches the app on a real device/emulator and checks the home screen renders without crashing. */
@RunWith(AndroidJUnit4::class)
class SmokeTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    @Test fun homeScreenShowsAppName() {
        assertTrue(rule.onAllNodesWithText("Sensora").fetchSemanticsNodes().isNotEmpty())
    }
}
