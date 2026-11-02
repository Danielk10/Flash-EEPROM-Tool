# Flash SPI Tool v1.9.12 (versionCode 98) — Selección interactiva de chips, interfaz uniforme y corrección de precio / Interactive Chip Selection, Uniform UI & Price Fix

Esta versión consolida el flujo de **desambiguación de chips**: cuando flashrom detecta varias definiciones compatibles (por ejemplo, las cinco variantes Macronix `MX25L128*`), la app muestra un diálogo con todos los modelos encontrados y reintenta automáticamente la lectura, escritura o verificación con el `-c` seleccionado. Además, unifica el color de la barra de estado con la cabecera, corrige el precio mostrado en la compra Pro y añade al colaborador **mingming987** en los créditos.

This release consolidates the **chip disambiguation flow**: when flashrom matches multiple compatible definitions (for example, the five Macronix `MX25L128*` variants), the app lists every detected model in a dialog and automatically retries the read, write, or verify operation with the selected `-c` argument. It also unifies the status bar color with the header, fixes the displayed Pro purchase price, and adds contributor **mingming987** to the credits.

---

## 🚀 Novedades y Mejoras Técnicas

### 1. Selección interactiva ante ambigüedad de chips
• **Detección robusta:** `FlashromExecutor.parseOutputForAmbiguity()` reconoce tanto `Multiple flash chip definitions match...` como `Please specify which chip definition to use with the -c` y extrae todos los modelos detectados.
• **Independiente del código de salida:** el diálogo ya no depende de que flashrom devuelva un código distinto de cero; se dispara siempre que el log confirme la ambigüedad.
• **Diálogo con todos los modelos:** `MainActivity.onAmbiguityDetected()` muestra la lista completa de chips y una opción para introducir un modelo manualmente. Al elegir uno, la operación original se reintenta con `-c <modelo>`.
• **Construcción de comandos centralizada:** se añadieron `FlashromExecutor.hasChipArgument()` y `withChipArgument()`, reutilizados por el flujo principal y por el reintento tras la selección. Esto evita duplicar lógica y garantiza que `-c` se inserte en una posición válida del comando.

### 2. Interfaz uniforme
• **Barra de estado y cabecera del mismo color:** `android:statusBarColor` ahora usa `@color/primary` (el mismo azul de la barra de título/ActionBar) tanto en el tema claro como en `values-night`, eliminando el contraste con `primary_variant`.

### 3. Compra Pro y créditos
• **Precio corregido:** el texto de respaldo del diálogo de compra muestra **$5.00** en lugar de $4.99. El precio final sigue proviniendo de Google Play Billing cuando el producto está disponible.
• **Colaborador:** se añadió a **mingming987** en el `README.md` y en la sección «Acerca de / Licencias» de la app (inglés, español, chino simplificado y chino tradicional) por sus pruebas de hardware FT232H y el reporte de ambigüedad de chips del issue #101.

---

## ✅ Validación de esta Versión

• **Tests unitarios:** 7/7 en `FlashromExecutorTest` (detección de las cinco variantes MX25L128, inserción/reemplazo de `-c` y casos sin programador).
• **Compilación Android completa:** `:app:compileDebugJavaWithJavac` y `:app:testDebugUnitTest` con Gradle finalizados correctamente.
• **Emulador flashrom:** suite completa de los cinco programadores (serprog, Bus Pirate, SPIDriver, CH341A y FTDI FT232H) superada.
• **Reproducción del issue #101:** la opción `--mx25l128` del emulador simula un Macronix MX25L128 de 16 MB (JEDEC `C2 20 18`); se obtienen exactamente las cinco definiciones Macronix y el mensaje de ambigüedad original.
• **Resolución con `-c`:** lectura de 16.777.216 bytes y verificación `VERIFIED` con `flashrom v2.0.0-devel`, libusb parcheado (`[LIBUSB-HACK]`) y libftdi. La validación completa está automatizada en `emulador_flashrom/test_issue_101.sh`.

---

## 📦 Artefactos

• **APK Release:** `Flash-SPI-Tool-v1.9.12-release.apk`
• **AAB Release (Google Play Bundle):** `Flash-SPI-Tool-v1.9.12-release.aab`

---

## 🔗 Enlaces

• **Repositorio:** https://github.com/Danielk10/Flash-EEPROM-Tool
• **Lanzamiento v1.9.12:** https://github.com/Danielk10/Flash-EEPROM-Tool/releases/tag/v1.9.12
• **Issue #101:** https://github.com/Danielk10/Flash-EEPROM-Tool/issues/101
