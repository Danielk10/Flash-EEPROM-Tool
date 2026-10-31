# Flash SPI Tool v1.9.11 (versionCode 97) — Soporte FTDI robusto, parche libusb ioctl y binarios actualizados / Robust FTDI support, libusb ioctl patch & updated binaries

Esta versión soluciona de forma definitiva los errores de inicialización en programadores FTDI (`-3: device not found` y `-10: libusb_get_config_descriptor() failed`), incorpora un parche quirúrgico en `libusb` para el paso de descriptores de configuración mediante `ioctl` sin requerir root en Android, sincroniza la suite completa de binarios y dependencias nativas ARM64 (`jniLibs/arm64-v8a`), amplía la compatibilidad USB con NVIDIA SMA y optimiza los filtros para ST-LINK V3 y Google Servo.

This release definitively resolves initialization errors on FTDI programmers (`-3: device not found` and `-10: libusb_get_config_descriptor() failed`), implements a surgical `libusb` patch for configuration descriptor handling via `ioctl` without root on Android, synchronizes the complete ARM64 native binaries and dependencies suite (`jniLibs/arm64-v8a`), adds USB support for NVIDIA SMA, and refines filter mappings for ST-LINK V3 and Google Servo.

---

## 🚀 Novedades y Mejoras Técnicas

### 1. Resolución integral del soporte para programadores FTDI (FT232H, FT2232H, FT4232H)
• **Corrección del error `-3 (device not found)`:**  
  Se actualizó el mapa automático `USB_AUTO_MAP` en `UsbController.java` para asociar cada VID:PID FTDI con su parámetro de silicio correspondiente (`ft2232_spi:type=232H`, `type=2232H`, `type=4232H`, etc.). Esto evita que flashrom recurra al tipo por defecto y falle al abrir el dispositivo.
• **Corrección del error `-10 (libusb_get_config_descriptor() failed)`:**  
  En Android sin root, el acceso a `/dev/bus/usb/` y `/sys/bus/usb/` está restringido por SELinux. Cuando `libftdi` intentaba consultar el descriptor de configuración para validar interfaces y endpoints, el backend estándar de `libusb` fallaba al no poder leer el filesystem.
  - **Interceptación en `descriptor.c`:** Al detectar el descriptor de archivo inyectado (`ANDROID_USB_FD`), `libusb_get_config_descriptor()` realiza una transferencia de control directa mediante `ioctl(fd, USBDEVFS_CONTROL)` con valor `0x0200` (Configuration Descriptor 0), obteniendo la longitud total (`wTotalLength`) y analizando la jerarquía completa de endpoints en memoria con `raw_desc_to_config()`.
  - **Interceptación en `core.c`:** `libusb_get_configuration()` retorna la configuración activa 1, previniendo llamadas conflictivas a `libusb_set_configuration()` que provocan errores `EBUSY` en interfaces ya asignadas en Android.

### 2. Sincronización completa de binarios nativos ARM64 (`jniLibs/arm64-v8a`)
• **5 Binarios ejecutables (cumplimiento Google Play):**  
  `libflashrom_bin.so` (830 KB), `libftdi_eeprom.so` (76.5 KB), `liblspci.so` (94.8 KB), `libpcilmr.so` (29.2 KB), `libsetpci.so` (24 KB).
• **19 Librerías compartidas actualizadas:**  
  - `libusb_1_0.so` (137 KB) con el interceptor de configuración y dispositivo.
  - `libflashrom.so`, `libflashrom_1.so`, `libflashrom_1_0_0.so` (794 KB).
  - `libftdi1.so`, `libftdipp1.so`, `libjaylink.so`, `libpci.so`, `libpyftdi1.so`.
  - Dependencias de sistema y compilador actualizadas: `libc++_shared.so` (1.42 MB), `libcrypto_3.so` (OpenSSL 3, 5.21 MB), `libconfuse.so` (64 KB) y `libz_1.so` (72 KB).
• **Cabeceras C/C++ actualizadas (`app/src/main/cpp/include`):**  
  Sincronización de `libjaylink` (v0.5.0 con soporte VCOM), opciones de logging en `libusb-1.0`, y alineación PCI en `pci/pciutils`.
• **Assets y scripts de entorno:**  
  Actualización de `ftdi1.py` a SWIG 4.5.1, autocompletado `flashrom.bash` y manuales man correspondientes.

### 3. Ampliación de Hardware USB Compatible
• **NVIDIA SMA USB-to-SPI:** Integración de VID:PID `0955:cf11` mapeado al programador `nv_sma_spi`.
• **ST-LINK V3:** Filtrado refinado para soportar únicamente modelos con puente SPI (`0x374f`, `0x3753`, `0x3754`), excluyendo variantes no compatibles (V3E `0x374e` y V2).
• **Google Servo / Raiden:** Detección dinámica de interfaces vendor-specific `0xFF/0x51` sin dependencia de PIDs fijos.

---

## ✅ Validación de esta versión

• **Validación con Emulador de Hardware:**  
  Ejecución completa de `test_all_devices.sh` cubriendo los 5 perfiles de conexión:
  - **SERPROG (PTY):** ✅ Detección y lectura verificada.
  - **BUS PIRATE (PTY):** ✅ Detección y lectura verificada.
  - **SPIDRIVER (PTY):** ✅ Detección y lectura verificada.
  - **CH341A (USB Directo / ANDROID_USB_FD):** ✅ Detección y lectura verificada.
  - **FTDI FT232H (USB Directo / ANDROID_USB_FD):** ✅ Detección de chip flash GigaDevice `GD25Q80(B)` (1024 kB, SPI) y volcado íntegro de 1 MB sin fallos ni timeouts.
• **Pruebas Unitarias:** Pruebas de mapeo automático de programadores y filtros USB validadas exitosamente (`ExampleUnitTest.java`).
• **Portabilidad y Seguridad:** Verificación de binarios ELF con `patchelf --remove-rpath` y eliminación de dependencias absolutas a Termux.

---

## 🌐 Notas de Versión para Google Play

### 🇪🇸 Español (`es-419` / `es-ES`)
- Soporte FTDI robusto: corrección de errores -3 y -10 mediante interceptación ioctl de descriptores USB sin root.
- Actualización de binarios nativos arm64-v8a (flashrom, libusb, libftdi, libcrypto).
- Soporte para adaptador USB-to-SPI NVIDIA SMA (0955:cf11).
- Filtros optimizados para ST-LINK V3 y Google Servo/Raiden.
- Mapeo automático de tipo de chip FTDI (:type=232H, :type=2232H).

### 🇺🇸 English (`en-US`)
- Robust FTDI support: fixes for errors -3 and -10 via rootless USB descriptor ioctl interception.
- Updated arm64-v8a native binaries (flashrom, libusb, libftdi, libcrypto).
- Added support for NVIDIA SMA USB-to-SPI adapter (0955:cf11).
- Optimized USB filters for ST-LINK V3 and Google Servo/Raiden.
- Automatic FTDI chip type mapping (:type=232H, :type=2232H).

### 🇨🇳 简体中文 (`zh-CN`)
- 稳健的 FTDI 支持：通过底层 ioctl 拦截读取 USB 配置描述符，彻底修复 -3 与 -10 错误（免 Root）。
- 更新 arm64-v8a 原生库（flashrom、libusb、libftdi、libcrypto）。
- 新增 NVIDIA SMA USB 转 SPI 适配器支持（0955:cf11）。
- 优化 ST-LINK V3 和 Google Servo/Raiden 过滤器。
- 自动匹配 FTDI 芯片类型参数（:type=232H、:type=2232H）。

### 🇹🇼 繁體中文 (`zh-TW`)
- 穩健的 FTDI 支援：透過底層 ioctl 攔截讀取 USB 設定描述元，徹底修復 -3 與 -10 錯誤（免 Root）。
- 更新 arm64-v8a 原生函式庫（flashrom、libusb、libftdi、libcrypto）。
- 新增 NVIDIA SMA USB 轉 SPI 介面卡支援（0955:cf11）。
- 最佳化 ST-LINK V3 和 Google Servo/Raiden 過濾器。
- 自動對應 FTDI 晶片類型參數（:type=232H、:type=2232H）。

---

## 📦 Artefactos de la Versión

• **APK Release:** `Flash-SPI-Tool-v1.9.11-release.apk` (19.5 MiB / 20,455,216 bytes)  
  **SHA-256:** `e4f9bfa18309d6b4929152199457154b8934827b9ab50ab6d876f8c81da52996`
• **AAB Release (Google Play Bundle):** `Flash-SPI-Tool-v1.9.11-release.aab` (18.5 MiB / 19,394,641 bytes)  
  **SHA-256:** `10a84b362538d13300d1b01d272f4216332ba655288513d7cb41adcf0d4aad9e`
• **Arquitectura:** `arm64-v8a` (alineación de página de memoria de 16 KB)
• **Firma Digital:** Certificado oficial RSA 2048-bit (`firma_flash_spi_tool.jks`), válido hasta 2046.

---

## 🔗 Enlaces

• **Google Play:** https://play.google.com/store/apps/details?id=com.diamon.curso
• **Repositorio:** https://github.com/Danielk10/Flash-EEPROM-Tool
• **Lanzamiento v1.9.11:** https://github.com/Danielk10/Flash-EEPROM-Tool/releases/tag/v1.9.11
