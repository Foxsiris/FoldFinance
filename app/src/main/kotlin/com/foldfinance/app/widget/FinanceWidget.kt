package com.foldfinance.app.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.foldfinance.app.AppIntents
import com.foldfinance.app.MainActivity
import com.foldfinance.app.R
import com.foldfinance.app.ui.format.MoneyFormat
import com.foldfinance.app.ui.theme.PaletteTokens
import kotlinx.coroutines.CancellationException

/**
 * Glance-виджет для рабочего стола внешнего экрана (этап 10): расходы за сегодня и за месяц,
 * счётчик «на проверку» (тап — очередь проверки), кнопка «+» (ручное добавление).
 *
 * **Приватность.** Виджет живёт на рабочем столе вне блокировки приложения и FLAG_SECURE:
 * это осознанный выбор пользователя, поставившего виджет. Он показывает только две суммы и
 * число операций — без мерчантов, карт и текстов сообщений. Для тех, кому и этого много, есть
 * настройка «Скрывать суммы в виджете» (по умолчанию выкл.): суммы заменяются на «•••».
 * Тапы ведут в `MainActivity`, а она сама требует разблокировку.
 *
 * Данные загружаются при каждом обновлении ([FinanceWidgetUpdater.requestUpdate],
 * `updatePeriodMillis`); если БД недоступна, виджет предлагает открыть приложение.
 * Цвета — палитра «Шов», светлая/тёмная по теме системы.
 */
class FinanceWidget internal constructor(private val loader: FinanceWidgetDataLoader) : GlanceAppWidget() {

    constructor() : this(DatabaseWidgetDataLoader())

    override val sizeMode: SizeMode = SizeMode.Responsive(setOf(FinanceWidgetSizes.Small, FinanceWidgetSizes.Wide))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = try {
            loader.load(context)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Ключ БД недоступен и т. п. Ничего не логируем: в данных суммы.
            null
        }
        provideContent { FinanceWidgetContent(data) }
    }
}

/** Размеры раскладок: узкий (3×2 ячейки внешнего экрана) и широкий (4×2 и больше). */
object FinanceWidgetSizes {
    val Small = DpSize(180.dp, 110.dp)
    val Wide = DpSize(260.dp, 110.dp)
}

private object WidgetColors {
    val background = ColorProvider(day = PaletteTokens.Light.surface, night = PaletteTokens.Dark.surface)
    val text = ColorProvider(day = PaletteTokens.Light.text, night = PaletteTokens.Dark.text)
    val textMuted = ColorProvider(day = PaletteTokens.Light.textMuted, night = PaletteTokens.Dark.textMuted)
    val saffron = ColorProvider(day = PaletteTokens.Light.saffron, night = PaletteTokens.Dark.saffron)
    val onSaffron = ColorProvider(day = PaletteTokens.Light.onSaffron, night = PaletteTokens.Dark.onSaffron)
    val saffronContainer = ColorProvider(
        day = PaletteTokens.Light.saffronContainer,
        night = PaletteTokens.Dark.saffronContainer,
    )
    val onSaffronContainer = ColorProvider(
        day = PaletteTokens.Light.onSaffronContainer,
        night = PaletteTokens.Dark.onSaffronContainer,
    )
}

private fun appIntent(context: Context, action: String?): Intent =
    Intent(context, MainActivity::class.java)
        .setAction(action ?: Intent.ACTION_MAIN)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)

@Composable
internal fun FinanceWidgetContent(data: FinanceWidgetData?) {
    val context = LocalContext.current
    val wide = LocalSize.current.width >= FinanceWidgetSizes.Wide.width
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .appWidgetBackground()
            .cornerRadius(24.dp)
            .background(WidgetColors.background)
            .clickable(actionStartActivity(appIntent(context, null)))
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        if (data == null) {
            Unavailable(context)
        } else {
            Summary(context, data, wide)
        }
    }
}

@Composable
private fun Unavailable(context: Context) {
    Row(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = context.getString(R.string.widget_unavailable),
            modifier = GlanceModifier.defaultWeight(),
            style = TextStyle(color = WidgetColors.textMuted, fontSize = 14.sp),
        )
        Spacer(GlanceModifier.width(12.dp))
        AddButton(context)
    }
}

@Composable
private fun Summary(context: Context, data: FinanceWidgetData, wide: Boolean) {
    fun amount(minor: Long): String =
        if (data.hideAmounts) {
            context.getString(R.string.widget_hidden_amount) + MoneyFormat.NARROW_NBSP + MoneyFormat.symbol(data.currency)
        } else {
            MoneyFormat.format(minor, data.currency)
        }
    val today = context.getString(R.string.widget_today)
    val month = amount(data.monthNetExpenseMinor) + " " + context.getString(R.string.widget_month)
    val labelStyle = TextStyle(color = WidgetColors.textMuted, fontSize = 13.sp)

    if (wide) {
        // Широкий: сумма за сегодня рядом с «+», снизу месяц и счётчик полной подписью.
        Column(GlanceModifier.fillMaxSize()) {
            Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Column(GlanceModifier.defaultWeight()) {
                    Text(text = today, style = labelStyle)
                    Text(
                        text = amount(data.todayNetExpenseMinor),
                        style = TextStyle(color = WidgetColors.text, fontSize = 30.sp, fontWeight = FontWeight.Medium),
                        maxLines = 1,
                    )
                }
                Spacer(GlanceModifier.width(8.dp))
                AddButton(context, 48.dp)
            }
            Spacer(GlanceModifier.defaultWeight())
            Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(text = month, modifier = GlanceModifier.defaultWeight(), style = labelStyle.copy(fontSize = 14.sp), maxLines = 1)
                if (data.needsReviewCount > 0) {
                    Spacer(GlanceModifier.width(8.dp))
                    ReviewChip(context, data.needsReviewCount, compact = false)
                }
            }
        }
    } else {
        // Узкий (3×2): сумма за сегодня на всю ширину, счётчик — числом в углу, «+» снизу справа.
        Column(GlanceModifier.fillMaxSize()) {
            Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(text = today, modifier = GlanceModifier.defaultWeight(), style = labelStyle, maxLines = 1)
                if (data.needsReviewCount > 0) ReviewChip(context, data.needsReviewCount, compact = true)
            }
            Text(
                text = amount(data.todayNetExpenseMinor),
                style = TextStyle(color = WidgetColors.text, fontSize = 24.sp, fontWeight = FontWeight.Medium),
                maxLines = 1,
            )
            Spacer(GlanceModifier.defaultWeight())
            Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(text = month, modifier = GlanceModifier.defaultWeight(), style = labelStyle, maxLines = 1)
                Spacer(GlanceModifier.width(8.dp))
                AddButton(context, 40.dp)
            }
        }
    }
}

/** Счётчик «на проверку»; [compact] — только число (описание для TalkBack полное). */
@Composable
private fun ReviewChip(context: Context, count: Int, compact: Boolean) {
    val label = context.getString(R.string.widget_review_count, count)
    Box(
        modifier = GlanceModifier
            .height(if (compact) 28.dp else 32.dp)
            .cornerRadius(if (compact) 14.dp else 16.dp)
            .background(WidgetColors.saffronContainer)
            .clickable(actionStartActivity(appIntent(context, AppIntents.ACTION_OPEN_REVIEW)))
            .semantics { contentDescription = label }
            .padding(horizontal = if (compact) 10.dp else 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = if (compact) count.toString() else label,
            style = TextStyle(color = WidgetColors.onSaffronContainer, fontSize = 13.sp, fontWeight = FontWeight.Medium),
            maxLines = 1,
        )
    }
}

@Composable
private fun AddButton(context: Context, size: Dp = 48.dp) {
    val description = context.getString(R.string.widget_add)
    Box(
        modifier = GlanceModifier
            .size(size)
            .cornerRadius(size * 0.4f)
            .background(WidgetColors.saffron)
            .clickable(actionStartActivity(appIntent(context, AppIntents.ACTION_QUICK_ADD)))
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Text(text = "+", style = TextStyle(color = WidgetColors.onSaffron, fontSize = 24.sp, fontWeight = FontWeight.Medium))
    }
}
