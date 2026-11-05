package com.foldfinance.app.ui.format

/**
 * The one way a card is shown in the UI: `•• 1234` (two bullets, a no-break space, the last four
 * digits). Masks come from parsers and storage in different shapes — `*1234`, `**1234`, `••1234`,
 * `1234` — and are never shown raw, so there is no `•*1234`.
 */
object CardMaskFormat {
    /** Bullets in front of the digits. */
    const val PREFIX: String = "••"

    private const val NBSP: Char = ' '
    private const val DIGITS_SHOWN = 4

    /**
     * @param mask stored mask (`*1234`, `1234`, `••1234`…).
     * @return `•• 1234`; a mask without digits comes back trimmed, without a prefix.
     */
    fun format(mask: String): String {
        val digits = mask.filter(Char::isDigit)
        if (digits.isEmpty()) return mask.trim()
        return PREFIX + NBSP + digits.takeLast(DIGITS_SHOWN)
    }
}
