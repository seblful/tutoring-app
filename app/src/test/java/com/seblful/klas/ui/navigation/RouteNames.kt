package com.seblful.klas.ui.navigation

import androidx.navigation.NavDestination

internal fun NavDestination.routeName(): String? =
    route?.substringBefore('/')?.substringBefore('?')?.substringAfterLast('.')
