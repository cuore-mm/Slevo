package com.websarva.wings.android.slevo.ui.settings.backup

import android.content.Context
import android.text.format.DateFormat
import java.time.Instant
import java.util.Date

/**
 * ISO 8601 UTC の backup 作成日時を、端末設定に合わせた日付と時刻へ整形する。
 *
 * Android の locale、time zone、12/24 時間設定を [DateFormat] に委譲し、想定外の値は
 * 検証済み manifest の raw 値へ fallback して確認ダイアログをクラッシュさせない。
 */
internal fun formatRestoreBackupDate(context: Context, createdAt: String): String {
    return try {
        val date = Date.from(Instant.parse(createdAt))
        val dateText = DateFormat.getMediumDateFormat(context).format(date)
        val timeText = DateFormat.getTimeFormat(context).format(date)
        "$dateText $timeText"
    } catch (_: RuntimeException) {
        createdAt
    }
}
