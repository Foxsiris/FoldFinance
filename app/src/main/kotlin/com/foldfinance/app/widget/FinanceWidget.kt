package com.foldfinance.app.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.PreviewSizeMode
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import com.foldfinance.app.R
import kotlinx.coroutines.CancellationException

/**
 * Glance-виджет «Расходы» для рабочего стола внешнего экрана (docs/design-v2/Widgets.dc.html):
 * сегодня и месяц, мини-график по дням, счётчик «на проверку» (тап — очередь проверки) и «+»
 * (ручное добавление). Раскладка по размеру — [FinanceWidgetLayout].
 *
 * **Приватность.** Виджет живёт на рабочем столе вне блокировки приложения и FLAG_SECURE:
 * это осознанный выбор пользователя, поставившего виджет. Он показывает только суммы и число
 * операций — без мерчантов, карт и текстов сообщений. Настройка «Скрывать суммы в виджете»
 * (по умолчанию выкл.) заменяет суммы на «••••» и гасит график. Тапы ведут в `MainActivity`,
 * а она сама требует разблокировку.
 *
 * Данные загружаются при каждом обновлении ([FinanceWidgetUpdater.requestUpdate],
 * `updatePeriodMillis`); если БД недоступна, виджет предлагает открыть приложение.
 */
class FinanceWidget internal constructor(private val loader: FinanceWidgetDataLoader) : GlanceAppWidget() {

    constructor() : this(DatabaseWidgetDataLoader())

    override val sizeMode: SizeMode = SizeMode.Responsive(FinanceWidgetLayout.sizes)

    override val previewSizeMode: PreviewSizeMode = SizeMode.Responsive(FinanceWidgetLayout.sizes)

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = loadOrNull(loader, context)
        provideContent { FinanceWidgetContent(data) }
    }

    /** Превью в списке виджетов (Android 15+): образец из макета, не данные пользователя. */
    override suspend fun providePreview(context: Context, widgetCategory: Int) {
        provideContent { FinanceWidgetContent(WidgetSamples.finance()) }
    }
}

/** Загрузка без исключений: `null` — «данные недоступны». Ничего не логируем: в данных суммы. */
internal suspend fun loadOrNull(loader: FinanceWidgetDataLoader, context: Context): FinanceWidgetData? = try {
    loader.load(context)
} catch (e: CancellationException) {
    throw e
} catch (_: Exception) {
    null
}

/**
 * Раскладки по размеру виджета (сетка внешнего экрана 4 колонки; макет: 4×3, 4×2, 4×1, 2×2).
 * В `SizeMode.Responsive` система берёт самую большую раскладку, которая помещается, поэтому
 * пороги — минимальные размеры, при которых раскладка ещё влезает.
 */
enum class FinanceWidgetLayout(val minSize: DpSize) {
    /** 2×1: только «сегодня» и «+». */
    StripCompact(DpSize(120.dp, 44.dp)),

    /** 4×1: сегодня | месяц | счётчик | «+». */
    Strip(DpSize(280.dp, 44.dp)),

    /** 2×2: сегодня, месяц, счётчик и «+» снизу. */
    Small(DpSize(140.dp, 130.dp)),

    /** 4×2: сегодня, месяц, мини-график; справа счётчик и «+». */
    Medium(DpSize(280.dp, 130.dp)),

    /** 4×3: месяц крупно, доход, мини-график, две кнопки. */
    Large(DpSize(280.dp, 220.dp)),
    ;

    companion object {
        val sizes: Set<DpSize> = entries.map { it.minSize }.toSet()

        /** Самая крупная раскладка, которая помещается в [size] (меньше всех — [StripCompact]). */
        fun forSize(size: DpSize): FinanceWidgetLayout =
            entries
                .filter { size.width >= it.minSize.width && size.height >= it.minSize.height }
                .maxByOrNull { it.minSize.width.value * it.minSize.height.value }
                ?: StripCompact
    }
}

@Composable
internal fun FinanceWidgetContent(data: FinanceWidgetData?) {
    val layout = FinanceWidgetLayout.forSize(LocalSize.current)
    val strip = layout == FinanceWidgetLayout.Strip || layout == FinanceWidgetLayout.StripCompact
    val large = layout == FinanceWidgetLayout.Large
    WidgetCard(
        radius = if (strip) 24.dp else 28.dp,
        padding = if (strip) 0.dp else if (large) 18.dp else 16.dp,
        paddingStart = if (strip || large) 18.dp else 16.dp,
        paddingEnd = if (strip) 12.dp else if (large) 18.dp else 16.dp,
    ) {
        when {
            data == null -> UnavailableState(compact = strip)
            !data.hasTransactions -> EmptyState(data, compact = strip)
            else -> when (layout) {
                FinanceWidgetLayout.Large -> LargeLayout(data)
                FinanceWidgetLayout.Medium -> MediumLayout(data)
                FinanceWidgetLayout.Small -> SmallLayout(data)
                FinanceWidgetLayout.Strip -> StripLayout(data, showMonth = true)
                FinanceWidgetLayout.StripCompact -> StripLayout(data, showMonth = false)
            }
        }
    }
}

/** 4×3: «Ноябрь · потрачено», месяц крупно, доход, график 64 dp, кнопки «N на проверку» и «Добавить». */
@Composable
private fun LargeLayout(data: FinanceWidgetData) {
    val context = LocalContext.current
    Column(GlanceModifier.fillMaxSize()) {
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            if (data.hideAmounts) {
                HiddenLabel(13.sp, GlanceModifier.defaultWeight())
            } else {
                val monthName = context.resources.getStringArray(R.array.widget_months)[data.month.monthValue - 1]
                WidgetText(
                    context.getString(R.string.widget_month_spent, monthName),
                    13.sp,
                    WidgetPalette.textSecondary,
                    GlanceModifier.defaultWeight(),
                )
                Spacer(GlanceModifier.width(8.dp))
                WidgetText(
                    context.getString(R.string.widget_today_inline, WidgetMoney.display(context, data, data.todayNetExpenseMinor)),
                    13.sp,
                    WidgetPalette.textSecondary,
                )
            }
        }
        Spacer(GlanceModifier.height(8.dp))
        Amount(data, data.monthNetExpenseMinor, 38)
        Spacer(GlanceModifier.height(6.dp))
        when {
            data.hideAmounts -> WidgetText(context.getString(R.string.widget_hidden_hint), 13.sp, WidgetPalette.textMuted)
            data.monthIncomeMinor > 0 -> WidgetText(
                context.getString(R.string.widget_income, WidgetMoney.format(data.monthIncomeMinor, data.currency)),
                13.sp,
                WidgetPalette.income,
            )
        }
        Spacer(GlanceModifier.defaultWeight())
        DayBars(data, height = 64.dp, gap = 3.dp, minBar = 4.dp)
        Spacer(GlanceModifier.height(14.dp))
        Row(GlanceModifier.fillMaxWidth()) {
            ReviewBadge(data.needsReviewCount, GlanceModifier.defaultWeight().height(44.dp), radius = 14.dp) { color ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    WidgetIcon(R.drawable.widget_ic_review, 18.dp, color)
                    Spacer(GlanceModifier.width(8.dp))
                    WidgetText(
                        context.getString(R.string.widget_review_count, data.needsReviewCount),
                        14.sp,
                        color,
                        weight = FontWeight.Medium,
                    )
                }
            }
            Spacer(GlanceModifier.width(8.dp))
            AddButton(
                GlanceModifier.defaultWeight().height(44.dp),
                radius = 14.dp,
                iconSize = 18.dp,
                label = context.getString(R.string.widget_add_short),
            )
        }
    }
}

/** 4×2: слева «Сегодня», сумма, месяц и график 30 dp; справа колонка 108 dp — счётчик и «+». */
@Composable
private fun MediumLayout(data: FinanceWidgetData) {
    val context = LocalContext.current
    Row(GlanceModifier.fillMaxSize()) {
        Column(GlanceModifier.defaultWeight().fillMaxHeight()) {
            TodayLabel(data, 12.5f)
            Spacer(GlanceModifier.height(4.dp))
            Amount(data, data.todayNetExpenseMinor, 30)
            Spacer(GlanceModifier.height(4.dp))
            WidgetText(
                if (data.hideAmounts) {
                    context.getString(R.string.widget_hidden_hint)
                } else {
                    context.getString(R.string.widget_month_inline, WidgetMoney.format(data.monthNetExpenseMinor, data.currency))
                },
                13.sp,
                if (data.hideAmounts) WidgetPalette.textMuted else WidgetPalette.textSecondary,
                maxLines = if (data.hideAmounts) 2 else 1,
            )
            Spacer(GlanceModifier.defaultWeight())
            DayBars(data, height = 30.dp, gap = 2.dp, minBar = 3.dp)
        }
        Spacer(GlanceModifier.width(12.dp))
        Column(GlanceModifier.width(108.dp).fillMaxHeight()) {
            ReviewBadge(data.needsReviewCount, GlanceModifier.fillMaxWidth().defaultWeight(), radius = 18.dp) { color ->
                Column(GlanceModifier.fillMaxWidth().padding(horizontal = 14.dp)) {
                    WidgetText(data.needsReviewCount.toString(), 24.sp, color, weight = FontWeight.Bold)
                    Spacer(GlanceModifier.height(2.dp))
                    WidgetText(context.getString(R.string.widget_review_label), 12.sp, color, weight = FontWeight.Medium)
                }
            }
            Spacer(GlanceModifier.height(8.dp))
            AddButton(GlanceModifier.fillMaxWidth().height(56.dp), radius = 18.dp, iconSize = 24.dp)
        }
    }
}

/** 2×2: «Сегодня», сумма, месяц; снизу счётчик (растягивается) и «+» 44 dp. */
@Composable
private fun SmallLayout(data: FinanceWidgetData) {
    val context = LocalContext.current
    Column(GlanceModifier.fillMaxSize()) {
        TodayLabel(data, 12.5f)
        Spacer(GlanceModifier.height(4.dp))
        Amount(data, data.todayNetExpenseMinor, 28)
        Spacer(GlanceModifier.height(4.dp))
        WidgetText(
            context.getString(R.string.widget_month_inline, WidgetMoney.display(context, data, data.monthNetExpenseMinor)),
            12.sp,
            WidgetPalette.textSecondary,
        )
        Spacer(GlanceModifier.defaultWeight())
        Row(GlanceModifier.fillMaxWidth()) {
            ReviewBadge(data.needsReviewCount, GlanceModifier.defaultWeight().height(44.dp), radius = 14.dp) { color ->
                WidgetText(data.needsReviewCount.toString(), 15.sp, color, weight = FontWeight.Bold)
            }
            Spacer(GlanceModifier.width(8.dp))
            AddButton(GlanceModifier.size(44.dp), radius = 14.dp, iconSize = 20.dp)
        }
    }
}

/** 4×1 (и 2×1 без месяца и счётчика): две колонки сумм через разделитель, круглый счётчик, «+». */
@Composable
private fun StripLayout(data: FinanceWidgetData, showMonth: Boolean) {
    val context = LocalContext.current
    Row(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
        StripValue(context.getString(R.string.widget_today), WidgetMoney.display(context, data, data.todayNetExpenseMinor), data, if (showMonth) GlanceModifier else GlanceModifier.defaultWeight())
        if (showMonth) {
            Spacer(GlanceModifier.width(14.dp))
            Box(GlanceModifier.width(1.dp).height(32.dp).background(WidgetPalette.line)) {}
            Spacer(GlanceModifier.width(14.dp))
            StripValue(
                context.getString(R.string.widget_month_title),
                WidgetMoney.display(context, data, data.monthNetExpenseMinor),
                data,
                GlanceModifier.defaultWeight(),
            )
            Spacer(GlanceModifier.width(14.dp))
            ReviewBadge(data.needsReviewCount, GlanceModifier.size(44.dp), radius = 22.dp) { color ->
                WidgetText(data.needsReviewCount.toString(), 16.sp, color, weight = FontWeight.Bold)
            }
        }
        Spacer(GlanceModifier.width(if (showMonth) 14.dp else 8.dp))
        AddButton(GlanceModifier.size(if (showMonth) 52.dp else 44.dp), radius = if (showMonth) 18.dp else 14.dp, iconSize = 22.dp)
    }
}

@Composable
private fun StripValue(label: String, value: String, data: FinanceWidgetData, modifier: GlanceModifier) {
    Column(modifier) {
        WidgetText(label, 11.5.sp, WidgetPalette.textSecondary)
        Spacer(GlanceModifier.height(2.dp))
        WidgetText(
            value,
            19.sp,
            if (data.hideAmounts) WidgetPalette.hiddenAmount else WidgetPalette.text,
            weight = FontWeight.Medium,
        )
    }
}

/** «Сегодня» или «Суммы скрыты» с иконкой. */
@Composable
private fun TodayLabel(data: FinanceWidgetData, sizeSp: Float) {
    if (data.hideAmounts) {
        HiddenLabel(sizeSp.sp)
    } else {
        WidgetText(LocalContext.current.getString(R.string.widget_today), sizeSp.sp, WidgetPalette.textSecondary)
    }
}

/** Крупная сумма; скрытая — «•••• ₽» приглушённым цветом. */
@Composable
private fun Amount(data: FinanceWidgetData, amountMinor: Long, sizeSp: Int) {
    WidgetText(
        WidgetMoney.display(LocalContext.current, data, amountMinor),
        sizeSp.sp,
        if (data.hideAmounts) WidgetPalette.hiddenAmount else WidgetPalette.text,
        weight = FontWeight.Medium,
    )
}
