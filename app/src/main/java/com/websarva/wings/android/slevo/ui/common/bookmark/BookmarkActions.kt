package com.websarva.wings.android.slevo.ui.common.bookmark

import com.websarva.wings.android.slevo.data.model.Groupable
import kotlinx.coroutines.flow.StateFlow

/**
 * 板/スレッド共通のブックマーク操作を提供する。
 *
 * 共通UIはこのインターフェース経由でブックマーク操作を呼び出す。
 */
interface BookmarkActions {
    /** ブックマーク関連のUI状態を提供する。 */
    val bookmarkState: StateFlow<SingleBookmarkState>

    /** 指定グループにブックマークを保存する。 */
    fun saveBookmark(groupId: Long)

    /** 現在のブックマークを解除する。 */
    fun unbookmarkBoard()

    /** ブックマーク選択シートを表示する。 */
    fun openBookmarkSheet()

    /** ブックマーク選択シートを閉じる。 */
    fun closeBookmarkSheet()

    /** グループ追加ダイアログを開く。 */
    fun openAddGroupDialog()

    /** グループ編集ダイアログを開く。 */
    fun openEditGroupDialog(group: Groupable)

    /** グループ追加/編集ダイアログを閉じる。 */
    fun closeAddGroupDialog()

    /** 入力中のグループ名を更新する。 */
    fun setEnteredGroupName(name: String)

    /** 選択中のグループカラーを更新する。 */
    fun setSelectedColor(color: String)

    /** グループ追加/更新を確定する。 */
    fun confirmGroup()

    /** グループ削除確認ダイアログを開く。 */
    fun requestDeleteGroup()

    /** グループ削除を確定する。 */
    fun confirmDeleteGroup()

    /** グループ削除確認ダイアログを閉じる。 */
    fun closeDeleteGroupDialog()
}
