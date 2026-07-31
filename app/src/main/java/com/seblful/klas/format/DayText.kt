package com.seblful.klas.format

import android.content.res.Resources
import com.seblful.klas.R
import com.seblful.klas.domain.DaySummary
import com.seblful.klas.domain.model.Currency

/**
 * What a day comes to, in words: «4 занятия · 5 ч · 240 BYN» — or **null** when there is nothing to say
 * about it.
 *
 * The arithmetic is [DaySummary]'s and arrives already done; this is the wording, the separator and the
 * two cases where the sentence is not the ordinary one. It is here rather than inside the composable that
 * used to own it because there are now **two** readers of the same sentence — the schedule's day heading
 * and the morning digest in the notification tray — and the app's rule for that is one function, not two
 * copies that agree today. A digest that added a day up differently from the screen it summarises would
 * be the worst kind of wrong: both numbers plausible, one of them the tutor's actual morning.
 *
 * Takes [Resources] rather than being a `@Composable`, for the same reason: a notification is built from a
 * `Context` and has no composition to read `stringResource` from. Plurals still come from the resources'
 * own configuration rather than from the folder the strings were loaded from, so **which** `Resources` is
 * handed in decides the quantity rule: a screen's come from `AppRoot`, a notification's from
 * `Context.inAppLanguage()`, and both are the language the app is set to.
 *
 * Null rather than an empty string, so a caller has to decide what "nothing to say" looks like: the
 * schedule draws no line at all, because its empty state already says it at length and «0 занятий · 0 ч»
 * above that would be the same news twice in a colder voice.
 *
 * @param currency what money is printed in — the display setting, passed rather than read, since neither
 *   caller can reach `LocalCurrency` (one is not in composition, the other is below the UI).
 */
fun daySummaryText(res: Resources, summary: DaySummary, currency: Currency): String? {
    if (summary.isEmpty) return null

    val cancelled = res.getString(R.string.schedule_summary_cancelled, summary.cancelled)
        .takeIf { summary.cancelled > 0 }
    val parts = if (summary.lessons > 0) {
        listOfNotNull(
            res.getQuantityString(R.plurals.lessons_count, summary.lessons, summary.lessons),
            res.getString(R.string.hours_value, formatHours(summary.minutes)),
            summary.money.money(currency),
            cancelled,
        )
    } else {
        // Everything on this day was called off. Saying so is the whole of it — there are no hours and no
        // money, and printing zeroes for them would dress a cancelled day up as a counted one.
        listOfNotNull(cancelled)
    }
    return parts.joinToString(" · ")
}
