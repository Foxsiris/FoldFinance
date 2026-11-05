package com.foldfinance.core.data.repository

import androidx.room.withTransaction
import com.foldfinance.core.data.db.FoldFinanceDatabase
import com.foldfinance.core.data.db.entity.TransactionSplitEntity
import com.foldfinance.core.data.db.toModel
import com.foldfinance.core.model.TransactionSplit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Часть для [TransactionSplitRepository.replace].
 *
 * @property amountMinor > 0, в валюте родительской операции.
 * @property label что вошло в часть (строки чека); обрезается до [TransactionSplitRepository.MAX_LABEL].
 */
data class SplitPartInput(val categoryId: Long?, val amountMinor: Long, val label: String? = null)

/**
 * Разбиение операции по категориям (чек). Сама операция не меняется — сумма, категория, ключ дедупа
 * остаются, поэтому кросс-источниковый дедуп и перепарсинг работают как раньше. Аналитика
 * распределяет сумму операции по частям, остаток — в категорию операции
 * ([com.foldfinance.core.domain.analytics.AnalyticsCalculator]); итоги не меняются.
 */
interface TransactionSplitRepository {
    /** Части операции [parentId]; пусто — не разбита. */
    fun observe(parentId: Long): Flow<List<TransactionSplit>>

    /**
     * Заменить разбиение операции [parentId] на [parts] атомарно; пустой список — снять разбиение.
     *
     * @throws NoSuchElementException операции нет.
     * @throws IllegalArgumentException часть с суммой ≤ 0 или Σ частей больше суммы операции.
     */
    suspend fun replace(parentId: Long, parts: List<SplitPartInput>)

    companion object {
        const val MAX_LABEL: Int = 200
    }
}

@Singleton
class TransactionSplitRepositoryImpl @Inject constructor(
    private val db: FoldFinanceDatabase,
) : TransactionSplitRepository {
    private val dao = db.transactionSplitDao()

    override fun observe(parentId: Long): Flow<List<TransactionSplit>> =
        dao.observeByParent(parentId).map { list -> list.map { it.toModel() } }

    override suspend fun replace(parentId: Long, parts: List<SplitPartInput>) {
        require(parts.all { it.amountMinor > 0 }) { "split part amount must be positive" }
        val total = try {
            parts.fold(0L) { acc, p -> Math.addExact(acc, p.amountMinor) }
        } catch (e: ArithmeticException) {
            throw IllegalArgumentException("split parts exceed the operation amount", e)
        }
        db.withTransaction {
            val parent = db.transactionDao().getById(parentId)
                ?: throw NoSuchElementException("transaction $parentId not found")
            require(total <= parent.amountMinor) { "split parts exceed the operation amount" }
            dao.deleteByParent(parentId)
            if (parts.isNotEmpty()) {
                dao.insertAll(
                    parts.map {
                        TransactionSplitEntity(
                            parentId = parentId,
                            categoryId = it.categoryId,
                            amountMinor = it.amountMinor,
                            label = it.label?.trim()?.take(TransactionSplitRepository.MAX_LABEL)?.ifEmpty { null },
                        )
                    },
                )
            }
        }
    }
}
