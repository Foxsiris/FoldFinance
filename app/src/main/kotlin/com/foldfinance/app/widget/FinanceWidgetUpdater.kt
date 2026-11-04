package com.foldfinance.app.widget

import android.content.Context
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Обновление виджета после изменения данных.
 *
 * Вызывать после записи операций (ingest SMS/push, ручное добавление, правки, проверка,
 * импорт бэкапа) и после смены настроек, влияющих на виджет (валюта, скрытие сумм).
 * Без установленных виджетов вызов ничего не делает. Плюс к этому система обновляет виджет
 * сама раз в 30 минут (`updatePeriodMillis`), чтобы «сегодня» сменилось после полуночи.
 */
object FinanceWidgetUpdater {

    /** Пачка операций (повторное сканирование SMS) даёт одно обновление, а не сотню. */
    private const val DEBOUNCE_MILLIS = 500L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val pending = AtomicBoolean(false)

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
        try {
            FinanceWidget().updateAll(context.applicationContext)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Виджет — не критичный путь: ошибка обновления не должна ронять ingest.
        }
    }
}
