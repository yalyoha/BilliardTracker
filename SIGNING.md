# BilliardTracker — подпись Release APK

## Одноразово: сгенерировать keystore

```bash
keytool -genkeypair \
  -v \
  -keystore ~/.keystores/billiardtracker.keystore \
  -alias billiardtracker \
  -keyalg RSA -keysize 2048 -validity 10000 \
  -storepass 'ЗАМЕНИ_НАДЁЖНЫЙ_ПАРОЛЬ' \
  -keypass 'ЗАМЕНИ_НАДЁЖНЫЙ_ПАРОЛЬ' \
  -dname "CN=Aleksey Losev, O=BilliardTracker, C=RU"
```

**Храните keystore в надёжном месте** (внешний диск + облако). Если потеряете — вы больше не сможете выпускать обновления с тем же application id, придётся ставить как новое приложение.

## Секреты

Все секреты — в `BilliardTracker/.env` (gitignored), бэкап в `~/.keystores/billiardtracker.env`:

```
BT_KEYSTORE_PATH=C:/Users/LAV/.keystores/billiardtracker.keystore
BT_KEY_ALIAS=billiardtracker
BT_STORE_PASSWORD=…
BT_KEY_PASSWORD=…
GITHUB_TOKEN=ghp_…     # для GH release + удаления старых тегов
```

Release-скрипт (`E:/PROJECTS/LAV-Server/bin/.deploy/release-billiardtracker.mjs:38-47`) сам подтягивает `.env` в `process.env` — руками экспортировать ничего не нужно.

Резервный путь: экспортировать `BT_*` в текущей сессии PowerShell / `~/.zshrc` — приоритет у уже установленных env-переменных над `.env`.

## Выпустить релиз

```bash
node E:/PROJECTS/LAV-Server/bin/.deploy/release-billiardtracker.mjs
```

Шаги скрипта:
1. Читает `versionName` + `versionCode` из `app/build.gradle.kts`.
2. Догружает секреты из `BilliardTracker/.env`.
3. `gradlew :app:assembleRelease` — подписанный APK (~6 МБ после R8 + shrink).
4. SFTP → `/srv/billiardtracker/releases/v{versionName}/billiardtracker.apk` + `version.json` на VPS.
5. Обновляет симлинк `/srv/billiardtracker/releases/latest → v{versionName}` (auto-updater смотрит именно `latest/version.json`).
6. Если задан `GITHUB_TOKEN` — создаёт GH release `v{versionName}` с APK-asset'ом и **удаляет ВСЕ старые release'ы + git-теги** (на GH держим только последнюю версию; auto-updater юзеров ходит на VPS, GH — просто зеркало для последней сборки).
