package com.websarva.wings.android.bbsviewer.ui.util

import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hierarchy

fun NavDestination?.isInRoute(vararg routeNames: String): Boolean =
    this?.hierarchy
        ?.any { dest -> routeNames.any { name -> dest.route?.contains(name) == true } }
        ?: false
