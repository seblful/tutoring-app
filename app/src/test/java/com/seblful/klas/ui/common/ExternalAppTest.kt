package com.seblful.klas.ui.common

import android.content.Intent
import androidx.core.content.IntentCompat
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * What each [ExternalTarget] asks Android for. The screens name only the target now, so this is the one
 * place left that says the dialler is opened with `ACTION_DIAL` (never a call placed), a mail with
 * `ACTION_SENDTO` (mail apps only, not every app that views a link) — what the screens built by hand before.
 */
@RunWith(RobolectricTestRunner::class)
class ExternalAppTest {

    @Test
    fun `a phone number is dialled without its spaces, and not called`() {
        val intent = ExternalTarget.Dial("+375 29 111-22-33").toIntent()

        assertEquals(Intent.ACTION_DIAL, intent.action)
        assertEquals("tel:+37529111-22-33", intent.dataString)
    }

    @Test
    fun `an address opens a new mail in a mail app`() {
        val intent = ExternalTarget.Email("anya@example.com").toIntent()

        assertEquals(Intent.ACTION_SENDTO, intent.action)
        assertEquals("mailto:anya@example.com", intent.dataString)
    }

    @Test
    fun `Telegram is reached through t-me with the number in digits`() {
        val intent = ExternalTarget.Telegram("+375 (29) 111-22-33").toIntent()

        assertEquals(Intent.ACTION_VIEW, intent.action)
        assertEquals("https://t.me/+375291112233", intent.dataString)
    }

    @Test
    fun `shared text goes to the share sheet under its title`() {
        val chooser = ExternalTarget.ShareText("§12, упр. 4–7", "Отправить").toIntent()
        val send = IntentCompat.getParcelableExtra(chooser, Intent.EXTRA_INTENT, Intent::class.java)!!

        assertEquals(Intent.ACTION_CHOOSER, chooser.action)
        assertEquals("Отправить", chooser.getCharSequenceExtra(Intent.EXTRA_TITLE).toString())
        assertEquals(Intent.ACTION_SEND, send.action)
        assertEquals("text/plain", send.type)
        assertEquals("§12, упр. 4–7", send.getStringExtra(Intent.EXTRA_TEXT))
    }
}
