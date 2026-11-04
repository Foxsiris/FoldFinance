package com.foldfinance.app.widget

import android.content.Context
import com.foldfinance.core.data.db.FoldFinanceDatabase
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Обновляет виджет после любой записи в таблицу операций — из SMS, push, ручного ввода,
 * проверки или импорта. Одна точка вместо вызовов в каждом месте записи.
 */
@Singleton
class WidgetRefresher @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: FoldFinanceDatabase,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @OptIn(FlowPreview::class)
    fun start() {
        scope.launch {
            database.invalidationTracker.createFlow(TRANSACTIONS_TABLE, emitInitialState = false)
                .debounce(DEBOUNCE_MS)
                .collect { FinanceWidgetUpdater.requestUpdate(context) }
        }
    }

    private companion object {
        const val TRANSACTIONS_TABLE = "transactions"
        const val DEBOUNCE_MS = 1_500L
    }
}
