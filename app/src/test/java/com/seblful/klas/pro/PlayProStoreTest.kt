package com.seblful.klas.pro

import org.junit.Assert.assertEquals
import org.junit.Test

class PlayProStoreTest {

    @Test
    fun `asks Play for each product type in its own query`() {
        // Play Billing throws building a query that mixes one-time products and subscriptions; the paywall then
        // read it as Play being out of reach and no purchase could start.
        assertEquals(2, PlayProStore.productQueries().size)
    }

    @Test
    fun `reads a trial's length from Play's period`() {
        assertEquals(7, PlayProStore.days("P7D"))
        assertEquals(14, PlayProStore.days("P2W"))
        assertEquals(30, PlayProStore.days("P1M"))
        assertEquals(null, PlayProStore.days("P1Y"))
    }
}
