# Flash SPI Tool v1.9.8 (versionCode 94) — Corrección de estabilidad en facturación / Billing Stability Fix

Esta versión (`v1.9.8`, código de versión `94`) corrige los dos fallos de facturación que permanecían abiertos en GitHub y Crashlytics: el `NullPointerException` de `BillingManager` al destruirse una actividad con una consulta de productos en curso y el `NoSuchMethodError` de Play Billing 9.1.0 en compilaciones de Android 14 que no exponen la API `ActivityOptions.setPendingIntentBackgroundActivityStartMode()`.

This release (`v1.9.8`, version code `94`) fixes the two billing failures still open in GitHub and Crashlytics: the `BillingManager` `NullPointerException` when an activity is destroyed while a product query is in flight, and the Play Billing 9.1.0 `NoSuchMethodError` on Android 14 builds that do not expose `ActivityOptions.setPendingIntentBackgroundActivityStartMode()`.

---

## 🚀 Correcciones y Mejoras Técnicas

### 1. NPE en `BillingManager` (GitHub #98 / Crashlytics `128a80b5135922d25fe53a1ae8f9a181`)
• **Causa raíz:** `destroy()` anulaba `listener` mientras el callback de `queryProductDetailsAsync` ya estaba encolado en el `Handler` principal. Al ejecutarse el lambda, desreferenciaba `listener` null y lanzaba la excepción en `BillingManager.java:214`.
• **Ciclo de vida seguro:** `destroyed` y `listener` ahora son `volatile`; `destroy()` cancela los callbacks pendientes con `removeCallbacksAndMessages(null)`.
• **Guardas defensivas:** `startConnection()`, `queryProducts()`, `queryActivePurchases()`, `onPurchasesUpdated()` y los callbacks de conexión/productos/compras son no-op después de `destroy()`.
• **Callbacks publicados:** capturan el listener localmente y comprueban `destroyed`/null antes de invocarlo, eliminando la carrera entre el encolado y la destrucción de la actividad.
• **Cobertura de regresión:** nuevas pruebas en `BillingManagerTest` para callbacks tardíos de compras, productos, compras activas y para `launchPurchaseFlow()` tras `destroy()`.

### 2. `NoSuchMethodError` de Play Billing 9.1.0 (Crashlytics `a6e619dc60f9cd6944e726e6b3ba602b`)
• **Causa raíz:** Play Billing 9.1.0 llama desde `ProxyBillingActivity.onCreate` a `ActivityOptions.setPendingIntentBackgroundActivityStartMode(int)` cuando `SDK_INT >= 34`. Algunas compilaciones de Android 14 no exponen ese método y el flujo de compra termina en `NoSuchMethodError`.
• **Mitigación:** antes de `launchBillingFlow()` se comprueba la disponibilidad real del método mediante reflexión. Si no existe, se devuelve un error controlado al listener en lugar de abrir `ProxyBillingActivity`, evitando el crash. En dispositivos compatibles el flujo de compra no cambia.
• **Dependencia:** Play Billing `9.1.0` sigue siendo la última versión publicada en Google Maven; la protección se mantendrá hasta que Google publique una versión corregida.

### 3. Cierre de incidencias
• **GitHub #98:** corregido con el blindaje de ciclo de vida de `BillingManager`.
• **GitHub #97:** cerrado como no accionable; la excepción se origina dentro de App Center 5.0.6 y no hay código propio implicado.
• **Crashlytics:** ambos issues abiertos revisados, documentados y cerrados.

---

## 🌐 Notas de Versión para Google Play (Español / English)

### 🇪🇸 Español (`es-419` / `es-ES`)
- Corregido el cierre inesperado al consultar productos de compra mientras se cierra una pantalla.
- Corregida la compatibilidad del flujo de compra en dispositivos Android 14 con compilaciones que no soportan la API de Play Billing.
- Mayor estabilidad en el ciclo de vida de la facturación y en las compras pendientes.
- Mejoras generales de estabilidad y rendimiento (API 24 - 37).

### 🇺🇸 English (`en-US`)
- Fixed the unexpected crash when querying in-app products while a screen is being closed.
- Fixed purchase flow compatibility on Android 14 builds that do not support the Play Billing API.
- Improved billing lifecycle stability and pending purchase handling.
- General stability and performance improvements (API 24 - 37).

---

## 📦 Artefactos de la Versión

• **APK Release:** `Flash-SPI-Tool-v1.9.8-release.apk` (PENDING MB)  
  **SHA-256:** `PENDING`  
• **AAB Release (Google Play Bundle):** `Flash-SPI-Tool-v1.9.8.aab` (PENDING MB)  
  **SHA-256:** `PENDING`  
• **Firma Digital:** Certificado oficial RSA 2048-bit (`firma_flash_spi_tool.jks`)
