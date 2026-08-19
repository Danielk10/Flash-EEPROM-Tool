# Guía de Uso del Emulador Flashrom (CH341A, Serprog y Bus Pirate)

Esta guía explica en detalle cómo compilar, configurar y usar el **Emulador de Memorias Flash SPI** desarrollado en C++ junto con la versión parchada de **`libusb`** y **`flashrom`** nativo. 

El emulador permite simular dispositivos programadores reales contra una memoria SPI de prueba (**GigaDevice GD25Q80** de 1 MB) en un entorno local, ideal para desarrollo, validación y depuración rápida sin hardware real.

---

## 🧩 ¿Qué es y qué hace este emulador?

El emulador es un **simulador de hardware en software** que cumple dos funciones principales para probar la aplicación Android localmente sin necesidad de conectar ningún programador o chip físico:

### Función 1: Simulación del Chip de Memoria SPI (GD25Q80 / GD25080PCP)

El chip físico GigaDevice `GD25Q80` (también conocido como GD25080PCP, 1 Megabyte, interfaz SPI) se simula dentro de la clase `SpiFlashGD25Q80` usando una máquina de estados C++:

*   **Memoria Virtual**: Mantiene un buffer en RAM de 1,048,576 bytes (1MB), inicializado a `0xFF` (estado erased).
*   **Instrucciones SPI soportadas**: Interpreta y ejecuta los comandos estándar de memorias Flash NOR:

| Código | Comando | Descripción |
|--------|---------|-------------|
| `0x9F` | Read JEDEC ID (RDID) | Devuelve los identificadores del fabricante (`C8 40 14`) para que `flashrom` reconozca el chip exacto como GD25Q80(B). |
| `0x90` | Read Electronic Manufacturer/Device ID (REMS) | Devuelve el par Manufacturer ID (`C8`) y Device ID (`13`). |
| `0xAB` | Release Power-down / Device ID (RES) | Devuelve Device ID (`13`). |
| `0x03` | Read Data | Lee datos secuenciales de la memoria virtual a partir de la dirección de 24 bits indicada. |
| `0x0B` | Fast Read | Igual que Read Data pero con un byte dummy adicional antes de los datos de salida. |
| `0x06` | Write Enable (WREN) | Habilita el flag interno `write_enable`, requerido antes de cualquier operación de borrado o escritura. |
| `0x04` | Write Disable (WRDI) | Desactiva el flag `write_enable`. |
| `0x05` | Read Status Register (RDSR) | Devuelve el registro de estado con el bit WEL (Write Enable Latch). |
| `0x01` | Write Status Register (WRSR) | Escribe al registro de estado interno. |
| `0x20` | Sector Erase (SE, 4KB) | Borra un sector de 4096 bytes rellenándolo con `0xFF`. |
| `0x52` | Block Erase 32KB (BE32) | Borra un bloque de 32768 bytes. |
| `0xD8` | Block Erase 64KB (BE64) | Borra un bloque de 65536 bytes. |
| `0xC7` / `0x60` | Chip Erase (CE) | Borra toda la memoria completa (1MB → `0xFF`). |
| `0x02` | Page Program (PP) | Programa hasta 256 bytes en una página. Respeta la semántica real del hardware: solo puede limpiar bits (AND con datos existentes). |

### Función 2: Simulación de los Programadores (El Puente de Comunicación)

Para que `flashrom` (o el código nativo de la app Android) pueda comunicarse con este chip virtual, el emulador simula el comportamiento de los siguientes programadores físicos:

*   **Modo CH341A (USB Directo)**: A través del script `patch_libusb_local.py`, se interceptan las llamadas a la biblioteca `libusb`. Todas las transferencias de lectura/escritura USB se desvían a través de un Socket UNIX (variable de entorno `ANDROID_USB_FD`). El emulador escucha en ese socket, descodifica los paquetes de comandos USB de CH341A (como levantar/bajar la señal CS en los pines `0xAB` o enviar datos SPI `0xA8`), ejecuta la operación en el chip virtual, y devuelve los bytes simulados empaquetados como si vinieran de un chip USB real.

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

Para mantener un entorno de trabajo organizado y bajo control de versiones, se utiliza un flujo de dos directorios:

*   **Versión del Repositorio (`~/Flash-SPI-Tool/emulador_flashrom`)**: Es la fuente de verdad. **Cualquier actualización, corrección o mejora en el código fuente del emulador debe aplicarse y confirmarse (commit) en esta ubicación.**
*   **Versión de Desarrollo/Ejecución (`~/emulador_flashrom`)**: Es el entorno de trabajo activo. Se utiliza para compilar el binario, ejecutar las pruebas y generar archivos de datos temporales.

*Recomendación*: Si realizas cambios en el código en la carpeta de home para probarlos, asegúrate de copiarlos de vuelta a la carpeta del repositorio una vez validados para no perderlos.

---

## 📋 Arquitectura del Sistema de Emulación

El entorno consta de tres partes principales que trabajan en conjunto:

```
[ Flashrom (Host CLI) ]
          │ (Mediante libusb parchado con ANDROID_USB_FD o enlace PTY serie)
          ▼
[ Puente Socket UNIX / Pseudo-terminal PTY ]
          │ (Framing de paquetes LSB o traducción de protocolos de hardware)
          ▼
[ Emulador C++ (emulador_flashrom) ]
          │ (Interacción SPI con el chip de memoria virtual)
          ▼
[ Modelo de Memoria (SpiFlashGD25Q80) ]
```

1. **El Parche de `libusb`**: intercepta las llamadas de bus del sistema real. Cuando la variable de entorno `ANDROID_USB_FD` está presente, simula descriptores, salta controles de kernel invasivos (`ioctl`s de reclamación de interfaz) e implementa una cola de llamadas asíncronas en diferido para evitar deadlocks de callbacks.
2. **El Emulador C++**: implementa la lógica específica de los microcontroladores de los programadores.
3. **El Chip SPI Virtual**: simula los comandos estándar JEDEC (`RDID` `9Fh`, `REMS` `90h`), control de chip select (`CS`), y lectura/escritura de memoria.

---

## 🛠️ Requisitos de Compilación

Asegúrate de contar con las siguientes herramientas de desarrollo en tu sistema:
```bash
sudo apt-get update
sudo apt-get install -y build-essential meson ninja-build libpci-dev libudev-dev pkg-config curl unzip
```

---

## 🚀 Paso 1: Compilar el Emulador C++

El emulador contiene los tres controladores en un único binario multiprotocolo. Para compilarlo:

```bash
cd /home/danielpdiamon/emulador_flashrom
make clean && make
```
Esto generará el archivo ejecutable `emulador_flashrom`.

---

## 📦 Paso 2: Compilar e Instalar `libusb` y `flashrom` Locales

Para no ensuciar las bibliotecas del sistema principal, toda la instalación local se compila y aloja dentro del directorio `/home/danielpdiamon/emulador_flashrom/local_root`.

1. **Compilar `libusb` con el parche**:
   El script descarga la versión adecuada de `libusb`, le aplica las modificaciones requeridas de simulación de descriptor y temporización de callbacks, y la compila:
   ```bash
   bash build_libusb_local.sh
   ```

2. **Compilar `flashrom` contra la biblioteca parchada**:
   El script descarga la última versión de `flashrom`, la enlaza estáticamente contra nuestro `libusb` modificado y la instala en `local_root/sbin`:
   ```bash
   bash build_flashrom_local.sh
   ```

3. **Script de Ejecución Wrapper (`flashrom_local.sh`)**:
   Para ejecutar `flashrom` forzando el uso de las bibliotecas dinámicas de nuestro directorio local en lugar de las del sistema, utiliza el wrapper provisto:
   ```bash
   ./flashrom_local.sh -p <programador>
   ```

---

## 🔌 Paso 3: Pruebas de Detección por Dispositivo

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

## 📊 Salida de Detección Exitosa de Memoria (GD25Q80)

Cuando ejecutes cualquiera de los modos anteriores, la salida de `flashrom` reportará la correcta inicialización del programador, la interrogación de los IDs de fabricante y capacidad y la identificación final de la memoria virtualizada:

```text
Initializing programmer...
...
Probing for Generic unknown SPI chip (RDID), 0 kB: compare_id: id1 0xc8, id2 0x4014
Found GigaDevice flash chip "GD25Q80(B)" (1024 kB, SPI).
This flash part has status UNTESTED for operations: WP
No operations were specified.
Bus Pirate/Serprog/CH341A/SPIDriver shutdown completed.
```

¡El entorno local de emulación y validación está listo y comprobado al 100%!

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
