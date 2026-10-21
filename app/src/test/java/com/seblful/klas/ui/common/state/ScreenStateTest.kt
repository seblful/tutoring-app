package com.seblful.klas.ui.common.state

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore
import com.seblful.klas.testutil.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ScreenStateTest {
    @get:Rule
    val mainRule = MainDispatcherRule()

    @Test
    fun `derived work releases its subscription after the grace period`() = runTest(mainRule.dispatcher) {
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
            }.derive(owner, mainRule.dispatcher, 0)
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
