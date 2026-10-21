package com.seblful.klas.ui.common

import com.seblful.klas.testutil.ProjectFiles
import org.junit.Assert.assertFalse
import org.junit.Test

class HapticFeedbackTest {
    @Test
    fun `the manifest needs no raw vibration permission`() {
        val manifest = ProjectFiles.moduleFile("src/main/AndroidManifest.xml").readText()
        assertFalse(manifest.contains("android.permission.VIBRATE"))
    }
}
