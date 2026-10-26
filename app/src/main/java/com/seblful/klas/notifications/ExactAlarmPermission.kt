package com.seblful.klas.notifications

/**
 * Whether the platform will let a reminder fire at the **minute it was promised**.
 *
 * A port for the reason [NotificationPermission] is one, and it answers the same shape of question
 * about the other half of a reminder: that one says whether a reminder may be *shown*, this one says
 * whether it will be shown *on time*. Both are the platform's answers, neither is observable, and both
 * can be revoked while the app is away.
 *
 * `SCHEDULE_EXACT_ALARM` is granted by default on Android 13 but revocable, and **absent by default on
 * 14+**. Without it `ReminderAlarms` still arms the alarm — `setAndAllowWhileIdle`, which Doze may hold
 * until its next maintenance window — so the reminder is late rather than missing. That fallback is a
 * genuine one and stays, but the tutor has to be told. «Занятие через час» arriving forty minutes into
 * the lesson is the app's one time-critical promise quietly not being kept, and an adapter-private check
 * has no way to say anything to the tutor.
 *
 * The app still never *prompts* for it (see the manifest): a tutoring reminder does not justify
 * `USE_EXACT_ALARM`, and `ACTION_REQUEST_SCHEDULE_EXACT_ALARM` is a full-screen system page. Settings
 * reports the state and leaves the choice where it already is.
 */
interface ExactAlarmPermission {

    /**
     * True when an alarm may be set for an exact instant. Always true below Android 12, which has no
     * such permission to withhold.
     */
    fun isGranted(): Boolean
}
