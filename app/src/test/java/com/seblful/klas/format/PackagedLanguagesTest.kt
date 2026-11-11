package com.seblful.klas.format

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.seblful.klas.domain.model.AppLanguage
import com.seblful.klas.testutil.ProjectFiles
import com.seblful.klas.testutil.texts
import com.seblful.klas.text.PluralResource
import com.seblful.klas.text.Plurals
import com.seblful.klas.text.Strings
import com.seblful.klas.text.Texts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
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
                .parse(File(ProjectFiles.strings(), "$folder/strings.xml"))
            val strings = document.getElementsByTagName("string")
            val expected = (0 until strings.length).map(strings::item)
                .first { it.attributes.getNamedItem("name").nodeValue == "nav_homework" }.textContent
            val texts = Texts.of(tag)
            assertEquals(tag, expected, texts.string(Strings.nav_homework))
            val plurals = Plurals.javaClass.declaredMethods
                .filter { it.parameterCount == 0 && it.returnType == PluralResource::class.java }
                .map { it.invoke(Plurals) as PluralResource }
            assertEquals(document.getElementsByTagName("plurals").length, plurals.size)
            for (plural in plurals) {
                for (count in listOf(0, 1, 2, 3, 5, 11, 21, 100, 1_000_000)) {
                    val formatted = texts.plural(plural, count, count, count, count)
                    assertTrue("$tag/${plural.key}/$count", formatted.isNotBlank())
                }
            }
        }
    }
}
