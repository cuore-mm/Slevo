package com.websarva.wings.android.slevo.ui.bbsroute

import androidx.activity.ComponentActivity
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.websarva.wings.android.slevo.data.model.GestureDirection
import com.websarva.wings.android.slevo.ui.util.detectDirectionalGesture
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * BbsRoute のページ切り替えと本文ドラッグ境界を検証する Compose テスト。
 */
@OptIn(ExperimentalFoundationApi::class)
@RunWith(AndroidJUnit4::class)
class BbsRoutePagerSwipeTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    /**
     * スレッド本文の縦スクロール操作で隣接タブへ切り替わらないことを確認する。
     */
    @Test
    fun threadContentDragDoesNotSwitchTab() {
        val gestureState = mutableStateOf<GestureDirection?>(null)
        val pagerState = setTestContent(
            contentTag = "threadContent",
            bottomBarTag = "threadBottomBar",
            gestureState = gestureState,
        )

        composeRule.onNodeWithTag("threadContent")
            .performTouchInput { swipeUp() }

        composeRule.runOnIdle {
            assertEquals(0, pagerState.currentPage)
        }
    }

    /**
     * 板本文のドラッグ操作で隣接タブへ切り替わらないことを確認する。
     */
    @Test
    fun boardContentDragDoesNotSwitchTab() {
        val gestureState = mutableStateOf<GestureDirection?>(null)
        val pagerState = setTestContent(
            contentTag = "boardContent",
            bottomBarTag = "boardBottomBar",
            gestureState = gestureState,
        )

        composeRule.onNodeWithTag("boardContent")
            .performTouchInput { swipeUp() }

        composeRule.runOnIdle {
            assertEquals(0, pagerState.currentPage)
        }
    }

    /**
     * ボトムバー領域のスワイプで隣接タブへ遷移できることを確認する。
     */
    @Test
    fun bottomBarSwipeSwitchesTab() {
        val gestureState = mutableStateOf<GestureDirection?>(null)
        val pagerState = setTestContent(
            contentTag = "content",
            bottomBarTag = "bottomBar",
            gestureState = gestureState,
        )

        composeRule.onNodeWithTag("bottomBar")
            .performTouchInput { swipeLeft() }

        composeRule.runOnIdle {
            assertEquals(1, pagerState.currentPage)
        }
    }

    /**
     * テスト用の Pager レイアウトを構築し、状態参照を返す。
     */
    @Composable
    private fun TestPagerLayout(
        pagerState: PagerState,
        contentTag: String,
        bottomBarTag: String,
        gestureState: MutableState<GestureDirection?>,
    ) {
        val bottomBarDragState = rememberDraggableState { delta ->
            pagerState.dispatchRawDelta(-delta)
        }
        val bottomBarSwipeModifier = Modifier.draggable(
            state = bottomBarDragState,
            orientation = Orientation.Horizontal,
        )
        HorizontalPager(
            state = pagerState,
            pageCount = { 2 },
            modifier = Modifier.fillMaxSize(),
            userScrollEnabled = false,
        ) { _ ->
            Column(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag(contentTag)
                            .detectDirectionalGesture(
                                enabled = true,
                                onGesture = { gestureState.value = it },
                            )
                    )
                }
                Box(
                    modifier = Modifier
                        .height(56.dp)
                        .fillMaxWidth()
                        .then(bottomBarSwipeModifier)
                        .testTag(bottomBarTag)
                )
            }
        }
    }

    /**
     * Compose テスト用の UI をセットして PagerState を返す。
     */
    private fun setTestContent(
        contentTag: String,
        bottomBarTag: String,
        gestureState: MutableState<GestureDirection?>,
    ): PagerState {
        lateinit var pagerState: PagerState
        composeRule.setContent {
            pagerState = rememberPagerState(initialPage = 0, pageCount = { 2 })
            TestPagerLayout(
                pagerState = pagerState,
                contentTag = contentTag,
                bottomBarTag = bottomBarTag,
                gestureState = gestureState,
            )
        }
        return pagerState
    }
}
