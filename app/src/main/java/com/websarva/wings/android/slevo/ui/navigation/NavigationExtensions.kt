package com.websarva.wings.android.slevo.ui.navigation

import androidx.navigation.NavHostController
import androidx.navigation.NavOptionsBuilder

/**
 * 板画面 route への画面遷移だけを行う拡張関数。
 */
fun NavHostController.navigateToBoardSurface(
    route: AppRoute.Board,
    builder: NavOptionsBuilder.() -> Unit = {},
) {
    navigate(route) {
        launchSingleTop = true
        builder()
    }
}

/**
 * スレ画面 route への画面遷移だけを行う拡張関数。
 */
fun NavHostController.navigateToThreadSurface(
    route: AppRoute.Thread,
    builder: NavOptionsBuilder.() -> Unit = {},
) {
    navigate(route) {
        launchSingleTop = true
        builder()
    }
}

/**
 * タブ選択に応じて板画面 surface を表示する。
 *
 * 同種別 surface 上での板タブ切り替えでは navigation を積まず、
 * 別種別 surface から板 surface へ移る場合のみ現在 surface を置換する。
 */
fun NavHostController.showBoardSurfaceForTabSelection(
    currentSurfaceRoute: AppRoute?,
    route: AppRoute.Board,
) {
    when (currentSurfaceRoute) {
        is AppRoute.Board -> Unit
        is AppRoute.Thread -> replaceCurrentSurface(currentSurfaceRoute, route)
        else -> navigateToBoardSurface(route)
    }
}

/**
 * タブ選択に応じてスレ画面 surface を表示する。
 *
 * 同種別 surface 上でのスレタブ切り替えでは navigation を積まず、
 * 別種別 surface からスレ surface へ移る場合のみ現在 surface を置換する。
 */
fun NavHostController.showThreadSurfaceForTabSelection(
    currentSurfaceRoute: AppRoute?,
    route: AppRoute.Thread,
) {
    when (currentSurfaceRoute) {
        is AppRoute.Thread -> Unit
        is AppRoute.Board -> replaceCurrentSurface(currentSurfaceRoute, route)
        else -> navigateToThreadSurface(route)
    }
}

/**
 * 現在表示中 surface を別の surface で置換する。
 */
private fun NavHostController.replaceCurrentSurface(
    currentSurfaceRoute: AppRoute,
    targetRoute: AppRoute,
) {
    navigate(targetRoute) {
        launchSingleTop = true
        popUpTo(currentSurfaceRoute) {
            inclusive = true
        }
    }
}
