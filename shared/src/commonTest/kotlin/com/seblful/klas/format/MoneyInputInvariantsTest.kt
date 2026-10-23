package com.seblful.klas.format

import com.seblful.klas.domain.model.Money
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals

class MoneyInputInvariantsTest {
    @Test
    fun `every generated valid input amount reads back in all supported decimal conventions`() {
        val seed = 61705
        val random = Random(seed)
        val amounts = listOf(0L, 1, 99, 100, 101, Money.MAX.minor) +
            List(500) { random.nextLong(Money.MAX.minor + 1) }
        amounts.forEachIndexed { case, minor ->
            val money = Money(minor)
            for (decimal in listOf('.', ',', '\u066B')) {
                assertEquals(
                    money, parseMoneyOrNull(money.amountText(decimal)),
                    "seed=$seed case=$case decimal=$decimal minor=$minor",
                )
            }
        }
    }
}
