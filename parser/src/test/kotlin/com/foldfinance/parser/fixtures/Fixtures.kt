package com.foldfinance.parser.fixtures

import com.foldfinance.parser.MessageChannel
import com.foldfinance.parser.MessageMeta
import com.foldfinance.parser.ParseResult
import com.foldfinance.parser.ParsedBalance
import com.foldfinance.parser.ParsedOperation
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId

@Serializable
data class FixtureMeta(
    val channel: String,
    val sender: String,
    val receivedAt: String,
    val title: String? = null,
)

@Serializable
data class ExpectedOperation(
    val amountMinor: Long,
    val currency: String,
    val direction: String,
    val kind: String,
    val merchant: String? = null,
    val cardMask: String? = null,
    val counterpartyCardMask: String? = null,
    val occurredAt: String? = null,
    val needsReview: Boolean,
)

@Serializable
data class ExpectedBalance(
    val amountMinor: Long,
    val currency: String,
    val cardMask: String? = null,
)

@Serializable
data class FixtureExpectation(
    val description: String,
    val meta: FixtureMeta,
    val result: String,
    val parserId: String? = null,
    val reason: String? = null,
    val operations: List<ExpectedOperation> = emptyList(),
    val partial: ExpectedOperation? = null,
    val balance: ExpectedBalance? = null,
)

/** Пара «текст + ожидание» из `fixtures/<bank>/NNN`. */
data class Fixture(val bank: String, val name: String, val txt: File?, val json: File?) {
    val id: String get() = "$bank/$name"
}

object Fixtures {
    /** Пояс, в котором записаны `occurredAt` в фикстурах. */
    val ZONE: ZoneId = ZoneId.of("Europe/Moscow")

    val json = Json { ignoreUnknownKeys = false }

    private const val TXT = ".txt"
    private const val EXPECTED = ".expected.json"

    fun root(): File {
        val url = requireNotNull(Fixtures::class.java.getResource("/fixtures")) { "fixtures resource dir not found" }
        return File(url.toURI())
    }

    /** Каталоги банков (всё, кроме `raw/` — туда кладутся сырые образцы, не фикстуры). */
    fun bankDirs(): List<File> = root().listFiles().orEmpty()
        .filter { it.isDirectory && it.name != "raw" }
        .sortedBy { it.name }

    /** Все файлы фикстур, сгруппированные по имени NNN, включая непарные. */
    fun all(): List<Fixture> = bankDirs().flatMap { dir ->
        val files = dir.listFiles().orEmpty().filter { it.isFile }
        val names = files.mapNotNull { f ->
            when {
                f.name.endsWith(EXPECTED) -> f.name.removeSuffix(EXPECTED)
                f.name.endsWith(TXT) -> f.name.removeSuffix(TXT)
                else -> null
            }
        }.toSortedSet()
        names.map { n ->
            Fixture(
                bank = dir.name,
                name = n,
                txt = File(dir, n + TXT).takeIf { it.isFile },
                json = File(dir, n + EXPECTED).takeIf { it.isFile },
            )
        }
    }

    /** Файлы в каталогах банков, которые не являются ни `.txt`, ни `.expected.json`. */
    fun strayFiles(): List<File> = bankDirs().flatMap { dir ->
        dir.listFiles().orEmpty().filter { f -> !(f.name.endsWith(TXT) || f.name.endsWith(EXPECTED)) }
    }

    fun readText(file: File): String = file.readText(Charsets.UTF_8).removeSuffix("\n").removeSuffix("\r")

    fun readExpectation(file: File): FixtureExpectation =
        json.decodeFromString(FixtureExpectation.serializer(), file.readText(Charsets.UTF_8))

    fun toMeta(meta: FixtureMeta): MessageMeta = MessageMeta(
        channel = MessageChannel.valueOf(meta.channel),
        sender = meta.sender,
        receivedAtMillis = OffsetDateTime.parse(meta.receivedAt).toInstant().toEpochMilli(),
        title = meta.title,
    )

    /** Фактический результат в формате ожиданий — так Truth показывает понятный diff. */
    fun describe(result: ParseResult): FixtureExpectationBody = when (result) {
        ParseResult.NotFinancial -> FixtureExpectationBody(result = "NOT_FINANCIAL")
        is ParseResult.Parsed -> FixtureExpectationBody(
            result = "PARSED",
            parserId = result.parserId,
            operations = result.operations.map(::describe),
            balance = result.balance?.let(::describe),
        )
        is ParseResult.Uncertain -> FixtureExpectationBody(
            result = "UNCERTAIN",
            parserId = result.parserId,
            reason = result.reason,
            partial = result.partial?.let(::describe),
            balance = result.balance?.let(::describe),
        )
    }

    fun body(e: FixtureExpectation) = FixtureExpectationBody(
        result = e.result,
        parserId = e.parserId,
        reason = e.reason,
        operations = e.operations.map { it.copy(occurredAt = it.occurredAt?.let(::normalizeTime)) },
        partial = e.partial?.let { it.copy(occurredAt = it.occurredAt?.let(::normalizeTime)) },
        balance = e.balance,
    )

    private fun normalizeTime(iso: String): String =
        OffsetDateTime.parse(iso).toInstant().atZone(ZONE).toOffsetDateTime().toString()

    private fun describe(op: ParsedOperation) = ExpectedOperation(
        amountMinor = op.amountMinor,
        currency = op.currency,
        direction = op.direction.name,
        kind = op.kind.name,
        merchant = op.merchant,
        cardMask = op.cardMask,
        counterpartyCardMask = op.counterpartyCardMask,
        occurredAt = op.occurredAtMillis?.let { Instant.ofEpochMilli(it).atZone(ZONE).toOffsetDateTime().toString() },
        needsReview = op.confidence < ParsedOperation.NEEDS_REVIEW_THRESHOLD,
    )

    private fun describe(b: ParsedBalance) = ExpectedBalance(b.amountMinor, b.currency, b.cardMask)
}

/** Сравниваемая часть ожидания (без описания и метаданных). */
data class FixtureExpectationBody(
    val result: String,
    val parserId: String? = null,
    val reason: String? = null,
    val operations: List<ExpectedOperation> = emptyList(),
    val partial: ExpectedOperation? = null,
    val balance: ExpectedBalance? = null,
)
