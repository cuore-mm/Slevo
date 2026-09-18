package com.websarva.wings.android.slevo.ui.common.scroll

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.junit4.createComposeRule
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.atomic.AtomicBoolean

/**
 * LazyColumnの初期中央寄せとリスト境界でのクランプを検証する。
 */
class LazyListCenteringTest {

    @get:Rule
    val composeRule = createComposeRule()

    /** 中間のアイテムをviewport中央へ配置できることを確認する。 */
    @Test
    fun centerLazyListItemAtIndex_centersMiddleItem() {
        lateinit var listState: androidx.compose.foundation.lazy.LazyListState
        lateinit var launchScroll: () -> Unit
        val completed = AtomicBoolean(false)

        composeRule.setContent {
            CenteringTestList { state, scope ->
                listState = state
                launchScroll = {
                    scope.launch {
                        centerLazyListItemAtIndex(state, index = 8, animate = false)
                        completed.set(true)
                    }
                }
            }
        }

        composeRule.waitForIdle()
        composeRule.runOnIdle { launchScroll() }
        composeRule.waitUntil { completed.get() }
        composeRule.waitForIdle()

        val delta = findLazyListItemCenterDeltaPx(listState.layoutInfo, index = 8)
        assertTrue(delta != null && kotlin.math.abs(delta) <= 1)
    }

    /** 先頭アイテムは中央寄せ補正でリスト境界より前へ移動しないことを確認する。 */
    @Test
    fun centerLazyListItemAtIndex_clampsAtStartBoundary() {
        lateinit var listState: androidx.compose.foundation.lazy.LazyListState
        lateinit var launchScroll: () -> Unit
        val completed = AtomicBoolean(false)

        composeRule.setContent {
            CenteringTestList { state, scope ->
                listState = state
                launchScroll = {
                    scope.launch {
                        centerLazyListItemAtIndex(state, index = 0, animate = false)
                        completed.set(true)
                    }
                }
            }
        }

        composeRule.waitForIdle()
        composeRule.runOnIdle { launchScroll() }
        composeRule.waitUntil { completed.get() }
        composeRule.waitForIdle()

        assertEquals(0, listState.firstVisibleItemIndex)
    }
}

/** 中央寄せテスト用の固定サイズLazyColumnを配置する。 */
@Composable
private fun CenteringTestList(
    onReady: (androidx.compose.foundation.lazy.LazyListState, kotlinx.coroutines.CoroutineScope) -> Unit,
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    SideEffect { onReady(listState, scope) }
    LazyColumn(
        state = listState,
        modifier = Modifier.height(300.dp),
        contentPadding = PaddingValues(vertical = 20.dp),
    ) {
        items((0 until 20).toList()) {
            Box(modifier = Modifier.height(40.dp))
        }
    }
}
