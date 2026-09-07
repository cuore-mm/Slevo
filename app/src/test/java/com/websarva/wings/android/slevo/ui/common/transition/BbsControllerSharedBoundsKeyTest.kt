package com.websarva.wings.android.slevo.ui.common.transition

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * Board/ThreadコントローラーShared Bounds keyのidentity分離を検証する。
 */
class BbsControllerSharedBoundsKeyTest {
    /** 同じBoard identityは同じ共有対象として扱う。 */
    @Test
    fun boardKey_withSameIdentity_isEqual() {
        assertEquals(
            BbsControllerSharedBoundsKey.Board("https://example.com/board/"),
            BbsControllerSharedBoundsKey.Board("https://example.com/board/"),
        )
    }

    /** BoardとThreadは同じ文字列identityでも共有対象を分離する。 */
    @Test
    fun boardAndThreadKeys_areNotEqual() {
        assertNotEquals(
            BbsControllerSharedBoundsKey.Board("same-identity"),
            BbsControllerSharedBoundsKey.Thread("same-identity"),
        )
    }

    /** 同じ種別でも異なるidentityは共有対象を分離する。 */
    @Test
    fun keys_withDifferentIdentity_areNotEqual() {
        assertNotEquals(
            BbsControllerSharedBoundsKey.Thread("thread-a"),
            BbsControllerSharedBoundsKey.Thread("thread-b"),
        )
    }

    /** 下段アクション行はBoard/Threadのidentityとは独立した固定共有対象である。 */
    @Test
    fun actionsRowKey_isStableAndDistinct() {
        assertEquals(
            BbsControllerSharedBoundsKey.ActionsRow,
            BbsControllerSharedBoundsKey.ActionsRow,
        )
        assertNotEquals(
            BbsControllerSharedBoundsKey.ActionsRow,
            BbsControllerSharedBoundsKey.Board("actions"),
        )
    }
}
