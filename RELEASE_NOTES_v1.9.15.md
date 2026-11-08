# Flash SPI Tool v1.9.15 (versionCode 101) — Corrección del crash de Play Billing / Play Billing Crash Fix

Fecha de lanzamiento / Release Date: 8 de noviembre de 2026 / November 8, 2026

---

## 🇪🇸 Español

### 📌 Resumen
Esta versión corrige el cierre inesperado que ocurría al abrir el flujo de compra en instalaciones que no venían de Google Play. En esos casos el servicio de facturación podía devolver un `PendingIntent` nulo y la librería Play Billing 8.3.0 lanzaba una `NullPointerException` fatal dentro de `ProxyBillingActivity.onCreate`, antes de que la app pudiera manejarla.

### 🛠️ Cambios
• La compra solo se inicia si la app fue instalada desde Google Play (`com.android.vending`) y Play Store está habilitado.
• En APK sideloaded (GitHub, ADB, etc.) se muestra un mensaje específico que indica instalar o actualizar desde Play Store, en lugar de abrir un flujo que podía cerrar la app.
• Se registra el instalador detectado como breadcrumb de diagnóstico en Crashlytics.
• Se mantiene Play Billing 8.3.0: las versiones 9.0.0 y 9.1.0 tampoco validan el `PendingIntent` y añaden regresiones.
• Nuevos tests unitarios del guard de instalación y del bloqueo de `launchBillingFlow`.

### ✅ Validación
• `:app:testDebugUnitTest`: 140 tests, 0 fallos, 0 errores.
• Compilaciones debug y release (`:app:assembleDebug`, `:app:assembleRelease`) y AAB (`:app:bundleRelease`) correctas.
• APK y AAB firmados con la clave de producción; huella SHA-256 del certificado verificada.
• `versionCode 101` y `versionName 1.9.15`.

### ⚠️ Nota
El guard elimina el camino de crash en instalaciones que no vienen de Play Store. En instalaciones de Play con un `PendingIntent` nulo por un fallo del servicio de facturación, el riesgo residual de la librería 8.3.0 persiste; se documenta en el issue de Crashlytics.

### 📦 Artefactos
• **APK Release:** `Flash-SPI-Tool-v1.9.15-release.apk`  
  **SHA-256:** `7f2fed319f15ebf8017c1d60201427b66972f0d70c7dd7abe5e0fb318287a280`
• **AAB Release (Google Play Bundle):** `Flash-SPI-Tool-v1.9.15-release.aab`  
  **SHA-256:** `f126944732f92daf26c01218ad95c76365869d3f2e39a6b723288b462f3e89d8`
• **APK Debug:** `Flash-SPI-Tool-v1.9.15-debug.apk`  
  **SHA-256:** `1ddafa3cc6f77275c4900edb61ac0a3c26ecdc62f580c274ca6830a4c3d12130`

---

## 🇺🇸 English

### 📌 Overview
This release fixes the crash that occurred when opening the purchase flow on installs that did not come from Google Play. In those cases the billing service could return a null `PendingIntent`, and Play Billing 8.3.0 threw a fatal `NullPointerException` inside `ProxyBillingActivity.onCreate` before the app could handle it.

### 🛠️ Changes
• Purchases only start when the app was installed from Google Play (`com.android.vending`) and Play Store is enabled.
• On sideloaded APKs (GitHub, ADB, etc.) a specific message asks the user to install or update from the Play Store instead of opening a flow that could crash the app.
• The detected installer is logged as a diagnostic breadcrumb in Crashlytics.
• Play Billing 8.3.0 is kept: versions 9.0.0 and 9.1.0 also fail to validate the `PendingIntent` and introduce regressions.
• New unit tests for the install guard and for blocking `launchBillingFlow`.

### ✅ Validation
• `:app:testDebugUnitTest`: 140 tests, 0 failures, 0 errors.
• Debug and release builds (`:app:assembleDebug`, `:app:assembleRelease`) and AAB (`:app:bundleRelease`) completed successfully.
• APK and AAB signed with the production key; certificate SHA-256 fingerprint verified.
• `versionCode 101` and `versionName 1.9.15`.

### ⚠️ Note
The guard removes the crash path on non-Play installs. On Play installs with a null `PendingIntent` caused by a billing service failure, the residual risk in library 8.3.0 remains; it is documented in the Crashlytics issue.

### 📦 Artifacts
• **APK Release:** `Flash-SPI-Tool-v1.9.15-release.apk`  
  **SHA-256:** `7f2fed319f15ebf8017c1d60201427b66972f0d70c7dd7abe5e0fb318287a280`
• **AAB Release (Google Play Bundle):** `Flash-SPI-Tool-v1.9.15-release.aab`  
  **SHA-256:** `f126944732f92daf26c01218ad95c76365869d3f2e39a6b723288b462f3e89d8`
• **APK Debug:** `Flash-SPI-Tool-v1.9.15-debug.apk`  
  **SHA-256:** `1ddafa3cc6f77275c4900edb61ac0a3c26ecdc62f580c274ca6830a4c3d12130`

---

## 🔗 Enlaces / Links
• **Repositorio / Repository:** https://github.com/Danielk10/Flash-EEPROM-Tool
• **Release:** https://github.com/Danielk10/Flash-EEPROM-Tool/releases/tag/v1.9.15
