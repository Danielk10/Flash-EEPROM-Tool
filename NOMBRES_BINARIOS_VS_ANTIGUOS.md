# Nombres Nuevos vs Antiguos (Binarios Ejecutables)

De acuerdo a la verificación del archivo `REPORTE_ANALISIS_DEPENDENCIAS.md` y a la separación estricta entre binarios (ejecutables) y librerías compartidas (`.so`), a continuación se presenta la relación de los binarios reales de la aplicación.

Google Play requiere que todo archivo dentro de `jniLibs/<abi>/` tenga el formato `lib<nombre>.so`. Por ello, los binarios han sido renombrados en las carpetas `jniLibs/arm64-v8a/` y `jniLibs/armeabi-v7a/`, y el código de la app (`AssetHelper.java`) se encarga de extraerlos/enlazarlos en las rutas exactas requeridas en el *fake_root* de la aplicación en tiempo de ejecución.

> **ABIs soportadas:** `arm64-v8a` (64 bits) y `armeabi-v7a` (32 bits). Ambas carpetas contienen **exactamente los mismos 24 nombres de archivo**; lo único que cambia es la arquitectura del contenido (`ELF64 AArch64` frente a `ELF32 ARM`). `app/build.gradle` declara las dos en `abiFilters`.

## 1. Mapeo de Binarios Ejecutables

Solo estos 5 archivos son los verdaderos binarios ejecutables PIE descritos en `REPORTE_ANALISIS_DEPENDENCIAS.md`. Todos han sido renombrados en `arm64-v8a` y `armeabi-v7a` para cumplir con las políticas de Google Play, y son reconstruidos en las rutas exactas del `fake_root` por `AssetHelper.java`.

| Nombre Antiguo (Original) | Nombre Nuevo (Google Play) en `jniLibs/<abi>` | Ruta exacta reconstruida en App (`fake_root`) |
|---|---|---|
| `flashrom` | `libflashrom_bin.so` | `usr/sbin/flashrom` |
| `ftdi_eeprom` | `libftdi_eeprom.so` | `usr/bin/ftdi_eeprom` |
| `lspci` | `liblspci.so` | `usr/bin/lspci` |
| `pcilmr` | `libpcilmr.so` | `usr/sbin/pcilmr` |
| `setpci` | `libsetpci.so` | `usr/sbin/setpci` |

`<abi>` es `arm64-v8a` o `armeabi-v7a` en todos los casos.

---

## 2. Dependencias de los Binarios

Todas las dependencias listadas en el reporte han sido verificadas dentro de `jniLibs/arm64-v8a/` y de `jniLibs/armeabi-v7a/`. Las librerías que originalmente tenían versionado (como `.so.3`) también han sido renombradas (`_3.so`) para ser válidas en Google Play, y la app restaura sus SONAMEs originales (`.so.3`) como enlaces simbólicos en `usr/lib/`.

| Binario | Dependencias Exigidas (DT_NEEDED) | Estado en `jniLibs/<abi>` / App |
|---|---|---|
| **`flashrom`** | `libcrypto.so.3`, `libpci.so.3`, `libusb-1.0.so`, `libftdi1.so.2`, `libjaylink.so`, `libc.so` | ✅ Todas las dependencias presentes. (`libcrypto_3.so`, `libpci_3.so`, `libusb_1_0.so`, `libftdi1_2.so`, etc.) |
| **`ftdi_eeprom`** | `liblog.so`, `libftdi1.so.2`, `libconfuse.so`, `libusb-1.0.so`, `libdl.so`, `libc.so` | ✅ Todas las dependencias presentes. (`libusb_1_0.so`, etc.) |
| **`lspci`** | `libpci.so.3`, `libdl.so`, `libc.so` | ✅ Todas las dependencias presentes. |
| **`pcilmr`** | `libpci.so.3`, `libdl.so`, `libc.so` | ✅ Todas las dependencias presentes. |
| **`setpci`** | `libpci.so.3`, `libdl.so`, `libc.so` | ✅ Todas las dependencias presentes. |

El ejecutable `flashrom` necesita además `libpci.so.3`, que se empaqueta como `libpci.so`, `libpci_3.so` y `libpci_3_15_0.so` (los tres son el mismo archivo; el sufijo `_3_15_0` refleja la versión 3.15.0 de pciutils).

---

## 3. Árbol de compilación: rutas nuevas por ABI

Los scripts de build (`build_flashrom_custom.sh`, `build_libusb_custom.sh`, `build_libftdi_custom.sh`, `build_libjaylink_custom.sh`, `build_pciutils_custom.sh`) aceptan la variable `ABI` y generan el árbol correspondiente:

```bash
ABI=64 ./build_flashrom_custom.sh   # por defecto
ABI=32 ./build_flashrom_custom.sh
```

| Ruta nueva | Contenido | Ruta antigua eliminada |
|---|---|---|
| `fake_root_64_bit/` | Árbol de instalación ARM64 (`usr/bin`, `usr/sbin`, `usr/lib`, `usr/include`, `usr/share`) | `fake_root/` |
| `fake_root_32_bit/` | El mismo árbol para ARMv7 | — |
| `libs_64/` | Librerías de Termux ARM64 (`libcrypto.so.3`, `libconfuse.so`, `libc++_shared.so`, `libz.so.1`) | `libs/` |
| `libs_32/` | Las mismas librerías para ARMv7 (`libz.so.1` es un symlink a `libz.so.1.4`) | — |
| `app/src/main/jniLibs/arm64-v8a/` | 24 archivos `lib*.so` ARM64 | (sin cambio) |
| `app/src/main/jniLibs/armeabi-v7a/` | Los mismos 24 nombres en ARMv7 | — |

Dentro de cada `fake_root_<abi>` las rutas internas siguen el prefijo de la app:

```
fake_root_64_bit/data/data/com.diamon.curso/files/usr/{bin,sbin,lib,include,share}
fake_root_32_bit/data/data/com.diamon.curso/files/usr/{bin,sbin,lib,include,share}
```

Ese prefijo es intencionadamente idéntico en ambas arquitecturas: es la ruta canónica que esperan los binarios y que `AssetHelper` reproduce en tiempo de ejecución (`/data/user/0/com.diamon.curso/files/usr`).

### Origen de cada archivo en `jniLibs/armeabi-v7a/`

| Archivo en `jniLibs/armeabi-v7a/` | Origen |
|---|---|
| `libflashrom_bin.so` | `fake_root_32_bit/.../usr/sbin/flashrom` |
| `liblspci.so`, `libftdi_eeprom.so` | `fake_root_32_bit/.../usr/bin/{lspci,ftdi_eeprom}` |
| `libsetpci.so`, `libpcilmr.so` | `fake_root_32_bit/.../usr/sbin/{setpci,pcilmr}` |
| `libflashrom.so`, `libflashrom_1.so`, `libflashrom_1_0_0.so` | `fake_root_32_bit/.../usr/lib/libflashrom.so` |
| `libpci.so`, `libpci_3.so`, `libpci_3_15_0.so` | `fake_root_32_bit/.../usr/lib/libpci.so.3.15.0` |
| `libftdi1.so`, `libftdi1_2.so`, `libftdi1_2_6_0.so` | `fake_root_32_bit/.../usr/lib/libftdi1.so.2.6.0` |
| `libftdipp1.so`, `libftdipp1_3.so`, `libftdipp1_2_6_0.so` | `fake_root_32_bit/.../usr/lib/libftdipp1.so.3` |
| `libusb_1_0.so` | `fake_root_32_bit/.../usr/lib/libusb-1.0.so` |
| `libjaylink.so` | `fake_root_32_bit/.../usr/lib/libjaylink.so` |
| `libpyftdi1.so` | `fake_root_32_bit/.../usr/lib/python3.14/site-packages/_pyftdi1.so` |
| `libcrypto_3.so`, `libconfuse.so`, `libc++_shared.so`, `libz_1.so` | `libs_32/` |

La variante `arm64-v8a` se obtiene con exactamente la misma correspondencia partiendo de `fake_root_64_bit/` y `libs_64/`.

---

## 4. Estado de Archivos en `assets` vs `fake_root`

Al revisar y comparar las carpetas `fake_root_32_bit` y `fake_root_64_bit` contra los archivos incluidos en `assets`, se confirmó que:

1. **Binarios y Librerías (.so):** No están en `assets`. Están correctamente ubicados en `app/src/main/jniLibs/arm64-v8a/` y `app/src/main/jniLibs/armeabi-v7a/`. Durante la ejecución de la app, se enlazan (symlinks) o copian a la jerarquía de directorios esperada (`usr/bin`, `usr/sbin`, `usr/lib`). Android entrega al proceso únicamente el directorio nativo de su ABI, por lo que `AssetHelper` resuelve los nombres dentro de `context.getApplicationInfo().nativeLibraryDir`.
2. **Scripts Shell y Otros Recursos:** Herramientas como `update-pciids` o `libftdi1-config` no son binarios ELF (son scripts shell). Éstos están localizados correctamente en `assets/` y se extraen directamente a `usr/sbin/` y `usr/bin/` al iniciarse la app. Son independientes de la arquitectura.
3. **Resto de archivos en assets:** Se incluyen correctamente `pci.ids.gz`, HTML, y manuales sin faltar ningún archivo de sus respectivas rutas.

---

## 5. Verificación

El script `analizar_dependencias.py` recorre ambas carpetas y regenera `REPORTE_ANALISIS_DEPENDENCIAS.md`:

```bash
python3 analizar_dependencias.py
```

Comprueba, para cada ABI:

- Que la clase ELF y la máquina coincidan (`ELF64 AArch64` / `ELF32 ARM`).
- Que el SONAME de cada archivo se resuelva a su nombre en `jniLibs` aplicando la misma lógica que `AssetHelper.getNativeCandidates()`.
- Que cada dependencia `DT_NEEDED` esté empaquetada o la provea Android (`libc.so`, `libm.so`, `libdl.so`, `liblog.so`).

Salida esperada: `Verificación exitosa: 32 y 64 bits con todas las dependencias presentes.` (código de salida 0).
