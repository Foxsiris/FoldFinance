package com.foldfinance.core.data.repository

import androidx.room.withTransaction
import com.foldfinance.core.data.db.FoldFinanceDatabase
import com.foldfinance.core.data.db.toModel
import com.foldfinance.core.model.RawMessage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Раздел «Не распознано» очереди проверки: сохранённые банковские сообщения, из которых не
 * получилось извлечь операцию (`UNCERTAIN` без частичной операции или `FAILED`). Без него такие
 * сообщения были бы видны только после улучшения парсера.
 */
interface UnrecognizedMessagesRepository {
    /** Нераспознанные сообщения без операции, не скрытые пользователем; новые сверху. */
    fun observeUnrecognized(): Flow<List<RawMessage>>

    /** Скрыть сообщение из раздела; операции из него не создаются и при перепарсинге. */
    suspend fun hide(rawMessageId: Long)

    /**
     * Пользователь добавил операцию [transactionId] вручную по тексту сообщения: сообщение
     * привязывается к ней (уходит из раздела, текст виден в карточке операции).
     * @return `false`, если сообщение уже привязано к другой операции или удалено.
     */
    suspend fun attachToTransaction(rawMessageId: Long, transactionId: Long): Boolean
}

@Singleton
class UnrecognizedMessagesRepositoryImpl @Inject constructor(
    private val db: FoldFinanceDatabase,
) : UnrecognizedMessagesRepository {
    private val dao = db.rawMessageDao()

    override fun observeUnrecognized(): Flow<List<RawMessage>> =
        dao.observeUnrecognized().map { list -> list.map { it.toModel() } }

    override suspend fun hide(rawMessageId: Long) {
        dao.hide(rawMessageId)
    }

    override suspend fun attachToTransaction(rawMessageId: Long, transactionId: Long): Boolean =
        db.withTransaction {
            db.transactionDao().getById(transactionId) != null &&
                dao.attachToTransaction(rawMessageId, transactionId) == 1
        }
}
