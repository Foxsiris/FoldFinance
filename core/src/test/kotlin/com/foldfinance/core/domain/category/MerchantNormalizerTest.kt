package com.foldfinance.core.domain.category

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test

class MerchantNormalizerTest {

    @Test
    fun `normalize lowercases, replaces yo and strips punctuation`() {
        assertThat(MerchantNormalizer.normalize("  Пятёрочка!!  ")).isEqualTo("пятерочка")
        assertThat(MerchantNormalizer.normalize("YANDEX*GO")).isEqualTo("yandex go")
        assertThat(MerchantNormalizer.normalize("OZON.RU")).isEqualTo("ozon ru")
    }

    @Test
    fun `normalize removes terminal codes and legal forms`() {
        assertThat(MerchantNormalizer.normalize("ООО \"Пятёрочка\" #1234")).isEqualTo("пятерочка")
        assertThat(MerchantNormalizer.normalize("PYATEROCHKA5432 MOSCOW RUS")).isEqualTo("pyaterochka moscow")
        assertThat(MerchantNormalizer.normalize("ИП Иванов")).isEqualTo("иванов")
    }

    @Test
    fun `normalize of null or blank is empty`() {
        assertThat(MerchantNormalizer.normalize(null)).isEmpty()
        assertThat(MerchantNormalizer.normalize(" \t")).isEmpty()
        assertThat(MerchantNormalizer.canonical("1234 5678")).isEmpty()
    }

    @Test
    fun `canonical is script independent`() {
        val pairs = listOf(
            "Пятёрочка" to "PYATEROCHKA",
            "Перекрёсток" to "PEREKRESTOK",
            "ВкусВилл" to "VkusVill",
            "Яндекс Такси" to "YANDEX TAXI",
            "Ростелеком" to "ROSTELECOM",
            "Тройка" to "TROYKA",
            "Кафе" to "CAFE",
            "Нетфликс" to "NETFLIX",
        )
        for ((cyr, lat) in pairs) {
            assertWithMessage(cyr).that(MerchantNormalizer.canonical(cyr)).isEqualTo(MerchantNormalizer.canonical(lat))
        }
    }

    @Test
    fun `canonical is idempotent for typical names`() {
        for (name in listOf("PYATEROCHKA 1234", "Яндекс Плюс", "Вкусно и точка", "APPLE.COM/BILL")) {
            val once = MerchantNormalizer.canonical(name)
            assertWithMessage(name).that(MerchantNormalizer.canonical(once)).isEqualTo(once)
        }
    }
}
