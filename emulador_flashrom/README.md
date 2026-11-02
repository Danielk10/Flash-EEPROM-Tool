# Emulador de Flashrom (GD25Q80 / MX25L128) — Flash SPI Tool v1.9.12

Entorno de simulación de hardware y silicio para desarrollo, pruebas unitarias y validación local de **Flash SPI Tool v1.9.12** sin necesidad de conectar programadores físicos o chips reales.

El emulador implementa un modelo de silicio en C++ configurable: por defecto simula el chip **GigaDevice GD25Q80** (1 Megabyte / 1024 KB) y, con la opción `--mx25l128`, el **Macronix MX25L128** (16 MB, JEDEC `C2 20 18`) del issue #101. Incluye la emulación de protocolo para los cinco tipos de programadores compatibles con la aplicación.

---

## 🛠️ Programadores Virtuales Soportados

1. **CH341A (Modo USB Directo)**:
   - Simula un programador USB conectándose a través de un par de sockets UNIX.
   - Utiliza inyección de descriptor mediante la variable de entorno `ANDROID_USB_FD` y la librería `libusb` modificada.
   - Procesa flujos `UIO_STREAM` (pines CS) y `SPI_STREAM` (datos MOSI/MISO) con encuadre LSB de 4 bytes para evitar desalineación.

2. **Serprog (RP2040 / Arduino / STM32)**:
   - Crea un pseudo-terminal maestro/esclavo PTY virtual (`./serprog_pty`).
   - Implementa el protocolo binario `serprog` completo (SYNCNOP, Version, CmdMap, BufSize, BusType, SPI Operation).
   - Compatible con velocidades estándar y altas velocidades de comunicación (1.000.000 y 2.000.000 baudios) con respuesta de sincronización de beacon `0xAA 0x55`.

3. **Bus Pirate (v3 / v4)**:
   - Expone un PTY virtual en `./buspirate_pty`.
   - Simula la secuencia de inicialización de texto, entrada a modo binario `BBIO` y transición a modo SPI (`0x01`), respondiendo con la firma `Bus Pirate v3a...`.

4. **SPIDriver**:
   - Expone un PTY virtual en `./spidriver_pty`.
   - Simula el protocolo híbrido binario/ASCII a 460.800 baudios con comando de estado `?`, comprobación de eco `e <byte>`, Chip Select `s`/`u` y bloques de transferencia SPI de 1 a 64 bytes.

5. **FTDI FT232H (USB Directo / libftdi)**:
   - Simula las transferencias MPSSE del FT232H a través del socket UNIX y la variable `ANDROID_USB_FD`.
   - `flashrom` usa `libftdi1` sobre la `libusb` parcheada; el emulador descodifica las tramas MPSSE y las traduce a operaciones SPI.
   - Las respuestas MISO se empaquetan en tramas de 512 bytes (2 de estado + 510 de datos), el `packet_size` que `libftdi` obtiene del endpoint high-speed del FT232H. Un framing de 64 bytes corrompe las lecturas largas a partir del byte 63.

---

## 🚀 Compilación y Ejecución

### Compilar el binario:
```bash
make clean && make
```
Esto genera el ejecutable `emulador_flashrom`.

### Dependencias y wrapper autónomo

`local_root/lib` ya incluye:

- `libusb-1.0.so.0.6.0` con el parche de descriptores y `ANDROID_USB_FD`.
- `libftdi1.so.2` / `libftdi1.so.2.5.0` para la ruta FT232H.

`flashrom_local.sh` añade `local_root/lib` a `LD_LIBRARY_PATH`, así que **no hace falta exportar rutas manualmente**. Para comprobar la instalación:

```bash
./flashrom_local.sh -V
```

### Comandos de prueba rápida:

```bash
# Ver ayuda de opciones disponibles
./emulador_flashrom --help

# Modo CH341A autónomo con flashrom:
./emulador_flashrom --ch341a ./flashrom_local.sh -p ch341a_spi -r backup.bin

# Modo Serprog en segundo plano:
./emulador_flashrom --serprog &
./flashrom_local.sh -p serprog:dev=./serprog_pty:115200 -r backup.bin
kill %1

# Modo Bus Pirate en segundo plano:
./emulador_flashrom --buspirate &
./flashrom_local.sh -p buspirate_spi:dev=./buspirate_pty -r backup.bin
kill %1

# Modo SPIDriver en segundo plano:
./emulador_flashrom --spidriver &
./flashrom_local.sh -p spidriver:dev=./spidriver_pty -r backup.bin
kill %1

# Modo FTDI FT232H autónomo:
./emulador_flashrom --ftdi ./flashrom_local.sh -p ft2232_spi:type=232H -r backup.bin

# Modo FTDI con el Macronix MX25L128 de 16 MB (issue #101):
./emulador_flashrom --ftdi --mx25l128 ./flashrom_local.sh -p ft2232_spi:type=232H

# Suite completa de los cinco programadores (chip GD25Q80 por defecto):
./test_all_devices.sh

# Reproducción automatizada del issue #101 (probe + lectura + escritura/VERIFIED):
./test_issue_101.sh
```

---

## 🧪 Reproducción del issue #101 (MX25L128 de 16 MB)

El chip por defecto es el GD25Q80 (1 MB). La opción `--mx25l128` cambia el modelo de silicio a un **Macronix MX25L128 de 16 MB** que responde al comando RDID con `C2 20 18`. Al compartir ese JEDEC ID con otras variantes, `flashrom` encuentra cinco definiciones compatibles y aborta pidiendo `-c`:

```text
Found Macronix flash chip "MX25L12805D" (16384 kB, SPI) on ft2232_spi.
Found Macronix flash chip "MX25L12833F" (16384 kB, SPI) on ft2232_spi.
Found Macronix flash chip "MX25L12835F/MX25L12873F" (16384 kB, SPI) on ft2232_spi.
Found Macronix flash chip "MX25L12845E/MX25L12865E" (16384 kB, SPI) on ft2232_spi.
Found Macronix flash chip "MX25L12850F" (16384 kB, SPI) on ft2232_spi.
Multiple flash chip definitions match the detected chip(s): "MX25L12805D", "MX25L12833F", "MX25L12835F/MX25L12873F", "MX25L12845E/MX25L12865E", "MX25L12850F"
Please specify which chip definition to use with the -c <chipname> option.
```

El script `test_issue_101.sh` automatiza la validación completa:

1. **Probe:** comprueba las cinco definiciones y el mensaje de ambigüedad.
2. **Lectura:** ejecuta `-c MX25L12835F/MX25L12873F -r` y verifica 16.777.216 bytes idénticos a la memoria emulada.
3. **Escritura:** ejecuta `-w` sobre los 16 MB y exige `VERIFIED.` y contenido idéntico.

---

## 🔐 Validación de Volcados e Integridad (MD5 / SHA-256 & Hex Diff)

Los archivos `.bin` generados durante las pruebas con el emulador pueden auditarse de inmediato:
- **Verificación Criptográfica:** Mediante `md5sum backup.bin` y `sha256sum backup.bin` en terminal o con la opción **"🔒 Verificar Integridad MD5 / SHA-256"** de la app.
- **Comparador Hex Diff con Hashes MD5:** Comparación side-by-side de archivos en **"Comparar Archivos Hex (Diff)"**, mostrando sumas MD5, discrepancias byte a byte y mapa de calor visual por sectores de 4 KB.
- **Suite Pro:** Los volcados son compatibles con las herramientas de la suite Pro (Bóveda Antibrick, Editor Hexadecimal en Vivo con parcheo in-place, inyector MAC Intel GbE `0xBABA`, extractor Windows MSDM y registros SPI), disponibles con el Pase Libre de 12 horas o compra permanente con **"Sin anuncios de por vida"**.

---

## 🔄 Flujo de Desarrollo

- **Repositorio (`Flash-EEPROM-Tool/emulador_flashrom/`)**: única fuente de verdad y único directorio de ejecución. Todas las modificaciones deben registrarse aquí.
- **No mantener una copia en `~/emulador_flashrom/`**: la copia antigua del home se eliminó para evitar binarios y librerías desincronizados.
- **No mezclar librerías**: la `libusb` de este emulador es específica para flashrom (FTDI/CH341A). La `libusb` de `~/native_test_root` pertenece a minipro y su parche es distinto; copiarla aquí rompe la ruta FTDI y viceversa.
- Para documentación exhaustiva sobre arquitectura, parcheo de `libusb` y simulación JEDEC, consulta [`GUIA_USO_EMULADOR.md`](../GUIA_USO_EMULADOR.md).
