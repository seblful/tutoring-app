package com.seblful.klas.ui.schedule.booking

import com.seblful.klas.domain.model.ClassMode
import com.seblful.klas.domain.schedule.Recurrence
import com.seblful.klas.domain.schedule.Repeat
import com.seblful.klas.format.MoneyField
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * A booking begun in the quick-book sheet, carried to the full form by «All fields» as the route's
 * `draft`. Everything the tutor chose travels, including whether they chose it ([BookingForm.touched]),
 * so the form leaves the same way the sheet would have.
 */
@Serializable
internal data class BookingDraft(
    val target: ClassTarget = ClassTarget.STUDENT,
    val studentId: Long? = null,
    val groupId: Long? = null,
    val subjectId: Long? = null,
    val date: LocalDate? = null,
    val timeFrom: LocalTime? = null,
    val durationMinutes: Int? = null,
    val price: String = "",
    val mode: ClassMode = ClassMode.IN_PERSON,
    val excludedMemberIds: Set<Long> = emptySet(),
    val repeat: Repeat = Repeat.NONE,
    val repeatCount: Int = Recurrence.DEFAULT_COUNT,
    val touched: Boolean = false,
    val automaticPrice: Boolean = true,
    val filledFrom: LocalDate? = null,
) {
    fun toBooking(): BookingForm = BookingForm(
        form = ClassForm(
            target = target,
            studentId = studentId,
            groupId = groupId,
            subjectId = subjectId,
            date = date,
            timeFrom = timeFrom,
            durationMinutes = durationMinutes,
            price = MoneyField(price, MoneyField.Kind.RATE),
            mode = mode,
            excludedMemberIds = excludedMemberIds,
            repeat = repeat,
            repeatCount = repeatCount,
        ),
        touched = touched,
        automaticPrice = automaticPrice,
        filledFrom = filledFrom,
    )

    companion object {
        fun of(booking: BookingForm): BookingDraft = with(booking.form) {
            BookingDraft(
                target = target,
                studentId = studentId,
                groupId = groupId,
                subjectId = subjectId,
                date = date,
                timeFrom = timeFrom,
                durationMinutes = durationMinutes,
                price = price.text,
                mode = mode,
                excludedMemberIds = excludedMemberIds,
                repeat = repeat,
                repeatCount = repeatCount,
                touched = booking.touched,
                automaticPrice = booking.automaticPrice,
                filledFrom = booking.filledFrom,
            )
        }

        fun encode(booking: BookingForm): String = Json.encodeToString(serializer(), of(booking))

        fun decode(text: String): BookingForm = Json.decodeFromString(serializer(), text).toBooking()
    }
}
