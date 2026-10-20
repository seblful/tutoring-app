package com.seblful.klas.testutil

import org.junit.Assert.assertTrue
import org.junit.Test

class DirectSeedingRatchetTest {

    private val cap = 251

    private val seedCall = Regex("""[A-Za-z]*[Dd]ao\.(?:insertAll|upsert|insert)\(""")

    @Test
    fun `direct DAO seeding only ever gets rarer`() {
        val counts = ProjectFiles.testSources()
            .filterNot { it.parentFile?.name == "testutil" }
            .associate { it.name to seedCall.findAll(it.readText()).count() }
            .filterValues { it > 0 }
        val total = counts.values.sum()

        val worst = counts.entries.sortedByDescending { it.value }
            .joinToString("\n") { "  ${it.value}\t${it.key}" }
        assertTrue(
            "Tests seed rows straight through $total DAO writes, over the cap of $cap. Seed " +
                "through `env.fixtures` instead — it goes in through the repositories, so the " +
                "invariant is applied and the charge is the production rule's. If a direct write is " +
                "genuinely needed, lower the cap in this test to pay for it.\n$worst",
            total <= cap,
        )
    }

    @Test
    fun `the cap is not stale`() {
        val total = ProjectFiles.testSources()
            .filterNot { it.parentFile?.name == "testutil" }
            .sumOf { seedCall.findAll(it.readText()).count() }

        assertTrue(
            "Only $total direct DAO seed calls are left but the cap is still $cap. Lower it to " +
                "$total so the next one that appears is caught.",
            total >= cap,
        )
    }
}
