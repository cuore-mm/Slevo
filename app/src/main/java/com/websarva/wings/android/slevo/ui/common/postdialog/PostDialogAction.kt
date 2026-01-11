package com.websarva.wings.android.slevo.ui.common.postdialog

/**
 * PostDialog内で発生するUI操作を表すアクション。
 */
sealed class PostDialogAction {
    /**
     * 名前入力を変更する操作。
     */
    data class ChangeName(val value: String) : PostDialogAction()

    /**
     * メール入力を変更する操作。
     */
    data class ChangeMail(val value: String) : PostDialogAction()

    /**
     * タイトル入力を変更する操作。
     */
    data class ChangeTitle(val value: String) : PostDialogAction()

    /**
     * 本文入力を変更する操作。
     */
    data class ChangeMessage(val value: String) : PostDialogAction()

    /**
     * 名前履歴を選択する操作。
     */
    data class SelectNameHistory(val value: String) : PostDialogAction()

    /**
     * メール履歴を選択する操作。
     */
    data class SelectMailHistory(val value: String) : PostDialogAction()

    /**
     * 名前履歴を削除する操作。
     */
    data class DeleteNameHistory(val value: String) : PostDialogAction()

    /**
     * メール履歴を削除する操作。
     */
    data class DeleteMailHistory(val value: String) : PostDialogAction()

    /**
     * 投稿を実行する操作。
     */
    object Post : PostDialogAction()
}
