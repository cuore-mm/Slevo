package com.websarva.wings.android.slevo.ui.common.transition

/**
 * 画像 shared transition の namespace とキーを生成するユーティリティ。
 *
 * 遷移元と遷移先の両方で同じロジックを使うことで、キー形式の不一致を防ぐ。
 */
object ImageSharedTransitionKeyFactory {
    private const val KEY_SEPARATOR = "|"

    /**
     * 通常スレッド表示の投稿向け namespace を返す。
     */
    fun threadPostNamespace(postNumber: Int): String {
        return "thread-post-$postNumber"
    }

    /**
     * ポップアップ内投稿向け namespace を返す。
     */
    fun popupPostNamespace(popupId: Long, postNumber: Int): String {
        return "popup-$popupId-post-$postNumber"
    }

    /**
     * 投稿ダイアログ向け namespace を返す。
     */
    fun postDialogNamespace(modeName: String): String {
        return "post-dialog-$modeName"
    }

    /**
     * namespace・URL・画像インデックスから shared transition キーを構築する。
     */
    fun buildKey(transitionNamespace: String, imageUrl: String, imageIndex: Int): String {
        return "$transitionNamespace$KEY_SEPARATOR$imageUrl$KEY_SEPARATOR$imageIndex"
    }
}
