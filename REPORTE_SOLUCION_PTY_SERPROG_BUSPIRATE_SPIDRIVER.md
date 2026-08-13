# Reporte de Solución: Problemas de PTY (Line Discipline) y Desalineación de Bytes

Este documento detalla el análisis y la solución definitiva a un bug crítico que afectaba a todos los programadores de `flashrom` basados en puerto serie (como **Serprog / Arduino** y **Bus Pirate**) que se comunican a través del puente PTY (Pseudo-Terminal) en la aplicación Android.

## 1. El Síntoma del Problema

Durante lecturas masivas de memoria (ej. volcados de 1 MB o superiores), el proceso fallaba devolviendo mensajes de error por respuestas inválidas:

`Reading flash... Error: invalid response 0x01 from device (to command 0x13)`
`Reading flash... Error: invalid response 0xA0 from device (to command 0x13)`

Esto indicaba que el flujo de datos se estaba desalineando o perdiendo bytes de forma silenciosa.

## 2. Análisis de las Causas

Se determinó que existían dos problemas principales:

### A. Corrupción del Modo RAW en PTY por Line Discipline del Kernel (Android/Linux)
Este fallo era intrínseco de Linux y **afectaba a todos los programadores seriales (Serprog, Bus Pirate, SPIDriver, etc.)**.

En la app Android, creamos el puente PTY, le aplicamos `cfmakeraw` para que opere como un túnel binario puro y cerrábamos el descriptor de archivo (FD) esclavo temporalmente para que `flashrom` lo abriera después. 
En Linux/Android, cuando se cierra el último FD asociado a un lado esclavo de un PTY, el kernel de forma automática revierte la configuración del terminal a su estado original (Modo "Cooked").

Cuando `flashrom` abría el terminal, éste estaba operando en modo texto con la regla "Line Discipline" activa. Si el programador leía bytes binarios específicos como `0x11` (XON), `0x13` (XOFF), o retornos de carro, el sistema operativo los interceptaba como comandos de control de terminal y los eliminaba o mutaba. Esto destruía la integridad de la lectura.

### B. Desbordamiento del Buffer UART Hardware del CH340 (Específico de Arduino/Serprog)
Además del PTY, Serprog sufría de otro problema. Flashrom intentaba leer toda la capacidad del chip en comandos gigantes. El Arduino enviaba todos estos datos ininterrumpidamente, desbordando el pequeño buffer de hardware (128 bytes) del chip CH340G si Android se demoraba en leer, ocasionando la pérdida de bytes.

*(Nota: Bus Pirate no sufría este problema específico de overrun gracias a sus chips FTDI/PIC dedicados que respetan los límites de lectura, pero sí era víctima de la corrupción PTY A).*

## 3. La Solución Definitiva (Implementación Tripartita)

La solución requirió correcciones en tres frentes para aislar la comunicación:

### Fix 1: El "Dummy Slave FD" en la Capa JNI de Android (`native-lib.cpp`)
Se modificó la creación del PTY para no cerrar el FD esclavo tras aplicar el modo RAW. Se envía el FD "Dummy" de regreso a Java. Al mantener siempre vivo al menos un FD conectado al esclavo, el kernel de Linux asume que el terminal sigue en uso y **respeta la configuración RAW**, impidiendo que vuelva a modo "Cooked".

### Fix 2: El Backend de Java (`PtyBridge.java`)
La capa Java captura el descriptor Dummy del Esclavo y lo mantiene vivo artificialmente (`dummySlaveFd`) durante toda la vida útil de la conexión USB y la sesión flashrom, cerrándolo solo al finalizar de manera segura.
*Al implementar esto a nivel global en el puente, tanto `buspirate_spi`, `serprog` como `spidriver` heredan automáticamente la inmunidad total al procesamiento del Kernel, transitando datos binarios de forma 100% fiable.*

### Fix 3: En el Firmware Arduino (`serprog_arduino_uno_ch340g.ino`)
Para arreglar el overrun del CH340, se implementaron los comandos `S_CMD_Q_WRNMAXLEN` (0x08) y `S_CMD_Q_RDNMAXLEN` (0x11) limitando las ráfagas a 64 bytes. Esto obliga a flashrom a particionar los bloques, garantizando que el hardware no colapse bajo buffers masivos.

---

## 4. Validación Adicional: Soporte de Emulación de SPIDriver

Se ha integrado en la suite de pruebas locales la emulación del programador **SPIDriver** (`spidriver`), el cual opera nativamente a **460800 bps**. 

Al hacer uso del canal virtual PTY y beneficiarse del mecanismo **Dummy Slave FD** en `PtyBridge`, se ha verificado que la negociación inicial (handshake ASCII `?` y eco `e`) junto con el envío masivo de bloques SPI (`0x80` y `0xC0`) no sufre ningún tipo de corrupción ni desalineación de bytes. Las operaciones de lectura y escritura sobre el chip de memoria virtual GD25Q80 completaron con éxito rotundo (`VERIFIED` y comparación binaria 100% coincidente).

Con estas soluciones aplicadas globalmente en la infraestructura PTY, la comunicación con **Serprog**, **Bus Pirate** y **SPIDriver** es ahora completamente robusta, segura y libre de corrupción.
