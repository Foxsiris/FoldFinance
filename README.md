# FoldFinance

Личный учёт финансов для Samsung Galaxy Z Fold 8. Операции собираются из банковских SMS и
push-уведомлений, разбираются и хранятся только на телефоне (зашифрованная БД, без доступа в сеть).

- Контекст проекта и инварианты — [CLAUDE.md](CLAUDE.md), этапы — [STAGES.md](STAGES.md).
- Решения — [docs/adr](docs/adr), дизайн — [docs/design.md](docs/design.md),
  тесты — [docs/testing-strategy.md](docs/testing-strategy.md),
  ручная проверка — [docs/manual-checklist.md](docs/manual-checklist.md).

## Сборка (Windows)

JDK 17 лежит в `%USERPROFILE%\.jdks`, в PATH он не прописан:

```powershell
$env:JAVA_HOME="$env:USERPROFILE\.jdks\jdk-17.0.20.1+1"
.\gradlew.bat assembleDebug
.\gradlew.bat :parser:test testDebugUnitTest verifyRoborazziDebug
```

APK: `app/build/outputs/apk/debug/app-debug.apk`.

## Подпись release

Release-сборка подписывается debug-ключом, чтобы APK сразу ставился на свой телефон.
Обновления поверх установленной версии работают, пока ключ тот же (ключ лежит в
`%USERPROFILE%\.android\debug.keystore`). Для долгой жизни заведите свой keystore.

## Шрифты

Onest и Unbounded — SIL Open Font License 1.1, тексты лицензий в `docs/licenses`.
