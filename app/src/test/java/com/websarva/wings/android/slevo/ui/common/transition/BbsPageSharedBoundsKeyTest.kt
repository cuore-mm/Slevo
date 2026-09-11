package com.websarva.wings.android.slevo.ui.common.transition

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * TabsカードとBoard / Thread表示ページ全体で共有するpage keyのidentity分離を検証する。
 */
class BbsPageSharedBoundsKeyTest {
    /** 同じBoard identityは同じページ共有対象として扱う。 */
    @Test
    fun boardKey_withSameIdentity_isEqual() {
        assertEquals(
            BbsPageSharedBoundsKey.Board("https://example.com/board"),
            BbsPageSharedBoundsKey.Board("https://example.com/board"),
        )
    }

    /** BoardとThreadは同じ文字列identityでもページ共有対象を分離する。 */
    @Test
    fun boardAndThreadKeys_areNotEqual() {
        assertNotEquals(
            BbsPageSharedBoundsKey.Board("same-identity"),
            BbsPageSharedBoundsKey.Thread("same-identity"),
        )
    }

    /** 同じ種別でも異なるidentityはページ共有対象を分離する。 */
    @Test
    fun keys_withDifferentIdentity_areNotEqual() {
        assertNotEquals(
            BbsPageSharedBoundsKey.Thread("thread-a"),
            BbsPageSharedBoundsKey.Thread("thread-b"),
        )
    }

    /** ページ全体用keyと既存コントローラー用keyは別の型として扱う。 */
    @Test
    fun pageAndControllerKeys_areNotEqual() {
        assertNotEquals(
            BbsPageSharedBoundsKey.Board("board"),
            BbsControllerSharedBoundsKey.Board("board"),
        )
    }
}
