package com.websarva.wings.android.bbsviewer.data.repository

import com.websarva.wings.android.bbsviewer.data.datasource.local.dao.NgIdDao
import com.websarva.wings.android.bbsviewer.data.datasource.local.entity.NgIdEntity
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NgIdRepository @Inject constructor(
    private val dao: NgIdDao
) {
    suspend fun addNgId(pattern: String, isRegex: Boolean, boardId: Long?) {
        dao.insert(NgIdEntity(pattern = pattern, isRegex = isRegex, boardId = boardId))
    }
}
