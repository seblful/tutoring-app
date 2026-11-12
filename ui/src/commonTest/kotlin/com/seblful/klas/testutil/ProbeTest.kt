package com.seblful.klas.testutil

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class ProbeTest {
    @Test
    fun opensARealDatabase() = runTest {
        TestEnv().use { env ->
            Fixtures(env).student()
            assertEquals(1, env.stored().students.size)
        }
    }
}
