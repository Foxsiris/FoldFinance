package com.foldfinance.core.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import com.foldfinance.core.data.db.FoldFinanceDatabase
import com.foldfinance.core.data.db.dao.CardDao
import com.foldfinance.core.data.db.dao.CategoryDao
import com.foldfinance.core.data.db.dao.KnownBalanceDao
import com.foldfinance.core.data.db.dao.MerchantAliasDao
import com.foldfinance.core.data.db.dao.MerchantRuleDao
import com.foldfinance.core.data.db.dao.RawMessageDao
import com.foldfinance.core.data.db.dao.SubscriptionDao
import com.foldfinance.core.data.db.dao.TransactionDao
import com.foldfinance.core.data.db.dao.TransactionSplitDao
import com.foldfinance.core.data.ingest.IngestObserver
import com.foldfinance.core.data.ingest.IngestRepository
import com.foldfinance.core.data.ingest.IngestRepositoryImpl
import com.foldfinance.core.data.repository.BalanceRepository
import com.foldfinance.core.data.repository.BalanceRepositoryImpl
import com.foldfinance.core.data.repository.CardRepository
import com.foldfinance.core.data.repository.CardRepositoryImpl
import com.foldfinance.core.data.repository.CategoryRepository
import com.foldfinance.core.data.repository.CategoryRepositoryImpl
import com.foldfinance.core.data.repository.MerchantAliasRepository
import com.foldfinance.core.data.repository.MerchantAliasRepositoryImpl
import com.foldfinance.core.data.repository.MerchantRuleRepository
import com.foldfinance.core.data.repository.MerchantRuleRepositoryImpl
import com.foldfinance.core.data.repository.SubscriptionRepository
import com.foldfinance.core.data.repository.SubscriptionRepositoryImpl
import com.foldfinance.core.data.repository.TransactionRepository
import com.foldfinance.core.data.repository.TransactionRepositoryImpl
import com.foldfinance.core.data.repository.TransactionSplitRepository
import com.foldfinance.core.data.repository.TransactionSplitRepositoryImpl
import com.foldfinance.core.data.repository.UnrecognizedMessagesRepository
import com.foldfinance.core.data.repository.UnrecognizedMessagesRepositoryImpl
import com.foldfinance.core.data.security.DatabaseKeyManager
import com.foldfinance.core.data.settings.IngestSettingsStore
import com.foldfinance.parser.ParserRegistry
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.Multibinds
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import java.io.File
import java.time.Clock
import javax.inject.Qualifier
import javax.inject.Singleton

/** Часы слоя данных (`createdAt`/`updatedAt`); в тестах подменяются фиксированными. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DataClock

/**
 * Создание зашифрованной БД. Это единственное место, знающее про SQLCipher и Keystore:
 * в инструментальных/Robolectric-тестах `:app` модуль заменяется через
 * `@TestInstallIn(components = [SingletonComponent::class], replaces = [DatabaseModule::class])`
 * на in-memory `FoldFinanceDatabase.build(context, openHelperFactory = null, name = null)`.
 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabaseKeyManager(@ApplicationContext context: Context): DatabaseKeyManager =
        DatabaseKeyManager(File(context.noBackupFilesDir, DatabaseKeyManager.KEY_FILE_NAME))

    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context,
        keyManager: DatabaseKeyManager,
    ): FoldFinanceDatabase {
        System.loadLibrary("sqlcipher")
        val factory = SupportOpenHelperFactory(keyManager.getOrCreateKey())
        return FoldFinanceDatabase.build(context, factory, FoldFinanceDatabase.NAME)
    }
}

/** DAO из БД — не зависят от способа её создания. */
@Module
@InstallIn(SingletonComponent::class)
object DaoModule {
    @Provides fun transactionDao(db: FoldFinanceDatabase): TransactionDao = db.transactionDao()
    @Provides fun rawMessageDao(db: FoldFinanceDatabase): RawMessageDao = db.rawMessageDao()
    @Provides fun categoryDao(db: FoldFinanceDatabase): CategoryDao = db.categoryDao()
    @Provides fun merchantRuleDao(db: FoldFinanceDatabase): MerchantRuleDao = db.merchantRuleDao()
    @Provides fun cardDao(db: FoldFinanceDatabase): CardDao = db.cardDao()
    @Provides fun knownBalanceDao(db: FoldFinanceDatabase): KnownBalanceDao = db.knownBalanceDao()
    @Provides fun subscriptionDao(db: FoldFinanceDatabase): SubscriptionDao = db.subscriptionDao()
    @Provides fun merchantAliasDao(db: FoldFinanceDatabase): MerchantAliasDao = db.merchantAliasDao()
    @Provides fun transactionSplitDao(db: FoldFinanceDatabase): TransactionSplitDao = db.transactionSplitDao()
}

@Module
@InstallIn(SingletonComponent::class)
object DataSupportModule {

    @Provides
    @DataClock
    fun provideClock(): Clock = Clock.systemUTC()

    /** Реестр парсеров `:parser`; в тестах ингеста подставляется фейковый. */
    @Provides
    @Singleton
    fun provideParserRegistry(): ParserRegistry = ParserRegistry.default()

    @Provides
    @Singleton
    @IngestSettingsStore
    fun provideIngestSettingsStore(@ApplicationContext context: Context): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(produceFile = { context.preferencesDataStoreFile("ingest_settings") })
}

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds abstract fun transactions(impl: TransactionRepositoryImpl): TransactionRepository
    @Binds abstract fun categories(impl: CategoryRepositoryImpl): CategoryRepository
    @Binds abstract fun merchantRules(impl: MerchantRuleRepositoryImpl): MerchantRuleRepository
    @Binds abstract fun cards(impl: CardRepositoryImpl): CardRepository
    @Binds abstract fun balances(impl: BalanceRepositoryImpl): BalanceRepository
    @Binds abstract fun subscriptions(impl: SubscriptionRepositoryImpl): SubscriptionRepository
    @Binds abstract fun ingest(impl: IngestRepositoryImpl): IngestRepository
    @Binds abstract fun unrecognized(impl: UnrecognizedMessagesRepositoryImpl): UnrecognizedMessagesRepository
    @Binds abstract fun merchantAliases(impl: MerchantAliasRepositoryImpl): MerchantAliasRepository
    @Binds abstract fun transactionSplits(impl: TransactionSplitRepositoryImpl): TransactionSplitRepository

    /** Наблюдатели приёма (`:app` добавляет запуск дообработки локальной моделью); может быть пусто. */
    @Multibinds abstract fun ingestObservers(): Set<IngestObserver>
}
