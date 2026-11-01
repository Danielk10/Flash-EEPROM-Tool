# Guía Técnica: Parche Quirúrgico de Libusb en Android (Operación USB sin Root)

> **Proyecto:** Flash SPI Tool  
> **Versión:** `v1.9.2` (Código de Versión `88`)  
> **Área:** Biblioteca Nativa `libusb-1.0` / Subsistema Linux USBFS / Android NDK C++  
> **Herramientas Beneficiadas:** `flashrom`, `ftdi_eeprom`, programadores USB directos (CH341A, CH347, ST-LINK v2/v3, J-Link, FT2232H, Dediprog SF100/SF600).

> **Nota para el emulador local:** esta guía describe el parche de Android (`patch_libusb.py`). Para el emulador x86_64 se usa `emulador_flashrom/patch_libusb_local.py`; `flashrom_local.sh` ya incluye la `libusb` parcheada y `libftdi1` en `local_root/lib`. No mezclar esa librería con la de minipro en `~/native_test_root`.

---

## 1. El Problema Base: Arquitectura Linux vs. Sandbox SELinux en Android

En un sistema operativo Linux convencional, cuando una utilidad de consola como `flashrom` requiere interactuar con un periférico USB:
1. `libusb-1.0` realiza una consulta al kernel mediante el subsistema de eventos **Netlink** y escanea el árbol de dispositivos montado en `/dev/bus/usb/`.
2. El kernel devuelve la lista de buses y dispositivos accesibles.
3. El proceso abre el archivo de dispositivo correspondiente (`/dev/bus/usb/BBB/DDD`) con permisos estándar POSIX.

### El Bloqueo de Seguridad en Android
En **Android (desde Android 5.0 Lollipop y reforzado drásticamente en Android 10+)**:
- Las políticas estrictas de **SELinux** impiden a cualquier proceso de espacio de usuario no rooteado abrir o escanear `/dev/bus/usb/`.
- El socket Netlink del kernel rechaza las conexiones de escaneo USB provenientes del contexto de una aplicación (`untrusted_app`).
- **Consecuencia:** Cuando una versión sin modificar de `libusb-1.0` se inicializa mediante `libusb_init()`, la llamada a Netlink falla de inmediato. La librería asume que el subsistema USB del sistema operativo está roto y aborta la ejecución con el error fatal **`LIBUSB_ERROR_IO` (-1)**, cerrando la aplicación instantáneamente.

---

## 2. Antecedentes: El Parche Destructivo Anterior y por qué Fallaba

En iteraciones tempranas del proyecto, se intentó solucionar el problema interceptando `libusb_init()` y forzando un retorno exitoso (`return 0`) sin ejecutar el código de inicialización del backend:
```c
// PARCHE ANTIGUO (DEFECTUOSO):
int libusb_init(libusb_context **ctx) {
    if (ctx) *ctx = NULL; // Destrucción del puntero de contexto
    return 0; // Fingir éxito
}
```

### Por qué Causaba Cierres Inexplicables (Segmentation Faults):
Al anular el puntero del contexto (`*ctx = NULL`):
1. `libusb` jamás reservaba las estructuras internas de memoria (`struct libusb_context`).
2. Cuando `flashrom` llamaba a funciones posteriores (como `libusb_get_device_list()` o `libusb_open()`), la librería intentaba desreferenciar campos del contexto nulo.
3. El procesador generaba inmediatamente un fallo de página y el sistema operativo terminaba el proceso con una señal **SIGSEGV (Fallo de Segmentación)**.

---

## 3. El Parche Actual: Inicialización Fantasma y Cirugía Quirúrgica

En **Flash SPI Tool v1.9.2**, el parche a `libusb-1.0` (aplicado por [`patch_libusb.py`](file:///home/danielpdiamon/Flash-EEPROM-Tool/patch_libusb.py)) no anula la inicialización, sino que permite que `libusb` reserve su memoria, construya sus estructuras y prepare su entorno por completo. La intervención se realiza quirúrgicamente en cuatro puntos clave:

```
┌────────────────────────────────────────────────────────────────────────┐
│             CIRUGÍA QUIRÚRGICA EN LIBUSB-1.0 (ANDROID NO-ROOT)         │
├────────────────────────────────────────────────────────────────────────┤
│ 1. core.c: Ignorar fallo Netlink ────► if (r) { r = 0; }               │
│    (Contexto de memoria intacto y válido, sin error LIBUSB_ERROR_IO)   │
│                                                                        │
│ 2. libusb_get_device_list: Inyección ──► usbi_alloc_device()           │
│    (Falsifica una lista con 1 dispositivo si existe ANDROID_USB_FD)    │
│                                                                        │
│ 3. libusb_get_device_descriptor:     ──► ioctl(USBDEVFS_CONTROL)      │
│    (Lectura atómica de descriptor y almacenamiento en caché estática) │
│                                                                        │
│ 4. libusb_open: Envoltura oficial    ──► libusb_wrap_sys_device(fd)   │
│    (Asocia el File Descriptor otorgado por Android USB Host API)       │
│                                                                        │
│ 5. linux_usbfs.c: Cierre Seguro      ──► if (init_count == 0) return;  │
│    (Evita assert crasheando el proceso durante libusb_exit)            │
└────────────────────────────────────────────────────────────────────────┘
```

### A. Ignorar el Fallo de Netlink en `core.c`
Cuando `libusb` intenta conectarse al socket Netlink y el kernel de Android rechaza la llamada, la variable `r` contiene el código de error. El parche inyecta:
```c
if (r) {
    r = 0; /* HACK para Android: Ignorar fallo netlink bajo SELinux */
}
```
**Efecto:** La inicialización concluye al 100%. El contexto `ctx` queda plenamente operativo y válido en el heap. Si no hay ningún programador USB conectado, las funciones de escaneo simplemente devuelven una lista vacía de forma limpia y `flashrom` reporta `No programmer found` sin crashear.

### B. Inyección de Dispositivo Virtual (`libusb_get_device_list`)
Cuando el usuario conecta un dispositivo OTG (por ejemplo un **CH341A**) y concede el permiso en el diálogo emergente del sistema, la capa Java de Android extrae el descriptor de archivo nativo y lo expone como variable de entorno:
```bash
export ANDROID_USB_FD=42
```
Al invocar `libusb_get_device_list()`, el parche verifica si `ANDROID_USB_FD` está definido en el entorno:
- Si existe, reserva memoria para un dispositivo utilizando la función interna `usbi_alloc_device(ctx, 0)`.
- Como el contexto `ctx` es completamente válido (gracias al paso A), la reserva de memoria es segura y genera un puntero de dispositivo legítimo en la lista de retorno.

### C. Lectura Atómica del Descriptor del Dispositivo (`libusb_get_device_descriptor`)
Android expone el dispositivo USB a través de un descriptor de archivo `/dev/usbdevX.X`. Leer directamente de dicho descriptor mediante llamadas `read()` convencionales puede alterar la posición del puntero de flujo (file offset) o fallar si el driver de kernel no implementa lectura secuencial de descriptores.

El parche resuelve esto ejecutando una transferencia de control atómica directa al kernel a través de `ioctl()` con el comando **`USBDEVFS_CONTROL`** (código `0xC0185500`):
```c
struct usbdevfs_ctrltransfer ctrl;
ctrl.bRequestType = 0x80; // Dirección IN, estándar, receptor dispositivo
ctrl.bRequest = 0x06;     // GET_DESCRIPTOR
ctrl.wValue = 0x0100;     // Tipo de descriptor: DEVICE (0x01), índice 0
ctrl.wIndex = 0x0000;
ctrl.wLength = 18;        // Tamaño del descriptor estándar de dispositivo USB
ctrl.timeout = 1000;
ctrl.data = desc_buffer;

if (ioctl(fd, USBDEVFS_CONTROL, &ctrl) >= 0) {
    // Descriptor leído exitosamente; se almacena en una caché estática en memoria.
}
```
Al cachear el descriptor en memoria estática, las subsiguientes llamadas de `flashrom` obtienen la información de VID/PID instantáneamente sin re-consultar al kernel.

### D. Envoltura del Descriptor de Sistema (`libusb_open`)
Cuando `flashrom` decide abrir el programador seleccionado, el parche intercepta la llamada `libusb_open` y en su lugar invoca la API oficial de libusb para sistemas embebidos:
```c
int r = libusb_wrap_sys_device(ctx, (intptr_t) android_fd, dev_handle);
```
Esta función vincula directamente el File Descriptor del kernel con la estructura interna `libusb_device_handle`, otorgando a `flashrom` control total sobre las transferencias USB de tipo Control, Bulk e Interrupt.

### E. Cierre Limpio del Sistema (`os/linux_usbfs.c`)
Durante la salida del programa (`libusb_exit`), la biblioteca intenta liberar recursos del subsistema de escaneo del kernel que nunca fueron creados en Android. Sin el parche, el código ejecutaba:
```c
assert(init_count != 0);
```
lo cual disparaba un abort del proceso al finalizar. El parche añade la guarda temprana:
```c
if (init_count == 0) return;
```
permitiendo que el proceso termine ordenadamente con código de salida `0`.

---

## 4. Preservación del File Descriptor en Android 10+ (Exec / Fork JNI)

A partir de **Android 10 (API 29)**, la máquina virtual ART implementó una política de endurecimiento de seguridad en las clases de ejecución de procesos de Java (`java.lang.ProcessBuilder` y `java.lang.Runtime.exec`).

Antes de ejecutar la llamada `execve()`, la implementación de la biblioteca C de Android (bionic) en Java ejecuta un bucle sistemático cerrando **todos los descriptores de archivo abiertos mayores a 2 (STDERR_FILENO)**:
```c
// Comportamiento interno de ProcessBuilder en Android 10+:
for (int fd = 3; fd < max_fd; fd++) {
    close(fd);
}
```
Esto causaba que el File Descriptor inyectado en `ANDROID_USB_FD` fuera invalidado justamente antes de que `flashrom` tomara el control, arrojando el error `Invalid File Descriptor (errno 9)`.

### Solución en Flash SPI Tool: Ejecutor Nativo JNI (`FlashromExecutor.java` + `native-lib.cpp`)
Para eludir este cierre sistemático de Java:

1. **Duplicación y Eliminación de `O_CLOEXEC`:**
   En [`native-lib.cpp`](file:///home/danielpdiamon/Flash-EEPROM-Tool/app/src/main/cpp/native-lib.cpp), la función `dupFdForChild()` realiza un `dup()` del descriptor entregado por `UsbDeviceConnection` y borra forzosamente el flag `FD_CLOEXEC`:
   ```cpp
   int newFd = dup((int) fd);
   int flags = fcntl(newFd, F_GETFD);
   if (flags >= 0 && (flags & FD_CLOEXEC)) {
       fcntl(newFd, F_SETFD, flags & ~FD_CLOEXEC);
   }
   ```

2. **Bifurcación Nativa en C++ (`startNativeProcess`):**
   En lugar de usar `ProcessBuilder` de Java, el proceso se lanza directamente en C++ utilizando llamadas al sistema del estándar POSIX:
   ```cpp
   int pipefds[2];
   pipe(pipefds); // Captura en tiempo real de stdout y stderr
   pid_t pid = fork();
   if (pid == 0) {
       // PROCESO HIJO (flashrom)
       dup2(pipefds[1], STDOUT_FILENO);
       dup2(pipefds[1], STDERR_FILENO);
       close(pipefds[0]);
       close(pipefds[1]);

       // Inyectar variables de entorno de forma segura
       setenv("ANDROID_USB_FD", std::to_string(usbFd).c_str(), 1);
       setenv("LD_LIBRARY_PATH", ldLibPath, 1);

       // Preservar el FD libre de la intervención de la JVM
       execv(execPath, argv.data());
       _exit(127);
   }
   ```

3. **Lectura Asíncrona en Java:**
   El descriptor de lectura del pipe (`pipefds[0]`) es adoptado en Java a través de `ParcelFileDescriptor.adoptFd()` y procesado en hilos de fondo para actualizar la consola en tiempo real.

---

## 5. Compatibilidad con Páginas de Memoria de 16KB (Android 15+)

A partir de Android 15 (Vanilla Ice Cream / API 35), el kernel de Linux puede utilizar tamaños de página de memoria virtual de **16 KB** en lugar de los clásicos 4 KB para acelerar operaciones en procesadores ARM64 modernos.

Todas las librerías nativas del proyecto, incluyendo `libusb_1_0.so` y el ejecutable `flashrom`, han sido compiladas con las directivas del NDK:
```bash
-Wl,-z,max-page-size=16384
```
Esto garantiza que las secciones de código (`LOAD`) y datos de las librerías compartidas estén alineadas a límites de 16 KB, asegurando compatibilidad total con Android 15, Android 16 y Android 17 sin causar errores de alineación en tiempo de carga (`dlopen`).

---

## 6. Resumen de Flujo de Datos

```
[ Usuario conecta periférico USB OTG ]
                     │
                     ▼
[ UsbManager solicita autorización ]
                     │ (Permiso Concedido)
                     ▼
[ UsbDeviceConnection.getFileDescriptor() ]
                     │
                     ▼
[ native-lib.cpp: dupFdForChild() ] ──► Quita flag O_CLOEXEC
                     │
                     ▼
[ native-lib.cpp: startNativeProcess() ] ──► fork() + setenv(ANDROID_USB_FD) + execv()
                     │
                     ▼
[ flashrom carga libusb_1_0.so parcheada ]
   ├─► core.c: ignora fallo netlink (ctx preservado en heap)
   ├─► libusb_get_device_list: usbi_alloc_device() inyecta 1 nodo
   ├─► libusb_get_device_descriptor: ioctl atómico USBDEVFS_CONTROL
   └─► libusb_open: libusb_wrap_sys_device(ctx, fd)
                     │
                     ▼
[ Acceso directo y completo al hardware SPI en espacio de usuario no-root ]
```
