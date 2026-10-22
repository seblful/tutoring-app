package com.seblful.klas

import android.app.Activity
import android.content.Context
import android.graphics.drawable.Animatable
import android.os.Looper
import android.view.View
import android.widget.FrameLayout
import androidx.core.content.ContextCompat
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
class SplashHandoverTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun `the handover clears the splash and leaves the app at full size`() {
        // Attached, as in the app: a view property animation starts from the window's run queue.
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val content = View(activity)
        val icon = View(activity)
        val splash = FrameLayout(activity).apply { addView(icon) }
        activity.setContentView(FrameLayout(activity).apply { addView(content); addView(splash) })
        var removed = 0

        fadeThrough(splash, icon, content) { removed++ }
        assertEquals(0.92f, content.scaleX)

        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1))

        assertEquals(1, removed)
        assertEquals(0f, splash.alpha)
        assertEquals(0f, icon.alpha)
        assertEquals(1f, content.scaleX)
        assertEquals(1f, content.scaleY)
    }

    @Test
    fun `the splash mark is an animated vector at the splash icon size`() {
        val mark = ContextCompat.getDrawable(context, R.drawable.ic_splash_logo)!!
        val px = (240 * context.resources.displayMetrics.density).toInt()

        assertTrue(mark is Animatable)
        assertEquals(px, mark.intrinsicWidth)
        assertEquals(px, mark.intrinsicHeight)
    }
}
