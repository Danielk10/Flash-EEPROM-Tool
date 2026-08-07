# Reporte Técnico: Parche Nivel Dios para Libusb en Android (Portabilidad Nativa)

Este documento explica de manera detallada y técnica la arquitectura del parche aplicado a `libusb-1.0` en los scripts de compilación de este proyecto. Este parche es el responsable de que herramientas de línea de comandos diseñadas originalmente para Linux de escritorio (como `flashrom` y `minipro`) puedan ejecutarse de manera nativa y directa dentro de Android sin requerir acceso root, utilizando el File Descriptor (FD) proveído por la API Java de Android.

## 1. El Problema Base: Linux vs. Android (SELinux)

En un entorno Linux tradicional, cuando una aplicación como `flashrom` solicita escanear los puertos USB, `libusb` se comunica con el kernel a través del subsistema `netlink` (`/dev/bus/usb/`). El kernel devuelve felizmente una lista de dispositivos.

En **Android**, las políticas de seguridad estrictas (SELinux) bloquean completamente el acceso directo al bus USB desde el espacio de usuario (C/C++). Si `libusb` intenta escanear el bus como lo haría en Linux, el kernel de Android le deniega el acceso y la inicialización de `libusb` aborta prematuramente, devolviendo un error fatal conocido como **`LIBUSB_ERROR_IO`**. Esto provoca que cualquier aplicación nativa asuma que el bus USB está dañado y se cierre de inmediato.

## 2. El Parche Anterior (El Método Destructivo)

El primer intento de parchear este problema fue extremadamente agresivo: interceptar la función inicial `libusb_init` y forzarla a devolver éxito (`return 0`) sin ejecutar el código de inicialización, destruyendo intencionalmente el puntero del contexto (`*ctx = NULL`).

**¿Por qué falló este método?**
Al evitar la inicialización, toda la infraestructura de memoria de `libusb` jamás se construyó. Cuando `flashrom` (con un dispositivo real conectado) intentaba añadir el dispositivo a su lista, las funciones de libusb intentaban acceder a la memoria del contexto. Como el contexto era NULO (`NULL`), el sistema operativo cerraba la aplicación abruptamente con un **Segfault (Fallo de Segmentación)**.

## 3. El Parche Actual (Inicialización Fantasma y Cirugía de Precisión)

El nuevo diseño del parche abandona la idea de bloquear la inicialización y permite que `libusb` reserve su memoria, construya sus estructuras y prepare su entorno por completo. La intervención solo ocurre en los puntos críticos de fallo:

### A. Ignorar el fallo de Netlink (`core.c`)
Justo en el milisegundo en el que `libusb` intenta usar `netlink` y falla por culpa de SELinux en Android, la variable `r` devuelve el código de error. El parche inyecta el siguiente código:
```c
if (r) { r = 0; /* HACK para Android: Ignorar fallo netlink */ }
```
**Resultado:** `libusb` completa su inicialización al 100% en la memoria. No hay pánico y no hay `LIBUSB_ERROR_IO`. El sistema de libusb asume que simplemente no hay dispositivos conectados en los puertos del sistema.

### B. El Comportamiento "Sin Dispositivo"
Gracias a que la inicialización fue exitosa, si ejecutamos `flashrom` sin pasar un FD de Android, el programa simplemente escaneará la lista vacía de dispositivos de `libusb`. Como el contexto es válido y no hay dispositivos, el programa fluirá con normalidad y terminará devolviendo el mensaje esperado (ej. `No programmer found.`), sin generar cierres forzosos.

### C. El Comportamiento "Con Dispositivo" (Inyección de FD)
Cuando el usuario concede permisos USB en la interfaz gráfica de Android, la capa Java extrae el File Descriptor (FD) y lo pasa por entorno mediante la variable `ANDROID_USB_FD`.

El parche intercepta tres puntos vitales:
1. **Falsificación de la Lista de Dispositivos (`libusb_get_device_list`):**
   Si la variable `ANDROID_USB_FD` existe, forzamos a que `libusb` devuelva una lista que contiene un solo "dispositivo emulado" usando la función `usbi_alloc_device()`. Como el contexto de libusb es válido (gracias al parche A), esta función reserva memoria sin provocar Segfault.

2. **Lectura y Caché del Descriptor (`libusb_get_device_descriptor`):**
   Android expone el dispositivo USB como un stream (character device). Para evitar que fallas en el cursor de lectura del stream arruinen la lectura del descriptor, el parche realiza una transferencia de control atómica directamente al kernel a través de `ioctl(fd, USBDEVFS_CONTROL, &ctrl)` (código de ioctl `0xC0185500`). El descriptor obtenido se almacena en una caché estática en memoria para responder de inmediato en llamadas futuras sin reiniciar el flujo del descriptor.

3. **Envoltura del Dispositivo (`libusb_open`):**
   Cuando `flashrom` intenta abrir el dispositivo, interceptamos la llamada y en su lugar usamos la función oficial de libusb para sistemas embebidos: `libusb_wrap_sys_device(ctx, fd, dev_handle)`. 
   Esta función toma el FD nativo que Android nos regaló (pasado en `ANDROID_USB_FD`) y se lo entrega directamente al contexto de `libusb`.

**Resultado:** `flashrom` ahora tiene acceso directo e ininterrumpido al hardware USB, saltándose por completo las restricciones de escaneo de SELinux, sin violar la integridad de la memoria interna de C ni provocar fallos de lectura del descriptor.

### D. Cierre Limpio del Sistema (`os/linux_usbfs.c`)
Durante el apagado del programa (`libusb_exit`), `libusb` intentaba liberar recursos del bus de sistema que en Android nunca llegó a abrir, disparando el error `assert(init_count != 0);` y crasheando el proceso al finalizar.
El parche soluciona esto agregando una validación temprana:
```c
if (init_count == 0) return;
```
Permitiendo que la aplicación nativa se cierre con código `0` de manera limpia.

## 4. Bypass de Cierre de File Descriptor en Android 10+ (Exec / Fork JNI)
A partir de Android 10 (API 29), las llamadas del sistema a través de la API estándar de Java (`ProcessBuilder` y `Runtime.exec`) realizan una limpieza interna en la que cierran de forma sistemática todos los descriptores de archivo (FD) abiertos mayores a 2 en el proceso hijo antes de llamar a `execve`. Esto invalidaba el FD inyectado para la comunicación USB sin Root (`ANDROID_USB_FD`).

Para solucionarlo, la ejecución se realiza de manera nativa en C++ a través de llamadas JNI. El proceso se bifurca (`fork()`) y se ejecuta (`execv()`) en el cargador nativo preservando el FD de comunicación USB libre de la interferencia de Java. El texto de salida de terminal se redirige a través de un `pipe` POSIX en C++ y se lee en Java en tiempo real utilizando `ParcelFileDescriptor.adoptFd()`.

## Resumen de Estabilidad
El parche actual es la solución definitiva para correr binarios C/C++ que dependan de libusb en Android no-root. Garantiza que la capa JNI de Java se comunique limpiamente con el hardware sin requerir que las librerías nativas o el programa final (`flashrom`) sean reescritos masivamente.
