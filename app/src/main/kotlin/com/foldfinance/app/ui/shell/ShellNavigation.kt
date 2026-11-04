package com.foldfinance.app.ui.shell

import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf

/**
 * Cross-section navigation for screens: «Вся история», «Разобрать N», a tap on an operation in
 * «Обзор» that opens it in «История». Provided by [FoldFinanceAppUi] from [ShellViewModel].
 */
@Stable
interface ShellNavigation {
    /** Operation History should open (then call [consumePendingTransaction]); `null` — nothing. */
    val pendingTransactionId: Long?

    fun openSection(section: Section)

    fun openTransaction(transactionId: Long)

    fun consumePendingTransaction()

    companion object {
        /** No shell around (tests, previews): requests go nowhere. */
        val None: ShellNavigation = object : ShellNavigation {
            override val pendingTransactionId: Long? = null
            override fun openSection(section: Section) = Unit
            override fun openTransaction(transactionId: Long) = Unit
            override fun consumePendingTransaction() = Unit
        }
    }
}

val LocalShellNavigation = compositionLocalOf { ShellNavigation.None }
