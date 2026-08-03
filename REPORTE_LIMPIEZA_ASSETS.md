# Reporte de Limpieza de Assets y Corrección de Rutas
Paquete: `com.diamon.curso` (Flash-EEPROM-Tool)

Este reporte documenta los archivos y carpetas dentro de `app/src/main/assets/data/data/com.diamon.curso/files/usr` que no son necesarios para la ejecución de los binarios en la arquitectura arm64-v8a del proyecto Android y que, por lo tanto, han sido eliminados para reducir significativamente el peso del APK y mejorar el rendimiento de la aplicación.

### 1. Archivos y Carpetas Eliminados

Los siguientes recursos correspondían a cabeceras de compilación, configuraciones de enlazadores y documentación, que no son empleados en tiempo de ejecución en Android por `flashrom` ni por las utilidades de `pciutils`.

**A. Cabeceras y Archivos de Código Fuente C/C++**
*   **Ruta:** `usr/include/` (Directorio completo)
*   **Descripción:** Contenía cabeceras `.h` de libflashrom, libftdi1, libjaylink, libusb-1.0 y pciutils.
*   **Razón:** Solo se necesitan durante el tiempo de compilación nativa en Termux. El ejecutable del APK no enlaza cabeceras.
*   **Ahorro de espacio:** ~450 KB

**B. Librerías Estáticas (.a)**
*   **Ruta:** `usr/lib/*.a` (Múltiples archivos)
*   **Descripción:** `libflashrom.a`, `libftdi1.a`, `libftdipp1.a`, `libjaylink.a`, `libusb-1.0.a`.
*   **Razón:** Las librerías estáticas ya están enlazadas y compiladas dentro de los ejecutables PIE (`flashrom`, `lspci`) o librerías `.so`. No se usan en runtime por Android.
*   **Ahorro de espacio:** ~2.1 MB

**C. Archivos de Configuración de Desarrollo y CMake/PkgConfig**
*   **Rutas:** `usr/lib/cmake/` y `usr/lib/pkgconfig/`
*   **Descripción:** Archivos de configuración `.cmake` para la detección de librerías en CMake y `.pc` para el uso de `pkg-config`. También el archivo libtool `libjaylink.la`.
*   **Razón:** Pkg-config, CMake y Libtool no son ejecutados en el dispositivo Android para lanzar la aplicación. Además, los scripts como `LibFTDI1Config.cmake` contenían rutas hardcodeadas absolutas hacia `/data/data/com.termux/files/home/fake_root/`.
*   **Ahorro de espacio:** ~50 KB

**D. Documentación y Manuales (Man pages)**
*   **Rutas:** `usr/share/doc/` y `usr/share/man/`
*   **Descripción:** Archivos HTML de documentación oficial de flashrom, ejemplos python, archivos `.3` y `.8` de manuales (man pages).
*   **Razón:** Ninguna de las rutinas de la aplicación muestra las *man pages* ni accede a la documentación interna a través del código Java. La aplicación tiene su propia interfaz de usuario.
*   **Ahorro de espacio:** ~7.0 MB

### 2. Archivos y Carpetas Necesarios (Conservados)

Los siguientes archivos se han considerado esenciales para la correcta operación del ecosistema y se mantendrán en `assets/`:

*   **`usr/share/pci.ids.gz`**: Esta es la base de datos de los identificadores de hardware PCI que utilizan `flashrom`, `lspci` y el resto de la suite pciutils para traducir los ID hexadecimales de los buses en nombres legibles (ej: Realtek, Intel).
*   **Scripts Shell Ejecutables (`usr/bin/libftdi1-config`, `usr/sbin/update-pciids`)**: Herramientas que forman parte del entorno y pueden ser ejecutadas si se requieren; `update-pciids` es necesario en caso de que el usuario decida actualizar su base de datos local del pci.ids utilizando curl/wget, y al ser un script en bash, no puede empaquetarse en `jniLibs/`.

### Resumen del Impacto de la Limpieza
*   **Peso Inicial Ahorrado:** Aproximadamente **9.6 MB** liberados en total.
*   **Velocidad de Extracción:** El archivo `AssetHelper.java` de la aplicación recorrerá muchos menos ficheros durante su rutina `extractAssets()`, acelerando dramáticamente la velocidad del primer inicio tras instalar el APK.
*   **Seguridad:** Eliminadas configuraciones absolutas a `com.termux` garantizando portabilidad absoluta y reduciendo superficie al quitar estáticos o código de inicialización ajeno a los binarios finales de producción.
