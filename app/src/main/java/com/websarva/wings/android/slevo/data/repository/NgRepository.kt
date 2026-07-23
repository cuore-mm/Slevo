package com.websarva.wings.android.slevo.data.repository

import com.websarva.wings.android.slevo.data.database.DatabaseWriteGate
import com.websarva.wings.android.slevo.data.datasource.local.dao.NgDao
import com.websarva.wings.android.slevo.data.datasource.local.entity.NgEntity
import com.websarva.wings.android.slevo.data.model.NgType
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

@Singleton
class NgRepository @Inject constructor(
    private val dao: NgDao,
    private val gate: DatabaseWriteGate,
) {
    suspend fun addNg(
        pattern: String,
        isRegex: Boolean,
        type: NgType,
        boardId: Long?,
        id: Long? = null,
    ) = gate.withWritePermit {
        dao.insert(
            NgEntity(
                id = id ?: 0L,
                pattern = pattern,
                isRegex = isRegex,
                boardId = boardId,
                type = type,
            )
        )
    }

    fun observeNgs(): Flow<List<NgEntity>> = dao.getAll()

    suspend fun remove(ids: List<Long>) = gate.withWritePermit {
        dao.deleteByIds(ids)
    }
}
