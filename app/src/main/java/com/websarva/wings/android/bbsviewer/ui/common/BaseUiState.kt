package com.websarva.wings.android.bbsviewer.ui.common

interface BaseUiState<T> where T : BaseUiState<T> {
    val isLoading: Boolean
    val showAddGroupDialog: Boolean
    val enteredGroupName: String
    val selectedColor: String?
    val showTabListSheet: Boolean

    // 共通プロパティを更新して、自身の具象型の新しいインスタンスを返すメソッド
    fun copyState(
        isLoading: Boolean = this.isLoading,
        showAddGroupDialog: Boolean = this.showAddGroupDialog,
        enteredGroupName: String = this.enteredGroupName,
        selectedColor: String? = this.selectedColor,
        showTabListSheet: Boolean = this.showTabListSheet
    ): T
}
