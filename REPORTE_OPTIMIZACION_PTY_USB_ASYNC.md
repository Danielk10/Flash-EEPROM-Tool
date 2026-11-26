# Reporte de Optimización: PTY Nativo No Bloqueante y USB Asíncrono

> **Versión del informe:** Flash SPI Tool v1.9.2 (Build 88) — arquitectura vigente en v1.9.27 (Build 113)  
> **Guía Técnica de Referencia:** Para la arquitectura completa de transporte en C++, consulta la [Guía Técnica del Parche PTY USB](file:///home/danielpdiamon/Flash-EEPROM-Tool/GUIA_TECNICA_PARCHE_PTY_USB.md).

Este reporte detalla las mejoras críticas implementadas en el proyecto **Flash SPI Tool** para resolver problemas de latencia, bloqueos de hilos y desincronización de protocolo cuando se utiliza con programadores basados en serprog (Arduino UNO / CH340G).

## 1. Problemas Identificados (Causa Raíz)

1. **Bloqueos por Garbage Collector (GC):** La API estándar `bulkTransfer` de Android es bloqueante. Cuando el sistema realiza una pausa de GC, el hilo de lectura USB se detiene, provocando que el Arduino supere su timeout de 1000ms.
2. **Lentitud en el Puente PTY Java:** El uso de `FileInputStream` y `FileOutputStream` sobre el master FD del PTY en Java introduce una capa de abstracción que puede bloquearse si el flujo de datos (throughput) es muy alto, especialmente con un `libusb` parcheado.
3. **Desincronización de Protocolo:** Cuando ocurría un timeout, el Arduino enviaba un `S_NAK` y volvía a esperar, pero `flashrom` quedaba esperando datos, resultando en un cuelgue total de la operación.

## 2. Soluciones Implementadas

### A. Capa de Transporte USB (App Android)
Se migró la lectura USB de llamadas bloqueantes hacia:
1. **`UsbRequest` Asíncrono:** Implementación de `queue()` y `requestWait(timeout)` en `PtyBridge.java` para el backend en Java de respaldo.
2. **Motor Nativo C++ (`usb_bridge.cpp`) en v1.9.2:** Transferencias directas bulk con `ioctl(USBDEVFS_BULK)` en dos hilos POSIX independientes con prioridad `-8`.
*   **Razón:** Al operar en C++ sin intervención de la JVM, las pausas de recolección de basura no afectan la latencia P90 de las transferencias.
*   **Resultado:** Estabilidad absoluta en transferencias masivas (lectura y escritura de memorias SPI de 8 MB, 16 MB y 32 MB).

### B. Optimización del Puente PTY (JNI Nativo con `poll()`)
Se eliminó la dependencia de streams de Java para la comunicación con el terminal virtual.
*   **Acción:** Creación de funciones JNI `readFd` y `writeFd` en `native-lib.cpp` utilizando la syscall `poll()`.
*   **Razón:** `poll()` permite consultar el estado del PTY con un timeout milimétrico (10ms a 100ms). Si el buffer está lleno o vacío, el hilo no se bloquea indefinidamente; simplemente continúa, permitiendo que la CPU se use de forma eficiente.
*   **Resultado:** Eliminación total de cuelgues en los hilos de reenvío cuando el búfer del PTY se satura.

### C. Firmware Arduino y Auto-Resincronización
Se actualizó el firmware para soportar una recuperación activa ante pérdida de fase.
*   **Acción:** Cuando el Arduino detecta un timeout SPI, ahora envía `S_NAK` + un **Beacon de Sincronización** (`0xAA 0x55`).
*   **Acción en App:** Tanto `PtyBridge.java` como `usb_bridge.cpp` detectan esta secuencia inyectada en el flujo de datos y automáticamente envían un comando `SYNCNOP` (`0x10`).
*   **Razón:** Permite que `flashrom` y el Arduino vuelvan a estar en fase sin necesidad de que el usuario desconecte físicamente el cable OTG o reinicie la app.

## 3. Detalle de Archivos del Sistema

| Archivo | Rol en el Sistema v1.9.2 |
| :--- | :--- |
| `native-lib.cpp` | Funciones nativas `createPty` con Dummy Slave FD, `readFd`, `writeFd` con `poll()`, y `startNativeProcess` con `fork()`/`execv()`. |
| `usb_bridge.cpp` | Motor nativo POSIX multihilo para USB bulk y servidores TCP loopback / PTY. |
| `PtyBridge.java` | Gestor del ciclo de vida del puente, detección de beacons, y fallback automático a hilos Java si el motor nativo no está disponible. |
| `serprog_arduino_uno_ch340g.ino` | Firmware con balizas de arranque y recuperación (`0xAA 0x55`), y limitación de búfer de 64 bytes (`Q_RDNMAXLEN`). |

## 4. Resultados de Verificación
*   **Compatibilidad Android:** Verificada desde Android 7.0 (API 24) hasta Android 17 (API 37).
*   **Alineación NDK:** 16KB max-page-size bajo NDK r30.
*   **Pruebas Locales:** Verificado con la suite `emulador_flashrom` en modo Serprog, Bus Pirate y SPIDriver.
