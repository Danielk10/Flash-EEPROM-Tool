# Notas de Lanzamiento - Flash-EEPROM-Tool v1.8.7 (versionCode 82)

## 🌐 Notas para Google Play Console (Bilingüe)

### Español (`es-419` / `es-ES`)
- La casilla "Verificar escritura" ahora funciona en todos los modos: USB, serial y simulador.
- Tú decides si verificar después de escribir; la casilla inicia desmarcada en cada sesión.
- Mejoras de estabilidad y mantenimiento interno.

### English (`en-US`)
- The "Verify Write" checkbox now works in every mode: USB, serial and simulator.
- You decide whether to verify after writing; the checkbox starts unchecked on each session.
- Stability improvements and internal maintenance.

---

## 🛠️ Detalle Técnico de Cambios

1. **Casilla "Verify Write" unificada (`MainActivity.java`)**:
   - Nuevo método `applyVerifyWritePreference()`: si la casilla está desmarcada y el comando incluye `-w`, agrega `-n` (`--noverify`) a flashrom y lo registra en el log. Si el comando ya incluye `-n` o `--noverify`, no se duplica.
   - Se aplica tanto en `executeFlashromTask()` (botón Write con programador real) como en `executeCustomFlashromCommand()` (modo dummy, diálogo de prueba, programadores serie por PTY/Socket y consola de comandos). Antes solo aplicaba en la primera ruta.
   - La casilla inicia desmarcada y no se persiste: el usuario elige en cada sesión.
   - El botón **Verify ROM** (`-v`) sigue siendo independiente de la casilla.

2. **Validación**:
   - Probado contra el emulador de flashrom local (serprog, Bus Pirate, SPIDriver, CH341A y dummy): con `-n` flashrom no verifica; sin `-n` verifica y reporta `VERIFIED`.
