# Notas de Lanzamiento - Flash SPI Tool v1.8.5

Esta versión (`v1.8.5`, código de versión `80`) añade aclaraciones fundamentales sobre la compatibilidad de memorias y arquitecturas de hardware (diferenciación entre Flash SPI y EEPROM I2C), mejoras en la interfaz de usuario, desplazamiento suave en los diálogos de información y licencias, y optimización de cadenas en español e inglés, garantizando compatibilidad total en todo el rango soportado (**Android 6.0 / API 23 hasta Android 17 / API 37**).

---

## 🛠️ Mejoras y Novedades de la Versión

### 1. Clarificación Técnica de Compatibilidad de Chips (SPI vs I2C)
* **Contexto:** Se detectaron consultas de usuarios intentando leer memorias EEPROM de la serie 24C (I2C) utilizando el programador CH341A.
* **Explicación y Solución:** 
  - La aplicación está impulsada por el motor nativo **flashrom**, cuyo controlador para CH341A es estrictamente `ch341a_spi` (bus SPI de 4 hilos: MOSI, MISO, CLK y CS).
  - Se detalla explícitamente que las memorias EEPROM de la serie **24C** utilizan el protocolo **I2C** (2 hilos: SDA y SCL), carecen de identificador electrónico universal (JEDEC ID `0x9F`), y **no son compatibles con flashrom**.
  - Se añadieron directrices sobre la colocación de chips en el zócalo ZIF del programador CH341A (uso exclusivo de la sección `25 SPI FLASH`).

### 2. Guía de Hardware y Modos de Jumpers del CH341A
* Se documentó tanto en la sección **Acerca de / Licencias** como en las notas del visor de **Pinouts de Hardware**:
  - **Pines 1-2 (Modo Programador SPI):** Requerido para operar con chips Flash de la serie 25xx.
  - **Pines 2-3 (Modo Serial UART/TTL):** El grabador conmuta a puerto serie COM y no opera para flasheo de memorias.
  - Advertencia preventiva sobre la alimentación a 3.3V.

### 3. Rediseño del Diálogo de Información y Licencias con ScrollView
* El diálogo **Acerca de / Licencias** (`action_about`) fue refactorizado para contener el texto completo dentro de un contenedor `ScrollView`, permitiendo un desplazamiento fluido y ergonómico en dispositivos de cualquier resolución o relación de aspecto.

### 4. Actualización de Textos e Internacionalización Completa
* Se renombró la opción de donación en el menú principal a **"Invitar una Pizza 🍕 $5"** (`Invitar una Pizza 🍕 $5` / `Buy a Pizza 🍕 $5`).
* Todas las cadenas fueron añadidas y sincronizadas en sus respectivos recursos XML (`values/strings.xml` y `values-es/strings.xml`) sin ningún texto hardcodeado en el código fuente.

---

## 🔬 Validación y Pruebas con Emulador de Hardware

Se verificó el 100% de los flujos de hardware con la suite local `test_all_devices.sh`:
* **SERPROG (PTY):** ✅ Detección e inicialización de protocolo serie simulado.
* **Bus Pirate (PTY):** ✅ Secuencia texto → BBIO → modo binario SPI validada.
* **SPIDriver (PTY):** ✅ Handshake y transferencias de comandos SPI exitosas.
* **CH341A (USB Directo):** ✅ Detección de chip virtual `GD25Q80(B)` vía interfaz USB directa.
