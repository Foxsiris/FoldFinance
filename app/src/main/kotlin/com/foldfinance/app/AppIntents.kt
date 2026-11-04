package com.foldfinance.app

/** Контракт интентов MainActivity (виджет, ярлыки). */
object AppIntents {
    /** Открыть ручное добавление операции (кнопка «+» виджета). */
    const val ACTION_QUICK_ADD = "com.foldfinance.app.action.QUICK_ADD"

    /** Открыть очередь проверки (счётчик «на проверку» виджета). */
    const val ACTION_OPEN_REVIEW = "com.foldfinance.app.action.OPEN_REVIEW"
}
