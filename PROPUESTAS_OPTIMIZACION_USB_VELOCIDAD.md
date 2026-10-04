# Propuestas de Optimización: Máximo Rendimiento y Latencia Cero en Comunicación USB

Este documento detalla tres soluciones de arquitectura para eliminar los cuellos de botella de velocidad, latencia y pausas de recolección de basura (*Garbage Collector*) en **Flash-EEPROM-Tool**, especialmente durante operaciones con programadores basados en serie (`serprog`, Arduino, CH340, FTDI).

---

## Comparativa de Arquitectura: Actual vs. Propuestas

```
1. ARQUITECTURA ACTUAL (Híbrida con PTY y Java):
   Hardware USB ↔ Kernel Linux ↔ UsbRequest (Java) ↔ JNI / poll() ↔ Master PTY ↔ /dev/pts/X ↔ flashrom (C)
   [Riesgo: Múltiples cambios de contexto, copias de memoria en heap Java, pausas de GC]

2. PROPUESTA 1 (Búferes Directos DMA):
   Hardware USB ↔ Kernel Linux ↔ UsbRequest (Direct ByteBuffer) ↔ GetDirectBufferAddress() ↔ Master PTY ↔ flashrom (C)
   [Mejora: Cero copias entre kernel y JVM, DMA directo, sin fragmentación de memoria]

3. PROPUESTA 2 (Socket TCP Loopback en vez de PTY):
   Hardware USB ↔ UsbRequest / Bridge ↔ Socket TCP Local (127.0.0.1:9999) ↔ flashrom (-p serprog:ip=127.0.0.1:9999)
   [Mejora: Mayor capacidad de búfer en kernel, sin restricciones tty/termios de Linux]

4. PROPUESTA 3 (Puente 100% Nativo en C++):
   Hardware USB (USB FD) ↔ Thread C++ (libusb / ioctl / epoll) ↔ Master PTY / Socket ↔ flashrom (C)
   [Mejora: JVM 100% fuera del flujo de datos, latencia de microsegundos, cero pausas]
```

---

## Solución 1: Optimización Inmediata con Búferes Directos DMA (`ByteBuffer.allocateDirect`)

### El Porqué
Actualmente, el flujo de datos en `PtyBridge.java` manipula arrays primitivos `byte[]` asignados en la memoria heap de Java. Cada vez que se transfieren bloques entre el sistema operativo y la JVM:
1. La máquina virtual debe duplicar (*memcopy*) o fijar (*pin*) los búferes entre el espacio de usuario de Android y el kernel.
2. Durante lecturas de chips pesados (4MB, 8MB, 16MB o más), la creación y reciclaje continuo de arrays en Java genera presión sobre el recolector de basura (*Garbage Collector*). Cuando el GC entra en pausa (*Stop-The-World*), la lectura USB se detiene momentáneamente, provocando que el firmware del programador (Arduino/serprog) supere su tiempo de espera de 1000ms y desincronice el protocolo SPI.

Al utilizar memoria directa (*Off-Heap Direct Memory*), el controlador USB del procesador realiza transferencias **DMA (Direct Memory Access)** directamente a la memoria física compartida con C/C++, alcanzando la eficiencia de streaming que utilizan herramientas masivas como EtchDroid.

### Cómo Implementarlo en el Proyecto

#### 1. En `PtyBridge.java`: Migrar a `ByteBuffer.allocateDirect`
Sustituir los arrays de bytes de los hilos de transmisión por búferes directos preasignados:

```java
// Asignar memoria nativa fija (fuera del heap del GC)
private final ByteBuffer usbReadBuffer = ByteBuffer.allocateDirect(BUFFER_SIZE);
private final ByteBuffer usbWriteBuffer = ByteBuffer.allocateDirect(BUFFER_SIZE);

// En el hilo de lectura USB:
usbRequest.initialize(usbConnection, endpointIn);
// queue() con Direct ByteBuffer pasa el puntero de memoria físico al kernel
usbRequest.queue(usbReadBuffer);
```

#### 2. En `native-lib.cpp`: Operar directamente sobre el puntero nativo
Añadir soporte en JNI para escribir y leer directamente del descriptor de archivo PTY sin pasar por arrays de Java intermedios:

```cpp
extern "C" JNIEXPORT jint JNICALL
Java_com_diamon_curso_core_PtyBridge_writeFdDirect(
    JNIEnv *env, jclass clazz, jint fd, jobject directBuffer, jint len) {
    
    // Obtener la dirección física de memoria sin copias de la JVM
    void *bufAddress = env->GetDirectBufferAddress(directBuffer);
    if (!bufAddress || len <= 0) return -1;

    ssize_t written = write(fd, bufAddress, (size_t) len);
    return (jint) written;
}
```

---

## Solución 2: Reemplazo del Puente PTY por un Socket TCP Local (`serprog:ip=127.0.0.1:puerto`)

### El Porqué
El mecanismo de terminal virtual pseudoterminal (`/dev/pts/*`) de Linux fue concebido para emular consolas interactivas de texto, no para transferencias binarias masivas y continuas de hardware:
1. **Límites de búfer del kernel:** Los búferes de tty en el kernel de Linux suelen tener un tamaño máximo restringido (habitualmente 4 KB). Si `flashrom` escribe una ráfaga más rápido de lo que el programador drena el USB, el PTY se satura inmediatamente y bloquea el hilo de escritura.
2. **Soporte nativo de red en flashrom:** `flashrom` soporta de manera nativa la comunicación con `serprog` a través de sockets de red mediante el parámetro `-p serprog:ip=127.0.0.1:puerto`.
3. **Mayor escalabilidad:** La pila de red loopback de Linux (`lo`) cuenta con búferes internos configurables de cientos de kilobytes, soporte óptimo de contrapresión (*backpressure*) y no depende de la gestión de permisos en `/dev/pts`.

### Cómo Implementarlo en el Proyecto

#### 1. Crear un servidor de Socket Local en Java o C++
En lugar de abrir descriptores PTY (`createPty()`), se instancia un `ServerSocket` en un puerto local efímero:

```java
// En UsbController / PtyBridge:
ServerSocket serverSocket = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"));
int localPort = serverSocket.getLocalPort();

// Aceptar la conexión entrante de flashrom:
Socket clientSocket = serverSocket.accept();
clientSocket.setTcpNoDelay(true); // Desactivar algoritmo de Nagle para latencia mínima
InputStream socketIn = clientSocket.getInputStream();
OutputStream socketOut = clientSocket.getOutputStream();
```

#### 2. Pasar el parámetro a `flashrom`
En `UsbController.java`, ajustar el constructor de parámetros para usar la sintaxis IP de serprog:

```java
public String buildProgrammerParam(String programmer, int localPort) {
    if ("serprog".equals(programmer)) {
        return "serprog:ip=127.0.0.1:" + localPort;
    }
    return programmer;
}
```

El puente ahora simplemente reenvía bytes entre el socket local y el dispositivo USB, eliminando los fallos asociados a `dummySlaveFd`, control de flujo de terminales y reseteos del PTY.

---

## Solución 3: Puente 100% Nativo en C/C++ (Cero Java en el Flujo de Datos)

### El Porqué
En la arquitectura actual, cada bloque de datos que entra o sale del microchip debe cruzar la frontera entre el entorno nativo y la máquina virtual Java:
- La máquina virtual ART de Android añade sobrecarga por cambio de contexto (*context switching*) y comprobaciones de límites en cada llamada JNI repetitiva.
- Si el hilo de Java se demora unos milisegundos debido a la actividad de la interfaz de usuario o servicios del sistema, la cola del programador se vacía o se satura.

Tras solicitar y obtener el permiso USB desde Android en Java, es posible extraer el descriptor de archivo nativo (`usbConnection.getFileDescriptor()`) y transferirle el control absoluto de la comunicación a un hilo POSIX nativo en C++.

### Cómo Implementarlo en el Proyecto

#### 1. Iniciar el puente desde Java pasando solo los descriptores
```java
int usbFd = currentConnection.getFileDescriptor();
int masterFd = this.masterFd; // o el FD del socket local

// Arrancar hilo nativo en segundo plano
startNativeUsbBridge(usbFd, masterFd, endpointIn.getAddress(), endpointOut.getAddress());
```

#### 2. Lógica del bucle de I/O en `native-lib.cpp`
En C++, se crea un hilo worker que utiliza la llamada del sistema `poll()` o `epoll()` para multiplexar el descriptor USB y el descriptor PTY/Socket:

```cpp
#include <poll.h>
#include <linux/usbdevice_fs.h>
#include <sys/ioctl.h>

void* nativeBridgeThread(void* arg) {
    BridgeArgs* args = (BridgeArgs*) arg;
    struct pollfd fds[2];
    fds[0].fd = args->ptyMasterFd;
    fds[0].events = POLLIN;
    fds[1].fd = args->usbFd;
    fds[1].events = POLLIN;

    uint8_t buffer[4096];

    while (args->running) {
        int ret = poll(fds, 2, 50); // Timeout de 50ms
        if (ret <= 0) continue;

        // 1. Datos desde flashrom hacia USB
        if (fds[0].revents & POLLIN) {
            ssize_t n = read(args->ptyMasterFd, buffer, sizeof(buffer));
            if (n > 0) {
                struct usbdevfs_bulktransfer bulk;
                bulk.ep = args->outEndpoint;
                bulk.len = n;
                bulk.timeout = 100;
                bulk.data = buffer;
                ioctl(args->usbFd, USBDEVFS_BULK, &bulk);
            }
        }

        // 2. Datos desde USB hacia flashrom
        if (fds[1].revents & POLLIN) {
            struct usbdevfs_bulktransfer bulk;
            bulk.ep = args->inEndpoint;
            bulk.len = sizeof(buffer);
            bulk.timeout = 100;
            bulk.data = buffer;
            if (ioctl(args->usbFd, USBDEVFS_BULK, &bulk) >= 0 && bulk.len > 0) {
                write(args->ptyMasterFd, buffer, bulk.len);
            }
        }
    }
    return nullptr;
}
```

### Beneficios Clave de la Solución 3:
1. **Latencia a nivel de microsegundos:** La transmisión ocurre en código de máquina compilado, igualando la velocidad de una máquina Linux de escritorio.
2. **Inmunidad absoluta al Garbage Collector:** La JVM puede pausar o gestionar vistas sin que la comunicación USB sufra el más mínimo retardo.
3. **Consumo mínimo de CPU y batería:** `poll()` duerme el hilo en el kernel hasta que hay datos disponibles por interrupción de hardware.

---

## Hoja de Ruta de Implementación Recomendada

1. **Fase 1 (Inmediata / Bajo impacto):** Implementar la **Solución 1** (`ByteBuffer.allocateDirect`) en `PtyBridge.java` y `native-lib.cpp`. Es compatible con la estructura actual y elimina las copias innecesarias de memoria.
2. **Fase 2 (Medio plazo / Mayor estabilidad):** Migrar de PTY a Socket TCP Local (**Solución 2**), desacoplándose de los problemas de asignación de terminales `/dev/pts`.
3. **Fase 3 (Largo plazo / Máximo rendimiento):** Consolidar el puente nativo en C++ (**Solución 3**), logrando paridad completa de rendimiento con herramientas de escritorio.
