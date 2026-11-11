package com.seblful.klas.ui.students

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import com.seblful.klas.ui.common.presentOnTop
import platform.Contacts.CNContactFormatter
import platform.Contacts.CNContactFormatterStyle
import platform.Contacts.CNContactPhoneNumbersKey
import platform.Contacts.CNContactProperty
import platform.Contacts.CNPhoneNumber
import platform.ContactsUI.CNContactPickerDelegateProtocol
import platform.ContactsUI.CNContactPickerViewController
import platform.darwin.NSObject

@Composable
actual fun rememberContactPicker(onPicked: (name: String, number: String) -> Unit): () -> Unit {
    val picked by rememberUpdatedState(onPicked)
    // UIKit holds the delegate weakly.
    val delegate = remember { ContactDelegate { name, number -> picked(name, number) } }
    return {
        presentOnTop(
            CNContactPickerViewController().apply {
                this.delegate = delegate
                displayedPropertyKeys = listOf(CNContactPhoneNumbersKey)
            },
        )
    }
}

/** Picking a phone number answers; picking a contact without one answers nothing. */
private class ContactDelegate(private val onPicked: (String, String) -> Unit) :
    NSObject(),
    CNContactPickerDelegateProtocol {

    override fun contactPicker(picker: CNContactPickerViewController, didSelectContactProperty: CNContactProperty) {
        val number = (didSelectContactProperty.value as? CNPhoneNumber)?.stringValue ?: return
        val contact = didSelectContactProperty.contact
        val name = CNContactFormatter.stringFromContact(contact, FullName)
        onPicked(name.orEmpty(), number)
    }
}

private val FullName = CNContactFormatterStyle.CNContactFormatterStyleFullName
