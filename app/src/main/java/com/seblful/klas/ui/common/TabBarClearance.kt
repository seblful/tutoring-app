package com.seblful.klas.ui.common

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.unit.dp

/**
 * How much of a tab's foot the phone's floating tab bar covers above the navigation bar's inset: room
 * [KlasScreen] keeps clear for the body's last row, the FAB and snackbars. Zero beside a rail and on a pushed
 * screen, where nothing floats over the foot.
 */
val LocalTabBarClearance = compositionLocalOf { 0.dp }
