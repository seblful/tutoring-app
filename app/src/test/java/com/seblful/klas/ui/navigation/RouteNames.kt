package com.seblful.klas.ui.navigation

internal fun Screen.routeName(): String = route::class.simpleName.orEmpty()
