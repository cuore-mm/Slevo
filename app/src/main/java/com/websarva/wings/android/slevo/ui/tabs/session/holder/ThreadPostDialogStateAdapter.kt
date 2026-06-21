package com.websarva.wings.android.slevo.ui.tabs.session.holder

import com.websarva.wings.android.slevo.ui.common.postdialog.PostDialogState
import com.websarva.wings.android.slevo.ui.common.postdialog.PostDialogStateAdapter

/**
 * Thread タブの SessionState 上にある PostDialogState を読み書きするアダプタ。
 *
 * 共通 PostDialogController の更新結果を、対象タブの [ThreadSessionState.postDialogState] へ反映する。
 */
internal class ThreadPostDialogStateAdapter(
    private val stateReader: () -> PostDialogState,
    private val stateUpdater: ((PostDialogState) -> PostDialogState) -> Unit,
) : PostDialogStateAdapter {

    override fun readState(): PostDialogState = stateReader()

    override fun updateState(transform: (PostDialogState) -> PostDialogState) = stateUpdater(transform)
}
