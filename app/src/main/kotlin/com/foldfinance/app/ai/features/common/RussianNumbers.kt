package com.foldfinance.app.ai.features.common

import java.math.BigDecimal
import java.util.Locale

/**
 * Russian numerals and spoken amounts → exact numbers, without a model.
 *
 * Understands words («двести пятьдесят», «тысяча двести», «полторы тысячи», «две тысячи триста
 * сорок пять»), digits as speech recognisers write them («250», «1 500», «2,5 тысячи»), and
 * roubles with kopecks («сто рублей пятьдесят копеек», «250,50»). Never uses floating point.
 *
 * Grammar of one number: groups of `hundreds? tens? units?` (or teens, or digits) separated by
 * descending scales (миллион > тысяча). A word that cannot continue the current number
 * («двести двести», «пять три») ends it, so a phrase may contain several numbers.
 */
object RussianNumbers {

    /** A number found in a text: `[start, end)` in the original string. */
    data class Span(val start: Int, val end: Int, val value: BigDecimal)

    /** An amount found in a text, in minor units. */
    data class AmountSpan(val start: Int, val end: Int, val minor: Long)

    private enum class Slot(val rank: Int) { UNIT(1), TEN(2), TEEN(2), HUNDRED(3) }

    private data class Word(val value: Long, val slot: Slot)

    private val words: Map<String, Word> = buildMap {
        fun put(value: Long, slot: Slot, vararg forms: String) = forms.forEach { put(it, Word(value, slot)) }
        put(0, Slot.UNIT, "ноль", "нуль")
        put(1, Slot.UNIT, "один", "одна", "одно", "одну")
        put(2, Slot.UNIT, "два", "две")
        put(3, Slot.UNIT, "три")
        put(4, Slot.UNIT, "четыре")
        put(5, Slot.UNIT, "пять")
        put(6, Slot.UNIT, "шесть")
        put(7, Slot.UNIT, "семь")
        put(8, Slot.UNIT, "восемь")
        put(9, Slot.UNIT, "девять")
        put(10, Slot.TEEN, "десять")
        put(11, Slot.TEEN, "одиннадцать")
        put(12, Slot.TEEN, "двенадцать")
        put(13, Slot.TEEN, "тринадцать")
        put(14, Slot.TEEN, "четырнадцать")
        put(15, Slot.TEEN, "пятнадцать")
        put(16, Slot.TEEN, "шестнадцать")
        put(17, Slot.TEEN, "семнадцать")
        put(18, Slot.TEEN, "восемнадцать")
        put(19, Slot.TEEN, "девятнадцать")
        put(20, Slot.TEN, "двадцать")
        put(30, Slot.TEN, "тридцать")
        put(40, Slot.TEN, "сорок")
        put(50, Slot.TEN, "пятьдесят")
        put(60, Slot.TEN, "шестьдесят")
        put(70, Slot.TEN, "семьдесят")
        put(80, Slot.TEN, "восемьдесят")
        put(90, Slot.TEN, "девяносто")
        put(100, Slot.HUNDRED, "сто", "сотня", "сотню")
        put(200, Slot.HUNDRED, "двести")
        put(300, Slot.HUNDRED, "триста")
        put(400, Slot.HUNDRED, "четыреста")
        put(500, Slot.HUNDRED, "пятьсот")
        put(600, Slot.HUNDRED, "шестьсот")
        put(700, Slot.HUNDRED, "семьсот")
        put(800, Slot.HUNDRED, "восемьсот")
        put(900, Slot.HUNDRED, "девятьсот")
    }

    private val scales: Map<String, Long> = buildMap {
        listOf("тысяча", "тысячи", "тысяч", "тысячу", "тыща", "тыщи", "тыщ", "тыщу", "тыс").forEach { put(it, 1_000L) }
        listOf("миллион", "миллиона", "миллионов", "млн").forEach { put(it, 1_000_000L) }
    }

    private val halves = setOf("полтора", "полторы")
    private val majorUnits = setOf("рубль", "рубля", "рублей", "руб", "р", "₽", "рэ")
    private val minorUnits = setOf("копейка", "копейки", "копеек", "копейку", "коп")

    private data class Token(val text: String, val start: Int, val end: Int) {
        val isDigits: Boolean get() = text.first().isDigit()
    }

    private val tokenRegex = Regex("""\d+(?:[.,]\d+)?|[\p{L}]+|₽""")

    private fun tokens(text: String): List<Token> =
        tokenRegex.findAll(text).map { m ->
            Token(m.value.lowercase(Locale.ROOT).replace('ё', 'е'), m.range.first, m.range.last + 1)
        }.toList()

    private data class Read(val value: BigDecimal, val next: Int, val wordsOnly: Boolean)

    /** Reads the longest number starting at token [from]. */
    private fun readNumber(text: String, toks: List<Token>, from: Int): Read? {
        var i = from
        var total = BigDecimal.ZERO
        var group = BigDecimal.ZERO
        var groupHas = false
        var lastRank = 4
        var lastScale = Long.MAX_VALUE
        var consumed = false
        var wordsOnly = true
        while (i < toks.size) {
            val t = toks[i]
            val word = words[t.text]
            val scale = scales[t.text]
            when {
                t.isDigits -> {
                    if (groupHas) break
                    val (value, next) = readDigits(text, toks, i)
                    group = value
                    groupHas = true
                    lastRank = 0
                    wordsOnly = false
                    i = next
                    consumed = true
                    continue
                }
                t.text in halves -> {
                    if (groupHas) break
                    group = BigDecimal("1.5")
                    groupHas = true
                    lastRank = 0
                }
                word != null -> {
                    if (word.slot.rank >= lastRank) break
                    group += BigDecimal.valueOf(word.value)
                    groupHas = true
                    lastRank = if (word.slot == Slot.TEEN) 1 else word.slot.rank
                }
                scale != null -> {
                    if (scale >= lastScale) break
                    total += (if (groupHas) group else BigDecimal.ONE) * BigDecimal.valueOf(scale)
                    group = BigDecimal.ZERO
                    groupHas = false
                    lastRank = 4
                    lastScale = scale
                }
                else -> break
            }
            consumed = true
            i++
        }
        if (!consumed) return null
        return Read(total + group, i, wordsOnly)
    }

    /** Digits, joining «1 500» / «12 345 678» written with group spaces. */
    private fun readDigits(text: String, toks: List<Token>, from: Int): Pair<BigDecimal, Int> {
        val first = toks[from]
        var digits = first.text
        var i = from + 1
        if (digits.length <= 3 && digits.all(Char::isDigit)) {
            while (i < toks.size) {
                val t = toks[i]
                val gap = text.substring(toks[i - 1].end, t.start)
                val isGroup = t.text.length == 3 && t.text.all(Char::isDigit) ||
                    t.text.length > 3 && t.text[3].let { it == ',' || it == '.' } && t.text.take(3).all(Char::isDigit)
                if (!isGroup || gap.isEmpty() || !gap.all { it == ' ' || it == ' ' || it == ' ' || it == ' ' }) break
                digits += t.text
                i++
            }
        }
        return BigDecimal(digits.replace(',', '.')) to i
    }

    /** Every number in [text] (words and digits), in order. Used by the hallucination guard. */
    fun findNumbers(text: String): List<Span> {
        val toks = tokens(text)
        val out = mutableListOf<Span>()
        var i = 0
        while (i < toks.size) {
            val read = readNumber(text, toks, i)
            if (read == null) {
                i++
                continue
            }
            out += Span(toks[i].start, toks[read.next - 1].end, read.value)
            i = read.next
        }
        return out
    }

    /** Spelled-out numbers only (no digits): «две», «тысяча двести». */
    fun findSpelledNumbers(text: String): List<Span> {
        val toks = tokens(text)
        val out = mutableListOf<Span>()
        var i = 0
        while (i < toks.size) {
            val read = readNumber(text, toks, i)
            if (read == null) {
                i++
                continue
            }
            if (read.wordsOnly) out += Span(toks[i].start, toks[read.next - 1].end, read.value)
            i = read.next
        }
        return out
    }

    /** Amounts in [text] with optional «рублей … копеек», in minor units; zero amounts are skipped. */
    fun findAmounts(text: String, fractionDigits: Int = 2): List<AmountSpan> {
        val toks = tokens(text)
        val out = mutableListOf<AmountSpan>()
        var i = 0
        while (i < toks.size) {
            val amount = readAmount(text, toks, i, fractionDigits)
            if (amount == null) {
                i++
                continue
            }
            if (amount.first > 0) out += AmountSpan(toks[i].start, toks[amount.second - 1].end, amount.first)
            i = amount.second
        }
        return out
    }

    /**
     * [fragment] as one amount, in minor units: the whole fragment must be a number with optional
     * currency words («двести пятьдесят», «250 р», «сто рублей пятьдесят копеек»). `null` otherwise.
     */
    fun parseAmountMinor(fragment: String, fractionDigits: Int = 2): Long? {
        val toks = tokens(fragment)
        if (toks.isEmpty()) return null
        val amount = readAmount(fragment, toks, 0, fractionDigits) ?: return null
        if (amount.second != toks.size) return null
        return amount.first.takeIf { it > 0 }
    }

    /** (minor, next token). Major part with optional currency word, then optional kopecks. */
    private fun readAmount(text: String, toks: List<Token>, from: Int, fractionDigits: Int): Pair<Long, Int>? {
        val major = readNumber(text, toks, from) ?: return null
        var i = major.next
        var minorPart = BigDecimal.ZERO
        if (i < toks.size && toks[i].text in majorUnits) {
            i++
            val kop = readNumber(text, toks, i)
            if (kop != null && kop.next < toks.size && toks[kop.next].text in minorUnits) {
                minorPart = kop.value
                i = kop.next + 1
            }
        } else if (i < toks.size && toks[i].text in minorUnits) {
            // «пятьдесят копеек» alone.
            val scaleMinor = BigDecimal.TEN.pow(fractionDigits)
            if (major.value.scale() > 0 && major.value.stripTrailingZeros().scale() > 0) return null
            return runCatching { major.value.toBigIntegerExact().toLong() }.getOrNull()
                ?.takeIf { BigDecimal.valueOf(it) < scaleMinor }
                ?.let { it to i + 1 }
        }
        val scale = BigDecimal.TEN.pow(fractionDigits)
        if (minorPart >= scale || minorPart.stripTrailingZeros().scale() > 0) return null
        val minor = major.value * scale + minorPart
        return runCatching { minor.stripTrailingZeros().toBigIntegerExact().toLong() }.getOrNull()?.let { it to i }
    }
}
