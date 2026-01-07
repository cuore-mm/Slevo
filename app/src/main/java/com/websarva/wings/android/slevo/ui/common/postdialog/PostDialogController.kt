package com.websarva.wings.android.slevo.ui.common.postdialog

import com.websarva.wings.android.slevo.data.datasource.local.entity.history.PostIdentityType
import com.websarva.wings.android.slevo.data.repository.ConfirmationData
import com.websarva.wings.android.slevo.data.repository.PostHistoryRepository
import com.websarva.wings.android.slevo.data.repository.PostResult
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/**
 * PostDialogの状態更新と投稿処理を共通化するコントローラ。
 *
 * 状態はアダプタ経由で反映し、投稿実行と履歴管理を差し替え可能な構成で扱う。
 */
class PostDialogController @AssistedInject constructor(
    private val postHistoryRepository: PostHistoryRepository,
    @Assisted private val scope: CoroutineScope,
    @Assisted private val stateAdapter: PostDialogStateAdapter,
    @Assisted private val identityHistoryKey: String,
    @Assisted private val executor: PostDialogExecutor,
    @Assisted private val boardIdProvider: () -> Long,
    @Assisted private val onPostSuccess: (PostDialogSuccess) -> Unit,
) {
    private val identityHistoryObservers = mutableMapOf<String, IdentityHistoryObserver>()

    /**
     * PostDialogを表示する。
     */
    fun showDialog() {
        updateState { it.copy(isDialogVisible = true) }
    }

    /**
     * 返信番号を挿入してPostDialogを表示する。
     */
    fun showReplyDialog(resNum: Int) {
        updateState { current ->
            val message = current.formState.message
            // 既存文末が改行で終わっていない場合のみ区切りを挿入する。
            val separator = if (message.isNotEmpty() && !message.endsWith("\n")) "\n" else ""
            current.copy(
                isDialogVisible = true,
                formState = current.formState.copy(
                    message = message + separator + ">>${resNum}\n"
                ),
            )
        }
    }

    /**
     * PostDialogを閉じる。
     */
    fun hideDialog() {
        updateState { it.copy(isDialogVisible = false) }
    }

    /**
     * 確認画面を閉じる。
     */
    fun hideConfirmationScreen() {
        updateState { it.copy(isConfirmationScreen = false) }
    }

    /**
     * エラーページを閉じて内容をクリアする。
     */
    fun hideErrorWebView() {
        updateState { it.copy(showErrorWebView = false, errorHtmlContent = "") }
    }

    /**
     * 投稿結果メッセージをクリアする。
     */
    fun clearPostResultMessage() {
        updateState { it.copy(postResultMessage = null) }
    }

    /**
     * 名前入力を更新し、履歴候補を再計算する。
     */
    fun updateName(name: String) {
        updateState { it.copy(formState = it.formState.copy(name = name)) }
        refreshIdentityHistory(PostIdentityType.NAME)
    }

    /**
     * メール入力を更新し、履歴候補を再計算する。
     */
    fun updateMail(mail: String) {
        updateState { it.copy(formState = it.formState.copy(mail = mail)) }
        refreshIdentityHistory(PostIdentityType.EMAIL)
    }

    /**
     * タイトル入力を更新する。
     */
    fun updateTitle(title: String) {
        updateState { it.copy(formState = it.formState.copy(title = title)) }
    }

    /**
     * 本文入力を更新する。
     */
    fun updateMessage(message: String) {
        updateState { it.copy(formState = it.formState.copy(message = message)) }
    }

    /**
     * 画像URLを本文末尾に追記する。
     */
    fun appendImageUrl(url: String) {
        updateState { current ->
            current.copy(
                formState = current.formState.copy(
                    message = current.formState.message + "\n" + url
                ),
            )
        }
    }

    /**
     * 名前履歴を選択してフォームへ反映する。
     */
    fun selectNameHistory(name: String) {
        updateState { it.copy(formState = it.formState.copy(name = name)) }
        refreshIdentityHistory(PostIdentityType.NAME)
    }

    /**
     * メール履歴を選択してフォームへ反映する。
     */
    fun selectMailHistory(mail: String) {
        updateState { it.copy(formState = it.formState.copy(mail = mail)) }
        refreshIdentityHistory(PostIdentityType.EMAIL)
    }

    /**
     * 名前履歴を削除する。
     */
    fun deleteNameHistory(name: String) {
        deleteIdentityHistory(PostIdentityType.NAME, name)
    }

    /**
     * メール履歴を削除する。
     */
    fun deleteMailHistory(mail: String) {
        deleteIdentityHistory(PostIdentityType.EMAIL, mail)
    }

    /**
     * 投稿履歴候補の監視を準備する。
     */
    fun prepareIdentityHistory(boardId: Long) {
        // --- Observer setup ---
        val observer = identityHistoryObservers.getOrPut(identityHistoryKey) {
            IdentityHistoryObserver(
                onLastIdentity = null,
                onNameSuggestions = { },
                onMailSuggestions = { },
                nameQueryProvider = { "" },
                mailQueryProvider = { "" },
            )
        }.apply {
            onLastIdentity = { name, mail ->
                updateState { current ->
                    val form = current.formState
                    if (form.name.isEmpty() && form.mail.isEmpty()) {
                        // 既存入力がある場合は上書きしない。
                        current.copy(
                            formState = form.copy(
                                name = name,
                                mail = mail,
                            ),
                        )
                    } else {
                        current
                    }
                }
            }
            onNameSuggestions = { suggestions ->
                updateState { it.copy(nameHistory = suggestions) }
            }
            onMailSuggestions = { suggestions ->
                updateState { it.copy(mailHistory = suggestions) }
            }
            nameQueryProvider = { stateAdapter.readState().formState.name }
            mailQueryProvider = { stateAdapter.readState().formState.mail }
        }

        // --- Board change handling ---
        if (observer.boardId == boardId) {
            // ボードが同一の場合は候補のみ再計算する。
            refreshIdentityHistorySuggestions(identityHistoryKey)
            return
        }

        observer.boardId = boardId
        observer.nameJob?.cancel()
        observer.mailJob?.cancel()
        observer.latestNames = emptyList()
        observer.latestMails = emptyList()
        refreshIdentityHistorySuggestions(identityHistoryKey)

        // --- Guard ---
        if (boardId == 0L) {
            // 未登録のboardIdでは履歴監視を開始しない。
            return
        }

        // --- Last identity ---
        scope.launch {
            postHistoryRepository.getLastIdentity(boardId)?.let { identity ->
                observer.onLastIdentity?.invoke(identity.name, identity.email)
            }
        }

        // --- Observation ---
        observer.nameJob = scope.launch {
            postHistoryRepository.observeIdentityHistories(boardId, PostIdentityType.NAME).collect { histories ->
                observer.latestNames = histories
                refreshIdentityHistorySuggestions(identityHistoryKey, PostIdentityType.NAME)
            }
        }
        observer.mailJob = scope.launch {
            postHistoryRepository.observeIdentityHistories(boardId, PostIdentityType.EMAIL).collect { histories ->
                observer.latestMails = histories
                refreshIdentityHistorySuggestions(identityHistoryKey, PostIdentityType.EMAIL)
            }
        }
    }

    /**
     * 投稿の第一段階（確認画面取得）を実行する。
     */
    fun postFirstPhase(
        host: String,
        board: String,
        threadKey: String?,
    ) {
        val formState = stateAdapter.readState().formState
        val request = PostDialogFirstPhaseRequest(
            host = host,
            board = board,
            threadKey = threadKey,
            title = formState.title,
            formState = formState,
        )
        scope.launch {
            updateState { it.copy(isPosting = true, isDialogVisible = false) }
            val result = executor.postFirstPhase(request)
            handlePostResult(result, formState)
        }
    }

    /**
     * 投稿の第二段階（書き込み実行）を実行する。
     */
    fun postSecondPhase(
        host: String,
        board: String,
        threadKey: String?,
        confirmationData: ConfirmationData,
    ) {
        val formState = stateAdapter.readState().formState
        val request = PostDialogSecondPhaseRequest(
            host = host,
            board = board,
            threadKey = threadKey,
            confirmationData = confirmationData,
        )
        scope.launch {
            updateState { it.copy(isPosting = true, isConfirmationScreen = false) }
            val result = executor.postSecondPhase(request)
            handlePostResult(result, formState)
        }
    }

    /**
     * 投稿処理の結果を受け取り、UI状態と後処理を更新する。
     */
    private fun handlePostResult(result: PostResult, submittedForm: PostFormState) {
        // --- Posting flag ---
        updateState { it.copy(isPosting = false) }

        when (result) {
            is PostResult.Success -> {
                // --- Success ---
                val updatedForm = submittedForm.copy(title = "", message = "")
                updateState {
                    it.copy(
                        postResultMessage = "書き込みに成功しました。",
                        formState = updatedForm,
                    )
                }
                recordIdentityIfNeeded(submittedForm)
                onPostSuccess(
                    PostDialogSuccess(
                        resNum = result.resNum,
                        message = submittedForm.message,
                        name = submittedForm.name,
                        mail = submittedForm.mail,
                    )
                )
            }

            is PostResult.Confirm -> {
                // --- Confirm ---
                updateState {
                    it.copy(
                        postConfirmation = result.confirmationData,
                        isConfirmationScreen = true,
                    )
                }
            }

            is PostResult.Error -> {
                // --- Error ---
                updateState {
                    it.copy(showErrorWebView = true, errorHtmlContent = result.html)
                }
            }
        }
    }

    /**
     * 投稿履歴候補の再計算を委譲する。
     */
    private fun refreshIdentityHistory(type: PostIdentityType) {
        refreshIdentityHistorySuggestions(identityHistoryKey, type)
    }

    /**
     * 投稿履歴の削除を委譲する。
     */
    private fun deleteIdentityHistory(type: PostIdentityType, value: String) {
        deleteIdentityHistory(identityHistoryKey, type, value)
    }

    /**
     * 投稿成功時に履歴を記録する。
     */
    private fun recordIdentityIfNeeded(formState: PostFormState) {
        val boardId = boardIdProvider()
        if (boardId == 0L) {
            // 未登録のboardIdでは履歴を保存しない。
            return
        }
        scope.launch {
            postHistoryRepository.recordIdentity(
                boardId = boardId,
                name = formState.name,
                email = formState.mail,
            )
        }
    }

    private fun updateState(transform: (PostDialogState) -> PostDialogState) {
        stateAdapter.updateState(transform)
    }

    /**
     * PostDialogControllerを生成するためのファクトリ。
     */
    @AssistedFactory
    interface Factory {
        /**
         * PostDialogControllerを生成する。
         */
        fun create(
            scope: CoroutineScope,
            stateAdapter: PostDialogStateAdapter,
            identityHistoryKey: String,
            executor: PostDialogExecutor,
            boardIdProvider: () -> Long,
            onPostSuccess: (PostDialogSuccess) -> Unit,
        ): PostDialogController
    }

    /**
     * 投稿履歴候補を検索条件で絞り込む。
     */
    private fun filterIdentityHistories(source: List<String>, query: String): List<String> {
        val normalized = query.trim()
        return if (normalized.isEmpty()) {
            source
        } else {
            source.filter { it.contains(normalized, ignoreCase = true) }
        }
    }

    /**
     * 投稿履歴候補の再計算を行う。
     */
    private fun refreshIdentityHistorySuggestions(
        key: String,
        type: PostIdentityType? = null,
    ) {
        val observer = identityHistoryObservers[key] ?: return
        if (type == null || type == PostIdentityType.NAME) {
            val suggestions = filterIdentityHistories(observer.latestNames, observer.nameQueryProvider())
            observer.onNameSuggestions.invoke(suggestions)
        }
        if (type == null || type == PostIdentityType.EMAIL) {
            val suggestions = filterIdentityHistories(observer.latestMails, observer.mailQueryProvider())
            observer.onMailSuggestions.invoke(suggestions)
        }
    }

    /**
     * 投稿履歴の削除処理を行う。
     */
    private fun deleteIdentityHistory(
        key: String,
        type: PostIdentityType,
        value: String,
    ) {
        val observer = identityHistoryObservers[key] ?: return
        val normalized = value.trim()
        if (normalized.isEmpty()) {
            // 空入力は削除対象にしない。
            return
        }
        when (type) {
            PostIdentityType.NAME -> {
                observer.latestNames = observer.latestNames.filterNot { it == normalized }
            }

            PostIdentityType.EMAIL -> {
                observer.latestMails = observer.latestMails.filterNot { it == normalized }
            }
        }
        refreshIdentityHistorySuggestions(key, type)
        val boardId = observer.boardId
        if (boardId == 0L) {
            // 未登録のboardIdでは永続削除を行わない。
            return
        }
        scope.launch {
            postHistoryRepository.deleteIdentity(boardId, type, normalized)
        }
    }

    /**
     * 投稿履歴の監視状態を保持する。
     */
    private class IdentityHistoryObserver(
        var onLastIdentity: ((String, String) -> Unit)?,
        var onNameSuggestions: (List<String>) -> Unit,
        var onMailSuggestions: (List<String>) -> Unit,
        var nameQueryProvider: () -> String,
        var mailQueryProvider: () -> String,
    ) {
        var boardId: Long = -1L
        var nameJob: Job? = null
        var mailJob: Job? = null
        var latestNames: List<String> = emptyList()
        var latestMails: List<String> = emptyList()
    }
}
