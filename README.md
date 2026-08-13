# Flash EEPROM Tool

[![Android](https://img.shields.io/badge/Android-6.0%20(API%2023)%20a%20Android%2016%20(API%2037)-3DDC84?logo=android&logoColor=white)](https://developer.android.com/)
[![ABI](https://img.shields.io/badge/ABI-arm64--v8a-0091EA?logo=arm&logoColor=white)](https://developer.android.com/ndk/guides/abis)
[![NDK](https://img.shields.io/badge/NDK-r30--rc1-4CAF50?logo=android&logoColor=white)](https://developer.android.com/ndk)
[![AGP](https://img.shields.io/badge/AGP-9.2.1-blue?logo=android)](https://developer.android.com/studio/releases/gradle-plugin)
[![Flashrom](https://img.shields.io/badge/flashrom-integrado-orange)](https://github.com/flashrom/flashrom)
[![Licencia](https://img.shields.io/badge/Licencia-GPLv3-blue)](./LICENSE.txt)

Aplicación Android para lectura/escritura/verificación de memorias **SPI, LPC/FWH y MTD** usando **flashrom** y librerías nativas compiladas para **ARM64**.
Soporta programadores **USB directos** (CH341A, FT2232, Dediprog, etc.) vía libusb parcheada y programadores **seriales** (serprog/Arduino, Bus Pirate, SPIDriver) vía puente PTY↔USB.

> Nombre visible de la app: **Flash EEPROM Tool**.
> Versión actual: **1.7.1** (Código de versión: **64**).
> Rango de soporte Android: **API 23 a API 37** (Android 6.0 a Android 16+).

---

## 1) Objetivo del proyecto

Este proyecto empaqueta una cadena nativa completa (flashrom + dependencias) dentro de una app Android y la conecta con la capa Java/Kotlin para:

- Detectar programadores USB de forma práctica (no limitado a un único modelo).
- Solicitar permisos USB de Android correctamente.
- Reutilizar un **File Descriptor (FD)** autorizado por Android dentro de una `libusb` parcheada.
- Ejecutar binarios nativos en rutas de runtime equivalentes a las rutas de compilación.

---

## 2) Arquitectura general

La app tiene **dos caminos** de comunicación USB según el tipo de programador:

```
                                ┌─────────────────┐
                                │  USB_AUTO_MAP   │
                                │  (18 VID:PID)   │
                                └───────┬─────────┘
                                        │
                            ┌───────────┴───────────┐
                            │                       │
                Camino A: Serial (PTY)      Camino B: USB directo (libusb)
             [serprog, buspirate, spidriver]   [ch341a_spi, ft2232_spi, etc.]
                            │                       │
                  ┌─────────┴─────────┐   ┌────────┴────────┐
                  │  PtyBridge.java   │   │  ANDROID_USB_FD  │
                  │  DTR/RTS + Beacon │   │  → patch_libusb  │
                  │  USB↔PTY threads  │   │  → flashrom      │
                  └─────────┬─────────┘   └────────┬────────┘
                       /dev/pts/N              libusb nativo
                            │                       │
                        flashrom                flashrom
```

### Camino A — Programadores Seriales (serprog, buspirate_spi, spidriver)

- `PtyBridge.java` crea un pseudo-terminal (`/dev/pts/N`) con JNI.
- Abre el dispositivo USB-serial con `usb-serial-for-android`.
- Dos hilos Java envían datos en ambas direcciones: **PTY→USB** y **USB→PTY**.
- flashrom recibe `-p serprog:dev=/dev/pts/N:115200` (o `buspirate_spi:dev=...`).
- **No se usa** `ANDROID_USB_FD` — se remueve del entorno.
- Para serprog (Arduino): espera beacon `0xAA 0x55` antes de lanzar flashrom.
- Para buspirate/spidriver: activa DTR/RTS + purge sin beacon.

### Camino B — Programadores USB Directos (CH341A, FT2232, Dediprog, ST-Link, etc.)

- Java obtiene `fd = connection.getFileDescriptor()` del USB.
- Inyecta `ANDROID_USB_FD=fd` al proceso nativo (`ProcessBuilder`).
- `libusb` parcheada (`patch_libusb.py` / `build_libusb_custom.sh`) intercepta y usa ese FD.
- flashrom opera normalmente con `-p ch341a_spi`, `-p ft2232_spi`, etc.

### Capa nativa / runtime

- Binarios y `.so` en `app/src/main/jniLibs/arm64-v8a/`.
- Runtime operativo en `files/usr` del sandbox Android.
- Enlaces simbólicos (o copia fallback) para replicar rutas exactas esperadas por binarios y sonames.

---

## 3) Rutas exactas (clave para que funcione)

### Ruta base de compilación/documentación

```text
data/data/com.diamon.curso/files/usr
```

### En el proyecto (assets empaquetados)

```text
app/src/main/assets/data/data/com.diamon.curso/files/usr
```

### En tiempo de ejecución (teléfono)

```text
/data/user/0/com.diamon.curso/files/usr
```

El código copia assets de datos (share/include/pkgconfig, etc.) al runtime y crea enlaces para binarios/librerías desde `nativeLibraryDir` a `files/usr/*`, manteniendo compatibilidad de rutas y resolución de dependencias.

---

## 4) Estructura real del proyecto

El repositorio está organizado de la siguiente manera:

```text
├── app/                              # Módulo principal de la aplicación Android
│   ├── build.gradle                  # Configuración de compilación de la app (SDK 37, NDK r30)
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml   # Manifiesto con permisos USB y definición de actividades
│       │   ├── assets/               # Assets empaquetados en la APK (firmwares, datos de entorno)
│       │   ├── cpp/                  # Código C/C++ JNI (puente JNI para PtyBridge y terminal)
│       │   └── jniLibs/arm64-v8a/    # Binarios y librerías dinámicas precompiladas (.so) para ARM64
├── emulador_flashrom/                 # Emulador de flashrom y utilidades de depuración local
│   ├── emulador_flashrom.cpp         # Código C++ del emulador (emula CH341A, Bus Pirate, serprog)
│   ├── build_flashrom_local.sh       # Script para compilar el emulador localmente
│   ├── build_libusb_local.sh         # Script para compilar libusb localmente
│   └── patch_libusb_local.py         # Script para aplicar el parche de libusb localmente
├── gradle/                           # Gradle Wrapper y catálogo de dependencias
│   └── libs.versions.toml            # Definición centralizada de versiones (AGP, dependencias)
├── libs/                             # Copias de librerías nativas compartidas de referencia
├── fake_root/                        # Directorio de instalación temporal ("fake root") para compilaciones
├── setup-sdk.sh                      # Configura automáticamente el Android SDK/NDK necesario
├── build_libusb_custom.sh            # Script de compilación y parcheo de libusb en Termux
├── build_flashrom_custom.sh          # Script de compilación de flashrom en Termux
├── build_pciutils_custom.sh          # Script de compilación de pciutils en Termux
├── build_libftdi_custom.sh           # Script de compilación de libftdi en Termux
├── build_libjaylink_custom.sh        # Script de compilación de libjaylink en Termux
├── analizar_dependencias.py          # Script utilitario para analizar dependencias ELF (DT_NEEDED)
├── serprog_arduino_uno_ch340g.ino    # Código fuente del firmware serprog para Arduino UNO (en la raíz)
└── serprog_arduino_uno_ch340g.hex    # Firmware compilado e idéntico listo para Arduino UNO (en la raíz)
```

---

## 5) Binarios y dependencias nativas

### Binarios principales (en jniLibs, formato Android)

- `libflashrom_bin.so`  → runtime `usr/sbin/flashrom`
- `libsetpci.so`        → runtime `usr/sbin/setpci`
- `libpcilmr.so`        → runtime `usr/sbin/pcilmr`
- `libupdate-pciids.so` → runtime `usr/sbin/update-pciids`
- `liblspci.so`         → runtime `usr/bin/lspci`
- `libftdi_eeprom.so`   → runtime `usr/bin/ftdi_eeprom`

### Librerías base enlazadas

- `libflashrom.so(.1*)`
- `libpci.so(.3*)`
- `libftdi1.so(.2*)`
- `libftdipp1.so(.3*)`
- `libusb-1.0.so`
- `libjaylink.so`
- `libcrypto.so.3`
- `libssl.so.3`
- `libz.so.1`

### Resolución de nombres para Android (copia garantizada)

Android solo garantiza la copia directa de nombres terminados en `.so`. Por eso el proyecto mantiene binarios renombrados en `jniLibs` y crea enlaces runtime con el soname esperado:

- `libcrypto.so.3` runtime -> `libcrypto_3.so` en `jniLibs`
- `libssl.so.3` runtime -> `libssl_3.so` en `jniLibs`
- `libz.so.1` runtime -> `libz_1.so` en `jniLibs`

Las variantes con versión original (`*.so.N`) se pueden conservar como respaldo, pero el flujo principal usa los nombres Android-compatibles.

---

## 6) Parche de `libusb` para Android (fuente)

Archivo: `patch_libusb.py` (Python 3).

El parche inserta dos comportamientos clave en `core.c` de libusb:

1. **`libusb_get_device_list`**
   - Si existe `ANDROID_USB_FD`, crea una lista mínima y permite continuar flujo sin escaneo clásico.

2. **`libusb_open`**
   - Si existe `ANDROID_USB_FD`, llama:
   - `libusb_wrap_sys_device(ctx, (intptr_t)fd, dev_handle)`

Con esto, flashrom/libusb usan el descriptor otorgado por Android en Java, evitando problemas del modelo de permisos USB de Android.

---

## 7) Detección de dispositivos y auto-detección

La app detecta automáticamente el programador USB conectado usando un mapa de **18 VID:PID** y los rutea al camino correcto (PTY o libusb):

| Familia | VID:PID | Programador | Interfaz |
|---------|---------|-------------|----------|
| CH341A | `1a86:5512`, `1a86:5523` | `ch341a_spi` | libusb |
| CH347 | `1a86:55db` | `ch347_spi` | libusb |
| FT2232/FT232H | `0403:6010`–`6015` | `ft2232_spi` | libusb |
| Bus Pirate | `0403:6001` | `buspirate_spi` | **PTY** |
| ST-Link | `0483:3748`–`3754` | `stlinkv3_spi` | libusb |
| J-Link | `1366:0101/0105`, `1fc9:000c` | `jlink_spi` | libusb |
| Serprog | `2341:0043/0001`, `1a86:7523`, `10c4:ea60`, `067b:2303` | `serprog` | **PTY** |

### Flujo de detección

1. Java enumera dispositivos USB con `UsbManager`.
2. `USB_AUTO_MAP` busca coincidencia por VID:PID.
3. Si es programador serial (`serprog`, `buspirate_spi`, `spidriver`): abre `PtyBridge`.
4. Si es programador USB directo: exporta `ANDROID_USB_FD` al proceso nativo.
5. flashrom opera con el parámetro `-p` correcto.

### Programadores soportados por esta build

**Funcionan en Android (USB OTG):**
- **Vía PTY (serial):** serprog, buspirate_spi, spidriver
- **Vía libusb:** ch341a_spi, ch347_spi, ft2232_spi, dediprog, stlinkv3_spi, jlink_spi, pickit2_spi, usbblaster_spi, digilent_spi, dirtyjtag_spi, raiden_debug_spi, developerbox_spi
- **Emulación:** dummy

**Requieren root/PCI (no funcionan en Android sin root):**
- internal, drkaiser, gfxnvidia, nicintel, nicintel_spi, nicintel_eeprom, satasii, it8212, asm106x, atavia, ogp_spi, nv_sma_spi
- linux_spi, linux_mtd, parade_lspcon, mediatek_i2c_spi, mstarddc_spi, realtek_mst_i2c_spi, pony_spi

> Nota: la app no bloquea programadores manuales. La caja de comandos permite ejecutar cualquier parámetro.

---

## 8) Compilación y empaquetado

El proyecto tiene dos capas bien diferenciadas: la capa de aplicación Android (compilada mediante el SDK de Android) y la capa nativa (compilada directamente en Android usando Termux).

### A) Compilación nativa usando Termux

Para compilar la capa nativa directamente en tu teléfono Android se utiliza **Termux** como entorno de compilación Linux local:

1. **Instalar Termux** en el dispositivo Android.
2. Clona el repositorio dentro de Termux y accede a él.
3. Ejecuta los scripts de compilación custom en orden para compilar cada componente con `clang` e instalar las dependencias en el directorio temporal `fake_root` (`$HOME/fake_root/data/data/com.diamon.curso/files/usr`):
   
   - **`build_libusb_custom.sh`**: Parchea y compila `libusb`.
   - **`build_pciutils_custom.sh`**: Compila `pciutils` (necesario para acceso a buses).
   - **`build_libftdi_custom.sh`**: Compila `libftdi` (soporte para chips FTDI).
   - **`build_libjaylink_custom.sh`**: Compila `libjaylink` (soporte para programadores Segger J-Link).
   - **`build_flashrom_custom.sh`**: Compila `flashrom` integrando todas las librerías anteriores.

#### Notas críticas de Termux y alineación a 16KB:
- **Termux Toolchain**: Los scripts instalan automáticamente las dependencias de compilación mediante `pkg install` (`clang`, `make`, `pkg-config`, `git`, `python`, etc.) y `pip` (`meson`, `ninja`).
- **Soporte Android 15+ (16KB page-alignment)**: Para asegurar que los ejecutables y librerías `.so` funcionen en dispositivos con Android 15 y superiores que utilicen tamaños de página de 16KB, las banderas de compilación nativa en los scripts incluyen `-Wl,-z,max-page-size=16384` y `-flto`.
- **Rutas de compilación fija**: Las librerías y binarios se compilan con el prefijo `/data/data/com.diamon.curso/files/usr` para evitar fallos de reubicación en runtime.

### B) Compilación de la aplicación Android (Gradle)

Una vez que tengas los binarios precompilados en `jniLibs` y los datos de runtime en `assets`, puedes compilar la aplicación utilizando el SDK de Android:

1. **Instalación automática del SDK**:
   Ejecuta el script proporcionado en tu ordenador (Linux) para descargar e instalar el SDK, NDK (r30-rc1) y configurar `local.properties`:
   ```bash
   bash setup-sdk.sh
   ```
   *Nota: El SDK se descarga en `/tmp/android-sdk` de forma temporal.*

2. **Compilar la APK**:
   ```bash
   ./gradlew assembleDebug
   ```
   El APK generado se ubicará en `/tmp/calculo/outputs/apk/debug/app-debug.apk` debido a la redirección de compilación para limpieza del workspace.

### Archivos y Reportes de Referencia
- `setup-sdk.sh`: Prepara el SDK/NDK y el archivo `local.properties`.
- `REPORTE_ANALISIS_DEPENDENCIAS.md`: Muestra las dependencias dinámicas (`DT_NEEDED`) de cada binario compilado.
- `REPORTE_AUDITORIA_RUTAS_PORTABILIDAD.md`: Auditoría de rutas del sistema y portabilidad en el runtime.
- `REPORTE_PARCHE_LIBUSB_ANDROID.md`: Detalles del parche aplicado a `libusb` para inyección de file descriptors.
- `REPORTE_SOLUCION_PTY_SERPROG_BUSPIRATE.md`: Informe sobre la solución de comunicación serie mediante PTY en emuladores.

---

## 9) Uso básico de la app

1. Conecta el programador USB OTG.
2. Pulsa **Detectar y Conectar Automáticamente** — la app reconoce automáticamente el programador por VID:PID y establece el camino correcto (PTY para serprog/buspirate, libusb para CH341A/FT2232/etc.).
3. **Cargar ROM** (si vas a escribir) — importa `.bin`, `.hex`, `.rom` desde almacenamiento.
4. Usa los botones principales:
   - **Identificar Chip** — detecta el chip flash conectado
   - **Leer Backup** — lee el chip completo a `bios.bin`
   - **Verificar ROM** — compara ROM cargada vs chip
   - **Flashear ROM** — escribe ROM al chip
   - **Borrar Chip** — borra todo el contenido (0xFF) con confirmación
   - **Guardar ROM** — exporta datos leídos al almacenamiento
   - **Borrar ROM** — limpia archivos temporales
5. **Barra de progreso en tiempo real**: barra inline que parsea el porcentaje del stdout de flashrom.
6. **Consola de comandos**: ejecuta cualquier comando flashrom directamente.
7. **Visor Hex y Comparador Hex**: inspecciona y compara firmwares byte a byte.

El panel de log muestra salida nativa real con prefijo `[native]`, diagnósticos PTY cuando aplica, y estados de entorno.

---

## 10) Dependencias y licencias

### Proyecto

- **Flash EEPROM Tool**
- Licencia: **GNU GPL v3.0**
- Archivo: [`LICENSE.txt`](./LICENSE.txt)

### Dependencias principales

- **flashrom** — GPL-2.0+  
  https://github.com/flashrom/flashrom
- **libusb** — LGPL-2.1+  
  https://github.com/libusb/libusb
- **pciutils** — GPL-2.0+  
  https://github.com/pciutils/pciutils
- **libftdi** — LGPL-2.1+  
  https://developer.intra2net.com/git/libftdi
- **libjaylink** — GPL-2.0+  
  https://gitlab.zapb.de/libjaylink/libjaylink
- **OpenSSL (`libcrypto`/`libssl`)** — Apache License 2.0  
  https://www.openssl.org/
- **usb-serial-for-android** — MIT+  
  https://github.com/mik3y/usb-serial-for-android

> Revisa siempre obligaciones de distribución de binarios y código fuente según cada licencia.

---

## 11) Notas operativas importantes

- La app y los binarios están preparados para **ARM64 (`arm64-v8a`)**.
- Java usa `UsbManager` nativo para enumeración/selección/permisos/FD, y la operación del bus USB la realiza la capa nativa (flashrom/libusb parcheada).
- La selección de backend de flashrom (`-p`) se configura en la app y puede adaptarse a distintos programadores soportados por flashrom.
- Si agregas nuevos binarios/librerías en `jniLibs`, respeta sonames y rutas runtime esperadas para mantener compatibilidad.

---

## 12) Características de Usuario

### Barra de Progreso en Tiempo Real
- Barra delgada (4dp) integrada en el header del terminal — no consume espacio extra.
- Parsea el porcentaje del stdout de flashrom (ej: `Reading flash... 50%`) y muestra progreso visual.
- Cambia de color automáticamente: amarillo (en progreso), verde (completado), rojo (error).
- Se oculta automáticamente 3 segundos después de finalizar la operación.

### Borrado de Chip
- Botón **Borrar Chip** (`--erase`) con diálogo de confirmación de seguridad.
- Funciona tanto en modo real (hardware USB) como en modo dummy (emulación).

### Modo Dummy y Diagnóstico
La app incluye soporte nativo y un flujo dedicado para el programador `dummy` de flashrom.
- Permite probar la interfaz y comandos sin hardware real conectado.
- Cuenta con un menú **Modo Prueba (Dummy)** para generar chips virtuales (SST25VF040, MX25L6436, etc.) en un archivo temporal.
- Los botones principales adaptan su comando automáticamente si el programador dummy está seleccionado.

### Visor Hexadecimal Integrado
Cuenta con un visor hexadecimal profesional (`HexViewerActivity`) capaz de:
- Mostrar volcados de datos leídos del chip.
- Decodificar e inspeccionar archivos Intel HEX (analizando direcciones reales, soportando Extended Address Types 02/04).
- Mostrar siempre el *Origen* de los datos (ej: `Leído del chip (ch341a_spi)` o `Importado: firmware.hex`).
- Protección contra OOM para archivos grandes (límite de 32 MB para visualización).

### Comparador HEX (Diff)
Herramienta de comparación binaria (`HexDiffActivity`) accesible desde el menú:
- Compara dos archivos byte a byte con vista hexadecimal.
- Resalta diferencias en rojo con notación `A→B` (valor original → valor nuevo).
- Muestra estadísticas: cantidad y porcentaje de bytes diferentes.
- Pre-carga automáticamente `bios.bin` como archivo A si existe.

### Pinouts de Hardware
Guía de referencia rápida accesible desde el menú con diagramas renderizados:
- **CH341A Mini Programmer** — pinout del header SPI y configuración de jumpers.
- **Clip SOIC8/DIP8** — pinout del chip flash y tabla de conexión al CH341A.
- **Arduino UNO (serprog)** — conexión Flash SPI a pines 10-13 con nota de level shifter 5V→3.3V.
- **Bus Pirate** — conexión Flash SPI a header Bus Pirate con alimentación Vout.
- **SPIDriver** — conexión Flash SPI a SPIDriver (3.3V nativo).
- **Interfaz SPI** — diagrama de conexión Master-Slave.
- **Interfaz LPC/FWH** — diagrama de bus LPC para chips de bios de motherboard.

### Puente PTY↔USB para Programadores Seriales
- `PtyBridge.java` maneja la comunicación serial entre flashrom and dispositivos USB-serial.
- Soporta: **serprog** (Arduino), **buspirate_spi** (Bus Pirate), **spidriver** (SPIDriver).
- Sincronización: serprog espera beacon `0xAA 0x55`; buspirate/spidriver usan DTR/RTS + purge.
- Diagnóstico integrado: contadores de bytes y errores visibles en el log.

### Firmware Arduino Serprog
El repositorio incluye el código fuente del firmware y el archivo precompilado listos para instalar en tu placa Arduino:
- **Código fuente**: [`serprog_arduino_uno_ch340g.ino`](./serprog_arduino_uno_ch340g.ino) (en la raíz).
- **Firmware compilado**: [`serprog_arduino_uno_ch340g.hex`](./serprog_arduino_uno_ch340g.hex) (en la raíz, listo para ser subido directamente usando herramientas como `avrdude`).

Este firmware implementa el protocolo serprog para convertir tu Arduino UNO en un programador SPI compatible:
- Implementa 10 comandos serprog (NOP, SYNCNOP, Version, CmdMap, Name, BufSize, BusType, SetBus, SPI Op, Debug).
- Beacon de sincronización `0xAA 0x55` para comunicación confiable vía PTY.
- Compatible con chips CH340G y FTDI a 115200 bps.

### Consola de Comandos Avanzada
- Ejecuta comandos completos de `flashrom` tal como se haría en una terminal de PC (por ejemplo, `flashrom -p serprog -r backup.bin` o simplemente `-p serprog -r backup.bin`).
- Limpia y procesa el prefijo del ejecutable (`flashrom` o `./flashrom`) de forma automática.
- Resuelve dinámicamente y auto-completa el parámetro `-p` para programadores basados en puerto serie (ej. `serprog`, `buspirate_spi`, `spidriver`), inyectando el descriptor del pseudo-terminal (PTY) y sincronizando los hilos de comunicación por USB.
- Funciona sin conexión USB para comandos informativos (`flashrom --version`, `flashrom -L`, `flashrom --help`).

### Detección Automática de Programadores USB
18 VID:PIDs cubriendo 7 familias de programadores. Auto-selecciona parámetro `-p` y ruteo (PTY vs libusb).

### Importación y Exportación Inteligente
- **Cargar ROM**: usa el Android Storage Access Framework (SAF) para importar sin restricciones (`*/*`), detectando automáticamente el formato (binario crudo vs Intel HEX) y validando tamaños (hasta 128 MB). Incluye conversión Intel HEX → binario automática con validación de checksum.
- **Guardar ROM**: solo permite respaldar volcados legítimos (datos comprobados mediante chip read), asegurando que los usuarios no exporten accidentalmente el mismo archivo que importaron.
