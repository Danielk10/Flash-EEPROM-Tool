# Release Notes v1.9.10 (96)

## Objetivo

Eliminar los falsos positivos del beacon `0xAA 0x55` y hacer que el puente
serprog sea robusto tanto con el firmware custom como con firmware serprog
estándar (`frser-arduino`, RP2040, STM32, etc.).

## Cambios

### Firmware (`serprog_arduino_uno_ch340g.ino`)

- Nuevo comando `HELLO` (`0xF0`): responde
  `ACK 'S' 'P' 'R' 'G' version caps xor`.
- Se eliminan los beacons `0xAA 0x55` de las rutas de error SPI. En caso de
  timeout o longitud inválida, el firmware envía solo `NAK` y limpia la entrada.
- Se mantiene el beacon de arranque `0xAA 0x55` únicamente por compatibilidad
  con versiones antiguas de la app (<= 1.9.9).
- `SYNCNOP` (`0x10`) sigue respondiendo `NAK + ACK` por compatibilidad, pero la
  app nueva ya no lo usa para resincronizar.

### App

- `prepareForFlashromSession()` ahora hace un handshake solicitado:
  1. `HELLO` con reintentos (firmware nuevo, versionado).
  2. Fallback `NOP/ACK` (firmware estándar o custom antiguo).
  3. Si ninguno responde, error claro sin cerrar la app.
- Se elimina por completo el escaneo de `0xAA 0x55` durante el forwarding, tanto
  en el motor C++ (`usb_bridge.cpp`) como en el fallback Java (`PtyBridge.java`).
- Se elimina la inyección de `SYNCNOP` en medio de las respuestas serprog.
- Se elimina el parámetro `beaconResync` de la API nativa y de las estadísticas.
- Se corrige el NPE de `setRTS()` ya incluido en el commit `595c274`
  (Crashlytics `151f4af3f7a67cdbf6197cbec9d3db55`).

## Compatibilidad

| Firmware | App 1.9.9 | App 1.9.10 |
|---|---|---|
| Custom antiguo (beacon) | Funciona | `HELLO` falla -> `NOP/ACK` -> funciona |
| Custom nuevo (HELLO) | Beacon de arranque mantenido -> funciona | `HELLO` -> funciona, versión registrada |
| `frser-arduino` estándar | Falla a los 8 s | `NOP/ACK` -> funciona |

## Compilación del firmware

```bash
arduino-cli core install arduino:avr
./compile-serprog-firmware.sh
```

El script compila con `arduino-cli` y actualiza automáticamente:

- `serprog_arduino_uno_ch340g.hex`
- `app/src/main/assets/serprog_arduino.hex`
- `app/src/main/assets/serprog_arduino.ino`

## Pruebas recomendadas

- UNO + CH340: arranque, lectura y escritura.
- Placa con USB nativo (RP2040/STM32): handshake sin depender del reset por DTR.
- `frser-arduino` estándar: debe responder al fallback `NOP/ACK`.
- Lectura de 1 MB con `AA 55` inyectado: verificar SHA-256 y ausencia de
  `SYNCNOP` en el flujo.
- Desconexión USB durante el handshake: la app debe abortar sin crash.

## Notas

- El firmware se actualiza manualmente; la app solo exporta el `.hex`.
- El crash NPE de Crashlytics debe permanecer abierto hasta publicar esta
  versión y verificar que deja de aparecer.
