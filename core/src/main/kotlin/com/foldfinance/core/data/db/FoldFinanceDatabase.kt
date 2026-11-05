package com.foldfinance.core.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.execSQL
import androidx.sqlite.db.SupportSQLiteOpenHelper
import com.foldfinance.core.data.db.dao.CardDao
import com.foldfinance.core.data.db.dao.CategoryDao
import com.foldfinance.core.data.db.dao.KnownBalanceDao
import com.foldfinance.core.data.db.dao.MerchantAliasDao
import com.foldfinance.core.data.db.dao.MerchantRuleDao
import com.foldfinance.core.data.db.dao.RawMessageDao
import com.foldfinance.core.data.db.dao.SubscriptionDao
import com.foldfinance.core.data.db.dao.TransactionDao
import com.foldfinance.core.data.db.dao.TransactionSplitDao
import com.foldfinance.core.data.db.entity.CardEntity
import com.foldfinance.core.data.db.entity.CategoryEntity
import com.foldfinance.core.data.db.entity.KnownBalanceEntity
import com.foldfinance.core.data.db.entity.MerchantAliasEntity
import com.foldfinance.core.data.db.entity.MerchantRuleEntity
import com.foldfinance.core.data.db.entity.RawMessageEntity
import com.foldfinance.core.data.db.entity.SubscriptionEntity
import com.foldfinance.core.data.db.entity.TransactionEntity
import com.foldfinance.core.data.db.entity.TransactionSplitEntity

/**
 * Локальная БД приложения. На устройстве открывается через SQLCipher
 * (см. [com.foldfinance.core.di.DatabaseFactoryModule]); в тестах — in-memory без шифрования.
 */
@Database(
    entities = [
        TransactionEntity::class,
        RawMessageEntity::class,
        CategoryEntity::class,
        MerchantRuleEntity::class,
        CardEntity::class,
        KnownBalanceEntity::class,
        SubscriptionEntity::class,
        MerchantAliasEntity::class,
        TransactionSplitEntity::class,
    ],
    version = FoldFinanceDatabase.VERSION,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class FoldFinanceDatabase : RoomDatabase() {
    abstract fun transactionDao(): TransactionDao
    abstract fun rawMessageDao(): RawMessageDao
    abstract fun categoryDao(): CategoryDao
    abstract fun merchantRuleDao(): MerchantRuleDao
    abstract fun cardDao(): CardDao
    abstract fun knownBalanceDao(): KnownBalanceDao
    abstract fun subscriptionDao(): SubscriptionDao
    abstract fun merchantAliasDao(): MerchantAliasDao
    abstract fun transactionSplitDao(): TransactionSplitDao

    companion object {
        const val VERSION: Int = 4
        const val NAME: String = "foldfinance.db"

        /** v2: `transactions.review_confirmed` — подтверждение пользователя переживает перепарсинг. */
        val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            private val sql = "ALTER TABLE transactions ADD COLUMN review_confirmed INTEGER NOT NULL DEFAULT 0"

            override fun migrate(db: SupportSQLiteDatabase) = db.execSQL(sql)

            override fun migrate(connection: SQLiteConnection) = connection.execSQL(sql)
        }

        /**
         * v3 (локальный ИИ): таблица `merchant_alias` (понятные имена мерчантов и кэш ответов модели),
         * `transactions.ai_parsed` / `ai_category`, `raw_messages.ai_attempted_at`. Данные не меняются.
         */
        val MIGRATION_2_3: Migration = object : Migration(2, 3) {
            private val statements = listOf(
                "CREATE TABLE IF NOT EXISTS `merchant_alias` (`merchant_key` TEXT NOT NULL, `ai_name` TEXT, " +
                    "`user_name` TEXT, `ai_category_id` INTEGER, `category_confirmed` INTEGER NOT NULL DEFAULT 0, " +
                    "`ai_checked_at` INTEGER, `updated_at` INTEGER NOT NULL, PRIMARY KEY(`merchant_key`), " +
                    "FOREIGN KEY(`ai_category_id`) REFERENCES `categories`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL )",
                "CREATE INDEX IF NOT EXISTS `index_merchant_alias_ai_category_id` ON `merchant_alias` (`ai_category_id`)",
                "ALTER TABLE transactions ADD COLUMN ai_parsed INTEGER NOT NULL DEFAULT 0",
                "ALTER TABLE transactions ADD COLUMN ai_category INTEGER NOT NULL DEFAULT 0",
                "ALTER TABLE raw_messages ADD COLUMN ai_attempted_at INTEGER",
            )

            override fun migrate(db: SupportSQLiteDatabase) = statements.forEach { db.execSQL(it) }

            override fun migrate(connection: SQLiteConnection) = statements.forEach { connection.execSQL(it) }
        }

        /**
         * v4 (разбиение по чеку): таблица `transaction_split` — части операции по категориям.
         * Существующие операции не меняются.
         */
        val MIGRATION_3_4: Migration = object : Migration(3, 4) {
            private val statements = listOf(
                "CREATE TABLE IF NOT EXISTS `transaction_split` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`parent_id` INTEGER NOT NULL, `category_id` INTEGER, `amount_minor` INTEGER NOT NULL, `label` TEXT, " +
                    "FOREIGN KEY(`parent_id`) REFERENCES `transactions`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , " +
                    "FOREIGN KEY(`category_id`) REFERENCES `categories`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL )",
                "CREATE INDEX IF NOT EXISTS `index_transaction_split_parent_id` ON `transaction_split` (`parent_id`)",
                "CREATE INDEX IF NOT EXISTS `index_transaction_split_category_id` ON `transaction_split` (`category_id`)",
            )

            override fun migrate(db: SupportSQLiteDatabase) = statements.forEach { db.execSQL(it) }

            override fun migrate(connection: SQLiteConnection) = statements.forEach { connection.execSQL(it) }
        }

        /** Все миграции по порядку версий. */
        val MIGRATIONS: Array<Migration> = arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)

        /**
         * Собрать БД.
         *
         * @param openHelperFactory фабрика SQLite (SQLCipher на устройстве); `null` — стандартная.
         * @param name имя файла; `null` — in-memory (тесты).
         */
        fun build(
            context: Context,
            openHelperFactory: SupportSQLiteOpenHelper.Factory?,
            name: String? = NAME,
        ): FoldFinanceDatabase {
            val builder = if (name == null) {
                Room.inMemoryDatabaseBuilder(context, FoldFinanceDatabase::class.java)
            } else {
                Room.databaseBuilder(context, FoldFinanceDatabase::class.java, name)
            }
            if (openHelperFactory != null) builder.openHelperFactory(openHelperFactory)
            return builder.addMigrations(*MIGRATIONS).addCallback(SeedCallback).build()
        }
    }

    /** Сидирование базовых категорий при первом создании файла БД. */
    internal object SeedCallback : RoomDatabase.Callback() {
        override fun onCreate(db: SupportSQLiteDatabase) {
            CategorySeeder.seed(db)
        }
    }
}
