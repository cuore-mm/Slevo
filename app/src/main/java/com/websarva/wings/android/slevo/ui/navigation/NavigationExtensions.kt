package com.websarva.wings.android.slevo.ui.navigation

import androidx.navigation.NavHostController
import androidx.navigation.NavOptionsBuilder

/**
 * 板画面 route への画面遷移だけを行う拡張関数。
 */
fun NavHostController.navigateToBoardScreen(
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
fun NavHostController.navigateToThreadScreen(
    route: AppRoute.Thread,
    builder: NavOptionsBuilder.() -> Unit = {},
) {
    navigate(route) {
        launchSingleTop = true
        builder()
    }
}

/**
 * タブ選択に応じて板画面を表示する。
 *
 * 同種別画面上での板タブ切り替えでは navigation を積まず、
 * 別種別画面から板画面へ移る場合のみ現在画面を置換する。
 */
fun NavHostController.showBoardScreenForTabSelection(
    currentScreenRoute: AppRoute?,
    route: AppRoute.Board,
) {
    when (currentScreenRoute) {
        is AppRoute.Board -> Unit
        is AppRoute.Thread -> replaceCurrentScreen(currentScreenRoute, route)
        else -> navigateToBoardScreen(route)
    }
}

/**
 * タブ選択に応じてスレ画面を表示する。
 *
 * 同種別画面上でのスレタブ切り替えでは navigation を積まず、
 * 別種別画面からスレ画面へ移る場合のみ現在画面を置換する。
 */
fun NavHostController.showThreadScreenForTabSelection(
    currentScreenRoute: AppRoute?,
    route: AppRoute.Thread,
) {
    when (currentScreenRoute) {
        is AppRoute.Thread -> Unit
        is AppRoute.Board -> replaceCurrentScreen(currentScreenRoute, route)
        else -> navigateToThreadScreen(route)
    }
}

/**
 * 現在表示中の画面を別の画面で置換する。
 */
private fun NavHostController.replaceCurrentScreen(
    currentScreenRoute: AppRoute,
    targetRoute: AppRoute,
) {
    navigate(targetRoute) {
        launchSingleTop = true
        popUpTo(currentScreenRoute) {
            inclusive = true
        }
    }
}
