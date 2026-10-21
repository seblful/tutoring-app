package com.seblful.klas.domain.lesson

import com.seblful.klas.domain.model.Money
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ChargeInvariantsTest {
    @Test
    fun `generated charges stay nonnegative monotone and within half a minor unit of the exact price`() {
        val seed = 61703
        val random = Random(seed)
        repeat(500) { case ->
            val rate = Money(random.nextLong(Money.MAX.minor + 1))
            val duration = random.nextInt(1, 1440)
            checkCharge(rate, duration, "seed=$seed case=$case")
        }
        for (rate in listOf(Money.ZERO, Money(1), Money.MAX)) {
            for (duration in listOf(1, 29, 30, 59, 60, 61, 1439, Int.MAX_VALUE)) {
                checkCharge(rate, duration, "rate=$rate duration=$duration")
            }
        }
    }

    private fun checkCharge(rate: Money, duration: Int, context: String) {
        val charge = ClassRules.chargeFor(rate, duration)
        val exactNumerator = rate.minor * duration
        // An integer error bound checks rounding independently of the implementation's division.
        val roundingError = charge.minor * 60 - exactNumerator
        assertTrue(roundingError in -29L..30L, context)
        assertTrue(charge >= Money.ZERO, context)
        if (duration < Int.MAX_VALUE) {
            assertTrue(ClassRules.chargeFor(rate, duration + 1) >= charge, context)
        }
        assertEquals(rate, ClassRules.chargeFor(rate, 60), context)
    }
}
