# Reporte de Solución: Desalineación de Bytes en Lecturas Masivas (Bulk Reads)

Este documento detalla el análisis y la solución definitiva a un bug crítico donde la aplicación fallaba repetidamente al intentar leer de la memoria flash a través de un programador serprog basado en Arduino.

## 1. El Síntoma del Problema

Durante lecturas masivas de memoria (ej. volcados de 1 MB o superiores), el proceso fallaba aleatoriamente devolviendo mensajes de error en los que se obtenía un código de respuesta inválido, como:

`Reading flash... Error: invalid response 0x01 from device (to command 0x13)`
`Reading flash... Error: invalid response 0xA0 from device (to command 0x13)`

El protocolo Serprog dictamina que al comando `0x13` (operación SPI), el Arduino debe responder siempre con un byte de `ACK` (`0x06`), seguido de los bytes de datos leídos. Sin embargo, el host (la app Android y flashrom) estaba leyendo bytes aleatorios (como `0x01` o `0xA0`) cuando esperaba el `ACK`. Esto indicaba claramente que el flujo de datos se estaba desalineando o perdiendo bytes enteros de forma silenciosa.

## 2. Análisis de las Causas (Eran Dos Problemas Simultáneos)

Tras un extenso proceso de debugeo del puente Android PTY↔USB y el firmware del Arduino, se determinó que no existía una sola causa, sino dos bugs sutiles interactuando entre sí:

### A. Desbordamiento del Buffer UART Hardware del CH340 (Arduino)
La causa principal de la pérdida masiva de bytes radicaba en la falta de limitación del tamaño de lectura:
- El protocolo serprog permite consultar al programador cuál es su tamaño máximo de lectura (`S_CMD_Q_RDNMAXLEN`, `0x11`).
- Nuestro código original `.ino` no implementaba este comando.
- Al no conocer un límite, `flashrom` intentaba leer toda la capacidad del chip de memoria en comandos gigantes (ej. enviar el comando `0x13` pidiendo miles o incluso el megabyte entero de datos de un solo golpe).
- El microcontrolador del Arduino es rápido y volcaba todos estos datos al conversor USB-Serial (CH340G) de manera ininterrumpida.
- **El Fallo:** El chip CH340 tiene un buffer interno de hardware minúsculo (aprox. 128 bytes). Si el sistema operativo host (Android, que es multitarea y propenso a micro-pausas) tardaba unos cuantos milisegundos en recolectar los datos desde el USB, el Arduino llenaba el buffer del CH340, y éste empezaba a descartar y botar a la basura los bytes más recientes (UART Overrun). Esto desplazaba por completo los bytes de la comunicación.

### B. Corrupción del Modo RAW en PTY por Line Discipline del Kernel (Android/Linux)
Aún si se enviaban pocos bytes, ocurría otra corrupción en la forma que Linux (Android) manejaba el Pseudo-Terminal (PTY) que conecta Java con flashrom:
- Creamos el PTY, le aplicamos `cfmakeraw` para que opere como un túnel binario puro y luego, equivocadamente, cerrábamos el descriptor de archivo (FD) esclavo temporal de nuestro lado para que `flashrom` lo abriera después.
- **El Fallo:** En la arquitectura de Linux, cuando se cierra el último FD asociado a un lado esclavo de un PTY, el kernel de forma automática revierte la configuración del terminal a su estado original (Modo "Cooked").
- Cuando `flashrom` abría el terminal, éste estaba operando en modo texto con la regla "Line Discipline" activa. Si el Arduino leía bytes aleatorios de la memoria flash que tuvieran el valor `0x11` (XON), `0x13` (XOFF) o combinaciones de retornos de carro, el kernel de Linux los interceptaba, los procesaba como comandos de control de terminal, y los extraía o mutaba del flujo de datos. Esto causaba un nuevo desplazamiento de bytes.

## 3. La Solución Definitiva (Implementación Tripartita)

### Fix 1: En el Firmware Arduino (`.ino`)
Se implementaron los comandos `S_CMD_Q_WRNMAXLEN` (0x08) y `S_CMD_Q_RDNMAXLEN` (0x11) para forzar a flashrom a particionar los bloques. Declarar que el buffer máximo es 64 bytes asegura que flashrom jamás le pedirá más de 64 bytes seguidos, evitando que el CH340 desborde su buffer de hardware mientras espera que Android lea el USB. Adicionalmente, el byte de ACK y los datos se envían en un bloque atómico.

```cpp
    case 0x08: { // Query Maximum Write Length
      uint8_t resp[4] = {S_ACK, 32, 0x00, 0x00}; // Límite de 32 bytes
      Serial.write(resp, 4);
      Serial.flush();
      break;
    }

    case 0x11: { // Query Maximum Read Length
      uint8_t resp[4] = {S_ACK, 64, 0x00, 0x00}; // Límite de 64 bytes
      Serial.write(resp, 4);
      Serial.flush();
      break;
    }
```

### Fix 2: En la Capa JNI de Android (`native-lib.cpp`)
Se modificó la creación del PTY para no cerrar el FD esclavo tras aplicar el modo RAW. Se envía el FD "Dummy" de regreso a Java. Al mantener siempre vivo al menos un FD conectado al esclavo, el kernel de Linux se ve impedido de restaurar la configuración a modo "Cooked", garantizando que los datos binarios no sufran alteraciones.

```cpp
    int slaveFd = open(slavePath, O_RDWR | O_NOCTTY);
    if (slaveFd >= 0) {
        if (tcgetattr(slaveFd, &tio) == 0) {
            cfmakeraw(&tio);
            tcsetattr(slaveFd, TCSANOW, &tio);
            LOGI("PTY slave configurado en modo RAW (FD guardado para mantener estado)");
        }
        // YA NO SE CIERRA AQUÍ: close(slaveFd);
    }
    // Retornamos el slaveFd a Java en un arreglo de 3 elementos.
```

### Fix 3: En el Backend de Java (`PtyBridge.java`)
Se captura el descriptor Dummy del Esclavo y se resguarda dentro del ciclo de vida del puente, asegurándose de que sólo se cierre de forma segura cuando el puente y la sesión flashrom hayan concluido totalmente.

```java
    // -------- Estado --------
    private int dummySlaveFd = -1;

    // Al arrancar:
    dummySlaveFd = Integer.parseInt(ptyResult[2]);

    // Al finalizar (cleanup):
    if (dummySlaveFd >= 0) {
        closeFd(dummySlaveFd);
        dummySlaveFd = -1;
    }
```

Con estos 3 frentes abarcados, el flujo puenteado PTY ↔ Java ↔ USB Serial se vuelve inmune tanto al procesamiento del Kernel de Linux como a los desbordamientos (overruns) en buffers microcontroladores.

## 4. Actualizaciones Adicionales (Release v1.7.1)

Durante la preparación de la versión definitiva `v1.7.1`, se integraron y documentaron las siguientes mejoras en base a la solidez alcanzada con la comunicación PTY:

### A. Integración de la Barra de Progreso (`--progress`)
Se comprobó que las versiones modernas de `flashrom` aceptan el flag oculto `--progress`, el cual imprime en *stdout* el avance porcentual (ej. `[READ:  45%]...`). 
En la aplicación Android, tanto los botones de la interfaz gráfica (Leer, Escribir, Borrar, Verificar) como el cuadro de comandos manuales, ahora **inyectan automáticamente** este flag. Como la tubería de ejecución de la app ya captura *stdout/stderr* del binario y los redirige al visor negro de la UI, los usuarios ahora pueden monitorear visualmente el progreso sin modificaciones complejas en JNI.

### B. Evaluación del Programador Bus Pirate (`buspirate_spi`)
Se realizó una validación sobre el programador Bus Pirate:
- **Overrun (Buffer UART):** A diferencia de Arduino/CH340, los Bus Pirate (que utilizan internamente micros dedicados con chips FTDI o hardware nativo USB-Serial) gestionan correctamente el control de flujo y los límites de su protocolo propio. Por consiguiente, no sufren la desincronización por "overrun" de latencia.
- **Corrupción PTY:** Sí se veían afectados previamente por el problema del Line Discipline del kernel de Linux (los bytes binarios que coincidían con `0x11` o `0x13` eran capturados por el sistema operativo).
- **Conclusión:** Gracias a la solución universal del **Dummy Slave FD** implementada para el `serprog`, el programador `buspirate_spi` (y `spidriver`) automáticamente hereda la estabilidad absoluta y su terminal se conserva estrictamente en modo `RAW`.

### C. Localización Total y Preparación para Producción
1. **Localización de Cadenas de Texto:** Se extrajeron todas las cadenas de log (ej. `"Hilos de forwarding activos"`, `"Escribiendo flash"`) a los archivos de recursos `strings.xml`. Se implementó el correcto escapado XML de comillas simples (`"Fallo crítico: Binario \'flashrom\' no existe"`) que el compilador de Android requiere. Ambas versiones, Inglés y Español, se encuentran 100% sincronizadas.
2. **Compilación de Release:** La versión final se empaquetó exitosamente a través de `assembleRelease`. Para ello se debió generar un *Keystore* local y configurar `keystore.properties` temporalmente. El resultado es un APK `app-release.apk` totalmente ofuscado y sin símbolos de depuración, el cual reemplazó a todos los pre-lanzamientos y binarios debug en el repositorio oficial.
