package com.websarva.wings.android.slevo.ui.navigation

import androidx.navigation.NavDestination.Companion.hasRoute
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
 * 板画面上では navigation を変更しない。
 * スレ画面からの遷移では、直前が板画面ならそこへ戻り、
 * それ以外は現在のスレ画面を板画面へ置き換える。
 * その他の画面からは板画面へ通常遷移する。
 */
fun NavHostController.showBoardScreenForTabSelection(
    currentScreenRoute: AppRoute?,
    route: AppRoute.Board,
) {
    when (currentScreenRoute) {
        is AppRoute.Board -> Unit

        is AppRoute.Thread -> {
            val previousIsBoard =
                previousBackStackEntry
                    ?.destination
                    ?.hasRoute<AppRoute.Board>() == true
            if (previousIsBoard) {
                popBackStack()
            } else {
                replaceCurrentScreen(currentScreenRoute, route)
            }
        }

        else -> navigateToBoardScreen(route)
    }
}

/**
 * タブ選択に応じてスレ画面を表示する。
 *
 * スレ画面上では navigation を変更せず、
 * それ以外の画面からはスレ画面へ通常遷移する。
 */
fun NavHostController.showThreadScreenForTabSelection(
    currentScreenRoute: AppRoute?,
    route: AppRoute.Thread,
) {
    when (currentScreenRoute) {
        is AppRoute.Thread -> Unit
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
        // 既存の背後に同種画面があっても、それを再利用せず新しい選択先を積む。
        popUpTo(currentScreenRoute) {
            inclusive = true
        }
    }
}
