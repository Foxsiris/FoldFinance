package com.foldfinance.core.domain.category

/**
 * Встроенный словарь «ключевое слово мерчанта → ключ базовой категории»
 * (см. [com.foldfinance.core.model.DefaultCategories]).
 *
 * Ключевые слова перечислены по-русски и латиницей и сравниваются через
 * [MerchantNormalizer.canonical]. Совпадение засчитывается, если ключевое слово
 * стоит с начала слова в названии мерчанта (`кино` ⊂ `KINOPOISK`), либо — для слов
 * длиной от [MIN_COMPACT_LENGTH] букв — входит в название без учёта пробелов
 * (`YANDEXGO`). Если подходят несколько слов, побеждает самое длинное:
 * `METRO CASH&CARRY` → продукты, а не `метро` → транспорт.
 */
object MerchantDictionary {

    /** Одно ключевое слово словаря. */
    data class Entry(val keyword: String, val categoryKey: String)

    /** Минимальная длина ключевого слова (без пробелов) для поиска без учёта пробелов. */
    const val MIN_COMPACT_LENGTH: Int = 6

    private val raw: Map<String, List<String>> = mapOf(
        "groceries" to listOf(
            "Пятёрочка", "PYATEROCHKA", "Перекрёсток", "PEREKRESTOK", "Магнит", "MAGNIT",
            "ВкусВилл", "VkusVill", "Лента", "LENTA", "Ашан", "AUCHAN", "Дикси", "DIXY",
            "METRO CASH", "Самокат", "SAMOKAT", "Азбука вкуса", "AZBUKA VKUSA",
        ),
        "transport" to listOf(
            "Яндекс Такси", "YANDEX TAXI", "YANDEX GO", "Яндекс Go", "Метро", "METRO",
            "MOSMETRO", "Мосметро", "Тройка", "TROIKA", "TROYKA", "Ситидрайв", "CITYDRIVE",
            "Делимобиль", "DELIMOBIL", "РЖД", "RZD", "Аэроэкспресс", "AEROEXPRESS",
        ),
        "entertainment" to listOf(
            "кино", "KINO", "Кинопоиск", "KINOPOISK", "cinema", "театр", "TEATR", "THEATRE",
            "Steam",
        ),
        "shopping" to listOf(
            "Ozon", "Озон", "Wildberries", "Вайлдберриз", "Яндекс Маркет", "YANDEX MARKET",
            "AliExpress", "Lamoda", "Ламода", "DNS", "М.Видео", "MVIDEO", "Эльдорадо", "ELDORADO",
        ),
        "restaurants" to listOf(
            "ресторан", "RESTORAN", "restaurant", "кафе", "CAFE", "кофейня", "кофе", "COFFEE",
            "KFC", "Вкусно и точка", "VKUSNO I TOCHKA", "Шоколадница", "SHOKOLADNITSA",
            "Теремок", "TEREMOK", "Burger King", "Бургер Кинг", "Starbucks", "Додо пицца",
            "DODO PIZZA", "Яндекс Еда", "YANDEX EDA", "Delivery Club",
        ),
        "utilities" to listOf(
            "ЖКУ", "ZHKU", "ЖКХ", "ZHKH", "МосЭнерго", "MOSENERGO", "Мосэнергосбыт",
            "Ростелеком", "ROSTELECOM", "Мосводоканал", "MOSVODOKANAL", "Мосгаз", "MOSGAZ",
            "Единый платежный документ", "EPD", "МГТС", "MGTS",
        ),
        "subscriptions" to listOf(
            "Яндекс Плюс", "YANDEX PLUS", "Spotify", "Netflix", "Нетфликс", "iCloud",
            "APPLE COM BILL", "Google One", "YouTube Premium",
        ),
        "health" to listOf(
            "аптека", "APTEKA", "pharmacy", "клиника", "CLINIC", "KLINIKA", "Ригла", "RIGLA",
            "Горздрав", "GORZDRAV", "Инвитро", "INVITRO", "Гемотест", "GEMOTEST", "стоматология",
            "STOMATOLOG",
        ),
    )

    /** Все ключевые слова словаря в исходном написании. */
    val entries: List<Entry> = raw.flatMap { (key, words) -> words.map { Entry(it, key) } }

    private data class Compiled(val canonical: String, val compact: String, val categoryKey: String)

    private val compiled: List<Compiled> = entries
        .map { Compiled(MerchantNormalizer.canonical(it.keyword), "", it.categoryKey) }
        .filter { it.canonical.isNotEmpty() }
        .map { it.copy(compact = it.canonical.replace(" ", "")) }
        .distinctBy { it.canonical to it.categoryKey }
        .sortedByDescending { it.compact.length }

    /**
     * Ищет категорию мерчанта по словарю.
     *
     * @param merchant название мерчанта как в сообщении (нормализуется внутри).
     * @return ключ базовой категории или `null`, если ничего не подошло.
     */
    fun lookup(merchant: String?): String? {
        val canonical = MerchantNormalizer.canonical(merchant)
        if (canonical.isEmpty()) return null
        return lookupCanonical(canonical)
    }

    /** То же, что [lookup], но для уже канонизированного ([MerchantNormalizer.canonical]) названия. */
    fun lookupCanonical(canonical: String): String? {
        if (canonical.isEmpty()) return null
        val spaced = " $canonical"
        val compactMerchant = canonical.replace(" ", "")
        // compiled отсортирован по убыванию длины — первое совпадение самое длинное.
        return compiled.firstOrNull { entry ->
            spaced.contains(" ${entry.canonical}") ||
                (entry.compact.length >= MIN_COMPACT_LENGTH && compactMerchant.contains(entry.compact))
        }?.categoryKey
    }
}
