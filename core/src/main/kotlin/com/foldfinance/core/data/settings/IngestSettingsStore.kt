package com.foldfinance.core.data.settings

import javax.inject.Qualifier

/** DataStore настроек приёма сообщений (чтобы не конфликтовать с другими DataStore в графе Hilt). */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class IngestSettingsStore
