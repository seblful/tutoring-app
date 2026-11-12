package com.seblful.klas.testutil

import com.seblful.klas.data.backup.RestoreSnapshots
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.time.Instant

/** [RestoreSnapshots] in memory: the copy a restore keeps, readable by the test that made it. */
class InMemoryRestoreSnapshots : RestoreSnapshots {
    var kept: String? = null
        private set

    private val _keptAt = MutableStateFlow<Instant?>(null)
    override val keptAt: StateFlow<Instant?> = _keptAt.asStateFlow()

    override suspend fun save(json: String, takenAt: Instant) {
        kept = json
        _keptAt.value = takenAt
    }

    override suspend fun read(): String? = kept

    override suspend fun clear() {
        kept = null
        _keptAt.value = null
    }
}
