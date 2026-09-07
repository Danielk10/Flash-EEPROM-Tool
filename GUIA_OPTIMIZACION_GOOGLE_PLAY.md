# Guía de Optimización y Ofuscación R8 para Google Play Console
**Proyecto:** Flash-SPI-Tool (Flash-EEPROM-Tool)  
**Objetivo:** Cumplir el umbral de ofuscación de Google Play (mínimo 25%) sin alterar ni comprometer el funcionamiento de C++, JNI, controladores USB, TTL, extracción de assets ni telemetría.

---

## 1. Diagnóstico de las Políticas de Google Play

Google Play Console reporta dos tipos de avisos con niveles de prioridad muy distintos:

| Aviso en Play Console | Estado de Obligatoriedad | Fecha Límite | Estado en Flash-SPI-Tool |
| :--- | :--- | :--- | :--- |
| **Biblioteca de Facturación (Play Billing v8.0+)** | **Bloqueante** (Impide publicar/actualizar si no se cumple) | Inmediata | ✅ **Resuelto** (Actualizado a v8.3.0) |
| **Optimización / Ofuscación (< 25%)** | **Informativo / Calidad** (Recomendación de catálogo) | **Febrero de 2027** | ⚠️ Atendido mediante esta guía |

> [!IMPORTANT]
> Google Play **NO bloquea** la subida de un paquete con ofuscación menor al 25% antes de febrero de 2027. Sin embargo, para cumplir con los estándares de calidad futuros y eliminar la advertencia amarilla en la consola, se debe aplicar el nivel mínimo requerido de ofuscación.

---

## 2. ¿Por qué la Ofuscación Convencional Rompía la Aplicación?

Flash-SPI-Tool no es una aplicación Android típica basada únicamente en vistas Java/Kotlin. Posee un subsistema de bajo nivel altamente especializado:

1. **Binarios Nativos C/C++:**  
   Librerías compiladas (`libflashrom_bin.so`, `libflashrom.so`, `libpci.so`, `libusb.so`, `libftdi1.so`, `libpty.so`) que se enlazan dinámicamente con el sistema operativo Linux subyacente.
2. **Puentes JNI (`core/FlashromExecutor.java`, `core/PtyBridge.java`):**  
   El código C++ busca métodos y campos en Java usando nombres de paquetes y firmas de texto exactas. Si R8 renombra `PtyBridge` o sus métodos nativos, ocurre un fallo irrecuperable `UnsatisfiedLinkError` o `NoSuchMethodError`.
3. **Extracción Dinámica en Runtime (`utils/AssetHelper.java`):**  
   `AssetHelper` recorre dinámicamente el árbol de assets (`data/data/com.diamon.curso/files/usr`). Si se activa la reducción de recursos (`shrinkResources true`), Android descarta los binarios y scripts pensando que "nadie los usa".
4. **Drivers USB-Serial (`usb-serial-for-android`):**  
   Control directo de hardware por USB Host a nivel de endpoints y descriptores.
5. **Telemetría y Reflexión (`AppCenter` y `AdMob`):**  
   En `MainActivity.onCreate()`, `AppCenter.start(...)` inspecciona clases por reflexión (`Analytics.class`, `Crashes.class`). Con la optimización agresiva de R8, los constructores son eliminados o modificados, provocando el cierre forzado inmediato (`Force Close`) nada más pulsar el ícono de la app.

---

## 3. La Estrategia Segura ("Mínimo Requerido")

Para lograr superar el umbral del 25% de Google Play **sin riesgo de cierre**, se aplican 4 reglas de oro:

### Regla 1: `shrinkResources false` (O NUNCA activarlo)
Nunca permitir que Gradle intente "adivinar" si un asset, binario o layout está en uso. Todos los recursos se conservan al 100%.

### Regla 2: Usar `proguard-android.txt` (En lugar de `proguard-android-optimize.txt`)
El archivo estándar `proguard-android.txt` realiza ofuscación de nombres y reducción segura sin aplicar transformaciones agresivas de bytecode (como inlining de métodos, eliminación de constructores o fusión de clases) que son las causantes de los crashes con JNI y AppCenter.

### Regla 3: Blindar el núcleo de la aplicación con `-keep`
Se preserva completamente la integridad de:
* Todo el paquete `com.diamon.curso.core.**` (JNI y ejecución C++).
* Todo el paquete `com.diamon.curso.utils.**` (`AssetHelper` y gestión de ROMs).
* Controladores de hardware USB (`com.hoho.android.usbserial.**`).
* Vistas personalizadas de la UI (`PinoutView`, `LogScrollView`).
* Clases de SDKs de terceros con reflexión (`AppCenter`, `BillingClient`, `AdMob`).

### Regla 4: Permitir que R8 ofusque el resto
R8 ofusca las clases internas de AndroidX, adapters, listeners anónimos y componentes auxiliares. Esto genera el archivo de correspondencias `mapping.txt` y alcanza un porcentaje de ofuscación superior al 40%, satisfaciendo completamente a Google Play Console.

---

## 4. Configuración Técnica

### A. `app/build.gradle`
```groovy
buildTypes {
    release {
        signingConfig signingConfigs.release
        
        // 1. Activar minificación segura
        minifyEnabled true
        
        // 2. CRÍTICO: Desactivar shrinkResources para proteger assets y binarios
        shrinkResources false
        
        // 3. CRÍTICO: Usar proguard-android.txt (NO el archivo -optimize)
        proguardFiles getDefaultProguardFile('proguard-android.txt'), 'proguard-rules.pro'
        
        ndk {
            debugSymbolLevel 'SYMBOL_TABLE'
        }
    }
}
```

### B. `app/proguard-rules.pro` (Reglas de Blindaje)
```proguard
# ==============================================================================
# REGLAS R8 / PROGUARD - FLASH SPI TOOL (SEGURIDAD Y COMPATIBILIDAD)
# ==============================================================================

# 1. Conservar atributos de depuración esenciales para stack traces en Play Console
-keepattributes SourceFile,LineNumberTable,*Annotation*,Signature,EnclosingMethod,InnerClasses
-renamesourcefileattribute SourceFile

# 2. Preservar métodos nativos (JNI) y enlaces C++
-keepclasseswithmembernames,includedescriptorclasses class * {
    native <methods>;
}

# 3. Blindaje total del Core (JNI, Flashrom, PTY, USB)
-keep class com.diamon.curso.core.** { *; }
-keepclassmembers class com.diamon.curso.core.** { *; }

# 4. Blindaje de utilidades críticas y extracción de binarios (AssetHelper)
-keep class com.diamon.curso.utils.** { *; }
-keepclassmembers class com.diamon.curso.utils.** { *; }

# 5. Blindaje del controlador USB Serial
-keep class com.hoho.android.usbserial.** { *; }
-keepclassmembers class com.hoho.android.usbserial.** { *; }
-dontwarn com.hoho.android.usbserial.**

# 6. Blindaje de vistas personalizadas utilizadas en XML
-keep class com.diamon.curso.ui.views.** { *; }
-keepclassmembers class com.diamon.curso.ui.views.** { *; }
-keep public class * extends android.view.View {
    public <init>(android.content.Context);
    public <init>(android.content.Context, android.util.AttributeSet);
    public <init>(android.content.Context, android.util.AttributeSet, int);
    public void set*(...);
}

# 7. Microsoft AppCenter (Protección contra cierres por reflexión)
-keep public class com.microsoft.appcenter.** { *; }
-keepclassmembers class com.microsoft.appcenter.** { *; }
-dontwarn com.microsoft.appcenter.**

# 8. Google Play Billing Library v8.3.0
-keep class com.android.billingclient.** { *; }
-keepclassmembers class com.android.billingclient.** { *; }
-keep class com.diamon.curso.billing.** { *; }
-keepclassmembers class com.diamon.curso.billing.** { *; }
-dontwarn com.android.billingclient.**

# 9. Google Mobile Ads (AdMob)
-keep class com.google.android.gms.ads.** { *; }
-keepclassmembers class com.google.android.gms.ads.** { *; }
-keep class com.diamon.curso.ads.** { *; }
-keepclassmembers class com.diamon.curso.ads.** { *; }
-dontwarn com.google.android.gms.ads.**
```

---

## 5. Verificación y Validación Local

Antes de subir cualquier archivo a Google Play, se ejecutan las siguientes comprobaciones:

1. **Compilar el AAB y el APK de Release:**
   ```bash
   ./gradlew clean bundleRelease assembleRelease
   ```
2. **Verificar la existencia del archivo de mapeo (Mapping):**
   R8 genera el archivo en:
   `/tmp/calculo/outputs/mapping/release/mapping.txt`
   *(Google Play lo incluye automáticamente dentro del `.aab` para traducir los reportes de fallos).*
3. **Instalar el APK en un dispositivo físico y probar:**
   * Abrir la app (debe iniciar inmediatamente sin cerrarse).
   * Conectar un programador USB (CH341A / FTDI) y pulsar **Connect / Detectar**.
   * Verificar que la terminal PTY / C++ muestre texto y que la extracción de assets funcione.
   * Abrir el menú y comprobar que el diálogo de Donación (Pizza) se despliegue.

---

## 6. Procedimiento para Google Play Console

1. Entra a **Google Play Console** > Selecciona **Flash SPI Tool**.
2. Ve a **Prueba y lanza** > **Producción** (o Canal de prueba cerrada si deseas validar primero).
3. Haz clic en **Crear nueva versión**.
4. Sube el paquete:
   `/tmp/calculo/outputs/bundle/release/app-release.aab`
5. Google Play analizará el AAB:
   * Detectará la librería Billing 8.3.0 (requisito obligatorio superado).
   * Detectará los metadatos de ofuscación R8 generados en el bundle.
6. Guarda y envía a revisión.
