# ADR 0001 — стек, модули и версии

- Статус: принято (пересмотрено 2026-11-05: переход на последние стабильные версии)
- Дата: 2026-11-05

## Контекст

CLAUDE.md задаёт стек и три модуля (`:parser`, `:core`, `:app`) и требует актуальные
стабильные версии. Последние стабильные androidx (core 1.19, Compose UI 1.12, adaptive 1.3)
требуют compileSdk 37 и AGP ≥ 9.1. По согласию пользователя установлены SDK Platform 36 и 37.0,
build-tools 36.1/37, эмулятор и образ Android 16 (API 36, Google Play, x86_64).

## Решение

1. Версии зафиксированы в `gradle/libs.versions.toml` и проверены по maven-metadata и
   `aar-metadata.properties` (minCompileSdk): AGP 9.5.0, Gradle 9.8.2, Kotlin 2.4.21, KSP 2.3.13,
   Compose BOM 2026.09.00 (Material 3 1.4.0, UI 1.12.1), material3-adaptive 1.3.0, Room 2.8.5,
   SQLCipher 4.20.0, Hilt 2.60.1, WorkManager 2.12.0, DataStore 1.3.0, Vico 3.3.1, Glance 1.2.0,
   Window 1.5.1, Robolectric 4.17, Roborazzi 1.77.0.
2. AGP 9 компилирует Kotlin сам (built-in Kotlin): плагин `kotlin-android` не применяется,
   `kotlinOptions` заменён на `kotlin { compilerOptions }`. Версия Kotlin Gradle Plugin
   поднимается объявлением `kotlin-jvm` в корневом `plugins` (AGP 9.5 тянет KGP 2.2.10).
3. `compileSdk = 37`, `targetSdk = 36` (Android 16 на Fold 8; поведение Android 17 включить после
   проверки на устройстве), `minSdk = 31`.
4. `Direction` и `Kind` объявлены в `:parser` и переиспользуются `:core` (`api(project(":parser"))`),
   чтобы не было двух параллельных словарей и маппинга.
5. Время в домене — `java.time.Instant`, в Room — epoch millis через конвертеры.
6. БД — Room поверх SQLCipher (`SupportOpenHelperFactory`). Ключ 32 байта из `SecureRandom`,
   зашифрован AES-GCM ключом Android Keystore, хранится в приватном файле.
   В Robolectric-тестах нативная библиотека SQLCipher недоступна, поэтому DAO-тесты идут на
   in-memory Room без шифрования; фабрика БД подменяется.
7. Раскладка вычисляется чистой функцией из размеров окна + `FoldingFeature`
   (`AppLayout`) и отдаётся в UI через `CompositionLocal`. Скриншот-тесты и Preview
   подставляют её явно — Robolectric не умеет эмулировать шарнир.
8. Строки фич — в отдельных `strings_<feature>.xml`, чтобы параллельная работа не конфликтовала.

## Последствия

- Material 3 1.4.0: Expressive API частично помечены `@ExperimentalMaterial3ExpressiveApi`;
  используем их только там, где они стабильны в 1.4.0, остальное — собственные токены движения.
- Vico 3.x отличается по API от 2.x — сверяться с исходниками 3.3.1.
- Реальные dp экранов и положение шарнира — оценки до замера на устройстве (этап 0).
  Эмулятор `Fold8` (профиль «7.6in Foldable», Android 16) — для ручной проверки поз.
