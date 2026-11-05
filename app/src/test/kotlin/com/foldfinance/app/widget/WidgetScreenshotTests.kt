package com.foldfinance.app.widget

import android.content.Context
import android.widget.FrameLayout
import android.widget.RemoteViews
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.compose
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.foldfinance.app.SCREENSHOT_DIR
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.GraphicsMode
import java.time.YearMonth

/**
 * Превью виджетов: настоящий Glance-вывод (`GlanceAppWidget.compose` → `RemoteViews`), развёрнутый
 * в View и снятый Roborazzi в светлой и тёмной теме системы (цвета day/night применяются при
 * развёртывании `RemoteViews`). Фон — подложка «рабочего стола» из макета, не тема приложения:
 * снимки виджетов не зависят от экранов.
 *
 * - `widget_preview_sizes_*`: 4×3, 4×2, 4×1, 2×2 и «На проверку» — как первый ряд Widgets.dc.html;
 * - `widget_preview_states_*`: скрытые суммы, пустое состояние, «данные недоступны», 2×1, 0 на проверку.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WidgetScreenshotTests {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private val sample = WidgetSamples.finance(YearMonth.of(2026, 11))
    private val hidden = sample.copy(hideAmounts = true)
    private val emptyNoSms = sample.copy(hasTransactions = false, smsAccess = false, needsReviewCount = 0)
    private val emptyWaiting = emptyNoSms.copy(smsAccess = true)

    private val large = DpSize(352.dp, 260.dp)
    private val medium = DpSize(352.dp, 170.dp)
    private val strip = DpSize(352.dp, 80.dp)
    private val small = DpSize(170.dp, 170.dp)
    private val compactStrip = DpSize(170.dp, 80.dp)

    @Test fun sizes() {
        val views = listOf(
            finance(sample, large), finance(sample, medium), finance(sample, strip), finance(sample, small), review(sample),
        )
        captureBothThemes("widget_preview_sizes", width = 1000, height = 420) { SizesSheet(views) }
    }

    @Composable
    private fun SizesSheet(v: List<RemoteViews>) {
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            Widget(v[0], large)
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Widget(v[1], medium)
                Widget(v[2], strip)
            }
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Widget(v[3], small)
                Widget(v[4], ReviewWidgetSize)
            }
        }
    }

    @Test fun states() {
        val v = listOf(
            finance(hidden, large), finance(hidden, medium), finance(hidden, strip), finance(hidden, small),
            finance(sample, compactStrip), finance(emptyNoSms, small), finance(emptyWaiting, small), finance(null, small),
            review(sample.copy(needsReviewCount = 0)), finance(null, strip), finance(emptyNoSms, strip),
        )
        captureBothThemes("widget_preview_states", width = 1240, height = 520) {
            Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    Widget(v[0], large)
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Widget(v[1], medium)
                        Widget(v[2], strip)
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Widget(v[3], small)
                        Widget(v[4], compactStrip)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    Widget(v[5], small)
                    Widget(v[6], small)
                    Widget(v[7], small)
                    Widget(v[8], ReviewWidgetSize)
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Widget(v[9], strip)
                        Widget(v[10], strip)
                    }
                }
            }
        }
    }

    private fun finance(data: FinanceWidgetData?, size: DpSize) = render(FinanceWidget(loaderOf(data)), size)

    private fun review(data: FinanceWidgetData?) = render(ReviewWidget(loaderOf(data)), ReviewWidgetSize)

    private fun loaderOf(data: FinanceWidgetData?) = FinanceWidgetDataLoader { data ?: error("no data") }

    private fun render(widget: GlanceAppWidget, size: DpSize): RemoteViews = runBlocking {
        withTimeout(60_000) { widget.compose(context, size = size) }
    }

    /**
     * Снимок в `src/test/screenshots/<name>_<light|dark>.png`. Виджеты рендерятся внутри
     * [content] после смены квалификаторов, поэтому `RemoteViews` получают тему системы.
     */
    private fun captureBothThemes(name: String, width: Int, height: Int, content: @Composable () -> Unit) {
        for (dark in listOf(false, true)) {
            val theme = if (dark) "dark" else "light"
            RuntimeEnvironment.setQualifiers("ru-w${width}dp-h${height}dp-land-${if (dark) "night" else "notnight"}-xhdpi")
            captureRoboImage("$SCREENSHOT_DIR/${name}_$theme.png") {
                Box(Modifier.fillMaxSize().background(if (dark) DarkDesk else LightDesk).padding(24.dp)) { content() }
            }
        }
    }

    @Composable
    private fun Widget(views: RemoteViews, size: DpSize) {
        AndroidView(
            modifier = Modifier.size(size.width, size.height),
            factory = { ctx -> FrameLayout(ctx).also { host -> host.addView(views.apply(ctx, host)) } },
        )
    }

    private companion object {
        /** Подложки «рабочего стола» из Widgets.dc.html. */
        val DarkDesk = Color(0xFF2B2E36)
        val LightDesk = Color(0xFFD5D8DE)
    }
}
