package com.seblful.klas.testutil

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain

/**
 * The main dispatcher and the view models a test made, for one test.
 *
 * [start] before the test body (view models built in field initializers are built before it, as under a JUnit
 * rule), [finish] after it and before the database closes: it clears the view models and cancels [appScope].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TestMain(val dispatcher: TestDispatcher = UnconfinedTestDispatcher()) {

    private val viewModels = ViewModelStore()
    private var nextOwner = 0

    fun <T : ViewModel> own(viewModel: T): T = viewModel.also {
        viewModels.put("test-${nextOwner++}", it)
    }

    /** Leaving the screen: the view models are cleared, their application writes are not cancelled. */
    fun clearViewModels() = viewModels.clear()

    /**
     * Stands in for the injected `@ApplicationScope` — the scope a view model hands its **writes** to so they
     * outlive the screen. Built on [dispatcher], so `advanceUntilIdle()` drives a write launched into it just
     * like a `viewModelScope` one. Deliberately not a child of the test's scope: cancelling the view model must
     * not cancel the write.
     */
    val appScope: CoroutineScope = CoroutineScope(dispatcher)

    fun start() = Dispatchers.setMain(dispatcher)

    fun finish() {
        try {
            clearViewModels()
        } finally {
            appScope.cancel()
            Dispatchers.resetMain()
        }
    }
}
