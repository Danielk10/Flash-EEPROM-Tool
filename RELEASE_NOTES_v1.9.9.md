# Flash SPI Tool v1.9.9 (versionCode 95) — Soporte CH347F y frecuencia SPI configurable / CH347F Support & Configurable SPI Speed

Esta versión (`v1.9.9`, código de versión `95`) añade el soporte del programador **CH347F** (`1a86:55de`) mediante el backend nativo `ch347_spi`, amplía la detección automática de programadores USB/serie compatibles y estrena un selector de frecuencia SPI de **15/30/60 MHz** para CH347T/F. También incorpora las correcciones de estabilidad de facturación preparadas para la versión 1.9.8, que no llegó a publicarse como release de GitHub.

This release (`v1.9.9`, version code `95`) adds support for the **CH347F** programmer (`1a86:55de`) through the native `ch347_spi` backend, expands automatic detection of compatible USB/serial programmers, and introduces a **15/30/60 MHz** SPI clock selector for CH347T/F. It also includes the billing stability fixes prepared for version 1.9.8, which was never published as a GitHub release.

---

## 🚀 Novedades y Mejoras Técnicas

### 1. Soporte CH347F (`1a86:55de`)
• **Detección USB:** el CH347F se reconoce ahora como `1a86:55de` y se asigna automáticamente al programador `ch347_spi`.
• **CH347T:** se mantienen los identificadores `1a86:5523` y `1a86:55db` para las variantes CH347T/SPI Mode 3.
• **Sin cambios en consola:** los comandos nativos escritos manualmente siguen ejecutándose tal cual; la app no reescribe ni limita su contenido.
• **Firmware incluido:** el motor `flashrom v1.8.0-devel` ya incorpora el soporte CH347T/F y el parámetro `spispeed`, por lo que no fue necesario recompilar el binario nativo.

### 2. Selector de frecuencia SPI (CH347T/F)
• **Opciones:** `15 MHz` (predeterminada y segura), `30 MHz` (rápida) y `60 MHz` (máxima).
• **Aplicación automática:** la frecuencia se añade como `spispeed=15M/30M/60M` únicamente a los comandos generados por la interfaz gráfica cuando el programador es `ch347_spi`.
• **Valor manual respetado:** si el usuario ya escribió `spispeed=` en el programador, la app conserva ese valor explícito.
• **Aislamiento:** los comandos de la consola no pasan por esta lógica y los programadores serie (Serprog, Bus Pirate y SPIDriver) no se modifican funcionalmente.
• **Persistencia:** la preferencia se guarda por separado en `spi_speed`, con `15M` como valor inicial.
• **Aviso de estabilidad:** la propia interfaz advierte de que 30/60 MHz pueden ser inestables según el chip, el cableado y la calidad de la conexión; ante errores debe volver a 15 MHz.

### 3. Detección ampliada de programadores compatibles
• **FTDI/FT2232:** se añaden FT4233H, TIAO TUMPA/TUMPA Lite, Kristech KT-LINK, Amontec JTAGkey, GOEPEL PicoTAP, FIC OpenMoko y las variantes Olimex ARM-USB-OCD/TINY (`0003`, `0004`, `002a`, `002b`) y Google Servo (`5001`-`5003`).
• **Serprog USB-serie:** se reconocen Teensy, Atmel LUFA, LeafLabs Maple, ARM mbed, STM32 Virtual COM Port, Raspberry Pi Pico (MicroPython/SDK), CH9102F, CP2105/CP2108 y las variantes PL2303GC/GB/GT/GL/GE/GS, además de los adaptadores ya soportados (Arduino, CH340, CP2102 y PL2303).
• **SEGGER J-Link:** `libjaylink` reconoce cualquier dispositivo con VID `1366`, no solo unos PID concretos; se eliminaron las entradas parciales del filtro USB.
• **Filtro Android:** `device_filter.xml` incorpora los mismos VID/PID para que Android ofrezca abrir la app al conectar el programador.

### 4. Interfaz y experiencia de uso
• **Selector de idioma retirado:** la app vuelve a seguir el idioma del sistema y desaparece la entrada de menú correspondiente, evitando conflictos con la localización nativa.
• **Selector de chip simplificado:** solo el botón de chip abre el diálogo de selección; se elimina el área pulsable duplicada.
• **Ajustes del programador:** el selector de frecuencia solo aparece cuando el programador configurado es `ch347_spi`.

### 5. Correcciones de facturación incluidas (base v1.9.8)
• **NPE de `BillingManager`:** el ciclo de vida de `destroy()` es seguro; los callbacks pendientes se cancelan y no se invoca un listener nulo (GitHub #98 / Crashlytics `128a80b5135922d25fe53a1ae8f9a181`).
• **Play Billing 9.1.0 en Android 14:** se comprueba por reflexión la disponibilidad de `ActivityOptions.setPendingIntentBackgroundActivityStartMode()` y se devuelve un error controlado en lugar de provocar un `NoSuchMethodError` (Crashlytics `a6e619dc60f9cd6944e726e6b3ba602b`).

### 6. Colaboradores y Agradecimientos Especiales
• **fsvip:** Agradecimiento especial por las pruebas de hardware y especificaciones para el soporte del programador CH347F / CH347.

---

## ✅ Validación de esta versión

• **Pruebas unitarias:** **119 pruebas, 0 fallos, 0 errores, 0 omitidas** (`:app:testDebugUnitTest`).
• **Emulador flashrom:** validación completa sobre GD25Q80 de 1 MB en los cuatro backends: **Serprog, Bus Pirate, SPIDriver y CH341A**.
• **Flujo probado por backend:** escritura del patrón de 1 MB → verificación → relectura con SHA-256 idéntico → borrado → relectura completa en `0xFF`.
• **Resultado:** los cuatro backends superaron escritura, verificación, integridad SHA-256 y borrado.
• **Hardware físico:** el CH347F se validó contra el emulador del proyecto; la comprobación con hardware real queda documentada en GitHub #99 y #100.

---

## 🌐 Notas de Versión para Google Play (Español / English / 简体中文 / 繁體中文)

### 🇪🇸 Español (`es-419` / `es-ES`)
- Añadido soporte para el programador CH347F y nuevos IDs USB compatibles (FTDI, Serprog, J-Link).
- Selector de frecuencia SPI (15/30/60 MHz) para CH347T/F, con 15 MHz por defecto.
- Correcciones de estabilidad en facturación y compatibilidad con Android 14.
- Agradecimiento especial al colaborador fsvip por pruebas y soporte de hardware.
- Mejoras generales de estabilidad y compatibilidad (API 24 - 37).

### 🇺🇸 English (`en-US`)
- Added support for the CH347F programmer and expanded USB IDs (FTDI, Serprog, J-Link).
- New 15/30/60 MHz SPI clock selector for CH347T/F, defaulting to 15 MHz.
- In-app billing lifecycle stability and Android 14 compatibility fixes.
- Special thanks to collaborator fsvip for hardware testing and support.
- General stability and compatibility improvements (API 24 - 37).

### 🇨🇳 简体中文 (`zh-CN`)
- 新增对 CH347F 编程器的支持及扩展兼容 USB ID（FTDI、Serprog、J-Link）。
- 新增 CH347T/F 的 15/30/60 MHz SPI 时钟频率选择器（默认 15 MHz）。
- 修复应用内购买生命周期稳定性与 Android 14 兼容性问题。
- 特别致谢协作者 fsvip 提供的硬件规格与测试支持。
- 常规稳定性与兼容性改进（API 24 - 37）。

### 🇹🇼 繁體中文 (`zh-TW`)
- 新增對 CH347F 燒錄器的支援及擴展相容 USB ID（FTDI、Serprog、J-Link）。
- 新增 CH347T/F 的 15/30/60 MHz SPI 時脈頻率選擇器（預設 15 MHz）。
- 修復應用程式內購買生命週期穩定性與 Android 14 相容性問題。
- 特別致謝協作者 fsvip 提供的硬體規格與測試支援。
- 常規穩定性與相容性改進（API 24 - 37）。

---

## 📦 Artefactos de la Versión

• **APK Release:** `Flash-SPI-Tool-v1.9.9-release.apk` (19.7 MiB)  
  **SHA-256:** `dbe0153fa5d49da25cd5c53b5af1c3282b665073017cb32c7cc3b51fad667905`  
• **AAB Release (Google Play Bundle):** `Flash-SPI-Tool-v1.9.9.aab` (18.6 MiB)  
  **SHA-256:** `8700a94dcc844b5e9a343cad5995c4b70392d1f44d983ac5cf3429cc72b66e3b`  
• **Firma Digital:** Certificado oficial RSA 2048-bit (`firma_flash_spi_tool.jks`)
