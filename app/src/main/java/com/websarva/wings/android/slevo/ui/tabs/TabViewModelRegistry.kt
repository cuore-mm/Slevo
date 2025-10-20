package com.websarva.wings.android.slevo.ui.tabs

import com.websarva.wings.android.slevo.ui.board.BoardViewModel
import com.websarva.wings.android.slevo.ui.board.BoardViewModelFactory
import com.websarva.wings.android.slevo.ui.thread.viewmodel.ThreadViewModel
import com.websarva.wings.android.slevo.ui.thread.viewmodel.ThreadViewModelFactory
import javax.inject.Inject

class TabViewModelRegistry @Inject constructor(
    private val threadViewModelFactory: ThreadViewModelFactory,
    private val boardViewModelFactory: BoardViewModelFactory,
) {
    private val boardViewModels: MutableMap<String, BoardViewModel> = mutableMapOf()
    private val threadViewModels: MutableMap<String, ThreadViewModel> = mutableMapOf()

    fun getOrCreateBoardViewModel(boardUrl: String): BoardViewModel {
        return boardViewModels.getOrPut(boardUrl) {
            boardViewModelFactory.create(boardUrl)
        }
    }

    fun getOrCreateThreadViewModel(viewModelKey: String): ThreadViewModel {
        return threadViewModels.getOrPut(viewModelKey) {
            threadViewModelFactory.create(viewModelKey)
        }
    }

    fun releaseBoardViewModel(boardUrl: String) {
        boardViewModels.remove(boardUrl)?.release()
    }

    fun releaseThreadViewModel(viewModelKey: String) {
        threadViewModels.remove(viewModelKey)?.release()
    }

    fun releaseAll() {
        threadViewModels.values.forEach { it.release() }
        threadViewModels.clear()
        boardViewModels.values.forEach { it.release() }
        boardViewModels.clear()
    }
}
