# ADR 0001 — стек, модули и версии

- Статус: принято
- Дата: 2026-11-05

## Контекст

CLAUDE.md задаёт стек и три модуля (`:parser`, `:core`, `:app`). На машине установлен
Android SDK только с платформой 35 и Gradle 8.11.1. Самые свежие версии библиотек
(Compose BOM 2026.09, AGP 9.5, Kotlin 2.4) требуют compileSdk 36+ и миграции на AGP 9
(встроенный Kotlin, новый DSL). Ставить SDK-компоненты без подтверждения пользователя нельзя.

## Решение

1. Версии зафиксированы в `gradle/libs.versions.toml` и проверены по maven-metadata:
   AGP 8.10.1, Kotlin 2.1.21, KSP 2.1.21-2.0.1, Compose BOM 2025.05.01 (Material 3 1.3.2),
   material3-adaptive 1.1.0, Room 2.7.1, SQLCipher 4.9.0, Hilt 2.56.2, WorkManager 2.10.1,
   Vico 2.1.3, Glance 1.1.1, Window 1.4.0, Robolectric 4.15.1, Roborazzi 1.44.0.
   Это последние стабильные версии, совместимые с compileSdk 35.
2. `Direction` и `Kind` объявлены в `:parser` и переиспользуются `:core` (`api(project(":parser"))`),
   чтобы не было двух параллельных словарей и маппинга.
3. Время в домене — `java.time.Instant` (minSdk 31), в Room — epoch millis через конвертеры.
4. БД — Room поверх SQLCipher (`SupportOpenHelperFactory`). Ключ 32 байта из `SecureRandom`,
   зашифрован AES-GCM ключом Android Keystore, хранится в приватном файле.
   В Robolectric-тестах нативная библиотека SQLCipher недоступна, поэтому DAO-тесты идут на
   in-memory Room без шифрования; фабрика БД подменяется через Hilt-модуль.
5. Раскладка вычисляется чистой функцией из размеров окна + `FoldingFeature`
   (`AppLayout`), которая отдаётся в UI через `CompositionLocal`. Скриншот-тесты и Preview
   подставляют её явно — Robolectric не умеет эмулировать шарнир.
6. Строки фич — в отдельных `strings_<feature>.xml`, чтобы параллельная работа не конфликтовала.

## Последствия

- Обновление до compileSdk 36+/AGP 9 — отдельный этап после установки SDK Platform 36
  (`sdkmanager "platforms;android-36"`), с проверкой всех API adaptive/Vico.
- Material 3 Expressive API (`MaterialExpressiveTheme`, `MotionScheme`) в Material 3 1.3.2
  нет. Выразительность достигается собственными токенами движения (пружины) и формами.
- Реальные dp экранов и положение шарнира — оценки до замера на устройстве (этап 0).
