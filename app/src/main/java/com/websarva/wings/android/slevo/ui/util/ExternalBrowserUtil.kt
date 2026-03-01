package com.websarva.wings.android.slevo.ui.util

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ResolveInfo
import android.net.Uri
import androidx.core.net.toUri

/**
 * Slevo を除外した外部ブラウザの起動を提供するユーティリティ。
 */
object ExternalBrowserUtil {
    /**
     * Slevo を除外したブラウザ候補の選択ダイアログを表示する。
     *
     * 候補が存在しない場合は false を返す。
     */
    fun openBrowserChooser(
        context: Context,
        url: String,
        chooserTitle: String? = null
    ): Boolean {
        val baseIntent = Intent(Intent.ACTION_VIEW, url.toUri()).apply {
            addCategory(Intent.CATEGORY_BROWSABLE)
        }
        val activities = context.packageManager.queryIntentActivities(baseIntent, 0)
        val candidates = buildBrowserIntents(
            packageName = context.packageName,
            baseIntent = baseIntent,
            activities = activities
        )
        if (candidates.isEmpty()) {
            // 候補が無い場合は呼び出し側で通知する。
            return false
        }
        val primaryIntent = candidates.first()
        val chooser = Intent.createChooser(primaryIntent, chooserTitle)
        if (candidates.size > 1) {
            chooser.putExtra(
                Intent.EXTRA_INITIAL_INTENTS,
                candidates.drop(1).toTypedArray()
            )
        }
        context.startActivity(chooser)
        return true
    }
}

/**
 * Slevo を除外したブラウザ起動用の明示 Intent を構築する。
 */
internal fun buildBrowserIntents(
    packageName: String,
    baseIntent: Intent,
    activities: List<ResolveInfo>
): List<Intent> {
    return activities.mapNotNull { info ->
        val activityInfo = info.activityInfo ?: return@mapNotNull null
        if (activityInfo.packageName == packageName) {
            // Slevo 自身は除外する。
            return@mapNotNull null
        }
        Intent(baseIntent).apply {
            component = ComponentName(activityInfo.packageName, activityInfo.name)
        }
    }
}
