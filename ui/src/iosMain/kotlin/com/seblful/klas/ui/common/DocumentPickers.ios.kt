package com.seblful.klas.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import com.seblful.klas.storage.DocumentRef
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.NSUUID
import platform.UIKit.UIDocumentPickerDelegateProtocol
import platform.UIKit.UIDocumentPickerViewController
import platform.UniformTypeIdentifiers.UTType
import platform.UniformTypeIdentifiers.UTTypeData
import platform.UniformTypeIdentifiers.UTTypeFolder
import platform.darwin.NSObject

/*
 * UIKit's document picker. A new document is an empty file first, exported to wherever the tutor puts it;
 * an opened one is a copy in the app's sandbox; a folder keeps a security-scoped URL the store opens. The
 * delegate is remembered: UIKit holds it weakly.
 */

@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun rememberCreateDocument(
    mimeType: String,
    failureMessage: String,
    messages: Messages,
    onCreated: (DocumentRef) -> Unit,
): (suggestedName: String) -> Unit {
    val delegate = rememberPickerDelegate(onCreated)
    return { name ->
        val directory = NSTemporaryDirectory() + NSUUID().UUIDString
        val path = "$directory/$name"
        val manager = NSFileManager.defaultManager
        val made = manager.createDirectoryAtPath(
            directory,
            withIntermediateDirectories = true,
            attributes = null,
            error = null,
        ) && manager.createFileAtPath(path, contents = null, attributes = null)
        if (made) {
            val file = NSURL.fileURLWithPath(path)
            delegate.present(UIDocumentPickerViewController(forExportingURLs = listOf(file), asCopy = false))
        } else {
            messages.show(failureMessage)
        }
    }
}

@Composable
actual fun rememberOpenDocument(
    failureMessage: String,
    messages: Messages,
    onOpened: (DocumentRef) -> Unit,
): (mimeTypes: Array<String>) -> Unit {
    val delegate = rememberPickerDelegate(onOpened)
    return { types ->
        val contentTypes = types.mapNotNull { UTType.typeWithMIMEType(it) }.ifEmpty { listOf(UTTypeData) }
        delegate.present(UIDocumentPickerViewController(forOpeningContentTypes = contentTypes, asCopy = true))
    }
}

@Composable
actual fun rememberOpenFolder(
    failureMessage: String,
    messages: Messages,
    onPicked: (DocumentRef) -> Unit,
): () -> Unit {
    val delegate = rememberPickerDelegate(onPicked)
    return { delegate.present(UIDocumentPickerViewController(forOpeningContentTypes = listOf(UTTypeFolder))) }
}

@Composable
private fun rememberPickerDelegate(onPicked: (DocumentRef) -> Unit): PickerDelegate {
    val picked by rememberUpdatedState(onPicked)
    return remember { PickerDelegate { url -> url.absoluteString?.let { picked(DocumentRef(it)) } } }
}

private class PickerDelegate(private val onPicked: (NSURL) -> Unit) : NSObject(), UIDocumentPickerDelegateProtocol {

    fun present(picker: UIDocumentPickerViewController) {
        picker.delegate = this
        presentOnTop(picker)
    }

    override fun documentPicker(controller: UIDocumentPickerViewController, didPickDocumentsAtURLs: List<*>) {
        (didPickDocumentsAtURLs.firstOrNull() as? NSURL)?.let(onPicked)
    }
}
