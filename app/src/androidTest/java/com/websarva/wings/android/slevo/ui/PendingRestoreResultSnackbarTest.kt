package com.websarva.wings.android.slevo.ui

import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.websarva.wings.android.slevo.data.backup.pending.PendingRestoreNotificationType
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * root-level restore result Snackbarの表示と表示完了callbackを検証する。
 */
@RunWith(AndroidJUnit4::class)
class PendingRestoreResultSnackbarTest {
    @get:Rule
    val composeRule = createComposeRule()

    /** success messageを表示し、表示完了後にtokenを返す。 */
    @Test
    fun successNotification_isShownAndAcknowledgedAfterDisplay() {
        var displayedToken: String? = null
        composeRule.setContent {
            val hostState = remember { SnackbarHostState() }
            Scaffold(snackbarHost = { SnackbarHost(hostState) }) {
                PendingRestoreResultSnackbar(
                    notification = PendingRestoreNotificationUiModel(
                        token = "success-token",
                        type = PendingRestoreNotificationType.SUCCESS,
                    ),
                    snackbarHostState = hostState,
                    onDisplayed = { displayedToken = it },
                )
            }
        }

        composeRule.onNodeWithText("バックアップを復元しました").assertIsDisplayed()
        composeRule.mainClock.advanceTimeBy(5_000)
        composeRule.waitForIdle()

        assertEquals("success-token", displayedToken)
    }

    /** failure notificationはfailure固定文言を使う。 */
    @Test
    fun failureNotification_usesFailureMessage() {
        composeRule.setContent {
            val hostState = remember { SnackbarHostState() }
            Scaffold(snackbarHost = { SnackbarHost(hostState) }) {
                PendingRestoreResultSnackbar(
                    notification = PendingRestoreNotificationUiModel(
                        token = "failure-token",
                        type = PendingRestoreNotificationType.FAILURE,
                    ),
                    snackbarHostState = hostState,
                    onDisplayed = {},
                )
            }
        }

        composeRule.onNodeWithText("バックアップの復元に失敗しました").assertIsDisplayed()
    }

    /** notificationがない場合はhostへ何もenqueueしない。 */
    @Test
    fun noNotification_doesNotShowOrAcknowledge() {
        var callbackCount = 0
        composeRule.setContent {
            val hostState = remember { SnackbarHostState() }
            Scaffold(snackbarHost = { SnackbarHost(hostState) }) {
                PendingRestoreResultSnackbar(
                    notification = null,
                    snackbarHostState = hostState,
                    onDisplayed = { callbackCount++ },
                )
            }
        }

        composeRule.waitForIdle()

        assertEquals(0, callbackCount)
    }

    /** token更新時も同じroot hostで新notificationを表示する。 */
    @Test
    fun updatedToken_keepsRootHostAndShowsTheNewResult() {
        val first = PendingRestoreNotificationUiModel(
            token = "first-token",
            type = PendingRestoreNotificationType.SUCCESS,
        )
        val second = PendingRestoreNotificationUiModel(
            token = "second-token",
            type = PendingRestoreNotificationType.FAILURE,
        )
        lateinit var updateNotification: (PendingRestoreNotificationUiModel) -> Unit

        composeRule.setContent {
            var notification by remember { mutableStateOf(first) }
            updateNotification = { notification = it }
            val hostState = remember { SnackbarHostState() }
            Scaffold(snackbarHost = { SnackbarHost(hostState) }) {
                PendingRestoreResultSnackbar(
                    notification = notification,
                    snackbarHostState = hostState,
                    onDisplayed = {},
                )
            }
        }

        composeRule.onNodeWithText("バックアップを復元しました").assertIsDisplayed()
        updateNotification(second)
        composeRule.waitForIdle()

        composeRule.onNodeWithText("バックアップの復元に失敗しました").assertIsDisplayed()
    }
}
