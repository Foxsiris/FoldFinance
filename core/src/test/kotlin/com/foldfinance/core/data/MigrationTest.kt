package com.foldfinance.core.data

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.foldfinance.core.data.db.FoldFinanceDatabase
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Миграции по экспортированным схемам (`core/schemas`): v1 открывается, v1 → v2 сохраняет данные.
 *
 * Используется вариант helper'а с [AndroidSQLiteDriver]: вариант с `SupportSQLiteOpenHelper`
 * в Room 2.8 под Robolectric на Windows не совпадает по пути файла БД.
 */
@RunWith(RobolectricTestRunner::class)
class MigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        instrumentation = InstrumentationRegistry.getInstrumentation(),
        file = ApplicationProvider.getApplicationContext<android.content.Context>().getDatabasePath("migration-test.db"),
        driver = AndroidSQLiteDriver(),
        databaseClass = FoldFinanceDatabase::class,
    )

    @Test
    fun migration1to2_addsReviewConfirmed_andKeepsData() {
        helper.createDatabase(1).apply {
            prepare(
                """
                INSERT INTO transactions (id, occurred_at, amount_minor, currency, direction, parsed_direction, kind,
                    confidence, needs_review, source, dedup_key, search_text, user_edited, op_index, created_at, updated_at)
                VALUES (1, 1000, 150000, 'RUB', 'EXPENSE', 'EXPENSE', 'PURCHASE', 0.5, 0, 'SMS', 'SMS:42#0', '', 0, 0, 1000, 1000)
                """.trimIndent(),
            ).use { it.step() }
            close()
        }

        val connection = helper.runMigrationsAndValidate(2, listOf(FoldFinanceDatabase.MIGRATION_1_2))
        try {
            connection.prepare("SELECT amount_minor, dedup_key, review_confirmed FROM transactions WHERE id = 1").use { stmt ->
                assertThat(stmt.step()).isTrue()
                assertThat(stmt.getLong(0)).isEqualTo(150_000L)
                assertThat(stmt.getText(1)).isEqualTo("SMS:42#0")
                assertThat(stmt.getLong(2)).isEqualTo(0L)
            }
        } finally {
            connection.close()
        }
    }

    @Test
    fun migration2to3_addsAiColumnsAndAliasTable_andKeepsData() {
        helper.createDatabase(2).apply {
            prepare("INSERT INTO categories (id, `key`, name, icon_key, is_system, sort_order) VALUES (7, 'other', 'Другое', 'category', 1, 9)")
                .use { it.step() }
            prepare(
                """
                INSERT INTO transactions (id, occurred_at, amount_minor, currency, direction, parsed_direction, kind,
                    merchant, confidence, needs_review, source, dedup_key, search_text, user_edited, op_index,
                    created_at, updated_at, review_confirmed, category_id)
                VALUES (1, 1000, 150000, 'RUB', 'EXPENSE', 'EXPENSE', 'PURCHASE', 'PYATEROCHKA 1234', 0.9, 0, 'SMS',
                    'SMS:42#0', 'pyaterochka 1234', 0, 0, 1000, 1000, 1, 7)
                """.trimIndent(),
            ).use { it.step() }
            prepare(
                """
                INSERT INTO raw_messages (id, channel, sender, source_key, received_at, text, text_hash, parse_status,
                    parser_version, parser_id, transaction_id, user_deleted)
                VALUES (3, 'SMS', '900', '42', 1000, 'Покупка 1500р', 'h', 'PARSED', 2, 'sber', 1, 0)
                """.trimIndent(),
            ).use { it.step() }
            close()
        }

        val connection = helper.runMigrationsAndValidate(3, listOf(FoldFinanceDatabase.MIGRATION_2_3))
        try {
            connection.prepare(
                "SELECT amount_minor, merchant, review_confirmed, ai_parsed, ai_category, category_id FROM transactions WHERE id = 1",
            ).use { stmt ->
                assertThat(stmt.step()).isTrue()
                assertThat(stmt.getLong(0)).isEqualTo(150_000L)
                assertThat(stmt.getText(1)).isEqualTo("PYATEROCHKA 1234")
                assertThat(stmt.getLong(2)).isEqualTo(1L)
                assertThat(stmt.getLong(3)).isEqualTo(0L)
                assertThat(stmt.getLong(4)).isEqualTo(0L)
                assertThat(stmt.getLong(5)).isEqualTo(7L)
            }
            connection.prepare("SELECT parser_id, ai_attempted_at FROM raw_messages WHERE id = 3").use { stmt ->
                assertThat(stmt.step()).isTrue()
                assertThat(stmt.getText(0)).isEqualTo("sber")
                assertThat(stmt.isNull(1)).isTrue()
            }
            // Новая таблица пуста и работает, внешний ключ на категории — SET NULL.
            connection.prepare(
                "INSERT INTO merchant_alias (merchant_key, ai_name, ai_category_id, updated_at) VALUES ('piaterochka', 'Пятёрочка', 7, 1)",
            ).use { it.step() }
            connection.prepare("PRAGMA foreign_keys = ON").use { it.step() }
            connection.prepare("DELETE FROM categories WHERE id = 7").use { it.step() }
            connection.prepare("SELECT ai_name, ai_category_id, category_confirmed FROM merchant_alias").use { stmt ->
                assertThat(stmt.step()).isTrue()
                assertThat(stmt.getText(0)).isEqualTo("Пятёрочка")
                assertThat(stmt.isNull(1)).isTrue()
                assertThat(stmt.getLong(2)).isEqualTo(0L)
            }
        } finally {
            connection.close()
        }
    }

    @Test
    fun schemaV1_opens() {
        val connection = helper.createDatabase(1)
        try {
            val tables = connection.prepare("SELECT name FROM sqlite_master WHERE type = 'table'").use { stmt ->
                buildList { while (stmt.step()) add(stmt.getText(0)) }
            }
            assertThat(tables).containsAtLeast(
                "transactions", "raw_messages", "categories", "merchant_rules",
                "cards", "known_balances", "subscriptions",
            )
            val version = connection.prepare("PRAGMA user_version").use { stmt -> stmt.step(); stmt.getLong(0) }
            assertThat(version).isEqualTo(1L)
        } finally {
            connection.close()
        }
    }
}
