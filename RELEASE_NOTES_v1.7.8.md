# Notas de Lanzamiento - Flash-SPI-Tool v1.7.8

Esta versión de producción (`v1.7.8`, código de versión `70`) optimiza y robustece la detección y gestión de permisos USB para el programador **CH341A (SPI/I2C)** y demás herramientas compatibles en dispositivos Android modernos (Android 14 y 15 / One UI), incorporando soporte nativo para eventos de conexión y desconexión en caliente sin interferir con la selección manual del usuario.

## 🚀 Nuevas Características y Mejoras

*   **Identificación Completa de Hardware en `device_filter.xml`:**
    *   Se agregó el PID nativo **`0x5512`** (`21778`) correspondiente al modo **Programador SPI/I2C de CH341A**, permitiendo que Android reconozca el hardware de inmediato al ser conectado por OTG.
    *   Se incorporaron PIDs adicionales para CH347 SPI Modo 3 (`0x55DB`), ST-LINK v2/v3, Dediprog SF100/SF600, SEGGER J-Link, PICkit 2, Altera USB-Blaster, Digilent y DirtyJTAG.
*   **Enlace Inmediato y Solicitud de Permisos (`ACTION_USB_DEVICE_ATTACHED`):**
    *   La aplicación ahora procesa eventos de conexión de dispositivos USB tanto en el arranque (`onCreate`) como cuando la app ya se encuentra en ejecución (`onNewIntent`).
    *   Al conectar el dispositivo por OTG, la app solicita los permisos del sistema automáticamente para que el token y descriptor USB (`fd`) queden listos.
*   **Desconexión Física Limpia (`ACTION_USB_DEVICE_DETACHED`):**
    *   Se añadió un receptor para desconexiones físicas en caliente. Al retirar el cable USB u OTG, la app cierra el descriptor de conexión de forma segura y restaura el estado de los controles de la interfaz de usuario.
*   **Respeto Estricto de la Selección Manual del Usuario:**
    *   La conexión de un dispositivo USB no sobreescribe las preferencias guardadas por el usuario en *Ajustes de Programador*.
    *   El botón **"Detectar"** de la interfaz mantiene su función de análisis activo y auto-configuración del programador específico cuando el usuario lo solicita explícitamente.

## 🛠️ Validación y Pruebas Locales

*   **Validación 100% Exitosa en Suite de Emulación:**
    *   Verificación de los cuatro modos de emulación local (`test_all_devices.sh`): **CH341A** (USB socket directo), **Serprog** (PTY), **Bus Pirate** (PTY) y **SPIDriver** (PTY) frente a memoria virtual SPI GD25Q80(B) de 1 MB.
*   **Pruebas Unitarias de Regresión:**
    *   Superadas todas las pruebas unitarias de mapeo VID:PID y compatibilidad de arquitectura.
