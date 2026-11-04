package com.foldfinance.app.widget

import android.content.Context
import com.foldfinance.app.prefs.AppPreferences
import com.foldfinance.core.data.repository.TransactionFilter
import com.foldfinance.core.data.repository.TransactionRepository
import com.foldfinance.core.domain.analytics.AnalyticsCalculator
import com.foldfinance.core.domain.analytics.Period
import com.foldfinance.core.domain.analytics.PeriodType
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first
import java.time.Clock

/**
 * Что показывает виджет. Суммы — нетто-расходы в основной валюте по известным операциям
 * ([AnalyticsCalculator.todayMonth]).
 *
 * @property hideAmounts пользователь включил «Скрывать суммы в виджете».
 */
data class FinanceWidgetData(
    val currency: String,
    val todayNetExpenseMinor: Long,
    val monthNetExpenseMinor: Long,
    val needsReviewCount: Int,
    val hideAmounts: Boolean,
)

/** Источник данных виджета; в тестах и превью подменяется. */
fun interface FinanceWidgetDataLoader {
    suspend fun load(context: Context): FinanceWidgetData
}

/** Зависимости виджета из графа Hilt (у `GlanceAppWidget` нет инъекции в конструктор). */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface FinanceWidgetEntryPoint {
    fun transactionRepository(): TransactionRepository
    fun appPreferences(): AppPreferences
}

/**
 * Данные из БД: операции текущего месяца (в часовом поясе телефона) + число операций на проверку
 * за всё время.
 */
class DatabaseWidgetDataLoader(private val clock: Clock = Clock.systemDefaultZone()) : FinanceWidgetDataLoader {
    override suspend fun load(context: Context): FinanceWidgetData {
        val entryPoint = EntryPointAccessors.fromApplication(context.applicationContext, FinanceWidgetEntryPoint::class.java)
        val settings = entryPoint.appPreferences().current()
        val repository = entryPoint.transactionRepository()
        val now = clock.instant()
        val zone = clock.zone
        val month = Period.containing(PeriodType.MONTH, now, zone)
        val transactions = repository
            .observe(TransactionFilter(from = month.start, to = month.endExclusive, currency = settings.defaultCurrency))
            .first()
        val summary = AnalyticsCalculator.todayMonth(transactions, settings.defaultCurrency, now, zone)
        return FinanceWidgetData(
            currency = summary.currency,
            todayNetExpenseMinor = summary.todayNetExpenseMinor,
            monthNetExpenseMinor = summary.monthNetExpenseMinor,
            needsReviewCount = repository.observeReviewCount().first(),
            hideAmounts = settings.hideWidgetAmounts,
        )
    }
}
