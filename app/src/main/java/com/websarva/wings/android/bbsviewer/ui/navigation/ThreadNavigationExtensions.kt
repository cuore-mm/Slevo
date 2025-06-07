package com.websarva.wings.android.bbsviewer.ui.navigation

import androidx.navigation.NavHostController

fun NavHostController.openThread(thread: AppRoute.Thread) {
    navigate(thread) {
        launchSingleTop = true
        restoreState = true
    }
}
