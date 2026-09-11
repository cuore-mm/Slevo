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
 * Tabs上で選択した板を、Tabsを除去した後の既存画面種別へ反映する。
 *
 * contextual Tabsでは現在の公開back stack列から直下のBoard有無を判定し、TabsとThreadを
 * まとめて除去する。直下のBoardがない場合はThreadとTabsを一度に置換し、ルートTabsでは
 * Tabsを残して通常のpush規則を使う。
 */
fun NavHostController.showBoardScreenFromTabs(
    sourceRoute: AppRoute?,
    tabsEntryId: String,
    route: AppRoute.Board,
) {
    if (!isCurrentTabsEntry(tabsEntryId)) return
    if (sourceRoute == null) {
        showBoardScreenForTabSelection(currentScreenRoute = null, route = route)
        return
    }

    // Guard: contextual Tabsには必ず遷移元entryが存在するため、start destinationをpopしない。
    if (previousBackStackEntry == null) return
    // Guard: 非同期の選択処理中にTabsを離れていた場合は、古いcallbackで履歴を変更しない。
    when (sourceRoute) {
        is AppRoute.Board -> {
            if (!popBackStack()) return
        }

        is AppRoute.Thread -> {
            if (hasBoardImmediatelyBelowThread(tabsEntryId)) {
                if (!popBackStack<AppRoute.Board>(inclusive = false)) return
            } else {
                navigate(route) {
                    popUpTo(sourceRoute) { inclusive = true }
                }
            }
        }

        else -> return
    }
}

/**
 * Tabs上で選択したスレッドを、Tabsを除去した後の既存画面種別へ反映する。
 *
 * contextual TabsではBoard起点ならBoardを残してThreadを追加し、Thread起点なら既存Threadを
 * 再利用する。ルートTabsではTabsを残して通常のpush規則を使う。
 */
fun NavHostController.showThreadScreenFromTabs(
    sourceRoute: AppRoute?,
    tabsEntryId: String,
    route: AppRoute.Thread,
) {
    if (!isCurrentTabsEntry(tabsEntryId)) return
    if (sourceRoute == null) {
        showThreadScreenForTabSelection(currentScreenRoute = null, route = route)
        return
    }

    // Guard: contextual Tabsには必ず遷移元entryが存在するため、start destinationをpopしない。
    if (previousBackStackEntry == null) return
    // Guard: 非同期の選択処理中にTabsを離れていた場合は、古いcallbackで履歴を変更しない。
    when (sourceRoute) {
        is AppRoute.Board -> {
            navigateToThreadScreen(route) {
                popUpTo(sourceRoute) { inclusive = false }
            }
        }

        is AppRoute.Thread -> {
            if (!popBackStack()) return
        }

        else -> return
    }
}

/** 現在のentryが、指定されたTabs destinationのままかを検証する。 */
private fun NavHostController.isCurrentTabsEntry(tabsEntryId: String): Boolean =
    currentBackStackEntry?.id == tabsEntryId &&
        currentBackStackEntry?.destination?.hasRoute<AppRoute.Tabs>() == true

/**
 * 公開されているcurrentBackStackから、Tabsの直下がThread、その直下がBoardかを判定する。
 *
 * Graph entryなどBoard / Thread / Tabs以外のentryを除外し、選択中Tabsに対応する末尾の
 * destination列だけを判定対象にする。
 */
private fun NavHostController.hasBoardImmediatelyBelowThread(tabsEntryId: String): Boolean {
    val bbsEntries = currentBackStack.value.filter { entry ->
        entry.destination.hasRoute<AppRoute.Board>() ||
            entry.destination.hasRoute<AppRoute.Thread>() ||
            entry.destination.hasRoute<AppRoute.Tabs>()
    }
    val tabsIndex = bbsEntries.indexOfLast { it.id == tabsEntryId }
    if (tabsIndex < 2) return false
    return bbsEntries[tabsIndex - 1].destination.hasRoute<AppRoute.Thread>() &&
        bbsEntries[tabsIndex - 2].destination.hasRoute<AppRoute.Board>()
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
