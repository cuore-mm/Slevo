package com.websarva.wings.android.slevo.ui.settings.backup

import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** [BackupResultSnackbarEffect] の durable queue 表示契約を検証する Compose test。 */
@RunWith(AndroidJUnit4::class)
class BackupSnackbarEffectTest {
    @get:Rule
    val composeRule = createComposeRule()

    /** export success は既存文言、Short duration、同じ result ID の acknowledge を使う。 */
    @Test
    fun exportSucceeded_usesExistingMessageShortDurationAndAcknowledgesId() {
        assertResult(
            result = BackupUiEvent.ExportSucceeded(11L),
            message = "バックアップファイルを作成しました",
        )
    }

    /** export failure は既存文言、Short duration、同じ result ID の acknowledge を使う。 */
    @Test
    fun exportFailed_usesExistingMessageShortDurationAndAcknowledgesId() {
        assertResult(
            result = BackupUiEvent.ExportFailed(12L),
            message = "バックアップファイルの作成に失敗しました",
        )
    }

    /** invalid backup は既存文言、Short duration、同じ result ID の acknowledge を使う。 */
    @Test
    fun invalidBackup_usesExistingMessageShortDurationAndAcknowledgesId() {
        assertResult(
            result = BackupUiEvent.InvalidBackup(13L),
            message = "無効または未対応のバックアップファイルです",
        )
    }

    /** restore preparation failure は既存文言、Short duration、同じ result ID の acknowledge を使う。 */
    @Test
    fun restorePrepareFailed_usesExistingMessageShortDurationAndAcknowledgesId() {
        assertResult(
            result = BackupUiEvent.RestorePrepareFailed(14L),
            message = "復元の準備に失敗しました",
        )
    }

    /** 表示中の effect cancellation では acknowledge せず、再作成後に同じ先頭を再表示する。 */
    @Test
    fun effectCancellation_keepsHeadForRecreation() {
        val result = BackupUiEvent.ExportSucceeded(21L)
        var acknowledgedId: Long? = null

        composeRule.setContent {
            val hostState = remember { SnackbarHostState() }
            Scaffold(snackbarHost = { SnackbarHost(hostState) }) {
                BackupResultSnackbarEffect(result, hostState) { acknowledgedId = it }
            }
        }
        composeRule.onNodeWithText("バックアップファイルを作成しました").assertIsDisplayed()

        // Disposing the first composition cancels showSnackbar before its normal completion.
        composeRule.setContent {
            val hostState = remember { SnackbarHostState() }
            Scaffold(snackbarHost = { SnackbarHost(hostState) }) {
                BackupResultSnackbarEffect(result, hostState) { acknowledgedId = it }
            }
        }
        assertNull(acknowledgedId)
        composeRule.onNodeWithText("バックアップファイルを作成しました").assertIsDisplayed()

        composeRule.mainClock.advanceTimeBy(5_000)
        composeRule.waitForIdle()
        assertEquals(21L, acknowledgedId)
    }

    /** queue 先頭の表示完了後にだけ次の result を表示し、順序を保持する。 */
    @Test
    fun queuedResults_areDisplayedSequentially() {
        val first = BackupUiEvent.ExportSucceeded(31L)
        val second = BackupUiEvent.ExportFailed(32L)
        var acknowledgedIds = emptyList<Long>()

        composeRule.setContent {
            var pendingResults by remember { mutableStateOf(listOf(first, second)) }
            val hostState = remember { SnackbarHostState() }
            Scaffold(snackbarHost = { SnackbarHost(hostState) }) {
                BackupResultSnackbarEffect(pendingResults.firstOrNull(), hostState) { id ->
                    acknowledgedIds += id
                    pendingResults = pendingResults.drop(1)
                }
            }
        }

        composeRule.onNodeWithText("バックアップファイルを作成しました").assertIsDisplayed()
        composeRule.mainClock.advanceTimeBy(5_000)
        composeRule.waitForIdle()
        assertEquals(listOf(31L), acknowledgedIds)
        composeRule.onNodeWithText("バックアップファイルの作成に失敗しました").assertIsDisplayed()

        composeRule.mainClock.advanceTimeBy(5_000)
        composeRule.waitForIdle()
        assertEquals(listOf(31L, 32L), acknowledgedIds)
    }

    /** 4 種類の result が共通の Snackbar duration と completion callback を使うことを検証する。 */
    private fun assertResult(result: BackupUiEvent, message: String) {
        var acknowledgedId: Long? = null
        lateinit var hostState: SnackbarHostState

        composeRule.setContent {
            hostState = remember { SnackbarHostState() }
            Scaffold(snackbarHost = { SnackbarHost(hostState) }) {
                BackupResultSnackbarEffect(result, hostState) { acknowledgedId = it }
            }
        }

        composeRule.onNodeWithText(message).assertIsDisplayed()
        composeRule.runOnIdle {
            assertEquals(SnackbarDuration.Short, hostState.currentSnackbarData?.visuals?.duration)
        }
        composeRule.mainClock.advanceTimeBy(5_000)
        composeRule.waitForIdle()
        assertEquals(result.id, acknowledgedId)
    }
}
