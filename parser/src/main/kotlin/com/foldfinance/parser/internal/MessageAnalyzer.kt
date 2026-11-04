package com.foldfinance.parser.internal

import com.foldfinance.parser.AmountParser
import com.foldfinance.parser.AmountSign
import com.foldfinance.parser.Direction
import com.foldfinance.parser.Kind
import com.foldfinance.parser.MessageMeta
import com.foldfinance.parser.ParseResult
import com.foldfinance.parser.ParsedAmount
import com.foldfinance.parser.ParsedBalance
import com.foldfinance.parser.ParsedOperation
import com.foldfinance.parser.UncertainReasons
import java.time.ZoneId

/**
 * Настройки банка для общего движка.
 *
 * @property baseConfidence уверенность для полностью распознанной операции.
 * @property maxConfidence верхняя граница уверенности (у GenericParser ≤ 0.6).
 */
internal data class BankProfile(
    val parserId: String,
    val baseConfidence: Float,
    val maxConfidence: Float = 1f,
)

/**
 * Общий движок разбора: ключевые слова операции + суммы + маски карт + остаток + время.
 *
 * Порядок решений:
 * 1. пусто → NotFinancial; слишком длинно → Uncertain;
 * 2. код подтверждения → NotFinancial (даже если в нём есть сумма);
 * 3. реклама без маски карты и без остатка → NotFinancial; отказ/отклонение → NotFinancial;
 * 4. вид операции по самому раннему ключевому слову; нет слова → Uncertain (если есть сумма);
 * 5. сумма операции — первая сумма, не являющаяся остатком/комиссией; комиссии — отдельные операции FEE.
 */
internal class MessageAnalyzer(private val profile: BankProfile, private val zone: ZoneId) {

    private enum class Role { PLAIN, BALANCE, FEE }

    fun analyze(text: String, meta: MessageMeta): ParseResult = try {
        analyzeUnsafe(text, meta)
    } catch (e: RuntimeException) {
        ParseResult.Uncertain(reason = UncertainReasons.PARSER_ERROR, parserId = profile.parserId)
    }

    private fun analyzeUnsafe(text: String, meta: MessageMeta): ParseResult {
        val title = meta.title?.takeIf { it.isNotBlank() }
        val combined = if (title != null) "$title\n$text" else text
        if (combined.isBlank()) return ParseResult.NotFinancial
        if (combined.length > UncertainReasons.MAX_TEXT_LENGTH) {
            return uncertain(UncertainReasons.TEXT_TOO_LONG)
        }
        val norm = TextNormalizer.normalize(combined)
        val lower = TextNormalizer.matchingForm(norm)

        if (Signals.isOneTimeCode(lower)) return ParseResult.NotFinancial

        val amounts = AmountParser.findAll(norm)
        val roles = assignRoles(amounts, lower)
        val masks = CardMasks.find(lower)
        val balanceAmount = amounts.indices.firstOrNull { roles[it] == Role.BALANCE }?.let { amounts[it] }

        if (Signals.looksLikePromo(lower) && masks.isEmpty() && balanceAmount == null &&
            !Signals.reportsCompletedOperation(lower)
        ) {
            return ParseResult.NotFinancial
        }
        val firstPlain = amounts.indices.firstOrNull { roles[it] == Role.PLAIN }
        val declinedAt = Signals.declinedAt(lower)
        if (declinedAt != null && (firstPlain == null || declinedAt < amounts[firstPlain].range.first)) {
            return ParseResult.NotFinancial
        }

        val keyword = Signals.findOperation(lower)
        val time = TimeExtractor.extract(lower, meta.receivedAtMillis, zone)

        if (keyword == null) {
            val candidate = firstPlain?.let { amounts[it] }
            val own = masks.firstOrNull()?.mask
            val balance = balanceOf(balanceAmount, own)
            if (candidate == null) {
                return if (balance != null) uncertain(UncertainReasons.BALANCE_ONLY, balance = balance)
                else ParseResult.NotFinancial
            }
            if (candidate.amountMinor <= 0L) return uncertain(UncertainReasons.ZERO_AMOUNT, balance = balance)
            val partial = operation(
                amount = candidate,
                direction = if (candidate.sign == AmountSign.PLUS) Direction.INCOME else Direction.EXPENSE,
                kind = Kind.UNKNOWN,
                merchant = extractMerchant(norm, lower, candidate.range.last + 1),
                cardMask = own,
                counterparty = null,
                time = time,
                confidence = UNKNOWN_CONFIDENCE,
            )
            return uncertain(UncertainReasons.UNKNOWN_OPERATION, partial, balance)
        }

        var type = keyword.type
        val firstNonBalance = amounts.indices.firstOrNull { roles[it] != Role.BALANCE }
        val verbPart = lower.substring(0, firstNonBalance?.let { amounts[it].range.first } ?: lower.length)
        if (type == OpType.PURCHASE) {
            if (Signals.subscription.containsMatchIn(lower)) {
                type = OpType.SUBSCRIPTION
            } else if (Signals.feePrefix.containsMatchIn(verbPart)) {
                type = OpType.FEE
            }
        }
        if (type == OpType.TOP_UP && ("перевод" in lower || masks.any { it.role == MaskRole.SOURCE })) {
            type = OpType.TRANSFER
        }

        val opIndex = if (type == OpType.FEE) firstNonBalance else firstPlain
        if (opIndex == null) {
            return uncertain(UncertainReasons.AMOUNT_NOT_FOUND, balance = balanceOf(balanceAmount, masks.firstOrNull()?.mask))
        }
        val opAmount = amounts[opIndex]

        // Направление и маски.
        var direction: Direction? = null
        var kind: Kind
        var counterparty: String? = null
        var own: String? = masks.firstOrNull()?.mask
        when (type) {
            OpType.TRANSFER -> {
                kind = Kind.TRANSFER
                val incoming = Signals.incomingMarkers.containsMatchIn(lower) || opAmount.sign == AmountSign.PLUS
                val outgoingExplicit = Signals.outgoingMarkers.containsMatchIn(lower) || opAmount.sign == AmountSign.MINUS
                val hasDestination = masks.any { it.role == MaskRole.DESTINATION }
                direction = when {
                    incoming && !outgoingExplicit -> Direction.INCOME
                    !incoming && (outgoingExplicit || hasDestination) -> Direction.EXPENSE
                    else -> null
                }
                val counterpartyHit = when (direction) {
                    Direction.INCOME -> masks.firstOrNull { it.role == MaskRole.SOURCE }
                    Direction.EXPENSE -> masks.firstOrNull { it.role == MaskRole.DESTINATION }
                    else -> null
                }
                counterparty = counterpartyHit?.mask
                own = masks.firstOrNull { it !== counterpartyHit }?.mask
            }
            else -> {
                val mapped = mapType(type)
                direction = mapped.first
                kind = mapped.second
            }
        }

        val balance = balanceOf(balanceAmount, own)
        val merchant = if (type == OpType.FX) null else extractMerchant(norm, lower, opAmount.range.last + 1)

        var confidence = profile.baseConfidence
        if (own == null) confidence -= 0.1f
        if ((kind == Kind.PURCHASE || kind == Kind.SUBSCRIPTION) && merchant == null) confidence -= 0.1f
        val unexplained = amounts.indices.any { i ->
            i != opIndex && roles[i] == Role.PLAIN && amounts[i].currency == opAmount.currency
        }
        if (unexplained) confidence -= 0.15f
        // «ХАЛВА пополнена 25000.00 р.»: без карты и источника это может быть и доход,
        // и перевод себе (погашение кредитки) — не угадываем, отправляем на проверку.
        if (kind == Kind.TOP_UP && own == null) confidence = minOf(confidence, LOW_CONFIDENCE)

        if (opAmount.amountMinor <= 0L) return uncertain(UncertainReasons.ZERO_AMOUNT, balance = balance)

        fun main(dir: Direction, conf: Float) = operation(
            amount = opAmount,
            direction = dir,
            kind = kind,
            merchant = merchant,
            cardMask = own,
            counterparty = counterparty,
            time = time,
            confidence = conf,
        )

        if (direction == null) {
            return uncertain(UncertainReasons.DIRECTION_UNKNOWN, main(Direction.EXPENSE, minOf(confidence, LOW_CONFIDENCE)), balance)
        }
        if (type == OpType.FX) {
            return uncertain(UncertainReasons.FX_EXCHANGE, main(direction, minOf(confidence, LOW_CONFIDENCE)), balance)
        }
        if (opAmount.isAmbiguous) {
            return uncertain(UncertainReasons.AMBIGUOUS_AMOUNT, main(direction, minOf(confidence, LOW_CONFIDENCE)), balance)
        }

        val operations = mutableListOf(main(direction, confidence))
        if (type != OpType.FEE) {
            amounts.forEachIndexed { i, fee ->
                if (roles[i] == Role.FEE && fee.amountMinor > 0L) {
                    operations += ParsedOperation(
                        amountMinor = fee.amountMinor,
                        currency = requireNotNull(fee.currency),
                        direction = Direction.EXPENSE,
                        kind = Kind.FEE,
                        merchant = null,
                        cardMask = own,
                        counterpartyCardMask = null,
                        occurredAtMillis = time,
                        confidence = clamp(if (fee.isAmbiguous) minOf(confidence, LOW_CONFIDENCE) else confidence),
                    )
                }
            }
        }
        return ParseResult.Parsed(operations = operations, balance = balance, parserId = profile.parserId)
    }

    private fun mapType(type: OpType): Pair<Direction, Kind> = when (type) {
        OpType.PURCHASE -> Direction.EXPENSE to Kind.PURCHASE
        OpType.SUBSCRIPTION -> Direction.EXPENSE to Kind.SUBSCRIPTION
        OpType.REFUND -> Direction.REFUND to Kind.REFUND
        OpType.CASH_WITHDRAWAL -> Direction.EXPENSE to Kind.CASH_WITHDRAWAL
        OpType.CASH_DEPOSIT -> Direction.INCOME to Kind.TOP_UP
        OpType.TOP_UP -> Direction.INCOME to Kind.TOP_UP
        OpType.FEE -> Direction.EXPENSE to Kind.FEE
        OpType.CREDIT_IN -> Direction.INCOME to Kind.CREDIT
        OpType.CREDIT_OUT -> Direction.EXPENSE to Kind.CREDIT
        OpType.FX -> Direction.EXPENSE to Kind.FX
        OpType.TRANSFER -> Direction.EXPENSE to Kind.TRANSFER
    }

    /** Остаток/комиссия определяются по ближайшему ключевому слову перед суммой. */
    private fun assignRoles(amounts: List<ParsedAmount>, lower: String): List<Role> {
        var prevEnd = 0
        return amounts.map { a ->
            val from = maxOf(prevEnd, a.range.first - ROLE_WINDOW)
            val window = lower.substring(from, a.range.first)
            prevEnd = a.range.last + 1
            val balanceAt = Signals.balanceKeyword.findAll(window).lastOrNull()?.range?.first ?: -1
            val feeAt = Signals.feeKeyword.findAll(window)
                .lastOrNull { m -> !window.substring(maxOf(0, m.range.first - 4), m.range.first).endsWith("без ") }
                ?.range?.first ?: -1
            when {
                balanceAt < 0 && feeAt < 0 -> Role.PLAIN
                balanceAt > feeAt -> Role.BALANCE
                else -> Role.FEE
            }
        }
    }

    private fun balanceOf(amount: ParsedAmount?, cardMask: String?): ParsedBalance? {
        val currency = amount?.currency ?: return null
        if (amount.isAmbiguous) return null
        return ParsedBalance(amountMinor = amount.amountMinor, currency = currency, cardMask = cardMask)
    }

    private fun extractMerchant(norm: String, lower: String, from: Int): String? {
        var i = from
        while (i < norm.length && norm[i] in LEADING_SEPARATORS) i++
        if (i >= norm.length) return null
        // «на карту *5678», «с карты *9012», «Баланс: …» сразу после суммы — это не мерчант.
        if (MERCHANT_REJECT_PREFIXES.any { lower.startsWith(it, i) }) return null
        LEADING_PREPOSITION.find(lower.substring(i))?.let { i += it.value.length }
        val end = MERCHANT_TERMINATOR.find(lower, i)?.range?.first ?: norm.length
        if (end <= i) return null
        val candidate = norm.substring(i, end).trim().trimEnd('.', ',', ';', ':').trim()
        if (candidate.isEmpty() || candidate.length > MAX_MERCHANT_LENGTH) return null
        val lc = TextNormalizer.matchingForm(candidate)
        if (MERCHANT_REJECT_PREFIXES.any { lc.startsWith(it) }) return null
        if (candidate.none { it.isLetter() }) return null
        // Ссылка банка в конце SMS («https://scb.im/hva») — не название магазина.
        if (URL_LIKE.containsMatchIn(lc)) return null
        return candidate
    }

    private fun operation(
        amount: ParsedAmount,
        direction: Direction,
        kind: Kind,
        merchant: String?,
        cardMask: String?,
        counterparty: String?,
        time: Long?,
        confidence: Float,
    ): ParsedOperation {
        val currency = requireNotNull(amount.currency) { "amount without currency" }
        return ParsedOperation(
            amountMinor = amount.amountMinor,
            currency = currency,
            direction = direction,
            kind = kind,
            merchant = merchant,
            cardMask = cardMask,
            counterpartyCardMask = counterparty,
            occurredAtMillis = time,
            confidence = clamp(confidence),
        )
    }

    private fun clamp(confidence: Float): Float = confidence.coerceIn(0f, profile.maxConfidence)

    private fun uncertain(reason: String, partial: ParsedOperation? = null, balance: ParsedBalance? = null) =
        ParseResult.Uncertain(reason = reason, partial = partial, balance = balance, parserId = profile.parserId)

    private companion object {
        const val ROLE_WINDOW = 30
        const val MAX_MERCHANT_LENGTH = 64
        private val URL_LIKE = Regex("^(https?://|www\\.)")
        const val UNKNOWN_CONFIDENCE = 0.3f
        const val LOW_CONFIDENCE = 0.4f
        const val LEADING_SEPARATORS = " .,;:-–—)\n"
        val LEADING_PREPOSITION = Regex("^(?:в|во|at|у|от|from)\\s+")
        val MERCHANT_TERMINATOR = Regex(
            "\\.(?:\\s|$)|;|,\\s|\\n|\\s(?=(?:баланс|остаток|доступно|карта|карты|карте|комисс|счет|по курсу))",
        )
        val MERCHANT_REJECT_PREFIXES = listOf(
            "на карт", "на счет", "с карт", "со счет", "карта", "карты", "карте", "карту", "счет ", "счет*", "счета",
            "*", "баланс", "остаток", "доступно",
            "комисс", "по курсу",
        )
    }
}
