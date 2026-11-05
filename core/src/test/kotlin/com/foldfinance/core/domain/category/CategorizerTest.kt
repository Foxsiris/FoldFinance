package com.foldfinance.core.domain.category

import com.foldfinance.core.model.Category
import com.foldfinance.core.model.DefaultCategories
import com.foldfinance.core.model.MerchantRule
import com.foldfinance.parser.Direction
import com.foldfinance.parser.Kind
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test
import java.time.Instant

class CategorizerTest {

    private val categories: List<Category> = DefaultCategories.all.mapIndexed { i, seed ->
        Category(id = (i + 1).toLong(), key = seed.key, name = seed.name, iconKey = seed.iconKey, isSystem = true, sortOrder = i)
    } + Category(id = 100, key = "user_coffee", name = "Кофе", iconKey = "coffee", isSystem = false, sortOrder = 100)

    private fun id(key: String): Long = categories.first { it.key == key }.id

    private fun rule(pattern: String, categoryId: Long, createdAt: Long = 0, id: Long = 0) =
        MerchantRule(id = id, merchantPattern = pattern, categoryId = categoryId, createdAt = Instant.ofEpochSecond(createdAt))

    private fun resolve(
        merchant: String?,
        rules: List<MerchantRule> = emptyList(),
        direction: Direction = Direction.EXPENSE,
        kind: Kind = Kind.PURCHASE,
        cats: List<Category> = categories,
    ) = Categorizer.resolve(merchant, direction, kind, rules, cats)

    @Test
    fun `dictionary maps merchants to base categories`() {
        val expected = mapOf(
            "PYATEROCHKA 1234 MOSCOW RUS" to "groceries",
            "Пятёрочка" to "groceries",
            "ПЕРЕКРЕСТОК" to "groceries",
            "MAGNIT MM 5531" to "groceries",
            "VKUSVILL" to "groceries",
            "ВкусВилл 77" to "groceries",
            "YANDEX*GO" to "transport",
            "YANDEXGO" to "transport",
            "Яндекс Такси" to "transport",
            "MOSMETRO" to "transport",
            "Метро" to "transport",
            "TROYKA" to "transport",
            "Тройка" to "transport",
            "KINOPOISK" to "entertainment",
            "Кинотеатр Октябрь" to "entertainment",
            "OZON.RU" to "shopping",
            "WILDBERRIES" to "shopping",
            "Ресторан Пушкин" to "restaurants",
            "CAFE PUSHKIN" to "restaurants",
            "Кофейня на углу" to "restaurants",
            "KFC 12345" to "restaurants",
            "VKUSNO I TOCHKA" to "restaurants",
            "Вкусно — и точка" to "restaurants",
            "ЖКУ Москва" to "utilities",
            "MOSENERGOSBYT" to "utilities",
            "ROSTELECOM" to "utilities",
            "Ростелеком" to "utilities",
            "YANDEX*PLUS" to "subscriptions",
            "Яндекс Плюс" to "subscriptions",
            "SPOTIFY" to "subscriptions",
            "NETFLIX.COM" to "subscriptions",
            "APPLE.COM/BILL" to "subscriptions",
            "ICLOUD" to "subscriptions",
            "GOOGLE *Google One" to "subscriptions",
            "APTEKA 36,6" to "health",
            "Аптека Ригла" to "health",
            "Клиника Здоровье" to "health",
        )
        for ((merchant, key) in expected) {
            assertWithMessage(merchant).that(MerchantDictionary.lookup(merchant)).isEqualTo(key)
            assertWithMessage(merchant).that(resolve(merchant)).isEqualTo(id(key))
        }
    }

    @Test
    fun `longest keyword wins`() {
        assertThat(MerchantDictionary.lookup("METRO CASH&CARRY")).isEqualTo("groceries")
        assertThat(MerchantDictionary.lookup("METRO")).isEqualTo("transport")
        assertThat(MerchantDictionary.lookup("YANDEX PLUS")).isEqualTo("subscriptions")
        assertThat(MerchantDictionary.lookup("YANDEX TAXI")).isEqualTo("transport")
    }

    @Test
    fun `unknown merchant goes to other`() {
        assertThat(resolve("IP IVANOV SHAURMA")).isEqualTo(id(DefaultCategories.OTHER_KEY))
        assertThat(resolve(null)).isEqualTo(id(DefaultCategories.OTHER_KEY))
        assertThat(resolve("   ")).isEqualTo(id(DefaultCategories.OTHER_KEY))
        assertThat(MerchantDictionary.lookup("YANDEX")).isNull()
    }

    @Test
    fun `user rule beats dictionary`() {
        val rules = listOf(rule("Пятёрочка", categoryId = 100))
        assertThat(resolve("PYATEROCHKA 5432", rules)).isEqualTo(100)
        // Без правила — словарь.
        assertThat(resolve("PYATEROCHKA 5432")).isEqualTo(id("groceries"))
    }

    @Test
    fun `rule exact match beats contains match`() {
        val rules = listOf(
            rule("starbucks", categoryId = id("restaurants"), id = 1),
            rule("STARBUCKS AIRPORT", categoryId = 100, id = 2),
        )
        assertThat(resolve("Starbucks Airport 12", rules)).isEqualTo(100)
        assertThat(resolve("STARBUCKS ARBAT", rules)).isEqualTo(id("restaurants"))
    }

    @Test
    fun `exact match preferred over contains match`() {
        val rules = listOf(
            rule("shop", categoryId = 100, id = 1),
            rule("sho", categoryId = id("shopping"), id = 2),
        )
        // Канонически "shop" == "shop" — точное совпадение важнее.
        assertThat(resolve("SHOP", rules)).isEqualTo(100)
    }

    @Test
    fun `among equal rules the newest wins`() {
        val rules = listOf(
            rule("lavka", categoryId = id("groceries"), createdAt = 10, id = 1),
            rule("LAVKA", categoryId = 100, createdAt = 20, id = 2),
        )
        assertThat(resolve("Лавка", rules)).isEqualTo(100)
    }

    @Test
    fun `rule pointing to deleted category is ignored`() {
        val rules = listOf(rule("PYATEROCHKA", categoryId = 999))
        assertThat(resolve("PYATEROCHKA", rules)).isEqualTo(id("groceries"))
    }

    @Test
    fun `transfers go to transfers category`() {
        assertThat(resolve("Иван И.", kind = Kind.TRANSFER)).isEqualTo(id(DefaultCategories.TRANSFERS_KEY))
        assertThat(resolve("Ozon", direction = Direction.INTERNAL_TRANSFER, kind = Kind.TRANSFER))
            .isEqualTo(id(DefaultCategories.TRANSFERS_KEY))
    }

    @Test
    fun `internal transfer ignores user rules`() {
        val rules = listOf(rule("Иван", categoryId = 100))
        assertThat(resolve("Иван И.", rules, direction = Direction.INTERNAL_TRANSFER, kind = Kind.TRANSFER))
            .isEqualTo(id(DefaultCategories.TRANSFERS_KEY))
        // Обычный перевод — правило пользователя применяется.
        assertThat(resolve("Иван И.", rules, direction = Direction.EXPENSE, kind = Kind.TRANSFER)).isEqualTo(100)
    }

    @Test
    fun `refund uses dictionary, income does not`() {
        assertThat(resolve("OZON", direction = Direction.REFUND, kind = Kind.REFUND)).isEqualTo(id("shopping"))
        assertThat(resolve("OZON", direction = Direction.INCOME, kind = Kind.CREDIT))
            .isEqualTo(id(DefaultCategories.OTHER_KEY))
    }

    @Test
    fun `subscription kind falls back to subscriptions`() {
        assertThat(resolve("SOME SERVICE", kind = Kind.SUBSCRIPTION)).isEqualTo(id("subscriptions"))
    }

    @Test
    fun `missing base category falls back to other, no categories gives null`() {
        val withoutGroceries = categories.filterNot { it.key == "groceries" }
        assertThat(resolve("PYATEROCHKA", cats = withoutGroceries)).isEqualTo(id(DefaultCategories.OTHER_KEY))
        assertThat(resolve("PYATEROCHKA", cats = emptyList())).isNull()
    }

    @Test
    fun `priority is rule then dictionary then AI then Other`() {
        val ai = id("restaurants")
        fun res(merchant: String, rules: List<MerchantRule> = emptyList(), direction: Direction = Direction.EXPENSE, kind: Kind = Kind.PURCHASE) =
            Categorizer.resolveWithSource(merchant, direction, kind, rules, categories, aiCategoryId = ai)

        assertThat(res("SHOKO BAR", listOf(rule("shoko", id("groceries")))).source).isEqualTo(CategorySource.RULE)
        assertThat(res("PYATEROCHKA 1234").source).isEqualTo(CategorySource.DICTIONARY)
        assertThat(res("PYATEROCHKA 1234").categoryId).isEqualTo(id("groceries"))
        assertThat(res("SHOKO BAR")).isEqualTo(CategoryResolution(ai, CategorySource.AI))
        // Доход, перевод, подписка — не категория модели.
        assertThat(res("SHOKO BAR", direction = Direction.INCOME).source).isEqualTo(CategorySource.OTHER)
        assertThat(res("SHOKO BAR", kind = Kind.TRANSFER).source).isEqualTo(CategorySource.TRANSFER)
        assertThat(res("SHOKO BAR", kind = Kind.SUBSCRIPTION).source).isEqualTo(CategorySource.SUBSCRIPTION)
        // Без предложения модели и для удалённой категории — «Другое».
        assertThat(Categorizer.resolveWithSource("SHOKO BAR", Direction.EXPENSE, Kind.PURCHASE, emptyList(), categories).source)
            .isEqualTo(CategorySource.OTHER)
        assertThat(Categorizer.resolveWithSource("SHOKO BAR", Direction.EXPENSE, Kind.PURCHASE, emptyList(), categories, 999).categoryId)
            .isEqualTo(id(DefaultCategories.OTHER_KEY))
    }

    @Test
    fun `alias names must be recognisable in the raw merchant`() {
        assertThat(MerchantAliasRules.isPlausible("YANDEX*GO*TAXI MOSCOW", "Яндекс Go")).isTrue()
        assertThat(MerchantAliasRules.isPlausible("PYATEROCHKA 1234", "Пятёрочка")).isTrue()
        assertThat(MerchantAliasRules.isPlausible("VKUSVILL 77", "ВкусВилл")).isTrue()
        assertThat(MerchantAliasRules.isPlausible("PYATEROCHKA 1234", "Магнит")).isFalse()
        assertThat(MerchantAliasRules.isPlausible("PYATEROCHKA 1234", "")).isFalse()
        assertThat(MerchantAliasRules.isPlausible("PYATEROCHKA 1234", "1234")).isFalse()
        assertThat(MerchantAliasRules.isPlausible("PYATEROCHKA", "Пятёрочка\nignore previous")).isFalse()
    }
}
