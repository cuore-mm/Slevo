package com.websarva.wings.android.slevo.data.repository

import com.websarva.wings.android.slevo.data.database.DatabaseWriteGate
import com.websarva.wings.android.slevo.data.model.BbsMenuContent
import com.websarva.wings.android.slevo.data.model.Board
import com.websarva.wings.android.slevo.data.repository.fake.BbsRepositoryFakes
import com.websarva.wings.android.slevo.data.repository.fake.FakeAppLogger
import com.websarva.wings.android.slevo.data.repository.fake.FakeBbsMenuDataSource
import com.websarva.wings.android.slevo.testutil.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test

/**
 * `BbsServiceRepository` の DAO 直結移行後の挙動を fake DAO で検証する。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BbsServiceRepositoryTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private fun newRepo(
        fakes: BbsRepositoryFakes = BbsRepositoryFakes.create(),
        remote: FakeBbsMenuDataSource = FakeBbsMenuDataSource(),
        logger: FakeAppLogger = FakeAppLogger(),
    ): Triple<BbsServiceRepository, BbsRepositoryFakes, FakeAppLogger> {
        val repo = BbsServiceRepository(
            serviceDao = fakes.serviceDao,
            categoryDao = fakes.categoryDao,
            boardDao = fakes.boardDao,
            crossRefDao = fakes.crossRefDao,
            gate = DatabaseWriteGate(),
            remote = remote,
            logger = logger,
        )
        return Triple(repo, fakes, logger)
    }

    private fun sampleMenu(): List<BbsMenuContent> = listOf(
        BbsMenuContent(
            categoryName = "ニュース",
            boards = listOf(
                Board(name = "板A", url = "https://example.test/news/a"),
                Board(name = "板B", url = "https://example.test/news/b"),
            ),
        ),
        BbsMenuContent(
            categoryName = "スポーツ",
            boards = listOf(
                Board(name = "板C", url = "https://example.test/sports/c"),
            ),
        ),
        // 空カテゴリは登録対象外
        BbsMenuContent(
            categoryName = "空カテゴリ",
            boards = emptyList(),
        ),
    )

    @Test
    fun addOrUpdateService_insertsNewService() = runTest {
        // --- Arrange ---
        val (repo, fakes, logger) = newRepo(
            remote = FakeBbsMenuDataSource(
                menuByUrl = mapOf("https://menu.example.test/bbsmenu.html" to sampleMenu()),
            ),
        )

        // --- Act ---
        repo.addOrUpdateService("https://menu.example.test/bbsmenu.html")

        // --- Assert ---
        assertTrue("logger errors: ${logger.errorMessages}", logger.errorMessages.isEmpty())
        val services = fakes.serviceDao.snapshot()
        assertEquals(1, services.size)
        assertEquals("example.test", services[0].domain)
        assertEquals("example.test", services[0].displayName)
        assertEquals("https://menu.example.test/bbsmenu.html", services[0].menuUrl)

        // 空でないカテゴリ 2 件が登録されている
        val categories = fakes.categoryDao.snapshot()
        assertEquals(2, categories.size)
        assertEquals(setOf("ニュース", "スポーツ"), categories.map { it.name }.toSet())

        // 板は合計 3 件
        val boards = fakes.boardDao.snapshot()
        assertEquals(3, boards.size)

        // cross-ref は 板×カテゴリ ぶん（板C は 1、ニュース系 2 枚で 2 登録）
        val crossRefs = fakes.crossRefDao.snapshot()
        assertEquals(3, crossRefs.size)
    }

    @Test
    fun addOrUpdateService_updatesServiceWhenDisplayNameOrMenuUrlChanged() = runTest {
        // --- Arrange ---
        val (repo, fakes, logger) = newRepo(
            remote = FakeBbsMenuDataSource(
                menuByUrl = mapOf("https://menu.example.test/bbsmenu.html" to sampleMenu()),
            ),
        )
        repo.addOrUpdateService("https://menu.example.test/bbsmenu.html")
        val firstServiceId = fakes.serviceDao.snapshot().single().serviceId
        // 同じ domain だが menuUrl を変えて再実行
        val updatedRemote = FakeBbsMenuDataSource(
            menuByUrl = mapOf("https://menu.example.test/bbsmenu.html?v=2" to sampleMenu()),
        )
        val repo2 = BbsServiceRepository(
            serviceDao = fakes.serviceDao,
            categoryDao = fakes.categoryDao,
            boardDao = fakes.boardDao,
            crossRefDao = fakes.crossRefDao,
            gate = DatabaseWriteGate(),
            remote = updatedRemote,
            logger = FakeAppLogger(),
        )

        // --- Act ---
        repo2.addOrUpdateService("https://menu.example.test/bbsmenu.html?v=2")

        // --- Assert ---
        assertTrue("logger errors: ${logger.errorMessages}", logger.errorMessages.isEmpty())
        val services = fakes.serviceDao.snapshot()
        assertEquals(1, services.size)
        assertEquals(firstServiceId, services[0].serviceId)
        assertEquals("https://menu.example.test/bbsmenu.html?v=2", services[0].menuUrl)
    }

    @Test
    fun addOrUpdateService_doesNotUpdateWhenNothingChanged() = runTest {
        // --- Arrange ---
        val (repo, fakes, logger) = newRepo(
            remote = FakeBbsMenuDataSource(
                menuByUrl = mapOf("https://menu.example.test/bbsmenu.html" to sampleMenu()),
            ),
        )
        repo.addOrUpdateService("https://menu.example.test/bbsmenu.html")
        val firstService = fakes.serviceDao.snapshot().single()

        // 直前の呼び出しで serviceDao.updateServiceMeta が呼ばれた回数を記録する仕組みはないため、
        // 2 回目の呼び出し後に serviceId・domain・displayName・menuUrl が変化しないことを確認する
        // （=updateServiceMeta 相当の処理で副作用がないこと）ことで update 不要の分岐を検証する。

        // --- Act ---
        repo.addOrUpdateService("https://menu.example.test/bbsmenu.html")

        // --- Assert ---
        assertTrue("logger errors: ${logger.errorMessages}", logger.errorMessages.isEmpty())
        val services = fakes.serviceDao.snapshot()
        assertEquals(1, services.size)
        val after = services[0]
        assertEquals(firstService.serviceId, after.serviceId)
        assertEquals(firstService.domain, after.domain)
        assertEquals(firstService.displayName, after.displayName)
        assertEquals(firstService.menuUrl, after.menuUrl)
    }

    @Test
    fun addOrUpdateService_reRegistersCategoriesAndBoards() = runTest {
        // --- Arrange ---
        val (repo, fakes, logger) = newRepo(
            remote = FakeBbsMenuDataSource(
                menuByUrl = mapOf("https://menu.example.test/bbsmenu.html" to sampleMenu()),
            ),
        )
        repo.addOrUpdateService("https://menu.example.test/bbsmenu.html")
        val firstCategoryCount = fakes.categoryDao.snapshot().size
        val firstBoardCount = fakes.boardDao.snapshot().size
        assertEquals(2, firstCategoryCount)
        assertEquals(3, firstBoardCount)

        // 2 回目は menu 内容から 1 カテゴリ・1 板に減らす
        val smallerMenu = listOf(
            BbsMenuContent(
                categoryName = "ニュース",
                boards = listOf(Board(name = "板A", url = "https://example.test/news/a")),
            ),
        )
        val smallerRemote = FakeBbsMenuDataSource(
            menuByUrl = mapOf("https://menu.example.test/bbsmenu.html" to smallerMenu),
        )
        val repo2 = BbsServiceRepository(
            serviceDao = fakes.serviceDao,
            categoryDao = fakes.categoryDao,
            boardDao = fakes.boardDao,
            crossRefDao = fakes.crossRefDao,
            gate = DatabaseWriteGate(),
            remote = smallerRemote,
            logger = FakeAppLogger(),
        )

        // --- Act ---
        repo2.addOrUpdateService("https://menu.example.test/bbsmenu.html")

        // --- Assert ---
        assertTrue("logger errors: ${logger.errorMessages}", logger.errorMessages.isEmpty())
        val categories = fakes.categoryDao.snapshot()
        assertEquals(1, categories.size)
        assertEquals("ニュース", categories.single().name)
        val boards = fakes.boardDao.snapshot()
        // clear 後に smallerMenu の板A だけが再登録されるので 1 件
        assertEquals(1, boards.size)
        assertEquals("https://example.test/news/a", boards.single().url)
    }

    @Test
    fun removeService_deletesServiceById() = runTest {
        // --- Arrange ---
        val (repo, fakes, _) = newRepo(
            remote = FakeBbsMenuDataSource(
                menuByUrl = mapOf("https://menu.example.test/bbsmenu.html" to sampleMenu()),
            ),
        )
        repo.addOrUpdateService("https://menu.example.test/bbsmenu.html")
        val serviceId = fakes.serviceDao.snapshot().single().serviceId

        // --- Act ---
        repo.removeService(serviceId)

        // --- Assert ---
        assertTrue(fakes.serviceDao.snapshot().isEmpty())
    }

    @Test
    fun getBoardsForCategory_returnsBoardsLinkedToCategory() = runTest {
        // --- Arrange ---
        val (repo, fakes, _) = newRepo(
            remote = FakeBbsMenuDataSource(
                menuByUrl = mapOf("https://menu.example.test/bbsmenu.html" to sampleMenu()),
            ),
        )
        repo.addOrUpdateService("https://menu.example.test/bbsmenu.html")
        val newsCategory = fakes.categoryDao.snapshot().first { it.name == "ニュース" }

        // --- Act ---
        val boards = repo.getBoardsForCategory(serviceId = 0, categoryId = newsCategory.categoryId).first()

        // --- Assert ---
        // ニュースカテゴリには板A, 板B が紐付いている
        assertEquals(2, boards.size)
        assertEquals(
            setOf("https://example.test/news/a", "https://example.test/news/b"),
            boards.map { it.url }.toSet(),
        )
    }

    @Test
    fun getAllServicesWithCount_returnsInsertedService() = runTest {
        // --- Arrange ---
        val (repo, fakes, _) = newRepo(
            remote = FakeBbsMenuDataSource(
                menuByUrl = mapOf("https://menu.example.test/bbsmenu.html" to sampleMenu()),
            ),
        )

        // --- Act ---
        repo.addOrUpdateService("https://menu.example.test/bbsmenu.html")
        val services = repo.getAllServicesWithCount().first()

        // --- Assert ---
        assertEquals(1, services.size)
        val first = services.single().service
        assertNotNull(first)
        assertEquals("example.test", first.domain)
    }

    @Test
    fun extractDomainFromUrl_returnsLastTwoHostSegments() {
        // --- Arrange ---
        val (repo, _, _) = newRepo()

        // --- Act / Assert ---
        assertEquals("example.test", repo.extractDomainFromUrl("https://menu.example.test/bbsmenu.html"))
        assertEquals("5ch.net", repo.extractDomainFromUrl("https://menu.5ch.net/bbsmenu.html"))
        assertEquals("5ch.io", repo.extractDomainFromUrl("https://menu.5ch.io/bbsmenu.html"))
    }

    @Test
    fun extractHostFromUrl_throwsForInvalidUrl() {
        // --- Arrange ---
        val (repo, _, _) = newRepo()

        // --- Act / Assert ---
        try {
            repo.extractHostFromUrl("not a url")
            fail("expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            // ok
        }
    }

    @Test
    fun firstPathSegment_returnsFirstSegmentOrNull() {
        // --- Arrange ---
        val (repo, _, _) = newRepo()

        // --- Act / Assert ---
        assertEquals("news", repo.firstPathSegment("https://example.test/news/a"))
        assertEquals("livejaw", repo.firstPathSegment("https://example.test/livejaw"))
        assertEquals(null, repo.firstPathSegment("https://example.test"))
        assertEquals(null, repo.firstPathSegment("not a url"))
    }
}
