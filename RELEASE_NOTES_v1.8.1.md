# Notas de Lanzamiento - Flash-SPI-Tool v1.8.1

Esta versión de producción (`v1.8.1`, código de versión `76`) introduce soluciones definitivas a múltiples problemas de estabilidad y cierres inesperados reportados en versiones anteriores, limpia componentes obsoletos, optimiza recursos y sincroniza el 100% de las traducciones.

---

## 🐛 Corrección de Problemas de Estabilidad (Crashes)

* **Corrección de NPE en Scroll de Logs (Issues #84 y #82):**
  * Se solucionó `NullPointerException` en `LogScrollView.requestChildFocus` al invocar `scrollToChild` cuando el hijo enfocado era nulo o se actualizaba dinámicamente en pantalla.
  * Se implementó `requestChildRectangleOnScreen` personalizado para evitar saltos y bloqueos de foco.

* **Protección contra NPE en el Puente Serie (Issue #81):**
  * Se añadieron modificadores `volatile` y captura de referencias locales seguras (`final UsbSerialPort port = this.usbPort;`) con validaciones de `isOpen()` en `purge()`, `prepareForSerialSession()` y `prepareForFlashromSession()`.
  * Se mitigaron condiciones de carrera cuando el puerto USB se desconecta durante un ciclo de lectura o vaciado de búfer.

* **Manejo Seguro de Cola Asíncrona UsbRequest (Issue #80):**
  * Se corrigió la excepción `IllegalStateException: this request is currently queued` en el Thread B de `PtyBridge`.
  * Se introdujo control explícito de estado encolado (`isQueued`) y cancelación/cierre seguro de peticiones `UsbRequest` ante timeouts o interrupción de hilos.

* **Prevención de Exceso de Buffer IPC en Ciclo de Vida (Issue #78):**
  * Se solucionó `TransactionTooLargeException` al rotar pantalla o cambiar de aplicación (`onSaveInstanceState`), desactivando la serialización de estado (`android:saveEnabled="false"`) en los contenedores de texto y scroll de log.

* **Eliminación de Código y Notificaciones Obsoletas (Issues #79 y #83):**
  * Eliminación integral de `FlashromService.java`, permisos y declaraciones de servicio en segundo plano que no formaban parte de la funcionalidad esencial de la aplicación.

---

## 🌐 Internacionalización y Limpieza de Recursos

* **Localización 100% Completa (Español / Inglés):**
  * Se extrajeron todas las cadenas que permanecían embebidas en menús (`main_menu.xml`), configuraciones y clases Java (`MainActivity`, `UsbController`, `PtyBridge`, `FlashromExecutor`, `BillingManager`, `HexDiffActivity`, `HexViewerActivity`).
  * Sincronización exacta de 229 cadenas en `values/strings.xml` y `values-es/strings.xml` con 0 discrepancias y 0 cadenas sin uso.
  * Ajuste de formato en textos de donación de pizza (eliminación de paréntesis).

* **Limpieza de Recursos y Código Muerto:**
  * Eliminación de selectores de color no referenciados (`btn_tint_brown`, `btn_tint_orange`, `btn_tint_purple`, `btn_tint_teal`) y drawables plantilla no utilizados.
  * Depuración de 19 cadenas obsoletas y duplicadas en los catálogos de recursos.

---

## 🛠️ Validación y Compilación de Producción

* **Validación con Emulador de Hardware Local:**
  * Validación integral con el emulador local de `flashrom`:
    * SERPROG (PTY) — Detección y lectura byte a byte de chip virtual `GD25Q80(B)` verificada.
    * Bus Pirate (PTY) — Conmutación a modo binario/SPI verificada.
    * SPIDriver (PTY) — Comunicación serie emulada verificada.
    * CH341A (USB Socket) — Comunicación directa libusb mockeada verificada.

* **Entorno de Compilación:**
  * Redirección de la salida de compilación Gradle fuera del árbol de trabajo hacia `/tmp/flashrom`.
  * Compilado y firmado en producción:
    * `app-release.apk` (16.0 MB)
    * `app-release.aab` (14.9 MB)
  * Target SDK 37, Min SDK 23, Gradle 9.6, NDK r30.
