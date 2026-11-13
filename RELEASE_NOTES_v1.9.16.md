# Flash SPI Tool v1.9.16 (versionCode 102) — Corrección del crash sin gestor de archivos / No File Manager Crash Fix

Fecha de lanzamiento / Release Date: 13 de noviembre de 2026 / November 13, 2026

---

## 🇪🇸 Español

### 📌 Resumen
Esta versión corrige el cierre inesperado que ocurría al importar un archivo, exportar una ROM o elegir el directorio de trabajo en dispositivos que no tienen ninguna actividad capaz de atender las intents SAF (`ACTION_OPEN_DOCUMENT`, `ACTION_CREATE_DOCUMENT` o `ACTION_OPEN_DOCUMENT_TREE`). En esas ROMs (AOSP puro, Huawei sin GMS, etc.) el sistema lanzaba una `ActivityNotFoundException` fatal antes de que la app pudiera reaccionar. Corrige el issue #102.

### 🛠️ Cambios
• Se captura `ActivityNotFoundException` en los tres flujos de archivos: importar, exportar y seleccionar carpeta de trabajo.
• Importar y exportar reintentan con `ACTION_GET_CONTENT` cuando el proveedor SAF no está disponible.
• Si tampoco existe un handler para el fallback, se muestra el mensaje `str_no_file_manager` en lugar de cerrar la aplicación.
• Nuevo recurso de texto traducido a español, inglés, chino simplificado y chino tradicional.
• Cierre del issue #102 (`ActivityNotFoundException: No Activity found to handle Intent { act=android.intent.action.OPEN_DOCUMENT ... }`).

### ✅ Validación
• `:app:testDebugUnitTest`: 140 tests, 0 fallos, 0 errores.
• Compilaciones debug y release (`:app:assembleDebug`, `:app:assembleRelease`) y AAB (`:app:bundleRelease`) correctas.
• APK y AAB firmados con la clave de producción; huella SHA-256 del certificado verificada.
• `versionCode 102` y `versionName 1.9.16`.

### 📦 Artefactos
• **APK Release:** `Flash-SPI-Tool-v1.9.16-release.apk`  
  **SHA-256:** `22f619517ab95fb6b15e0cfa8bcf14335e643479d6ac33f8fdfc0dab02c422d1`
• **AAB Release (Google Play Bundle):** `Flash-SPI-Tool-v1.9.16-release.aab`  
  **SHA-256:** `7a334b1a330a90675d6e6a5487b869dc248306ffc4204e8d24d07fe1f5f4c777`
• **APK Debug:** `Flash-SPI-Tool-v1.9.16-debug.apk`  
  **SHA-256:** `f06fbe51e7266a0771e2807937d87a1efc06e8d9095ad697a8851323b295e41f`

---

## 🇺🇸 English

### 📌 Overview
This release fixes the crash that occurred when importing a file, exporting a ROM, or choosing the working directory on devices with no activity able to handle the SAF intents (`ACTION_OPEN_DOCUMENT`, `ACTION_CREATE_DOCUMENT`, or `ACTION_OPEN_DOCUMENT_TREE`). On those ROMs (pure AOSP, Huawei without GMS, etc.) the system threw a fatal `ActivityNotFoundException` before the app could react. Fixes issue #102.

### 🛠️ Changes
• `ActivityNotFoundException` is now caught in all three file flows: import, export, and working-directory selection.
• Import and export retry with `ACTION_GET_CONTENT` when the SAF provider is unavailable.
• If the fallback has no handler either, the `str_no_file_manager` message is shown instead of closing the app.
• New string resource localized to Spanish, English, Simplified Chinese, and Traditional Chinese.
• Closes issue #102 (`ActivityNotFoundException: No Activity found to handle Intent { act=android.intent.action.OPEN_DOCUMENT ... }`).

### ✅ Validation
• `:app:testDebugUnitTest`: 140 tests, 0 failures, 0 errors.
• Debug and release builds (`:app:assembleDebug`, `:app:assembleRelease`) and AAB (`:app:bundleRelease`) completed successfully.
• APK and AAB signed with the production key; certificate SHA-256 fingerprint verified.
• `versionCode 102` and `versionName 1.9.16`.

### 📦 Artifacts
• **APK Release:** `Flash-SPI-Tool-v1.9.16-release.apk`  
  **SHA-256:** `22f619517ab95fb6b15e0cfa8bcf14335e643479d6ac33f8fdfc0dab02c422d1`
• **AAB Release (Google Play Bundle):** `Flash-SPI-Tool-v1.9.16-release.aab`  
  **SHA-256:** `7a334b1a330a90675d6e6a5487b869dc248306ffc4204e8d24d07fe1f5f4c777`
• **APK Debug:** `Flash-SPI-Tool-v1.9.16-debug.apk`  
  **SHA-256:** `f06fbe51e7266a0771e2807937d87a1efc06e8d9095ad697a8851323b295e41f`

---

## 🔗 Enlaces / Links
• **Repositorio / Repository:** https://github.com/Danielk10/Flash-EEPROM-Tool
• **Release:** https://github.com/Danielk10/Flash-EEPROM-Tool/releases/tag/v1.9.16
• **Issue corregido / Fixed issue:** https://github.com/Danielk10/Flash-EEPROM-Tool/issues/102
