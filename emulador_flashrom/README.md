# Emulador de Flashrom (GD25Q80)

Este emulador permite simular un chip de memoria flash SPI GD25Q80 (1MB) y comunicarlo a través de diferentes programadores virtuales para probar la aplicación Flash-EEPROM-Tool.

## Programadores Soportados:
- **CH341A**: Simula un programador USB conectándose por un par de sockets de UNIX, e inyectando un FD con `ANDROID_USB_FD`.
- **Serprog (Arduino)**: Crea un PTY (Pseudo-Terminal) que implementa el protocolo de `serprog`. Útil para programadores Arduino.
- **Bus Pirate v3**: Crea un PTY que implementa el protocolo binario SPI del Bus Pirate v3.

## Uso:
Compilar con `make`.
Ejecutar `./emulador_flashrom --help` para ver opciones.

```bash
./emulador_flashrom --all --fill random
```

## Desarrollo y Mantenimiento:
- **Actualizaciones de Código**: Deben realizarse en la carpeta del repositorio (`~/Flash-EEPROM-Tool/emulador_flashrom`) para mantener el control de versiones.
- **Entorno de Ejecución**: La carpeta en el home (`~/emulador_flashrom`) es para desarrollo activo, compilación y pruebas.
