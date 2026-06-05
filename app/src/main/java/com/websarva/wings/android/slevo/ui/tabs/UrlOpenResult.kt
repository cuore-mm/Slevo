package com.websarva.wings.android.slevo.ui.tabs

import com.websarva.wings.android.slevo.ui.navigation.AppRoute

/**
 * URL入力ダイアログからの解決結果を表す sealed class。
 *
 * 板遷移、スレッド遷移、またはエラーのいずれかを示す。
 */
sealed class UrlOpenResult {
    data class NavigateBoard(val route: AppRoute.Board) : UrlOpenResult()
    data class NavigateThread(val route: AppRoute.Thread) : UrlOpenResult()
    data class Error(val message: String?) : UrlOpenResult()
}
