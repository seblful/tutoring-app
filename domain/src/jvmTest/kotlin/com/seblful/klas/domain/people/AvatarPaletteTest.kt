package com.seblful.klas.domain.people

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AvatarPaletteTest {

    private val size = 10

    @Test
    fun `the first students each get their own colour`() {
        // The reported bug: the student added during onboarding (id 1) wore the same colour as the
        // second student (id 2). Sequential ids must never collide inside one palette's worth.
        val used = (1L..size).map { AvatarPalette.indexFor(it, size) }
        assertEquals("every one of the first $size students is a distinct colour", size, used.toSet().size)
    }

    @Test
    fun `the onboarding student and the second student differ`() {
        assertNotEquals(AvatarPalette.indexFor(1, size), AvatarPalette.indexFor(2, size))
    }

    @Test
    fun `the same student always gets the same colour`() {
        assertEquals(AvatarPalette.indexFor(7, size), AvatarPalette.indexFor(7, size))
    }

    @Test
    fun `the palette wraps once it runs out of colours`() {
        // Unavoidable with a fixed palette — asserted so the wrap is a decision, not a surprise.
        assertEquals(AvatarPalette.indexFor(3, size), AvatarPalette.indexFor(3L + size, size))
    }

    @Test
    fun `every id lands inside the palette, including zero and negatives`() {
        val ids = listOf(0L, -1L, -9L, -10L, -11L, 1L, 999_999L, Long.MAX_VALUE, Long.MIN_VALUE)
        ids.forEach { id ->
            val i = AvatarPalette.indexFor(id, size)
            assertTrue("id $id gave out-of-range index $i", i in 0 until size)
        }
    }

    @Test
    fun `a single-colour palette is legal and always picks it`() {
        assertEquals(0, AvatarPalette.indexFor(42, 1))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `an empty palette is refused rather than dividing by zero`() {
        AvatarPalette.indexFor(1, 0)
    }
}
