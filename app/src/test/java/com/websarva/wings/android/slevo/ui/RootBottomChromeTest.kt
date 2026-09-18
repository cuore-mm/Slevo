package com.websarva.wings.android.slevo.ui

import org.junit.Assert.assertEquals
import org.junit.Test

/** Root Snackbarの回避量がRootNavHostへ影響せずdestination種別だけで決まることを検証する。 */
class RootBottomChromeTest {

    /** MainShell、BBS、下部chromeなしの各実測値を混同せずに返す。 */
    @Test
    fun heightFor_selectsOnlyTheCurrentChromeOwner() {
        val heights = RootBottomChromeHeights(
            mainShellHeightPx = 96,
            bbsHeightPx = 144,
        )

        assertEquals(96, heights.heightFor(RootBottomChromeOwner.MainShell))
        assertEquals(144, heights.heightFor(RootBottomChromeOwner.Bbs))
        assertEquals(0, heights.heightFor(RootBottomChromeOwner.None))
    }

    /** 未測定値は0として扱い、Snackbar表示前にRoot contentへ余白を追加しない。 */
    @Test
    fun defaultHeights_doNotReserveRootContentSpace() {
        val heights = RootBottomChromeHeights()

        assertEquals(0, heights.heightFor(RootBottomChromeOwner.MainShell))
        assertEquals(0, heights.heightFor(RootBottomChromeOwner.Bbs))
        assertEquals(0, heights.heightFor(RootBottomChromeOwner.None))
    }
}
