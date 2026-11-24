# Flash SPI Tool v1.9.24 (versionCode 110) — Inicialización asíncrona de AdMob, sincronización de vista nativa y reintentos automáticos

Fecha / Date: 24 de noviembre de 2026 / November 24, 2026

> **Publicado / Published:** compilado y firmado con el SDK de Android. Publicado en la pista de producción de Google Play Store.

---

## 🇪🇸 Español

### 📌 Resumen
Esta versión corrige las fallas de sincronización del ciclo de vida de los anuncios nativos y del banner adaptativo respecto a PIC K150 y C-PIC. Implementa la inicialización de MobileAds con callbacks de confirmación, reintentos automáticos ante fallos temporales de red y enlace seguro a la ventana para el contenedor nativo.

### 🛠️ Cambios
• **Inicialización asíncrona segura de MobileAds:** Tanto el banner como el anuncio nativo esperan al callback de confirmación de inicialización del SDK de Google Mobile Ads antes de emitir solicitudes a la red.
• **Reintentos automáticos con retardo exponencial:** Si el banner o el anuncio nativo fallan en el arranque en frío (por conexión en establecimiento o retardo en WebViews), se programa un reintento automático para garantizar su despliegue sin requerir reiniciar la pantalla.
• **Sincronización del contenedor nativo con la ventana:** El anuncio nativo verifica que la vista esté adjunta a la ventana visible (`isAttachedToWindow`) o enlaza un `OnAttachStateChangeListener`, evitando medir u ocultar anuncios en contenedores con estado `View.GONE` durante la extracción inicial de recursos.
• **Garantía al finalizar la carga de recursos:** Al pasar de la pantalla de carga (`layoutLoading`) a la interfaz principal (`layoutMainUI`), se verifica y solicita automáticamente el anuncio nativo si aún no estaba presente.
• **Preservación de la jerarquía de vistas:** Eliminada cualquier llamada destructiva sobre el widget `NativeAdView` del layout XML, destruyendo únicamente el objeto de datos AdMob para evitar invalidar la vista.

### ✅ Validación
• `:app:compileReleaseJavaWithJavac`, `:app:assembleRelease` y `:app:bundleRelease` correctos con `compileSdk 37`, `buildTools 37.0.0`, `NDK 30.0.14904198` y `targetSdk 37`.
• APK y AAB firmados con `firma_flash_spi_tool.jks`.
• `versionCode 110` y `versionName 1.9.24`.
• Publicado exitosamente en Google Play Store (Producción).

### 📦 Artefactos
• `Flash-SPI-Tool-v1.9.24-release.apk` — 25130914 bytes
  • SHA-256: `7f6df42512e269881182571f50bac775238badbad0a3699427a875dd073127c2`
• `Flash-SPI-Tool-v1.9.24-release.aab` — 24070155 bytes
  • SHA-256: `6cd3cedf11c5ccf374d0eb3e6604e4c71c6e3ea01758a731d0e9ef474b27ce25`
• `versionCode 110`, `versionName 1.9.24`, `minSdk 24`, `targetSdk 37`.

---

## 🇺🇸 English

### 📌 Overview
This release addresses lifecycle and view synchronization discrepancies in native and banner ads compared to PIC K150 and C-PIC. It implements asynchronous MobileAds initialization callbacks, automatic retries for transient network/WebView errors, and window-attach listeners for the native container.

### 🛠️ Changes
• **Asynchronous MobileAds initialization:** Banner and native ad requests wait for the Google Mobile Ads SDK initialization callback before firing network queries.
• **Automatic retry on initial load failure:** If a banner or native ad encounters a cold-start failure, automatic delayed retries recover the ad without requiring user navigation to settings.
• **Window attach synchronization:** Native ad view checks `isAttachedToWindow` and uses `OnAttachStateChangeListener` to avoid measuring or rendering within `View.GONE` layouts during asset extraction.
• **Post-resource-readiness check:** When transitioning from `layoutLoading` to `layoutMainUI`, the native ad is checked and requested if absent.
• **View hierarchy safety:** Avoids destroying static layout view widgets, ensuring ad containers remain healthy across the activity lifecycle.

### ✅ Validation
• Succeeded `:app:assembleRelease` and `:app:bundleRelease` with `compileSdk 37`, `buildTools 37.0.0`, `NDK 30.0.14904198`, and `targetSdk 37`.
• Signed with `firma_flash_spi_tool.jks`.
• `versionCode 110` and `versionName 1.9.24`.
• Successfully published to Google Play Store (Production).

### 📦 Artifacts
• `Flash-SPI-Tool-v1.9.24-release.apk` — 25130914 bytes
  • SHA-256: `7f6df42512e269881182571f50bac775238badbad0a3699427a875dd073127c2`
• `Flash-SPI-Tool-v1.9.24-release.aab` — 24070155 bytes
  • SHA-256: `6cd3cedf11c5ccf374d0eb3e6604e4c71c6e3ea01758a731d0e9ef474b27ce25`
• `versionCode 110`, `versionName 1.9.24`, `minSdk 24`, `targetSdk 37`.
