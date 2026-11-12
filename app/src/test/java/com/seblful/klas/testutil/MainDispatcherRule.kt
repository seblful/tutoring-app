package com.seblful.klas.testutil

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/** [TestMain] as a JUnit rule: give it rule order 1 and the database rule order 0, so it finishes first. */
@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(dispatcher: TestDispatcher = UnconfinedTestDispatcher()) : TestWatcher() {
    private val main = TestMain(dispatcher)

    val dispatcher: TestDispatcher get() = main.dispatcher
    val appScope: CoroutineScope get() = main.appScope

    fun <T : ViewModel> own(viewModel: T): T = main.own(viewModel)

    fun clearViewModels() = main.clearViewModels()

    override fun starting(description: Description) = main.start()

    override fun finished(description: Description) = main.finish()
}
