package com.seblful.klas.ui.common.state

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.time.Duration.Companion.milliseconds

class LoadableTest {

    @Test
    fun `an empty list is Ready — not Loading`() {
        val state: Loadable<List<String>> = Loadable.Ready(emptyList())

        assertEquals(emptyList<String>(), state.valueOrNull())
    }

    @Test
    fun `Loading carries no value to mistake for one`() {
        assertNull(Loadable.Loading.valueOrNull())
    }

    @Test
    fun `ready marks every emission as arrived`() = runTest {
        val emitted = flowOf(listOf("Аня"), emptyList()).ready().toList()

        assertEquals(
            listOf(Loadable.Ready(listOf("Аня")), Loadable.Ready(emptyList<String>())),
            emitted,
        )
    }

    @Test
    fun `ready adds no emission of its own`() = runTest {
        assertEquals(emptyList<Loadable<List<String>>>(), flowOf<List<String>>().ready().toList())
    }

    @Test
    fun `an answer inside the grace arrives without a Loading before it`() = runTest {
        val emitted = flow {
            delay(100.milliseconds)
            emit("вторник")
        }.readyOrLoadingAfter(250.milliseconds).toList()

        assertEquals(listOf(Loadable.Ready("вторник")), emitted)
    }

    @Test
    fun `an answer later than the grace is preceded by Loading`() = runTest {
        val emitted = flow {
            delay(400.milliseconds)
            emit("вторник")
            delay(1_000.milliseconds)
            emit("среда")
        }.readyOrLoadingAfter(250.milliseconds).toList()

        assertEquals(
            listOf(Loadable.Loading, Loadable.Ready("вторник"), Loadable.Ready("среда")),
            emitted,
        )
    }

    @Test
    fun `map transforms a loaded value`() {
        val state: Loadable<List<String>> = Loadable.Ready(listOf("Аня", "Борис"))

        assertEquals(Loadable.Ready(2), state.map { it.size })
    }

    @Test
    fun `map over Loading stays Loading — and does not run the transform`() {
        var ran = false
        val mapped = (Loadable.Loading as Loadable<List<String>>).map { ran = true; it.size }

        assertSame(Loadable.Loading, mapped)
        assertEquals(false, ran)
    }
}
