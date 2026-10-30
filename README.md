# Flash SPI Tool v1.9.9

[![Release](https://img.shields.io/badge/Release-v1.9.9%20(Build%2095)-007ACC?logo=github&logoColor=white)](https://github.com/danielk10/Flash-EEPROM-Tool)
[![Android](https://img.shields.io/badge/Android-7.0%2B%20(API%2024%20a%20API%2037)-3DDC84?logo=android&logoColor=white)](https://developer.android.com/)
[![minSdk](https://img.shields.io/badge/minSdk-24%20(Nougat)-brightgreen?logo=android)](https://developer.android.com/)
[![Architecture](https://img.shields.io/badge/Architecture-Pro%20Suite%20%7C%20Zero--OOM-FF6F00?logo=materialdesignicons&logoColor=white)](./RELEASE_NOTES_v1.9.9.md)
[![ABI](https://img.shields.io/badge/ABI-arm64--v8a%20(16KB%20Aligned)-0091EA?logo=arm&logoColor=white)](https://developer.android.com/ndk/guides/abis)
[![NDK](https://img.shields.io/badge/NDK-r30--rc1-4CAF50?logo=android&logoColor=white)](https://developer.android.com/ndk)
[![AGP](https://img.shields.io/badge/AGP-9.4.1-blue?logo=android)](https://developer.android.com/studio/releases/gradle-plugin)
[![Flashrom](https://img.shields.io/badge/flashrom-integrado%20(Custom%20Engine)-orange)](https://github.com/flashrom/flashrom)
[![Build](https://img.shields.io/badge/Build-Passing-success?logo=githubactions&logoColor=white)](#)
[![Licencia](https://img.shields.io/badge/Licencia-GPLv3-blue.svg)](./LICENSE.txt)
[![Buy Me A Coffee](https://img.shields.io/badge/Buy%20Me%20a%20Coffee-ffdd00?style=flat-square&logo=buy-me-a-coffee&logoColor=black)](https://buymeacoffee.com/danielpdiai)
[![Ko-fi](https://img.shields.io/badge/Ko--fi-F16061?style=flat-square&logo=ko-fi&logoColor=white)](https://ko-fi.com/danieleliasdiamonvazquez)

---

## 📌 Descripción del Proyecto

**Flash SPI Tool** es la solución profesional para Android de lectura, escritura, borrado y verificación de memorias **Flash SPI, LPC/FWH y EEPROM** a través de **USB OTG sin requerir permisos de superusuario (root)**.

Diseñada tanto para ingenieros de hardware y laboratorios de reparación técnica como para entusiastas de la electrónica, la suite integra el poder del motor nativo de **flashrom** y una capa de transporte de alto rendimiento en C++ (`usb_bridge.cpp`) con una interfaz gráfica moderna, segura y optimizada para dispositivos táctiles.

> **Información de la Versión Actual:**
> - **Versión de la App:** `v1.9.9`
> - **Código de Versión (`versionCode`):** `95`
> - **Rango de Compatibilidad:** Android 7.0 Nougat (API 24) hasta Android 17 (API 37)
> - **Arquitectura de Procesador:** ARM64 (`arm64-v8a`) con alineación de páginas de memoria de 16KB para compatibilidad total con Android 15+.
> - **Seguridad:** Operación 100% en espacio de usuario mediante Android USB Host API; no requiere root.

---

## 🔌 Programadores Compatibles y Conexión de Hardware

Flash SPI Tool implementa una arquitectura de comunicación dual de alta fidelidad que admite los programadores de silicio más populares del mercado sin necesidad de controladores propietarios en el sistema operativo del teléfono:

| Programador | Modo de Conexión | Identificador USB (VID:PID) | Descripción Técnica y Características |
|---|---|---|---|
| **CH341A / CH347T / CH347F** | TCP Bridge Socket / USB Directo | `1a86:5512` (CH341A)<br>`1a86:5523`<br>`1a86:55db` (CH347T)<br>`1a86:55de` (CH347F) | Programadores SPI/I2C de bajo costo. CH347T/F aceptan la frecuencia SPI seleccionable en la app (`spispeed=15M/30M/60M`). Usa un bridge nativo con sockets TCP locales y descriptores `libusb` inyectados vía `ANDROID_USB_FD` con herencia despojada de `O_CLOEXEC`. |
| **Serprog (RP2040, Arduino, STM32)** | Pseudo-Terminal PTY / Socket TCP | `2341:0043` / `0001` (Arduino)<br>`1a86:7523` (CH340)<br>`10c4:ea60` (CP2102)<br>`067b:2303` (PL2303) | Protocolo serie estándar configurado de forma fija y transparente a **115.200 baudios**. Handshake solicitado `HELLO` (versionado) con fallback `NOP/ACK`, compatible con el firmware Arduino oficial (`frser-arduino`), Raspberry Pi Pico, STM32 y el firmware custom. El forwarding es transparente: no escanea los datos SPI en busca de beacons, por lo que `0xAA 0x55` en una lectura nunca se confunde con control. |
| **Bus Pirate (v3 / v4)** | PTY Serial Bridge | `0403:6001` (FTDI) | Dispositivo multiprotocolo. Conmutación automática a modo binario `BBIO` y control directo de líneas SPI mediante pseudo-terminales dedicados. |
| **SPIDriver** | PTY Serial Bridge | `0403:6015` / FTDI Serial | Herramienta de desarrollo SPI a 460.800 baudios con soporte de protocolo binario/ASCII, validación de eco `e` y control granular de Chip Select. |
| **Programadores USB Directos Adicionales** | libusb Nativa Parcheada | FTDI FT2232H/FT4232H/FT232H/FT4233H, TIAO TUMPA, Olimex ARM-USB, GOEPEL PicoTAP, ST-Link v3, Segger J-Link (cualquier PID), Dediprog SF100/SF600, Pickit2, USB-Blaster | Operación directa mediante invocación nativa y bypass de permisos de kernel. |

```
                               ┌─────────────────────────┐
                               │   Android USB Host API  │
                               │  (Permiso OTG Aprobado) │
                               └────────────┬────────────┘
                                            │ UsbDeviceConnection
                                            ▼
                               ┌─────────────────────────┐
                               │    USB_AUTO_MAP VID:PID │
                               └────────────┬────────────┘
                                            │
                     ┌──────────────────────┴──────────────────────┐
                     │                                             │
          [ Camino A: Serial PTY / TCP ]                [ Camino B: USB Directo ]
          serprog, buspirate_spi, spidriver             ch341a_spi, ch347, ft2232, etc.
                     │                                             │
          ┌──────────┴──────────┐                       ┌──────────┴──────────┐
          │   PtyBridge.java    │                       │   dup() + fcntl()   │
          │  Motor usb_bridge   │                       │  ANDROID_USB_FD=fd  │
          │  TCP /dev/pts/N     │                       │  libusb parcheada   │
          └──────────┬──────────┘                       └──────────┬──────────┘
                     │                                             │
                     └──────────────────────┬──────────────────────┘
                                            ▼
                               ┌─────────────────────────┐
                               │   flashrom Native Core  │
                               │    (GPLv3 - ARM64)      │
                               └────────────┬────────────┘
                                            ▼
                               ┌─────────────────────────┐
                               │    Memoria Flash SPI    │
                               │  (SOIC-8, DIP-8, WSON)  │
                               └─────────────────────────┘
```

---

## 🆓 Características Gratuitas (100% Libres y Sin Restricciones)

Todas las funciones indispensables para el trabajo con chips flash y recuperación de placas son y seguirán siendo **completamente gratuitas y sin límites de uso**:

- 🔍 **Detección Automática y Sondeo (Probe)**: Identificación exhaustiva de fabricantes y modelos JEDEC mediante comandos `RDID` (`0x9F`), `REMS` (`0x90`) y `RES` (`0xAB`).
- 📥 **Lectura Completa (Read / Dump)**: Volcado íntegro de la memoria SPI hacia el almacenamiento local o hacia una ruta elegida mediante Android Storage Access Framework (SAF).
- ⚡ **Escritura y Flasheo Seguro (Write / Flash)**: Programación de bloques de memoria con verificación rigurosa byte a byte (`-v`) para certificar que el contenido grabado coincide exactamente con el archivo de origen.
- 🧹 **Borrado Completo de Chip (`--erase`)**: Limpieza de todos los sectores del silicio a estado inicial (`0xFF`) con confirmación de seguridad previa para prevenir borrados accidentales.
- 🔓 **Desbloqueo de Registros de Protección de Estado SPI**: Desactivación con un solo clic de los bits de protección contra escritura (`--wp-disable` en registros SR1, SR2 y SR3).
- 🚀 **Activación y Verificación de Quad Enable (Bit QE)**: Habilitación y consulta del bit QE en el registro SR2 para memorias Quad-SPI de alta velocidad requeridas por BIOS y GPUs modernas.
- 📐 **Guía Interactiva de Pinouts de Hardware**: Diagramas visuales en pantalla con asignación de pines y jumpers para encapsulados **DIP-8**, **SOIC-8** y **WSON-8**, así como diagramas para CH341A, Arduino Serprog, Bus Pirate y SPIDriver.
- 🎛️ **Consola de Comandos flashrom**: Terminal interactiva integrada con soporte para cualquier parámetro avanzado de flashrom (`flashrom -p ...`).
- 🧪 **Modo Dummy de Pruebas**: Simulación de chips virtuales (ej. `W25Q128FV`, `MX25L6436`, `SST25VF040`) para pruebas de software sin necesidad de conectar hardware real.

---

## 💎 Flash SPI Tool Pro Suite (Monetización Ética & Híbrida)

Para respaldar el mantenimiento continuo del proyecto sin comprometer las operaciones básicas de silicio, la versión 1.9.2 incluye la suite de ingeniería avanzada **Flash SPI Tool PRO** con un esquema de monetización justo, transparente y accesible:

### Esquema de Acceso Flexible y Justo
1. **Pase Pro Gratuito de 12 Horas con Nube Firebase**: Cualquier usuario puede desbloquear la suite Pro completa de forma 100% gratuita durante 12 horas continuas con solo visualizar un anuncio de video bonificado (Rewarded Video de Google AdMob). El pase cuenta con **persistencia multinivel en Firebase Firestore (colección `pro_passes`)**, respaldo local firmado con SHA-256 (`.flash_spi_pro_pass.dat`) y Google Cloud Backup para que no se pierda al reinstalar la app o limpiar datos. Puede renovarse indefinidamente.
2. **Desbloqueo Permanente In-App ($4.99 USD)**: Compra única de por vida mediante Google Play Billing (`donacion_pizza` / `remove_ads_pro`) con reconocimiento permanente de licencia (`acknowledgePurchase`) para técnicos profesionales.
3. **Sin anuncios de por vida**: Tanto con el Pase de 12 horas como con la compra de por vida, se suprimen en su totalidad los banners e intersticiales publicitarios.

### Herramientas Avanzadas de la Suite Pro
- 🛡️ **Bóveda de Seguridad y Respaldo Pre-Flash Antibrick**:
  - **Snapshot Automático Pre-Flash**: Respaldo instantáneo del chip con hash SHA-256 en la bóveda de seguridad antes de cualquier operación destructiva de escritura (`-w`) o borrado (`--erase`).
  - **Rollback de Emergencia en 1 Clic**: Restauración instantánea del último volcado funcional garantizado en caso de interrupción de energía o corrupción de firmware.
- 🔐 **Verificador Criptográfico de Integridad MD5, SHA-256 y CRC32 (Streaming Zero-OOM)**:
  - **Acceso Directo desde el Menú Principal**: Opción dedicada **"Verificar Integridad MD5 / SHA-256"** para auditar y certificar el volcado cargado en cualquier instante.
  - **Cálculo multialgoritmo en streaming**: Generación en streaming (MD5, SHA-1, SHA-256 y CRC32) sobre canales NIO para archivos de hasta 64 MB o más sin sobrecargar la memoria RAM del teléfono.
  - **Cotejo inteligente con portapapeles**: Comparación automática en tiempo real contra hashes del portapapeles con confirmación visual de coincidencia.
  - **Copia instantánea y Sidecars GNU**: Botones rápidos para copiar hashes MD5 o SHA-256 al portapapeles con un solo toque y exportación de archivos sidecar estándar GNU (`.sha256` y `.md5`) compatibles con la utilidad de consola `sha256sum -c`.
- ✏️ **Editor Hexadecimal en Vivo con Parcheo In-Place**:
  - Inspección celda a celda con diálogo interactivo en múltiples bases (Hexadecimal, Decimal, Binario, ASCII y selección de Endianness).
  - Resaltado visual ámbar (`#FFB300`) para identificar de inmediato todos los bytes alterados.
  - Pila completa de **Deshacer/Rehacer (Undo/Redo)** basada en transacciones atómicas (`PatchTransaction`).
  - Capacidad de **Guardar y Flashear** directamente el archivo modificado a la memoria en un solo paso.
- 🌐 **Inyector MAC Intel GbE LAN con Checksum NVM 0xBABA**:
  - Detección automática de la región LAN de Intel en BIOS de placas base.
  - Asistente para reemplazar la dirección MAC física (EUI-48).
  - **Recálculo criptográfico automático de la suma de comprobación NVM `0xBABA`** para prevenir errores de tarjeta de red no operativa (`PXE-E05`).
- 🔑 **Extractor de Clave Windows OEM desde Tablas MSDM ACPI**:
  - Escaneo automático de tablas ACPI en el firmware de la BIOS.
  - Decodificación del algoritmo Base24 para recuperar la clave de producto original de 25 caracteres de Windows instalada de fábrica.
- 🏷️ **Modificador de Números de Serie y UUID SMBIOS y DMI**:
  - Búsqueda y reemplazo quirúrgico de cadenas de número de serie y UUID GUID de 16 bytes (en formato little-endian SMBIOS o RFC-4122) para clonación o restauración de tarjetas madre.
- 🧩 **Analizador de Particiones Intel ME y CSME**:
  - Decodificación profunda de la tabla de particiones `$FPT` del Intel Management Engine.
  - Verificación de versiones, integridad y estado de los módulos ME.
- 🔓 **Desbloqueo de Registros SPI SR1, SR2 y SR3 en 1 Clic**:
  - Desactivación con un solo clic de los bits de protección contra escritura de hardware (`--wp-disable`) en registros de estado.
- ⚡ **Flasheo Delta Selectivo de Sectores de 4KB de Alta Velocidad**:
  - Reprogramación optimizada que actualiza únicamente los bloques con cambios detectados, minimizando tiempos de escritura y desgaste del silicio.
- 🔥 **Comparador Hex Diff con Detección Estricta y Mapa de Calor**:
  - **Detección Estricta en Tamaños Diferentes**: Identificación rigurosa byte a byte al comparar volcados de diferente tamaño (ej. 16 MB frente a 1 MB relleno de `0xFF`), descartando falsos positivos de archivos idénticos y señalando bytes excedentes con notación explícita (`XX→--` / `--→XX`).
  - **Cotejo Side-by-Side de Hashes MD5**: Cálculo simultáneo y visualización en paralelo de sumas de verificación MD5 y tamaños exactos en bytes de ambos archivos seleccionados.
  - **Cinta de Calor Visual (`DiffHeatmapView`)**: Mapeo dinámico por sectores de 4 KB con navegación instantánea hacia las diferencias exactas de firmware (`A → B`).

---

## 📱 Requisitos del Sistema

- **Dispositivo Móvil:** Teléfono o tablet Android con puerto USB Type-C o Micro-USB y soporte para **USB Host / OTG**.
- **Versión de Android:** Android 7.0 Nougat (**API 24**) o superior (probado exhaustivamente hasta Android 17 / API 37).
- **Permisos Root:** **No requeridos**. La aplicación se ejecuta de forma completamente estándar aprovechando el sistema de permisos de Android `UsbManager` y descriptores de archivo pasados a capas nativas.
- **Accesorios Recomendados:**
  - Cable adaptador USB OTG de buena calidad.
  - Pinza clip de prueba SOIC-8 (Pomona 5250 o compatible) o socket ZIF DIP-8.
  - Adaptador de nivel de voltaje de 1.8V para memorias de bajo voltaje (ej. Winbond 25Q64FW/JW).

---

## 🏗️ Arquitectura del Sistema y Funcionamiento Interno

### 1. Manejo Seguro de Permisos y Descriptores USB en Android
Android protege el acceso directo a los nodos de `/dev/bus/usb/` mediante permisos de Linux en el sandbox de cada aplicación. Flash SPI Tool supera esta limitación técnica mediante dos estrategias de puente nativo:

- **Estrategia para Programadores Seriales (Camino A):** La app crea un par de pseudo-terminales maestro/esclavo (`/dev/pts/N`) mediante llamadas JNI (`openpty`) blindadas con la técnica del **Dummy Slave FD** para evitar la reversión a modo cooked del kernel. El puerto serie físico es transferido bidireccionalmente por un motor nativo C++ multihilo (`usb_bridge.cpp`) con prioridad `-8` y llamadas directas `ioctl(USBDEVFS_BULK)` o mediante un **Socket TCP local (`127.0.0.1`)** de latencia ultra baja.  
  👉 *Documentación completa:* Consulta la [Guía Técnica del Parche PTY USB](./GUIA_TECNICA_PARCHE_PTY_USB.md).

- **Estrategia para Programadores USB Directos (Camino B):** La aplicación solicita autorización formal al usuario mediante `UsbManager.requestPermission()`. Tras la confirmación, obtiene el descriptor de archivo de la conexión con `connection.getFileDescriptor()`. Debido a que Android activa por defecto el flag `O_CLOEXEC` en dicho descriptor, una rutina JNI ejecuta `dup()` y remueve el flag con `fcntl(newFd, F_SETFD, flags & ~FD_CLOEXEC)`, inyectando el nuevo descriptor en el entorno con `ANDROID_USB_FD`. Para eludir el cierre sistemático de descriptores de archivo en Android 10+ (`ProcessBuilder`), el proceso se bifurca en C++ (`fork()` + `execv()`). Una versión modificada de `libusb` intercepta este descriptor con `usbi_alloc_device()` y `libusb_wrap_sys_device()`.  
  👉 *Documentación completa:* Consulta la [Guía Técnica del Parche Libusb en Android](./GUIA_TECNICA_PARCHE_LIBUSB_ANDROID.md).

### 2. Rutas del Sistema y Runtime
Para garantizar que las librerías dinámicas y binarios nativos resuelvan sus dependencias sin errores de reubicación, la aplicación crea enlaces simbólicos en tiempo de ejecución:

- **Ruta de compilación canónica:** `/data/data/com.diamon.curso/files/usr`
- **Ruta en tiempo de ejecución:** `/data/user/0/com.diamon.curso/files/usr`
- **Binarios nativos:** Empaquetados en `jniLibs/arm64-v8a` con nombres terminados en `.so` para satisfacer las políticas de empaquetado del instalador de paquetes de Android, compilados con alineación de páginas de 16KB (`-Wl,-z,max-page-size=16384`) para Android 15+.

---

## ⚙️ Compilación del Proyecto y Configuración del SDK

Para compilar el proyecto se debe seguir la directriz oficial establecida en [`GEMINI.md`](./GEMINI.md):

### 1. Instalación Automática del SDK de Android
El SDK, NDK y CMake se instalan en el directorio `/tmp/android-sdk` ejecutando el script provisto:
```bash
bash setup-sdk.sh
```

### 2. Compilación del APK
Una vez configurado el SDK, compila la aplicación utilizando el Gradle Wrapper del proyecto:

- **Compilación de depuración (Debug):**
  ```bash
  ./gradlew assembleDebug
  ```
- **Compilación de producción (Release):**
  ```bash
  ./gradlew assembleRelease
  ```

### 3. Ubicación del APK Generado
Para mantener el árbol de trabajo limpio, Gradle redirige todos los artefactos de salida hacia `/tmp/`:
```text
/tmp/flashrom/outputs/apk/debug/app-debug.apk
/tmp/flashrom/outputs/apk/release/app-release.apk
```

---

## 🧪 Pruebas Locales con el Emulador Flashrom (`emulador_flashrom`)

El repositorio incluye un **emulador local de silicio y hardware multiprotocolo** desarrollado en C++ dentro del directorio [`emulador_flashrom/`](./emulador_flashrom/). Este emulador simula un chip **GigaDevice GD25Q80 (1 Megabyte)** con soporte completo de comandos SPI e implementa los puentes de comunicación de los cuatro programadores soportados para permitir pruebas completas sin periféricos conectados:

1. **Compilar el Emulador:**
   ```bash
   cd emulador_flashrom
   make clean && make
   ```
2. **Probar los 4 Modos de Emulación:**
   - **Emulador CH341A (Socket UNIX):**
     ```bash
     ./emulador_flashrom --ch341a ./flashrom_local.sh -p ch341a_spi -VVV
     ```
   - **Emulador Serprog (PTY Virtual):**
     ```bash
     ./emulador_flashrom --serprog &
     ./flashrom_local.sh -p serprog:dev=./serprog_pty:115200 -r dump.bin
     kill %1
     ```
   - **Emulador Bus Pirate (PTY Virtual):**
     ```bash
     ./emulador_flashrom --buspirate &
     ./flashrom_local.sh -p buspirate_spi:dev=./buspirate_pty -r dump.bin
     kill %1
     ```
   - **Emulador SPIDriver (PTY Virtual):**
     ```bash
     ./emulador_flashrom --spidriver &
     ./flashrom_local.sh -p spidriver:dev=./spidriver_pty -r dump.bin
     kill %1
     ```

Para un desglose paso a paso de las opciones de emulación y validación de protocolos, consulta la [Guía de Uso del Emulador](file:///home/danielpdiamon/Flash-EEPROM-Tool/GUIA_USO_EMULADOR.md).

---

## 📑 Diagramas de Conexión y Pinouts de Referencia

La aplicación integra diagramas interactivos accesibles desde el menú principal para los encapsulados y programadores más habituales:

### Encapsulados Flash SPI Típicos (SOIC-8 / DIP-8 / WSON-8)
```
          ┌───┬───┐
   /CS ───┤ 1   8 ├─── VCC (3.3V / 1.8V)
  MISO ───┤ 2   7 ├─── /HOLD
   /WP ───┤ 3   6 ├─── SCK
   GND ───┤ 4   5 ├─── MOSI
          └───┴───┘
```

> **¡ADVERTENCIA IMPORTANTE DE VOLTAJE!**
> La gran mayoría de memorias modernas de la serie 25Q operan a **3.3V** o **1.8V**. Muchos programadores CH341A comerciales genéricos ("Black Edition") tienen una falla de diseño de fábrica que alimenta las líneas de datos SPI con 5V a pesar del jumper de 3.3V. Utiliza siempre un multímetro para verificar el voltaje en el pin 8 (VCC) o conecta un adaptador de nivel para evitar daños permanentes en el chip de memoria.

---

## 📚 Índice de Documentación Técnica y Arquitectura

Para facilitar la auditoría, mantenimiento y extensión del proyecto, se detallan a continuación las guías especializadas disponibles en el repositorio:

| Documento | Temática Principal | Audiencia y Utilidad |
|---|---|---|
| [**Guía Técnica del Parche PTY USB**](./GUIA_TECNICA_PARCHE_PTY_USB.md) | Análisis del bug de Line Discipline en terminales Linux, solución con **Dummy Slave FD**, prevención de overrun en CH340 y arquitectura del motor en C++ multihilo (`usb_bridge.cpp`). | Desarrolladores de firmware, programadores seriales (Serprog, Bus Pirate, SPIDriver). |
| [**Guía Técnica del Parche Libusb en Android**](./GUIA_TECNICA_PARCHE_LIBUSB_ANDROID.md) | Bypass de restricciones SELinux en Android sin root, inyección `ANDROID_USB_FD`, lecturas atómicas de descriptor con `ioctl` y preservación de FDs en Android 10+ vía JNI `fork()`/`execv()`. | Ingenieros de bajo nivel trabajando con `libusb` y herramientas de silicio nativas. |
| [**Especificación de Optimización de Velocidad USB**](./PROPUESTAS_OPTIMIZACION_USB_VELOCIDAD.md) | Especificación del transporte dual Socket TCP Loopback (`127.0.0.1`) vs PTY, eliminación del Garbage Collector del flujo en tiempo real y memoria `mmap` Zero-Copy. | Optimización de rendimiento y benchmarks. |
| [**Reporte Solución PTY Serprog / Bus Pirate**](./REPORTE_SOLUCION_PTY_SERPROG_BUSPIRATE_SPIDRIVER.md) | Informe histórico y técnico de resolución de corrupción de bytes `0x11` y `0x13` (`S_CMD_O_SPIOP`). | Diagnóstico de comunicaciones serie. |
| [**Reporte Parche Libusb Android**](./REPORTE_PARCHE_LIBUSB_ANDROID.md) | Informe técnico del cambio de enfoque destructivo (`*ctx = NULL`) a quirúrgico (`core.c` + `usbi_alloc_device`). | Historial de portabilidad nativa. |
| [**Reporte Optimización PTY Asíncrono**](./REPORTE_OPTIMIZACION_PTY_USB_ASYNC.md) | Implementación de `poll()` JNI, colas `UsbRequest` y beacons de sincronización `0xAA 0x55`. | Mitigación de timeouts de hardware. |
| [**Guía de Uso del Emulador Flashrom**](./GUIA_USO_EMULADOR.md) | Manual interactivo para ejecutar la suite de emulación de hardware sin dispositivos físicos conectados. | Pruebas locales y CI/CD. |
| [**Guía de Configuración de Monetización**](./GUIA_CONFIGURACION_PLAY_CONSOLE_MONETIZACION.md) | Pasos de Google Play Console para el producto `donacion_pizza`, AdMob Rewarded Video y persistencia Firestore. | Administración y publicación en Play Store. |
| [**Notas de Lanzamiento v1.9.9**](./RELEASE_NOTES_v1.9.9.md) | Registro de cambios completo, hashes SHA-256 de binarios y notas bilingües de la versión 1.9.9 (Build 95). | Registro de versiones y auditoría de seguridad. |

---

## 📜 Licencia y Atribución

Este proyecto es software libre y de código abierto distribuido bajo la licencia **GNU General Public License v3.0**. Consulta el archivo [`LICENSE.txt`](./LICENSE.txt) para más información.

### Proyectos y Bibliotecas de Terceros
- **[flashrom](https://github.com/flashrom/flashrom)** — Licencia GPL-2.0+
- **[libusb](https://github.com/libusb/libusb)** — Licencia LGPL-2.1+
- **[usb-serial-for-android](https://github.com/mik3y/usb-serial-for-android)** — Licencia MIT
- **[pciutils](https://github.com/pciutils/pciutils)** — Licencia GPL-2.0+
- **[libftdi](https://developer.intra2net.com/git/libftdi)** — Licencia LGPL-2.1+
- **[libjaylink](https://gitlab.zapb.de/libjaylink/libjaylink)** — Licencia GPL-2.0+
- **[OpenSSL](https://www.openssl.org/)** — Licencia Apache 2.0

### Colaboradores y Agradecimientos Especiales
- **fsvip** — Pruebas de hardware y especificaciones para el soporte de CH347F / CH347.

---

## ☕ Apoyo y Donaciones

Si este proyecto te ha sido de utilidad y deseas apoyar el desarrollo continuo:

[![Buy Me A Coffee](https://img.buymeacoffee.com/button-api/?text=Cómprame%20un%20café&emoji=☕&slug=danielpdiai&button_colour=FFDD00&font_colour=000000&font_family=Cookie&outline_colour=000000&coffee_colour=ffffff)](https://buymeacoffee.com/danielpdiai)
[![Support me on Ko-fi](https://ko-fi.com/img/githubbutton_sm.svg)](https://ko-fi.com/danieleliasdiamonvazquez)
