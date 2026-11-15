# Flash SPI Tool v1.9.17 (versionCode 103) — Soporte ARM de 32 bits / 32-bit ARM Support

Fecha de lanzamiento / Release Date: 15 de noviembre de 2026 / November 15, 2026

---

## 🇪🇸 Español

### 📌 Resumen
Esta versión incorpora el soporte nativo de **ARM de 32 bits (`armeabi-v7a`)** junto a la variante de 64 bits (`arm64-v8a`) que ya existía. Hasta ahora la aplicación solo podía instalarse en dispositivos ARM64; a partir de la 1.9.17 también funciona en equipos y tablets ARMv7 (Android 7.0 o superior) que no soportan binarios de 64 bits. Android elige automáticamente el conjunto correcto al instalar el APK o el bundle.

### 🛠️ Cambios
• Nuevo conjunto de 24 binarios ARMv7 en `jniLibs/armeabi-v7a` con exactamente los mismos nombres que el conjunto ARM64: `flashrom`, `pciutils` (`lspci`, `setpci`, `pcilmr`), `ftdi_eeprom`, `libftdi1`, `libftdipp1`, `libjaylink`, `libusb-1.0`, `libcrypto`, `libconfuse`, `libc++_shared` y `libz`.
• `abiFilters` pasa a `"arm64-v8a", "armeabi-v7a"`, de modo que el APK y el AAB incluyen ambas arquitecturas.
• El motor `flashrom` se enlaza ahora con `-llog`, lo que permite que sus trazas se integren en el registro de Android al ejecutarse en el dispositivo.
• El script `build_flashrom_custom.sh` se parametrizó por arquitectura: `ABI=64` (por defecto) usa `fake_root_64_bit/` y `ABI=32` usa `fake_root_32_bit/`. Los demás scripts de compilación no se modificaron.
• `analizar_dependencias.py` valida ahora las dos arquitecturas: clase ELF, SONAME y resolución de cada dependencia `DT_NEEDED` (aplicando la misma lógica que `AssetHelper.getNativeCandidates()`), y regenera `REPORTE_ANALISIS_DEPENDENCIAS.md`.
• Documentación actualizada para reflejar las dos ABIs: `README.md`, `NOMBRES_BINARIOS_VS_ANTIGUOS.md`, `REPORTE_LIMPIEZA_ASSETS.md` y `REPORTE_AUDITORIA_RUTAS_PORTABILIDAD.md`.

### ✅ Validación
• `analizar_dependencias.py`: 24 archivos por ABI, todos `ELF32 ARM` / `ELF64 AArch64`, sin dependencias pendientes.
• `:app:assembleRelease` y `:app:bundleRelease` correctos: el APK contiene `lib/arm64-v8a/` y `lib/armeabi-v7a/` con 30 librerías cada una (24 binarios empaquetados + 6 del puente USB/JNI), todas sin `RUNPATH`.
• APK y AAB firmados con la clave de producción; huella SHA-256 del certificado verificada.
• `versionCode 103` y `versionName 1.9.17`.

### 📦 Artefactos
• **APK Release:** `Flash-SPI-Tool-v1.9.17-release.apk`  
  **SHA-256:** `d5f848d012e47e29afddde24a39be020b07937e3aa10f67a22108cba1fc2ee17`
• **AAB Release (Google Play Bundle):** `Flash-SPI-Tool-v1.9.17-release.aab`  
  **SHA-256:** `8af72b2374689122ffe6b2275b10b5a51f079bf4ab343ad2ce877196df1fa2df`

---

## 🇺🇸 English

### 📌 Overview
This release adds native **32-bit ARM (`armeabi-v7a`)** support alongside the existing 64-bit variant (`arm64-v8a`). Until now the app could only be installed on ARM64 devices; starting with 1.9.17 it also runs on ARMv7 phones and tablets (Android 7.0 or later) that cannot execute 64-bit binaries. Android automatically picks the right set when installing the APK or the bundle.

### 🛠️ Changes
• New set of 24 ARMv7 binaries in `jniLibs/armeabi-v7a` with exactly the same file names as the ARM64 set: `flashrom`, `pciutils` (`lspci`, `setpci`, `pcilmr`), `ftdi_eeprom`, `libftdi1`, `libftdipp1`, `libjaylink`, `libusb-1.0`, `libcrypto`, `libconfuse`, `libc++_shared`, and `libz`.
• `abiFilters` is now `"arm64-v8a", "armeabi-v7a"`, so both the APK and the AAB ship both architectures.
• The `flashrom` engine is now linked with `-llog`, so its traces integrate with the Android log when running on the device.
• The `build_flashrom_custom.sh` script is now architecture-parameterised: `ABI=64` (default) uses `fake_root_64_bit/` and `ABI=32` uses `fake_root_32_bit/`. The other build scripts are unchanged.
• `analizar_dependencias.py` now validates both architectures: ELF class, SONAME, and resolution of every `DT_NEEDED` dependency (mirroring `AssetHelper.getNativeCandidates()`), regenerating `REPORTE_ANALISIS_DEPENDENCIAS.md`.
• Documentation updated for both ABIs: `README.md`, `NOMBRES_BINARIOS_VS_ANTIGUOS.md`, `REPORTE_LIMPIEZA_ASSETS.md`, and `REPORTE_AUDITORIA_RUTAS_PORTABILIDAD.md`.

### ✅ Validation
• `analizar_dependencias.py`: 24 files per ABI, all `ELF32 ARM` / `ELF64 AArch64`, with no missing dependencies.
• `:app:assembleRelease` and `:app:bundleRelease` succeeded: the APK contains `lib/arm64-v8a/` and `lib/armeabi-v7a/` with 30 libraries each (24 packaged binaries + 6 from the USB/JNI bridge), all without `RUNPATH`.
• APK and AAB signed with the production key; certificate SHA-256 fingerprint verified.
• `versionCode 103` and `versionName 1.9.17`.

### 📦 Artifacts
• **APK Release:** `Flash-SPI-Tool-v1.9.17-release.apk`  
  **SHA-256:** `d5f848d012e47e29afddde24a39be020b07937e3aa10f67a22108cba1fc2ee17`
• **AAB Release (Google Play Bundle):** `Flash-SPI-Tool-v1.9.17-release.aab`  
  **SHA-256:** `8af72b2374689122ffe6b2275b10b5a51f079bf4ab343ad2ce877196df1fa2df`

---

## 🔗 Enlaces / Links
• **Repositorio / Repository:** https://github.com/Danielk10/Flash-EEPROM-Tool
• **Release:** https://github.com/Danielk10/Flash-EEPROM-Tool/releases/tag/v1.9.17
