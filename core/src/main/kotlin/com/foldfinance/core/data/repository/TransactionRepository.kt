package com.foldfinance.core.data.repository

import com.foldfinance.core.model.RawMessage
import com.foldfinance.core.model.Transaction
import com.foldfinance.parser.Direction
import com.foldfinance.parser.Kind
import kotlinx.coroutines.flow.Flow
import java.time.Instant

/** Порядок списка операций. При равенстве — по id убыванию. */
enum class TransactionSort { DATE_DESC, DATE_ASC, AMOUNT_DESC, AMOUNT_ASC }

/**
 * Фильтр списка операций. Все условия объединяются через AND; `null` — условие не применяется.
 *
 * @property from начало периода, включительно.
 * @property to конец периода, **не включительно**.
 * @property categoryIds только эти категории; пустое множество — ничего (кроме [includeUncategorized]).
 * @property includeUncategorized при заданном [categoryIds] добавить операции без категории.
 * @property directions только эти направления.
 * @property currency только эта валюта (ISO 4217); полезно вместе с границами суммы.
 * @property minAmountMinor / [maxAmountMinor] границы суммы в минорных единицах, включительно.
 * @property query подстрока мерчанта или заметки, без учёта регистра (кириллица тоже).
 */
data class TransactionFilter(
    val from: Instant? = null,
    val to: Instant? = null,
    val categoryIds: Set<Long>? = null,
    val includeUncategorized: Boolean = false,
    val directions: Set<Direction>? = null,
    val currency: String? = null,
    val minAmountMinor: Long? = null,
    val maxAmountMinor: Long? = null,
    val query: String? = null,
    val sort: TransactionSort = TransactionSort.DATE_DESC,
)

/** Ручной ввод операции (FAB «Добавить»). Сумма положительна, знак задаёт [direction]. */
data class ManualTransactionInput(
    val occurredAt: Instant,
    val amountMinor: Long,
    val currency: String,
    val direction: Direction,
    val kind: Kind = Kind.UNKNOWN,
    val merchant: String? = null,
    val cardMask: String? = null,
    val categoryId: Long? = null,
    val note: String? = null,
) {
    init {
        require(amountMinor > 0) { "amountMinor must be positive" }
        require(currency.length == 3) { "currency must be ISO 4217" }
    }
}

/** Операции: чтение списков для UI и пользовательские правки. */
interface TransactionRepository {

    /** Операции по фильтру; обновляется при любом изменении таблицы. */
    fun observe(filter: TransactionFilter = TransactionFilter()): Flow<List<Transaction>>

    /** Одна операция; `null`, если удалена. */
    fun observeById(id: Long): Flow<Transaction?>

    /** Очередь проверки: `needsReview = true`, новые сверху. */
    fun observeReviewQueue(): Flow<List<Transaction>>

    /** Размер очереди проверки (бейдж, виджет). */
    fun observeReviewCount(): Flow<Int>

    /** Исходные сообщения, привязанные к операции (SMS и/или push). */
    fun observeRawMessages(transactionId: Long): Flow<List<RawMessage>>

    /** Добавить ручную операцию (`source = MANUAL`, `confidence = 1`). @return id. */
    suspend fun addManual(input: ManualTransactionInput): Long

    /**
     * Сохранить правку пользователя. Берутся редактируемые поля (`occurredAt`, сумма, валюта,
     * `direction`, `kind`, `merchant`, `cardMask`, `categoryId`, `note`, `needsReview`, `linkedTransactionId`);
     * `source`, `dedupKey`, `createdAt` остаются из БД. Ставит `userEdited = true` —
     * перепарсинг больше не перезаписывает операцию.
     * @throws NoSuchElementException если операции нет.
     */
    suspend fun update(transaction: Transaction)

    /** Подтвердить операции из очереди проверки (`needsReview = false`), значения не меняются. */
    suspend fun confirmReview(ids: Collection<Long>)

    suspend fun confirmReview(id: Long) = confirmReview(listOf(id))

    /** Назначить категорию (или снять — `null`). Это ручная правка: ставит `userEdited = true`. */
    suspend fun setCategory(ids: Collection<Long>, categoryId: Long?)

    suspend fun setCategory(id: Long, categoryId: Long?) = setCategory(listOf(id), categoryId)

    /**
     * Удалить операцию. Привязанные исходные сообщения остаются (повторный импорт их пропустит),
     * но помечаются так, чтобы перепарсинг не воскресил операцию.
     */
    suspend fun delete(id: Long)
}
