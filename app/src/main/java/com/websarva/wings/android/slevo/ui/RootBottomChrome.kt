package com.websarva.wings.android.slevo.ui

/**
 * Root overlayが避けるアプリ内下部chromeの実測高さを保持する。
 *
 * MainShellとBoard / Threadは別の下部chromeを所有するため、RootNavHostへ渡さずSnackbarの
 * 配置だけで参照する。高さは同一値の更新を呼び出し側で抑止できる不変値として扱う。
 */
internal data class RootBottomChromeHeights(
    val mainShellHeightPx: Int = 0,
    val bbsHeightPx: Int = 0,
) {
    /** 表示中のRoot destination種別に対応する下部chrome高さを返す。 */
    fun heightFor(owner: RootBottomChromeOwner): Int = when (owner) {
        RootBottomChromeOwner.MainShell -> mainShellHeightPx
        RootBottomChromeOwner.Bbs -> bbsHeightPx
        RootBottomChromeOwner.None -> 0
    }
}

/**
 * Root overlayが回避対象とする下部chromeの所有者。
 *
 * NoneはHistoryやSettingsなど、アプリ内下部chromeを持たないRoot destinationを表す。
 */
internal enum class RootBottomChromeOwner {
    MainShell,
    Bbs,
    None,
}
