# Flash SPI Tool v1.9.13 (versionCode 99) — Correcciones de Billing y Play Core HSDP / Billing & Play Core HSDP Fixes

Fecha de lanzamiento / Release Date: 2 de Noviembre de 2026 / November 2, 2026

---

## 🇪🇸 Español

### 📌 Resumen
Esta versión corrige los dos fallos fatales reportados en Crashlytics: el `NoSuchMethodError` de `ProxyBillingActivity.onCreate` en compilaciones de Android 14 y el `IllegalStateException: targetPackageName is null` de `HsdpShimActivity`. También se elimina el bloqueo preventivo que impedía comprar en algunos dispositivos Android 14.

### 🛠️ Correcciones Principales

#### 1. `ProxyBillingActivity.onCreate` — `NoSuchMethodError` (Crashlytics `a6e619dc60f9cd6944e726e6b3ba602b`)
• **Causa raíz:** Play Billing 9.1.0 invoca `ActivityOptions.setPendingIntentBackgroundActivityStartMode` desde `ProxyBillingActivity.onCreate` con una comprobación de SDK que no cubre todas las compilaciones de Android 14. En dispositivos sin ese método, el flujo de compra termina en `NoSuchMethodError`.
• **Corrección:** se fijó **Play Billing 8.3.0**, cuya `ProxyBillingActivity` no referencia ese método. Se eliminó además `isBillingFlowCompatible()`, que bloqueaba la compra en dispositivos Android 14 donde el método no estaba disponible.

#### 2. `HsdpShimActivity.zzd` — `targetPackageName is null` (Crashlytics `80e49a5ebec1a785e34f5735141bac92`)
• **Causa raíz:** `play-services-ads-api 25.5.0` arrastra `com.google.android.play:hsdp:2.0.1`. Su `HsdpShimActivity` lanzaba `IllegalStateException` cuando el intent no incluía `target_package_name`.
• **Corrección:** se fijó **HSDP 2.2.0**, que valida los extras requeridos en `onCreate`/`onNewIntent` y termina la actividad limpiamente si faltan, además de comprobar `isFinishing()` en `onAttachedToWindow`.

### ✅ Validación
• **Tests unitarios:** 136 pruebas, 0 fallos, 0 errores, 0 omitidas.
• **Emulador flashrom:** validación completa de los cinco backends — **Serprog, Bus Pirate, SPIDriver, CH341A y FTDI FT232H** — con detección correcta del chip virtual GigaDevice GD25Q80.
• **Build release:** `:app:assembleRelease` y `:app:bundleRelease` correctos; APK y AAB firmados con la clave de producción.

### 📦 Artefactos
• **APK Release:** `Flash-SPI-Tool-v1.9.13-release.apk`  
  **SHA-256:** `d464efca011463fc09d6f46e94df66a24e0758e32a18859014a404b9f717105e`
• **AAB Release (Google Play Bundle):** `Flash-SPI-Tool-v1.9.13-release.aab`  
  **SHA-256:** `f811d277b5576251e9718e2444a0397b391bcca0ab558eaa6b7beee6d82f8918`

---

## 🇺🇸 English

### 📌 Overview
This release fixes the two fatal Crashlytics issues: the `NoSuchMethodError` in `ProxyBillingActivity.onCreate` on certain Android 14 builds and the `IllegalStateException: targetPackageName is null` in `HsdpShimActivity`. It also removes the compatibility guard that blocked purchases on some Android 14 devices.

### 🛠️ Key Fixes

#### 1. `ProxyBillingActivity.onCreate` — `NoSuchMethodError` (Crashlytics `a6e619dc60f9cd6944e726e6b3ba602b`)
• **Root cause:** Play Billing 9.1.0 calls `ActivityOptions.setPendingIntentBackgroundActivityStartMode` from `ProxyBillingActivity.onCreate` behind an SDK check that does not cover every Android 14 build. On devices without the method, the purchase flow fails with `NoSuchMethodError`.
• **Fix:** pinned **Play Billing 8.3.0**, whose `ProxyBillingActivity` does not reference the method. Removed `isBillingFlowCompatible()`, which blocked purchases on Android 14 devices lacking the method.

#### 2. `HsdpShimActivity.zzd` — `targetPackageName is null` (Crashlytics `80e49a5ebec1a785e34f5735141bac92`)
• **Root cause:** `play-services-ads-api 25.5.0` pulls `com.google.android.play:hsdp:2.0.1`. Its `HsdpShimActivity` threw `IllegalStateException` when the intent had no `target_package_name`.
• **Fix:** pinned **HSDP 2.2.0**, which validates the required extras in `onCreate`/`onNewIntent` and finishes the activity cleanly when they are missing, and checks `isFinishing()` in `onAttachedToWindow`.

### ✅ Validation
• **Unit tests:** 136 tests, 0 failures, 0 errors, 0 skipped.
• **flashrom emulator:** full validation of all five backends — **Serprog, Bus Pirate, SPIDriver, CH341A and FTDI FT232H** — with successful detection of the virtual GigaDevice GD25Q80 chip.
• **Release build:** `:app:assembleRelease` and `:app:bundleRelease` succeeded; APK and AAB signed with the production key.

### 📦 Artifacts
• **APK Release:** `Flash-SPI-Tool-v1.9.13-release.apk`  
  **SHA-256:** `d464efca011463fc09d6f46e94df66a24e0758e32a18859014a404b9f717105e`
• **AAB Release (Google Play Bundle):** `Flash-SPI-Tool-v1.9.13-release.aab`  
  **SHA-256:** `f811d277b5576251e9718e2444a0397b391bcca0ab558eaa6b7beee6d82f8918`

---

## 📋 Notas Cortas para Google Play Console / Short Play Store Release Notes

### 🇪🇸 Español (`es-419` / `es-ES`)
```text
• Corregido cierre en Google Play Billing (ProxyBillingActivity) en Android 14.
• Corregido cierre de HsdpShimActivity por falta de targetPackageName.
• Actualizados Play Billing 8.3.0 y HSDP 2.2.0 para mayor compatibilidad.
• Mejoras de estabilidad en lectura/escritura de memorias SPI.
```

### 🇺🇸 English (`en-US`)
```text
• Fixed a Google Play Billing (ProxyBillingActivity) crash on Android 14.
• Fixed the HsdpShimActivity crash caused by a missing targetPackageName.
• Updated Play Billing 8.3.0 and HSDP 2.2.0 for better compatibility.
• Stability improvements for SPI flash read/write operations.
```

### 🇨🇳 简体中文 (`zh-CN`)
```text
• 修复 Android 14 上 Google Play Billing（ProxyBillingActivity）崩溃。
• 修复 HsdpShimActivity 因缺少 targetPackageName 导致的崩溃。
• 更新 Play Billing 8.3.0 和 HSDP 2.2.0，提高兼容性。
• 提升 SPI 闪存读写稳定性。
```

### 🇹🇼 繁體中文 (`zh-TW`)
```text
• 修復 Android 14 上 Google Play Billing（ProxyBillingActivity）崩潰。
• 修復 HsdpShimActivity 因缺少 targetPackageName 導致的崩潰。
• 更新 Play Billing 8.3.0 和 HSDP 2.2.0，提高相容性。
• 提升 SPI 快閃記憶體讀寫穩定性。
```

---

## 🔗 Enlaces / Links
• **Repositorio / Repository:** https://github.com/Danielk10/Flash-EEPROM-Tool
• **Lanzamiento / Release:** https://github.com/Danielk10/Flash-EEPROM-Tool/releases/tag/v1.9.13
