package com.websarva.wings.android.slevo.ui.common

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.websarva.wings.android.slevo.R
import com.websarva.wings.android.slevo.ui.theme.SlevoTheme
import com.websarva.wings.android.slevo.ui.util.ImageLoadFailureType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** 共有サムネイル抽出後も画像グリッドが三列配置を維持することを検証する。 */
@OptIn(ExperimentalSharedTransitionApi::class)
@RunWith(AndroidJUnit4::class)
class ImageThumbnailGridTest {
    @get:Rule
    val composeRule = createComposeRule()

    /** 四枚の画像を三列で並べ、四枚目を次行の先頭に配置する。 */
    @Test
    fun gridKeepsThreeColumnsAfterThumbnailExtraction() {
        // --- Fixture and image grid ---
        val urls = listOf("image-1", "image-2", "image-3", "image-4")
        val description = InstrumentationRegistry.getInstrumentation()
            .targetContext.getString(R.string.image_thumbnail_open)

        composeRule.setContent {
            SlevoTheme {
                SharedTransitionLayout {
                    val sharedScope = this
                    AnimatedVisibility(visible = true) {
                        ImageThumbnailGrid(
                            imageUrls = urls,
                            modifier = Modifier.width(320.dp),
                            transitionNamespace = "test-post-1",
                            onImageClick = { _, _, _, _ -> },
                            imageLoadFailureByUrl = urls.associateWith { ImageLoadFailureType.UNKNOWN },
                            enableSharedElement = false,
                            sharedTransitionScope = sharedScope,
                            animatedVisibilityScope = this,
                        )
                    }
                }
            }
        }

        // --- Three-column layout assertions ---
        val bounds = composeRule.onAllNodesWithContentDescription(
            description,
            useUnmergedTree = true,
        ).fetchSemanticsNodes().map { it.boundsInRoot }

        assertEquals(4, bounds.size)
        assertTrue(bounds[0].left < bounds[1].left && bounds[1].left < bounds[2].left)
        assertEquals(bounds[0].top, bounds[1].top, 1f)
        assertEquals(bounds[0].top, bounds[2].top, 1f)
        assertTrue(bounds[3].top > bounds[0].top)
        assertEquals(bounds[0].width, bounds[1].width, 1f)
        assertEquals(bounds[1].width, bounds[2].width, 1f)
    }
}
