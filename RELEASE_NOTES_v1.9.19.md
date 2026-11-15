# Flash SPI Tool v1.9.19 (versionCode 105) — Banner adaptativo y precarga del intersticial

Fecha / Date: 15 de noviembre de 2026 / November 15, 2026

> **Publicado / Published:** compilado y firmado con el SDK de Android. Esta
> versión es la primera que llega a Google Play con el rediseño de publicidad
> (la 1.9.18 no se publicó en Play).

---

## 🇪🇸 Español

### 📌 Resumen
Esta versión aprovecha todo el ancho de la pantalla para el banner y hace que el
intersticial del visor HEX esté listo antes de que el usuario vuelva a la
terminal. Ya no aparece un anuncio por sorpresa varios segundos después de una
operación.

### 🛠️ Cambios
• **Banner adaptativo a ancho completo:** el `AdView` se añade al contenedor con
  `MATCH_PARENT` y usa `AnchoredAdaptiveBannerAdSize` calculado con el ancho
  real de la ventana, así que ocupa toda la franja inferior sin márgenes.
• **Precarga del intersticial:** al abrir el visor HEX se solicita el anuncio;
  mientras el usuario lee la memoria, el anuncio termina de cargar y al volver
  a la terminal se muestra de inmediato.
• **Espera acotada a 3 segundos:** si al volver el anuncio aún no ha llegado,
  solo se muestra si aparece dentro de los 3 segundos siguientes. Pasado ese
  plazo se descarta, en lugar de aparecer más tarde mientras el usuario ya está
  haciendo otra cosa.
• **Cooldown y caducidad sin cambios:** mínimo de 3 minutos entre
  intersticiales y descarte del anuncio a la hora.
• **Sin cambios en el motor:** los binarios de `flashrom`, `pciutils`,
  `libusb`, `libftdi`, `libjaylink` y las dos ABIs (`arm64-v8a` y
  `armeabi-v7a`) se mantienen exactamente iguales.

### ✅ Validación
• `:app:compileDebugJavaWithJavac` y `:app:bundleRelease` correctos con
  `compileSdk 37`, `buildTools 37.0.0`, `NDK 30.0.14904198` y `targetSdk 37`.
• APK y AAB firmados con `firma_flash_spi_tool.jks`.
• `versionCode 105` y `versionName 1.9.19`.

### 📦 Artefactos
- `Flash-SPI-Tool-v1.9.19-release.apk` — 25126718 bytes
  - SHA-256: `aa0822f5943752bcea09a2bbb8c037233eef5fd5783c855ddea8cd05ae96a33e`
- `Flash-SPI-Tool-v1.9.19-release.aab` — 24066055 bytes
  - SHA-256: `b1fabd7b98ddb749bbd983dbfd38449cf6f022b189ca478becbe8272f9d59b4f`
- `versionCode 105`, `versionName 1.9.19`, `minSdk 24`, `targetSdk 37`.

---

## 🇺🇸 English

### 📌 Overview
This release uses the full screen width for the banner and makes the HEX viewer
interstitial ready before the user returns to the terminal. No ad pops up out of
nowhere several seconds after an operation anymore.

### 🛠️ Changes
• **Full-width adaptive banner:** the `AdView` is added to its container with
  `MATCH_PARENT` and uses `AnchoredAdaptiveBannerAdSize` computed from the real
  window width, filling the whole bottom strip with no side margins.
• **Interstitial preloading:** the ad is requested when the HEX viewer opens;
  while the user reads the memory the ad finishes loading and is shown
  immediately when they return to the terminal.
• **3-second wait cap:** if the ad has not arrived when the viewer closes, it is
  only shown if it arrives within the next 3 seconds. After that it is dropped
  instead of appearing later while the user is already doing something else.
• **Cooldown and expiry unchanged:** at least 3 minutes between interstitials
  and loaded ads are dropped after one hour.
• **Engine untouched:** the `flashrom`, `pciutils`, `libusb`, `libftdi`,
  `libjaylink` binaries and both ABIs (`arm64-v8a` and `armeabi-v7a`) remain
  exactly the same.

### ✅ Validation
• `:app:compileDebugJavaWithJavac` and `:app:bundleRelease` succeeded with
  `compileSdk 37`, `buildTools 37.0.0`, `NDK 30.0.14904198` and `targetSdk 37`.
• APK and AAB signed with `firma_flash_spi_tool.jks`.
• `versionCode 105` and `versionName 1.9.19`.

### 📦 Artifacts
- `Flash-SPI-Tool-v1.9.19-release.apk` — 25126718 bytes
  - SHA-256: `aa0822f5943752bcea09a2bbb8c037233eef5fd5783c855ddea8cd05ae96a33e`
- `Flash-SPI-Tool-v1.9.19-release.aab` — 24066055 bytes
  - SHA-256: `b1fabd7b98ddb749bbd983dbfd38449cf6f022b189ca478becbe8272f9d59b4f`
- `versionCode 105`, `versionName 1.9.19`, `minSdk 24`, `targetSdk 37`.
