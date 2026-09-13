package com.websarva.wings.android.slevo.ui.navigation

import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph
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
            val targetRoute = route.copy(entryTransition = BbsEntryTransition.BoardThreadSlide)
            val previousIsBoard =
                previousBackStackEntry
                    ?.destination
                    ?.hasRoute<AppRoute.Board>() == true
            if (previousIsBoard) {
                popBackStack()
            } else {
                replaceCurrentScreen(currentScreenRoute, targetRoute)
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
        is AppRoute.Board -> navigateToThreadScreen(
            route.copy(entryTransition = BbsEntryTransition.BoardThreadSlide),
        )
        else -> navigateToThreadScreen(route)
    }
}

/**
 * Tabs上で選択した板を、Tabsを除去した後の既存画面種別へ反映する。
 *
 * contextual TabsではBoard起点の同種選択を、source BoardとTabsを除去して選択先Boardへ
 * 置換する。Thread起点では、直下Boardの有無に応じて既存のBoard再利用またはThread置換を
 * 行う。ルートTabsではTabsを残して通常のpush規則を使う。
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
            navigateToBoardScreen(route) {
                popUpTo(sourceRoute) { inclusive = true }
            }
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
 * contextual TabsではBoard起点ならBoardを残してThreadを追加し、Thread起点の同種選択なら
 * source ThreadとTabsを除去して選択先Threadへ置換する。ルートTabsではTabsを残して通常の
 * push規則を使う。
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
            navigateToThreadScreen(route) {
                popUpTo(sourceRoute) { inclusive = true }
            }
        }

        else -> return
    }
}

/**
 * MainShell内のTabsから選択した板をRoot Navigationへ反映する。
 *
 * contextual Tabsの直接選択（`origin == Tabs`）では遷移元Boardを置換し、Bookmarkなどを経由した
 * 選択では通常のRoot pushを行う。Rootとinnerのentry IDを確認して古い非同期callbackを抑止する。
 */
fun NavHostController.showBoardScreenFromMainShell(
    sourceRoute: AppRoute?,
    origin: MainShellBbsOrigin = MainShellBbsOrigin.Tabs,
    mainShellEntryId: String,
    tabsEntryId: String,
    mainShellNavController: NavHostController,
    route: AppRoute.Board,
) {
    if (!isCurrentMainShellEntry(mainShellEntryId)) return
    if (origin != MainShellBbsOrigin.Tabs) {
        navigateToBoardScreen(route.copy(entryTransition = BbsEntryTransition.MainShellSlide))
        return
    }
    if (!mainShellNavController.isCurrentTabsEntry(tabsEntryId)) return

    val targetRoute = route.copy(entryTransition = BbsEntryTransition.TabsSharedBounds)
    if (sourceRoute == null) {
        navigateToBoardScreen(targetRoute)
        return
    }

    // Guard: contextual MainShellではRoot側に必ずsource entryが存在する。
    if (previousBackStackEntry == null) return
    when (sourceRoute) {
        is AppRoute.Board -> navigateToBoardScreen(targetRoute) {
            popUpTo(sourceRoute) { inclusive = true }
        }

        is AppRoute.Thread -> {
            if (hasBoardImmediatelyBelowMainShell(mainShellEntryId)) {
                if (!popBackStack<AppRoute.Board>(inclusive = false)) return
            } else {
                navigateToBoardScreen(targetRoute) {
                    popUpTo(sourceRoute) { inclusive = true }
                }
            }
        }

        else -> return
    }
}

/**
 * MainShell内のTabsから選択したスレッドをRoot Navigationへ反映する。
 *
 * Board起点ではBoardを残してThreadを追加し、Thread起点ではThreadを選択先へ置換する。Tabs以外の
 * originではsourceを統合せず、通常のRoot pushを行う。
 */
fun NavHostController.showThreadScreenFromMainShell(
    sourceRoute: AppRoute?,
    origin: MainShellBbsOrigin = MainShellBbsOrigin.Tabs,
    mainShellEntryId: String,
    tabsEntryId: String,
    mainShellNavController: NavHostController,
    route: AppRoute.Thread,
) {
    if (!isCurrentMainShellEntry(mainShellEntryId)) return
    if (origin != MainShellBbsOrigin.Tabs) {
        navigateToThreadScreen(route.copy(entryTransition = BbsEntryTransition.MainShellSlide))
        return
    }
    if (!mainShellNavController.isCurrentTabsEntry(tabsEntryId)) return

    val targetRoute = route.copy(entryTransition = BbsEntryTransition.TabsSharedBounds)
    if (sourceRoute == null) {
        navigateToThreadScreen(targetRoute)
        return
    }

    // Guard: contextual MainShellではRoot側に必ずsource entryが存在する。
    if (previousBackStackEntry == null) return
    when (sourceRoute) {
        is AppRoute.Board -> navigateToThreadScreen(targetRoute) {
            popUpTo(sourceRoute) { inclusive = false }
        }

        is AppRoute.Thread -> navigateToThreadScreen(targetRoute) {
            popUpTo(sourceRoute) { inclusive = true }
        }

        else -> return
    }
}

/** 現在のentryが、指定されたTabs destinationのままかを検証する。 */
private fun NavHostController.isCurrentTabsEntry(tabsEntryId: String): Boolean =
    currentBackStackEntry?.id == tabsEntryId &&
        currentBackStackEntry?.destination?.hasRoute<AppRoute.Tabs>() == true

/** Root側の現在entryが、非同期Navigationを開始したMainShellのままかを検証する。 */
private fun NavHostController.isCurrentMainShellEntry(mainShellEntryId: String): Boolean =
    currentBackStackEntry?.id == mainShellEntryId &&
        currentBackStackEntry?.destination?.hasRoute<AppRoute.MainShell>() == true

/**
 * 公開されているcurrentBackStackから、Tabsの直下がThread、その直下がBoardかを判定する。
 *
 * graph entryだけを除外し、Bookmarkなどの実destinationは列に残すことで、より古いBoardを
 * 直下Boardとして誤再利用しない。
 */
private fun NavHostController.hasBoardImmediatelyBelowThread(tabsEntryId: String): Boolean {
    val destinationEntries = currentBackStack.value.filterNot { entry ->
        entry.destination is NavGraph
    }
    val tabsIndex = destinationEntries.indexOfLast { it.id == tabsEntryId }
    if (tabsIndex < 2) return false
    return destinationEntries[tabsIndex - 1].destination.hasRoute<AppRoute.Thread>() &&
        destinationEntries[tabsIndex - 2].destination.hasRoute<AppRoute.Board>()
}

/** MainShell直前のRoot entryがThread、その直前がBoardかを判定する。 */
private fun NavHostController.hasBoardImmediatelyBelowMainShell(mainShellEntryId: String): Boolean {
    val destinationEntries = currentBackStack.value.filterNot { entry ->
        entry.destination is NavGraph
    }
    val shellIndex = destinationEntries.indexOfLast { it.id == mainShellEntryId }
    if (shellIndex < 2) return false
    return destinationEntries[shellIndex - 1].destination.hasRoute<AppRoute.Thread>() &&
        destinationEntries[shellIndex - 2].destination.hasRoute<AppRoute.Board>()
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
