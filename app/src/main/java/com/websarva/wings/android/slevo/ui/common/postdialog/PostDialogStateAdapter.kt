package com.websarva.wings.android.slevo.ui.common.postdialog

/**
 * PostDialogControllerから画面固有のUI状態へアクセスするためのアダプタ。
 *
 * Board/Threadの状態構造の差分を吸収し、共通のPostDialogStateで操作できるようにする。
 */
interface PostDialogStateAdapter {
    /**
     * 現在のPostDialogStateを取得する。
     */
    fun readState(): PostDialogState

    /**
     * PostDialogStateを更新する。
     */
    fun updateState(transform: (PostDialogState) -> PostDialogState)
}
