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
import com.foldfinance.core.data.db.dao.MerchantRuleDao
import com.foldfinance.core.data.db.dao.RawMessageDao
import com.foldfinance.core.data.db.dao.SubscriptionDao
import com.foldfinance.core.data.db.dao.TransactionDao
import com.foldfinance.core.data.db.entity.CardEntity
import com.foldfinance.core.data.db.entity.CategoryEntity
import com.foldfinance.core.data.db.entity.KnownBalanceEntity
import com.foldfinance.core.data.db.entity.MerchantRuleEntity
import com.foldfinance.core.data.db.entity.RawMessageEntity
import com.foldfinance.core.data.db.entity.SubscriptionEntity
import com.foldfinance.core.data.db.entity.TransactionEntity

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

    companion object {
        const val VERSION: Int = 2
        const val NAME: String = "foldfinance.db"

        /** v2: `transactions.review_confirmed` — подтверждение пользователя переживает перепарсинг. */
        val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            private val sql = "ALTER TABLE transactions ADD COLUMN review_confirmed INTEGER NOT NULL DEFAULT 0"

            override fun migrate(db: SupportSQLiteDatabase) = db.execSQL(sql)

            override fun migrate(connection: SQLiteConnection) = connection.execSQL(sql)
        }

        /** Все миграции по порядку версий. */
        val MIGRATIONS: Array<Migration> = arrayOf(MIGRATION_1_2)

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
