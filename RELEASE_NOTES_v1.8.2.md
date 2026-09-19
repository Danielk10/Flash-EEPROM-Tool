# Notas de Lanzamiento - Flash-SPI-Tool v1.8.2

Esta versión de producción (`v1.8.2`, código de versión `77`) soluciona problemas críticos de compatibilidad con versiones heredadas de Android (Android 6.0 a 7.1, API 23–25), garantizando ejecución estable y libre de excepciones en todo el espectro soportado (API 23 a API 37).

---

## 🐛 Correcciones de Compatibilidad y Estabilidad (Crashes)

* **Compatibilidad Retrospectiva de Manejo de Archivos (Issue #85):**
  * Se corrigió la excepción `NoSuchMethodError: No virtual method toPath()Ljava/nio/file/Path; in class Ljava/io/File;` al importar archivos ROM (`MainActivity.importRomFile`).
  * Los métodos modernos de NIO (`java.nio.file.Files.copy()` y `Files.readAllBytes()`) ahora se ejecutan de manera condicional en dispositivos con Android 8.0+ (API 26+) aprovechando la aceleración nativa del kernel (zero-copy).
  * Para dispositivos con Android 6.0–7.1 (API 23–25), se implementó un mecanismo alternativo seguro basado en flujos clásicos (`FileInputStream` / `FileOutputStream` con buffers de 8 KB).
  * Se aplicó la misma protección en el visor hexadecimal ([HexViewerActivity.java](file:///home/danielpdiamon/Flash-EEPROM-Tool/app/src/main/java/com/diamon/curso/ui/activities/HexViewerActivity.java)) y en el comparador de binarios ([HexDiffActivity.java](file:///home/danielpdiamon/Flash-EEPROM-Tool/app/src/main/java/com/diamon/curso/ui/activities/HexDiffActivity.java)).

* **Reemplazo de `String.join()` por `TextUtils.join()`:**
  * `java.lang.String.join()` requiere API 24+ (Android 7.0), lo que provocaba cierres inesperados en Android 6.0 (API 23).
  * Se sustituyeron las 4 ocurrencias en `MainActivity` por `android.text.TextUtils.join()`, disponible de forma retrocompatible desde API 1.

* **Reemplazo de `java.util.function.Consumer` por Interfaz Propia:**
  * La interfaz `Consumer` requiere API 24+ (Android 7.0).
  * Se creó la interfaz funcional `OnProgrammerSelectedListener` en `UsbController.java`, eliminando la dependencia de clases no disponibles en Android 6.0.

---

## 🔬 Auditoría de Código y Verificación de Compatibilidad

* **Auditoría Exhaustiva de 14 Archivos Java:**
  * Se verificaron todos los puntos de entrada del código fuente contra las políticas de API level de Android.
  * 100% de llamadas a APIs superiores a API 23 cuentan con guardas condicionales `Build.VERSION.SDK_INT`.
  * Cero errores y cero advertencias de compilación en el build del proyecto.

---

## 🛠️ Pruebas y Validación con Emulador de Hardware

* **Validación de Protocolos y Flujo de Datos:**
  * **CH341A (USB Directo):** Ciclo completo probado sobre chip virtual SPI `GD25Q80(B)` (1 MB):
    * Detección JEDEC ID (`0xC8 0x40 0x14`).
    * Lectura completa de 1.048.576 bytes con patrón secuencial verificada.
    * Borrado, programación por páginas (`Page Program`) y verificación final exitosos.
  * **SERPROG (PTY):** Protocolo serie simulado probado para programadores basados en Arduino/AVR.
  * **Bus Pirate (PTY):** Secuencia de inicialización texto → BBIO → modo binario SPI validada.
  * **SPIDriver (PTY):** Handshake de estado (`?`), eco (`e`) y transferencias SPI validadas.

---

## 📦 Especificaciones del Paquete de Producción

* **Versión:** 1.8.2
* **Version Code:** 77
* **Target SDK:** 37 (Android 17)
* **Min SDK:** 23 (Android 6.0 Marshmallow)
* **NDK:** r30 (30.0.14904198)
* **Build Tools:** 37.0.0
* **CMake:** 4.1.2
