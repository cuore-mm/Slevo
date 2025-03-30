package com.websarva.wings.android.bbsviewer.ui.threadlist

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.websarva.wings.android.bbsviewer.data.ThreadRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ThreadListViewModel @Inject constructor(
    private val threadRepository: ThreadRepository
) : ViewModel() {

    // 初期状態として空のスレッドリストを設定
    private val _uiState = MutableStateFlow(ThreadListUiState())
    val uiState: StateFlow<ThreadListUiState> = _uiState.asStateFlow()

    @RequiresApi(Build.VERSION_CODES.O)
    fun loadThreads(url: String) {
        _uiState.update { it.copy(isLoading = true) }
        val subjectUrl = "${url}subject.txt"
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Repositoryからsubject.txtを取得してパースした結果を取得
                val threadListState = threadRepository.getThreadList(subjectUrl)
                _uiState.value = ThreadListUiState(threads = threadListState)
                _uiState.update { it.copy(isLoading = false) }
            } catch (e: Exception) {
                e.printStackTrace()
                // エラー処理として、必要に応じてエラーステートを反映するなどの対応を行う
            }
        }
    }
}

