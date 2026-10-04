# Notas de Lanzamiento - Flash-EEPROM-Tool v1.8.6 (versionCode 81)

## 🌐 Notas para Google Play Console (Bilingüe)

### Español (`es-419` / `es-ES`)
- Nuevo motor nativo C++ y transporte Socket TCP loopback de latencia cero.
- Visor Hexadecimal con mapeo virtual mmap (0 MB de consumo de RAM en heap).
- Exportación zero-copy ultrarrápida mediante FileChannel.transferTo a nivel de kernel.
- Corrección de cierre inesperado (NPE) en el receptor de alarmas en segundo plano.
- Soporte y compatibilidad completa con Android API 37.

### English (`en-US`)
- New native C++ engine and zero-latency loopback TCP Socket transport.
- Zero-copy mmap Hex Viewer (0 MB JVM heap footprint).
- High-speed zero-copy dump export via FileChannel.transferTo at kernel level.
- Fixed NPE alarm receiver background crash.
- Full Android API 37 support and compatibility.

---

## 🛠️ Detalle Técnico de Cambios

1. **Motor Nativo C++ (`usb_bridge.cpp`)**:
   - Hilos POSIX de alta prioridad (`setpriority -8`) para comunicación directa entre endpoints bulk USB (`ioctl(USBDEVFS_BULK)`) y sockets/PTY.
   - Eliminación de la JVM y de arrays `byte[]` en el flujo de datos en tiempo real.
   - Resincronización activa con firmware Arduino ante beacons `0xAA 0x55` con `SYNCNOP` (`0x10`).

2. **Transporte Socket TCP Loopback (`127.0.0.1`)**:
   - Servidor TCP local con `TCP_NODELAY` para `serprog:ip=127.0.0.1:puerto`.
   - Selector dual en ajustes del programador (`ProgrammerSettingsActivity`) permitiendo elegir entre Socket y PTY clásico.

3. **Visor Hexadecimal Zero-Copy (`HexViewerActivity.java`)**:
   - `MappedFileSource` con `FileChannel.map()` para paginar en memoria virtual solo 16 bytes por fila en `RecyclerView`.

4. **Exportación Directa a Descargas (`FileManager.java`)**:
   - Transferencia de bloques con `FileChannel.transferTo()` (syscall `sendfile`).

5. **Blindaje de Manifiesto (`AndroidManifest.xml`)**:
   - Eliminación de `AlarmManagerSchedulerBroadcastReceiver` mediante `tools:node="remove"` para evitar crashes de DataTransport.
