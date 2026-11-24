# Flash SPI Tool v1.9.26 (versionCode 112) — Carga fiable del anuncio nativo

Fecha / Date: 24 de noviembre de 2026 / November 24, 2026

> **Publicado / Published:** compilado y firmado con el SDK de Android. Publicado en la pista de producción de Google Play Store.

---

## 🇪🇸 Español

### 📌 Resumen
Esta versión corrige la carga del anuncio nativo de AdMob. La tarjeta nativa se pedía antes de que el SDK de Google Mobile Ads terminara de inicializarse y con la actividad todavía no visible, por lo que la petición solía fallar o quedarse sin mostrarse. Ahora se aplica el mismo patrón que C-PIC y PIC K150: primero se inicializa AdMob, después se solicita el anuncio con la pantalla ya visible.

### 🛠️ Cambios
• **Nativo tras inicializar AdMob:** La petición del anuncio nativo espera a que `MobileAds` esté inicializado, igual que en C-PIC y PIC K150.
• **Solicitud en `onResume`:** Banner y nativo se piden al volver a la pantalla principal, no en `onCreate`, cuando la actividad ya es visible.
• **Guarda de visibilidad:** Se evita pedir anuncios si la actividad está en segundo plano, cerrada o destruida.
• **Sin bucles ante `No fill`:** Se mantiene la corrección de la v1.9.25: sin reintentos cuando AdMob responde que no hay inventario y como máximo un reintento ante errores transitorios.
• **Diagnóstico:** Nuevos registros para confirmar la espera de inicialización y la carga correcta del nativo.

### ✅ Validación
• `:app:assembleRelease` y `:app:bundleRelease` correctos con `compileSdk 37`, `buildTools 37.0.0`, `NDK 30.0.14904198` y `targetSdk 37`.
• APK y AAB firmados con `firma_flash_spi_tool.jks`.
• `versionCode 112` y `versionName 1.9.26`.
• Verificado en dispositivo físico (TECNO BF7, Android 12): el anuncio nativo se muestra correctamente tras inicializar AdMob.
• Banner verificado en la v1.9.26: se muestra correctamente cuando AdMob entrega inventario; corrección confirmada por el dueño en el dispositivo.
• Bloque de banner verificado en AdMob: `ca-app-pub-5141499161332805/1518371626`, formato BANNER, perteneciente a Flash SPI Tool. El `No fill` ocasional corresponde a falta de inventario de ese bloque, no a un ID incorrecto.
• Referencia cruzada: PIC K150 (`ca-app-pub-5141499161332805/5248084133`) muestra su banner correctamente con el mismo patrón de carga.

### 📦 Artefactos
• `Flash-SPI-Tool-v1.9.26-release.apk` — 25131298 bytes
  • SHA-256: `8f09894a3fc72ab5d0767244d225e6d30387fd3b6e6e0cf9710e5a00acc34d51`
• `Flash-SPI-Tool-v1.9.26-release.aab` — 24070515 bytes
  • SHA-256: `ea4d0ca36ab1ed7744a2f6b3a7f8ae708a9345c4beafa5785a7c57e1a90b2a90`
• `versionCode 112`, `versionName 1.9.26`, `minSdk 24`, `targetSdk 37`.

---

## 🇺🇸 English

### 📌 Overview
This release fixes AdMob native ad loading. The native card was requested before the Google Mobile Ads SDK finished initializing and while the activity was not yet visible, so the request often failed or never rendered. It now follows the same pattern as C-PIC and PIC K150: initialize AdMob first, then request the ad with the screen visible.

### 🛠️ Changes
• **Native ad after AdMob init:** The native request waits until `MobileAds` is initialized, matching C-PIC and PIC K150.
• **Request in `onResume`:** Banner and native ads are requested when returning to the main screen, not in `onCreate`, once the activity is visible.
• **Visibility guard:** No ad requests while the activity is in the background, finishing or destroyed.
• **No `No fill` loops:** The v1.9.25 fix is preserved: no retries when AdMob reports no inventory and at most one retry for transient errors.
• **Diagnostics:** New logs confirm the initialization wait and successful native load.

### ✅ Validation
• `:app:assembleRelease` and `:app:bundleRelease` succeeded with `compileSdk 37`, `buildTools 37.0.0`, `NDK 30.0.14904198` and `targetSdk 37`.
• APK and AAB signed with `firma_flash_spi_tool.jks`.
• `versionCode 112` and `versionName 1.9.26`.
• Verified on a physical device (TECNO BF7, Android 12): the native ad renders correctly after AdMob initialization.
• Banner verified on v1.9.26: it renders correctly when AdMob returns inventory; fix confirmed by the owner on the device.
• Banner ad unit verified in AdMob: `ca-app-pub-5141499161332805/1518371626`, BANNER format, belonging to Flash SPI Tool. Occasional `No fill` is inventory-related, not a wrong ID.
• Cross-check: PIC K150 (`ca-app-pub-5141499161332805/5248084133`) renders its banner correctly with the same loading pattern.

### 📦 Artifacts
• `Flash-SPI-Tool-v1.9.26-release.apk` — 25131298 bytes
  • SHA-256: `8f09894a3fc72ab5d0767244d225e6d30387fd3b6e6e0cf9710e5a00acc34d51`
• `Flash-SPI-Tool-v1.9.26-release.aab` — 24070515 bytes
  • SHA-256: `ea4d0ca36ab1ed7744a2f6b3a7f8ae708a9345c4beafa5785a7c57e1a90b2a90`
• `versionCode 112`, `versionName 1.9.26`, `minSdk 24`, `targetSdk 37`.
