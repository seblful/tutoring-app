package com.seblful.klas.ui.common

import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * The distinction the list screens were missing: **empty is an answer, and the absence of one is not.**
 *
 * The screens used to branch on `rows.isEmpty()`, and the initial value of every one of them was an
 * empty collection — so for the frame or two before Room's first emission, "nothing has loaded" and
 * "there is nothing" were the same value. A tutor with forty students was invited to add their first;
 * the schedule stated «Нет занятий» over their actual morning. These assertions are about the type that
 * makes those two cases impossible to confuse rather than about any one screen.
 */
class LoadableTest {

    @Test
    fun `an empty list is Ready, not Loading`() {
        val state: Loadable<List<String>> = Loadable.Ready(emptyList())

        // The whole point: this is a legitimate, loaded answer and must be readable as one.
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

    /**
     * A flow that emits nothing emits nothing — `ready()` must not manufacture an arrival. This is the
     * failure mode that would put the bug straight back: an initial value that looks loaded.
     */
    @Test
    fun `ready adds no emission of its own`() = runTest {
        assertEquals(emptyList<Loadable<List<String>>>(), flowOf<List<String>>().ready().toList())
    }

    @Test
    fun `map transforms a loaded value`() {
        val state: Loadable<List<String>> = Loadable.Ready(listOf("Аня", "Борис"))

        assertEquals(Loadable.Ready(2), state.map { it.size })
    }

    /**
     * `map` over [Loadable.Loading] stays loading — which is what lets the schedule hand one day's rows
     * to its list without the list having to know that the *week's* sections are what actually loaded.
     */
    @Test
    fun `map over Loading stays Loading, and does not run the transform`() {
        var ran = false
        val mapped = (Loadable.Loading as Loadable<List<String>>).map { ran = true; it.size }

        assertSame(Loadable.Loading, mapped)
        assertEquals(false, ran)
    }
}
