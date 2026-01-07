package com.websarva.wings.android.slevo.ui.common.postdialog

import com.websarva.wings.android.slevo.data.datasource.local.entity.history.PostIdentityType
import com.websarva.wings.android.slevo.data.repository.ConfirmationData
import com.websarva.wings.android.slevo.data.repository.PostHistoryRepository
import com.websarva.wings.android.slevo.data.repository.PostResult
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * PostDialogの状態更新と投稿処理を共通化するコントローラ。
 *
 * 状態はアダプタ経由で反映し、投稿実行は差し替えインターフェースに委譲する。
 */
class PostDialogController @AssistedInject constructor(
    private val postHistoryRepository: PostHistoryRepository,
    @Assisted private val scope: CoroutineScope,
    @Assisted private val stateAdapter: PostDialogStateAdapter,
    @Assisted private val identityHistoryDelegate: IdentityHistoryDelegate,
    @Assisted private val identityHistoryKey: String,
    @Assisted private val executor: PostDialogExecutor,
    @Assisted private val boardIdProvider: () -> Long,
    @Assisted private val onPostSuccess: (PostDialogSuccess) -> Unit,
) {

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
        identityHistoryDelegate.onPrepareIdentityHistory(
            key = identityHistoryKey,
            boardId = boardId,
            repository = postHistoryRepository,
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
            },
            onNameSuggestions = { suggestions ->
                updateState { it.copy(nameHistory = suggestions) }
            },
            onMailSuggestions = { suggestions ->
                updateState { it.copy(mailHistory = suggestions) }
            },
            nameQueryProvider = { stateAdapter.readState().formState.name },
            mailQueryProvider = { stateAdapter.readState().formState.mail },
        )
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
        identityHistoryDelegate.onRefreshIdentityHistorySuggestions(identityHistoryKey, type)
    }

    /**
     * 投稿履歴の削除を委譲する。
     */
    private fun deleteIdentityHistory(type: PostIdentityType, value: String) {
        identityHistoryDelegate.onDeleteIdentityHistory(
            identityHistoryKey,
            postHistoryRepository,
            type,
            value,
        )
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
     * PostDialogControllerが履歴管理処理を委譲するためのインターフェース。
     */
    interface IdentityHistoryDelegate {
        /**
         * 履歴監視の準備処理を委譲する。
         */
        fun onPrepareIdentityHistory(
            key: String,
            boardId: Long,
            repository: PostHistoryRepository,
            onLastIdentity: ((String, String) -> Unit)?,
            onNameSuggestions: (List<String>) -> Unit,
            onMailSuggestions: (List<String>) -> Unit,
            nameQueryProvider: () -> String,
            mailQueryProvider: () -> String,
        )

        /**
         * 履歴候補の更新処理を委譲する。
         */
        fun onRefreshIdentityHistorySuggestions(
            key: String,
            type: PostIdentityType?,
        )

        /**
         * 履歴削除処理を委譲する。
         */
        fun onDeleteIdentityHistory(
            key: String,
            repository: PostHistoryRepository,
            type: PostIdentityType,
            value: String,
        )
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
            identityHistoryDelegate: IdentityHistoryDelegate,
            identityHistoryKey: String,
            executor: PostDialogExecutor,
            boardIdProvider: () -> Long,
            onPostSuccess: (PostDialogSuccess) -> Unit,
        ): PostDialogController
    }
}
