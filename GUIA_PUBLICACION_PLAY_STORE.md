# Guía de Automatización para Google Play Store 🚀

Esta guía explica cómo utilizar el script `upload_play_store.py` para publicar actualizaciones automáticamente en la Google Play Store para **cualquier proyecto Android**.

## 🛠 Requisitos Previos

Antes de ejecutar el script, asegúrate de tener instaladas las librerías oficiales de Google para Python:

```bash
pip install google-api-python-client google-auth-httplib2 google-auth-oauthlib
```

## 📂 Ubicación de los Archivos

En esta carpeta (`/home/danielpdiamon/`) cuentas con dos archivos clave:
1. **`upload_play_store.py`**: El script universal de publicación.
2. **`pc-api-6650547003605444910-569-9d23413fdc95.json`**: Tus credenciales de acceso de la cuenta de servicio (¡No compartas este archivo ni lo subas a GitHub!).

## 🚀 Cómo usar el script

Para subir una nueva versión de tu aplicación, primero debes compilar tu archivo `.aab` o `.apk` firmado. Luego, abre la terminal y ejecuta el script pasándole los parámetros correspondientes a tu aplicación.

### Formato del comando

```bash
python /home/danielpdiamon/upload_play_store.py \
  --package_name <PAQUETE_DE_LA_APP> \
  --aab_path <RUTA_AL_ARCHIVO_AAB> \
  --service_account_json /home/danielpdiamon/pc-api-6650547003605444910-569-9d23413fdc95.json \
  --track <PISTA_DE_PUBLICACION> \
  --release_notes "- Primera mejora relevante.
- Segunda corrección técnica.
- Mejoras generales de rendimiento." \
  --release_notes_en "- First key improvement.
- Second technical fix.
- General performance improvements."
```

### Ejemplo de uso (Plantilla)

Si tienes una aplicación llamada `com.mi.nueva.app` y acabas de generar el archivo `app-release.aab`, el comando sería:

```bash
python /home/danielpdiamon/upload_play_store.py \
  --package_name com.mi.nueva.app \
  --aab_path ./app/build/outputs/bundle/release/app-release.aab \
  --service_account_json /home/danielpdiamon/pc-api-6650547003605444910-569-9d23413fdc95.json \
  --track production \
  --release_notes "- Añadida compatibilidad con memorias Flash SPI de 8 MB.
- Corrección en comandos de emulación para flashrom nativo.
- Mejoras de rendimiento y estabilidad." \
  --release_notes_en "- Added support for 8 MB SPI Flash memories.
- Fixed emulation command compatibility for native flashrom.
- Performance and stability improvements."
```

### 🎛 Parámetros del Script

| Parámetro | Descripción | Ejemplo |
| :--- | :--- | :--- |
| `--package_name` | El ID de tu aplicación (Package Name) | `com.diamon.curso` |
| `--aab_path` | La ruta hacia tu archivo compilado `.aab` | `./Flash-EEPROM-Tool-v1.6.8.aab` |
| `--service_account_json` | La ruta a tu archivo JSON de credenciales | `/home/danielpdiamon/pc-api-....json` |
| `--track` | La pista donde quieres publicar (por defecto es `production`) | `production`, `beta`, `alpha`, o `internal` |
| `--release_notes` | Notas de versión estructuradas multilínea (`- `) para usuarios en español (`es-419` y `es-ES`) | `"- Cambio 1.\n- Cambio 2."` |
| `--release_notes_en` | (Opcional) Notas de versión estructuradas multilínea (`- `) para usuarios en inglés (`en-US`) | `"- Change 1.\n- Change 2."` |

## 💡 Notas Importantes
* **Seguridad:** El script **nunca** debe tener contraseñas escritas directamente en el código. Siempre debe leer el archivo JSON por parámetro.
* **Permisos:** La cuenta de servicio (JSON) debe tener permisos concedidos en tu consola de Google Play ("Administrar lanzamientos") para cada aplicación nueva que quieras subir con este script.
* **Firma:** El archivo `.aab` siempre debe haber sido firmado previamente (con un archivo `.jks` o `.keystore`) antes de ejecutar el script.

---

## 🔑 Cómo Firmar tu Aplicación para Producción

Antes de poder usar el script de subida, necesitas generar un archivo `.aab` o `.apk` que esté firmado con tu clave privada de producción (tu archivo `.jks` que está guardado en `/home/danielpdiamon/`).

Sigue estos pasos en cualquier proyecto de Android que quieras firmar:

### 1. Actualiza tu `app/build.gradle`
Abre el archivo `app/build.gradle` del proyecto y añade el bloque `signingConfigs` justo dentro del bloque `android { ... }` y asígnalo al `buildTypes { release { ... } }`:

```groovy
android {
    // ... (otras configuraciones) ...

    signingConfigs {
        release {
            // Ruta a tu archivo de firma (ajústala si es diferente)
            storeFile file("/home/danielpdiamon/Juego Java 30 Pasos.jks")
            storePassword "20270806Sa"
            keyAlias "Juego Java 30 Pasos"
            keyPassword "20270806Sa"
        }
    }

    buildTypes {
        release {
            // Se le indica a Gradle que use la firma para la versión release
            signingConfig signingConfigs.release
            minifyEnabled false
            proguardFiles getDefaultProguardFile('proguard-android-optimize.txt'), 'proguard-rules.pro'
        }
    }
}
```

### 2. Protege tus firmas de Git (Recomendado)
Asegúrate siempre de que tu archivo `.jks` y `.json` no se suban accidentalmente a GitHub (especialmente si los mueves dentro de la carpeta del proyecto). Añade esto a tu archivo `.gitignore`:
```text
# Excluir claves y credenciales
*.jks
*.keystore
*.json
```

### 3. Compila y Genera el Archivo Firmado
Abre tu terminal en la carpeta del proyecto y ejecuta el siguiente comando de Gradle:

```bash
# Para generar el AAB (App Bundle) de producción para Google Play
./gradlew bundleRelease

# Si también necesitas el APK
./gradlew assembleRelease
```

Cuando termine, los archivos generados y ya firmados se encontrarán en las carpetas de compilación, listos para que los pases por el script `upload_play_store.py`:
* **AAB:** `app/build/outputs/bundle/release/app-release.aab`
* **APK:** `app/build/outputs/apk/release/app-release.apk`
