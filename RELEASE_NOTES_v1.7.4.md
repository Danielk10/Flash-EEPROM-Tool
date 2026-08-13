# Notas de Lanzamiento - Flash-EEPROM-Tool v1.7.4

Esta versión de producción (`v1.7.4`, código de versión `66`) introduce soporte completo para el programador SPIDriver (tanto en la aplicación Android como en la suite de pruebas y emulación local), soluciona conflictos de auto-detección de hardware USB, unifica la consola de comandos de flashrom a un comportamiento estándar de PC, y robustece la suite de validación.

## 🚀 Nuevas Características y Mejoras

*   **Soporte Completo de SPIDriver:**
    *   Se ha implementado el soporte de emulación del protocolo **SPIDriver** a **460800 bps** en la suite nativa local (`emulador_flashrom.cpp`), cubriendo comandos de handshake ASCII (`?`), verificación de eco (`e`), control de Chip Select (`s`/`u`), y transferencias SPI rápidas de 1 a 64 bytes.
    *   La app ahora gestiona de forma transparente el túnel serial a través de `PtyBridge` para SPIDriver al ser invocado.
*   **Auto-detección Inteligente de Hardware (FTDI):**
    *   Se resolvió el conflicto donde la app identificaba erróneamente un dispositivo SPIDriver como Bus Pirate debido al uso compartido del mismo chip FTDI (`VID:PID 0403:6001`).
    *   La app ahora interroga el descriptor de producto USB (`productName`). Si contiene `"spidriver"`, se auto-selecciona y configura automáticamente el protocolo correcto.
*   **Consola de Comandos Estricta y Real:**
    *   Se eliminó la lógica obsoleta en la caja de comandos que permitía omitir el comando principal. Ahora, para mayor fidelidad a una terminal de PC, es **obligatorio** iniciar la línea de comandos con el prefijo `flashrom` o `./flashrom` (ej: `flashrom -p ch341a_spi -r bios.bin`).

## 🛠️ Correcciones de Errores y Calidad de Código

*   **Cero Strings Hardcodeados:**
    *   Migramos el mensaje de error de prefijo faltante a recursos XML localizados (`R.string.str_err_missing_flashrom_prefix`), garantizando una interfaz libre de cadenas duras e internacionalizable.
*   **Suite de Pruebas Unitarias y de Instrumentación:**
    *   Añadido el script automatizado [`test_all_devices.sh`](./emulador_flashrom/test_all_devices.sh) para validar el comportamiento local de todos los programadores emulados (Serprog, Bus Pirate, SPIDriver y CH341A) contra la memoria virtual `GD25Q80(B)`.
    *   Se incorporó un nuevo paso de prueba de instrumentación en la app (`ExampleInstrumentedTest.java`) para validar la detección correcta del mensaje de error traducido al enviar comandos con prefijo inválido.
*   **Renombrado y Actualización de Documentación:**
    *   La guía técnica fue renombrada a [`REPORTE_SOLUCION_PTY_SERPROG_BUSPIRATE_SPIDRIVER.md`](./REPORTE_SOLUCION_PTY_SERPROG_BUSPIRATE_SPIDRIVER.md) para reflejar con precisión el soporte de SPIDriver y su inmunidad a la disciplina de línea del kernel.
