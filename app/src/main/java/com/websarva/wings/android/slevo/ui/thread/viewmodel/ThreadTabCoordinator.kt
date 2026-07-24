package com.websarva.wings.android.slevo.ui.thread.viewmodel

import com.websarva.wings.android.slevo.data.datasource.local.entity.ThreadReadState
import com.websarva.wings.android.slevo.data.model.ThreadId
import com.websarva.wings.android.slevo.data.repository.TabsRepository
import com.websarva.wings.android.slevo.data.repository.ThreadReadStateRepository
import com.websarva.wings.android.slevo.data.repository.ThreadStateRepository
import com.websarva.wings.android.slevo.ui.tabs.model.ThreadTabInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Persists thread metadata and read state without replacing the open-tab collection.
 * Each update reads and writes only the requested thread's state rows.
 */
class ThreadTabCoordinator(
    private val scope: CoroutineScope,
    private val tabsRepository: TabsRepository,
    private val readStateRepository: ThreadReadStateRepository,
) {

    fun updateThreadTabInfo(threadId: ThreadId, title: String, resCount: Int) {
        scope.launch {
            val current = tabsRepository.getOpenThreadTab(threadId) ?: return@launch
            val candidate = if (current.lastReadResNo == 0) {
                null
            } else if (current.firstNewResNo == null || current.firstNewResNo <= current.lastReadResNo) {
                current.lastReadResNo + 1
            } else {
                current.firstNewResNo
            }
            val newFirst = candidate?.let { if (it > resCount) null else candidate }
            val updated = current.copy(
                title = title,
                resCount = resCount,
                prevResCount = current.resCount,
                firstNewResNo = newFirst,
            )
            tabsRepository.updateThreadState(
                ThreadStateRepository.ThreadStateUpdate(
                    threadId = updated.id,
                    boardId = updated.boardId,
                    boardUrl = updated.boardUrl,
                    boardName = updated.boardName,
                    title = updated.title,
                    latestResCount = updated.resCount,
                )
            )
            readStateRepository.saveReadState(
                threadId,
                ThreadReadState(
                    prevResCount = updated.prevResCount,
                    lastReadResNo = updated.lastReadResNo,
                    firstNewResNo = updated.firstNewResNo,
                )
            )
        }
    }

    /**
     * 指定スレッドタブのスクロール位置だけを更新する。
     * タブ一覧全体の read-map-save 経路を使わず、対象タブの scroll columns のみを更新する。
     */
    fun updateThreadScrollPosition(
        threadId: ThreadId,
        firstVisibleIndex: Int,
        scrollOffset: Int
    ) {
        scope.launch {
            tabsRepository.updateThreadTabScrollPosition(
                threadId = threadId,
                firstVisibleItemIndex = firstVisibleIndex,
                firstVisibleItemScrollOffset = scrollOffset,
            )
        }
    }

    fun updateThreadLastRead(threadId: ThreadId, lastReadResNo: Int) {
        scope.launch {
            val current = tabsRepository.getOpenThreadTab(threadId)
                ?: return@launch
            if (lastReadResNo > current.lastReadResNo) {
                readStateRepository.saveReadState(
                    threadId,
                    ThreadReadState(
                        prevResCount = current.prevResCount,
                        lastReadResNo = lastReadResNo,
                        firstNewResNo = current.firstNewResNo,
                    )
                )
            }
        }
    }
}
