package com.seblful.klas.ui.common

import android.content.Intent
import androidx.core.content.IntentCompat
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

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
    fun `messaging a number asks the system, with text messages as its base`() {
        val chooser = ExternalTarget.Message("29 123-45-67", "+375291234567", "Message").toIntent()
        val base = IntentCompat.getParcelableExtra(chooser, Intent.EXTRA_INTENT, Intent::class.java)!!

        assertEquals(Intent.ACTION_CHOOSER, chooser.action)
        assertEquals("Message", chooser.getCharSequenceExtra(Intent.EXTRA_TITLE).toString())
        assertEquals(Intent.ACTION_SENDTO, base.action)
        assertEquals("smsto:+375291234567", base.dataString)
    }

    @Test
    fun `Telegram and WhatsApp join the chooser by their packages when the whole number is known`() {
        val chooser = ExternalTarget.Message("29 123-45-67", "+375291234567", "Message").toIntent()
        val extra = IntentCompat.getParcelableArrayExtra(chooser, Intent.EXTRA_INITIAL_INTENTS, Intent::class.java)!!

        assertEquals(
            listOf(
                "org.telegram.messenger" to "https://t.me/+375291234567",
                "com.whatsapp" to "https://wa.me/375291234567",
                "com.whatsapp.w4b" to "https://wa.me/375291234567",
            ),
            extra.map { (it as Intent).let { intent -> intent.`package` to intent.dataString } },
        )
    }

    @Test
    fun `a number with no known country is only texted as typed`() {
        val chooser = ExternalTarget.Message("29 123-45-67", null, "Message").toIntent()
        val base = IntentCompat.getParcelableExtra(chooser, Intent.EXTRA_INTENT, Intent::class.java)!!
        val extra = IntentCompat.getParcelableArrayExtra(chooser, Intent.EXTRA_INITIAL_INTENTS, Intent::class.java)

        assertEquals("smsto:29123-45-67", base.dataString)
        assertEquals(0, extra!!.size)
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
