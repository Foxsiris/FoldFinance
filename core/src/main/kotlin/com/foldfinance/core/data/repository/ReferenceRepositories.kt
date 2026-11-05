package com.foldfinance.core.data.repository

import androidx.room.withTransaction
import com.foldfinance.core.data.db.CategorySeeder
import com.foldfinance.core.data.db.FoldFinanceDatabase
import com.foldfinance.core.data.db.TextNorm
import com.foldfinance.core.data.db.entity.CardEntity
import com.foldfinance.core.data.db.entity.CategoryEntity
import com.foldfinance.core.data.db.entity.MerchantRuleEntity
import com.foldfinance.core.data.db.entity.TransactionEntity
import com.foldfinance.core.data.db.toEntity
import com.foldfinance.core.data.db.toModel
import com.foldfinance.core.data.ingest.TransferClassifier
import com.foldfinance.core.di.DataClock
import com.foldfinance.core.model.Card
import com.foldfinance.core.model.Category
import com.foldfinance.core.model.KnownBalance
import com.foldfinance.core.model.MerchantRule
import com.foldfinance.core.model.Subscription
import com.foldfinance.core.model.SubscriptionStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

// ---------------------------------------------------------------- Категории

/** Категории. Базовые ([com.foldfinance.core.model.DefaultCategories]) создаются при первом создании БД. */
interface CategoryRepository {
    /** Все категории по `sortOrder`. */
    fun observeAll(): Flow<List<Category>>

    fun observeById(id: Long): Flow<Category?>

    /** Снимок всех категорий (для категоризации вне UI). */
    suspend fun getAll(): List<Category>

    /** Добавить пользовательскую категорию в конец списка. @return id. */
    suspend fun add(name: String, iconKey: String, colorArgb: Int? = null): Long

    suspend fun rename(id: Long, name: String)

    suspend fun setIcon(id: Long, iconKey: String)

    /** Свой цвет категории; `null` — вернуть цвет темы по ключу. */
    suspend fun setColor(id: Long, colorArgb: Int?)

    /** Досоздать недостающие базовые категории. Идемпотентно, переименования не затирает. */
    suspend fun ensureDefaults()
}

@Singleton
class CategoryRepositoryImpl @Inject constructor(
    private val db: FoldFinanceDatabase,
) : CategoryRepository {
    private val dao = db.categoryDao()

    override fun observeAll(): Flow<List<Category>> = dao.observeAll().map { l -> l.map { it.toModel() } }

    override fun observeById(id: Long): Flow<Category?> = dao.observeById(id).map { it?.toModel() }

    override suspend fun getAll(): List<Category> = dao.getAll().map { it.toModel() }

    override suspend fun add(name: String, iconKey: String, colorArgb: Int?): Long {
        require(name.isNotBlank()) { "name must not be blank" }
        return db.withTransaction {
            dao.insertIgnore(
                CategoryEntity(
                    key = "user_${UUID.randomUUID()}",
                    name = name.trim(),
                    iconKey = iconKey,
                    colorArgb = colorArgb,
                    isSystem = false,
                    sortOrder = dao.maxSortOrder() + 1,
                ),
            )
        }
    }

    override suspend fun rename(id: Long, name: String) {
        require(name.isNotBlank()) { "name must not be blank" }
        dao.rename(id, name.trim())
    }

    override suspend fun setIcon(id: Long, iconKey: String) {
        dao.setIcon(id, iconKey)
    }

    override suspend fun setColor(id: Long, colorArgb: Int?) {
        dao.setColor(id, colorArgb)
    }

    override suspend fun ensureDefaults() {
        db.withTransaction { CategorySeeder.seed(dao) }
    }
}

// ---------------------------------------------------------------- Правила мерчантов

/**
 * Правила «мерчант → категория». Шаблон сравнивается как подстрока мерчанта без учёта
 * регистра (ё = е). Правило с тем же шаблоном заменяется.
 */
interface MerchantRuleRepository {
    fun observeAll(): Flow<List<MerchantRule>>

    suspend fun getAll(): List<MerchantRule>

    /**
     * Добавить (или заменить) правило и, если [applyToExisting], назначить категорию прошлым
     * операциям с подходящим мерчантом, кроме правленых вручную (`userEdited`).
     * @return результат: id правила и сколько операций перекатегоризировано.
     */
    suspend fun add(merchantPattern: String, categoryId: Long, applyToExisting: Boolean = true): AddRuleResult

    suspend fun delete(id: Long)
}

data class AddRuleResult(val ruleId: Long, val updatedTransactions: Int)

@Singleton
class MerchantRuleRepositoryImpl @Inject constructor(
    private val db: FoldFinanceDatabase,
    @DataClock private val clock: Clock,
) : MerchantRuleRepository {
    private val dao = db.merchantRuleDao()

    override fun observeAll(): Flow<List<MerchantRule>> = dao.observeAll().map { l -> l.map { it.toModel() } }

    override suspend fun getAll(): List<MerchantRule> = dao.getAll().map { it.toModel() }

    override suspend fun add(merchantPattern: String, categoryId: Long, applyToExisting: Boolean): AddRuleResult {
        val pattern = requireNotNull(TextNorm.normalize(merchantPattern)) { "merchantPattern must not be blank" }
        return db.withTransaction {
            val now = clock.instant()
            val existing = dao.getByPattern(pattern)
            val ruleId = if (existing != null) {
                dao.update(existing.copy(categoryId = categoryId, createdAt = now))
                existing.id
            } else {
                dao.insert(MerchantRuleEntity(merchantPattern = pattern, categoryId = categoryId, createdAt = now))
            }
            val updated = if (applyToExisting) {
                db.transactionDao().applyMerchantCategory(TextNorm.containsLike(pattern), categoryId, now.toEpochMilli())
            } else {
                0
            }
            AddRuleResult(ruleId, updated)
        }
    }

    override suspend fun delete(id: Long) {
        dao.deleteById(id)
    }
}

// ---------------------------------------------------------------- Карты

/** Карты, встреченные в сообщениях или добавленные вручную. */
interface CardRepository {
    /** Все карты: сначала свои. */
    fun observeAll(): Flow<List<Card>>

    /**
     * Пометить карту своей / не своей (создаёт карту, если её ещё нет). Переводы с участием
     * карты пересчитываются: перевод на свою карту — `INTERNAL_TRANSFER`, иначе исходное
     * направление парсера. Не трогаются только операции, где пользователь сам сменил
     * направление (оно расходится с вычисленным по прежнему списку своих карт); смена категории,
     * мерчанта или заметки пересчёту не мешает.
     */
    suspend fun setOwn(mask: String, isOwn: Boolean)

    /** Подпись карты («Зарплатная»); `null` — убрать. Создаёт карту, если её нет. */
    suspend fun setLabel(mask: String, label: String?)
}

@Singleton
class CardRepositoryImpl @Inject constructor(
    private val db: FoldFinanceDatabase,
    @DataClock private val clock: Clock,
) : CardRepository {
    private val dao = db.cardDao()

    override fun observeAll(): Flow<List<Card>> = dao.observeAll().map { l -> l.map { it.toModel() } }

    override suspend fun setOwn(mask: String, isOwn: Boolean) {
        db.withTransaction {
            val ownBefore = dao.getOwnMasks().toSet()
            val current = dao.get(mask)
            dao.upsert(current?.copy(isOwn = isOwn) ?: CardEntity(mask = mask, label = null, isOwn = isOwn))
            val own = dao.getOwnMasks().toSet()
            val txDao = db.transactionDao()
            val now = clock.millis()
            txDao.getTransfersInvolving(mask)
                .filterNot { it.userEdited && it.directionChangedByUser(ownBefore) }
                .forEach { tx ->
                    val direction = TransferClassifier.direction(tx.kind, tx.parsedDirection, tx.counterpartyCardMask, own)
                    if (direction != tx.direction) txDao.setDirection(tx.id, direction, now)
                }
        }
    }

    /**
     * Направление расходится с тем, что дало бы правило по списку своих карт [ownMasks] до
     * изменения, — значит, его выставил пользователь в редакторе.
     */
    private fun TransactionEntity.directionChangedByUser(ownMasks: Set<String>): Boolean =
        direction != TransferClassifier.direction(kind, parsedDirection, counterpartyCardMask, ownMasks)

    override suspend fun setLabel(mask: String, label: String?) {
        db.withTransaction {
            val current = dao.get(mask)
            dao.upsert(current?.copy(label = label) ?: CardEntity(mask = mask, label = label, isOwn = false))
        }
    }
}

// ---------------------------------------------------------------- Остатки

/**
 * Известные банковские остатки из фраз «Баланс/Остаток». Это значение банка на момент
 * сообщения ([KnownBalance.asOf]), а не сумма по операциям.
 */
interface BalanceRepository {
    /** Последний известный остаток по каждой карте, по маске. */
    fun observeLatest(): Flow<List<KnownBalance>>

    fun observeLatest(cardMask: String): Flow<KnownBalance?>

    /**
     * История известных остатков карты [cardMask], новые сверху (по [KnownBalance.asOf]). Каждая
     * запись — значение банка из одного сообщения; пусто — банк остаток по карте не присылал.
     */
    fun observeHistory(cardMask: String): Flow<List<KnownBalance>>
}

@Singleton
class BalanceRepositoryImpl @Inject constructor(db: FoldFinanceDatabase) : BalanceRepository {
    private val dao = db.knownBalanceDao()

    override fun observeLatest(): Flow<List<KnownBalance>> = dao.observeLatestPerCard().map { l -> l.map { it.toModel() } }

    override fun observeLatest(cardMask: String): Flow<KnownBalance?> = dao.observeLatest(cardMask).map { it?.toModel() }

    override fun observeHistory(cardMask: String): Flow<List<KnownBalance>> =
        dao.observeHistory(cardMask).map { l -> l.map { it.toModel() } }
}

// ---------------------------------------------------------------- Подписки

/** Регулярные платежи (этап 8): CRUD и статус кандидата. */
interface SubscriptionRepository {
    /** Все подписки по ближайшему списанию. */
    fun observeAll(): Flow<List<Subscription>>

    fun observeByStatus(status: SubscriptionStatus): Flow<List<Subscription>>

    fun observeById(id: Long): Flow<Subscription?>

    /** Создать (`id == 0`) или обновить подписку. @return id. */
    suspend fun upsert(subscription: Subscription): Long

    suspend fun setStatus(id: Long, status: SubscriptionStatus)

    suspend fun delete(id: Long)
}

@Singleton
class SubscriptionRepositoryImpl @Inject constructor(db: FoldFinanceDatabase) : SubscriptionRepository {
    private val dao = db.subscriptionDao()

    override fun observeAll(): Flow<List<Subscription>> = dao.observeAll().map { l -> l.map { it.toModel() } }

    override fun observeByStatus(status: SubscriptionStatus): Flow<List<Subscription>> =
        dao.observeByStatus(status).map { l -> l.map { it.toModel() } }

    override fun observeById(id: Long): Flow<Subscription?> = dao.observeById(id).map { it?.toModel() }

    override suspend fun upsert(subscription: Subscription): Long {
        require(subscription.amountMinor > 0) { "amountMinor must be positive" }
        require(subscription.currency.length == 3) { "currency must be ISO 4217" }
        return if (subscription.id == 0L) {
            dao.insert(subscription.toEntity())
        } else {
            check(dao.update(subscription.toEntity()) == 1) { "subscription ${subscription.id} not found" }
            subscription.id
        }
    }

    override suspend fun setStatus(id: Long, status: SubscriptionStatus) {
        dao.setStatus(id, status)
    }

    override suspend fun delete(id: Long) {
        dao.deleteById(id)
    }
}
