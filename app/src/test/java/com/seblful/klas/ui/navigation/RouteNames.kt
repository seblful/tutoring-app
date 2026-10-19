package com.seblful.klas.ui.navigation

import androidx.navigation.NavDestination

/**
 * A destination's route as its route type's simple name — `StudentDetail` for
 * `com.seblful.klas.ui.navigation.Routes.StudentDetail/{id}` — so a test can write the back stack it expects
 * as words. Null for the graph itself, which has no route.
 */
internal fun NavDestination.routeName(): String? =
    route?.substringBefore('/')?.substringBefore('?')?.substringAfterLast('.')
