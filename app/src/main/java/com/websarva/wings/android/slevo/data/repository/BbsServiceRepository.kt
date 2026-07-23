package com.websarva.wings.android.slevo.data.repository

import com.websarva.wings.android.slevo.core.log.AppLogger
import com.websarva.wings.android.slevo.data.database.DatabaseWriteGate
import com.websarva.wings.android.slevo.data.datasource.local.dao.bbs.BbsServiceDao
import com.websarva.wings.android.slevo.data.datasource.local.dao.bbs.BoardCategoryCrossRefDao
import com.websarva.wings.android.slevo.data.datasource.local.dao.bbs.BoardDao
import com.websarva.wings.android.slevo.data.datasource.local.dao.bbs.CategoryDao
import com.websarva.wings.android.slevo.data.datasource.local.dao.bbs.CategoryWithBoardCount
import com.websarva.wings.android.slevo.data.datasource.local.dao.bbs.ServiceWithBoardCount
import com.websarva.wings.android.slevo.data.datasource.local.entity.bbs.BbsServiceEntity
import com.websarva.wings.android.slevo.data.datasource.local.entity.bbs.BoardCategoryCrossRef
import com.websarva.wings.android.slevo.data.datasource.local.entity.bbs.BoardEntity
import com.websarva.wings.android.slevo.data.datasource.local.entity.bbs.CategoryEntity
import com.websarva.wings.android.slevo.data.datasource.remote.BbsMenuDataSource
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import java.net.URI
import javax.inject.Inject
import javax.inject.Singleton

/**
 * BBS サービス／カテゴリ／板の読み書きと bbsmenu の解決を担当する Repository。
 *
 * `BbsServiceRepository` は BBS 書き込みの write boundary として、
 * 関連する Room DAO を直接利用する。旧 `BbsLocalDataSource` 層は廃止済み。
 *
 * @property serviceDao BBS サービス DAO。
 * @property categoryDao カテゴリ DAO。
 * @property boardDao 板 DAO。
 * @property crossRefDao 板⇔カテゴリ紐付け DAO。
 * @property gate 書き込み排他制御用のゲート。
 * @property remote bbsmenu 取得用のリモート DataSource。
 * @property logger ログ出力。
 */
@Singleton
class BbsServiceRepository @Inject constructor(
    private val serviceDao: BbsServiceDao,
    private val categoryDao: CategoryDao,
    private val boardDao: BoardDao,
    private val crossRefDao: BoardCategoryCrossRefDao,
    private val gate: DatabaseWriteGate,
    private val remote: BbsMenuDataSource,
    private val logger: AppLogger
) {
    companion object {
        private const val DEFAULT_MENU_URL = "https://menu.5ch.io/bbsmenu.html"
        private const val MENU_URL_5CH_NET = "https://menu.5ch.net/bbsmenu.html"
        private const val MENU_URL_5CH_IO = "https://menu.5ch.io/bbsmenu.html"
    }

    /** サービス一覧＋板数 */
    fun getAllServicesWithCount(): Flow<List<ServiceWithBoardCount>> =
        serviceDao.getServicesWithBoardCount()

    /**
     * リモートの bbsmenu を取得し、BBS サービス／カテゴリ／板を Room に登録する。
     *
     * 既存 service は `upsertServiceUngated` で表示名と menu URL の差分のみ update する。
     * カテゴリと板は一旦クリアしてから入力内容で再登録する。
     *
     * リモート fetch は gate の外で行い、DB 書き込み部分のみ `withWritePermit` 内で実行する。
     */
    suspend fun addOrUpdateService(menuUrl: String) {
        try {
            // --- リモート fetch (gate 外) ---
            val allCategories = remote.fetchBbsMenu(menuUrl) ?: emptyList()
            val nonEmpty = allCategories.filter { it.boards.isNotEmpty() }
            val domain = extractDomainFromUrl(menuUrl)

            // --- DB 書き込み (gate 内) ---
            gate.withWritePermit {
                val service = BbsServiceEntity(
                    domain = domain,
                    displayName = domain,
                    menuUrl = menuUrl
                )
                val svcId = upsertServiceUngated(service)

                // 既存のカテゴリ／板クリア
                categoryDao.clearForService(svcId)
                boardDao.clearForService(svcId)

                // カテゴリ登録＋板登録＋紐付け
                nonEmpty.forEach { cat ->
                    val newCat = CategoryEntity(serviceId = svcId, name = cat.categoryName)
                    val catId = categoryDao.insertCategory(newCat)
                    cat.boards.forEach { bd ->
                        val newBoard = BoardEntity(
                            serviceId = svcId,
                            url = bd.url,
                            name = bd.name
                        )
                        val boardId = insertOrGetBoardUngated(newBoard)
                        crossRefDao.insert(BoardCategoryCrossRef(boardId, catId))
                    }
                }
            }
        } catch (e: Exception) {
            logger.e(message = "サービス追加／更新失敗: $menuUrl", throwable = e)
        }
    }

    /** サービス削除 */
    suspend fun removeService(serviceId: Long) {
        gate.withWritePermit {
            serviceDao.deleteById(serviceId)
        }
    }

    /** カテゴリ一覧＋板数 */
    fun getCategoriesWithCount(serviceId: Long): Flow<List<CategoryWithBoardCount>> =
        categoryDao.getCategoriesWithBoardCount(serviceId)

    /**
     * 指定サービス・カテゴリの板一覧を取得
     */
    fun getBoardsForCategory(serviceId: Long, categoryId: Long): Flow<List<BoardEntity>> =
        observeBoardsForCategory(categoryId)

    /**
     * 指定サービスに属するすべての板を取得
     */
    fun getBoards(serviceId: Long): Flow<List<BoardEntity>> =
        boardDao.getBoardsForService(serviceId)

    /**
     * bbsmenu から boardKey に対応するホストを取得する。
     * DBへの保存は行わず、URL変換用途のみに利用する。
     */
    suspend fun resolveHostByBoardKeyFromMenu(
        boardKey: String,
        menuDomain: String? = null,
    ): String? {
        val menuUrl = when (menuDomain) {
            "5ch.net" -> MENU_URL_5CH_NET
            "5ch.io" -> MENU_URL_5CH_IO
            else -> DEFAULT_MENU_URL
        }
        val menu = remote.fetchBbsMenu(menuUrl) ?: return null
        val target = menu.asSequence()
            .flatMap { it.boards.asSequence() }
            .firstNotNullOfOrNull { board ->
                val segment = firstPathSegment(board.url)
                if (segment == boardKey) board.url else null
            }
        return target?.let { extractHostFromUrl(it) }
    }

    /**
     * menuUrl から host を抽出し、後半 2 段を `domain` として返す。
     *
     * `https://menu.5ch.io/...` → `5ch.io` のように、host の末尾 2 段だけを
     * 一意な service identifier として使う。
     */
    internal fun extractDomainFromUrl(menuUrl: String): String {
        val host = extractHostFromUrl(menuUrl)
        val parts = host.split('.')
        return if (parts.size >= 2) parts.takeLast(2).joinToString(".") else host
    }

    /**
     * URL 文字列から host 部分のみを取り出す。
     *
     * 解析に失敗した場合は `IllegalArgumentException` を投げる。
     */
    internal fun extractHostFromUrl(url: String): String {
        val host = runCatching { URI(url).host }.getOrNull()
        return host ?: throw IllegalArgumentException("Invalid URL: $url")
    }

    /**
     * URL のパス先頭セグメントを取り出す。
     *
     * パスが空、または解析に失敗した場合は `null` を返す（呼び出し側で continue する）。
     */
    internal fun firstPathSegment(url: String): String? {
        val path = runCatching { URI(url).path }.getOrNull() ?: return null
        val trimmed = path.trim('/')
        if (trimmed.isEmpty()) return null
        val first = trimmed.substringBefore('/', missingDelimiterValue = trimmed)
        return first.takeIf { it.isNotBlank() }
    }

    /**
     * 既存 service を domain で検索し、未登録なら insert、
     * 登録済みで表示名または menu URL が変わった場合のみ update する。
     *
     * @return 確定した serviceId。
     */
    private suspend fun upsertServiceUngated(service: BbsServiceEntity): Long {
        // --- 取得 ---
        val existing = serviceDao.findByDomain(service.domain)
        if (existing == null) {
            // --- 登録 ---
            serviceDao.insertService(service)
            val inserted = serviceDao.findByDomain(service.domain)
                ?: error("BbsServiceRepository: service insert lost (domain=${service.domain})")
            return inserted.serviceId
        }

        // --- 更新 ---
        if (existing.displayName != service.displayName || existing.menuUrl != service.menuUrl) {
            serviceDao.updateServiceMeta(
                serviceId = existing.serviceId,
                displayName = service.displayName,
                menuUrl = service.menuUrl
            )
        }
        return existing.serviceId
    }

    /**
     * board を insert し、conflict を示す場合は URL で既存 board の ID を取得して返す。
     */
    private suspend fun insertOrGetBoardUngated(board: BoardEntity): Long {
        // 1) まず挿入を試みる
        val rowId = boardDao.insertBoard(board)
        if (rowId != -1L) {
            // 新しく挿入できた → そのまま ID を返す
            return rowId
        }
        // 既に存在していた → URL で再取得
        return boardDao.findBoardIdByUrl(board.url)
    }

    /**
     * 指定カテゴリの board ID Flow と board DAO の Flow を合成し、
     * カテゴリ配下の board 一覧を返す。
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private fun observeBoardsForCategory(categoryId: Long): Flow<List<BoardEntity>> =
        // crossRef テーブル経由で取得
        crossRefDao.getBoardIdsForCategory(categoryId)
            .flatMapLatest { boardIds ->
                boardDao.getBoardsByIds(boardIds)
            }
}
