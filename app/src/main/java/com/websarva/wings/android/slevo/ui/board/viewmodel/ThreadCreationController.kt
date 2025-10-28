package com.websarva.wings.android.slevo.ui.board.viewmodel

import com.websarva.wings.android.slevo.data.datasource.local.entity.history.PostIdentityType
import com.websarva.wings.android.slevo.data.repository.ConfirmationData
import com.websarva.wings.android.slevo.data.repository.PostHistoryRepository
import com.websarva.wings.android.slevo.data.repository.PostResult
import com.websarva.wings.android.slevo.data.repository.ThreadCreateRepository
import com.websarva.wings.android.slevo.ui.board.state.BoardUiState
import com.websarva.wings.android.slevo.ui.board.state.CreateThreadFormState
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class ThreadCreationController @AssistedInject constructor(
    private val threadCreateRepository: ThreadCreateRepository,
    private val postHistoryRepository: PostHistoryRepository,
    @Assisted private val scope: CoroutineScope,
    @Assisted private val stateProvider: () -> BoardUiState,
    @Assisted private val updateState: ((BoardUiState) -> BoardUiState) -> Unit,
    @Assisted private val identityHistoryDelegate: IdentityHistoryDelegate,
    @Assisted private val refreshBoard: () -> Unit,
) {

    fun showCreateDialog() {
        updateState { it.copy(createDialog = true) }
    }

    fun hideCreateDialog() {
        updateState { it.copy(createDialog = false) }
    }

    fun updateCreateName(name: String) {
        updateState { it.copy(createFormState = it.createFormState.copy(name = name)) }
        refreshCreateIdentityHistory(PostIdentityType.NAME)
    }

    fun updateCreateMail(mail: String) {
        updateState { it.copy(createFormState = it.createFormState.copy(mail = mail)) }
        refreshCreateIdentityHistory(PostIdentityType.EMAIL)
    }

    fun updateCreateTitle(title: String) {
        updateState { it.copy(createFormState = it.createFormState.copy(title = title)) }
    }

    fun updateCreateMessage(message: String) {
        updateState { it.copy(createFormState = it.createFormState.copy(message = message)) }
    }

    fun selectCreateNameHistory(name: String) {
        updateState { it.copy(createFormState = it.createFormState.copy(name = name)) }
        refreshCreateIdentityHistory(PostIdentityType.NAME)
    }

    fun selectCreateMailHistory(mail: String) {
        updateState { it.copy(createFormState = it.createFormState.copy(mail = mail)) }
        refreshCreateIdentityHistory(PostIdentityType.EMAIL)
    }

    fun deleteCreateNameHistory(name: String) {
        deleteCreateIdentity(PostIdentityType.NAME, name)
    }

    fun deleteCreateMailHistory(mail: String) {
        deleteCreateIdentity(PostIdentityType.EMAIL, mail)
    }

    fun prepareCreateIdentityHistory(boardId: Long) {
        identityHistoryDelegate.onPrepareIdentityHistory(
            key = CREATE_IDENTITY_HISTORY_KEY,
            boardId = boardId,
            repository = postHistoryRepository,
            onLastIdentity = { name, mail ->
                updateState { state ->
                    val form = state.createFormState
                    if (form.name.isEmpty() && form.mail.isEmpty()) {
                        state.copy(
                            createFormState = form.copy(
                                name = name,
                                mail = mail,
                            ),
                        )
                    } else {
                        state
                    }
                }
            },
            onNameSuggestions = { suggestions ->
                updateState { it.copy(createNameHistory = suggestions) }
            },
            onMailSuggestions = { suggestions ->
                updateState { it.copy(createMailHistory = suggestions) }
            },
            nameQueryProvider = { stateProvider().createFormState.name },
            mailQueryProvider = { stateProvider().createFormState.mail },
        )
    }

    fun createThreadFirstPhase(
        host: String,
        board: String,
        title: String,
        name: String,
        mail: String,
        message: String,
    ) {
        scope.launch {
            updateState { it.copy(isPosting = true, createDialog = false) }
            val result = threadCreateRepository.createThreadFirstPhase(
                host,
                board,
                title,
                name,
                mail,
                message,
            )
            handlePostResult(result)
        }
    }

    fun createThreadSecondPhase(
        host: String,
        board: String,
        confirmationData: ConfirmationData,
    ) {
        scope.launch {
            updateState { it.copy(isPosting = true, isConfirmationScreen = false) }
            val result =
                threadCreateRepository.createThreadSecondPhase(host, board, confirmationData)
            handlePostResult(result)
        }
    }

    private fun handlePostResult(result: PostResult) {
        updateState { it.copy(isPosting = false) }
        when (result) {
            is PostResult.Success -> {
                val formState = stateProvider().createFormState
                val boardId = stateProvider().boardInfo.boardId
                updateState {
                    it.copy(
                        postResultMessage = "書き込みに成功しました。",
                        createFormState = CreateThreadFormState(
                            name = formState.name,
                            mail = formState.mail,
                        ),
                    )
                }
                if (boardId != 0L) {
                    scope.launch {
                        postHistoryRepository.recordIdentity(
                            boardId = boardId,
                            name = formState.name,
                            email = formState.mail,
                        )
                    }
                }
                refreshBoard()
            }

            is PostResult.Confirm -> {
                updateState {
                    it.copy(
                        postConfirmation = result.confirmationData,
                        isConfirmationScreen = true,
                    )
                }
            }

            is PostResult.Error -> {
                updateState {
                    it.copy(showErrorWebView = true, errorHtmlContent = result.html)
                }
            }
        }
    }

    private fun refreshCreateIdentityHistory(type: PostIdentityType) {
        identityHistoryDelegate.onRefreshIdentityHistorySuggestions(
            CREATE_IDENTITY_HISTORY_KEY,
            type,
        )
    }

    private fun deleteCreateIdentity(type: PostIdentityType, value: String) {
        identityHistoryDelegate.onDeleteIdentityHistory(
            CREATE_IDENTITY_HISTORY_KEY,
            postHistoryRepository,
            type,
            value,
        )
    }

    interface IdentityHistoryDelegate {
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

        fun onRefreshIdentityHistorySuggestions(
            key: String,
            type: PostIdentityType?,
        )

        fun onDeleteIdentityHistory(
            key: String,
            repository: PostHistoryRepository,
            type: PostIdentityType,
            value: String,
        )
    }

    @AssistedFactory
    interface Factory {
        fun create(
            scope: CoroutineScope,
            stateProvider: () -> BoardUiState,
            updateState: ((BoardUiState) -> BoardUiState) -> Unit,
            identityHistoryDelegate: IdentityHistoryDelegate,
            refreshBoard: () -> Unit,
        ): ThreadCreationController
    }

    private companion object {
        private const val CREATE_IDENTITY_HISTORY_KEY = "board_create_identity"
    }
}
