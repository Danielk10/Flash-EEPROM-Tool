# Guía de Resolución: Problemas de PTY (Line Discipline) en Programadores Seriales

Este documento aborda y explica la solución al problema de corrupción de datos binarios (desalineación) que afectaba a todos los programadores de `flashrom` basados en puerto serie que se comunicaban a través del túnel o puente PTY (Pseudo-Terminal) en Android.

## El Problema del PTY en Android/Linux

La aplicación Flash EEPROM Tool utiliza un puente PTY bidireccional (`PtyBridge`) para hacer creer a `flashrom` que está hablando con un puerto serial físico `/dev/ttyUSBX`, cuando en realidad es un PTY esclavo conectado a la API de `UsbManager` de Android.

Aunque el lado maestro del PTY se configuraba meticulosamente en modo binario `RAW` usando `cfmakeraw(&tio)`, se presentaba un fallo crítico:

1. El hilo maestro abría el PTY y lo configuraba en `RAW`.
2. El hilo maestro cerraba *temporalmente* el descriptor de archivo (FD) esclavo tras configurarlo, asumiendo que `flashrom` lo abriría milisegundos después.
3. **El Fallo del Kernel:** En Linux y Android, cuando se cierra el último FD asociado a un lado esclavo de un PTY, el kernel resetea automáticamente la configuración del terminal a sus valores por defecto (Modo "Cooked" / Line Discipline habilitada).
4. Cuando `flashrom` abría su extremo, el PTY estaba procesando caracteres de control.

### Consecuencias de la Regla "Line Discipline"
Si la memoria flash contenía bytes binarios específicos, como `0x11` (XON), `0x13` (XOFF), `0x0D` (CR) o `0x0A` (LF), el sistema operativo los interceptaba. El kernel pensaba que un humano estaba tipeando atajos de teclado en una terminal de texto y procedía a mutar, descartar o pausar el flujo de datos. Esto destruía la integridad del archivo binario leído.

---

## Solución: El "Dummy Slave FD"

La solución implementada radicó en **jamás dejar que el kernel cierre por completo el esclavo**. 

1. En la capa JNI (C++), cuando se crea el PTY, abrimos un FD hacia el esclavo (`dummySlaveFd`), configuramos el modo `RAW`, y en lugar de cerrarlo, lo retornamos a Java.
2. La capa Java (`PtyBridge.java`) almacena este FD y lo mantiene vivo artificialmente durante toda la vida útil de la conexión USB.
3. Al existir siempre al menos un FD apuntando al esclavo, el kernel asume que el terminal sigue en uso y **respeta la configuración `RAW`**.

---

## Impacto en Programadores: El Caso Bus Pirate (`buspirate_spi`)

Aunque originalmente este error fue descubierto mientras se analizaba el Arduino (`serprog`), el problema era intrínseco de Linux, no del hardware USB. Por ende, **el fallo de PTY afectaba a todos los programadores seriales.**

### Bus Pirate
El Bus Pirate (v3, v4) es un hardware dedicado que no sufre del desbordamiento de búfer UART (Overrun) que afectaba al Arduino CH340, ya que emplea micros dedicados (FTDI o PICs) que respetan sus límites de lectura y flujo de hardware.

Sin embargo, **el Bus Pirate SÍ era víctima de la corrupción por PTY**. Al enviar volcados completos de BIOS, si la memoria leída por el Bus Pirate contenía secuencias `0x11` o `0x13`, el puente PTY de Android descartaba los bytes antes de que `flashrom` los viera.

**Resultado de la Corrección:**
Al implementar la solución del *Dummy Slave FD* a nivel global en el `PtyBridge`, el programador `buspirate_spi` (así como `spidriver`) **hereda automáticamente la inmunidad total**. El flujo de datos del Bus Pirate ahora transita íntegramente de manera binaria, convirtiéndolo en un programador 100% fiable dentro de la app Android, incluso para volcados de 16 MB.
