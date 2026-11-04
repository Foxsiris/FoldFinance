package com.foldfinance.core.data

import com.foldfinance.core.data.db.CategorySeeder
import com.foldfinance.core.data.db.FoldFinanceDatabase
import com.foldfinance.core.data.ingest.SourceKeys
import com.foldfinance.core.data.repository.CategoryRepositoryImpl
import com.foldfinance.core.data.repository.SubscriptionRepositoryImpl
import com.foldfinance.core.model.DefaultCategories
import com.foldfinance.core.model.Subscription
import com.foldfinance.core.model.SubscriptionPeriod
import com.foldfinance.core.model.SubscriptionStatus
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
class ReferenceDataTest {

    private lateinit var db: FoldFinanceDatabase

    @Before fun setUp() { db = inMemoryDb() }
    @After fun tearDown() { db.close() }

    @Test
    fun defaultCategories_seededOnCreate_andSeedingIsIdempotent() = runTest {
        val repo = CategoryRepositoryImpl(db)
        val initial = repo.observeAll().first()
        assertThat(initial.map { it.key }).containsExactlyElementsIn(DefaultCategories.all.map { it.key }).inOrder()
        assertThat(initial.all { it.isSystem }).isTrue()

        val other = initial.first { it.key == DefaultCategories.OTHER_KEY }
        repo.rename(other.id, "Прочее")

        repo.ensureDefaults()
        repo.ensureDefaults()
        CategorySeeder.seed(db.openHelper.writableDatabase)

        val after = repo.getAll()
        assertThat(after).hasSize(DefaultCategories.all.size)
        assertThat(after.first { it.id == other.id }.name).isEqualTo("Прочее")
    }

    @Test
    fun userCategory_addedToEnd_withIconAndColor() = runTest {
        val repo = CategoryRepositoryImpl(db)
        val id = repo.add("  Путешествия ", "flight")
        repo.setIcon(id, "luggage")
        repo.setColor(id, 0xFF00AA00.toInt())

        val category = repo.observeById(id).first()!!
        assertThat(category.name).isEqualTo("Путешествия")
        assertThat(category.iconKey).isEqualTo("luggage")
        assertThat(category.colorArgb).isEqualTo(0xFF00AA00.toInt())
        assertThat(category.isSystem).isFalse()
        assertThat(category.sortOrder).isEqualTo(DefaultCategories.all.size)
    }

    @Test
    fun subscriptions_crudAndStatus() = runTest {
        val repo = SubscriptionRepositoryImpl(db)
        val id = repo.upsert(
            Subscription(
                merchant = "Музыка",
                amountMinor = 299_00,
                currency = "RUB",
                period = SubscriptionPeriod.MONTHLY,
                lastChargeAt = T0,
                nextChargeAt = T0.plus(Duration.ofDays(30)),
                status = SubscriptionStatus.CANDIDATE,
            ),
        )
        repo.setStatus(id, SubscriptionStatus.CONFIRMED)
        assertThat(repo.observeByStatus(SubscriptionStatus.CONFIRMED).first().single().id).isEqualTo(id)

        val current = repo.observeById(id).first()!!
        repo.upsert(current.copy(amountMinor = 349_00))
        assertThat(repo.observeById(id).first()!!.amountMinor).isEqualTo(349_00)

        repo.delete(id)
        assertThat(repo.observeAll().first()).isEmpty()
    }

    @Test
    fun sourceKeys_areDeterministic() {
        assertThat(SourceKeys.sms(42)).isEqualTo("42")
        assertThat(SourceKeys.sha256Hex("abc"))
            .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad")
        assertThat(SourceKeys.push("0|ru.bank|1|null|10001", 1_700_000_000_000, "abc"))
            .isEqualTo("0|ru.bank|1|null|10001|1700000000000|ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad")
    }
}
