package com.foldfinance.app.widget

import android.content.Context
import android.widget.FrameLayout
import android.widget.RemoteViews
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.glance.appwidget.compose
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.foldfinance.app.captureInAllConfigs
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

/**
 * Превью виджета: настоящий Glance-вывод (`GlanceAppWidget.compose` → `RemoteViews`), развёрнутый
 * в View и снятый Roborazzi так же, как экраны — 3 конфигурации × светлая/тёмная (тема виджета
 * следует за системой, цвета day/night применяются при развёртывании `RemoteViews`).
 * На каждом снимке: узкий и широкий размер, затем узкий со скрытыми суммами и «нет данных».
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WidgetScreenshotTests {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private val sample = FinanceWidgetData(
        currency = "RUB",
        todayNetExpenseMinor = 124_050,
        monthNetExpenseMinor = 3_890_000,
        needsReviewCount = 3,
        hideAmounts = false,
    )

    private fun render(data: FinanceWidgetData?, size: DpSize): RemoteViews = runBlocking {
        withTimeout(60_000) {
            val loader = FinanceWidgetDataLoader { data ?: error("no data") }
            FinanceWidget(loader).compose(context, size = size)
        }
    }

    @Test fun widget() {
        val small = render(sample, FinanceWidgetSizes.Small)
        val wide = render(sample, FinanceWidgetSizes.Wide)
        val hidden = render(sample.copy(hideAmounts = true, needsReviewCount = 0), FinanceWidgetSizes.Small)
        val unavailable = render(null, FinanceWidgetSizes.Wide)
        captureInAllConfigs("widget_preview") {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Widget(small, FinanceWidgetSizes.Small)
                Widget(wide, FinanceWidgetSizes.Wide)
                Widget(hidden, FinanceWidgetSizes.Small)
                Widget(unavailable, FinanceWidgetSizes.Wide)
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
}
