package com.seblful.klas.ui.students

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract.CommonDataKinds.Phone
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext

/**
 * Opens the phone's own contact picker and hands back the chosen contact's name and number. The picker
 * grants a one-time read of that row, so the app asks for no contacts permission.
 */
@Composable
fun rememberContactPicker(onPicked: (name: String, number: String) -> Unit): () -> Unit {
    val context = LocalContext.current
    val picked by rememberUpdatedState(onPicked)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        result.data?.data?.let { context.readContact(it) }?.let { (name, number) -> picked(name, number) }
    }
    return { runCatching { launcher.launch(Intent(Intent.ACTION_PICK, Phone.CONTENT_URI)) } }
}

private fun Context.readContact(uri: Uri): Pair<String, String>? =
    contentResolver.query(uri, arrayOf(Phone.DISPLAY_NAME, Phone.NUMBER), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) cursor.getString(0).orEmpty() to cursor.getString(1).orEmpty() else null
    }
