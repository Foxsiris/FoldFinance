package com.foldfinance.core.data.backup

import com.foldfinance.core.data.db.FoldFinanceDatabase
import com.foldfinance.core.model.ParseStatus
import com.foldfinance.core.model.Source
import com.foldfinance.core.model.SubscriptionPeriod
import com.foldfinance.core.model.SubscriptionStatus
import com.foldfinance.parser.Direction
import com.foldfinance.parser.Kind
import com.foldfinance.parser.MessageChannel
import java.util.Currency

/**
 * Снимок не прошёл проверку и не может быть восстановлен. Данные на устройстве не тронуты.
 *
 * [problem] описывает место ошибки (таблица, индекс строки, поле) и никогда не содержит
 * значений из файла — ни сумм, ни текстов, ни масок карт: сообщение может попасть в лог.
 */
class InvalidBackupException(val reason: Reason, val problem: String) : Exception("invalid backup: $problem") {
    enum class Reason {
        /** Это не снимок FoldFinance. */
        FOREIGN_FORMAT,

        /** Снимок сделан более новой версией приложения. */
        NEWER_VERSION,

        /** Содержимое нарушает инварианты (суммы, валюты, ссылки, уникальность). */
        INVALID_CONTENT,
    }
}

/**
 * Проверка снимка перед восстановлением: версия, обязательные поля, инварианты домена
 * (суммы операций > 0, валюта — ISO 4217, известные значения перечислений), уникальность
 * ключей и целостность ссылок между таблицами. Ошибка → [InvalidBackupException].
 */
object BackupValidator {

    private val ISO_CODE = Regex("^[A-Z]{3}$")

    fun validate(snapshot: BackupSnapshot) {
        if (snapshot.format != BackupSnapshot.FORMAT_ID) {
            throw InvalidBackupException(InvalidBackupException.Reason.FOREIGN_FORMAT, "unknown format id")
        }
        if (snapshot.schemaVersion > BackupSnapshot.SCHEMA_VERSION || snapshot.dbVersion > FoldFinanceDatabase.VERSION) {
            throw InvalidBackupException(InvalidBackupException.Reason.NEWER_VERSION, "created by a newer app version")
        }
        if (snapshot.schemaVersion < 1 || snapshot.dbVersion < 1) invalid("unsupported version")

        val categoryIds = snapshot.categories.uniqueIds("categories") { it.id }
        snapshot.categories.unique("categories.key") { it.key }
        snapshot.categories.forEachIndexed { i, c ->
            check(c.key.isNotBlank(), "categories[$i].key")
            check(c.name.isNotBlank(), "categories[$i].name")
            check(c.iconKey.isNotBlank(), "categories[$i].iconKey")
        }

        snapshot.merchantRules.uniqueIds("merchantRules") { it.id }
        snapshot.merchantRules.unique("merchantRules.merchantPattern") { it.merchantPattern }
        snapshot.merchantRules.forEachIndexed { i, r ->
            check(r.merchantPattern.isNotBlank(), "merchantRules[$i].merchantPattern")
            check(r.categoryId in categoryIds, "merchantRules[$i].categoryId")
        }

        snapshot.cards.unique("cards.mask") { it.mask }
        snapshot.cards.forEachIndexed { i, c -> check(c.mask.isNotBlank(), "cards[$i].mask") }

        val txIds = snapshot.transactions.uniqueIds("transactions") { it.id }
        snapshot.transactions.unique("transactions.dedupKey") { it.dedupKey }
        snapshot.transactions.forEachIndexed { i, t ->
            val at = "transactions[$i]"
            check(t.amountMinor > 0, "$at.amountMinor")
            check(isIsoCurrency(t.currency), "$at.currency")
            check(t.direction in Direction.entries.names(), "$at.direction")
            check(t.parsedDirection in Direction.entries.names(), "$at.parsedDirection")
            check(t.kind in Kind.entries.names(), "$at.kind")
            check(t.source in Source.entries.names(), "$at.source")
            check(t.dedupKey.isNotBlank(), "$at.dedupKey")
            check(t.confidence in 0f..1f, "$at.confidence")
            check(t.opIndex >= 0, "$at.opIndex")
            check(t.categoryId == null || t.categoryId in categoryIds, "$at.categoryId")
            check(t.linkedTransactionId == null || t.linkedTransactionId in txIds, "$at.linkedTransactionId")
        }

        val rawIds = snapshot.rawMessages.uniqueIds("rawMessages") { it.id }
        snapshot.rawMessages.unique("rawMessages.source") { it.channel to it.sourceKey }
        snapshot.rawMessages.forEachIndexed { i, r ->
            val at = "rawMessages[$i]"
            check(r.channel in MessageChannel.entries.names(), "$at.channel")
            check(r.parseStatus in ParseStatus.entries.names(), "$at.parseStatus")
            check(r.sourceKey.isNotBlank(), "$at.sourceKey")
            check(r.sender.isNotBlank(), "$at.sender")
            check(r.textHash.isNotBlank(), "$at.textHash")
            check(r.parserVersion >= 0, "$at.parserVersion")
            check(r.transactionId == null || r.transactionId in txIds, "$at.transactionId")
        }

        snapshot.knownBalances.uniqueIds("knownBalances") { it.id }
        snapshot.knownBalances.mapNotNull { it.rawMessageId }.let { ids ->
            if (ids.size != ids.toSet().size) invalid("knownBalances.rawMessageId is not unique")
        }
        snapshot.knownBalances.forEachIndexed { i, b ->
            val at = "knownBalances[$i]"
            check(b.cardMask.isNotBlank(), "$at.cardMask")
            check(isIsoCurrency(b.currency), "$at.currency")
            check(b.rawMessageId == null || b.rawMessageId in rawIds, "$at.rawMessageId")
        }

        snapshot.merchantAliases.unique("merchantAliases.merchantKey") { it.merchantKey }
        snapshot.merchantAliases.forEachIndexed { i, a ->
            val at = "merchantAliases[$i]"
            check(a.merchantKey.isNotBlank(), "$at.merchantKey")
            check(a.aiCategoryId == null || a.aiCategoryId in categoryIds, "$at.aiCategoryId")
        }

        snapshot.subscriptions.uniqueIds("subscriptions") { it.id }
        snapshot.subscriptions.forEachIndexed { i, s ->
            val at = "subscriptions[$i]"
            check(s.merchant.isNotBlank(), "$at.merchant")
            check(s.amountMinor > 0, "$at.amountMinor")
            check(isIsoCurrency(s.currency), "$at.currency")
            check(s.period in SubscriptionPeriod.entries.names(), "$at.period")
            check(s.status in SubscriptionStatus.entries.names(), "$at.status")
            check(s.categoryId == null || s.categoryId in categoryIds, "$at.categoryId")
        }
    }

    /** Три заглавные латинские буквы и код известен `java.util.Currency`. */
    fun isIsoCurrency(code: String): Boolean =
        ISO_CODE.matches(code) && runCatching { Currency.getInstance(code) }.isSuccess

    private fun check(condition: Boolean, field: String) {
        if (!condition) invalid("$field is invalid")
    }

    private fun invalid(problem: String): Nothing =
        throw InvalidBackupException(InvalidBackupException.Reason.INVALID_CONTENT, problem)

    private fun <T> List<T>.uniqueIds(table: String, id: (T) -> Long): Set<Long> {
        val ids = HashSet<Long>(size)
        forEachIndexed { i, row ->
            val value = id(row)
            if (value <= 0) invalid("$table[$i].id is invalid")
            if (!ids.add(value)) invalid("$table[$i].id is duplicated")
        }
        return ids
    }

    private fun <T, K> List<T>.unique(field: String, key: (T) -> K) {
        val seen = HashSet<K>(size)
        forEachIndexed { i, row -> if (!seen.add(key(row))) invalid("$field[$i] is duplicated") }
    }

    private fun <E : Enum<E>> List<E>.names(): Set<String> = mapTo(HashSet()) { it.name }
}
