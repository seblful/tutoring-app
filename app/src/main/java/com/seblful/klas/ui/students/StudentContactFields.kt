package com.seblful.klas.ui.students

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import com.seblful.klas.R
import com.seblful.klas.domain.model.ClassMode
import com.seblful.klas.ui.common.ModeField
import com.seblful.klas.ui.common.supportingTextOf

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
    email: String,
    onEmail: (String) -> Unit,
    note: String,
    onNote: (String) -> Unit,
    mode: ClassMode,
    onMode: (ClassMode) -> Unit,
) {
    OutlinedTextField(
        value = name,
        onValueChange = onName,
        label = { Text(stringResource(R.string.student_name_label)) },
        supportingText = supportingTextOf(
            hint = stringResource(R.string.field_required).takeIf { name.isBlank() },
        ),
        singleLine = true,
        keyboardOptions = NameKeyboard,
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = phone,
        onValueChange = onPhone,
        label = { Text(stringResource(R.string.student_phone_label)) },
        singleLine = true,
        keyboardOptions = PhoneKeyboard,
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = email,
        onValueChange = onEmail,
        label = { Text(stringResource(R.string.student_email_label)) },
        singleLine = true,
        keyboardOptions = EmailKeyboard,
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = note,
        onValueChange = onNote,
        label = { Text(stringResource(R.string.student_note_label)) },
        keyboardOptions = NoteKeyboard,
        modifier = Modifier.fillMaxWidth(),
    )
    ModeField(
        label = stringResource(R.string.student_mode_label),
        selected = mode,
        onSelect = onMode,
    )
}
private val NameKeyboard = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next)
private val PhoneKeyboard = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Next)
private val EmailKeyboard = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next)
private val NoteKeyboard = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)
