# Notas de Lanzamiento - Flash-SPI-Tool v1.8.0

Esta versión de producción (`v1.8.0`, código de versión `75`) introduce optimizaciones menores, mejoras de estabilidad en la interfaz y limpieza integral de los permisos del manifiesto para garantizar máxima compatibilidad con las políticas vigentes de Google Play.

---

## 🚀 Optimizaciones y Mejoras

* **Depuración Integral del Manifiesto (`AndroidManifest.xml`):**
    * Limpieza de declaraciones de permisos redundantes y obsoletas, dejando una estructura estricta y segura.
    * Optimización del perfil de seguridad y cumplimiento normativo para la sección de Seguridad de los Datos en Google Play.
* **Mejoras Internas de Rendimiento:**
    * Refactorización y optimización de componentes internos de la interfaz y callbacks de ciclo de vida.
    * Gestión más eficiente de recursos en segundo plano tras operaciones exitosas con memorias flash.
* **Compatibilidad de Plataforma:**
    * Validación y soporte continuo desde Android 6.0 (API 23) hasta Android 16 (API 37).
    * Consolidación de flujos de ejecución asíncronos y descriptores de comunicación por hardware.

---

## 🛠️ Validación y Compilación

* **Entorno de Compilación:**
    * Android SDK 37 (compileSdk 37, targetSdk 37, minSdk 23).
    * Compilación verificada con Gradle 9.6 y NDK r30.
