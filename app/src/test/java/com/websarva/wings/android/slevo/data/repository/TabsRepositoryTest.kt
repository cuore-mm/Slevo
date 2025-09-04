package com.websarva.wings.android.slevo.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.websarva.wings.android.slevo.data.datasource.local.AppDatabase
import com.websarva.wings.android.slevo.data.datasource.local.TabsLocalDataSource
import com.websarva.wings.android.slevo.ui.tabs.ThreadTabInfo
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TabsRepositoryTest {
    private lateinit var db: AppDatabase
    private lateinit var repository: TabsRepository

    private val localDataSource = object : TabsLocalDataSource {
        private val state = MutableStateFlow(0)
        override fun observeLastSelectedPage(): Flow<Int> = state
        override suspend fun setLastSelectedPage(page: Int) { state.value = page }
    }

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = TabsRepository(db.openBoardTabDao(), db.openThreadTabDao(), localDataSource, db)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun concurrentUpdatesAreMerged() = runTest {
        val tabA = ThreadTabInfo(
            key = "a",
            title = "A",
            boardName = "Board",
            boardUrl = "url",
            boardId = 1L,
            resCount = 1
        )
        val tabB = ThreadTabInfo(
            key = "b",
            title = "B",
            boardName = "Board",
            boardUrl = "url",
            boardId = 1L,
            resCount = 2
        )
        repository.saveOpenThreadTabs(listOf(tabA, tabB))
        val updateA = tabA.copy(resCount = 3)
        val updateB = tabB.copy(resCount = 4)
        coroutineScope {
            launch { repository.saveOpenThreadTabs(listOf(updateA, tabB)) }
            launch { repository.saveOpenThreadTabs(listOf(tabA, updateB)) }
        }
        val result = db.openThreadTabDao().getAll()
        val a = result.find { it.threadKey == "a" }!!
        val b = result.find { it.threadKey == "b" }!!
        assertEquals(3, a.resCount)
        assertEquals(4, b.resCount)
    }
}
