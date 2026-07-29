package com.seblful.klas.testutil

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * How many places still seed a test by writing rows straight to a fake DAO. **This number may only go
 * down.**
 *
 * Seeding through the DAOs skips the repository that owns the invariant, so a test can set up a state
 * the app cannot reach and then assert about it. It also means every test builds its own rows, and they
 * disagree: "a completed class" existed in three versions, one setting `chargeMinor` and leaving
 * `priceMinor` at zero, one hard-coding `priceMinor = 20` whatever charge it was passed, and one
 * omitting `chargeMinor` altogether. All three passed, because each asserted only the field it had
 * remembered to fill in. [Fixtures] books and completes a lesson the way the app does, so the charge is
 * whatever `ClassRules` says.
 *
 * There were 341 such calls across 25 files when [Fixtures] was introduced. Migrating them all in one
 * change would have been a 25-file rewrite with no way to tell a mechanical substitution from a
 * behavioural one, so it is being done a file at a time — and this test is what makes that a shrinking
 * number rather than an intention. A new test that seeds directly pushes the count over the cap and
 * fails here; a migrated file lowers it, and the cap comes down with it.
 *
 * It is a ratchet, not a ban. Reaching for a DAO to build something the repositories deliberately
 * refuse — a malformed row on a read path — is a real need, and lowering the cap by hand is how such a
 * call gets acknowledged rather than absorbed.
 */
class DirectSeedingRatchetTest {

    /**
     * Lower this when you migrate a file to [Fixtures]. Never raise it: if a change needs a direct
     * write, say why in the test that needs it and take the number down somewhere else first.
     */
    private val cap = 254

    /**
     * Writes through a fake DAO. Matches the field name loosely (`classDao`, `rateHistoryDao`) so a new
     * DAO cannot slip past under an unfamiliar name; the method list is the fakes' write surface.
     */
    private val seedCall = Regex("""[A-Za-z]*[Dd]ao\.(?:insertAll|upsert|insert)\(""")

    @Test
    fun `direct DAO seeding only ever gets rarer`() {
        val counts = ProjectFiles.testSources()
            // `testutil` is where the doubles and the fixtures live: writing rows is its job.
            .filterNot { it.parentFile?.name == "testutil" }
            .associate { it.name to seedCall.findAll(it.readText()).count() }
            .filterValues { it > 0 }
        val total = counts.values.sum()

        val worst = counts.entries.sortedByDescending { it.value }
            .joinToString("\n") { "  ${it.value}\t${it.key}" }
        assertTrue(
            "Tests seed rows straight through $total fake-DAO writes, over the cap of $cap. Seed " +
                "through `env.fixtures` instead — it goes in through the repositories, so the " +
                "invariant is applied and the charge is the production rule's. If a direct write is " +
                "genuinely needed, lower the cap in this test to pay for it.\n$worst",
            total <= cap,
        )
    }

    /**
     * The cap tracks the count. Left slack, the cap stops describing anything: a file gets migrated,
     * the number drifts down, and the ratchet quietly has room for eleven new direct writes that nobody
     * would see. So a migration has to bring the cap with it.
     */
    @Test
    fun `the cap is not stale`() {
        val total = ProjectFiles.testSources()
            .filterNot { it.parentFile?.name == "testutil" }
            .sumOf { seedCall.findAll(it.readText()).count() }

        assertTrue(
            "Only $total direct fake-DAO seed calls are left but the cap is still $cap. Lower it to " +
                "$total so the next one that appears is caught.",
            total >= cap,
        )
    }
}
