# Guía de Uso del Emulador Flashrom (CH341A, Serprog, Bus Pirate, SPIDriver y FTDI FT232H) — Flash SPI Tool v1.9.12

Esta guía explica en detalle cómo compilar, configurar y usar el **Emulador de Memorias Flash SPI** desarrollado en C++ junto con la versión parchada de **`libusb`** y **`flashrom`** nativo para la validación y pruebas de **Flash SPI Tool v1.9.12** (versionCode 98).

El emulador permite simular dispositivos programadores reales contra una memoria SPI de prueba (**GigaDevice GD25Q80** de 1 MB por defecto, o **Macronix MX25L128** de 16 MB con `--mx25l128`) en un entorno local, ideal para desarrollo, validación y depuración rápida sin hardware real. La misma instalación incluye `libftdi1` para validar el programador **FTDI FT232H**.

---

## 🧩 ¿Qué es y qué hace este emulador?

El emulador es un **simulador de hardware en software** que cumple dos funciones principales para probar la aplicación Android localmente sin necesidad de conectar ningún programador o chip físico:

### Función 1: Simulación del Chip de Memoria SPI (GD25Q80 por defecto / MX25L128 opcional)

El chip virtual se simula dentro de la clase `SpiFlashChip` usando una máquina de estados C++ configurable:

*   **Chip por defecto**: GigaDevice `GD25Q80` (también conocido como GD25080PCP, 1 Megabyte, JEDEC `C8 40 14`).
*   **Chip del issue #101**: con `--mx25l128`, Macronix `MX25L128` (16 MB, JEDEC `C2 20 18`), que hace que `flashrom` encuentre cinco definiciones compatibles.
*   **Memoria Virtual**: Mantiene un buffer en RAM de 1,048,576 bytes (1 MB) o 16,777,216 bytes (16 MB), inicializado a `0xFF` (estado erased).
*   **Instrucciones SPI soportadas**: Interpreta y ejecuta los comandos estándar de memorias Flash NOR:

| Código | Comando | Descripción |
|--------|---------|-------------|
| `0x9F` | Read JEDEC ID (RDID) | Devuelve `C8 40 14` (GD25Q80) o `C2 20 18` (MX25L128 con `--mx25l128`) para que `flashrom` reconozca el modelo. |
| `0x90` | Read Electronic Manufacturer/Device ID (REMS) | Devuelve `C8 13` (GD25Q80) o `C2 18` (MX25L128). |
| `0xAB` | Release Power-down / Device ID (RES) | Devuelve Device ID `13` (GD25Q80) o `18` (MX25L128). |
| `0x03` | Read Data | Lee datos secuenciales de la memoria virtual a partir de la dirección de 24 bits indicada. |
| `0x0B` | Fast Read | Igual que Read Data pero con un byte dummy adicional antes de los datos de salida. |
| `0x06` | Write Enable (WREN) | Habilita el flag interno `write_enable`, requerido antes de cualquier operación de borrado o escritura. |
| `0x04` | Write Disable (WRDI) | Desactiva el flag `write_enable`. |
| `0x05` | Read Status Register (RDSR) | Devuelve el registro de estado con el bit WEL (Write Enable Latch). |
| `0x01` | Write Status Register (WRSR) | Escribe al registro de estado interno. |
| `0x20` | Sector Erase (SE, 4KB) | Borra un sector de 4096 bytes rellenándolo con `0xFF`. |
| `0x52` | Block Erase 32KB (BE32) | Borra un bloque de 32768 bytes. |
| `0xD8` | Block Erase 64KB (BE64) | Borra un bloque de 65536 bytes. |
| `0xC7` / `0x60` | Chip Erase (CE) | Borra toda la memoria completa (1 MB o 16 MB → `0xFF`). |
| `0x02` | Page Program (PP) | Programa hasta 256 bytes en una página. Respeta la semántica real del hardware: solo puede limpiar bits (AND con datos existentes). |

### Función 2: Simulación de los Programadores (El Puente de Comunicación)

Para que `flashrom` (o el código nativo de la app Android) pueda comunicarse con este chip virtual, el emulador simula el comportamiento de los siguientes programadores físicos:

*   **Modo CH341A (USB Directo)**: A través del script `patch_libusb_local.py`, se interceptan las llamadas a la biblioteca `libusb`. Todas las transferencias de lectura/escritura USB se desvían a través de un Socket UNIX (variable de entorno `ANDROID_USB_FD`). El emulador escucha en ese socket, descodifica los paquetes de comandos USB de CH341A (como levantar/bajar la señal CS en los pines `0xAB` o enviar datos SPI `0xA8`), ejecuta la operación en el chip virtual, y devuelve los bytes simulados empaquetados como si vinieran de un chip USB real.

*   **Modo FTDI FT232H (USB Directo / libftdi)**: El emulador simula un FT232H con motor MPSSE. `flashrom` usa `libftdi1` para construir las tramas MPSSE y la `libusb` parcheada para entregarlas por el socket; el emulador las descodifica y las traduce a operaciones SPI sobre el chip virtual. Es el modo que valida la ruta FT2232H/FT232H.

*   **Modo Serprog (PTY Serie)**: El emulador crea un pseudo-terminal (PTY) y expone un enlace simbólico (`./serprog_pty`). `flashrom` lo abre creyendo que es un puerto serie físico. El emulador recibe los comandos binarios del protocolo Serprog y los traduce a señales SPI para el chip virtual.

*   **Modo Bus Pirate (PTY Serie)**: Similar a Serprog pero utilizando el protocolo binario de Bus Pirate v3. El emulador gestiona la secuencia de inicialización de texto → modo BBIO → modo SPI, incluyendo el banner de versión y el comando de reset `0x0f`.

*   **Modo SPIDriver (PTY Serie)**: Crea un PTY virtual en `./spidriver_pty`. Simula el protocolo binario/ASCII del programador SPIDriver a 460800 bps. Soporta el comando de estado `?` (devolviendo una cadena de texto de 80 bytes entre corchetes), el eco `e <byte>`, Chip Select `s` y `u`, desconexión `x` y bloques de lectura/escritura de 1 a 64 bytes (`0x80-0xBF` y `0xC0-0xFF`).

### ⚙️ ¿Para qué sirve todo esto?

Te permite **validar todo el flujo de software en tu máquina local**:

1. Compilar tu código nativo C++ y la lógica de Java/Android.
2. Comprobar que el parche de `libusb` y la herencia de descriptores USB (`O_CLOEXEC` → `dup()`) funcionan correctamente.
3. Ejecutar comandos reales de `flashrom` de **lectura**, **borrado**, **escritura** y **verificación** sobre la memoria virtual, detectando errores de protocolo o desalineaciones de bytes al instante.

---

## 🔄 Gestión de Cambios y Desarrollo

El repositorio es la **única fuente de verdad y el único directorio de ejecución**:

*   **Versión del Repositorio (`~/Flash-EEPROM-Tool/emulador_flashrom`)**: contiene el código, el binario, `local_root` con las librerías y los scripts de prueba. **Cualquier actualización, corrección o mejora debe aplicarse y confirmarse (commit) aquí.**
*   **No se mantiene una copia separada en `~/emulador_flashrom`**: la copia antigua del home se eliminó para evitar binarios y librerías desincronizados.
*   Si necesitas un espacio temporal para pruebas, usa `/tmp/opencode` y no dupliques `local_root` ni el código del emulador.

### ⚠️ Compatibilidad de librerías: flashrom vs. minipro

Cada herramienta usa su propio parche de `libusb`; **no se deben mezclar**:

*   **flashrom (este emulador):** `emulador_flashrom/local_root/lib/libusb-1.0.so.0.6.0`, con el parche de descriptores FTDI/CH341A y `ANDROID_USB_FD`.
*   **minipro (proyecto Lector-De-Memorias):** `~/native_test_root/lib/libusb-1.0.so.0.6.0`, con su propio parche de socket.

El parche de flashrom intercepta VIDs que no son FTDI/CH341A y devuelve un descriptor CH341 simulado; si se copia sobre la librería de minipro, minipro se queda colgado. A la inversa, la librería de minipro no implementa la ruta FTDI necesaria para `ft2232_spi`. Mantén cada librería en su directorio.

---

## 📋 Arquitectura del Sistema de Emulación

El entorno consta de tres partes principales que trabajan en conjunto:

```
[ Flashrom (Host CLI) ]
          │ (libusb parcheada + libftdi para USB directo, o enlace PTY serie)
          ▼
[ Puente Socket UNIX / Pseudo-terminal PTY ]
          │ (Framing de paquetes LSB o traducción de protocolos de hardware)
          ▼
[ Emulador C++ (emulador_flashrom) ]
          │ (Interacción SPI con el chip de memoria virtual)
          ▼
[ Modelo de Memoria (SpiFlashGD25Q80) ]
```

1. **El parche de `libusb` y `libftdi`**: intercepta las llamadas de bus del sistema real. Cuando la variable de entorno `ANDROID_USB_FD` está presente, simula descriptores, salta controles de kernel invasivos (`ioctl`s de reclamación de interfaz) e implementa una cola de llamadas asíncronas en diferido para evitar deadlocks de callbacks. En FTDI, `libftdi1` construye las tramas MPSSE sobre esa misma `libusb`.
2. **El Emulador C++**: implementa la lógica específica de los microcontroladores de los programadores.
3. **El Chip SPI Virtual**: simula los comandos estándar JEDEC (`RDID` `9Fh`, `REMS` `90h`), control de chip select (`CS`), y lectura/escritura de memoria.

---

## 🛠️ Requisitos de Compilación

Asegúrate de contar con las siguientes herramientas de desarrollo en tu sistema:
```bash
sudo apt-get update
sudo apt-get install -y build-essential meson ninja-build libpci-dev libudev-dev libftdi1-dev pkg-config curl unzip
```

---

## 🚀 Paso 1: Compilar el Emulador C++

El emulador contiene los tres controladores en un único binario multiprotocolo. Para compilarlo:

```bash
cd ~/Flash-EEPROM-Tool/emulador_flashrom
make clean && make
```
Esto generará el archivo ejecutable `emulador_flashrom`.

---

## 📦 Paso 2: Compilar e Instalar `libusb`, `libftdi` y `flashrom` Locales

Para no ensuciar las bibliotecas del sistema principal, toda la instalación local se compila y aloja dentro de `~/Flash-EEPROM-Tool/emulador_flashrom/local_root`.

1. **Compilar `libusb` con el parche**:
   El script descarga la versión adecuada de `libusb`, le aplica las modificaciones requeridas de simulación de descriptor y temporización de callbacks, y la instala en `local_root`:
   ```bash
   bash build_libusb_local.sh
   ```

2. **Compilar `flashrom` contra la biblioteca parchada**:
   El script descarga la última versión de `flashrom`, la enlaza dinámicamente contra la `libusb` local y la instala en `local_root/sbin`:
   ```bash
   bash build_flashrom_local.sh
   ```

3. **Dependencias de ejecución ya incluidas**:
   `local_root/lib` contiene:
   - `libusb-1.0.so.0.6.0`, con el parche nuevo de descriptores y `ANDROID_USB_FD`.
   - `libftdi1.so.2` y `libftdi1.so.2.5.0`, necesarias para la ruta FT232H.

   El wrapper `flashrom_local.sh` añade `local_root/lib` a `LD_LIBRARY_PATH`, por lo que **no hay que exportar rutas manualmente**.

4. **Verificar la instalación**:
   ```bash
   ./flashrom_local.sh -V
   ```
   Debe mostrar `flashrom v2.0.0-devel` sin errores de `libusb` ni de `libftdi1.so.2`.

---

## 🔌 Paso 3: Pruebas de Detección por Dispositivo

### Suite automática (cinco programadores)

La forma recomendada de validar toda la instalación es ejecutar la suite desde el repositorio:

```bash
cd ~/Flash-EEPROM-Tool/emulador_flashrom
./test_all_devices.sh
```

El script compila el emulador si hace falta y prueba **Serprog, Bus Pirate, SPIDriver, CH341A y FTDI FT232H**. La salida correcta termina con cinco `✅ [ÉXITO]` y `=== FIN DE LAS PRUEBAS ===`.

### A. Emulación de CH341A (Modo USB Directo)
El controlador de CH341A se comunica mediante transferencias USB. Nuestro `libusb` intercepta el socket UNIX generado por el emulador y simula los endpoints USB.

*   **¿Cómo funciona?**
    El host envía los comandos antecedidos por un tamaño de 4 bytes LSB (*framing*). El emulador extrae y procesa los flujos `UIO_STREAM` (para el control de los pines SPI `CS` de habilitación) y `SPI_STREAM` (para transferir bytes MOSI a la memoria y leer MISO).

*   **Comando de Ejecución (Autónomo)**:
    ```bash
    ./emulador_flashrom --ch341a ./flashrom_local.sh -p ch341a_spi -VVV
    ```
    *Nota: Este comando inicia el emulador, crea el canal de comunicación socket y ejecuta automáticamente el subproceso flashrom local redireccionando las variables de entorno necesarias.*

---

### B. Emulación de Serprog (Puente Serial PTY)
Serprog es un protocolo serie para programadores AVR/Arduino. El emulador crea un pseudo-terminal PTY virtual que actúa como puerto serie físico.

*   **¿Cómo funciona?**
    El emulador genera un PTY maestro y crea un enlace simbólico llamado `./serprog_pty` que apunta al puerto esclavo. `flashrom` abre este enlace como si fuese un puerto COM/tty. El emulador implementa comandos como `SYNCNOP`, `Query Interface`, `Query Programmer Name`, y `SPI Operation` traduciendo estas solicitudes a la memoria flash virtual.

*   **Comandos de Ejecución**:
    1. Iniciar el emulador Serprog en segundo plano:
       ```bash
       ./emulador_flashrom --serprog &
       ```
    2. Ejecutar `flashrom` apuntando al dispositivo PTY virtual:
       ```bash
       ./flashrom_local.sh -p serprog:dev=./serprog_pty:115200 -VVV
       ```
    3. Finalizar la ejecución del emulador en segundo plano:
       ```bash
       kill %1
       rm -f ./serprog_pty
       ```

---

### C. Emulación de Bus Pirate (Puente Serial PTY)
El Bus Pirate es una herramienta de diagnóstico multiprotocolo. Al igual que Serprog, el emulador crea un puerto serie virtual mediante pseudo-terminales.

*   **¿Cómo funciona?**
    Al iniciarse, el Bus Pirate se encuentra en modo texto. `flashrom` envía 20 caracteres `0x00` a intervalos regulares para forzar la entrada a modo binario (`BBIO`). Una vez ahí, envía `0x01` para entrar al modo SPI. El emulador soporta operaciones binarias y el comando de reset (`0x0f`) que restablece el banner de texto requerido por `flashrom` (`Bus Pirate v3a Firmware v5.5 HiZ>`).

*   **Comandos de Ejecución**:
    1. Iniciar el emulador Bus Pirate en segundo plano:
       ```bash
       ./emulador_flashrom --buspirate &
       ```
    2. Ejecutar `flashrom` apuntando al dispositivo PTY virtual:
       ```bash
       ./flashrom_local.sh -p buspirate_spi:dev=./buspirate_pty -VVV
       ```
    3. Finalizar la ejecución del emulador en segundo plano:
       ```bash
       kill %1
       rm -f ./buspirate_pty
       ```

---

### D. Emulación de SPIDriver (Puente Serial PTY)
El SPIDriver es un programador SPI compacto y rápido. El emulador crea un pseudo-terminal PTY virtual para simularlo.

*   **¿Cómo funciona?**
    El emulador genera un PTY maestro y crea el enlace simbólico `./spidriver_pty`. `flashrom` se conecta a 460800 bps y realiza un handshake enviando `?` para obtener la cadena de estado de 80 bytes. Utiliza comandos de eco (`e`) para asegurar la sincronización antes de iniciar las transferencias de lectura/escritura de 1 a 64 bytes.

*   **Comandos de Ejecución**:
    1. Iniciar el emulador SPIDriver en segundo plano:
       ```bash
       ./emulador_flashrom --spidriver &
       ```
    2. Ejecutar `flashrom` apuntando al dispositivo PTY virtual:
       ```bash
       ./flashrom_local.sh -p spidriver:dev=./spidriver_pty -VVV
       ```
    3. Finalizar la ejecución del emulador en segundo plano:
       ```bash
       kill %1
       rm -f ./spidriver_pty
       ```

---

### E. Emulación de FTDI FT232H (USB Directo / libftdi)
El FT232H es un puente USB-SPI basado en MPSSE. El emulador simula sus transferencias y `flashrom` lo usa mediante `libftdi1` sobre la `libusb` parcheada.

*   **Comando de Ejecución (Autónomo)**:
    ```bash
    ./emulador_flashrom --ftdi ./flashrom_local.sh -p ft2232_spi:type=232H -VVV
    ```
    *Nota: el emulador crea el canal socket, exporta `ANDROID_USB_FD` y lanza automáticamente `flashrom`. `libftdi1` construye las tramas MPSSE y el emulador las traduce a operaciones SPI sobre el chip virtual. Las respuestas MISO usan paquetes de 512 bytes (2 de estado + 510 de datos), el `max_packet_size` que `libftdi` obtiene del endpoint high-speed del FT232H.*

### F. Reproducción del issue #101 (MX25L128 de 16 MB)

La opción `--mx25l128` sustituye el GD25Q80 por un **Macronix MX25L128 de 16 MB** que responde al RDID con `C2 20 18`. Ese JEDEC ID coincide con cinco definiciones de `flashrom`, reproduciendo exactamente la ambigüedad reportada en el issue #101:

```bash
./emulador_flashrom --ftdi --mx25l128 ./flashrom_local.sh -p ft2232_spi:type=232H
```

Salida esperada:

```text
Found Macronix flash chip "MX25L12805D" (16384 kB, SPI) on ft2232_spi.
Found Macronix flash chip "MX25L12833F" (16384 kB, SPI) on ft2232_spi.
Found Macronix flash chip "MX25L12835F/MX25L12873F" (16384 kB, SPI) on ft2232_spi.
Found Macronix flash chip "MX25L12845E/MX25L12865E" (16384 kB, SPI) on ft2232_spi.
Found Macronix flash chip "MX25L12850F" (16384 kB, SPI) on ft2232_spi.
Multiple flash chip definitions match the detected chip(s): "MX25L12805D", "MX25L12833F", "MX25L12835F/MX25L12873F", "MX25L12845E/MX25L12865E", "MX25L12850F"
Please specify which chip definition to use with the -c <chipname> option.
```

En la app, ese mensaje de ambigüedad dispara el diálogo de selección de chip. Al elegir una definición, la app reintenta el mismo comando insertando `-c <chip>` justo después de `-p` (`FlashromExecutor.withChipArgument`) y la operación continúa. La validación completa de ese ciclo (intento sin `-c`, reintento con `-c`, lectura de 16.777.216 bytes y escritura con `VERIFIED.`) está automatizada en:

```bash
./test_issue_101.sh
```

Salida correcta:

```text
✅ Primer intento sin -c: 5 definiciones + ambigüedad, la app mostraría el diálogo (exit=1)
✅ Lectura: 16777216 bytes idénticos al contenido emulado
✅ Escritura: 16 MB programados y VERIFIED.
=== TODO OK: issue #101 reproducible con el emulador ===
```

---

## 📊 Salida de Detección Exitosa de Memoria (GD25Q80)

Cuando ejecutes cualquiera de los cinco modos anteriores, la salida de `flashrom` reportará la correcta inicialización del programador, la interrogación de los IDs de fabricante y capacidad y la identificación final de la memoria virtualizada. La suite `test_all_devices.sh` comprueba automáticamente esta salida en cada modo:

```text
Initializing programmer...
...
Probing for Generic unknown SPI chip (RDID), 0 kB: compare_id: id1 0xc8, id2 0x4014
Found GigaDevice flash chip "GD25Q80(B)" (1024 kB, SPI).
This flash part has status UNTESTED for operations: WP
No operations were specified.
Bus Pirate/Serprog/CH341A/SPIDriver/FTDI shutdown completed.
```

¡El entorno local de emulación y validación está listo y comprobado al 100%!

---

## 🔐 Validación Criptográfica y Cotejo de Volcados (MD5 / SHA-256 y Hex Diff)

Una vez realizada una operación de lectura o flasheo con el emulador (ej. `./flashrom_local.sh -p ch341a_spi -r backup.bin`), el flujo de desarrollo y pruebas permite certificar la integridad de los datos simulados mediante las mismas herramientas de ingeniería integradas en **Flash SPI Tool**:

1. **Verificación Criptográfica MD5 / SHA-256**:
   - **En consola Linux de desarrollo:**
     ```bash
     md5sum backup.bin
     sha256sum backup.bin
     ```
   - **En la aplicación móvil:** Mediante la opción del menú principal **"🔒 Verificar Integridad MD5 / SHA-256"**, se ejecuta el motor de cálculo en streaming NIO multialgoritmo (MD5, SHA-1, SHA-256 y CRC32) con verificación contra el portapapeles y exportación de sidecars `.sha256` y `.md5`.

2. **Comparador Hex Diff con Hashes MD5**:
   - **En la aplicación móvil:** Mediante la opción **"Comparar Archivos Hex (Diff)"**, se pueden cotejar dos volcados (por ejemplo, el firmware de fábrica `original_data.bin` frente al volcado simulado `backup.bin`).
   - El visor de diferencias realiza un cálculo simultáneo de las **sumas de verificación MD5** de ambos archivos, reportando el porcentaje de discrepancia exacto, los bytes agregados o removidos (`XX→--` / `--→XX`), y mapeando las divergencias sobre una cinta de calor interactiva (`DiffHeatmapView`) dividida en sectores de 4 KB.

3. **Integración con la Suite Pro**:
   - Los volcados obtenidos del emulador son 100% compatibles con las utilidades de laboratorio de la suite Pro (Bóveda Antibrick con snapshot previo, Editor Hexadecimal en Vivo con modificación in-place, inyector MAC Intel GbE con checksum NVM `0xBABA`, extractor de licencias OEM Windows MSDM y desbloqueo de registros SPI SR1/SR2/SR3), disponibles mediante el Pase Libre de 12 horas o la compra in-app permanente con **"Sin anuncios de por vida"**.

---

## 🔬 Detalles de Arquitectura Avanzada e Integración (Lecciones de Lector-De-Memorias y K150)

Para asegurar la máxima compatibilidad de las herramientas y evitar los errores comunes detectados en los proyectos emuladores previos (como `Lector-De-Memorias` y el emulador de `K150`), se documentan las siguientes soluciones arquitectónicas aplicadas en este proyecto:

### 1. Herencia de Descriptores USB en Procesos Hijos (`O_CLOEXEC`)
*   **Problema**: En Android, el descriptor de archivo de conexión USB obtenido mediante `UsbDeviceConnection.getFileDescriptor()` posee activado el flag `O_CLOEXEC` por defecto. Si se intenta lanzar `flashrom` directamente a través de `ProcessBuilder` (que ejecuta `exec`), el kernel de Android cierra el descriptor USB inmediatamente, provocando que `flashrom` reciba un descriptor inválido.
*   **Solución**: Se utiliza una llamada JNI nativa `dup()` en `native-lib.cpp` para duplicar el descriptor. Dado que `dup()` por definición no propaga el flag `O_CLOEXEC`, y removiendo dicho flag explícitamente a través de `fcntl(newFd, F_SETFD, flags & ~FD_CLOEXEC)`, el descriptor de socket duplicado sobrevive al proceso de herencia de `execv()` y puede ser consumido exitosamente en el subproceso a través de la variable de entorno `ANDROID_USB_FD`.

### 2. Aislamiento de Señales de Control de Módem (DTR/RTS) en PTYs
*   **Problema**: Los pseudo-terminales virtuales (PTYs) de Linux que actúan como puente COM para programadores basados en serie (como Serprog y Bus Pirate) no exponen físicamente líneas de control de módem. Intentar modificar el estado de DTR/RTS directamente sobre el esclavo del PTY (por ejemplo, con llamadas a `ioctl` o a través de bibliotecas como `pyserial` o serial-drivers de C) arroja excepciones del sistema operativo (`OSError` o error de tipo `ENOTTY` - Inappropriate ioctl for device).
*   **Solución**: Se implementó una arquitectura de puente aislado. Las llamadas para resetear o conmutar DTR/RTS son gestionadas **exclusivamente** desde el hilo principal Java en Android sobre el puerto USB físico real (`UsbSerialPort`). Por su parte, la comunicación por PTY se mantiene puramente en modo `RAW` binario. De esta forma, el subproceso CLI ve al PTY como una línea de comunicación transparente de datos puros y no genera fallos ni excepciones al interactuar con el puerto virtual.

### 3. Evitar el Solapamiento de Mensajes mediante Framing de Longitud
*   **Problema**: El bus USB real preserva los límites de los paquetes de datos definidos en cada transferencia asíncrona. Sin embargo, un canal de socket UNIX concatena los datos de forma continua (flujo de bytes continuo). En emuladores basados únicamente en buffers fijos, esto solía causar desalineación de bytes e interbloqueos (deadlocks) al cambiar el tamaño de los paquetes.
*   **Solución**: Se implementó un protocolo de encuadre en el canal socket de CH341A. Cada transacción es precedida por un entero LSB de 4 bytes que define el tamaño del paquete. Esto permite al emulador de C++ conocer con total precisión cuántos bytes del flujo SPI debe leer y procesar antes de emitir la respuesta correspondiente de vuelta al host.
