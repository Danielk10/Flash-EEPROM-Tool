# Notas de Lanzamiento - Flash SPI Tool v1.8.4

Esta versión (`v1.8.4`, código de versión `79`) introduce correcciones críticas de estabilidad, persistencia de datos y robustez en la comunicación de hardware y control de energía, garantizando compatibilidad total en todo el espectro soportado (**Android 6.0 / API 23 hasta Android 17 / API 37**).

---

## 🛠️ Correcciones Técnicas y Corrección de Errores (Bugs)

### 1. Prevención de Pérdida de Datos de ROM (`bios.bin`)
* **Problema:** Anteriormente, el ciclo de vida de `MainActivity` invocaba el borrado de archivos temporales en `onDestroy()` y `onCreate()`. Al rotar el dispositivo, cambiar de aplicación o al ocurrir una recreación del `Activity` por cambio de configuración o tema, el dump recién leído del chip se borraba de forma inesperada.
* **Solución:** Se eliminó el borrado forzado automático de `bios.bin`. El estado de los datos leídos (`hasReadData` y `lastReadFile`) ahora se conserva y restaura de forma persistente. La ROM temporal solo se descarta si el usuario pulsa explícitamente *"Borrar ROM"* o al iniciar una nueva operación de lectura.

### 2. Gestión de Energía y WakeLock Sincronizado
* **Problema:** Operaciones largas sobre memorias de gran tamaño (512 MB, 1 GB, 2 GB) o sobre programadores de baja velocidad (como UART a 115200 baudios) corrían riesgo de interrupción si se aplicaba un límite fijo de tiempo, provocando que Android suspendiera la CPU en mitad de la lectura/escritura.
* **Solución:** Se implementó una adquisición dinámica de `PowerManager.PARTIAL_WAKE_LOCK` y `FLAG_KEEP_SCREEN_ON` atada estrictamente al ciclo de vida del proceso nativo (`onProcessStarted`). El bloqueo se mantiene activo durante todo el proceso y se libera de forma segura e inmediata al finalizar (`onProcessFinished`, `onAmbiguityDetected` o `btnAbort`), eliminando timeouts arbitrarios que cortaban transferencias prolongadas.

### 3. Tolerancia a Fallos en Librería Nativa (`System.loadLibrary`)
* **Problema:** En arquitecturas no soportadas o entornos virtuales, una falla al resolver la librería nativa `libcurso.so` generaba una excepción `UnsatisfiedLinkError` no controlada que cerraba la aplicación de forma abrupta.
* **Solución:** Se encapsuló la carga de la librería en un bloque defensivo `try/catch` con bandera de verificación. Si la librería nativa no está disponible, la app informa al usuario con un mensaje claro en la consola sin cerrarse inesperadamente.

### 4. Desconexión USB Selectiva de Periféricos
* **Problema:** Al desenchufar cualquier periférico USB secundario (como memorias OTG, ratón o teclado), el receptor `ACTION_USB_DEVICE_DETACHED` desconectaba el programador SPI activo indiscriminadamente.
* **Solución:** Se agregó un rastreo de instancia (`currentDevice`). El controlador USB ahora valida el identificador único del hardware (`deviceId`) y solo finaliza la sesión si el dispositivo desconectado es exactamente el programador en uso.

---

## 🔬 Validación y Pruebas con Emulador de Hardware

Se verificó el 100% de los flujos de hardware con la suite local `test_all_devices.sh`:
* **SERPROG (PTY):** ✅ Detección e inicialización de protocolo serie simulado.
* **Bus Pirate (PTY):** ✅ Secuencia texto → BBIO → modo binario SPI validada.
* **SPIDriver (PTY):** ✅ Handshake y transferencias de comandos SPI exitosas.
* **CH341A (USB Directo):** ✅ Detección de chip virtual `GD25Q80(B)` vía interfaz USB directa.


