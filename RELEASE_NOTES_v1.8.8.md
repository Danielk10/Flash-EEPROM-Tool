# Notas de Lanzamiento - Flash-EEPROM-Tool v1.8.8 (versionCode 83)

## 🌐 Notas para Google Play Console (Bilingüe)

### Español (`es-419` / `es-ES`)
- Independencia total de la consola de comandos personalizados frente a la casilla de verificación.
- La casilla "Verificar escritura" aplica a los botones de acción en hardware real y simulador.
- Tú decides si verificar después de escribir; la casilla inicia desmarcada en cada sesión.
- Mejoras de estabilidad y optimización de comandos en tiempo real.

### English (`en-US`)
- Full independence of custom command console from the verify write checkbox.
- The "Verify Write" checkbox applies to UI action buttons on real hardware and simulator.
- You decide whether to verify after flashing; the checkbox starts unchecked on each session.
- Stability improvements and real-time command optimization.

---

## 🛠️ Detalle Técnico de Cambios

1. **Independencia de la Consola de Comandos Personalizados (`MainActivity.java`)**:
   - Se removió la alteración de argumentos de `executeCustomFlashromCommand()`: cualquier comando manual ingresado en la consola interactiva se despacha exactamente tal como fue escrito por el usuario (sin inyectar `-n` ni modificar parámetros).
   - De esta forma, el usuario tiene libertad absoluta para enviar comandos con o sin verificación según sus requerimientos técnicos.

2. **Gobierno de la Casilla "Verify Write" en Botones de UI**:
   - En hardware real (programadores USB nativos y serie por PTY/Socket), el botón de escritura en `executeFlashromTask()` aplica la preferencia de la casilla.
   - En el simulador (modo Dummy) y en su diálogo de pruebas, `executeMainDummyCommand()` aplica la preferencia de la casilla antes de armar el comando.
   - La casilla inicia desmarcada por defecto en cada sesión y no persiste.

3. **Validación Exhaustiva**:
   - Probado y contrastado con el emulador local de hardware flashrom para todos los modos (`serprog`, `buspirate_spi`, `spidriver`, `ch341a_spi` y `dummy`).
