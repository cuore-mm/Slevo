package com.websarva.wings.android.slevo.ui.common

import com.websarva.wings.android.slevo.ui.util.ImageLoadFailureType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** [shouldNavigateToImage] が成功状態だけを遷移可能にすることを検証する。 */
class ImageThumbnailNavigationTest {
    /** 読み込み中・失敗・再試行状態ではビューア遷移を許可しない。 */
    @Test
    fun navigationRequiresSuccessfulLoadWithoutFailure() {
        assertFalse(shouldNavigateToImage(hasLoadedSuccessfully = false, failureType = null))
        assertFalse(shouldNavigateToImage(hasLoadedSuccessfully = true, failureType = ImageLoadFailureType.UNKNOWN))
        assertFalse(shouldNavigateToImage(hasLoadedSuccessfully = true, failureType = ImageLoadFailureType.HTTP_404))
        assertFalse(shouldNavigateToImage(hasLoadedSuccessfully = true, failureType = ImageLoadFailureType.HTTP_410))
        assertTrue(shouldNavigateToImage(hasLoadedSuccessfully = true, failureType = null))
    }
}
