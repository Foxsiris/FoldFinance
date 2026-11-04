package com.foldfinance.core.domain.category

import com.foldfinance.core.model.Category
import com.foldfinance.core.model.DefaultCategories
import com.foldfinance.core.model.MerchantRule
import com.foldfinance.parser.Direction
import com.foldfinance.parser.Kind

/**
 * Автокатегоризация операции.
 *
 * Порядок принятия решения:
 * 1. [Direction.INTERNAL_TRANSFER] → «Переводы» (всегда: такие операции исключены из итогов,
 *    другая категория для них смысла не имеет).
 * 2. Правило пользователя ([MerchantRule]): сначала точное совпадение канонического названия
 *    ([MerchantNormalizer.canonical]), затем вхождение шаблона в название. Среди нескольких
 *    подходящих правил побеждает самое длинное, при равной длине — самое новое.
 *    Правило, указывающее на удалённую категорию, пропускается.
 * 3. [Kind.TRANSFER] → «Переводы».
 * 4. Встроенный словарь [MerchantDictionary] — только для [Direction.EXPENSE] и
 *    [Direction.REFUND] (возврат попадает в категорию покупки). Доход словарём не категоризуется.
 * 5. [Kind.SUBSCRIPTION] → «Подписки».
 * 6. «Другое».
 *
 * Если категория с нужным ключом отсутствует в [resolve]`(categories)`, используется «Другое».
 */
object Categorizer {
    /**
     * @param merchant название мерчанта как в сообщении (может быть `null`).
     * @param rules правила пользователя.
     * @param categories все категории из БД (нужны, чтобы найти id по ключу).
     * @return id категории или `null`, если категорий ещё нет (или нет даже «Другое»).
     */
    fun resolve(
        merchant: String?,
        direction: Direction,
        kind: Kind,
        rules: List<MerchantRule>,
        categories: List<Category>,
    ): Long? {
        val byKey = categories.associateBy { it.key }
        val otherId = byKey[DefaultCategories.OTHER_KEY]?.id
        fun idOf(key: String): Long? = byKey[key]?.id ?: otherId

        if (direction == Direction.INTERNAL_TRANSFER) return idOf(DefaultCategories.TRANSFERS_KEY)

        val canonical = MerchantNormalizer.canonical(merchant)
        if (canonical.isNotEmpty()) {
            val existingIds = categories.mapTo(HashSet()) { it.id }
            matchRule(canonical, rules.filter { it.categoryId in existingIds })?.let { return it.categoryId }
        }

        if (kind == Kind.TRANSFER) return idOf(DefaultCategories.TRANSFERS_KEY)

        if (canonical.isNotEmpty() && (direction == Direction.EXPENSE || direction == Direction.REFUND)) {
            MerchantDictionary.lookupCanonical(canonical)?.let { return idOf(it) }
        }

        if (kind == Kind.SUBSCRIPTION) return idOf(SUBSCRIPTIONS_KEY)
        return otherId
    }

    /**
     * Подбирает правило пользователя для мерчанта: точное совпадение, затем вхождение.
     *
     * @param canonicalMerchant результат [MerchantNormalizer.canonical].
     * @return подходящее правило или `null`.
     */
    fun matchRule(canonicalMerchant: String, rules: List<MerchantRule>): MerchantRule? {
        if (canonicalMerchant.isEmpty() || rules.isEmpty()) return null
        val withKeys = rules
            .map { it to MerchantNormalizer.canonical(it.merchantPattern) }
            .filter { it.second.isNotEmpty() }
        val preference = compareByDescending<Pair<MerchantRule, String>> { it.second.length }
            .thenByDescending { it.first.createdAt }
            .thenByDescending { it.first.id }
        withKeys.filter { it.second == canonicalMerchant }.minWithOrNull(preference)?.let { return it.first }
        return withKeys.filter { canonicalMerchant.contains(it.second) }.minWithOrNull(preference)?.first
    }

    private const val SUBSCRIPTIONS_KEY = "subscriptions"
}
