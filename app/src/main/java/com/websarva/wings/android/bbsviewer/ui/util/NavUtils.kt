package com.websarva.wings.android.bbsviewer.ui.util

import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hierarchy

fun checkCurrentRoute(
    currentDestination: NavDestination?,
    routeNames: List<String>
): Boolean {
    return currentDestination?.hierarchy?.any { destination ->
        destination.route?.let { route ->
            routeNames.any { route.contains(it) }
        } ?: false
    } ?: false
}
