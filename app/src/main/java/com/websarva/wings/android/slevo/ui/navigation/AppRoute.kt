package com.websarva.wings.android.slevo.ui.navigation

import kotlinx.serialization.Serializable

/**
 * アプリ内の画面遷移に使用する型安全なrouteを表す。
 *
 * RootとMainShellの所属は各具象routeが実装するmarker interfaceで表し、marker自体はdestinationに
 * 登録しない。
 */
@Serializable
sealed class AppRoute {
    @Serializable
    data object BookmarkList : AppRoute(), MainShellRoute

    @Serializable
    data object HistoryList : AppRoute(), RootRoute

    @Serializable
    data object BbsServiceGroup : AppRoute(), MainShellRoute

    @Serializable
    data object ServiceList : AppRoute(), MainShellRoute

    @Serializable
    data class BoardCategoryList(val serviceId: Long, val serviceName: String) : AppRoute(), MainShellRoute

    @Serializable
    data class BoardListByCategory(
        val serviceId: Long,
        val categoryId: Long,
        val serviceName: String,
        val categoryName: String,
    ) : AppRoute(), MainShellRoute

    @Serializable
    data class Board(
        val boardId: Long? = null, // 任意：未登録の場合は画面側で解決
        val boardName: String,
        val boardUrl: String,
        val entryTransition: BbsEntryTransition = BbsEntryTransition.Default,
    ) : AppRoute(), RootRoute

    @Serializable
    data class Thread(
        val threadKey: String, // 必須：スレッド識別子
        val boardUrl: String, // 必須：板URL（datUrl導出、投稿情報のため）
        val boardName: String, // 推奨：表示用
        val boardId: Long? = null, // 任意：未登録の場合は画面側で解決
        val threadTitle: String? = null, // 任意：未取得時はnull（読み込み後に実タイトルで更新）
        val resCount: Int = 0, // 表示用: レス数
        val entryTransition: BbsEntryTransition = BbsEntryTransition.Default,
    ) : AppRoute(), RootRoute

    @Serializable
    data object Settings : AppRoute(), RootRoute

    @Serializable
    data object SettingsHome : AppRoute(), RootRoute

    @Serializable
    data object SettingsGeneral : AppRoute(), RootRoute

    @Serializable
    data object SettingsNg : AppRoute(), RootRoute

    @Serializable
    data object SettingsThread : AppRoute(), RootRoute

    @Serializable
    data object SettingsCookie : AppRoute(), RootRoute

    @Serializable
    data object SettingsGesture : AppRoute(), RootRoute

    @Serializable
    data object SettingsBackup : AppRoute(), RootRoute

    @Serializable
    data object Tabs : AppRoute(), MainShellRoute

    /**
     * NavigationBarを含むMainShellのRoot entryを表す。
     *
     * Baseは通常起動用、ContextualTabsはBoardまたはThreadから開くTabs用で、開始するinner画面は
     * `startDestination`で指定する。
     */
    @Serializable
    data class MainShell(
        val mode: MainShellMode = MainShellMode.Base,
        val startDestination: MainShellStartDestination = MainShellStartDestination.Tabs,
    ) : AppRoute(), RootRoute

    /**
     * 画像ビューアの遷移情報を保持する。
     *
     * 同一レス内の画像URL一覧と初期表示位置、shared transition用文脈を受け取る。
     */
    @Serializable
    data class ImageViewer(
        val imageUrls: List<String>,
        val initialIndex: Int,
        val transitionNamespace: String = "",
    ) : AppRoute(), RootRoute

    @Serializable
    data object About : AppRoute(), RootRoute

    @Serializable
    data object OpenSourceLicense : AppRoute(), RootRoute

    /** route判定で使用するdestination名をまとめる。 */
    data object RouteName {
        const val BOOKMARK_LIST = "BookmarkList"
        const val BBS_SERVICE_GROUP = "BbsServiceGroup"
        const val SERVICE_LIST = "ServiceList"
        const val BOARD_CATEGORY_LIST = "BoardCategoryList"
        const val BOARD_LIST_BY_CATEGORY = "BoardListByCategory"
        const val BOARD = "Board"
        const val THREAD = "Thread"
        const val SETTINGS = "Settings"
        const val SETTINGS_HOME = "SettingsHome"
        const val SETTINGS_GENERAL = "SettingsGeneral"
        const val SETTINGS_NG = "SettingsNg"
        const val SETTINGS_THREAD = "SettingsThread"
        const val SETTINGS_COOKIE = "SettingsCookie"
        const val SETTINGS_GESTURE = "SettingsGesture"
        const val SETTINGS_BACKUP = "SettingsBackup"
        const val TABS = "Tabs"
        const val HISTORY_LIST = "HistoryList"
        const val ABOUT = "About"
        const val OPEN_SOURCE_LICENSE = "OpenSourceLicense"
        const val IMAGE_VIEWER = "ImageViewer"
    }
}
