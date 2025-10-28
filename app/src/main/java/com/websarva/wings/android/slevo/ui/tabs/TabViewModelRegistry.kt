package com.websarva.wings.android.slevo.ui.tabs

import com.websarva.wings.android.slevo.ui.board.viewmodel.BoardViewModel
import com.websarva.wings.android.slevo.ui.board.viewmodel.BoardViewModelFactory
import com.websarva.wings.android.slevo.ui.thread.viewmodel.ThreadViewModel
import com.websarva.wings.android.slevo.ui.thread.viewmodel.ThreadViewModelFactory
import dagger.hilt.android.scopes.ViewModelScoped
import javax.inject.Inject

@ViewModelScoped
/**
 * タブに関連する ViewModel を登録・再利用するシンプルなレジストリ。
 *
 * - 目的: 同じボードやスレッドを表示する複数の画面間で ViewModel を使い回すことで
 *   不要な生成を避け、状態（例: スクロール位置や読み込み状態）を保持する。
 * - スコープ: `@ViewModelScoped` により Hilt の ViewModel スコープと同等のライフサイクルで管理される。
 * - 実装: 内部に Map を持ち、キー（boardUrl / viewModelKey）に対して lazy に ViewModel を生成する。
 */
class TabViewModelRegistry @Inject constructor(
    private val threadViewModelFactory: ThreadViewModelFactory,
    private val boardViewModelFactory: BoardViewModelFactory,
) {
    // ボード用 ViewModel のキャッシュ。キーは boardUrl。
    private val boardViewModels: MutableMap<String, BoardViewModel> = mutableMapOf()

    // スレッド用 ViewModel のキャッシュ。キーは任意の viewModelKey（例: スレッド ID）
    private val threadViewModels: MutableMap<String, ThreadViewModel> = mutableMapOf()

    /**
     * 指定した boardUrl に対応する BoardViewModel を返す。既にキャッシュに存在すればそれを返し、
     * なければ factory で生成してキャッシュに格納してから返す。
     *
     * 入力: boardUrl
     * 出力: BoardViewModel（常に非 null）
     */
    fun getOrCreateBoardViewModel(boardUrl: String): BoardViewModel {
        return boardViewModels.getOrPut(boardUrl) {
            boardViewModelFactory.create(boardUrl)
        }
    }

    /**
     * 指定した viewModelKey に対応する ThreadViewModel を返す。既にキャッシュに存在すればそれを返し、
     * なければ factory で生成してキャッシュに格納してから返す。
     *
     * 入力: viewModelKey
     * 出力: ThreadViewModel（常に非 null）
     */
    fun getOrCreateThreadViewModel(viewModelKey: String): ThreadViewModel {
        return threadViewModels.getOrPut(viewModelKey) {
            threadViewModelFactory.create(viewModelKey)
        }
    }

    /**
     * 指定した boardUrl に紐づく BoardViewModel をキャッシュから削除し、
     * 存在した場合はその ViewModel の release() を呼んでリソース解放を行う。
     */
    fun releaseBoardViewModel(boardUrl: String) {
        boardViewModels.remove(boardUrl)?.release()
    }

    /**
     * 指定した viewModelKey に紐づく ThreadViewModel をキャッシュから削除し、
     * 存在した場合はその ViewModel の release() を呼んでリソース解放を行う。
     */
    fun releaseThreadViewModel(viewModelKey: String) {
        threadViewModels.remove(viewModelKey)?.release()
    }

    /**
     * キャッシュ内の全 ViewModel を release() してからキャッシュをクリアする。
     * 通常、アプリの大きな状態遷移やコンテキストが破棄されるタイミングで呼ばれる想定。
     */
    fun releaseAll() {
        threadViewModels.values.forEach { it.release() }
        threadViewModels.clear()
        boardViewModels.values.forEach { it.release() }
        boardViewModels.clear()
    }
}
