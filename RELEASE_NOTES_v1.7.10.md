# Notas de Lanzamiento - Flash-SPI-Tool v1.7.10

Esta versión de producción (`v1.7.10`, código de versión `72`) actualiza la **firma criptográfica oficial de la aplicación (Release Signing Key)** al certificado correspondiente de producción y consolida el soporte para **donaciones voluntarias mediante Google Play Billing ("Invítame una Pizza 🍕 $5")**.

---

## 🚀 Nuevas Características y Mejoras

*   **Firma Oficial de Producción:**
    *   Binarios APK y AAB firmados con el certificado de release oficial (`Juego Java 30 Pasos`), garantizando compatibilidad de actualización directa desde Google Play Store.
*   **Integración de Google Play Billing Library (v7.1.1):**
    *   Pasarela de facturación nativa y segura para aportes voluntarios bajo las políticas de Google Play.
    *   Configuración de producto consumible (`donacion_pizza`), permitiendo al usuario donar nuevamente en el futuro si así lo desea.
    *   Consumo automático e inmediato de la compra (`consumeAsync`) para prevenir cancelaciones o reembolsos por tiempo de espera.
*   **Nueva Opción en el Menú Principal con Ícono Exclusivo:**
    *   Ícono vectorial de rebanada de pizza (`ic_pizza.xml`) en el menú y en los diálogos de la aplicación.
    *   Diálogo personalizado: *"¿Te gustó mi trabajo o te resultó útil Flash SPI Tool? Puedes invitarle una pizza al desarrollador para apoyar el mantenimiento continuo y futuras mejoras."*
    *   Detección dinámica de moneda local y precios formateados según el país del usuario.
    *   Diálogo de agradecimiento tras completar la donación.
*   **Soporte Multilingüe Completo:**
    *   Traducciones oficiales en español e inglés en `values-es/strings.xml` y `values/strings.xml`.
*   **Guía de Publicación (`GUIA_COMPRAS_GOOGLE_PLAY.md`):**
    *   Paso a paso para crear y activar el producto integrado `donacion_pizza`, habilitar cuentas de prueba (License Testing) y opciones de despliegue a producción.

---

## 📝 Textos para Google Play Console (Novedades de esta versión)

### 🇪🇸 Español (es-419 / es-ES):
```text
• Nueva opción en el menú: "Apoyar con una Pizza 🍕 ($5)" mediante Google Play Billing.
• Detección automática de precios en moneda local según tu país.
• Consumo inmediato de compra que permite realizar aportes voluntarios múltiples.
• Iconografía mejorada y optimizaciones internas de estabilidad.
```

### 🇺🇸 English (en-US):
```text
• New menu option: Support with a Pizza 🍕 ($5) via Google Play Billing.
• Automatic localized currency and price detection.
• Instant purchase consumption allowing multiple voluntary donations.
• Improved iconography and internal stability optimizations.
```
