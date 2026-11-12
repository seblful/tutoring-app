package com.seblful.klas.testutil

import com.seblful.klas.calendar.CalendarPermission
import com.seblful.klas.notifications.ExactAlarmPermission
import com.seblful.klas.notifications.NotificationPermission
import com.seblful.klas.print.Sheet
import com.seblful.klas.print.SheetPrinter
import com.seblful.klas.storage.FolderGrants

class FakeCalendarPermission(var granted: Boolean = true) : CalendarPermission {
    override fun isGranted(): Boolean = granted
}

class FakeNotificationPermission(
    var granted: Boolean = true,
    override var canAsk: Boolean = true,
) : NotificationPermission {
    override fun isGranted(): Boolean = granted
}

class FakeExactAlarmPermission(var granted: Boolean = true) : ExactAlarmPermission {
    override fun isGranted(): Boolean = granted
}

/** [FolderGrants] that remembers what is kept; [refusing] is a picker that granted nothing to keep. */
class FakeFolderGrants(var refusing: Boolean = false) : FolderGrants {
    val kept = mutableSetOf<String>()
    val names = mutableMapOf<String, String>()

    override fun keep(folder: String) {
        if (refusing) throw IllegalStateException("No persistable grant for $folder")
        kept += folder
    }

    override fun release(folder: String) {
        kept -= folder
    }

    override suspend fun name(folder: String): String? = names[folder]
}

/** [SheetPrinter] that keeps each sheet and prints a stand-in: the platform's PDF writer is the renderer's test. */
class RecordingSheetPrinter : SheetPrinter {
    val printed = mutableListOf<Sheet>()

    override fun print(sheet: Sheet): ByteArray {
        printed += sheet
        return "%PDF ${sheet.title}".encodeToByteArray()
    }
}
