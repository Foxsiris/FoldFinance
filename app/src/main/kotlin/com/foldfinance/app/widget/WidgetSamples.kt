package com.foldfinance.app.widget

import java.time.YearMonth

/**
 * Образец для превью в списке виджетов (generated preview, Android 15+) — цифры из макета
 * Widgets.dc.html. Это картинка «как будет выглядеть», а не данные пользователя: превью
 * публикуется системе и видно до разблокировки приложения.
 */
internal object WidgetSamples {
    private val days = longArrayOf(6_420_00, 2_310_00, 9_870_00, 3_200_00, 1_840_00)

    fun finance(month: YearMonth = YearMonth.now()): FinanceWidgetData {
        val daily = LongArray(month.lengthOfMonth()).also { days.copyInto(it) }
        return FinanceWidgetData(
            currency = "RUB",
            month = month,
            todayOfMonth = days.size,
            todayNetExpenseMinor = 1_840_00,
            monthNetExpenseMinor = 23_640_00,
            monthIncomeMinor = 95_000_00,
            dailyNetExpenseMinor = daily.toList(),
            needsReviewCount = 3,
            hideAmounts = false,
            hasTransactions = true,
            smsAccess = true,
        )
    }
}
