package com.seblful.klas.format

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.seblful.klas.R
import com.seblful.klas.domain.model.AppLanguage
import com.seblful.klas.testutil.ProjectFiles
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory

@RunWith(RobolectricTestRunner::class)
class PackagedLanguagesTest {
    @Test
    fun `every offered locale loads its packaged translation and formats plural counts`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        for (language in AppLanguage.translated) {
            val tag = language.tag!!
            val folder = if (language == AppLanguage.FALLBACK) "values" else "values-${tag.replace("-", "-r")}"
            val document = DocumentBuilderFactory.newInstance().newDocumentBuilder()
                .parse(ProjectFiles.moduleFile("src/main/res/$folder/strings.xml"))
            val strings = document.getElementsByTagName("string")
            val expected = (0 until strings.length).map(strings::item)
                .first { it.attributes.getNamedItem("name").nodeValue == "nav_homework" }.textContent
            val localized = context.inLanguage(Locale.forLanguageTag(tag))
            assertEquals(tag, expected, localized.getString(R.string.nav_homework))
            val plurals = document.getElementsByTagName("plurals")
            for (index in 0 until plurals.length) {
                val name = plurals.item(index).attributes.getNamedItem("name").nodeValue
                val id = localized.resources.getIdentifier(name, "plurals", context.packageName)
                assertTrue("$tag/$name must be packaged", id != 0)
                for (count in listOf(0, 1, 2, 3, 5, 11, 21, 100, 1_000_000)) {
                    val formatted = localized.resources.getQuantityString(id, count, count, count, count)
                    assertTrue("$tag/$name/$count", formatted.isNotBlank())
                }
            }
        }
    }
}
