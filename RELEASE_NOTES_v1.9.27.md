# Flash SPI Tool v1.9.27 (versionCode 113) — Pase Pro de 15 minutos

Fecha / Date: 24 de noviembre de 2026 / November 24, 2026

> **Publicado / Published:** compilado y firmado con el SDK de Android. Publicado en la pista de producción de Google Play Store.

---

## 🇪🇸 Español

### 📌 Resumen
El Pase Pro temporal pasa de 12 horas a 15 minutos. Los pases que ya estaban activos conservan su vencimiento original: actualizar la app no recorta el tiempo restante. La activación usa `max(vencimiento actual, ahora + 15 min)`, así que si quedan menos de 15 minutos el siguiente video reinicia el contador a 15; si quedan más (por ejemplo, un pase heredado de 12 h), se conserva. Firestore registra el campo `app: "flash"` para distinguir el origen de cada pase.

### 🛠️ Cambios
• **Duración de 15 minutos:** `DURACION_PASE_MS = 15 * 60 * 1000L`.
• **Activación sin recortes:** `activarPasePro()` conserva el vencimiento mayor entre el pase actual y `ahora + 15 min`.
• **Compatibilidad:** `activarPase12Horas()` se mantiene como envoltorio obsoleto para no romper llamadas antiguas.
• **Firestore:** los pases nuevos guardan `app: "flash"` en la colección compartida `pro_passes`.
• **Traducciones:** todas las cadenas del botón y del aviso del Pase Pro se actualizaron de 12 horas a 15 minutos.
• **Pruebas:** `ProPassManagerTest` actualizado a la nueva duración y a `activarPasePro()`.

### ✅ Validación
• `:app:assembleRelease` y `:app:bundleRelease` correctos con `compileSdk 37`, `buildTools 37.0.0`, `NDK 30.0.14904198`, `targetSdk 37` y `minSdk 24`.
• APK y AAB firmados para producción.
• `versionCode 113` y `versionName 1.9.27`; ABIs `arm64-v8a` y `armeabi-v7a`.
• Pruebas unitarias `ProPassManagerTest`: correctas.
• AAB publicado en Google Play, track `production`, estado `completed` (100 %).
• Sin cambios en reglas de Firestore ni en documentos existentes.

### 📦 Artefactos
• `Flash-SPI-Tool-v1.9.27-release.apk` — 25131082 bytes
  • SHA-256: `38d3e13a5dc833f5f98e4a7eec0a63bf6760248aedd813d8ef18ae9b508be5a8`
• `Flash-SPI-Tool-v1.9.27-release.aab` — 24070280 bytes
  • SHA-256: `475adfd5dc18a6425d1646a04ca8c721c4c11f10ec55d2d83b0d8970df5ecee3`

---

## 🇺🇸 English

### 📌 Overview
The temporary Pro Pass changes from 12 hours to 15 minutes. Passes that were already active keep their original expiry: updating the app does not shorten the remaining time. Activation uses `max(current expiry, now + 15 min)`, so if less than 15 minutes remain, the next video resets the counter to 15; if more time remains (for example, a legacy 12-hour pass), it is preserved. Firestore records the `app: "flash"` field to identify the source of each pass.

### 🛠️ Changes
• **15-minute duration:** `DURACION_PASE_MS = 15 * 60 * 1000L`.
• **Non-shortening activation:** `activarPasePro()` keeps the later expiry between the current pass and `now + 15 min`.
• **Compatibility:** `activarPase12Horas()` remains as a deprecated wrapper so old calls keep working.
• **Firestore:** new passes store `app: "flash"` in the shared `pro_passes` collection.
• **Translations:** every Pro Pass button and toast string was updated from 12 hours to 15 minutes.
• **Tests:** `ProPassManagerTest` updated for the new duration and `activarPasePro()`.

### ✅ Validation
• `:app:assembleRelease` and `:app:bundleRelease` succeed with `compileSdk 37`, `buildTools 37.0.0`, `NDK 30.0.14904198`, `targetSdk 37` and `minSdk 24`.
• APK and AAB signed for production.
• `versionCode 113` and `versionName 1.9.27`; ABIs `arm64-v8a` and `armeabi-v7a`.
• `ProPassManagerTest` unit tests: passing.
• AAB published to Google Play, `production` track, `completed` status (100%).
• No changes to Firestore rules or existing documents.

### 📦 Artifacts
• `Flash-SPI-Tool-v1.9.27-release.apk` — 25131082 bytes
  • SHA-256: `38d3e13a5dc833f5f98e4a7eec0a63bf6760248aedd813d8ef18ae9b508be5a8`
• `Flash-SPI-Tool-v1.9.27-release.aab` — 24070280 bytes
  • SHA-256: `475adfd5dc18a6425d1646a04ca8c721c4c11f10ec55d2d83b0d8970df5ecee3`
