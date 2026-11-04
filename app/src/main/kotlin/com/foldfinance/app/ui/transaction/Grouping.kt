package com.foldfinance.app.ui.transaction

import com.foldfinance.core.model.Transaction
import java.time.LocalDate
import java.time.ZoneId

/** Consecutive runs of [items] by local date in [zone], keeping the order of [items]. */
fun groupByDay(items: List<Transaction>, zone: ZoneId): List<Pair<LocalDate, List<Transaction>>> {
    val result = ArrayList<Pair<LocalDate, List<Transaction>>>()
    var date: LocalDate? = null
    var run = ArrayList<Transaction>()
    for (tx in items) {
        val d = tx.occurredAt.atZone(zone).toLocalDate()
        if (d != date) {
            date?.let { result += it to run }
            date = d
            run = ArrayList()
        }
        run += tx
    }
    date?.let { result += it to run }
    return result
}