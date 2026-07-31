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

## Локально задать переменные окружения

Добавьте в `~/.zshrc` / `~/.bashrc` / PowerShell profile:

```bash
export BT_KEYSTORE_PATH=~/.keystores/billiardtracker.keystore
export BT_KEY_ALIAS=billiardtracker
export BT_STORE_PASSWORD='ваш пароль'
export BT_KEY_PASSWORD='ваш пароль'
```

Или создайте `keystore.properties` в корне проекта (файл gitignored):

```properties
storeFile=/absolute/path/to/billiardtracker.keystore
```

И заполните пароли через env-переменные.

## Выпустить релиз

```bash
node bin/.deploy/release-billiardtracker.mjs
```

Скрипт:
1. Читает `app/build.gradle.kts` → узнаёт `versionName` и `versionCode`.
2. `./gradlew :app:assembleRelease` — собирает подписанный APK.
3. Копирует APK на VPS в `/srv/billiardtracker/releases/v{versionName}/billiardtracker.apk`.
4. Обновляет симлинк `/srv/billiardtracker/releases/latest → v{versionName}`.
5. Генерит `/srv/billiardtracker/releases/v{versionName}/version.json` с метаданными.
