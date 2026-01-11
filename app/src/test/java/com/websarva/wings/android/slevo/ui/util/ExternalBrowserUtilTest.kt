package com.websarva.wings.android.slevo.ui.util

import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ResolveInfo
import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 外部ブラウザ候補生成のユニットテスト。
 */
class ExternalBrowserUtilTest {
    @Test
    fun buildBrowserIntents_excludesSelfPackage() {
        val baseIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com"))
        val activities = listOf(
            resolveInfo("com.example.slevo", "SelfActivity"),
            resolveInfo("com.browser.app", "BrowserActivity")
        )

        val intents = buildBrowserIntents(
            packageName = "com.example.slevo",
            baseIntent = baseIntent,
            activities = activities
        )

        assertEquals(1, intents.size)
        assertEquals("com.browser.app", intents.first().component?.packageName)
    }

    @Test
    fun buildBrowserIntents_returnsEmptyWhenNoCandidates() {
        val baseIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com"))
        val activities = listOf(
            resolveInfo("com.example.slevo", "SelfActivity")
        )

        val intents = buildBrowserIntents(
            packageName = "com.example.slevo",
            baseIntent = baseIntent,
            activities = activities
        )

        assertTrue(intents.isEmpty())
    }
}

private fun resolveInfo(packageName: String, className: String): ResolveInfo {
    return ResolveInfo().apply {
        activityInfo = ActivityInfo().apply {
            this.packageName = packageName
            name = className
        }
    }
}
