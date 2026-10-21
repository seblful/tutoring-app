package com.seblful.klas.testutil

class TestEnv(
    val database: TestDatabase = TestDatabase(),
    clock: MutableClock = testClock(),
) : RepositoryTestEnv(database.db, database.transactor, clock), AutoCloseable {
    override fun close() = database.close()
}
