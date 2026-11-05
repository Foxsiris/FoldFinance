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
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.PreviewSizeMode
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.text.FontWeight
import com.foldfinance.app.R

/**
 * Виджет «На проверку» 2×2 (макет «только проверка»): число операций в очереди проверки и
 * кнопка «Разобрать». Сумм не показывает, поэтому «Скрывать суммы» на него не влияет.
 * Весь виджет ведёт в очередь проверки.
 */
class ReviewWidget internal constructor(private val loader: FinanceWidgetDataLoader) : GlanceAppWidget() {

    constructor() : this(DatabaseWidgetDataLoader())

    override val sizeMode: SizeMode = SizeMode.Single

    override val previewSizeMode: PreviewSizeMode = SizeMode.Single

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = loadOrNull(loader, context)
        provideContent { ReviewWidgetContent(data) }
    }

    override suspend fun providePreview(context: Context, widgetCategory: Int) {
        provideContent { ReviewWidgetContent(WidgetSamples.finance()) }
    }
}

/** Размер по умолчанию (2×2 внешнего экрана) — для превью и тестов. */
val ReviewWidgetSize = DpSize(170.dp, 170.dp)

@Composable
internal fun ReviewWidgetContent(data: FinanceWidgetData?) {
    val compact = LocalSize.current.height < 120.dp
    WidgetCard(onClick = if (data == null) null else openReview()) {
        if (data == null) {
            UnavailableState(compact = compact)
        } else {
            ReviewCount(data.needsReviewCount)
        }
    }
}

@Composable
private fun ReviewCount(count: Int) {
    val context = LocalContext.current
    val active = count > 0
    Column(GlanceModifier.fillMaxSize()) {
        WidgetText(context.getString(R.string.widget_review_title), 12.5.sp, WidgetPalette.textSecondary)
        Spacer(GlanceModifier.height(6.dp))
        WidgetText(
            count.toString(),
            48.sp,
            if (active) WidgetPalette.review else WidgetPalette.textSecondary,
            weight = FontWeight.Bold,
        )
        Spacer(GlanceModifier.height(4.dp))
        WidgetText(
            if (active) {
                context.resources.getQuantityString(R.plurals.widget_review_waiting, count)
            } else {
                context.getString(R.string.widget_review_done)
            },
            12.sp,
            WidgetPalette.textMuted,
            maxLines = 2,
        )
        Spacer(GlanceModifier.defaultWeight())
        // Контур 1 dp: в Glance нет border, поэтому — подложка цвета контура и фон поверх с отступом.
        val description = reviewDescription(context, count)
        Box(
            modifier = GlanceModifier
                .fillMaxWidth()
                .height(44.dp)
                .cornerRadius(14.dp)
                .background(if (active) WidgetPalette.reviewOutline else WidgetPalette.line)
                .clickable(openReview())
                .semantics { contentDescription = description }
                .padding(1.dp),
        ) {
            Box(
                modifier = GlanceModifier.fillMaxSize().cornerRadius(13.dp).background(WidgetPalette.background),
                contentAlignment = Alignment.Center,
            ) {
                WidgetText(
                    context.getString(R.string.widget_review_action),
                    14.sp,
                    if (active) WidgetPalette.review else WidgetPalette.textSecondary,
                    weight = FontWeight.Medium,
                )
            }
        }
    }
}
