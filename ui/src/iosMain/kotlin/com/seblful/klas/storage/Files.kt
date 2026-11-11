package com.seblful.klas.storage

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSData
import platform.Foundation.NSFileManager
import platform.Foundation.NSURL
import platform.Foundation.NSUserDomainMask
import platform.Foundation.create
import platform.Foundation.dataWithContentsOfURL
import platform.Foundation.writeToURL
import platform.posix.memcpy

/*
 * Foundation's files as Kotlin bytes, for the iOS adapters. Every write is atomic: Foundation writes beside
 * the file and moves it in, so a kill mid-write cannot leave half a file.
 */

/** A file in the app's Application Support directory, which the system neither shows nor purges. */
@OptIn(ExperimentalForeignApi::class)
internal fun appFile(name: String): NSURL {
    val directory = NSFileManager.defaultManager.URLForDirectory(
        directory = NSApplicationSupportDirectory,
        inDomain = NSUserDomainMask,
        appropriateForURL = null,
        create = true,
        error = null,
    )
    return requireNotNull(directory?.URLByAppendingPathComponent(name)) { "Application Support is unavailable" }
}

internal fun NSURL.readBytes(): ByteArray? = NSData.dataWithContentsOfURL(this)?.toByteArray()

internal fun NSURL.writeBytes(bytes: ByteArray) {
    check(bytes.toNSData().writeToURL(this, atomically = true)) { "Could not write $lastPathComponent" }
}

internal fun NSURL.exists(): Boolean = path?.let { NSFileManager.defaultManager.fileExistsAtPath(it) } == true

@OptIn(ExperimentalForeignApi::class)
internal fun NSURL.delete() {
    if (exists()) NSFileManager.defaultManager.removeItemAtURL(this, error = null)
}

@OptIn(ExperimentalForeignApi::class)
internal fun NSData.toByteArray(): ByteArray {
    val size = length.toInt()
    val bytes = ByteArray(size)
    if (size > 0) bytes.usePinned { memcpy(it.addressOf(0), this.bytes, length) }
    return bytes
}

@OptIn(ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)
internal fun ByteArray.toNSData(): NSData =
    if (isEmpty()) NSData() else usePinned { NSData.create(bytes = it.addressOf(0), length = size.toULong()) }

/** Runs [block] with access to a URL the tutor picked outside the app's sandbox. */
internal inline fun <T> NSURL.accessing(block: (NSURL) -> T): T {
    val started = startAccessingSecurityScopedResource()
    try {
        return block(this)
    } finally {
        if (started) stopAccessingSecurityScopedResource()
    }
}
