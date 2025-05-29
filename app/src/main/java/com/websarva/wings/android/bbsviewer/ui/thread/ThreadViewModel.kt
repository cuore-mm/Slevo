package com.websarva.wings.android.bbsviewer.ui.thread

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.websarva.wings.android.bbsviewer.data.datasource.local.entity.BookmarkThreadEntity
import com.websarva.wings.android.bbsviewer.data.model.BoardInfo
import com.websarva.wings.android.bbsviewer.data.repository.BookmarkRepository
import com.websarva.wings.android.bbsviewer.data.repository.ConfirmationData
import com.websarva.wings.android.bbsviewer.data.repository.DatRepository
import com.websarva.wings.android.bbsviewer.data.repository.PostRepository
import com.websarva.wings.android.bbsviewer.data.repository.PostResult
import com.websarva.wings.android.bbsviewer.ui.util.keyToDatUrl
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ThreadViewModel @Inject constructor(
    private val datRepository: DatRepository,
    private val bookmarkRepository: BookmarkRepository,
    private val postRepository: PostRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ThreadUiState())
    val uiState: StateFlow<ThreadUiState> = _uiState.asStateFlow()

    var enteredUrl by mutableStateOf("")
        private set

    fun loadThread(datUrl: String) {
        _uiState.update { it.copy(isLoading = true) }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val (posts, title) = datRepository.getThread(datUrl)
                _uiState.update {
                    it.copy(
                        posts = posts,
                        isLoading = false,
                        // タイトルが取得できたら更新
                        threadInfo = it.threadInfo.copy(title = title ?: it.threadInfo.title)
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

    // お気に入り登録処理
    fun bookmarkThread() {
        // 現在のuiStateから情報を取得してBookmarkThreadEntityを生成
        val currentState = _uiState.value
        viewModelScope.launch {
            val bookmark = BookmarkThreadEntity(
                threadUrl = currentState.threadInfo.url,
                title = currentState.threadInfo.title,
                boardName = currentState.boardInfo.name,
                resCount = currentState.threadInfo.resCount
            )
            bookmarkRepository.insertBookmark(bookmark)
        }
    }

    //画面遷移した最初に行う初期処理
    fun initializeThread(
        threadKey: String,
        boardInfo: BoardInfo,
        threadTitle: String
    ) {
        val boardUrl = boardInfo.url
        val datUrl = keyToDatUrl(boardUrl, threadKey) // 導出

        val uri = boardUrl.toUri()
        val host = uri.host
        // boardUrl の末尾が / の場合とそうでない場合を考慮
        val boardKey = uri.pathSegments.filter { it.isNotEmpty() }.firstOrNull()

        val readCgiUrl = if (host != null && boardKey != null) {
            "https://${host}/test/read.cgi/${boardKey}/${threadKey}"
        } else {
            "" // 不正な URL の場合は空にするか、エラー処理
        }


        _uiState.update {
            it.copy(
                threadInfo = it.threadInfo.copy(
                    key = threadKey,
                    datUrl = datUrl, // 導出した datUrl を保持
                    title = threadTitle, // 初期タイトルを設定
                    url = readCgiUrl // read.cgi URL を設定
                ),
                boardInfo = boardInfo
            )
        }
        loadThread(datUrl = datUrl) // datUrl を使ってスレッドを読み込む
    }

    // 書き込み画面を表示
    fun showPostDialog() {
        _uiState.update { it.copy(postDialog = true) }
    }

    // 書き込み画面を閉じる
    fun hidePostDialog() {
        _uiState.update { it.copy(postDialog = false) }
    }

    // 書き込み確認画面を閉じる
    fun hideConfirmationScreen() {
        _uiState.update { it.copy(isConfirmationScreen = false) }
    }

    fun updatePostName(name: String) {
        _uiState.update { it.copy(postFormState = it.postFormState.copy(name = name)) }
    }

    fun updatePostMail(mail: String) {
        _uiState.update { it.copy(postFormState = it.postFormState.copy(mail = mail)) }
    }

    fun updatePostMessage(message: String) {
        _uiState.update { it.copy(postFormState = it.postFormState.copy(message = message)) }
    }

    /**
     * 初回投稿の確認フェーズを呼び出す
     */
    fun loadConfirmation(
        host: String,
        board: String,
        threadKey: String,
        name: String,
        mail: String,
        message: String
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val result =
                postRepository.postTo5chFirstPhase(host, board, threadKey, name, mail, message)
            val confirmationData = result
            _uiState.update { it.copy(postConfirmation = confirmationData) }
            _uiState.update { it.copy(isConfirmationScreen = true) }
            _uiState.update { it.copy(isLoading = false) }
        }
    }

    /**
     * 2回目投稿（書き込み実行）
     * 1回目の確認用リクエストから得た hidden パラメータと Cookie を使用して最終投稿を行う。
     */
    fun postTo5chSecondPhase(
        host: String,
        board: String,
        threadKey: String,
        confirmationData: ConfirmationData
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val result = postRepository.postTo5chSecondPhase(
                host,
                board,
                threadKey,
                confirmationData
            )
            if (result == PostResult.Success) {
                _uiState.update { it.copy(isConfirmationScreen = false) }
            } else {
                // エラー処理
            }
            _uiState.update { it.copy(isLoading = false) }
        }
    }
}
