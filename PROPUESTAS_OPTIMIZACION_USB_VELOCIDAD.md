# Propuestas de Optimización: Máximo Rendimiento, Latencia Cero y Memoria Directa

Este documento detalla las soluciones de arquitectura para eliminar los cuellos de botella de velocidad, latencia, pausas de recolección de basura (*Garbage Collector*) y uso de memoria en **Flash-EEPROM-Tool**.

Cubre tanto la capa de **transporte de comunicación USB** (para programadores `serprog`, Arduino, CH340, FTDI) como la capa de **gestión de memoria y archivos** (lectura/guardado de volcados, visor hexadecimal y exportación).

---

## Comparativa de Arquitectura: Actual vs. Optimizada

```
1. ARQUITECTURA ACTUAL (Híbrida con PTY y Arrays en Java Heap):
   • USB: Hardware ↔ Kernel ↔ UsbRequest (byte[]) ↔ JNI / poll() ↔ Master PTY ↔ /dev/pts/X ↔ flashrom (C)
   • Archivos: Disco ↔ FileInputStream / readAllBytes() ↔ Heap Java (byte[]) ↔ RecyclerView
   [Riesgos: Copias redundantes de memoria, pausas de GC, saturación de PTY y OutOfMemoryError en chips > 16MB]

2. PROPUESTA 1 (Búferes Directos DMA y Mapeo en Memoria mmap):
   • USB: Hardware ↔ UsbRequest (Direct ByteBuffer) ↔ GetDirectBufferAddress() ↔ DMA directo
   • Archivos: Disco ↔ MappedByteBuffer (mmap) ↔ Lectura paginada bajo demanda en RAM virtual
   [Mejora: Cero copias en memoria, consumo de RAM plano (~0 MB), sin pausas de GC ni errores OOM]

3. PROPUESTA 2 (Socket TCP Loopback Local en vez de PTY):
   • USB: Hardware ↔ UsbRequest / Bridge ↔ Socket TCP Local (127.0.0.1:9999) ↔ flashrom (-p serprog:ip=127.0.0.1:9999)
   • Naturaleza: Canal en memoria RAM interna del procesador (sin internet/Wi-Fi)
   [Mejora: Búferes del kernel de cientos de KB, control de flujo nativo, sin restricciones de terminales tty]

4. PROPUESTA 3 (Puente 100% Nativo en C++):
   • USB: Descriptor USB (FD) ↔ Thread C++ (libusb / ioctl / epoll) ↔ Socket / PTY ↔ flashrom (C)
   [Mejora: JVM 100% excluida del flujo de datos en tiempo real, latencia de microsegundos]
```

---

## Solución 1: Memoria Directa (DMA) y Eliminación de Arrays `byte[]`

### El Porqué
El uso de arrays primitivos `byte[]` en el heap de Java es el principal causante de degradación en operaciones continuas de flasheo y manejo de volcados:
1. **Copias redundantes:** Cada vez que un array `byte[]` cruza hacia el kernel o hacia C/JNI, la máquina virtual ART debe fijar o duplicar la memoria.
2. **Pausas del Garbage Collector (*Stop-The-World*):** En chips de 4MB, 8MB, 16MB o 32MB, crear y destruir arrays en bucles continuos dispara el GC. Una pausa de tan solo 50 ms puede provocar que el Arduino/serprog agote su timeout de 1000 ms y pierda la sincronía del protocolo SPI.
3. **Riesgo de `OutOfMemoryError`:** Cargar una imagen de BIOS completa en la RAM de Java para visualizarla satura la memoria en dispositivos de gama media o baja.

---

### Cómo Implementarlo en el Proyecto

#### A. En la Comunicación USB (`PtyBridge.java`): DMA Directo
Sustituir los arrays temporales por búferes nativos fuera del heap (*Off-Heap*):

```java
// Asignación de memoria física contigua directa (fuera del heap de Java)
private final ByteBuffer usbReadBuffer = ByteBuffer.allocateDirect(BUFFER_SIZE);
private final ByteBuffer usbWriteBuffer = ByteBuffer.allocateDirect(BUFFER_SIZE);

// En el hilo de lectura USB:
usbRequest.initialize(usbConnection, endpointIn);
// queue() con ByteBuffer directo instruye al hardware USB a usar DMA
usbRequest.queue(usbReadBuffer);
```

En `native-lib.cpp`, se accede directamente al puntero físico sin copias:
```cpp
extern "C" JNIEXPORT jint JNICALL
Java_com_diamon_curso_core_PtyBridge_writeFdDirect(
    JNIEnv *env, jclass clazz, jint fd, jobject directBuffer, jint len) {
    
    void *bufAddress = env->GetDirectBufferAddress(directBuffer);
    if (!bufAddress || len <= 0) return -1;

    return (jint) write(fd, bufAddress, (size_t) len);
}
```

#### B. En el Visor Hexadecimal (`HexViewerActivity.java`): Mapeo Virtual con `mmap`
Actualmente se cargan todos los bytes a la memoria con:
`data = java.nio.file.Files.readAllBytes(dataFile.toPath());`

**Reemplazo con `MappedByteBuffer` (Mapeo de archivos en memoria):**
```java
// 1. Mapear el archivo completo mediante la llamada del sistema mmap() de Linux
FileChannel channel = new RandomAccessFile(dataFile, "r").getChannel();
MappedByteBuffer mappedBuffer = channel.map(FileChannel.MapMode.READ_ONLY, 0, channel.size());

// 2. En el adaptador del RecyclerView: leer bajo demanda solo los 16 bytes visibles
@Override
public void onBindViewHolder(@NonNull HexViewHolder holder, int position) {
    long rowOffset = (long) position * 16;
    byte[] row = new byte[16];
    
    synchronized (mappedBuffer) {
        mappedBuffer.position((int) rowOffset);
        int toRead = (int) Math.min(16L, channel.size() - rowOffset);
        mappedBuffer.get(row, 0, toRead);
    }
    
    // Formatear y renderizar únicamente la fila en pantalla
    holder.bindRow(rowOffset, row);
}
```
* **Ventaja:** Apertura instantánea (0 ms) sin importar si el archivo pesa 16MB o 128MB. El consumo de RAM pasa de decenas de megabytes a prácticamente **cero**, ya que el kernel de Linux solo pagina en RAM los bytes que se están dibujando en pantalla.

#### C. En la Exportación de Archivos (`FileManager.java`): Transferencia Zero-Copy
En lugar de bucles `while ((read = in.read(buffer)) != -1)` con arrays de 8 KB:

```java
// Transferencia directa a nivel de bloques del kernel mediante syscall sendfile()
try (FileChannel inChannel = new FileInputStream(sourceFile).getChannel();
     FileChannel outChannel = ((FileOutputStream) outStream).getChannel()) {
    
    inChannel.transferTo(0, inChannel.size(), outChannel);
}
```
* **Ventaja:** El archivo se copia directamente entre descriptores de almacenamiento a nivel de kernel, sin transferir un solo byte por el espacio de usuario ni por la máquina virtual de Java.

---

## Solución 2: Reemplazo del Puente PTY por un Socket TCP Local (`serprog:ip=127.0.0.1:puerto`)

### El Porqué
* **¿Es una conexión de red real?** No. Ocurre dentro de la memoria RAM del propio dispositivo a través de la interfaz loopback (`127.0.0.1` / `localhost`). No requiere conexión Wi-Fi, datos móviles ni permisos especiales de red.
* **El problema del PTY (`/dev/pts/*`):** Los pseudoterminales fueron creados para consolas interactivas de texto y tienen colas de búfer reducidas en el kernel (generalmente 4 KB). Con transferencias binarias rápidas, el PTY se llena y bloquea los hilos de escritura.
* **Soporte oficial en flashrom:** `flashrom` incluye de fábrica el modo de red para serprog:
  `flashrom -p serprog:ip=127.0.0.1:puerto`
  La pila TCP interna de Linux maneja búferes de cientos de kilobytes, control de flujo y contrapresión (*backpressure*) automática, eliminando bloqueos sin depender de los permisos de `/dev/pts`.

### Cómo Implementarlo en el Proyecto

#### 1. Instanciar un servidor local en Java
```java
// En UsbController / PtyBridge:
ServerSocket serverSocket = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"));
int localPort = serverSocket.getLocalPort();

// Aceptar la conexión de flashrom:
Socket clientSocket = serverSocket.accept();
clientSocket.setTcpNoDelay(true); // Desactivar Nagle para latencia ultra-baja
InputStream socketIn = clientSocket.getInputStream();
OutputStream socketOut = clientSocket.getOutputStream();
```

#### 2. Ajustar el parámetro de flashrom en `UsbController.java`
```java
public String buildProgrammerParam(String programmer, int localPort) {
    if ("serprog".equals(programmer)) {
        return "serprog:ip=127.0.0.1:" + localPort;
    }
    return programmer;
}
```

---

## Solución 3: Puente 100% Nativo en C/C++ (Cero Java en el Flujo de Datos)

### El Porqué
En programadores serie (`serprog`), los datos actualmente realizan este ciclo:
`Hardware ↔ Kernel ↔ UsbRequest (Java) ↔ JNI ↔ Master PTY / Socket ↔ flashrom (C)`

Cada cambio de contexto entre Java y el código nativo genera micro-demoras. Moviendo el bucle de transporte a C++ en `native-lib.cpp`, Java solo solicita el permiso USB y entrega el File Descriptor (`usbConnection.getFileDescriptor()`). A partir de ahí, un hilo worker en C++ se encarga del intercambio directo.

### Cómo Implementarlo en el Proyecto

#### 1. Arrancar el puente nativo pasando los descriptores
```java
int usbFd = currentConnection.getFileDescriptor();
int commFd = this.masterFd; // o el descriptor del socket local

startNativeUsbBridge(usbFd, commFd, endpointIn.getAddress(), endpointOut.getAddress());
```

#### 2. Bucle multiplexado en `native-lib.cpp`
```cpp
#include <poll.h>
#include <linux/usbdevice_fs.h>
#include <sys/ioctl.h>

void* nativeBridgeThread(void* arg) {
    BridgeArgs* args = (BridgeArgs*) arg;
    struct pollfd fds[2];
    fds[0].fd = args->commFd; // Descriptor PTY o Socket TCP
    fds[0].events = POLLIN;
    fds[1].fd = args->usbFd;  // Descriptor nativo USB
    fds[1].events = POLLIN;

    uint8_t buffer[4096];

    while (args->running) {
        int ret = poll(fds, 2, 50); // Espera activa por interrupciones
        if (ret <= 0) continue;

        // 1. flashrom -> USB
        if (fds[0].revents & POLLIN) {
            ssize_t n = read(args->commFd, buffer, sizeof(buffer));
            if (n > 0) {
                struct usbdevfs_bulktransfer bulk;
                bulk.ep = args->outEndpoint;
                bulk.len = n;
                bulk.timeout = 100;
                bulk.data = buffer;
                ioctl(args->usbFd, USBDEVFS_BULK, &bulk);
            }
        }

        // 2. USB -> flashrom
        if (fds[1].revents & POLLIN) {
            struct usbdevfs_bulktransfer bulk;
            bulk.ep = args->inEndpoint;
            bulk.len = sizeof(buffer);
            bulk.timeout = 100;
            bulk.data = buffer;
            if (ioctl(args->usbFd, USBDEVFS_BULK, &bulk) >= 0 && bulk.len > 0) {
                write(args->commFd, buffer, bulk.len);
            }
        }
    }
    return nullptr;
}
```

---

## Estrategia Dual: Mantener PTY en C++ y Dar la Opción al Usuario en la UI

Si deseas conservar el soporte de **Pseudoterminal (PTY)** pero potenciado con C++ y además ofrecer el nuevo modo de **Socket TCP Local**, es completamente viable y altamente recomendado:

### La Belleza de POSIX: El mismo motor C++ sirve para ambos modos
En el kernel de Linux, **"todo es un descriptor de archivo" (`int fd`)**:
* El extremo maestro del PTY (`masterFd`) es un descriptor de archivo.
* El socket TCP local (`clientSocketFd`) es exactamente otro descriptor de archivo.

La función nativa en C++ `nativeBridgeThread()` **no necesita duplicarse ni cambiar una sola línea**:
* Si el usuario elige modo Socket: `args->commFd = socketFd;`
* Si el usuario elige modo PTY: `args->commFd = masterPtyFd;`

En ambos casos, las llamadas `poll()`, `read()` y `write()` en C++ se comportan con idéntica eficiencia y latencia de microsegundos.

---

### Cómo Implementar la Elección del Usuario en la UI

#### 1. Configuración en la Interfaz (`ProgrammerSettingsActivity.java`)
En la pantalla de ajustes de programador, se añade un selector (RadioGroup o Switch) para programadores de tipo serial:

```java
// Opciones en la UI:
// [●] Socket TCP Local (127.0.0.1) - Recomendado (Mayor velocidad y estabilidad)
// [○] Pseudoterminal PTY (/dev/pts) - Modo Clásico (Motor C++ optimizado)

SharedPreferences prefs = getSharedPreferences("flashrom_prefs", MODE_PRIVATE);
prefs.edit().putString("serprog_transport_mode", isSocket ? "socket" : "pty").apply();
```

#### 2. Despacho Dinámico en `UsbController.java`
Al iniciar una operación con un dispositivo serial (`serprog`, `buspirate_spi`, `spidriver`), se consulta la preferencia guardada:

```java
String transportMode = prefs.getString("serprog_transport_mode", "socket"); // "socket" por defecto

if ("socket".equals(transportMode)) {
    // Modo Socket TCP: Inicia servidor local y pasa parámetro IP a flashrom
    int port = startLocalSocketBridge(device);
    return "serprog:ip=127.0.0.1:" + port;
} else {
    // Modo PTY: Inicia el PTY optimizado con motor C++ y pasa parámetro dev
    String ptyPath = startOptimizedPtyBridge(device);
    return "serprog:dev=" + ptyPath + ":" + baudRate;
}
```

### Ventajas de la Estrategia Dual:
1. **Cero riesgo de regresión:** No se pierde la compatibilidad histórica del PTY, pero ahora funciona sin los bloqueos de Java gracias al motor en C++.
2. **Control técnico total:** Si en algún dispositivo o clon de Arduino el PTY presenta ventajas de sincronización, el usuario puede alternarlo con un toque.
3. **Transparencia y satisfacción del usuario:** El usuario avanzado puede experimentar y comparar la velocidad de ambos métodos en tiempo real.

---

## Sinergia de Soluciones: ¿Se pueden aplicar las tres a la vez?

**Sí, encajan como piezas de un único sistema modular:**

```
┌────────────────────────────────────────────────────────────────────────┐
│                   LA ARQUITECTURA INTEGRADA DUAL                      │
├────────────────────────────────────────────────────────────────────────┤
│ 1. Selección de Transporte en la UI:                                   │
│    • Opción A (Socket): flashrom -p serprog:ip=127.0.0.1:puerto        │
│    • Opción B (PTY):    flashrom -p serprog:dev=/dev/pts/X:baud        │
│                               ▲                                        │
│                               │ Flujo bidireccional en RAM             │
│                               ▼                                        │
│ 2. Un único hilo nativo en C++ mueve los datos:                        │
│    commFd (Socket o PTY) ◄───[C++ / poll]───► USB Hardware (FD)       │
│    (Cero pausas de Java en la comunicación serie)                      │
│                                                                        │
│ 3. La memoria de archivos y visor vuela:                               │
│    • Visor Hexadecimal: usa mmap (MappedByteBuffer)                    │
│    • Exportar a Descargas: usa sendfile (transferTo)                   │
│    (Cero arrays byte[], cero OutOfMemoryError)                         │
└────────────────────────────────────────────────────────────────────────┘
```

---

## Resumen de Aplicabilidad: ¿Qué aplica a cada parte?

| Componente | ¿Aplica? | Técnica Recomendada |
| :--- | :--- | :--- |
| **USB Nativo `libusb` (CH341A, ST-LINK, J-Link, FT2232H)** | Ya va en C nativo a máxima velocidad | Usar **Foreground Service + WakeLock** para evitar que Android suspenda la CPU o corte la energía USB. |
| **USB Serie (`serprog` / Arduino / CH340)** | Sí (crítico) | Ofrecer en UI **Socket Local `127.0.0.1`** (por defecto) y **PTY optimizado**, ambos propulsados por el motor en C++ nativo. |
| **Visor Hexadecimal (`HexViewerActivity`)** | Sí (crítico) | Aplicar **Mapeo `mmap` (`MappedByteBuffer`)** para abrir ROMs gigantes en 0 ms con 0 MB de consumo de RAM. |
| **Exportación a Descargas (`FileManager`)** | Sí | Aplicar **`FileChannel.transferTo()`** (syscall `sendfile`). |

---

## Hoja de Ruta de Implementación

1. **Paso 1 (Inmediato - Sin tocar USB):**
   - Implementar `MappedByteBuffer` en `HexViewerActivity` para que el visor hexagonal vuele sin importar el tamaño del archivo.
   - Usar `FileChannel.transferTo()` en `FileManager` para exportaciones zero-copy.
2. **Paso 2 (Motor C++ para PTY):**
   - Mover el bucle de I/O de `PtyBridge.java` a la función `nativeBridgeThread()` en `native-lib.cpp`, eliminando las pausas de Java en el PTY actual.
3. **Paso 3 (Añadir Socket Local y Selector en UI):**
   - Añadir la opción de Socket TCP Local conectada a la misma función `nativeBridgeThread()`.
   - Incorporar el selector en `ProgrammerSettingsActivity` para que el usuario decida libremente entre Socket y PTY.
