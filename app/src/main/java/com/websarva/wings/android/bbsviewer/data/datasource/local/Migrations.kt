package com.websarva.wings.android.bbsviewer.data.datasource.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object Migrations {
    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `groups` ADD COLUMN colorName TEXT NOT NULL DEFAULT 'yellow'")
            db.execSQL("ALTER TABLE thread_bookmark_groups ADD COLUMN colorName TEXT NOT NULL DEFAULT 'yellow'")
            // 旧HEX値から名称への変換
            val map = mapOf(
                "#EF5350" to "red", "#E57373" to "red",
                "#EC407A" to "pink", "#F06292" to "pink",
                "#AB47BC" to "purple", "#BA68C8" to "purple",
                "#5C6BC0" to "indigo", "#7986CB" to "indigo",
                "#42A5F5" to "blue", "#64B5F6" to "blue",
                "#26A69A" to "teal", "#4DB6AC" to "teal",
                "#66BB6A" to "green", "#81C784" to "green",
                "#FFEE58" to "yellow", "#FFF176" to "yellow",
                "#FFCA28" to "amber", "#FFD54F" to "amber",
                "#8D6E63" to "brown", "#A1887F" to "brown",
                "#FFFF00" to "yellow"
            )
            for ((hex, name) in map) {
                db.execSQL("UPDATE `groups` SET colorName = '$name' WHERE colorHex = '$hex'")
                db.execSQL("UPDATE thread_bookmark_groups SET colorName = '$name' WHERE colorHex = '$hex'")
            }
        }
    }
}
