package com.websarva.wings.android.slevo.data.util

import com.websarva.wings.android.slevo.data.datasource.local.entity.ThreadReadState

/**
 * スレッドの最新レス数と履歴に紐づく既読状態から、新着表示に使う値を導出するユーティリティ。
 * 板一覧とタブ一覧が同じ計算規則を共有し、保存済みの新着件数を持たずに表示値を生成する。
 */
object ThreadNewResCalculator {
    /**
     * 履歴の有無と最終既読番号から表示用の新着レス数を計算する。
     * 履歴がないスレッドは未訪問として扱い、新着レス数は常に 0 を返す。
     */
    fun calculate(latestResCount: Int, readState: ThreadReadState?): Int {
        val historyReadState = readState ?: return 0
        if (latestResCount <= historyReadState.lastReadResNo) {
            // Guard: 最新レス数が既読位置以下なら新着は存在しない。
            return 0
        }
        return (latestResCount - historyReadState.lastReadResNo).coerceAtLeast(0)
    }
}
