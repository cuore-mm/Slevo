package com.websarva.wings.android.bbsviewer.ui.thread

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.websarva.wings.android.bbsviewer.data.repository.DatRepository
import com.websarva.wings.android.bbsviewer.data.util.parseDat
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.nio.charset.Charset
import javax.inject.Inject

@HiltViewModel
class ThreadViewModel @Inject constructor(
    private val repository: DatRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ThreadUiState())
    val uiState: StateFlow<ThreadUiState> = _uiState.asStateFlow()

    var enteredUrl by mutableStateOf("")
        private set

    fun updateTextField(input: String) {
        enteredUrl = input
    }

    fun loadThread(datUrl: String) {
        _uiState.update { it.copy(isLoading = true) }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                _uiState.update {
                    it.copy(
                        posts = repository.getThread(datUrl).first,
                        title = repository.getThread(datUrl).second ?: "",
                        isLoading = false
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
                // エラー処理として、必要に応じてエラーステートを反映するなどの対応を行う
            }
        }
    }

    fun parseUrl() {
        // URLを解析
        val parsed = parseThreadUrl(enteredUrl)
        if (parsed != null) {
            val (board, thread) = parsed
            val datUrl = createDatUrl(board, thread)
            Log.i("BBSViewer", datUrl)
            /*
            loadDat(datUrl)
            if (_datContent.value != null) {
                _uiState.update { currentState ->
                    currentState.copy(posts = parseDat(_datContent.value!!))
                }
            } else {
                _uiState.update { currentState ->
                    currentState.copy(posts = emptyList())
                } // エラー時は空リスト
                Log.i("BBSViewer", "fetchDatData failure")
            }
             */
        }
    }

    /*
    入力されたURLからホスト名/板名/スレッドIDを抽出
     */
    private fun parseThreadUrl(url: String): Pair<String, String>? {
        val regex = Regex("""https://([^/]+)/test/read.cgi/([^/]+)/(\d+)""")
        val matchResult = regex.find(url)
        return matchResult?.let {
            val hostName = it.groupValues[1] // ホスト名
            val boardName = it.groupValues[2] // 板の名前
            val threadId = it.groupValues[3] // スレッドID

            Log.i("BBSViewer", "Host: $hostName, Board: $boardName, ThreadID: $threadId")
            Pair("$hostName/$boardName", threadId)
        }
    }

    /*
    datファイルのURLに変換
     */
    private fun createDatUrl(boardPath: String, threadId: String): String {
        return "https://$boardPath/dat/$threadId.dat"
    }
}
