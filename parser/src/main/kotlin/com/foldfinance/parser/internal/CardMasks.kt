package com.foldfinance.parser.internal

/** Роль маски карты в тексте перевода. */
internal enum class MaskRole { PLAIN, DESTINATION, SOURCE }

/** Маска карты в виде `*1234` и её позиция (начало цифр). */
internal class CardMaskHit(val mask: String, val position: Int, val role: MaskRole)

/**
 * Поиск масок карт: `*1234`, `**1234`, `••1234`, `VISA1234`, `ECMC1234`, `MIR-1234`, `СЧЁТ1234`.
 * Работает по [TextNormalizer.matchingForm].
 */
internal object CardMasks {

    private val patterns = listOf(
        Regex("(?<![\\d*])\\*{1,2}(\\d{4})(?!\\d)"),
        Regex("[•·]{2,} ?(\\d{4})(?!\\d)"),
        Regex("(?<!\\p{L})(?:visa|ecmc|mir|maes|mc|счет)-?(\\d{4})(?!\\d)"),
    )

    private const val CARD_PREFIX = "[\\s*•·№:]*(?:(?:visa|ecmc|mir|maes|mc)-?)?$"
    private val destinationBefore = Regex("(на карту|на карте|на счет|получател\\p{L}*)$CARD_PREFIX")
    private val sourceBefore = Regex("(с карты|со счета|с счета|от карты)$CARD_PREFIX")

    fun find(lower: String): List<CardMaskHit> {
        val byPosition = sortedMapOf<Int, String>()
        for (p in patterns) {
            for (m in p.findAll(lower)) {
                val g = m.groups[1] ?: continue
                byPosition.putIfAbsent(g.range.first, g.value)
            }
        }
        return byPosition.map { (pos, digits) ->
            val window = lower.substring(maxOf(0, pos - 24), pos)
            val role = when {
                destinationBefore.containsMatchIn(window) -> MaskRole.DESTINATION
                sourceBefore.containsMatchIn(window) -> MaskRole.SOURCE
                else -> MaskRole.PLAIN
            }
            CardMaskHit("*$digits", pos, role)
        }
    }
}
