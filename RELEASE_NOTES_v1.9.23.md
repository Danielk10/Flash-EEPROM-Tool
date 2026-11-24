# Flash SPI Tool v1.9.23 (versionCode 109) — Optimización del ciclo de vida de anuncios y Pase Pro

Fecha / Date: 24 de noviembre de 2026 / November 24, 2026

---

## 🇪🇸 Español

### 📌 Resumen
Esta versión corrige una condición de carrera en el ciclo de vida del anuncio nativo durante el arranque en frío, optimiza la carga del banner adaptativo para evitar áreas en blanco y garantiza la persistencia inmediata del Pase Pro y compras permanentes.

### 🛠️ Cambios
• **Corrección del anuncio nativo en arranque:** Se sincronizó la carga del anuncio nativo con la resolución del consentimiento UMP mediante listeners, garantizando que el bloque nativo se solicite automáticamente tan pronto como la verificación de políticas esté lista.
• **Prevención de peticiones concurrentes:** Se incorporaron guardas de estado en vuelo para evitar descargas duplicadas de anuncios nativos y prevenir que errores de red tardíos oculten anuncios válidos ya cargados.
• **Garantía en regiones sin requisito de consentimiento:** Los anuncios se habilitan de inmediato en países donde el consentimiento no es exigido o cuando UMP reporta estado no requerido.
• **Banner adaptativo optimizado:** El contenedor del banner permanece oculto hasta que el anuncio se encuentra completamente descargado, eliminando parpadeos y cajas vacías en conexiones lentas.
• **Pase Pro y compra sin anuncios:** Se perfeccionó la reactivación y desactivación instantánea de banners y nativos tanto para compras de por vida como para el pase temporal de 12 horas.

---

## 🇺🇸 English

### 📌 Overview
This release fixes a lifecycle race condition affecting native ad loading on cold starts, optimizes adaptive banner rendering to prevent blank boxes, and ensures immediate ad removal upon activating Pro Pass or permanent purchase.

### 🛠️ Changes
• **Cold-start native ad loading fix:** Synchronized native ad loading with UMP consent resolution via callbacks, ensuring native cards are requested reliably once privacy requirements are met.
• **Concurrent request guard:** Added in-flight protection to prevent redundant native ad queries and prevent late failure callbacks from hiding successfully loaded ads.
• **Reliable display in non-consent regions:** Ensured ads load smoothly in countries where consent forms are not required.
• **Smoother adaptive banner:** Banner view remains hidden until content is loaded, avoiding layout flickering or empty slots on slow networks.
• **Instant Pro status reflection:** Verified instant dismissal and restoration of ad units across lifetime purchases and 12-hour Pro Passes.
