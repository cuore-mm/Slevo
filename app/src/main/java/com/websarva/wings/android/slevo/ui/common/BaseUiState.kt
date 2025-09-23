package com.websarva.wings.android.slevo.ui.common

interface BaseUiState<T> where T : BaseUiState<T> {
    val isLoading: Boolean

    // 共通プロパティを更新して、自身の具象型の新しいインスタンスを返すメソッド
    fun copyState(
        isLoading: Boolean = this.isLoading,
    ): T
}
