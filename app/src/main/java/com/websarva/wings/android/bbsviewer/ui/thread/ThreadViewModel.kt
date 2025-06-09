package com.websarva.wings.android.bbsviewer.ui.thread

import android.util.Log
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.websarva.wings.android.bbsviewer.data.datasource.local.entity.BookmarkThreadEntity
import com.websarva.wings.android.bbsviewer.data.model.BoardInfo
import com.websarva.wings.android.bbsviewer.data.repository.ConfirmationData
import com.websarva.wings.android.bbsviewer.data.repository.DatRepository
import com.websarva.wings.android.bbsviewer.data.repository.PostRepository
import com.websarva.wings.android.bbsviewer.data.repository.PostResult
import com.websarva.wings.android.bbsviewer.data.repository.ThreadBookmarkRepository
import com.websarva.wings.android.bbsviewer.ui.util.keyToDatUrl
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ThreadViewModel @AssistedInject constructor(
    private val datRepository: DatRepository,
    private val threadBookmarkRepository: ThreadBookmarkRepository,
    private val postRepository: PostRepository,
    @Assisted val mapKey: String,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ThreadUiState())
    val uiState: StateFlow<ThreadUiState> = _uiState.asStateFlow()

    fun loadThread(datUrl: String) {
        _uiState.update { it.copy(isLoading = true) }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val threadData = datRepository.getThread(datUrl)
                if (threadData != null) {
                    val (posts, title) = threadData
                    _uiState.update {
                        it.copy(
                            posts = posts,
                            isLoading = false,
                            // タイトルが取得できたら更新
                            threadInfo = it.threadInfo.copy(title = title ?: it.threadInfo.title)
                        )
                    }
                } else {
                    // スレッドデータの取得またはパースに失敗した場合の処理
                    // 例: エラーステートを更新する、ログを出すなど
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            // posts は null のままか、空のリストにするなど、仕様に応じて設定
                            // posts = null or emptyList()
                            // errorMessage = "スレッドの読み込みに失敗しました" (UIに表示する場合)
                        )
                    }
                    // 必要であればエラーログを出力
                    Log.e("ThreadViewModel", "Failed to load thread data for URL: $datUrl")
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        // posts = null or emptyList()
                        // errorMessage = "予期せぬエラーが発生しました"
                    )
                }
            }
        }
    }

//    fun parseUrl() {
//        // URLを解析
//        val parsed = parseThreadUrl(enteredUrl)
//        if (parsed != null) {
//            val (board, thread) = parsed
//            val datUrl = createDatUrl(board, thread)
//            Log.i("BBSViewer", datUrl)
//        }
//    }
//
//    /*
//    入力されたURLからホスト名/板名/スレッドIDを抽出
//     */
//    private fun parseThreadUrl(url: String): Pair<String, String>? {
//        val regex = Regex("""https://([^/]+)/test/read.cgi/([^/]+)/(\d+)""")
//        val matchResult = regex.find(url)
//        return matchResult?.let {
//            val hostName = it.groupValues[1] // ホスト名
//            val boardName = it.groupValues[2] // 板の名前
//            val threadId = it.groupValues[3] // スレッドID
//
//            Log.i("BBSViewer", "Host: $hostName, Board: $boardName, ThreadID: $threadId")
//            Pair("$hostName/$boardName", threadId)
//        }
//    }
//
//    /*
//    datファイルのURLに変換
//     */
//    private fun createDatUrl(boardPath: String, threadId: String): String {
//        return "https://$boardPath/dat/$threadId.dat"
//    }

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
                boardInfo = boardInfo,
                isBookmarked = false, // 初期値。Flowで更新される
                currentThreadGroup = null
            )
        }
        loadThread(datUrl = datUrl) // datUrl を使ってスレッドを読み込む
        loadAvailableGroups() // グループ一覧を読み込む

        // ブックマーク状態を監視
        viewModelScope.launch {
            threadBookmarkRepository.getBookmarkWithGroup(threadKey, boardInfo.url)
                .collect { bookmarkWithGroup ->
                    _uiState.update {
                        it.copy(
                            isBookmarked = bookmarkWithGroup != null,
                            currentThreadGroup = bookmarkWithGroup?.group
                        )
                    }
                }
        }
    }

    private fun loadAvailableGroups() {
        viewModelScope.launch {
            threadBookmarkRepository.observeAllGroups().collect { groups ->
                _uiState.update { it.copy(availableThreadGroups = groups) }
            }
        }
    }

    fun handleFavoriteClick() {
        if (_uiState.value.isBookmarked) {
            unbookmarkCurrentThread()
        } else {
            if (_uiState.value.availableThreadGroups.isEmpty()) {
                // グループが一つもなければ、まずグループ作成を促すか、デフォルトグループに登録する仕様も検討可
                // ここでは、グループ選択画面は表示するが、グループがない場合は追加を促すことになる
                _uiState.update {
                    it.copy(
                        showThreadGroupSelector = true,
                        showAddGroupDialog = true
                    )
                } // グループがなければ追加ダイアログも開く
            } else {
                _uiState.update { it.copy(showThreadGroupSelector = true) }
            }
        }
    }

    fun selectGroupAndBookmark(groupId: Long) {
        val currentThreadInfo = _uiState.value.threadInfo
        val currentBoardInfo = _uiState.value.boardInfo
        viewModelScope.launch {
            val bookmark = BookmarkThreadEntity(
                threadKey = currentThreadInfo.key,
                boardUrl = currentBoardInfo.url,
                boardId = currentBoardInfo.boardId,
                groupId = groupId,
                title = currentThreadInfo.title.ifEmpty { "タイトルなし" },
                boardName = currentBoardInfo.name,
                resCount = currentThreadInfo.resCount // ThreadInfoから取得
            )
            threadBookmarkRepository.insertBookmark(bookmark)
            _uiState.update { it.copy(showThreadGroupSelector = false) }
        }
    }

    fun unbookmarkCurrentThread() {
        val currentThreadInfo = _uiState.value.threadInfo
        val currentBoardInfo = _uiState.value.boardInfo
        viewModelScope.launch {
            threadBookmarkRepository.deleteBookmark(currentThreadInfo.key, currentBoardInfo.url)
            _uiState.update { it.copy(showThreadGroupSelector = false) } // 念のためシートも閉じる
        }
    }

    fun dismissThreadGroupSelector() {
        _uiState.update { it.copy(showThreadGroupSelector = false) }
    }

    // グループ追加ダイアログ関連
    fun openAddGroupDialog() {
        _uiState.update { it.copy(showAddGroupDialog = true) }
    }

    fun closeAddGroupDialog() {
        _uiState.update {
            it.copy(
                showAddGroupDialog = false,
                enteredNewGroupName = "",
                selectedColorForNewGroup = "#FF0000"
            )
        } // 初期化
    }

    fun setEnteredGroupName(name: String) {
        _uiState.update { it.copy(enteredNewGroupName = name) }
    }

    fun setSelectedColorCode(color: String) {
        _uiState.update { it.copy(selectedColorForNewGroup = color) }
    }

    fun addNewGroup() {
        viewModelScope.launch {
            val name = _uiState.value.enteredNewGroupName.trim()
            val color = _uiState.value.selectedColorForNewGroup
            if (name.isNotBlank() && color != null) {
                threadBookmarkRepository.addGroupAtEnd(name, color)
                closeAddGroupDialog() // ダイアログを閉じて入力値をクリア
                // availableThreadGroups は observeGroups() により自動更新される
            }
        }
    }

    fun reloadThread() {
        val datUrl = _uiState.value.threadInfo.datUrl
        if (datUrl.isNotBlank()) {
            loadThread(datUrl)
        }
    }

    // タブ一覧ボトムシートを開く
    fun openTabListSheet() {
        _uiState.update { it.copy(showTabListSheet = true) }
    }

    // タブ一覧ボトムシートを閉じる
    fun closeTabListSheet() {
        _uiState.update { it.copy(showTabListSheet = false) }
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

    /**
     * このViewModelが不要になったときに、所有者であるTabsViewModelから呼び出される公開メソッド。
     * 内部で自身のライフサイクル終了処理を呼び出す。
     */
    fun release() {
        // このクラスの内部からなので、protectedなonCleared()を呼び出せる
        super.onCleared()
    }

}

@AssistedFactory
interface ThreadViewModelFactory {
    fun create(mapKey: String): ThreadViewModel
}
