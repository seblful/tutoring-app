package com.seblful.klas.ui.common

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.seblful.klas.domain.guide.Guide
import com.seblful.klas.domain.guide.Tip
import com.seblful.klas.testutil.AppLocaleRule
import com.seblful.klas.testutil.MainDispatcherRule
import com.seblful.klas.testutil.TestEnv
import com.seblful.klas.ui.designsystem.component.CoachMark
import com.seblful.klas.ui.theme.KlasTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "en-rUS")
class ScreenTipTest {
    @get:Rule(order = 0)
    val env = TestEnv()

    @get:Rule(order = 1)
    val mainRule = MainDispatcherRule()

    @get:Rule(order = 2)
    val compose = createComposeRule()

    @get:Rule(order = 3)
    val language = AppLocaleRule.english()

    private val guide by lazy { GuideViewModel(env.guideRepo, mainRule.appScope) }

    private var blocked by mutableStateOf(false)

    /** The screen's own lifecycle, to pause the way a system dialog over it does. */
    private val screen = object : LifecycleOwner {
        val registry = LifecycleRegistry(this).apply { currentState = Lifecycle.State.RESUMED }
        override val lifecycle: Lifecycle get() = registry
    }
    private val held = mutableListOf<String>()

    /** A screen with two tips to give — the date's, then picking's — on one anchor that a long-press picks. */
    @OptIn(ExperimentalFoundationApi::class)
    private fun show() = compose.setContent {
        KlasTheme {
            CompositionLocalProvider(LocalLifecycleOwner provides screen) {
                val tip = rememberScreenTip(blocked, guide) { seen ->
                    listOf(Tip.SCHEDULE_DATE, Tip.LESSON_PICK).firstOrNull { it !in seen }
                }
                CoachMark(text = "Tip about ${tip.tip}", visible = tip.tip != null, onDismiss = tip.dismiss) {
                    Text(
                        "Anchor ${tip.tip}",
                        Modifier.combinedClickable(onLongClick = { held += "picked" }) { tip.done(Tip.SCHEDULE_DATE) },
                    )
                }
            }
        }
    }

    /** Lets what changed recompose, then the screen sit still past the tip's delay. */
    private fun settle() {
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(2_000)
        compose.waitForIdle()
    }

    private fun seen(): Set<Tip> = runBlocking { env.guideRepo.guide.first().seen }

    @Test
    fun `a tip opens once the screen has settled - and once read, no other follows it in that visit`() {
        show()
        compose.onNodeWithText("Tip about SCHEDULE_DATE").assertDoesNotExist()
        settle()

        compose.onNodeWithText("Tip about SCHEDULE_DATE").assertExists()
        compose.onNodeWithText("Got it").performClick()
        settle()

        assertEquals(setOf(Tip.SCHEDULE_DATE), seen())
        compose.onNodeWithText("Tip about", substring = true).assertDoesNotExist()
    }

    @Test
    fun `nothing opens while something is in front of the screen`() {
        blocked = true
        show()
        settle()
        compose.onNodeWithText("Tip about", substring = true).assertDoesNotExist()

        compose.runOnIdle { blocked = false }
        settle()
        compose.onNodeWithText("Anchor SCHEDULE_DATE").assertExists()
        compose.onNodeWithText("Tip about SCHEDULE_DATE").assertExists()
    }

    @Test
    fun `nothing opens under a system dialog - the tip waits for the screen to be resumed`() {
        screen.registry.currentState = Lifecycle.State.STARTED
        show()
        settle()
        compose.onNodeWithText("Tip about", substring = true).assertDoesNotExist()

        compose.runOnIdle { screen.registry.currentState = Lifecycle.State.RESUMED }
        settle()
        compose.onNodeWithText("Tip about SCHEDULE_DATE").assertExists()
        assertTrue("not read while it was covered", seen().isEmpty())
    }

    @Test
    fun `making the gesture learns it - the tip closes and is not shown again`() {
        show()
        compose.onNodeWithText("Anchor", substring = true).performClick()
        settle()

        assertEquals(setOf(Tip.SCHEDULE_DATE), seen())
        compose.onNodeWithText("Tip about", substring = true).assertDoesNotExist()
    }

    @Test
    fun `a long-press on the anchor stays the anchor's`() {
        runBlocking { env.guideRepo.seen(Tip.SCHEDULE_DATE) }
        runBlocking { env.guideRepo.seen(Tip.LESSON_PICK) }
        show()
        settle()

        compose.onNodeWithText("Anchor", substring = true).performTouchInput { longClick() }
        settle()

        assertEquals(listOf("picked"), held)
        compose.onNodeWithText("Tip about", substring = true).assertDoesNotExist()
    }

    @Test
    fun `a guide marked done shows nothing`() {
        runBlocking { env.settingsRepo.update { it.copy(guide = Guide.DONE) } }
        show()
        settle()

        compose.onNodeWithText("Tip about", substring = true).assertDoesNotExist()
        assertTrue(held.isEmpty())
    }
}
