# Flash SPI Tool v1.8.7 (versionCode 82)

Esta versión (**v1.8.7**, código de versión **82**) incorpora el control unificado de verificación de escritura en toda la suite de operaciones de **Flash SPI Tool**, asegurando que la preferencia del usuario se respete de manera coherente en todos los programadores físicos y emulados.

---

## 🚀 Principales Novedades y Mejoras Técnicas

### 1. Control Unificado de Verificación de Escritura (`Verify Write`)
- **Libertad y flexibilidad total:** La casilla de verificación ahora gobierna cualquier operación de escritura en flashrom (`-w`). Si el usuario decide mantenerla desmarcada, la app inyecta automáticamente el parámetro `-n` (`--noverify`), omitiendo la fase de verificación y reduciendo drásticamente el tiempo de flasheo en tareas donde la velocidad es prioritaria.
- **Cobertura total en todos los modos:**
  - 🔌 **Programadores USB Directos:** CH341A, ST-LINK, J-Link, FT2232H, etc.
  - ⚡ **Programadores Serie (PTY / Socket TCP):** Arduino UNO/Nano (`serprog`), Bus Pirate v3 y SPIDriver.
  - 🧪 **Entorno de Simulación:** Modo Dummy integrado y diálogo interactivo de prueba de chips.
  - 💻 **Consola de Comandos Personalizados:** Comandos manuales ejecutados desde la UI.
- **Comportamiento seguro y transparente:** La casilla inicia desmarcada por defecto en cada sesión sin persistencia forzada, permitiendo elegir conscientemente cuándo verificar. Si el comando ya incluye `-n` o `--noverify`, se evita la duplicación redundante de parámetros.
- **Independencia del botón "Verify ROM":** El botón manual de verificación continúa ejecutando `-v` de forma completamente autónoma para contrastar la memoria contra cualquier archivo `.bin` en cualquier instante.

### 2. Validación Exhaustiva en Hardware Emulado
- Verificado y contrastado al 100% mediante el banco de pruebas local del emulador de memorias SPI GD25Q80 (1 MB) en todos los protocolos (`serprog`, `buspirate_spi`, `spidriver`, `ch341a_spi` y `dummy`).

---

## 🌐 Notas de Versión / Release Notes

### 🇪🇸 Español (`es-419` / `es-ES`)
- La casilla "Verificar escritura" ahora funciona de forma unificada en todos los modos: USB directo, serie (Arduino/serprog, Bus Pirate, SPIDriver) y simulador.
- Tú decides cuándo verificar tras la escritura; la casilla inicia desmarcada en cada sesión para mayor flexibilidad.
- Validación completa de flujo de datos y optimización de comandos en tiempo real.
- Mejoras continuas de estabilidad y rendimiento en Android API 37.

### 🇺🇸 English (`en-US`)
- The "Verify Write" checkbox is now unified across all operation modes: direct USB, serial (Arduino/serprog, Bus Pirate, SPIDriver), and simulator.
- You decide when to verify after flashing; the checkbox starts unchecked on each session for maximum flexibility.
- Full real-time data flow validation and command optimization.
- Ongoing stability and performance enhancements on Android API 37.

---

## 📦 Artefactos de la Versión
- **`app-release.aab`**: Android App Bundle oficial firmado para Google Play Store.
- **`app-release.apk`**: Paquete APK universal compilado y firmado para instalación directa en dispositivos físicos.
