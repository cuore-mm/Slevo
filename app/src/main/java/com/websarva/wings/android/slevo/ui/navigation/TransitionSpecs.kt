package com.websarva.wings.android.slevo.ui.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally

internal const val DefaultAnimDuration = 300

/** TabsとBoard / ThreadページのShared Boundsを邪魔しないfade-only enterを返す。 */
fun bbsPageEnterTransition(): EnterTransition =
    fadeIn(animationSpec = tween(DefaultAnimDuration))

/** TabsとBoard / ThreadページのShared Boundsを邪魔しないfade-only exitを返す。 */
fun bbsPageExitTransition(): ExitTransition =
    fadeOut(animationSpec = tween(DefaultAnimDuration))

// --- 通常画面用トランジション ---
fun defaultEnterTransition(): EnterTransition =
    slideInHorizontally(
        initialOffsetX = { fullWidth -> fullWidth },
        animationSpec = tween(DefaultAnimDuration)
    ) + fadeIn(animationSpec = tween(DefaultAnimDuration))

fun defaultExitTransition(): ExitTransition =
    slideOutHorizontally(
        targetOffsetX = { fullWidth -> -fullWidth },
        animationSpec = tween(DefaultAnimDuration)
    ) + fadeOut(animationSpec = tween(DefaultAnimDuration))

fun defaultPopEnterTransition(): EnterTransition =
    slideInHorizontally(
        initialOffsetX = { fullWidth -> -fullWidth },
        animationSpec = tween(DefaultAnimDuration)
    ) + fadeIn(animationSpec = tween(DefaultAnimDuration))

fun defaultPopExitTransition(): ExitTransition =
    slideOutHorizontally(
        targetOffsetX = { fullWidth -> fullWidth },
        animationSpec = tween(DefaultAnimDuration)
    ) + fadeOut(animationSpec = tween(DefaultAnimDuration))

/** Board/Thread切替用にfadeを含めず右から入るtransitionを返す。 */
fun boardThreadEnterTransition(): EnterTransition =
    slideInHorizontally(
        initialOffsetX = { fullWidth -> fullWidth },
        animationSpec = tween(DefaultAnimDuration)
    )

/** Board/Thread切替用にfadeを含めず左へ抜けるtransitionを返す。 */
fun boardThreadExitTransition(): ExitTransition =
    slideOutHorizontally(
        targetOffsetX = { fullWidth -> -fullWidth },
        animationSpec = tween(DefaultAnimDuration)
    )

/** Board/Thread切替のpop用にfadeを含めず左から入るtransitionを返す。 */
fun boardThreadPopEnterTransition(): EnterTransition =
    slideInHorizontally(
        initialOffsetX = { fullWidth -> -fullWidth },
        animationSpec = tween(DefaultAnimDuration)
    )

/** Board/Thread切替のpop用にfadeを含めず右へ抜けるtransitionを返す。 */
fun boardThreadPopExitTransition(): ExitTransition =
    slideOutHorizontally(
        targetOffsetX = { fullWidth -> fullWidth },
        animationSpec = tween(DefaultAnimDuration)
    )

/** route文字列が指定されたdestination名を表すかを判定する。 */
private fun String?.isRouteNamed(routeName: String): Boolean {
    val routeNamePart = this
        ?.substringAfterLast('.')
        ?.substringBefore('/')
        ?.substringBefore('?')
    return routeNamePart == routeName
}

/** BoardからThreadへ進むrouteの組み合わせかを判定する。 */
fun isBoardToThreadTransition(
    initialRoute: String?,
    targetRoute: String?,
): Boolean {
    return initialRoute.isRouteNamed(AppRoute.RouteName.BOARD) &&
            targetRoute.isRouteNamed(AppRoute.RouteName.THREAD)
}

/** ThreadからBoardへ戻るrouteの組み合わせかを判定する。 */
fun isThreadToBoardTransition(
    initialRoute: String?,
    targetRoute: String?,
): Boolean {
    return initialRoute.isRouteNamed(AppRoute.RouteName.THREAD) &&
            targetRoute.isRouteNamed(AppRoute.RouteName.BOARD)
}

/** TabsからBoardまたはThreadへ進むrouteの組み合わせかを判定する。 */
fun isTabsToBbsTransition(
    initialRoute: String?,
    targetRoute: String?,
): Boolean {
    return initialRoute.isRouteNamed(AppRoute.RouteName.TABS) &&
        (targetRoute.isRouteNamed(AppRoute.RouteName.BOARD) ||
            targetRoute.isRouteNamed(AppRoute.RouteName.THREAD))
}

/** BoardまたはThreadからTabsへ戻るrouteの組み合わせかを判定する。 */
fun isBbsToTabsTransition(
    initialRoute: String?,
    targetRoute: String?,
): Boolean {
    return (initialRoute.isRouteNamed(AppRoute.RouteName.BOARD) ||
        initialRoute.isRouteNamed(AppRoute.RouteName.THREAD)) &&
        targetRoute.isRouteNamed(AppRoute.RouteName.TABS)
}

/** 2つのNav routeがBoardとThreadの組み合わせかを判定する。 */
fun isBoardThreadTransition(
    initialRoute: String?,
    targetRoute: String?,
): Boolean {
    return isBoardToThreadTransition(initialRoute, targetRoute) ||
            isThreadToBoardTransition(initialRoute, targetRoute)
}
