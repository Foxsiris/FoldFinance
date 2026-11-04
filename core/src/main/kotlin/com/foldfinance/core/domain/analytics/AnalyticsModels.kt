package com.foldfinance.core.domain.analytics

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

/**
 * Итоги за период. Суммы по каждой валюте отдельно, без пересчёта по курсам.
 *
 * @property byCurrency итоги по ISO-коду валюты; порядок — по убыванию нетто-расходов.
 * @property daysCounted число дней, по которому считается среднее в день
 *   (для текущего периода — прошедшие дни включая сегодняшний).
 * @property needsReviewCount операции периода, ожидающие проверки (во всех валютах).
 */
data class PeriodSummary(
    val period: Period,
    val byCurrency: Map<String, CurrencySummary>,
    val daysCounted: Int,
    val needsReviewCount: Int,
) {
    /** Итоги по валюте или пустые итоги, если операций в ней не было. */
    fun currency(code: String): CurrencySummary = byCurrency[code] ?: CurrencySummary.empty(code, period)

    /** Валюты, в которых были операции. */
    val currencies: Set<String> get() = byCurrency.keys
}

/**
 * Итоги одной валюты за период. Все суммы — в минорных единицах.
 *
 * Возврат ([com.foldfinance.parser.Direction.REFUND]) уменьшает расходы своей категории
 * и доходом не считается; внутренние переводы исключены полностью.
 *
 * @property expenseMinor валовые расходы (только EXPENSE).
 * @property refundMinor возвраты.
 * @property netExpenseMinor нетто-расходы = [expenseMinor] − [refundMinor]; может быть
 *   отрицательным, если возвращено больше, чем потрачено в этом периоде.
 * @property incomeMinor доходы (только INCOME).
 * @property balanceMinor [incomeMinor] − [netExpenseMinor] «по известным операциям».
 * @property operationCount число учтённых операций (без внутренних переводов).
 * @property categories разбивка по категориям, по убыванию нетто-расходов.
 * @property averageDailyNetExpenseMinor нетто-расходы / [PeriodSummary.daysCounted], округление HALF_UP.
 * @property daily ряд по каждому дню периода (для графика), включая дни без операций.
 */
data class CurrencySummary(
    val currency: String,
    val expenseMinor: Long,
    val refundMinor: Long,
    val netExpenseMinor: Long,
    val incomeMinor: Long,
    val balanceMinor: Long,
    val operationCount: Int,
    val categories: List<CategoryBreakdown>,
    val averageDailyNetExpenseMinor: Long,
    val daily: List<DailyPoint>,
) {
    /** Топ категорий с положительными нетто-расходами. */
    fun topCategories(limit: Int = 5): List<CategoryBreakdown> =
        categories.filter { it.netExpenseMinor > 0 }.take(limit)

    /**
     * Доходы / нетто-расходы (для отображения). `null`, если нетто-расходов нет.
     * Значение > 1 — доходы превышают расходы.
     */
    val incomeToExpenseRatio: Double?
        get() = if (netExpenseMinor <= 0) null else incomeMinor.toDouble() / netExpenseMinor.toDouble()

    /** Доля нетто-расходов от доходов, %. `null`, если доходов нет. */
    val expenseShareOfIncomePercent: Double?
        get() = if (incomeMinor <= 0) null else percentOf(netExpenseMinor, incomeMinor)

    companion object {
        /** Пустые итоги валюты с нулевым рядом по дням. */
        fun empty(currency: String, period: Period): CurrencySummary = CurrencySummary(
            currency = currency,
            expenseMinor = 0, refundMinor = 0, netExpenseMinor = 0, incomeMinor = 0, balanceMinor = 0,
            operationCount = 0,
            categories = emptyList(),
            averageDailyNetExpenseMinor = 0,
            daily = period.dates().map { DailyPoint(it, 0, 0, 0, 0) },
        )
    }
}

/**
 * Расходы одной категории.
 *
 * @property categoryId `null` — операции без категории.
 * @property sharePercent доля в нетто-расходах валюты, %; `null`, если нетто-расходы ≤ 0.
 */
data class CategoryBreakdown(
    val categoryId: Long?,
    val expenseMinor: Long,
    val refundMinor: Long,
    val netExpenseMinor: Long,
    val sharePercent: Double?,
)

/** Точка дневного ряда. [date] — дата в часовом поясе периода. */
data class DailyPoint(
    val date: LocalDate,
    val expenseMinor: Long,
    val refundMinor: Long,
    val netExpenseMinor: Long,
    val incomeMinor: Long,
)

/**
 * Изменение суммы относительно прошлого периода.
 *
 * @property deltaMinor [currentMinor] − [previousMinor].
 */
data class AmountChange(val currentMinor: Long, val previousMinor: Long) {
    val deltaMinor: Long get() = currentMinor - previousMinor

    /** Изменение в процентах, точно (2 знака, HALF_UP). `null`, если в прошлом периоде было 0. */
    val percentExact: BigDecimal?
        get() = if (previousMinor == 0L) null else
            BigDecimal.valueOf(deltaMinor).multiply(HUNDRED)
                .divide(BigDecimal.valueOf(previousMinor).abs(), 2, RoundingMode.HALF_UP)

    /** Изменение в процентах для отображения. `null`, если в прошлом периоде было 0. */
    val percent: Double? get() = percentExact?.toDouble()
}

/** Сравнение одной валюты с прошлым периодом. */
data class CurrencyComparison(
    val currency: String,
    val netExpense: AmountChange,
    val income: AmountChange,
    val refund: AmountChange,
    /** По категориям (объединение текущего и прошлого периодов), по убыванию текущих нетто-расходов. */
    val categories: Map<Long?, AmountChange>,
)

/** Сравнение двух периодов по всем валютам, встретившимся хотя бы в одном из них. */
data class PeriodComparison(
    val current: Period,
    val previous: Period,
    val byCurrency: Map<String, CurrencyComparison>,
)

/**
 * Сводка для внешнего экрана и виджета.
 *
 * @property todayNetExpenseMinor нетто-расходы за сегодня в [currency].
 * @property monthNetExpenseMinor нетто-расходы с начала месяца в [currency].
 * @property needsReviewCount все операции в очереди проверки (во всех валютах, за всё время).
 */
data class TodayMonthSummary(
    val currency: String,
    val todayNetExpenseMinor: Long,
    val monthNetExpenseMinor: Long,
    val needsReviewCount: Int,
)

private val HUNDRED: BigDecimal = BigDecimal.valueOf(100)

internal fun percentOf(part: Long, whole: Long): Double =
    BigDecimal.valueOf(part).multiply(HUNDRED)
        .divide(BigDecimal.valueOf(whole), 2, RoundingMode.HALF_UP)
        .toDouble()
