package com.websarva.wings.android.slevo.data.model

/**
 * タブ一覧 pager の page identity と serialized integer index を表す canonical enum。
 * 既存 entry の宣言順は永続化された index の意味を固定するため変更せず、新しい entry は末尾へ追加する。
 */
enum class TabPage {
    BOARD,
    THREAD;

    /** pager と serialized format で使用する、この page の index を返す。 */
    val index: Int
        get() = ordinal

    companion object {
        /** canonical page 定義から導出した pager の page 数を返す。 */
        val count: Int
            get() = entries.size

        /** serialized index を canonical page へ変換し、範囲外なら null を返す。 */
        fun fromIndex(index: Int): TabPage? = entries.getOrNull(index)

        /** serialized index が canonical page の有効範囲に含まれるかを返す。 */
        fun isValidIndex(index: Int): Boolean = fromIndex(index) != null
    }
}
