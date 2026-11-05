package com.foldfinance.app.widget

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.foldfinance.app.prefs.AppPreferences
import com.foldfinance.core.data.repository.TransactionFilter
import com.foldfinance.core.data.repository.TransactionRepository
import com.foldfinance.core.domain.analytics.AnalyticsCalculator
import com.foldfinance.core.domain.analytics.Period
import com.foldfinance.core.domain.analytics.PeriodType
import com.foldfinance.core.model.Transaction
import com.foldfinance.parser.Direction
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first
import java.time.Clock
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId

/**
 * Что показывают виджеты. Суммы — в основной валюте по известным операциям: нетто-расходы
 * ([AnalyticsCalculator.todayMonth]) и доход за месяц; переводы между своими счетами не считаются.
 *
 * @property month текущий месяц в часовом поясе телефона.
 * @property todayOfMonth номер сегодняшнего дня в [month], с 1.
 * @property monthIncomeMinor доход за месяц (сумма операций INCOME).
 * @property dailyNetExpenseMinor нетто-расходы по дням месяца: индекс 0 — 1-е число, размер —
 *   длина месяца; дни после сегодняшнего — 0. Значение бывает отрицательным (возвратов больше).
 * @property hideAmounts пользователь включил «Скрывать суммы в виджете».
 * @property hasTransactions в базе есть хотя бы одна операция; `false` — пустое состояние.
 * @property smsAccess доступ к SMS выдан (подсказка в пустом состоянии).
 */
data class FinanceWidgetData(
    val currency: String,
    val month: YearMonth,
    val todayOfMonth: Int,
    val todayNetExpenseMinor: Long,
    val monthNetExpenseMinor: Long,
    val monthIncomeMinor: Long,
    val dailyNetExpenseMinor: List<Long>,
    val needsReviewCount: Int,
    val hideAmounts: Boolean,
    val hasTransactions: Boolean,
    val smsAccess: Boolean,
) {
    init {
        require(dailyNetExpenseMinor.size == month.lengthOfMonth()) { "one value per day of month" }
        require(todayOfMonth in 1..month.lengthOfMonth()) { "today must be inside the month" }
    }

    companion object {
        /**
         * Сводка из операций [transactions] (любые валюты, лишние отфильтруются) на момент [now].
         * Чистая функция: вся арифметика виджета здесь, чтобы её можно было проверить тестом.
         */
        fun summarize(
            transactions: List<Transaction>,
            currency: String,
            now: Instant,
            zone: ZoneId,
            needsReviewCount: Int,
            hideAmounts: Boolean,
            hasTransactions: Boolean,
            smsAccess: Boolean,
        ): FinanceWidgetData {
            val period = Period.containing(PeriodType.MONTH, now, zone)
            val today = now.atZone(zone).toLocalDate()
            val month = YearMonth.from(today)
            val summary = AnalyticsCalculator.todayMonth(transactions, currency, now, zone)
            val counted = transactions.filter {
                it.currency == currency && it.direction != Direction.INTERNAL_TRANSFER && it.occurredAt in period
            }
            val daily = LongArray(month.lengthOfMonth())
            for (tx in counted) {
                val day = period.dateOf(tx.occurredAt).dayOfMonth
                daily[day - 1] += AnalyticsCalculator.netExpenseContribution(tx)
            }
            return FinanceWidgetData(
                currency = currency,
                month = month,
                todayOfMonth = today.dayOfMonth,
                todayNetExpenseMinor = summary.todayNetExpenseMinor,
                monthNetExpenseMinor = summary.monthNetExpenseMinor,
                monthIncomeMinor = counted.filter { it.direction == Direction.INCOME }.sumOf { it.amountMinor },
                dailyNetExpenseMinor = daily.toList(),
                needsReviewCount = needsReviewCount,
                hideAmounts = hideAmounts,
                hasTransactions = hasTransactions,
                smsAccess = smsAccess,
            )
        }
    }
}

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
 * за всё время. Полная история читается только когда месяц пуст — чтобы отличить «новый месяц»
 * от «ещё ни одной операции».
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
        val reviewCount = repository.observeReviewCount().first()
        val hasTransactions = transactions.isNotEmpty() || reviewCount > 0 ||
            repository.observe(TransactionFilter()).first().isNotEmpty()
        return FinanceWidgetData.summarize(
            transactions = transactions,
            currency = settings.defaultCurrency,
            now = now,
            zone = zone,
            needsReviewCount = reviewCount,
            hideAmounts = settings.hideWidgetAmounts,
            hasTransactions = hasTransactions,
            smsAccess = hasSmsAccess(context),
        )
    }

    private fun hasSmsAccess(context: Context): Boolean =
        listOf(Manifest.permission.READ_SMS, Manifest.permission.RECEIVE_SMS).all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
}
