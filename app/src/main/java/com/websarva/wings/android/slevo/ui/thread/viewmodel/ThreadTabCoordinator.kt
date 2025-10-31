package com.websarva.wings.android.slevo.ui.thread.viewmodel

import com.websarva.wings.android.slevo.data.datasource.local.entity.ThreadReadState
import com.websarva.wings.android.slevo.data.model.ThreadId
import com.websarva.wings.android.slevo.data.repository.TabsRepository
import com.websarva.wings.android.slevo.data.repository.ThreadReadStateRepository
import com.websarva.wings.android.slevo.ui.tabs.ThreadTabInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class ThreadTabCoordinator(
    private val scope: CoroutineScope,
    private val tabsRepository: TabsRepository,
    private val readStateRepository: ThreadReadStateRepository,
) {

    fun updateThreadTabInfo(threadId: ThreadId, title: String, resCount: Int) {
        scope.launch {
            val current = tabsRepository.observeOpenThreadTabs().first()
            var target: ThreadTabInfo? = null
            val updated = current.map { tab ->
                if (tab.id == threadId) {
                    val candidate = if (tab.lastReadResNo == 0) {
                        null
                    } else if (tab.firstNewResNo == null || tab.firstNewResNo <= tab.lastReadResNo) {
                        tab.lastReadResNo + 1
                    } else {
                        tab.firstNewResNo
                    }
                    val newFirst = candidate?.let { if (it > resCount) null else candidate }
                    val newTab = tab.copy(
                        title = title,
                        resCount = resCount,
                        prevResCount = tab.resCount,
                        firstNewResNo = newFirst,
                    )
                    target = newTab
                    newTab
                } else {
                    tab
                }
            }
            tabsRepository.saveOpenThreadTabs(updated)
            target?.let {
                readStateRepository.saveReadState(
                    threadId,
                    ThreadReadState(
                        prevResCount = it.prevResCount,
                        lastReadResNo = it.lastReadResNo,
                        firstNewResNo = it.firstNewResNo,
                    )
                )
            }
        }
    }

    fun updateThreadScrollPosition(
        threadId: ThreadId,
        firstVisibleIndex: Int,
        scrollOffset: Int
    ) {
        scope.launch {
            val current = tabsRepository.observeOpenThreadTabs().first()
            val updated = current.map { tab ->
                if (tab.id == threadId) {
                    tab.copy(
                        firstVisibleItemIndex = firstVisibleIndex,
                        firstVisibleItemScrollOffset = scrollOffset
                    )
                } else {
                    tab
                }
            }
            tabsRepository.saveOpenThreadTabs(updated)
        }
    }

    fun updateThreadLastRead(threadId: ThreadId, lastReadResNo: Int) {
        scope.launch {
            val current = tabsRepository.observeOpenThreadTabs().first()
            val tab = current.find { it.id == threadId } ?: return@launch
            if (lastReadResNo > tab.lastReadResNo) {
                readStateRepository.saveReadState(
                    threadId,
                    ThreadReadState(
                        prevResCount = tab.prevResCount,
                        lastReadResNo = lastReadResNo,
                        firstNewResNo = tab.firstNewResNo,
                    )
                )
            }
        }
    }
}
