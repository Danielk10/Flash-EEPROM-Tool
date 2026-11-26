# Guía Técnica: Arquitectura del Parche PTY USB y Puente Serie Nativo

> **Proyecto:** Flash SPI Tool  
> **Versión vigente:** `v1.9.27` (Código de Versión `113`)  
> **Parche introducido en:** `v1.9.2` (Código de Versión `88`)  
> **Área:** Capa de Transporte de Bajo Nivel (JNI C++ / Kernel Linux / Android USB Host)  
> **Dispositivos Afectados:** Programadores basados en interfaz serie USB (**Serprog / Arduino**, **Bus Pirate**, **SPIDriver**, microcontroladores FTDI/CH340/CP2102/PL2303).

---

## 1. Introducción y Contexto

En sistemas operativos de escritorio (Linux / macOS), herramientas de silicio como **flashrom** se comunican directamente con dispositivos serie a través de nodos en el sistema de archivos del kernel, tales como `/dev/ttyUSB0` o `/dev/ttyACM0`.

En **Android**, las aplicaciones de usuario no tienen permisos de superusuario (root) ni acceso de lectura/escritura sobre el subsistema `/dev/bus/usb/` o sobre los nodos `/dev/tty*` debido a la política de sandboxing de **SELinux**. La única forma autorizada por el sistema operativo para interactuar con un periférico USB es a través de la API oficial **Android USB Host** (`android.hardware.usb.UsbManager` y `UsbDeviceConnection`), la cual entrega al proceso un **File Descriptor (FD)** del dispositivo o streams de lectura/escritura a nivel de usuario.

Para que un binario nativo de consola compilado en C (`flashrom`) pueda comunicarse con el hardware USB serie sin requerir controladores en el kernel de Android ni acceso root, Flash SPI Tool implementa una **arquitectura de puente virtual (PTY Bridge / Socket TCP)** de altísimo rendimiento.

---

## 2. El Problema Crítico de la "Line Discipline" en Terminales PTY

Durante el desarrollo inicial del puente PTY, las lecturas masivas de memoria SPI (volcados superiores a 1 MB) fallaban sistemáticamente con errores como:

```text
Reading flash... Error: invalid response 0x01 from device (to command 0x13)
Reading flash... Error: invalid response 0xA0 from device (to command 0x13)
```

Al analizar la traza a nivel de bytes, se descubrió que el flujo de datos binario se desalineaba o perdía bytes específicos de forma silenciosa.

### Causa Raíz: Reversión Automática a Modo "Cooked" en Linux
En el diseño estándar de pseudo-terminales de Linux:
1. La aplicación crea un par PTY maestro/esclavo con `posix_openpt()`, `grantpt()`, `unlockpt()`.
2. La aplicación aplica `cfmakeraw(&tio)` sobre el master para que opere como un túnel binario transparente.
3. El esclavo (`/dev/pts/N`) se entrega al proceso hijo (`flashrom -p serprog:dev=/dev/pts/N:115200`).
4. **El Comportamiento del Kernel Linux:** Si el proceso padre o de inicialización abre y luego cierra el FD esclavo antes de que `flashrom` lo abra, **en el instante en que el número de descriptores abiertos hacia el esclavo llega a cero, el kernel de Linux destruye la configuración y restablece el terminal a su estado original por defecto: Modo "Cooked" (Modo Texto Canónico)**.

### Consecuencias del Modo Cooked en Protocolos SPI Binarios
Cuando `flashrom` abría `/dev/pts/N` en modo cooked:
- El byte **`0x13` (XOFF)** en el protocolo Serprog es el comando fundamental `S_CMD_O_SPIOP` (operación SPI en bus). El kernel lo interceptaba como señal de control de flujo por software y pausaba la transmisión o se comía el byte.
- El byte **`0x11` (XON)** se interpretaba como reactivación de terminal y se descartaba.
- El byte **`0x03`** provocaba la generación de la señal `SIGINT`.
- Los retornos de carro **`0x0D` (`\r`)** se mutaban automáticamente a saltos de línea **`0x0A` (`\n`)**.

---

## 3. La Solución Definitiva: El "Dummy Slave FD"

Para garantizar que el kernel de Linux jamás revierta el terminal esclavo a modo texto, se implementó la técnica del **Dummy Slave FD** en [`app/src/main/cpp/native-lib.cpp`](file:///home/danielpdiamon/Flash-EEPROM-Tool/app/src/main/cpp/native-lib.cpp):

```cpp
// native-lib.cpp - Fragmento de createPty()
int masterFd = posix_openpt(O_RDWR | O_NOCTTY);
grantpt(masterFd);
unlockpt(masterFd);
const char *slavePath = ptsname(masterFd);

// 1. Configurar master en modo RAW binario puro
struct termios tio;
if (tcgetattr(masterFd, &tio) == 0) {
    cfmakeraw(&tio);
    tcsetattr(masterFd, TCSANOW, &tio);
}

// 2. ABRIR EL ESCLAVO Y CONFIGURARLO EN RAW
int slaveFd = open(slavePath, O_RDWR | O_NOCTTY);
if (slaveFd >= 0) {
    if (tcgetattr(slaveFd, &tio) == 0) {
        cfmakeraw(&tio);
        tcsetattr(slaveFd, TCSANOW, &tio);
    }
}

// 3. RETORNAR EL SLAVE FD A JAVA COMO "DUMMY SLAVE FD"
// Al mantener slaveFd abierto durante toda la vida útil de la sesión,
// el contador de referencias del kernel para el esclavo NUNCA llega a 0.
// Esto BLINDA el estado RAW e impide que el kernel vuelva a modo cooked.
```

En la capa Java ([`PtyBridge.java`](file:///home/danielpdiamon/Flash-EEPROM-Tool/app/src/main/java/com/diamon/curso/core/PtyBridge.java)):
- El descriptor `slaveFd` es almacenado en la variable `dummySlaveFd`.
- Permanece abierto mientras `flashrom` ejecuta sus operaciones de lectura, escritura o sondeo.
- Únicamente se cierra cuando el proceso nativo ha finalizado por completo.

**Resultado:** Inmunidad absoluta contra la corrupción de datos binarios en todos los programadores serie (`serprog`, `buspirate_spi`, `spidriver`).

---

## 4. Prevención de Desbordamiento de Búfer UART (Hardware Overrun en CH340)

Los clones de Arduino basados en el conversor USB-Serial **WCH CH340G** poseen un búfer FIFO interno de hardware muy reducido (128 bytes). 

Cuando `flashrom` solicitaba leer megabytes completos de una memoria flash, el microcontrolador transmitía ráfagas ininterrumpidas a máxima velocidad. Si el hilo de Android sufría una breve latencia de programación de CPU, el búfer del CH340 colapsaba y descartaba paquetes de datos.

### Corrección en el Firmware (`serprog_arduino_uno_ch340g.ino`)
Se implementaron los comandos de consulta de capacidades:
- **`S_CMD_Q_WRNMAXLEN` (`0x08`)**: Limita la ráfaga de escritura a 32 bytes.
- **`S_CMD_Q_RDNMAXLEN` (`0x11`)**: Limita la ráfaga de lectura a 64 bytes.

Al reportar estos límites, `flashrom` fragmenta automáticamente las lecturas en bloques seguros de 64 bytes, asegurando que el chip CH340G nunca sature su búfer FIFO.

---

## 5. El Motor Nativo Multihilo C++ (`usb_bridge.cpp`) en v1.9.2

En versiones anteriores, el puente entre el PTY y el puerto USB se ejecutaba en la máquina virtual Java usando hilos y llamadas a `UsbRequest`. En dispositivos con memoria ajustada o bajo cargas intensas de lectura (ej. memorias SPI de 16 MB o 32 MB), el **Garbage Collector (GC) de Android** introducía pausas que superaban el tiempo de espera del microcontrolador (1000 ms), provocando timeouts y cancelaciones de proceso.

En **Flash SPI Tool v1.9.2**, la capa de transporte fue completamente reescrita en **C++ nativo (`usb_bridge.cpp`)**, eliminando por completo a la JVM del flujo de datos en tiempo real:

```
┌────────────────────────────────────────────────────────────────────────┐
│              ARQUITECTURA DE TRANSPORTE NATURA EN C++                  │
├────────────────────────────────────────────────────────────────────────┤
│                                                                        │
│   ┌───────────────────┐               ┌──────────────────────────┐    │
│   │     flashrom      │               │   USB Hardware OTG       │    │
│   │   (Proceso Hijo)  │               │ (Arduino / Bus Pirate)   │    │
│   └─────────▲─────────┘               └────────────▲─────────────┘    │
│             │                                      │                  │
│   [/dev/pts/N o 127.0.0.1]             [Endpoint Bulk IN / OUT]       │
│             │                                      │                  │
│             ▼                                      ▼                  │
│   ┌──────────────────────────────────────────────────────────────┐    │
│   │                   usb_bridge.cpp (C++)                       │    │
│   │  • Hilo 1 (Comm → USB): poll(commFd) ──► ioctl(USBDEVFS_BULK)│    │
│   │  • Hilo 2 (USB → Comm): ioctl(BULK)   ──► write(commFd)      │    │
│   │  • Prioridad de Hilo Elevada: setpriority(PRIO_PROCESS, -8)  │    │
│   │  • Filtro de cabecera de 2 bytes para adaptadores FTDI       │    │
│   │  • Cero asignaciones en Java Heap / Cero pausas de GC        │    │
│   └──────────────────────────────────────────────────────────────┘    │
│                                                                        │
└────────────────────────────────────────────────────────────────────────┘
```

### Características Técnicas del Motor C++:
1. **Llamadas Directas al Kernel:** Utiliza la syscall `ioctl(usbFd, USBDEVFS_BULK, &bulk)` sobre el descriptor de archivo nativo entregado por Android.
2. **Prioridad en Tiempo Real:** Los hilos POSIX ejecutan `setpriority(PRIO_PROCESS, tid, -8)` (equivalente a `THREAD_PRIORITY_URGENT_DISPLAY`), asegurando que el planificador del kernel no suspenda el flujo de datos.
3. **Lectura por Paquetes Unitarios (`wMaxPacketSize`):** Para evitar que el kernel de Linux descarte paquetes parciales en timeouts de `USBDEVFS_BULK`, el hilo lee exactamente un paquete por llamada ioctl (típicamente 64 bytes para Full Speed).
4. **Manejo Especial de Chips FTDI:** Los chips FT232R/FT2232 anteponen 2 bytes de estado (`Modem Status`) a cada paquete recibido. El motor en C++ recorta automáticamente estos 2 bytes en memoria antes de transferir la carga útil a `flashrom`.

---

## 6. Arquitectura Dual de Selección de Transporte: Socket TCP vs PTY

Flash SPI Tool v1.9.2 permite al usuario alternar en los ajustes de la aplicación ([`ProgrammerSettingsActivity.java`](file:///home/danielpdiamon/Flash-EEPROM-Tool/app/src/main/java/com/diamon/curso/ui/activities/ProgrammerSettingsActivity.java)) entre dos modos de transporte para el protocolo Serprog:

| Modo de Transporte | Invocación de flashrom | Mecanismo Interno | Escenario de Uso Recomendado |
|---|---|---|---|
| **Socket TCP Loopback** *(Recomendado por defecto)* | `-p serprog:ip=127.0.0.1:PUERTO` | Servidor TCP local en `127.0.0.1` creado con `TCP_NODELAY`. Los bytes viajan a través de la pila de red local del kernel sin pasar por emulación de terminal. | **Máxima velocidad y estabilidad.** Elimina por completo las capas de emulación de terminal virtual tty. |
| **Pseudo-Terminal PTY** | `-p serprog:dev=/dev/pts/N:115200` | Canal PTY maestro/esclavo con `cfmakeraw` y Dummy Slave FD activo. | **Compatibilidad universal.** Requerido para herramientas que solo aceptan rutas dev o programadores como Bus Pirate y SPIDriver. |

---

## 7. Balizas de Sincronización (Beacon `0xAA 0x55`) y Resincronización Activa

Al conectar un microcontrolador Arduino por USB, la activación de las líneas DTR/RTS provoca un reinicio del microprocesador ATmega328P. Durante el reinicio, el bootloader puede emitir caracteres espurios.

Para mitigar este problema:
1. El firmware de Arduino emite una baliza de sincronización de dos bytes: **`0xAA 0x55`** cuando está listo para recibir comandos.
2. Tanto `PtyBridge.java` como `usb_bridge.cpp` consumen y descartan todo residuo serial anterior a la recepción de dicha baliza antes de conectar a `flashrom`.
3. Si durante la operación el Arduino detecta un timeout de bus SPI, emite un byte de error `S_NAK` acompañado de la baliza `0xAA 0x55`. El puente intercepta la baliza e inyecta inmediatamente el comando `SYNCNOP` (`0x10`) para devolver la comunicación a fase sin requerir reconectar el cable OTG.

---

## 8. Verificación y Validación de Protocolos

La arquitectura de este puente ha sido validada exitosamente con la suite de pruebas local ([`emulador_flashrom/`](file:///home/danielpdiamon/Flash-EEPROM-Tool/emulador_flashrom/)):

1. **Serprog / Arduino Uno (CH340G):** Volcado y flasheo de memorias Winbond W25Q128 (16 MB) completados sin errores de desalineación ni timeouts.
2. **Bus Pirate (v3 / v4):** Conmutación a modo binario SPI (`BBIO1` / `SPI1`) verificada a través del puente PTY con Dummy Slave FD.
3. **SPIDriver:** Negociación a 460.800 bps con eco de comando validado de forma continua y lecturas íntegras en chips GD25Q80.
