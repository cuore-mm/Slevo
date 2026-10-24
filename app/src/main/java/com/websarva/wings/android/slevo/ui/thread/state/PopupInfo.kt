package com.websarva.wings.android.slevo.ui.thread.state

import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize

/** ポップアップの通常レス表示と返信先候補一覧を区別する。 */
enum class PopupContentType {
    STANDARD,
    REPLY_TARGETS,
}

/**
 * 返信先一覧または通常のレス群ポップアップに表示する取得済みレス番号と配置情報を保持する。
 * [postNumbers]は最新の投稿一覧から解決し、未取得範囲は返信先一覧へ含めない。
 *
 * @property popupId ポップアップ表示箇所を識別する安定ID。
 * @property postNumbers 表示候補の1始まりのレス番号。
 * @property offset ポップアップの基準座標。
 * @property size ポップアップの計測済みサイズ。
 * @property indentLevels [postNumbers]に対応するツリーインデント。
 * @property rootNumbers ポップアップ内のツリー境界を判断するルート番号。
 * @property contentType 通常表示と返信先候補表示の区別。
 */
data class PopupInfo(
    val popupId: Long,
    val postNumbers: List<Int>,
    val offset: IntOffset,
    val size: IntSize = IntSize.Zero,
    val indentLevels: List<Int> = emptyList(),
    val rootNumbers: List<Int> = emptyList(),
    val contentType: PopupContentType = PopupContentType.STANDARD,
)
