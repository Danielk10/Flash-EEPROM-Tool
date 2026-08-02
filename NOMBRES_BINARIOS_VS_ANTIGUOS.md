# Nombres Nuevos vs Antiguos (Binarios Ejecutables)

De acuerdo a la verificación del archivo `REPORTE_ANALISIS_DEPENDENCIAS.md` y a la separación estricta entre binarios (ejecutables) y librerías compartidas (`.so`), a continuación se presenta la relación de los binarios reales de la aplicación. 

Google Play requiere que todo archivo dentro de `jniLibs/arm64-v8a/` tenga el formato `lib<nombre>.so`. Por ello, los binarios han sido renombrados en la carpeta, y el código de la app (`AssetHelper.java`) se encarga de extraerlos/enlazarlos en las rutas exactas requeridas en el *fake_root* de la aplicación en tiempo de ejecución.

## 1. Mapeo de Binarios Ejecutables

Solo estos 5 archivos son los verdaderos binarios ejecutables PIE descritos en `REPORTE_ANALISIS_DEPENDENCIAS.md`. Todos han sido renombrados en `arm64-v8a` para cumplir con las políticas de Google Play, y son reconstruidos en las rutas exactas del `fake_root` por `AssetHelper.java`.

| Nombre Antiguo (Original) | Nombre Nuevo (Google Play) en `arm64-v8a` | Ruta exacta reconstruida en App (`fake_root`) |
|---|---|---|
| `flashrom` | `libflashrom_bin.so` | `usr/sbin/flashrom` |
| `ftdi_eeprom` | `libftdi_eeprom.so` | `usr/bin/ftdi_eeprom` |
| `lspci` | `liblspci.so` | `usr/bin/lspci` |
| `pcilmr` | `libpcilmr.so` | `usr/sbin/pcilmr` |
| `setpci` | `libsetpci.so` | `usr/sbin/setpci` |

---

## 2. Dependencias de los Binarios

Todas las dependencias listadas en el reporte han sido verificadas dentro de `jniLibs/arm64-v8a/`. Las librerías que originalmente tenían versionado (como `.so.3`) también han sido renombradas (`_3.so`) para ser válidas en Google Play, y la app restaura sus SONAMEs originales (`.so.3`) como enlaces simbólicos en `usr/lib/`.

| Binario | Dependencias Exigidas (DT_NEEDED) | Estado en `arm64-v8a` / App |
|---|---|---|
| **`flashrom`** | `libcrypto.so.3`, `libpci.so.3`, `libusb-1.0.so`, `libftdi1.so.2`, `libjaylink.so`, `libc.so` | ✅ Todas las dependencias presentes. (`libcrypto_3.so`, `libpci_3.so`, `libftdi1_2.so`, etc.) |
| **`ftdi_eeprom`** | `liblog.so`, `libftdi1.so.2`, `libconfuse.so`, `libusb-1.0.so`, `libdl.so`, `libc.so` | ✅ Todas las dependencias presentes. |
| **`lspci`** | `libpci.so.3`, `libdl.so`, `libc.so` | ✅ Todas las dependencias presentes. |
| **`pcilmr`** | `libpci.so.3`, `libdl.so`, `libc.so` | ✅ Todas las dependencias presentes. |
| **`setpci`** | `libpci.so.3`, `libdl.so`, `libc.so` | ✅ Todas las dependencias presentes. |

---

## 3. Estado de Archivos en `assets` vs `fake_root`

Al revisar y comparar la carpeta `fake_root` contra los archivos incluidos en `assets`, se confirmó que:

1. **Binarios y Librerías (.so):** No están en `assets`. Están correctamente ubicados en `app/src/main/jniLibs/arm64-v8a/`. Durante la ejecución de la app, se enlazan (symlinks) o copian a la jerarquía de directorios esperada (`usr/bin`, `usr/sbin`, `usr/lib`).
2. **Scripts Shell y Otros Recursos:** Herramientas como `update-pciids` o `libftdi1-config` no son binarios ELF (son scripts shell). Éstos están localizados correctamente en `assets/` y se extraen directamente a `usr/sbin/` y `usr/bin/` al iniciarse la app.
3. **Resto de archivos en assets:** Se incluyen correctamente `pci.ids.gz`, HTML, y manuales sin faltar ningún archivo de sus respectivas rutas.
