package com.foldfinance.app.widget

import android.content.Context
import android.os.Build
import androidx.core.content.edit
import androidx.core.content.pm.PackageInfoCompat
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Обновление виджетов («Расходы» и «На проверку») после изменения данных.
 *
 * Вызывать после записи операций (ingest SMS/push, ручное добавление, правки, проверка,
 * импорт бэкапа) и после смены настроек, влияющих на виджет (валюта, скрытие сумм).
 * Без установленных виджетов вызов ничего не делает. Плюс к этому система обновляет виджеты
 * сама раз в 30 минут (`updatePeriodMillis`), чтобы «сегодня» сменилось после полуночи.
 *
 * Заодно (Android 15+) публикует сгенерированные превью для списка виджетов — один раз на
 * версию приложения: вызов у системы ограничен по частоте.
 */
object FinanceWidgetUpdater {

    /** Пачка операций (повторное сканирование SMS) даёт одно обновление, а не сотню. */
    private const val DEBOUNCE_MILLIS = 500L

    private const val PREVIEW_PREFS = "widget_previews"
    private const val KEY_PREVIEW_VERSION = "published_version"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val pending = AtomicBoolean(false)
    private val previewsTried = AtomicBoolean(false)

    /** Запросить обновление без ожидания (можно с любого потока). Частые вызовы схлопываются. */
    @JvmStatic
    fun requestUpdate(context: Context) {
        if (!pending.compareAndSet(false, true)) return
        val app = context.applicationContext
        scope.launch {
            delay(DEBOUNCE_MILLIS)
            pending.set(false)
            update(app)
        }
    }

    /** Обновить сразу и дождаться (из `CoroutineWorker`, где процесс может завершиться следом). */
    suspend fun update(context: Context) {
        val app = context.applicationContext
        guarded { FinanceWidget().updateAll(app) }
        guarded { ReviewWidget().updateAll(app) }
        publishPreviewsIfNeeded(app)
    }

    /** Опубликовать превью в фоне (из ресиверов виджетов). */
    fun requestPreviewPublish(context: Context) {
        val app = context.applicationContext
        scope.launch { publishPreviewsIfNeeded(app) }
    }

    /**
     * Сгенерированные превью (`GlanceAppWidget.providePreview`) для списка виджетов. Не чаще раза
     * за процесс и только если для этой версии приложения ещё не опубликованы. До Android 15 и
     * пока превью не опубликованы, система показывает `previewLayout` из `*_widget_info.xml`.
     */
    private suspend fun publishPreviewsIfNeeded(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) return
        if (!previewsTried.compareAndSet(false, true)) return
        guarded {
            val prefs = context.getSharedPreferences(PREVIEW_PREFS, Context.MODE_PRIVATE)
            val version = PackageInfoCompat.getLongVersionCode(context.packageManager.getPackageInfo(context.packageName, 0))
            if (prefs.getLong(KEY_PREVIEW_VERSION, -1L) == version) return@guarded
            val manager = GlanceAppWidgetManager(context)
            val published = listOf(FinanceWidgetReceiver::class, ReviewWidgetReceiver::class).all {
                manager.setWidgetPreviews(it) == GlanceAppWidgetManager.SET_WIDGET_PREVIEWS_RESULT_SUCCESS
            }
            if (published) prefs.edit { putLong(KEY_PREVIEW_VERSION, version) }
        }
    }

    /** Виджет — не критичный путь: ошибка обновления не должна ронять ingest. */
    private inline fun guarded(block: () -> Unit) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
        }
    }
}
