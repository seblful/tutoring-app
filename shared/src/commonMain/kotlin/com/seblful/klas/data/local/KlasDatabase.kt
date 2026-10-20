package com.seblful.klas.data.local

/** Owns the database lifetime; hosts do not need its tables or DAOs. */
class KlasDatabase internal constructor(internal val database: AppDatabase) : AutoCloseable {
    override fun close() = database.close()
}
