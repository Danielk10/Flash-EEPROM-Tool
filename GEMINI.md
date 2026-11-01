# Instrucciones de Compilación y SDK

Este documento describe cómo instalar el SDK de Android y compilar el proyecto **Flash-SPI-Tool**.

## 1. Instalación del SDK

El SDK de Android necesario para compilar este proyecto se instala automáticamente ejecutando el script proporcionado:

```bash
bash setup-sdk.sh
```

- **Ubicación del SDK:** Todas las descargas y herramientas del SDK (incluyendo NDK y CMake) se instalan temporalmente en el directorio `/tmp/android-sdk`.

## 2. Compilación

Una vez que el script `setup-sdk.sh` termine y el SDK esté listo, el proyecto se puede compilar ejecutando Gradle:

```bash
./gradlew assembleDebug
```

## 3. Ubicación del APK Generado

Después de una compilación exitosa, el archivo APK generado se encontrará en la siguiente ruta absoluta fuera del proyecto para mantener el espacio de trabajo limpio:

```
/tmp/flashrom/outputs/apk/debug/app-debug.apk
```

## 4. Notas de Versión para Google Play (Bilingüe Obligatorio)

Siempre que se prepare un lanzamiento o se suba una actualización a **Google Play**, es obligatorio generar y proporcionar las notas de versión estructuradas tanto en **Español** (`es-419` / `es-ES`) como en **Inglés** (`en-US`), usando viñetas concisas (`- Elemento`) compatibles con el límite de caracteres de Google Play Console.

### Ciclo de vida de las notas de versión

- **Solo se conserva la nota de la última versión.** El repositorio debe contener un único archivo `RELEASE_NOTES_vX.Y.Z.md`, correspondiente a la versión que se está publicando.
- **Eliminar las notas obsoletas.** Al preparar un nuevo lanzamiento, borra las notas de versiones anteriores (`RELEASE_NOTES_v*.md`) en lugar de acumularlas.
- **No crear históricos de notas.** El historial completo ya queda en Git, en las releases de GitHub y en Google Play; no hace falta mantener copias antiguas en el árbol de trabajo.
- **Verificar enlaces antes de borrar.** Comprueba que `README.md` y la documentación no enlacen a la nota que vas a eliminar; actualiza esos enlaces a la nota vigente.
- **No exponer datos de firma.** Las notas públicas (GitHub, Google Play, README) no deben incluir el nombre del keystore, su ruta, alias, contraseñas ni ningún detalle del certificado de firma.

## 5. Emulador Local y Pruebas

El emulador vive únicamente en `emulador_flashrom/`. No mantener copias de ejecución en `~/emulador_flashrom` ni duplicar `local_root`.

```bash
cd emulador_flashrom
./test_all_devices.sh
```

- `flashrom_local.sh` es autónomo: carga la `libusb` parcheada y `libftdi1` desde `local_root/lib`; no requiere `LD_LIBRARY_PATH` externo.
- La suite debe terminar con cinco `✅ [ÉXITO]`: Serprog, Bus Pirate, SPIDriver, CH341A y FTDI FT232H.
- No mezclar la `libusb` de flashrom con la de minipro (`~/native_test_root`): cada herramienta usa su propio parche.

