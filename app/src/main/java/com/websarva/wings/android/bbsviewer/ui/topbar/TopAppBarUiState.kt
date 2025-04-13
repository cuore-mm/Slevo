package com.websarva.wings.android.bbsviewer.ui.topbar

data class TopAppBarUiState(
    val title: String = "",
    val type: AppBarType = AppBarType.None
)

enum class AppBarType {
    Home,
    HomeWithScroll,
    Small,
    Thread,
    None
}
