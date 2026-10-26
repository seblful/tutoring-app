package com.seblful.klas.ui.backup

import org.junit.Assert.assertEquals
import org.junit.Test

class PickedExportTest {

    @Test
    fun `a sealed export writes with its password`() {
        assertEquals(PickedExport.Write("secret"), pickedExport(sealedRequested = true, password = "secret"))
    }

    @Test
    fun `an export asked to be plain writes without one`() {
        assertEquals(PickedExport.Write(null), pickedExport(sealedRequested = false, password = null))
    }

    @Test
    fun `a password lost to process death is asked for again rather than written as plain`() {
        assertEquals(PickedExport.PasswordLost, pickedExport(sealedRequested = true, password = null))
    }
}
