### ✨ Gran Actualización de Arquitectura y Estabilidad

Esta versión Alfa v0.1.0 (Build 58) marca la transformación total de la aplicación a una interfaz GUI robusta, segura y profesional para hardware flashrom en Android.

#### 🔧 Cambios Principales:
- **Rediseño Profesional (Dark Mode)**: Interfaz renovada completamente a un tema oscuro moderno, más amigable a la vista y orientado a un uso técnico profesional.
- **Multilenguaje (Inglés / Español)**: Sistema reconstruido desde cero extrayendo los recursos en duro a `strings.xml`.

#### 🛡️ Mejoras en el Núcleo y Estabilidad (18 Crashes Resueltos):
- **OOM de Memoria Evitados (#70, #72, etc.)**: Carga de ROMs optimizada con flujos directos (Streams) e impuestas barreras en el visor de Diferencias Hexadecimales para prevenir desbordes.
- **NullPointerExceptions en USB (#75, #62, etc.)**: Parcheado el puente PTY-Serial para prevenir caídas bruscas si el cable USB es retirado en medio de la lectura.
- **Android 14 Compatibilidad (#73)**: Se ajustaron los permisos `PendingIntent` para adherirse a las restricciones de seguridad obligatorias de las últimas versiones de Android.

#### ⚡ Funciones Especiales Flashrom:
- **Auto-Detector de Ambigüedad (Chips)**: Al recibir "Multiple flash chip definitions match", la aplicación captura la lista e invoca automáticamente un pop-up gráfico para seleccionar el chip sin tocar la consola (`-c`).
- **Verificación Asegurada (Verify)**: Agregada casilla predeterminada para que el usuario controle si la escritura de flash debe ser verificada de fábrica. Desmarcar la casilla inyecta la bandera rápida (`-n` noverify) para forzar escrituras de emergencia.
- **Botón de Detener (Abortar)**: Incorpora un sistema de terminación rápida (Kill Process) si flashrom queda colgado, aplicando purgas de memoria al chip controlador.
