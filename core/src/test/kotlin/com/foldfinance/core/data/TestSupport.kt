package com.foldfinance.core.data

import androidx.test.core.app.ApplicationProvider
import com.foldfinance.core.data.db.FoldFinanceDatabase
import com.foldfinance.core.data.ingest.IncomingMessage
import com.foldfinance.parser.BankParser
import com.foldfinance.parser.Direction
import com.foldfinance.parser.Kind
import com.foldfinance.parser.MessageChannel
import com.foldfinance.parser.MessageMeta
import com.foldfinance.parser.ParseResult
import com.foldfinance.parser.ParsedOperation
import com.foldfinance.parser.ParserRegistry
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/** In-memory БД без шифрования (нативный SQLCipher в Robolectric недоступен) — та же сборка, что в проде. */
fun inMemoryDb(): FoldFinanceDatabase =
    FoldFinanceDatabase.build(ApplicationProvider.getApplicationContext(), openHelperFactory = null, name = null)

val T0: Instant = Instant.parse("2026-10-01T10:00:00Z")

fun fixedClock(at: Instant = T0): Clock = Clock.fixed(at, ZoneOffset.UTC)

/** Фейковый парсер: результат по точному тексту, всё остальное — NotFinancial. Не зависит от `:parser`-правил. */
class ScriptedParser(private val results: Map<String, ParseResult>) : BankParser {
    override val id: String = "scripted"
    override fun canHandle(meta: MessageMeta): Boolean = true
    override fun parse(text: String, meta: MessageMeta): ParseResult = results[text] ?: ParseResult.NotFinancial
}

fun registryOf(vararg results: Pair<String, ParseResult>): ParserRegistry =
    ScriptedParser(results.toMap()).let { ParserRegistry(parsers = listOf(it), fallback = it) }

fun op(
    amountMinor: Long,
    direction: Direction = Direction.EXPENSE,
    kind: Kind = Kind.PURCHASE,
    merchant: String? = null,
    cardMask: String? = "*1234",
    counterpartyCardMask: String? = null,
    occurredAt: Instant? = null,
    confidence: Float = 0.95f,
    currency: String = "RUB",
) = ParsedOperation(
    amountMinor = amountMinor,
    currency = currency,
    direction = direction,
    kind = kind,
    merchant = merchant,
    cardMask = cardMask,
    counterpartyCardMask = counterpartyCardMask,
    occurredAtMillis = occurredAt?.toEpochMilli(),
    confidence = confidence,
)

fun parsed(vararg ops: ParsedOperation, balance: com.foldfinance.parser.ParsedBalance? = null) =
    ParseResult.Parsed(operations = ops.toList(), balance = balance, parserId = "scripted")

fun sms(id: Long, text: String, at: Instant, sender: String = "900") =
    IncomingMessage(MessageChannel.SMS, sender, id.toString(), at, text)

fun push(key: String, text: String, at: Instant, pkg: String = "ru.bank.app") =
    IncomingMessage(MessageChannel.PUSH, pkg, "$key|${at.toEpochMilli()}", at, text, title = "Bank")
