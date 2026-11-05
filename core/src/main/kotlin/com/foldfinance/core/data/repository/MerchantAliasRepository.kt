package com.foldfinance.core.data.repository

import androidx.room.withTransaction
import com.foldfinance.core.data.db.FoldFinanceDatabase
import com.foldfinance.core.data.db.entity.MerchantAliasEntity
import com.foldfinance.core.data.db.toModel
import com.foldfinance.core.di.DataClock
import com.foldfinance.core.domain.category.MerchantNormalizer
import com.foldfinance.core.model.MerchantAlias
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Отображаемые имена мерчантов (`YANDEX*GO*TAXI MOSCOW` → «Яндекс Go»).
 *
 * Имя предлагает локальная модель (один раз на мерчанта, фоновая задача), пользователь может
 * переименовать вручную — его имя всегда приоритетнее и моделью не перезаписывается. Сырое название
 * в операциях не меняется: поиск и правила категорий работают по нему, имя применяется при чтении
 * (`Transaction.merchantDisplayName` / `displayMerchant`).
 */
interface MerchantAliasRepository {
    fun observeAll(): Flow<List<MerchantAlias>>

    /** Алиас для сырого названия мерчанта (как в `Transaction.merchant`); `null` — нет. */
    fun observeFor(merchant: String?): Flow<MerchantAlias?>

    /**
     * Переименовать мерчанта для показа.
     *
     * @param merchant сырое название, как в операции.
     * @param displayName новое имя; `null` или пустое — вернуть имя модели (или сырое название).
     * @return `false`, если название пустое после нормализации (переименовывать нечего).
     */
    suspend fun rename(merchant: String, displayName: String?): Boolean
}

@Singleton
class MerchantAliasRepositoryImpl @Inject constructor(
    private val db: FoldFinanceDatabase,
    @DataClock private val clock: Clock,
) : MerchantAliasRepository {
    private val dao = db.merchantAliasDao()

    override fun observeAll(): Flow<List<MerchantAlias>> = dao.observeAll().map { l -> l.map { it.toModel() } }

    override fun observeFor(merchant: String?): Flow<MerchantAlias?> {
        val key = MerchantNormalizer.canonical(merchant)
        if (key.isEmpty()) return flowOf(null)
        return dao.observe(key).map { it?.toModel() }
    }

    override suspend fun rename(merchant: String, displayName: String?): Boolean {
        val key = MerchantNormalizer.canonical(merchant)
        if (key.isEmpty()) return false
        val name = displayName?.trim()?.take(MAX_NAME_LENGTH)?.ifEmpty { null }
        db.withTransaction {
            val now = clock.instant()
            val existing = dao.get(key)
            if (existing != null) {
                dao.upsert(existing.copy(userName = name, updatedAt = now))
            } else if (name != null) {
                // Модель по этому мерчанту ещё не спрашивали (aiCheckedAt = null) — спросит фоновая задача,
                // но имя пользователя не тронет.
                dao.upsert(
                    MerchantAliasEntity(
                        merchantKey = key,
                        aiName = null,
                        userName = name,
                        aiCategoryId = null,
                        aiCheckedAt = null,
                        updatedAt = now,
                    ),
                )
            }
        }
        return true
    }

    companion object {
        const val MAX_NAME_LENGTH: Int = 48
    }
}
