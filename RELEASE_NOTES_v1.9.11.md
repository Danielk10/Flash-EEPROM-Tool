# Flash SPI Tool v1.9.11 (versionCode 97) — Soporte Serial/USB Universal, Flashrom v2.0 con Serprog/BusPirate/SPIDriver y Parche FTDI / Universal Serial/USB Support, Flashrom v2.0 with Serprog/BusPirate/SPIDriver & FTDI Patch

Esta versión expande masivamente la compatibilidad de hardware del ecosistema **Flash SPI Tool**: compila y activa de forma nativa en Android la suite completa de programadores serie y puente USB de **flashrom v2.0.0-devel** (`serprog`, `buspirate_spi`, `spidriver`, `pony_spi`, `ch347_spi`, `stlinkv3_spi`, `dirtyjtag_spi`, `nv_sma_spi`, `linux_spi`, `linux_mtd`, `asm106x`), aísla el ciclo de vida del puente serie PTY/Socket para prevenir bloqueos de interfaz USB (`LIBUSB_ERROR_BUSY`), incorpora desambiguación dinámica de chips FTDI por descriptor de producto, resuelve de forma definitiva los errores de inicialización FTDI (`-3` y `-10`) mediante interceptación `ioctl` sin root, actualiza los binarios nativos ARM64 con alineación obligatoria de páginas de memoria de 16 KB para Android 15 y sincroniza los recursos de autocompletado del sistema.

This release massively expands hardware compatibility across the **Flash SPI Tool** ecosystem: natively compiles and enables the complete serial and USB-bridge programmer suite in **flashrom v2.0.0-devel** on Android (`serprog`, `buspirate_spi`, `spidriver`, `pony_spi`, `ch347_spi`, `stlinkv3_spi`, `dirtyjtag_spi`, `nv_sma_spi`, `linux_spi`, `linux_mtd`, `asm106x`), completely isolates the PTY/Socket serial bridge lifecycle to prevent interface contention (`LIBUSB_ERROR_BUSY`), adds dynamic FTDI chip disambiguation by USB product descriptor strings, definitively resolves FTDI initialization errors (`-3` and `-10`) via rootless descriptor `ioctl` interception, updates ARM64 native binaries with 16 KB memory page alignment for Android 15, and synchronizes shell completion assets.

---

## 🚀 Novedades y Mejoras Técnicas

### 1. Integración de `flashrom v2.0.0-devel` con Suite Completa de Programadores en Android
• **Activación de controladores serie en Android:**  
  En compilaciones estándar de Android / Termux, Meson desactiva por defecto los programadores serie al no incluir `'android'` en la lista `systems_serial`. Mediante parcheo automatizado en `build_flashrom_custom.sh`, se incorporaron formalmente a la compilación:
  - `serprog` (Serial flasher universal para Arduino, RP2040, ESP32, STM32 vía USB-CDC).
  - `buspirate_spi` (Bus Pirate v3 y v4).
  - `spidriver` (Excamera SPIDriver con display y monitor de corriente en vivo).
  - `pony_spi` (SI-Prog / PonyProg serie).
• **Activación de controladores de hardware extendidos y Linux kernel:**  
  Se habilitó soporte para `linux_spi`, `linux_mtd`, `ch347_spi` (alta velocidad hasta 60 MHz), `stlinkv3_spi`, `dirtyjtag_spi`, `nv_sma_spi`, `raiden_debug_spi`, `asm106x`, `atavia`, `nicintel`, `ogp_spi`.
• **Cumplimiento estricto de páginas de memoria de 16 KB (Android 15):**  
  Todos los binarios ejecutables y librerías dinámicas fueron compilados con `-Wl,-z,max-page-size=16384` (`0x4000`), garantizando compatibilidad nativa y previniendo fallos de segmentación en dispositivos modernos con Android 15+.

### 2. Control de Ciclo de Vida USB y Aislamiento PTY vs USB Directo (`libusb`)
• **Prevención de colisión de interfaces (`LIBUSB_ERROR_BUSY / -6`):**  
  Cuando un programador serie (`serprog`, `buspirate_spi`, `spidriver`) se conecta, el sistema Android inicia un puente serie (`UsbSerialBridge`) enlazado a un pseudoterminal `/dev/pts/N` o socket TCP local `127.0.0.1:port`. Si el usuario alternaba a un programador USB directo (`ch341a_spi`, `ft2232_spi`), la interfaz USB permanecía reclamada por el puente java, provocando fallos en `libusb_claim_interface()`.
  - Se implementó `UsbController.ensurePtyBridge(selectedProgrammer)` y `UsbController.closePtyBridge()` en `MainActivity.java`.
  - La app libera y cierra limpiamente la interfaz USB antes de ceder el descriptor `ANDROID_USB_FD` a `libusb`, eliminando el error `-6`.
• **Desambiguación dinámica de hardware con mismo VID:PID (`0403:6001` y `0403:6015`):**  
  Diversos dispositivos comparten el mismo chip FTDI FT232R o FT230X. `UsbController.resolveAutoProgrammer()` inspecciona ahora el `productName`:
  - `Bus Pirate` $\rightarrow$ `buspirate_spi:dev=/dev/pts/N`
  - `SPIDriver` $\rightarrow$ `spidriver:dev=/dev/pts/N`
  - `USB-Blaster` $\rightarrow$ `usbblaster_spi`
  - `Developerbox` $\rightarrow$ `ft2232_spi:type=4232H`
  - Genérico / Arduino $\rightarrow$ `serprog:dev=/dev/pts/N`
• **Ampliación masiva de filtros USB (`device_filter.xml` y `USB_AUTO_MAP`):**  
  Soporte automático para Arduino Mega 2560 (`2341:0042`, `2341:0010`), Leonardo (`2341:8036`), Micro (`2341:8037`), Due (`2341:003e`), SparkFun Pro Micro (`1b4f:9205`, `1b4f:9206`), Raspberry Pi Pico CDC stdio (`2e8a:0003`) y Debug Probe (`2e8a:0009`), ESP32-S2/S3/C3/C6 CDC (`303a:0002`, `303a:1001`), WCH CH342/CH343 (`1a86:55d2`, `1a86:55d3`), CP2104 (`10c4:ea61`) y DirtyJTAG 2 (`1209:c0cb`).

### 3. Resolución Integral del Soporte FTDI (FT232H, FT2232H, FT4232H)
• **Corrección del error `-3 (device not found)`:**  
  Mapeo explícito en `USB_AUTO_MAP` con parámetros de silicio (`ft2232_spi:type=232H`, `type=2232H`, `type=4232H`).
• **Corrección del error `-10 (libusb_get_config_descriptor() failed)`:**  
  Interceptación quirúrgica en `libusb` mediante `ioctl(fd, USBDEVFS_CONTROL)` con descriptor `0x0200` para leer la configuración en memoria sin requerir root ni acceso a `/dev/bus/usb/`.

### 4. Sincronización de Binarios Nativo ARM64 (`jniLibs/arm64-v8a`)
• **Binarios y Librerías Actualizadas:**  
  - `libflashrom_bin.so`: Actualizado a **918 KB** con soporte nativo para los más de 30 programadores.
  - `libflashrom.so`, `libflashrom_1.so`, `libflashrom_1_0_0.so`: Actualizadas a **883 KB**.
  - `libpci.so.3` / `libpci_3.so` (91 KB): Enlazadas dinámicamente para los controladores PCI.
  - `libusb_1_0.so` (137 KB), `libftdi1_2.so` (172 KB), `libcrypto_3.so` (OpenSSL 3, 5.21 MB), `libjaylink.so` (77 KB).
• **Assets Actualizados:**  
  - `flashrom.bash`: Base de autocompletado sincronizada con la lista completa de controladores activos.
  - `pci.ids.gz`: Base de datos de identificadores PCI verificada.

---

## ✅ Validación de esta Versión

• **Verificación en Runtime CLI:**  
  Comprobación nativa directa en el ejecutable confirmando estado `[OK]` en runtime para:  
  `ch341a_spi`, `ch347_spi`, `ft2232_spi`, `usbblaster_spi`, `serprog`, `buspirate_spi`, `spidriver`, `dirtyjtag_spi`, `stlinkv3_spi`, `jlink_spi`, `pickit2_spi`, `dediprog`, `digilent_spi`, `raiden_debug_spi`, `pony_spi`, `nv_sma_spi`, `asm106x`, `atavia`, `linux_mtd`, `linux_spi`, `nicintel`.
• **Pruebas de Aislamiento de Puertos:**  
  Verificación de liberación de descriptor USB y desconexión limpia del puente serie antes de conmutar entre programadores CDC y programadores USB directos.
• **Pruebas Unitarias:**  
  Mapeo de programadores, desambiguación dinámica por nombre de producto y filtros USB validados exitosamente en `UsbControllerTest.java` y `ExampleUnitTest.java`.

---

## 🌐 Notas de Versión para Google Play (Bilingüe Obligatorio)

### 🇪🇸 Español (`es-419` / `es-ES`)
- Soporte para flashrom v2.0: activados Serprog, Bus Pirate, SPIDriver, CH347 y ST-LINK V3.
- Corrección de conflicto USB: aislamiento limpio entre puente serie PTY y programadores directos libusb.
- Desambiguación automática por modelo para adaptadores basados en chips FTDI.
- Ampliada compatibilidad con Arduino Mega/Due/Leonardo, Raspberry Pi Pico y ESP32 CDC.
- Binarios nativos ARM64 optimizados con alineación de páginas de 16 KB para Android 15.

### 🇺🇸 English (`en-US`)
- Added flashrom v2.0 support: enabled Serprog, Bus Pirate, SPIDriver, CH347, and ST-LINK V3.
- Fixed USB interface conflict: clean isolation between PTY serial bridge and direct libusb drivers.
- Dynamic product disambiguation for multi-purpose FTDI-based programmer hardware.
- Expanded hardware support for Arduino Mega/Due/Leonardo, Raspberry Pi Pico, and ESP32 CDC.
- Optimized ARM64 native binaries with 16 KB memory page alignment for Android 15.

### 🇨🇳 简体中文 (`zh-CN`)
- 全面支持 flashrom v2.0：原生激活 Serprog、Bus Pirate、SPIDriver、CH347 和 ST-LINK V3。
- 修复 USB 冲突：在 PTY 串口桥接与 libusb 直连烧录器之间实现安全隔离。
- 针对 FTDI 芯片设备增加基于产品名称的动态智能识别。
- 新增支持 Arduino Mega/Due/Leonardo、Raspberry Pi Pico 与 ESP32 CDC 设备。
- 原生 ARM64 二进制库针对 Android 15 全面优化（16 KB 内存分页对齐）。

### 🇹🇼 繁體中文 (`zh-TW`)
- 全面支援 flashrom v2.0：原生啟用 Serprog、Bus Pirate、SPIDriver、CH347 與 ST-LINK V3。
- 修復 USB 衝突：在 PTY 序列埠橋接與 libusb 直連燒錄器之間實現乾淨隔離。
- 針對 FTDI 晶片裝置新增基於產品名稱的動態智慧辨識。
- 新增支援 Arduino Mega/Due/Leonardo、Raspberry Pi Pico 與 ESP32 CDC 裝置。
- 原生 ARM64 二進位程式庫針對 Android 15 全面最佳化（16 KB 記憶體分頁對齊）。

---

## 📦 Artefactos de la Versión

• **APK Release:** `Flash-SPI-Tool-v1.9.11-release.apk`  
• **AAB Release (Google Play Bundle):** `Flash-SPI-Tool-v1.9.11-release.aab`  
• **Arquitectura:** `arm64-v8a` (alineación estricta de 16 KB / Android 15)  
• **Firma Digital:** Certificado oficial RSA 2048-bit (`firma_flash_spi_tool.jks`), válido hasta 2046.

---

## 🔗 Enlaces

• **Google Play:** https://play.google.com/store/apps/details?id=com.diamon.curso  
• **Repositorio:** https://github.com/Danielk10/Flash-EEPROM-Tool  
• **Lanzamiento v1.9.11:** https://github.com/Danielk10/Flash-EEPROM-Tool/releases/tag/v1.9.11  
