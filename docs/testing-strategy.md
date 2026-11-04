# Стратегия тестирования

| Уровень | Где | Инструменты | Что проверяет |
|---|---|---|---|
| Парсер | `:parser:test` | JUnit, Truth, kotlinx.serialization | каждая фикстура `fixtures/<bank>/NNN.txt` ↔ `NNN.expected.json`; форматы сумм; NotFinancial/Uncertain |
| Домен | `:core:testDebugUnitTest` | JUnit, Truth | аналитика (границы периодов, часовой пояс), категоризация (правило > словарь > Другое), детектор подписок |
| Данные | `:core:testDebugUnitTest` | Robolectric, Room in-memory, Turbine | дедуп SMS+PUSH, идемпотентный повторный импорт, REFUND/INTERNAL_TRANSFER в итогах, перепарсинг без потери ручных правок, миграции |
| ViewModel | `:app:testDebugUnitTest` | Turbine, coroutines-test | непрерывность: выбор, фильтры, черновик переживают пересоздание (SavedStateHandle) |
| Раскладка | `:app:testDebugUnitTest` | JUnit | `AppLayout` из размеров окна и FoldingFeature: Compact / книжная / tabletop, граница панелей = шарнир |
| Скриншоты | `verifyRoborazziDebug` | Roborazzi + Robolectric | каждый основной экран × {внешний, внутренний, tabletop} × {светлая, тёмная} |
| Безопасность | `:app:testDebugUnitTest` | Robolectric | merged manifest без INTERNET, allowBackup=false, экспорт/импорт AES-GCM round-trip, неверный пароль |

## Конфигурации устройств (оценка до замера, этап 0)

- Внешний: 411 × 658 dp (Compact).
- Внутренний, книжная поза: 882 × 662 dp, вертикальный шарнир на x = 441 dp.
- Tabletop: 662 × 882 dp, горизонтальный шарнир на y = 441 dp, HALF_OPENED.

Значения живут в `app/src/test/.../DeviceConfigs.kt`; после замера на телефоне заменить.

## Что не покрывается автоматически

- Реальная поза и шарнир Fold 8, складывание «вживую», панель задач Samsung.
- Получение SMS и уведомлений банков на устройстве, «ограниченные настройки» sideload.
- BiometricPrompt, Keystore на железе, SQLCipher на устройстве (нативная библиотека).
→ ручной чек-лист `docs/manual-checklist.md`.
