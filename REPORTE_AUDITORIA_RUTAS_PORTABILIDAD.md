# Reporte de Auditoría y Corrección: Rutas y Portabilidad de Flash-SPI-Tool
ESTADO: CORREGIDO (Última actualización: Agosto 2026)

Este documento detalla los hallazgos críticos y las acciones correctivas aplicadas para garantizar la portabilidad de las herramientas nativas (flashrom, libftdi, pciutils, etc.) en Android para el paquete `com.diamon.curso`.

### 1. Problema: Rutas Hardcoded en Binarios (RUNPATH) - SOLUCIONADO
**Acción:** Se utilizó `patchelf --remove-rpath` en todas las librerías compartidas y binarios dentro de `app/src/main/jniLibs/arm64-v8a/`. Durante la auditoría se encontraron referencias a rutas obsoletas de compilación (ej. `/data/data/com.termux/files/usr/lib`) incrustadas en el header ELF `RUNPATH`.
**Resultado:** El sistema Android ahora cargará las librerías dinámicas utilizando estrictamente las rutas estándar del sistema y el directorio nativo del APK, o en su defecto a través de la variable `LD_LIBRARY_PATH` asignada en runtime. Se eliminó la dependencia total del entorno de Termux.

### 2. Contaminación en Assets y Scripts de Configuración - SOLUCIONADO
**Acción:** Se auditaron las carpetas `assets` y `fake_root` en busca de rutas absolutas al entorno de desarrollo (`/data/data/com.termux`). Estas rutas se encontraban mayoritariamente en configuraciones de CMake (`LibFTDI1Config.cmake`) y de `libtool` (`libjaylink.la`).
**Resultado:** Al no ser necesarias estas herramientas de compilación en el entorno de ejecución de Android, se procedió a eliminar completamente las carpetas `cmake/`, `pkgconfig/` y el archivo `libjaylink.la`. Los scripts que sí se necesitan en tiempo de ejecución (como `update-pciids` o `libftdi1-config`) se validaron y no contienen rutas absolutas hardcodeadas hacia Termux.

### 3. Optimización de Espacio en el APK - SOLUCIONADO
**Acción:** Se excluyeron de la carpeta de `assets` los recursos que no son necesarios en runtime (librerías estáticas `.a` y headers C/C++), reubicándolos conceptualmente para el desarrollo con NDK en el proyecto Android, mientras que los archivos originales se conservan intactos en la carpeta de referencia `fake_root` para tomar cualquier cosa faltante. También se limpió documentación innecesaria en runtime.
**Resultado:** Se redujo el tamaño del paquete, liberando aproximadamente **~10 MB de redundancia en los assets**. Con esto, se evita la extracción innecesaria de archivos de desarrollo en el dispositivo, acelerando los procesos de `AssetHelper.java` durante el primer arranque, garantizando al mismo tiempo que los includes y archivos de desarrollo NDK estén en su lugar correcto para el entorno.

### 4. Carga Dinámica de Dependencias JNI y Renombramiento - SOLUCIONADO
**Acción:** Se implementó una resolución inteligente de dependencias nativas en la capa Java (`AssetHelper.java`), junto con el renombramiento de los binarios PIE (Position-Independent Executable). Google Play y Android requieren que todo lo contenido en `jniLibs` lleve el prefijo `lib` y extensión `.so`.
**Resultado:** Binarios como `flashrom`, `lspci` o `ftdi_eeprom` fueron renombrados exitosamente (ej. `libflashrom_bin.so`). La aplicación recupera las versiones renombradas usando `getApplicationInfo().nativeLibraryDir` y crea symlinks en `usr/sbin` o `usr/bin` para reconstruir las rutas exactas. Adicionalmente, el chequeo automático mapea `.so.3` a versiones seguras como `_3.so`, solucionando problemas de dependencias compartidas no encontradas (UnsatisfiedLinkError simulado).

### 5. Soporte de 32 bits (`armeabi-v7a`) - AGOSTO 2026

**Acción:** Se replicó el conjunto completo de binarios para ARMv7 partiendo de `fake_root_32_bit/` y `libs_32/`, con los mismos 24 nombres de archivo que `jniLibs/arm64-v8a/`. `app/build.gradle` declara ahora `abiFilters "arm64-v8a", "armeabi-v7a"`.

**Resultado:** Ambas carpetas contienen exactamente los mismos nombres, de modo que la lógica de resolución de `AssetHelper.java` (incluido `getNativeCandidates()`) funciona sin cambios en las dos arquitecturas: Android entrega a cada proceso únicamente el `nativeLibraryDir` correspondiente a su ABI. `analizar_dependencias.py` valida clase ELF, SONAME y `DT_NEEDED` para ambas ABIs y la verificación termina sin pendientes.

> **Limpieza de `RUNPATH`:** los binarios de 32 bits se compilaron en Termux y, al igual que el conjunto de 64 bits publicado en v1.9.16, incluían entradas `RUNPATH` hacia `/data/data/com.termux/files/usr/lib` y `.../home/fake_root/...`. El 15 de noviembre de 2026 se eliminaron con `patchelf --remove-rpath` en **ambas** carpetas `jniLibs/<abi>/` (46 archivos). Los árboles de referencia `fake_root_32_bit`/`fake_root_64_bit` y las librerías `libs_32`/`libs_64` se conservan **intactos, tal como se compilaron**. La verificación posterior confirma que se mantienen intactos `DT_NEEDED`, `SONAME` y los segmentos `LOAD`. La resolución en tiempo de ejecución la hace `LD_LIBRARY_PATH` (fijado en `native-lib.cpp`) junto con los symlinks que crea `AssetHelper` en `usr/lib`. Los scripts de build no incluyen este paso: si se recompilan los binarios en Termux, hay que repetir `patchelf --remove-rpath` antes de copiarlos a `jniLibs/`.
