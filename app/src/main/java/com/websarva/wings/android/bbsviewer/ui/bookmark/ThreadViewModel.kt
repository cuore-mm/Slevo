package com.websarva.wings.android.bbsviewer.ui.bookmark

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.websarva.wings.android.bbsviewer.data.DatRepository
import dagger.hilt.android.lifecycle.HiltViewModel
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
    private val _datContent = MutableStateFlow<String?>(null)
    val datContent: StateFlow<String?> get() = _datContent.asStateFlow()

    private val _uiState = MutableStateFlow(ThreadUiState())
    val uiState: StateFlow<ThreadUiState> = _uiState.asStateFlow()

    var enteredUrl by mutableStateOf("")
        private set

    fun updateTextField(input: String) {
        enteredUrl = input
    }

    private fun loadDat(url: String) {
        viewModelScope.launch {
            _datContent.value = repository.fetchDatData(url)
            Log.i("BBSViewer", "_datContent success")
        }
    }

    fun parseUrl() {
        // URLを解析
        val parsed = parseThreadUrl(enteredUrl)
        if (parsed != null) {
            val (board, thread) = parsed
            val datUrl = createDatUrl(board, thread)
            Log.i("BBSViewer", datUrl)

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

    private fun fetchDatData(datUrl: String, onResult: (String?) -> Unit) {
        val client = OkHttpClient()
        val request = Request.Builder().url(datUrl).build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: okhttp3.Call, e: IOException) {
                onResult(null)
            }

            override fun onResponse(call: okhttp3.Call, response: Response) {
                if (response.isSuccessful) {
                    val data = response.body?.bytes()?.toString(Charset.forName("Shift_JIS"))
                    onResult(data)
                } else {
                    onResult(null)
                }
            }
        })
    }

    private fun parseDat(dat: String): List<ThreadPost> {
        val regex = Regex("^(.+?)<>(.*?)<>(.*?)\\s+ID:(\\w+)<>\\s(.*)\\s<>$")

        return dat.split("\n").mapNotNull { line ->
            val match = regex.find(line)
            if (match != null) {
                ThreadPost(
                    name = match.groupValues[1],  // 名前
                    email = match.groupValues[2], // メール
                    date = match.groupValues[3],  // 日付
                    id = match.groupValues[4],    // ID
                    content = match.groupValues[5] // 本文
                )
            } else {
                null
            }
        }
    }
}
