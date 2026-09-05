package com.websarva.wings.android.slevo.ui.common

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import com.websarva.wings.android.slevo.R
import org.junit.Rule
import org.junit.Test

/** 固定画面種別ボタンの表示ラベルとTalkBack向け意味論を検証する。 */
class TabToolBarTest {

    @get:Rule
    val composeRule = createComposeRule()

    /** 遷移先が未解決の場合、ラベルを保ったままボタンを無効化する。 */
    @Test
    fun destinationButton_exposesDescriptionAndDisabledState() {
        composeRule.setContent {
            MaterialTheme {
                TabDestinationButton(
                    labelRes = R.string.open_thread_screen,
                    contentDescriptionRes = R.string.open_thread_screen_description,
                    enabled = false,
                    onClick = {},
                )
            }
        }

        composeRule
            .onNodeWithContentDescription("スレッドタブに移動")
            .assertTextEquals("スレ")
            .assertIsNotEnabled()
    }
}
