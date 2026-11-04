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
 * Заготовка тестов миграций: экспортированная схема v1 (`core/schemas`) открывается.
 * Для v2: создать v1 с данными, затем `helper.runMigrationsAndValidate(2, listOf(MIGRATION_1_2))`.
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
            assertThat(version).isEqualTo(FoldFinanceDatabase.VERSION.toLong())
        } finally {
            connection.close()
        }
    }
}
