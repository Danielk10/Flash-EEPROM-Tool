# Flash SPI Tool v1.9.25 (versionCode 111) — Corrección del bucle de reintentos de anuncios

Fecha / Date: 24 de noviembre de 2026 / November 24, 2026

> **Publicado / Published:** compilado y firmado con el SDK de Android. Publicado en la pista de producción de Google Play Store.

---

## 🇪🇸 Español

### 📌 Resumen
Esta versión corrige el exceso de peticiones a AdMob detectado en la v1.9.24. Ante un error `No fill` (código 3), la app reintentaba el banner cada 3 segundos y el anuncio nativo cada 4 segundos de forma indefinida, lo que podía provocar limitaciones de tráfico y empeorar la entrega de anuncios. Ahora los reintentos son acotados y solo se aplican a fallos transitorios.

### 🛠️ Cambios
• **Sin reintentos ante `No fill`:** El banner y el anuncio nativo dejan de reprogramar peticiones cuando AdMob responde que no hay inventario disponible.
• **Límite de reintentos:** Se permite como máximo un reintento automático ante errores transitorios (red o internos), con una espera de 5 segundos.
• **Nueva oportunidad al volver a la app:** El contador de reintentos del anuncio nativo se reinicia en `onResume`, de modo que regresar a la app vuelve a solicitar el anuncio una sola vez, sin bucles.
• **Menos tráfico inválido:** Se elimina el flujo continuo de peticiones que se repetía mientras la pantalla permanecía abierta.

### ✅ Validación
• `:app:assembleRelease` y `:app:bundleRelease` correctos con `compileSdk 37`, `buildTools 37.0.0`, `NDK 30.0.14904198` y `targetSdk 37`.
• APK y AAB firmados con `firma_flash_spi_tool.jks`.
• `versionCode 111` y `versionName 1.9.25`.
• Verificado en dispositivo físico (TECNO BF7, Android 12): antes se registraban decenas de peticiones cada 2-4 segundos; tras el cambio se realiza una sola petición de banner y una de nativo, sin reintentos ante `No fill`.
• Publicado exitosamente en Google Play Store (Producción).

### 📦 Artefactos
• `Flash-SPI-Tool-v1.9.25-release.apk` — 25131330 bytes
  • SHA-256: `bfdb69e941da03c9a8aa0c46a8b5d0ccbe2512d2165e24b7c3cac1012ee762fa`
• `Flash-SPI-Tool-v1.9.25-release.aab` — 24070551 bytes
  • SHA-256: `348932fb8840ce742a39165ae66ce8147c31c7e2e11dbe2dd959386880d2c792`
• `versionCode 111`, `versionName 1.9.25`, `minSdk 24`, `targetSdk 37`.

---

## 🇺🇸 English

### 📌 Overview
This release fixes the excessive AdMob request volume found in v1.9.24. On a `No fill` error (code 3), the app retried the banner every 3 seconds and the native ad every 4 seconds indefinitely, which could trigger traffic throttling and degrade ad delivery. Retries are now bounded and only apply to transient failures.

### 🛠️ Changes
• **No retries on `No fill`:** Banner and native ads no longer reschedule requests when AdMob reports no available inventory.
• **Retry cap:** At most one automatic retry is allowed for transient errors (network or internal), with a 5-second delay.
• **Fresh chance on app resume:** The native ad retry counter is reset in `onResume`, so returning to the app requests the ad once again without loops.
• **Less invalid traffic:** The continuous stream of requests that repeated while the screen stayed open has been removed.

### ✅ Validation
• Succeeded `:app:assembleRelease` and `:app:bundleRelease` with `compileSdk 37`, `buildTools 37.0.0`, `NDK 30.0.14904198`, and `targetSdk 37`.
• APK and AAB signed with `firma_flash_spi_tool.jks`.
• `versionCode 111` and `versionName 1.9.25`.
• Verified on a physical device (TECNO BF7, Android 12): previously dozens of requests were logged every 2-4 seconds; after the change, a single banner request and a single native request are made, with no retries on `No fill`.
• Successfully published to Google Play Store (Production).

### 📦 Artifacts
• `Flash-SPI-Tool-v1.9.25-release.apk` — 25131330 bytes
  • SHA-256: `bfdb69e941da03c9a8aa0c46a8b5d0ccbe2512d2165e24b7c3cac1012ee762fa`
• `Flash-SPI-Tool-v1.9.25-release.aab` — 24070551 bytes
  • SHA-256: `348932fb8840ce742a39165ae66ce8147c31c7e2e11dbe2dd959386880d2c792`
• `versionCode 111`, `versionName 1.9.25`, `minSdk 24`, `targetSdk 37`.
