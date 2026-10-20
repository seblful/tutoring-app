package com.seblful.klas.data.backup

import com.seblful.klas.testutil.TestEnv
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class BackupEnvelopeTest {
    private val env = TestEnv()

    @AfterTest
    fun closeEnvironment() {
        env.close()
    }

    @Test
    fun `unrelated JSON cannot replace existing data or complete onboarding`() = runTest {
        env.fixtures.subject("Physics")
        val before = env.backupManager.export()
        val settings = env.settingsRepo.get()
        for (text in listOf("{}", """{"unrelated":"document"}""", """{"version":9}""")) {
            val failure = runCatching { env.backupRestore.restore(text) }.exceptionOrNull()

            assertEquals(BackupFailure.Malformed, failure?.let(BackupFailure::of), text)
            assertEquals(before, env.backupManager.export(), text)
            assertEquals(settings, env.settingsRepo.get(), text)
            assertTrue(env.calendar.deletes.isEmpty(), text)
        }
    }

    @Test
    fun `a backup requires its version and both identity tables`() {
        for (text in listOf(
            """{"subjects":[],"students":[]}""",
            """{"version":9,"students":[]}""",
            """{"version":9,"subjects":[]}""",
        )) {
            assertFailsWith<kotlinx.serialization.SerializationException>(text) { env.backupManager.parse(text) }
        }
    }

    @Test
    fun `an empty export remains a valid replacement`() = runTest {
        val empty = env.backupManager.exportJson()
        env.fixtures.subject("Physics")

        env.backupRestore.restore(empty)

        assertTrue(env.stored().subjects.isEmpty())
        assertTrue(env.settingsRepo.get().onboardingComplete)
    }

    @Test
    fun `older empty backups remain readable with later tables omitted`() {
        for (version in 1..BackupData.CURRENT_VERSION) {
            val parsed = env.backupManager.parse("""{"version":$version,"subjects":[],"students":[]}""")
            assertEquals(BackupData(version = version), parsed)
        }
    }
}
