package com.seblful.klas.ui.common

import com.seblful.klas.domain.model.AppLanguage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LanguageTest {
    @Test
    fun `both pickers offer every language once after System in native name order`() {
        assertEquals(AppLanguage.SYSTEM, LanguageChoices.first())
        assertEquals(AppLanguage.entries.toSet(), LanguageChoices.toSet())
        assertEquals(AppLanguage.entries.size, LanguageChoices.size)
        val labels = LanguageChoices.drop(1).map(::languageAutonym)
        assertTrue(labels.all(String::isNotBlank))
        assertEquals(labels.size, labels.toSet().size)
        assertEquals(labels.sorted(), labels)
    }
}
