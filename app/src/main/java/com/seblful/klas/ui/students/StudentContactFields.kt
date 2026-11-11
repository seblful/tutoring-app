package com.seblful.klas.ui.students

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import com.seblful.klas.domain.model.ClassMode
import com.seblful.klas.text.Strings
import com.seblful.klas.text.stringResource
import com.seblful.klas.ui.common.ModeField
import com.seblful.klas.ui.common.NamedIconButton
import com.seblful.klas.ui.common.supportingTextOf
import com.seblful.klas.ui.theme.KlasIcons

/**
 * Who a student is and how to reach them — name, phone, email, note, and where they are usually taught —
 * asked the same way by the add form and by the card's edit dialog.
 */
@Composable
fun StudentContactFields(
    name: String,
    onName: (String) -> Unit,
    phone: String,
    onPhone: (String) -> Unit,
    onPickContact: () -> Unit,
    email: String,
    onEmail: (String) -> Unit,
    note: String,
    onNote: (String) -> Unit,
    mode: ClassMode,
    onMode: (ClassMode) -> Unit,
) {
    StudentNameField(name, onName)
    // Whose number is the tutor's to know — a child's tutor mostly calls a parent — so the hint names
    // both, and says what the number is for: the card's call and Telegram buttons.
    OutlinedTextField(
        value = phone,
        onValueChange = onPhone,
        label = { Text(stringResource(Strings.student_phone_label)) },
        supportingText = { Text(stringResource(Strings.student_phone_hint)) },
        singleLine = true,
        trailingIcon = {
            NamedIconButton(KlasIcons.Outlined.Person, stringResource(Strings.student_phone_pick), onPickContact)
        },
        keyboardOptions = PhoneKeyboard,
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = email,
        onValueChange = onEmail,
        label = { Text(stringResource(Strings.student_email_label)) },
        supportingText = { Text(stringResource(Strings.student_email_hint)) },
        singleLine = true,
        keyboardOptions = EmailKeyboard,
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = note,
        onValueChange = onNote,
        label = { Text(stringResource(Strings.student_note_label)) },
        keyboardOptions = NoteKeyboard,
        modifier = Modifier.fillMaxWidth(),
    )
    ModeField(
        label = stringResource(Strings.student_mode_label),
        selected = mode,
        onSelect = onMode,
    )
}
/** The one field a student cannot do without — alone, it is what setup asks for. */
@Composable
fun StudentNameField(name: String, onName: (String) -> Unit) {
    OutlinedTextField(
        value = name,
        onValueChange = onName,
        label = { Text(stringResource(Strings.student_name_label)) },
        supportingText = supportingTextOf(
            hint = stringResource(Strings.field_required).takeIf { name.isBlank() },
        ),
        singleLine = true,
        keyboardOptions = NameKeyboard,
        modifier = Modifier.fillMaxWidth(),
    )
}

private val NameKeyboard = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next)
private val PhoneKeyboard = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Next)
private val EmailKeyboard = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next)
private val NoteKeyboard = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)
