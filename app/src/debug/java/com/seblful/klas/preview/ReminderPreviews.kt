package com.seblful.klas.preview

import androidx.compose.runtime.Composable
import com.seblful.klas.ui.more.ReminderLeadFields
import com.seblful.klas.ui.more.ReminderLeadRow
import com.seblful.klas.ui.more.ReminderUnit

@KlasPreview
@Composable
private fun ReminderLeadRowPreview() = PreviewSurface {
    ReminderLeadRow(minutes = 60, enabled = true, onSelect = {})
    ReminderLeadRow(minutes = 1440, enabled = true, onSelect = {})
    ReminderLeadRow(minutes = 90, enabled = false, onSelect = {})
}

@KlasPreview
@Composable
private fun ReminderLeadPickerPreview() = PreviewSurface {
    ReminderLeadFields(amount = "1", unit = ReminderUnit.DAYS, onAmountChange = {}, onUnitChange = {})
}
