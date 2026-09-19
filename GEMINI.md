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

