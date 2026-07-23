package com.websarva.wings.android.slevo.ui.settings.backup

import android.content.Context
import android.content.res.Configuration
import android.text.format.DateFormat
import androidx.test.core.app.ApplicationProvider
import java.time.Instant
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** [formatRestoreBackupDate] の locale、time zone、fallback 契約を検証する。 */
@RunWith(RobolectricTestRunner::class)
class RestoreBackupDateFormatterTest {

    /** UTC の作成日時を、指定した locale と time zone の DateFormat 結果へ変換する。 */
    @Test
    fun formatRestoreBackupDate_usesLocaleAndTimeZone() {
        val context = localizedContext(Locale.US)
        val previousTimeZone = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Tokyo"))
        try {
            val createdAt = "2026-01-01T00:00:00Z"
            val expectedDate = Date.from(Instant.parse(createdAt))
            val expected = "${DateFormat.getMediumDateFormat(context).format(expectedDate)} " +
                DateFormat.getTimeFormat(context).format(expectedDate)

            assertEquals(expected, formatRestoreBackupDate(context, createdAt))
            assertTrue(formatRestoreBackupDate(context, createdAt).contains("2026"))
        } finally {
            TimeZone.setDefault(previousTimeZone)
        }
    }

    /** malformed な作成日時は raw 値へ fallback し、dialog 表示用の例外を発生させない。 */
    @Test
    fun formatRestoreBackupDate_invalidValueFallsBackToRawValue() {
        val rawValue = "not-an-iso-date"

        assertEquals(
            rawValue,
            formatRestoreBackupDate(ApplicationProvider.getApplicationContext(), rawValue),
        )
    }

    /** テスト用に resource locale だけを差し替えた Context を生成する。 */
    private fun localizedContext(locale: Locale): Context {
        val baseContext = ApplicationProvider.getApplicationContext<Context>()
        val configuration = Configuration(baseContext.resources.configuration)
        configuration.setLocale(locale)
        return baseContext.createConfigurationContext(configuration)
    }
}
