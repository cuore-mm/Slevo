package com.websarva.wings.android.slevo.data.repository

import com.websarva.wings.android.slevo.data.datasource.local.SettingsLocalDataSource
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** [SettingsRepository] の返信通知設定に対する初期値参照と保存委譲を検証する。 */
class SettingsRepositoryTest {
    /** 返信通知設定の購読値と現在値の初期OFFを確認する。 */
    @Test
    fun replyNotificationSetting_observesDefaultDisabledValue() = runTest {
        val local = mockk<SettingsLocalDataSource>()
        every { local.observeIsReplyNotificationEnabled() } returns flowOf(false)
        coEvery { local.getIsReplyNotificationEnabled() } returns false
        val repository = SettingsRepository(local)

        assertFalse(repository.observeIsReplyNotificationEnabled().first())
        assertFalse(repository.getIsReplyNotificationEnabled())
    }

    /** 返信通知設定の更新がローカルデータソースへ委譲されることを確認する。 */
    @Test
    fun setReplyNotificationEnabled_delegatesPersistentUpdate() = runTest {
        val local = mockk<SettingsLocalDataSource>(relaxed = true)
        val repository = SettingsRepository(local)

        repository.setReplyNotificationEnabled(true)

        coVerify(exactly = 1) { local.setReplyNotificationEnabled(true) }
    }
}
