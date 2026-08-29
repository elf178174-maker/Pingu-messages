package app.pingu.messages.ui

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A smoke test that the app starts and composes something.
 *
 * Deliberately not asserting which screen appears: on a fresh install that is onboarding, and on a
 * device where the app already holds the SMS role it is the conversation list. Both are a
 * successful launch, and pinning the test to one would make it fail for the wrong reason.
 */
@RunWith(AndroidJUnit4::class)
class AppLaunchTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun theAppStartsAndRendersItsFirstScreen() {
        composeRule.waitForIdle()
        val root = composeRule.onRoot().fetchSemanticsNode()
        assertThat(root.children).isNotEmpty()
    }

    @Test
    fun theActivityIsNotFinishing() {
        composeRule.waitForIdle()
        assertThat(composeRule.activity.isFinishing).isFalse()
    }
}
