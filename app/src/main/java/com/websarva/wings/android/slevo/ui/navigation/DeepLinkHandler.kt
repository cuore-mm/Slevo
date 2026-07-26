package com.websarva.wings.android.slevo.ui.navigation

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavHostController
import com.websarva.wings.android.slevo.R
import com.websarva.wings.android.slevo.data.model.ThreadId
import com.websarva.wings.android.slevo.ui.tabs.store.TabSessionStore
import com.websarva.wings.android.slevo.ui.util.ResolvedUrl
import com.websarva.wings.android.slevo.ui.util.resolveDeepLinkUrl
import com.websarva.wings.android.slevo.ui.util.parseBoardUrl
import kotlinx.coroutines.CancellationException

/**
 * Deep Linkを受け取り、板/スレ画面へ遷移する。
 */
@Composable
fun DeepLinkHandler(
    deepLinkUrl: String?,
    navController: NavHostController,
    tabSessionStore: TabSessionStore,
    onConsumed: () -> Unit
) {
    val context = LocalContext.current
    val invalidUrlMessage = stringResource(R.string.invalid_url)

    LaunchedEffect(deepLinkUrl) {
        if (deepLinkUrl.isNullOrBlank()) {
            return@LaunchedEffect
        }

        try {
            // --- ルーティング ---
            val handled = handleDeepLinkUrl(
                url = deepLinkUrl,
                navController = navController,
                tabSessionStore = tabSessionStore
            )

            if (!handled) {
                Toast.makeText(context, invalidUrlMessage, Toast.LENGTH_SHORT).show()
            }
        } finally {
            onConsumed()
        }
    }
}

/**
 * Deep LinkのURLを解析して遷移し、成功時にtrueを返す。
 */
private suspend fun handleDeepLinkUrl(
    url: String,
    navController: NavHostController,
    tabSessionStore: TabSessionStore
): Boolean {
    // --- 対象の解決 ---
    val target = resolveDeepLinkUrl(url) ?: return false // 対象外URLは処理しない。

    // --- 遷移 ---
    return when (target) {
        is ResolvedUrl.ItestBoard -> {
            val host = tabSessionStore.resolveBoardHost(
                boardKey = target.boardKey,
                sourceUrl = target.rawUrl,
            ) ?: return false
            val boardUrl = "https://$host/${target.boardKey}/"
            val route = tabSessionStore.normalizeBoardRouteForNavigation(
                AppRoute.Board(
                    boardName = boardUrl,
                    boardUrl = boardUrl
                )
            )
            handleBoardDeepLinkRoute(route, tabSessionStore) {
                navController.navigateToBoardScreen(route)
            }
        }
        is ResolvedUrl.Thread -> {
            val boardUrl = "https://${target.host}/${target.boardKey}/"
            val route = tabSessionStore.normalizeThreadRouteForNavigation(
                AppRoute.Thread(
                    threadKey = target.threadKey,
                    boardUrl = boardUrl,
                    boardName = target.boardKey,
                    threadTitle = null
                )
            )
            try {
                handleThreadDeepLinkRoute(
                    route = route,
                    tabSessionStore = tabSessionStore,
                    navigate = { navController.navigateToThreadScreen(route) },
                )
            } catch (cancellationException: CancellationException) {
                throw cancellationException
            } catch (_: Throwable) {
                false
            }
        }
        is ResolvedUrl.Board -> {
            val boardUrl = "https://${target.host}/${target.boardKey}/"
            val route = tabSessionStore.normalizeBoardRouteForNavigation(
                AppRoute.Board(
                    boardName = boardUrl,
                    boardUrl = boardUrl
                )
            )
            handleBoardDeepLinkRoute(route, tabSessionStore) {
                navController.navigateToBoardScreen(route)
            }
        }
        is ResolvedUrl.Unknown -> false
    }
}

/**
 * スレッドのディープリンク登録を選択や遷移より先に完了させる。
 * callback は準備完了、正規状態の存在確認、選択がすべて成功した後にだけ呼び出す。
 */
internal suspend fun handleThreadDeepLinkRoute(
    route: AppRoute.Thread,
    tabSessionStore: TabSessionStore,
    navigate: () -> Unit,
): Boolean {
    // --- 準備完了 ---
    tabSessionStore.awaitThreadTabsReady()
    val result = tabSessionStore.registerAndSelectThreadRouteCommand(route)
    if (result == null) {
        // 旧テスト double との接続だけは従来の順序を維持し、本番 Store は上の明示 result を使う。
        val threadId = parseBoardUrl(route.boardUrl)?.let { (host, board) ->
            ThreadId.of(host, board, route.threadKey)
        } ?: return false
        val registrationIndex = tabSessionStore.registerThreadRoute(route)
        if (registrationIndex < 0 || !tabSessionStore.isCanonicalThreadTab(threadId)) return false
        if (!tabSessionStore.selectThreadTab(threadId)) return false
    }
    if (result != null && result !is com.websarva.wings.android.slevo.ui.tabs.controller.TabCommandResult.Success) return false

    // --- 明示 result 後の遷移 ---
    navigate()
    return true
}

/**
 * 板の登録・選択確認を navigation より先に完了させる。
 * target が atomic state の Selected にならない場合は既存画面を維持する。
 */
internal suspend fun handleBoardDeepLinkRoute(
    route: AppRoute.Board,
    tabSessionStore: TabSessionStore,
    navigate: () -> Unit,
): Boolean {
    if (!tabSessionStore.registerAndConfirmBoardRoute(route)) return false
    navigate()
    return true
}
