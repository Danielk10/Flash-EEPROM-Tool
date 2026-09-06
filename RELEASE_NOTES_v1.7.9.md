# Notas de Lanzamiento - Flash-SPI-Tool v1.7.9

Esta versión de producción (`v1.7.9`, código de versión `71`) introduce soporte oficial para **compras integradas y donaciones a través de Google Play Billing** ("Invítame una Pizza 🍕 $5"), permitiendo a los usuarios apoyar de forma voluntaria el desarrollo, mantenimiento y soporte continuo de **Flash SPI Tool**.

---

## 🚀 Nuevas Características y Mejoras

*   **Integración de Google Play Billing Library (v7.1.1):**
    *   Implementación de pasarela de pago nativa y segura bajo la normativa oficial de Google Play.
    *   Configuración de producto integrado de tipo consumible (`donacion_pizza`), lo que permite donar varias veces si el usuario así lo desea.
    *   Consumo automático e inmediato del token de compra (`consumeAsync`) para garantizar la confirmación de la transacción y prevenir reembolsos por timeout.
*   **Nueva Opción en el Menú Principal:**
    *   Se agregó la entrada **"Apoyar con una Pizza 🍕 ($5)"** en el menú de la aplicación.
    *   Diálogo interactivo de donación con detección dinámica del precio formateado y divisa local devuelta por Google Play.
    *   Mensaje de confirmación y agradecimiento tras completar la donación exitosamente.
*   **Internacionalización Completa:**
    *   Textos y cadenas traducidos para español e inglés en `values/strings.xml` y `values-es/strings.xml`.
*   **Guía de Publicación y Configuración (`GUIA_COMPRAS_GOOGLE_PLAY.md`):**
    *   Documentación exhaustiva sobre la configuración de productos integrados en Google Play Console, cuentas de prueba (License Testing) y opciones de despliegue a producción.

---

## 🛠️ Validación y Pruebas

*   **Compilación y Compatibilidad:**
    *   Verificación exitosa con Android SDK 37 (compileSdk 37, targetSdk 37, minSdk 23).
    *   Inclusión y fusión limpia del permiso `com.android.vending.BILLING`.

---

## 📝 Textos para Google Play Console (Novedades de esta versión)

### 🇪🇸 Español (es-419 / es-ES):
```text
• Nueva opción en el menú: "Apoyar con una Pizza 🍕 ($5)" mediante Google Play Billing.
• Detección automática de precios en moneda local según tu país.
• Consumo inmediato de compra que permite realizar aportes múltiples.
• Iconografía mejorada y optimizaciones internas de estabilidad.
```

### 🇺🇸 English (en-US):
```text
• New menu option: Support with a Pizza 🍕 ($5) via Google Play Billing.
• Automatic localized currency and price detection.
• Instant purchase consumption allowing multiple voluntary donations.
• Improved iconography and internal stability optimizations.
```

