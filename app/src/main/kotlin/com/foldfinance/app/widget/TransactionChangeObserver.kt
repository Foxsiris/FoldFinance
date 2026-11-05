package com.foldfinance.app.widget

import android.content.Context
import com.foldfinance.core.data.db.FoldFinanceDatabase
import com.foldfinance.core.data.subscriptions.RefreshSubscriptionsUseCase
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Реагирует на любую запись в таблицу операций — из SMS, push, ручного ввода, проверки или
 * импорта: обновляет виджет и пересчитывает кандидатов в подписки. Одна точка вместо вызовов
 * в каждом месте записи. Подписки пишутся в свою таблицу, поэтому цикла нет.
 */
@Singleton
class TransactionChangeObserver @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: FoldFinanceDatabase,
    private val refreshSubscriptions: RefreshSubscriptionsUseCase,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @OptIn(FlowPreview::class)
    fun start() {
        scope.launch {
            database.invalidationTracker.createFlow(TRANSACTIONS_TABLE, emitInitialState = false)
                .debounce(DEBOUNCE_MS)
                .collect {
                    FinanceWidgetUpdater.requestUpdate(context)
                    try {
                        refreshSubscriptions()
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        // Не критично: экран подписок пересчитает сам при открытии.
                    }
                }
        }
    }

    private companion object {
        const val TRANSACTIONS_TABLE = "transactions"
        const val DEBOUNCE_MS = 1_500L
    }
}
