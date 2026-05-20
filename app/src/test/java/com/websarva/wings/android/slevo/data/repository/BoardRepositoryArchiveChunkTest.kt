package com.websarva.wings.android.slevo.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 板更新時のアーカイブ対象分割ロジックを検証するテスト。
 *
 * `IN` 句へ渡す ID 群が巨大になっても、順序を保ったまま安全な単位へ分割されることを確認する。
 */
class BoardRepositoryArchiveChunkTest {
    /**
     * 件数が上限を超える場合に複数チャンクへ分割されることを確認する。
     */
    @Test
    fun chunkThreadIdsForArchive_splitsLargeListIntoMultipleChunks() {
        // --- Arrange ---
        val ids = (1..2_050).map { it.toString() }

        // --- Act ---
        val chunks = chunkThreadIdsForArchive(ids, chunkSize = 900)

        // --- Assert ---
        assertEquals(3, chunks.size)
        assertEquals(900, chunks[0].size)
        assertEquals(900, chunks[1].size)
        assertEquals(250, chunks[2].size)
        assertEquals(ids, chunks.flatten())
    }

    /**
     * 空入力では SQL 発行対象が存在しないため、空チャンクを返すことを確認する。
     */
    @Test
    fun chunkThreadIdsForArchive_returnsEmptyWhenNoIds() {
        // --- Act ---
        val chunks = chunkThreadIdsForArchive(emptyList(), chunkSize = 900)

        // --- Assert ---
        assertTrue(chunks.isEmpty())
    }

    /**
     * 未アーカイブ集合だけを差分計算の母集合に使うことを確認する。
     */
    @Test
    fun calculateRemovedActiveThreadIds_returnsOnlyIdsMissingFromLatestSubject() {
        // --- Arrange ---
        val activeExistingIds = listOf("100", "200", "300", "400")
        val latestSubjectIds = listOf("200", "400", "500")

        // --- Act ---
        val removed = calculateRemovedActiveThreadIds(activeExistingIds, latestSubjectIds)

        // --- Assert ---
        assertEquals(listOf("100", "300"), removed)
    }
}
