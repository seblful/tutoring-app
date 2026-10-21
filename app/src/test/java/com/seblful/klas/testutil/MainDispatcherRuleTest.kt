package com.seblful.klas.testutil

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.Description
import org.junit.runners.model.Statement

@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRuleTest {
    @Test
    fun `owned work is cancelled even when the test fails`() {
        val rule = MainDispatcherRule()
        var cleanedUp = false
        var cleared = false
        rule.own(object : ViewModel() {
            override fun onCleared() {
                cleared = true
            }
        })
        val work = rule.appScope.launch {
            try {
                awaitCancellation()
            } finally {
                cleanedUp = true
            }
        }
        val failure = AssertionError("test failed")
        try {
            rule.apply(
                object : Statement() {
                    override fun evaluate() = throw failure
                },
                Description.EMPTY,
            ).evaluate()
        } catch (caught: AssertionError) {
            assertTrue(caught === failure)
        }
        assertFalse(work.isActive)
        assertTrue(cleanedUp)
        assertTrue(cleared)
    }
}
