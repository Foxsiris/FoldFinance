package com.foldfinance.app.widget

import android.content.Context
import android.content.Intent
import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.color.ColorProvider as DayNightColor
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
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.foldfinance.app.AppIntents
import com.foldfinance.app.MainActivity
import com.foldfinance.app.R
import com.foldfinance.app.ui.format.MoneyFormat

/**
 * Палитра виджетов «Obsidian» (docs/design-v2/Widgets.dc.html): тёмная и светлая версии,
 * выбираются системой при развёртывании `RemoteViews`. Своя, а не из темы приложения: виджет
 * рисуется вне Compose-темы и не должен меняться вместе с экранами.
 */
internal object WidgetPalette {
    private fun dayNight(day: Long, night: Long): ColorProvider = DayNightColor(day = Color(day), night = Color(night))

    val background = dayNight(0xFFF6F6F3, 0xFF121215)
    val text = dayNight(0xFF121214, 0xFFF5F5F3)
    val textSecondary = dayNight(0xFF5E5E66, 0xFFA1A1A8)
    val textMuted = dayNight(0xFF74747C, 0xFF8A8A92)
    val hiddenAmount = dayNight(0xFF3A3A40, 0xFFD6D6DA)
    val line = dayNight(0xFFDEDED8, 0xFF2A2A30)
    val income = dayNight(0xFF1B7F50, 0xFF7FE0A8)
    val review = dayNight(0xFF8A5200, 0xFFFFB547)
    val reviewContainer = dayNight(0x3DFFB547, 0x26FFB547)
    val reviewOutline = dayNight(0x668A5200, 0x66FFB547)
    val accent = dayNight(0xFFD2F65A, 0xFFD2F65A)
    val onAccent = dayNight(0xFF0B0B0C, 0xFF0B0B0C)
    val tile = dayNight(0xFFEAEAE5, 0xFF1E1E23)
    val barPast = dayNight(0xFFC4C4C0, 0xFF3A3A42)
    val barFuture = dayNight(0xFFE6E6E1, 0xFF222227)
    val barHidden = dayNight(0xFFE6E6E1, 0xFF26262C)
}

/** Суммы в виджете — в целых единицах валюты (копейки не помещаются в 2×2 и не нужны «на взгляд»). */
internal object WidgetMoney {
    fun format(amountMinor: Long, currency: String): String {
        var divisor = 1L
        repeat(MoneyFormat.fractionDigits(currency)) { divisor *= 10 }
        val half = divisor / 2
        val rounded = if (amountMinor >= 0) (amountMinor + half) / divisor * divisor else -((-amountMinor + half) / divisor * divisor)
        return MoneyFormat.format(rounded, currency, hideZeroFraction = true)
    }

    /** «•••• ₽» вместо суммы, когда включено «Скрывать суммы». */
    fun hidden(context: Context, currency: String): String =
        context.getString(R.string.widget_hidden_amount) + MoneyFormat.NARROW_NBSP + MoneyFormat.symbol(currency)

    fun display(context: Context, data: FinanceWidgetData, amountMinor: Long): String =
        if (data.hideAmounts) hidden(context, data.currency) else format(amountMinor, data.currency)
}

/** Интент в `MainActivity`; она сама требует разблокировку приложения. */
internal fun openApp(context: Context, action: String? = null): Action = actionStartActivity(
    Intent(context, MainActivity::class.java)
        .setAction(action ?: Intent.ACTION_MAIN)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
)

/** Карточка виджета: фон, скругление, тап по пустому месту открывает [onClick] (по умолчанию — приложение). */
@Composable
internal fun WidgetCard(
    radius: Dp = 28.dp,
    padding: Dp = 16.dp,
    paddingStart: Dp = padding,
    paddingEnd: Dp = padding,
    onClick: Action? = null,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .appWidgetBackground()
            .cornerRadius(radius)
            .background(WidgetPalette.background)
            .clickable(onClick ?: openApp(context))
            .padding(start = paddingStart, end = paddingEnd, top = padding, bottom = padding),
    ) { content() }
}

@Composable
internal fun WidgetText(
    text: String,
    size: TextUnit,
    color: ColorProvider,
    modifier: GlanceModifier = GlanceModifier,
    weight: FontWeight = FontWeight.Normal,
    maxLines: Int = 1,
) {
    Text(text = text, modifier = modifier, style = TextStyle(color = color, fontSize = size, fontWeight = weight), maxLines = maxLines)
}

@Composable
internal fun WidgetIcon(@DrawableRes icon: Int, size: Dp, tint: ColorProvider, modifier: GlanceModifier = GlanceModifier) {
    Image(
        provider = ImageProvider(icon),
        contentDescription = null,
        modifier = modifier.size(size),
        colorFilter = ColorFilter.tint(tint),
    )
}

/** Подпись с иконкой «глаз перечёркнут» вместо обычной, когда суммы скрыты. */
@Composable
internal fun HiddenLabel(size: TextUnit, modifier: GlanceModifier = GlanceModifier) {
    val context = LocalContext.current
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        WidgetIcon(R.drawable.widget_ic_eye_off, 15.dp, WidgetPalette.textSecondary)
        Spacer(GlanceModifier.width(6.dp))
        WidgetText(context.getString(R.string.widget_hidden_title), size, WidgetPalette.textSecondary)
    }
}

/** Кнопка «+»: ручное добавление операции. С [label] — широкая «+ Добавить». */
@Composable
internal fun AddButton(
    modifier: GlanceModifier,
    radius: Dp,
    iconSize: Dp,
    label: String? = null,
) {
    val context = LocalContext.current
    val description = context.getString(R.string.widget_add)
    Box(
        modifier = modifier
            .cornerRadius(radius)
            .background(WidgetPalette.accent)
            .clickable(openApp(context, AppIntents.ACTION_QUICK_ADD))
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            WidgetIcon(R.drawable.widget_ic_plus, iconSize, WidgetPalette.onAccent)
            if (label != null) {
                Spacer(GlanceModifier.width(6.dp))
                WidgetText(label, 14.sp, WidgetPalette.onAccent, weight = FontWeight.Medium)
            }
        }
    }
}

/** Действие счётчика «на проверку»: очередь проверки. */
@Composable
internal fun openReview(): Action = openApp(LocalContext.current, AppIntents.ACTION_OPEN_REVIEW)

/** Описание счётчика для TalkBack: «3 операции на проверку». */
internal fun reviewDescription(context: Context, count: Int): String =
    context.resources.getQuantityString(R.plurals.widget_review_description, count, count)

/**
 * Счётчик «на проверку» плашкой. Нет операций на проверку — нейтральный цвет, но плашка остаётся:
 * раскладка не прыгает, а тап всё так же ведёт в очередь.
 */
@Composable
internal fun ReviewBadge(
    count: Int,
    modifier: GlanceModifier,
    radius: Dp,
    content: @Composable (color: ColorProvider) -> Unit,
) {
    val context = LocalContext.current
    val description = reviewDescription(context, count)
    val active = count > 0
    Box(
        modifier = modifier
            .cornerRadius(radius)
            .background(if (active) WidgetPalette.reviewContainer else WidgetPalette.tile)
            .clickable(openReview())
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) { content(if (active) WidgetPalette.review else WidgetPalette.textSecondary) }
}

/**
 * Мини-график расходов по дням месяца: ряд Box фиксированной высоты (без Canvas — его нет в
 * RemoteViews). Прошедшие дни — серые столбики пропорционально сумме, сегодня — акцентом,
 * будущие и скрытые — низкие плашки.
 *
 * Glance обрезает Row/Column/Box до 10 детей, поэтому дни разложены по группам (≤10 групп по
 * ≤10 дней, все группы одинаковые — у `defaultWeight` нет веса). Если длина месяца не делится
 * нацело (29, 31), последний слот пустой и прозрачный.
 */
@Composable
internal fun DayBars(data: FinanceWidgetData, height: Dp, gap: Dp, minBar: Dp, modifier: GlanceModifier = GlanceModifier) {
    val days = data.dailyNetExpenseMinor.size
    val (groups, perGroup) = DayBarGrid.of(days)
    val max = data.dailyNetExpenseMinor.take(data.todayOfMonth).maxOrNull()?.coerceAtLeast(1L) ?: 1L
    Row(modifier.fillMaxWidth().height(height), verticalAlignment = Alignment.Bottom) {
        for (group in 0 until groups) {
            Row(GlanceModifier.defaultWeight().fillMaxHeight(), verticalAlignment = Alignment.Bottom) {
                for (slot in 0 until perGroup) {
                    val day = group * perGroup + slot + 1
                    Box(
                        modifier = GlanceModifier.defaultWeight().fillMaxHeight().padding(horizontal = gap / 2),
                        contentAlignment = Alignment.BottomCenter,
                    ) {
                        if (day <= days) {
                            val bar = DayBarGrid.bar(data, day, max, height, minBar)
                            Box(
                                GlanceModifier.fillMaxWidth().height(bar.height).cornerRadius(2.dp).background(bar.color),
                            ) {}
                        }
                    }
                }
            }
        }
    }
}

internal object DayBarGrid {
    /** Раскладка [days] столбиков на `(группы, столбиков в группе)`, обе ≤ 10, минимум лишних слотов. */
    fun of(days: Int): Pair<Int, Int> {
        require(days in 1..100)
        var best = 10 to 10
        for (perGroup in 1..10) {
            val groups = (days + perGroup - 1) / perGroup
            if (groups > 10) continue
            if (groups * perGroup < best.first * best.second) best = groups to perGroup
        }
        return best
    }

    data class Bar(val height: Dp, val color: ColorProvider)

    fun bar(data: FinanceWidgetData, day: Int, max: Long, height: Dp, minBar: Dp): Bar = when {
        data.hideAmounts -> Bar(4.dp, WidgetPalette.barHidden)
        day > data.todayOfMonth -> Bar(3.dp, WidgetPalette.barFuture)
        else -> {
            val value = data.dailyNetExpenseMinor[day - 1].coerceAtLeast(0L)
            val scaled = height * (value.toFloat() / max)
            Bar(
                height = if (scaled < minBar) minBar else scaled,
                color = if (day == data.todayOfMonth) WidgetPalette.accent else WidgetPalette.barPast,
            )
        }
    }
}

/**
 * Состояние без цифр: «пока нет операций» или «данные недоступны». Иконка в плашке, заголовок,
 * пояснение и кнопка внизу — как в макете 2×2.
 */
@Composable
internal fun MessageState(
    @DrawableRes icon: Int,
    title: String,
    body: String,
    button: String,
    onClick: Action,
    compact: Boolean,
) {
    if (compact) {
        Row(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            IconTile(icon)
            Spacer(GlanceModifier.width(12.dp))
            Column(GlanceModifier.defaultWeight()) {
                WidgetText(title, 14.sp, WidgetPalette.text, weight = FontWeight.Medium)
                WidgetText(body, 12.sp, WidgetPalette.textSecondary)
            }
        }
        return
    }
    // Макет: плашка 40, отступ 12, кнопка 44 — в 2×2 (138 dp без отступов) с двухстрочным
    // пояснением не помещается; ужато до 36 / 8 / 40.
    Column(GlanceModifier.fillMaxSize()) {
        IconTile(icon, 36.dp)
        Spacer(GlanceModifier.height(8.dp))
        WidgetText(title, 15.sp, WidgetPalette.text, weight = FontWeight.Medium)
        Spacer(GlanceModifier.height(2.dp))
        WidgetText(body, 12.sp, WidgetPalette.textSecondary, maxLines = 2)
        Spacer(GlanceModifier.defaultWeight())
        Box(
            modifier = GlanceModifier
                .fillMaxWidth()
                .height(40.dp)
                .cornerRadius(14.dp)
                .background(WidgetPalette.tile)
                .clickable(onClick),
            contentAlignment = Alignment.Center,
        ) { WidgetText(button, 14.sp, WidgetPalette.text, weight = FontWeight.Medium) }
    }
}

@Composable
private fun IconTile(@DrawableRes icon: Int, size: Dp = 40.dp) {
    Box(
        modifier = GlanceModifier.size(size).cornerRadius(size / 3).background(WidgetPalette.tile),
        contentAlignment = Alignment.Center,
    ) { WidgetIcon(icon, 20.dp, WidgetPalette.textSecondary) }
}

/** БД недоступна (ключ ещё не расшифрован и т. п.): предлагаем открыть приложение. */
@Composable
internal fun UnavailableState(compact: Boolean) {
    val context = LocalContext.current
    MessageState(
        icon = R.drawable.widget_ic_lock,
        title = context.getString(R.string.widget_unavailable_title),
        body = context.getString(R.string.widget_unavailable),
        button = context.getString(R.string.widget_open_app),
        onClick = openApp(context),
        compact = compact,
    )
}

/** Ни одной операции: без доступа к SMS — «Настроить», с доступом — ждём первую SMS или ручной ввод. */
@Composable
internal fun EmptyState(data: FinanceWidgetData, compact: Boolean) {
    val context = LocalContext.current
    MessageState(
        icon = R.drawable.widget_ic_chat,
        title = context.getString(R.string.widget_empty_title),
        body = context.getString(if (data.smsAccess) R.string.widget_empty_waiting else R.string.widget_empty_sms),
        button = context.getString(if (data.smsAccess) R.string.widget_add_short else R.string.widget_empty_setup),
        onClick = if (data.smsAccess) openApp(context, AppIntents.ACTION_QUICK_ADD) else openApp(context),
        compact = compact,
    )
}
