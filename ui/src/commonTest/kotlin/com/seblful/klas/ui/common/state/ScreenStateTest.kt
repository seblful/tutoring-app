package com.seblful.klas.ui.common.state

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore
import com.seblful.klas.testutil.TestMain
import com.seblful.klas.testutil.UiTest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ScreenStateTest : UiTest() {

    @BeforeTest
    fun startMain() = main.start()

    @AfterTest
    fun finishMain() = main.finish()
    private val main = TestMain()

    @Test
    fun `derived work releases its subscription after the grace period`() = runTest(main.dispatcher) {
        var collecting = false
        val owner = object : ViewModel() {}
        val owners = ViewModelStore().apply { put("screen", owner) }
        try {
            val state = flow {
                collecting = true
                try {
                    emit(42)
                    awaitCancellation()
                } finally {
                    collecting = false
                }
            }.derive(owner, main.dispatcher, 0)
            assertFalse(collecting)
            val subscription = backgroundScope.launch { state.collect {} }
            runCurrent()
            assertEquals(42, state.value)
            assertTrue(collecting)

            subscription.cancel()
            runCurrent()
            advanceTimeBy(4_999)
            assertTrue(collecting)
            advanceTimeBy(1)
            runCurrent()
            assertFalse(collecting)
        } finally {
            owners.clear()
        }
    }
}
