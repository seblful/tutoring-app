package com.seblful.klas.data.local

import androidx.room.TypeConverter
import com.seblful.klas.domain.model.AppLanguage
import com.seblful.klas.domain.model.Attended
import com.seblful.klas.domain.model.ClassMode
import com.seblful.klas.domain.model.Currency
import com.seblful.klas.domain.model.LessonStatus
import com.seblful.klas.domain.model.ThemeChoice

/**
 * How every enum column is stored: as the constant's **name**, in a `TEXT` column, and read back by a
 * **total parse** — never `valueOf`, and never Room's own enum handling, which is `valueOf` underneath.
 *
 * The entities used to hold these as `String` and leave the reading to `Mappers.kt` and the repositories,
 * which put the same name ⇄ constant step in every reader. Typing the columns moves it here, once, and
 * changes nothing on disk: the text written is exactly what was written before, so neither the schema
 * (`shared/schemas/`), nor the version, nor a backup file differs. Renaming a constant is therefore still a
 * migration (`StoredEnumNamesTest`, which runs every constant through these).
 *
 * **What an unreadable name becomes.** A column holding an enum's name is a column that can hold something
 * else — a hand-edited database is the only route left now that a backup's rows are typed on the way in —
 * and this runs inside the `Flow`s every screen collects, so *throwing* here reports an error to nobody: it
 * kills the collector, and the screen simply never draws. So the answer differs by whether the column
 * carries money:
 *
 * - **A mode, a currency, a language and a theme do not.** Each falls back — to [ClassMode.IN_PERSON],
 *   [Currency.BYN], [AppLanguage.SYSTEM] and [ThemeChoice.SYSTEM] — and the app carries on: one lesson
 *   reads «Очно» when it was remote, or amounts are captioned in the wrong code, which is a wrong word on a
 *   screen rather than a screen that will not open.
 * - **A status does**, and so does an attendance: together they are what makes a lesson count as income
 *   (`TutoringClass.isBillable`), so guessing would move money — `SCHEDULED` un-bills a lesson that earned,
 *   `COMPLETED` invents one that did not, `ABSENT` writes off what a student owes. There is no safe
 *   fallback, so these fail — loudly and namedly, which is the best available outcome for a row that should
 *   be unreachable.
 *
 * A class with no-arg constructor rather than an `object`, which is the shape Room instantiates.
 */
class EnumConverters {

    @TypeConverter
    fun fromStatus(status: LessonStatus): String = status.name

    @TypeConverter
    fun toStatus(stored: String): LessonStatus =
        LessonStatus.parse(stored) ?: error("A lesson has an unreadable status \"$stored\"")

    @TypeConverter
    fun fromAttended(attended: Attended): String = attended.name

    @TypeConverter
    fun toAttended(stored: String): Attended =
        Attended.parse(stored) ?: error("A class has an unreadable attendance \"$stored\"")

    @TypeConverter
    fun fromMode(mode: ClassMode): String = mode.name

    @TypeConverter
    fun toMode(stored: String): ClassMode = ClassMode.parse(stored) ?: ClassMode.IN_PERSON

    @TypeConverter
    fun fromCurrency(currency: Currency): String = currency.name

    @TypeConverter
    fun toCurrency(stored: String): Currency = Currency.parse(stored)

    @TypeConverter
    fun fromLanguage(language: AppLanguage): String = language.name

    @TypeConverter
    fun toLanguage(stored: String): AppLanguage = AppLanguage.parse(stored)

    @TypeConverter
    fun fromTheme(theme: ThemeChoice): String = theme.name

    @TypeConverter
    fun toTheme(stored: String): ThemeChoice = ThemeChoice.parse(stored)
}
