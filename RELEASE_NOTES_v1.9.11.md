# Release Notes v1.9.11 (97)

## Objetivo

Solucionar el error de inicialización en programadores FTDI (`Unable to open FTDI device: -3 (device not found)`) al conectar dispositivos como el FT232H (`0403:6014`), especificando correctamente el parámetro de tipo (`type=...`) en los argumentos de `flashrom`, y ampliar la compatibilidad de hardware USB.

## Cambios Principales

### App y Core USB (`UsbController.java`, `device_filter.xml`)

- **Mapeo FTDI Preciso:** Se actualizó `USB_AUTO_MAP` para asignar parámetros de tipo específicos para cada VID:PID FTDI compatible (`ft2232_spi:type=232H`, `type=2232H`, `type=4232H`, `type=4233H`, etc.), evitando que flashrom intente usar un tipo por defecto incorrecto y falle al abrir el dispositivo.
- **Soporte NVIDIA SMA:** Se añadió soporte para el adaptador USB-to-SPI NVIDIA SMA (`0955:cf11` -> `nv_sma_spi`).
- **Limpieza ST-LINK V3:** Se ajustaron los filtros y mapeos para admitir únicamente los ST-LINK V3 con puente SPI soportado (`0x374f`, `0x3753`, `0x3754`), excluyendo explícitamente V3E (`0x374e`, marcado como `BAD` en flashrom) y modelos V2.
- **Filtros Google Servo / Raiden:** Se actualizó el filtro USB para Google Servo/Raiden (`0x18d1`) buscando la interfaz vendor-specific `0xFF/0x51` sin requerir una lista rígida de PID.
- **Tests Unitarios:** Se añadieron pruebas exhaustivas en `ExampleUnitTest.java` para validar el mapeo automático de VID:PID de todos los programadores soportados.

## Compatibilidad e Interfaz

- Interfaz gráfica y selectores actualizados en español, inglés y chino (`strings.xml`, `ProgrammerSettingsActivity.java`, `README.md`).
- Bash completion de flashrom actualizado.

## Pruebas Recomendadas

- Conexión de programador FTDI (ej. FT232H `0403:6014`): verificación de auto-detección como `ft2232_spi:type=232H` y ejecución exitosa de flashrom sin error `-3`.
- Detección automática de nuevos programadores añadidos.
