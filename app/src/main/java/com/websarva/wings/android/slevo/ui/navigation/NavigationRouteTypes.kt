package com.websarva.wings.android.slevo.ui.navigation

import androidx.annotation.Keep
import kotlinx.serialization.Serializable

/**
 * Root Navigationで管理されるrouteが実装する所属marker。
 *
 * この型自体はNavigation destinationとして登録せず、RootとMainShellの責務を静的に区別する。
 */
sealed interface RootRoute

/**
 * MainShell内部のNavigationで管理されるrouteが実装する所属marker。
 *
 * この型自体はNavigation destinationとして登録せず、MainShell内の画面を識別する。
 */
sealed interface MainShellRoute

/**
 * Root stack上のMainShellがどの用途で生成されたかを表す。
 *
 * Baseはアプリ起動時のMainShell、ContextualTabsはBoardまたはThreadから開くタブ一覧用である。
 */
@Keep
@Serializable
enum class MainShellMode {
    Base,
    ContextualTabs,
}

/**
 * 新しく生成するMainShellで最初に表示するinner destinationを表す。
 *
 * 通常の起動はTabsを使い、BoardまたはThread上の既存メニューから開く場合だけBookmarkまたは
 * BBSサービス一覧を初期画面として指定する。
 */
@Keep
@Serializable
enum class MainShellStartDestination {
    Tabs,
    BookmarkList,
    BbsServiceGroup,
}

/**
 * BoardまたはThread destinationへ入るときに選択するRoot transitionの文脈。
 *
 * routeへ保存できる値だけを持ち、実際のCompose transitionはRoot graph側で解決する。
 */
@Keep
@Serializable
enum class BbsEntryTransition {
    TabsSharedBounds,
    MainShellSlide,
    BoardThreadSlide,
    DeepLink,
    Default,
}

/**
 * MainShell内部からBBS destinationを開いた入口の種別。
 *
 * Contextual Tabsの直接選択だけが遷移元を統合し、それ以外は中間履歴を保持する。
 */
@Keep
@Serializable
enum class MainShellBbsOrigin {
    Tabs,
    Bookmark,
    BbsServiceGroup,
}
