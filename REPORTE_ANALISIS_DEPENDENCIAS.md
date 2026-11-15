# Reporte de análisis de dependencias — 32 y 64 bits

Generado por `analizar_dependencias.py` el 2026-11-15.

Verifica las librerías de `app/src/main/jniLibs/<abi>/`: clase ELF, SONAME, nombre adaptado a Google Play y que cada dependencia `DT_NEEDED` se resuelva dentro de la misma carpeta o la provea Android.

## Resumen

| Arquitectura | ABI | Contenido | Estado |
|---|---|---|---|
| 32-bit | `armeabi-v7a` | 24 archivos | ✔ Completo |
| 64-bit | `arm64-v8a` | 24 archivos | ✔ Completo |

## 1. 32-bit (`armeabi-v7a`)

### Clase ELF

| Archivo | Detectado | Esperado | OK |
|---|---|---|---|
| `libc++_shared.so` | ELF32 / ARM | ELF32 / ARM | ✔ |
| `libconfuse.so` | ELF32 / ARM | ELF32 / ARM | ✔ |
| `libcrypto_3.so` | ELF32 / ARM | ELF32 / ARM | ✔ |
| `libflashrom.so` | ELF32 / ARM | ELF32 / ARM | ✔ |
| `libflashrom_1.so` | ELF32 / ARM | ELF32 / ARM | ✔ |
| `libflashrom_1_0_0.so` | ELF32 / ARM | ELF32 / ARM | ✔ |
| `libflashrom_bin.so` | ELF32 / ARM | ELF32 / ARM | ✔ |
| `libftdi1.so` | ELF32 / ARM | ELF32 / ARM | ✔ |
| `libftdi1_2.so` | ELF32 / ARM | ELF32 / ARM | ✔ |
| `libftdi1_2_6_0.so` | ELF32 / ARM | ELF32 / ARM | ✔ |
| `libftdi_eeprom.so` | ELF32 / ARM | ELF32 / ARM | ✔ |
| `libftdipp1.so` | ELF32 / ARM | ELF32 / ARM | ✔ |
| `libftdipp1_2_6_0.so` | ELF32 / ARM | ELF32 / ARM | ✔ |
| `libftdipp1_3.so` | ELF32 / ARM | ELF32 / ARM | ✔ |
| `libjaylink.so` | ELF32 / ARM | ELF32 / ARM | ✔ |
| `liblspci.so` | ELF32 / ARM | ELF32 / ARM | ✔ |
| `libpci.so` | ELF32 / ARM | ELF32 / ARM | ✔ |
| `libpci_3.so` | ELF32 / ARM | ELF32 / ARM | ✔ |
| `libpci_3_15_0.so` | ELF32 / ARM | ELF32 / ARM | ✔ |
| `libpcilmr.so` | ELF32 / ARM | ELF32 / ARM | ✔ |
| `libpyftdi1.so` | ELF32 / ARM | ELF32 / ARM | ✔ |
| `libsetpci.so` | ELF32 / ARM | ELF32 / ARM | ✔ |
| `libusb_1_0.so` | ELF32 / ARM | ELF32 / ARM | ✔ |
| `libz_1.so` | ELF32 / ARM | ELF32 / ARM | ✔ |

### SONAME → nombre en jniLibs

| Archivo | SONAME | Nombre que resuelve | Coincide |
|---|---|---|---|
| `libc++_shared.so` | `libc++_shared.so` | `libc++_shared.so` | ✔ |
| `libconfuse.so` | `libconfuse.so` | `libconfuse.so` | ✔ |
| `libcrypto_3.so` | `libcrypto.so.3` | `libcrypto_3.so` | ✔ |
| `libflashrom.so` | `libflashrom.so` | `libflashrom.so` | ✔ |
| `libflashrom_1.so` | `libflashrom.so` | `libflashrom.so` | ≈ |
| `libflashrom_1_0_0.so` | `libflashrom.so` | `libflashrom.so` | ≈ |
| `libflashrom_bin.so` | — | — | — |
| `libftdi1.so` | `libftdi1.so.2` | `libftdi1_2.so` | ≈ |
| `libftdi1_2.so` | `libftdi1.so.2` | `libftdi1_2.so` | ✔ |
| `libftdi1_2_6_0.so` | `libftdi1.so.2` | `libftdi1_2.so` | ≈ |
| `libftdi_eeprom.so` | — | — | — |
| `libftdipp1.so` | `libftdipp1.so.3` | `libftdipp1_3.so` | ≈ |
| `libftdipp1_2_6_0.so` | `libftdipp1.so.3` | `libftdipp1_3.so` | ≈ |
| `libftdipp1_3.so` | `libftdipp1.so.3` | `libftdipp1_3.so` | ✔ |
| `libjaylink.so` | `libjaylink.so` | `libjaylink.so` | ✔ |
| `liblspci.so` | — | — | — |
| `libpci.so` | `libpci.so.3` | `libpci_3.so` | ≈ |
| `libpci_3.so` | `libpci.so.3` | `libpci_3.so` | ✔ |
| `libpci_3_15_0.so` | `libpci.so.3` | `libpci_3.so` | ≈ |
| `libpcilmr.so` | — | — | — |
| `libpyftdi1.so` | — | — | — |
| `libsetpci.so` | — | — | — |
| `libusb_1_0.so` | `libusb-1.0.so` | `libusb_1_0.so` | ✔ |
| `libz_1.so` | `libz.so.1` | `libz_1.so` | ✔ |

### Dependencias `DT_NEEDED`

| Archivo | Dependencia | Tipo | Resolución |
|---|---|---|---|
| `libc++_shared.so` | `libc.so` | Sistema (Android) | ✔ /system/lib/ |
| `libc++_shared.so` | `libm.so` | Sistema (Android) | ✔ /system/lib/ |
| `libc++_shared.so` | `libdl.so` | Sistema (Android) | ✔ /system/lib/ |
| `libconfuse.so` | `libc.so` | Sistema (Android) | ✔ /system/lib/ |
| `libcrypto_3.so` | `libdl.so` | Sistema (Android) | ✔ /system/lib/ |
| `libcrypto_3.so` | `libc.so` | Sistema (Android) | ✔ /system/lib/ |
| `libflashrom.so` | `libpci.so.3` | Externa (empaquetada) | ✔ `libpci_3.so` |
| `libflashrom.so` | `libusb-1.0.so` | Externa (empaquetada) | ✔ `libusb_1_0.so` |
| `libflashrom.so` | `libftdi1.so.2` | Externa (empaquetada) | ✔ `libftdi1_2.so` |
| `libflashrom.so` | `libjaylink.so` | Externa (empaquetada) | ✔ `libjaylink.so` |
| `libflashrom.so` | `libc.so` | Sistema (Android) | ✔ /system/lib/ |
| `libflashrom_1.so` | `libpci.so.3` | Externa (empaquetada) | ✔ `libpci_3.so` |
| `libflashrom_1.so` | `libusb-1.0.so` | Externa (empaquetada) | ✔ `libusb_1_0.so` |
| `libflashrom_1.so` | `libftdi1.so.2` | Externa (empaquetada) | ✔ `libftdi1_2.so` |
| `libflashrom_1.so` | `libjaylink.so` | Externa (empaquetada) | ✔ `libjaylink.so` |
| `libflashrom_1.so` | `libc.so` | Sistema (Android) | ✔ /system/lib/ |
| `libflashrom_1_0_0.so` | `libpci.so.3` | Externa (empaquetada) | ✔ `libpci_3.so` |
| `libflashrom_1_0_0.so` | `libusb-1.0.so` | Externa (empaquetada) | ✔ `libusb_1_0.so` |
| `libflashrom_1_0_0.so` | `libftdi1.so.2` | Externa (empaquetada) | ✔ `libftdi1_2.so` |
| `libflashrom_1_0_0.so` | `libjaylink.so` | Externa (empaquetada) | ✔ `libjaylink.so` |
| `libflashrom_1_0_0.so` | `libc.so` | Sistema (Android) | ✔ /system/lib/ |
| `libflashrom_bin.so` | `libcrypto.so.3` | Externa (empaquetada) | ✔ `libcrypto_3.so` |
| `libflashrom_bin.so` | `libpci.so.3` | Externa (empaquetada) | ✔ `libpci_3.so` |
| `libflashrom_bin.so` | `libusb-1.0.so` | Externa (empaquetada) | ✔ `libusb_1_0.so` |
| `libflashrom_bin.so` | `libftdi1.so.2` | Externa (empaquetada) | ✔ `libftdi1_2.so` |
| `libflashrom_bin.so` | `libjaylink.so` | Externa (empaquetada) | ✔ `libjaylink.so` |
| `libflashrom_bin.so` | `libc.so` | Sistema (Android) | ✔ /system/lib/ |
| `libftdi1.so` | `liblog.so` | Sistema (Android) | ✔ /system/lib/ |
| `libftdi1.so` | `libusb-1.0.so` | Externa (empaquetada) | ✔ `libusb_1_0.so` |
| `libftdi1.so` | `libdl.so` | Sistema (Android) | ✔ /system/lib/ |
| `libftdi1.so` | `libc.so` | Sistema (Android) | ✔ /system/lib/ |
| `libftdi1_2.so` | `liblog.so` | Sistema (Android) | ✔ /system/lib/ |
| `libftdi1_2.so` | `libusb-1.0.so` | Externa (empaquetada) | ✔ `libusb_1_0.so` |
| `libftdi1_2.so` | `libdl.so` | Sistema (Android) | ✔ /system/lib/ |
| `libftdi1_2.so` | `libc.so` | Sistema (Android) | ✔ /system/lib/ |
| `libftdi1_2_6_0.so` | `liblog.so` | Sistema (Android) | ✔ /system/lib/ |
| `libftdi1_2_6_0.so` | `libusb-1.0.so` | Externa (empaquetada) | ✔ `libusb_1_0.so` |
| `libftdi1_2_6_0.so` | `libdl.so` | Sistema (Android) | ✔ /system/lib/ |
| `libftdi1_2_6_0.so` | `libc.so` | Sistema (Android) | ✔ /system/lib/ |
| `libftdi_eeprom.so` | `liblog.so` | Sistema (Android) | ✔ /system/lib/ |
| `libftdi_eeprom.so` | `libftdi1.so.2` | Externa (empaquetada) | ✔ `libftdi1_2.so` |
| `libftdi_eeprom.so` | `libconfuse.so` | Externa (empaquetada) | ✔ `libconfuse.so` |
| `libftdi_eeprom.so` | `libusb-1.0.so` | Externa (empaquetada) | ✔ `libusb_1_0.so` |
| `libftdi_eeprom.so` | `libdl.so` | Sistema (Android) | ✔ /system/lib/ |
| `libftdi_eeprom.so` | `libc.so` | Sistema (Android) | ✔ /system/lib/ |
| `libftdipp1.so` | `liblog.so` | Sistema (Android) | ✔ /system/lib/ |
| `libftdipp1.so` | `libftdi1.so.2` | Externa (empaquetada) | ✔ `libftdi1_2.so` |
| `libftdipp1.so` | `libusb-1.0.so` | Externa (empaquetada) | ✔ `libusb_1_0.so` |
| `libftdipp1.so` | `libc++_shared.so` | Sistema (Android) | ✔ /system/lib/ |
| `libftdipp1.so` | `libdl.so` | Sistema (Android) | ✔ /system/lib/ |
| `libftdipp1.so` | `libm.so` | Sistema (Android) | ✔ /system/lib/ |
| `libftdipp1.so` | `libc.so` | Sistema (Android) | ✔ /system/lib/ |
| `libftdipp1_2_6_0.so` | `liblog.so` | Sistema (Android) | ✔ /system/lib/ |
| `libftdipp1_2_6_0.so` | `libftdi1.so.2` | Externa (empaquetada) | ✔ `libftdi1_2.so` |
| `libftdipp1_2_6_0.so` | `libusb-1.0.so` | Externa (empaquetada) | ✔ `libusb_1_0.so` |
| `libftdipp1_2_6_0.so` | `libc++_shared.so` | Sistema (Android) | ✔ /system/lib/ |
| `libftdipp1_2_6_0.so` | `libdl.so` | Sistema (Android) | ✔ /system/lib/ |
| `libftdipp1_2_6_0.so` | `libm.so` | Sistema (Android) | ✔ /system/lib/ |
| `libftdipp1_2_6_0.so` | `libc.so` | Sistema (Android) | ✔ /system/lib/ |
| `libftdipp1_3.so` | `liblog.so` | Sistema (Android) | ✔ /system/lib/ |
| `libftdipp1_3.so` | `libftdi1.so.2` | Externa (empaquetada) | ✔ `libftdi1_2.so` |
| `libftdipp1_3.so` | `libusb-1.0.so` | Externa (empaquetada) | ✔ `libusb_1_0.so` |
| `libftdipp1_3.so` | `libc++_shared.so` | Sistema (Android) | ✔ /system/lib/ |
| `libftdipp1_3.so` | `libdl.so` | Sistema (Android) | ✔ /system/lib/ |
| `libftdipp1_3.so` | `libm.so` | Sistema (Android) | ✔ /system/lib/ |
| `libftdipp1_3.so` | `libc.so` | Sistema (Android) | ✔ /system/lib/ |
| `libjaylink.so` | `libusb-1.0.so` | Externa (empaquetada) | ✔ `libusb_1_0.so` |
| `libjaylink.so` | `libdl.so` | Sistema (Android) | ✔ /system/lib/ |
| `libjaylink.so` | `libc.so` | Sistema (Android) | ✔ /system/lib/ |
| `liblspci.so` | `libpci.so.3` | Externa (empaquetada) | ✔ `libpci_3.so` |
| `liblspci.so` | `libdl.so` | Sistema (Android) | ✔ /system/lib/ |
| `liblspci.so` | `libc.so` | Sistema (Android) | ✔ /system/lib/ |
| `libpci.so` | `libz.so.1` | Externa (empaquetada) | ✔ `libz_1.so` |
| `libpci.so` | `libdl.so` | Sistema (Android) | ✔ /system/lib/ |
| `libpci.so` | `libc.so` | Sistema (Android) | ✔ /system/lib/ |
| `libpci_3.so` | `libz.so.1` | Externa (empaquetada) | ✔ `libz_1.so` |
| `libpci_3.so` | `libdl.so` | Sistema (Android) | ✔ /system/lib/ |
| `libpci_3.so` | `libc.so` | Sistema (Android) | ✔ /system/lib/ |
| `libpci_3_15_0.so` | `libz.so.1` | Externa (empaquetada) | ✔ `libz_1.so` |
| `libpci_3_15_0.so` | `libdl.so` | Sistema (Android) | ✔ /system/lib/ |
| `libpci_3_15_0.so` | `libc.so` | Sistema (Android) | ✔ /system/lib/ |
| `libpcilmr.so` | `libpci.so.3` | Externa (empaquetada) | ✔ `libpci_3.so` |
| `libpcilmr.so` | `libdl.so` | Sistema (Android) | ✔ /system/lib/ |
| `libpcilmr.so` | `libc.so` | Sistema (Android) | ✔ /system/lib/ |
| `libpyftdi1.so` | `liblog.so` | Sistema (Android) | ✔ /system/lib/ |
| `libpyftdi1.so` | `libftdi1.so.2` | Externa (empaquetada) | ✔ `libftdi1_2.so` |
| `libpyftdi1.so` | `libusb-1.0.so` | Externa (empaquetada) | ✔ `libusb_1_0.so` |
| `libpyftdi1.so` | `libdl.so` | Sistema (Android) | ✔ /system/lib/ |
| `libpyftdi1.so` | `libc.so` | Sistema (Android) | ✔ /system/lib/ |
| `libsetpci.so` | `libpci.so.3` | Externa (empaquetada) | ✔ `libpci_3.so` |
| `libsetpci.so` | `libdl.so` | Sistema (Android) | ✔ /system/lib/ |
| `libsetpci.so` | `libc.so` | Sistema (Android) | ✔ /system/lib/ |
| `libusb_1_0.so` | `libdl.so` | Sistema (Android) | ✔ /system/lib/ |
| `libusb_1_0.so` | `libc.so` | Sistema (Android) | ✔ /system/lib/ |
| `libz_1.so` | `libc.so` | Sistema (Android) | ✔ /system/lib/ |

## 2. 64-bit (`arm64-v8a`)

### Clase ELF

| Archivo | Detectado | Esperado | OK |
|---|---|---|---|
| `libc++_shared.so` | ELF64 / AArch64 | ELF64 / AArch64 | ✔ |
| `libconfuse.so` | ELF64 / AArch64 | ELF64 / AArch64 | ✔ |
| `libcrypto_3.so` | ELF64 / AArch64 | ELF64 / AArch64 | ✔ |
| `libflashrom.so` | ELF64 / AArch64 | ELF64 / AArch64 | ✔ |
| `libflashrom_1.so` | ELF64 / AArch64 | ELF64 / AArch64 | ✔ |
| `libflashrom_1_0_0.so` | ELF64 / AArch64 | ELF64 / AArch64 | ✔ |
| `libflashrom_bin.so` | ELF64 / AArch64 | ELF64 / AArch64 | ✔ |
| `libftdi1.so` | ELF64 / AArch64 | ELF64 / AArch64 | ✔ |
| `libftdi1_2.so` | ELF64 / AArch64 | ELF64 / AArch64 | ✔ |
| `libftdi1_2_6_0.so` | ELF64 / AArch64 | ELF64 / AArch64 | ✔ |
| `libftdi_eeprom.so` | ELF64 / AArch64 | ELF64 / AArch64 | ✔ |
| `libftdipp1.so` | ELF64 / AArch64 | ELF64 / AArch64 | ✔ |
| `libftdipp1_2_6_0.so` | ELF64 / AArch64 | ELF64 / AArch64 | ✔ |
| `libftdipp1_3.so` | ELF64 / AArch64 | ELF64 / AArch64 | ✔ |
| `libjaylink.so` | ELF64 / AArch64 | ELF64 / AArch64 | ✔ |
| `liblspci.so` | ELF64 / AArch64 | ELF64 / AArch64 | ✔ |
| `libpci.so` | ELF64 / AArch64 | ELF64 / AArch64 | ✔ |
| `libpci_3.so` | ELF64 / AArch64 | ELF64 / AArch64 | ✔ |
| `libpci_3_15_0.so` | ELF64 / AArch64 | ELF64 / AArch64 | ✔ |
| `libpcilmr.so` | ELF64 / AArch64 | ELF64 / AArch64 | ✔ |
| `libpyftdi1.so` | ELF64 / AArch64 | ELF64 / AArch64 | ✔ |
| `libsetpci.so` | ELF64 / AArch64 | ELF64 / AArch64 | ✔ |
| `libusb_1_0.so` | ELF64 / AArch64 | ELF64 / AArch64 | ✔ |
| `libz_1.so` | ELF64 / AArch64 | ELF64 / AArch64 | ✔ |

### SONAME → nombre en jniLibs

| Archivo | SONAME | Nombre que resuelve | Coincide |
|---|---|---|---|
| `libc++_shared.so` | `libc++_shared.so` | `libc++_shared.so` | ✔ |
| `libconfuse.so` | `libconfuse.so` | `libconfuse.so` | ✔ |
| `libcrypto_3.so` | `libcrypto.so.3` | `libcrypto_3.so` | ✔ |
| `libflashrom.so` | `libflashrom.so` | `libflashrom.so` | ✔ |
| `libflashrom_1.so` | `libflashrom.so` | `libflashrom.so` | ≈ |
| `libflashrom_1_0_0.so` | `libflashrom.so` | `libflashrom.so` | ≈ |
| `libflashrom_bin.so` | — | — | — |
| `libftdi1.so` | `libftdi1.so.2` | `libftdi1_2.so` | ≈ |
| `libftdi1_2.so` | `libftdi1.so.2` | `libftdi1_2.so` | ✔ |
| `libftdi1_2_6_0.so` | `libftdi1.so.2` | `libftdi1_2.so` | ≈ |
| `libftdi_eeprom.so` | — | — | — |
| `libftdipp1.so` | `libftdipp1.so.3` | `libftdipp1_3.so` | ≈ |
| `libftdipp1_2_6_0.so` | `libftdipp1.so.3` | `libftdipp1_3.so` | ≈ |
| `libftdipp1_3.so` | `libftdipp1.so.3` | `libftdipp1_3.so` | ✔ |
| `libjaylink.so` | `libjaylink.so` | `libjaylink.so` | ✔ |
| `liblspci.so` | — | — | — |
| `libpci.so` | `libpci.so.3` | `libpci_3.so` | ≈ |
| `libpci_3.so` | `libpci.so.3` | `libpci_3.so` | ✔ |
| `libpci_3_15_0.so` | `libpci.so.3` | `libpci_3.so` | ≈ |
| `libpcilmr.so` | — | — | — |
| `libpyftdi1.so` | — | — | — |
| `libsetpci.so` | — | — | — |
| `libusb_1_0.so` | `libusb-1.0.so` | `libusb_1_0.so` | ✔ |
| `libz_1.so` | `libz.so.1` | `libz_1.so` | ✔ |

### Dependencias `DT_NEEDED`

| Archivo | Dependencia | Tipo | Resolución |
|---|---|---|---|
| `libc++_shared.so` | `libc.so` | Sistema (Android) | ✔ /system/lib64/ |
| `libc++_shared.so` | `libm.so` | Sistema (Android) | ✔ /system/lib64/ |
| `libc++_shared.so` | `libdl.so` | Sistema (Android) | ✔ /system/lib64/ |
| `libconfuse.so` | `libc.so` | Sistema (Android) | ✔ /system/lib64/ |
| `libcrypto_3.so` | `libdl.so` | Sistema (Android) | ✔ /system/lib64/ |
| `libcrypto_3.so` | `libc.so` | Sistema (Android) | ✔ /system/lib64/ |
| `libflashrom.so` | `libpci.so.3` | Externa (empaquetada) | ✔ `libpci_3.so` |
| `libflashrom.so` | `libusb-1.0.so` | Externa (empaquetada) | ✔ `libusb_1_0.so` |
| `libflashrom.so` | `libftdi1.so.2` | Externa (empaquetada) | ✔ `libftdi1_2.so` |
| `libflashrom.so` | `libjaylink.so` | Externa (empaquetada) | ✔ `libjaylink.so` |
| `libflashrom.so` | `libc.so` | Sistema (Android) | ✔ /system/lib64/ |
| `libflashrom_1.so` | `libpci.so.3` | Externa (empaquetada) | ✔ `libpci_3.so` |
| `libflashrom_1.so` | `libusb-1.0.so` | Externa (empaquetada) | ✔ `libusb_1_0.so` |
| `libflashrom_1.so` | `libftdi1.so.2` | Externa (empaquetada) | ✔ `libftdi1_2.so` |
| `libflashrom_1.so` | `libjaylink.so` | Externa (empaquetada) | ✔ `libjaylink.so` |
| `libflashrom_1.so` | `libc.so` | Sistema (Android) | ✔ /system/lib64/ |
| `libflashrom_1_0_0.so` | `libpci.so.3` | Externa (empaquetada) | ✔ `libpci_3.so` |
| `libflashrom_1_0_0.so` | `libusb-1.0.so` | Externa (empaquetada) | ✔ `libusb_1_0.so` |
| `libflashrom_1_0_0.so` | `libftdi1.so.2` | Externa (empaquetada) | ✔ `libftdi1_2.so` |
| `libflashrom_1_0_0.so` | `libjaylink.so` | Externa (empaquetada) | ✔ `libjaylink.so` |
| `libflashrom_1_0_0.so` | `libc.so` | Sistema (Android) | ✔ /system/lib64/ |
| `libflashrom_bin.so` | `libcrypto.so.3` | Externa (empaquetada) | ✔ `libcrypto_3.so` |
| `libflashrom_bin.so` | `libpci.so.3` | Externa (empaquetada) | ✔ `libpci_3.so` |
| `libflashrom_bin.so` | `libusb-1.0.so` | Externa (empaquetada) | ✔ `libusb_1_0.so` |
| `libflashrom_bin.so` | `libftdi1.so.2` | Externa (empaquetada) | ✔ `libftdi1_2.so` |
| `libflashrom_bin.so` | `libjaylink.so` | Externa (empaquetada) | ✔ `libjaylink.so` |
| `libflashrom_bin.so` | `libc.so` | Sistema (Android) | ✔ /system/lib64/ |
| `libftdi1.so` | `liblog.so` | Sistema (Android) | ✔ /system/lib64/ |
| `libftdi1.so` | `libusb-1.0.so` | Externa (empaquetada) | ✔ `libusb_1_0.so` |
| `libftdi1.so` | `libdl.so` | Sistema (Android) | ✔ /system/lib64/ |
| `libftdi1.so` | `libc.so` | Sistema (Android) | ✔ /system/lib64/ |
| `libftdi1_2.so` | `liblog.so` | Sistema (Android) | ✔ /system/lib64/ |
| `libftdi1_2.so` | `libusb-1.0.so` | Externa (empaquetada) | ✔ `libusb_1_0.so` |
| `libftdi1_2.so` | `libdl.so` | Sistema (Android) | ✔ /system/lib64/ |
| `libftdi1_2.so` | `libc.so` | Sistema (Android) | ✔ /system/lib64/ |
| `libftdi1_2_6_0.so` | `liblog.so` | Sistema (Android) | ✔ /system/lib64/ |
| `libftdi1_2_6_0.so` | `libusb-1.0.so` | Externa (empaquetada) | ✔ `libusb_1_0.so` |
| `libftdi1_2_6_0.so` | `libdl.so` | Sistema (Android) | ✔ /system/lib64/ |
| `libftdi1_2_6_0.so` | `libc.so` | Sistema (Android) | ✔ /system/lib64/ |
| `libftdi_eeprom.so` | `liblog.so` | Sistema (Android) | ✔ /system/lib64/ |
| `libftdi_eeprom.so` | `libftdi1.so.2` | Externa (empaquetada) | ✔ `libftdi1_2.so` |
| `libftdi_eeprom.so` | `libconfuse.so` | Externa (empaquetada) | ✔ `libconfuse.so` |
| `libftdi_eeprom.so` | `libusb-1.0.so` | Externa (empaquetada) | ✔ `libusb_1_0.so` |
| `libftdi_eeprom.so` | `libdl.so` | Sistema (Android) | ✔ /system/lib64/ |
| `libftdi_eeprom.so` | `libc.so` | Sistema (Android) | ✔ /system/lib64/ |
| `libftdipp1.so` | `liblog.so` | Sistema (Android) | ✔ /system/lib64/ |
| `libftdipp1.so` | `libftdi1.so.2` | Externa (empaquetada) | ✔ `libftdi1_2.so` |
| `libftdipp1.so` | `libusb-1.0.so` | Externa (empaquetada) | ✔ `libusb_1_0.so` |
| `libftdipp1.so` | `libc++_shared.so` | Sistema (Android) | ✔ /system/lib64/ |
| `libftdipp1.so` | `libdl.so` | Sistema (Android) | ✔ /system/lib64/ |
| `libftdipp1.so` | `libm.so` | Sistema (Android) | ✔ /system/lib64/ |
| `libftdipp1.so` | `libc.so` | Sistema (Android) | ✔ /system/lib64/ |
| `libftdipp1_2_6_0.so` | `liblog.so` | Sistema (Android) | ✔ /system/lib64/ |
| `libftdipp1_2_6_0.so` | `libftdi1.so.2` | Externa (empaquetada) | ✔ `libftdi1_2.so` |
| `libftdipp1_2_6_0.so` | `libusb-1.0.so` | Externa (empaquetada) | ✔ `libusb_1_0.so` |
| `libftdipp1_2_6_0.so` | `libc++_shared.so` | Sistema (Android) | ✔ /system/lib64/ |
| `libftdipp1_2_6_0.so` | `libdl.so` | Sistema (Android) | ✔ /system/lib64/ |
| `libftdipp1_2_6_0.so` | `libm.so` | Sistema (Android) | ✔ /system/lib64/ |
| `libftdipp1_2_6_0.so` | `libc.so` | Sistema (Android) | ✔ /system/lib64/ |
| `libftdipp1_3.so` | `liblog.so` | Sistema (Android) | ✔ /system/lib64/ |
| `libftdipp1_3.so` | `libftdi1.so.2` | Externa (empaquetada) | ✔ `libftdi1_2.so` |
| `libftdipp1_3.so` | `libusb-1.0.so` | Externa (empaquetada) | ✔ `libusb_1_0.so` |
| `libftdipp1_3.so` | `libc++_shared.so` | Sistema (Android) | ✔ /system/lib64/ |
| `libftdipp1_3.so` | `libdl.so` | Sistema (Android) | ✔ /system/lib64/ |
| `libftdipp1_3.so` | `libm.so` | Sistema (Android) | ✔ /system/lib64/ |
| `libftdipp1_3.so` | `libc.so` | Sistema (Android) | ✔ /system/lib64/ |
| `libjaylink.so` | `libusb-1.0.so` | Externa (empaquetada) | ✔ `libusb_1_0.so` |
| `libjaylink.so` | `libdl.so` | Sistema (Android) | ✔ /system/lib64/ |
| `libjaylink.so` | `libc.so` | Sistema (Android) | ✔ /system/lib64/ |
| `liblspci.so` | `libpci.so.3` | Externa (empaquetada) | ✔ `libpci_3.so` |
| `liblspci.so` | `libdl.so` | Sistema (Android) | ✔ /system/lib64/ |
| `liblspci.so` | `libc.so` | Sistema (Android) | ✔ /system/lib64/ |
| `libpci.so` | `libz.so.1` | Externa (empaquetada) | ✔ `libz_1.so` |
| `libpci.so` | `libdl.so` | Sistema (Android) | ✔ /system/lib64/ |
| `libpci.so` | `libc.so` | Sistema (Android) | ✔ /system/lib64/ |
| `libpci_3.so` | `libz.so.1` | Externa (empaquetada) | ✔ `libz_1.so` |
| `libpci_3.so` | `libdl.so` | Sistema (Android) | ✔ /system/lib64/ |
| `libpci_3.so` | `libc.so` | Sistema (Android) | ✔ /system/lib64/ |
| `libpci_3_15_0.so` | `libz.so.1` | Externa (empaquetada) | ✔ `libz_1.so` |
| `libpci_3_15_0.so` | `libdl.so` | Sistema (Android) | ✔ /system/lib64/ |
| `libpci_3_15_0.so` | `libc.so` | Sistema (Android) | ✔ /system/lib64/ |
| `libpcilmr.so` | `libpci.so.3` | Externa (empaquetada) | ✔ `libpci_3.so` |
| `libpcilmr.so` | `libdl.so` | Sistema (Android) | ✔ /system/lib64/ |
| `libpcilmr.so` | `libc.so` | Sistema (Android) | ✔ /system/lib64/ |
| `libpyftdi1.so` | `liblog.so` | Sistema (Android) | ✔ /system/lib64/ |
| `libpyftdi1.so` | `libftdi1.so.2` | Externa (empaquetada) | ✔ `libftdi1_2.so` |
| `libpyftdi1.so` | `libusb-1.0.so` | Externa (empaquetada) | ✔ `libusb_1_0.so` |
| `libpyftdi1.so` | `libdl.so` | Sistema (Android) | ✔ /system/lib64/ |
| `libpyftdi1.so` | `libc.so` | Sistema (Android) | ✔ /system/lib64/ |
| `libsetpci.so` | `libpci.so.3` | Externa (empaquetada) | ✔ `libpci_3.so` |
| `libsetpci.so` | `libdl.so` | Sistema (Android) | ✔ /system/lib64/ |
| `libsetpci.so` | `libc.so` | Sistema (Android) | ✔ /system/lib64/ |
| `libusb_1_0.so` | `libdl.so` | Sistema (Android) | ✔ /system/lib64/ |
| `libusb_1_0.so` | `libc.so` | Sistema (Android) | ✔ /system/lib64/ |
| `libz_1.so` | `libc.so` | Sistema (Android) | ✔ /system/lib64/ |

## Notas

* `libc.so`, `libm.so`, `libdl.so` y `liblog.so` las provee Android; no se empaquetan en el APK.
* Los nombres con versión se adaptan a Google Play y la app restaura el SONAME original con symlinks en `usr/lib/` (`libcrypto.so.3` → `libcrypto_3.so`, `libpci.so.3.15.0` → `libpci_3_15_0.so`).
* Los ejecutables también viajan como `lib*.so` (`flashrom` → `libflashrom_bin.so`, `lspci` → `liblspci.so`, `setpci` → `libsetpci.so`, `pcilmr` → `libpcilmr.so`, `ftdi_eeprom` → `libftdi_eeprom.so`).

