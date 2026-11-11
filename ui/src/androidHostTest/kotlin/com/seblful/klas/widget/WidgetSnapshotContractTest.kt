package com.seblful.klas.widget

import kotlinx.serialization.json.Json
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Kotlin's half of the snapshot contract; `iosApp/Tests/WidgetSnapshotTests.swift` decodes the same file in
 * Swift. Every field must be in the fixture and nothing else may be, so a field renamed or added on one side
 * fails here or there.
 */
class WidgetSnapshotContractTest {

    @Test
    fun `the fixture the extension is tested against is the snapshot's exact shape`() {
        val fixture = File("../iosApp/Tests/Fixtures/widget-snapshot.json").readText()

        val snapshot = WidgetSnapshot.decode(fixture)

        assertEquals(Json.parseToJsonElement(fixture), Json.parseToJsonElement(snapshot.encode()))
    }
}
