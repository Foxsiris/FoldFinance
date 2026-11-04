package com.foldfinance.core.domain.category

import com.foldfinance.core.model.Category
import com.foldfinance.core.model.DefaultCategories
import com.foldfinance.core.model.MerchantRule
import com.foldfinance.parser.Direction
import com.foldfinance.parser.Kind

/**
 * Автокатегоризация операции. Приоритет: правило пользователя > встроенный словарь > «Другое».
 * Контракт каркаса; словарь и правила реализуются на этапе 6.
 */
object Categorizer {
    /**
     * @param categories все категории из БД (нужны, чтобы найти id по ключу).
     * @return id категории или `null`, если категорий ещё нет.
     */
    fun resolve(
        merchant: String?,
        direction: Direction,
        kind: Kind,
        rules: List<MerchantRule>,
        categories: List<Category>,
    ): Long? = categories.firstOrNull { it.key == DefaultCategories.OTHER_KEY }?.id
}
