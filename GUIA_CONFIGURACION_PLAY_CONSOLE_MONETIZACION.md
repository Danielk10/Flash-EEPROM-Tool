# Guía de Configuración de Monetización y Google Play Console 🚀
## Flash SPI Tool — Suite Pro & Pase Libre de 15 Minutos

Esta guía documenta en detalle el modelo de monetización híbrido ético para **Flash SPI Tool** (`com.diamon.curso`), explicando paso a paso cómo dar de alta o modificar el producto in-app en **Google Play Console**, cómo configurar las licencias de prueba y cómo enlazar los bloques de anuncios en **Google AdMob**.

---

## 🧭 1. Arquitectura de Monetización Híbrida (Fair Freemium)

La aplicación implementa un modelo **100% ético, técnico y respetuoso**: todas las operaciones fundamentales de flasheo de silicio con `flashrom` son libres y gratuitas para siempre, mientras que las herramientas avanzadas de laboratorio, análisis de integridad y modificación de firmware están disponibles mediante un pase temporal de 15 minutos o una compra permanente.

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                       ESTRUCTURA DE CARACTERÍSTICAS                         │
├──────────────────────────────────────┬──────────────────────────────────────┤
│ 🆓 100% LIBRES — Sin Restricciones   │ 👑 CARACTERÍSTICAS PRO — Gated       │
├──────────────────────────────────────┼──────────────────────────────────────┤
│ • Conexión USB CH341A, FT2232, etc.  │ • Bóveda Antibrick (Backup Pre-Flash)│
│ • Detección y JEDEC Probe de Chip    │ • Verificador Criptográfico          │
│ • Lectura Completa de Flash ROM      │   MD5, SHA-256, SHA-1, CRC32         │
│ • Grabación Completa de Flash ROM    │ • Cotejo de Hash con Portapapeles    │
│ • Borrado Masivo y Verificación      │ • Exportación Sidecar .sha256/.md5   │
│ • Consola de Comandos CLI flashrom   │ • Editor Hexadecimal en Vivo         │
│ • Diagramas Pinouts de Hardware      │ • Inyector MAC Intel GbE con 0xBABA  │
│ • Exportación Firmware Serprog       │ • Extractor Clave Windows OEM MSDM   │
│ • Importación / Exportación Dumps    │ • Desbloqueo Registros SR1/SR2/SR3   │
│ • Visor Hexadecimal Mapeado (mmap)   │ • Activador Quad Enable (Bit QE)     │
│ • Comparador Hex Diff con Hashes MD5 │ • Flasheo Delta Selectivo 4KB Sectors│
│                                      │ • Sin anuncios de por vida           │
└──────────────────────────────────────┴──────────────────────────────────────┘
```

El usuario puede desbloquear las herramientas Pro de dos formas:
1. 🎬 **Ver un Video Recompensado (Pase Libre de 15 Minutos):** Al terminar de ver el video de Google AdMob, se otorga acceso ilimitado a todas las funciones Pro y se ocultan los anuncios durante 15 minutos completos. La activación usa `max(vencimiento actual, ahora + 15 min)`, por lo que un pase activo de mayor duración (por ejemplo, uno heredado de 12 horas) nunca se recorta.
2. 👑 **Compra In-App Permanente:** Pago único que desbloquea para siempre todas las herramientas Pro y otorga **Sin anuncios de por vida** en toda la aplicación sin volver a requerir anuncios.

---

## 🛒 2. Configuración del Producto en Google Play Console

Tienes **dos opciones totalmente válidas** para configurar la compra permanente:

---

### 🟢 Opción A (Recomendada): Crear Producto Nuevo `remove_ads_pro`

1. Inicia sesión en [Google Play Console](https://play.google.com/console).
2. Selecciona la aplicación **Flash SPI Tool** (`com.diamon.curso`).
3. En el menú lateral izquierdo, ve a: **Monetizar con Play** ➔ **Productos** ➔ **Productos integrados en la aplicación** (In-App Products).
4. Haz clic en **Crear producto**.

#### Parámetros del Producto:
| Campo | Valor Requerido | Explicación |
| :--- | :--- | :--- |
| **ID de producto \*** | `remove_ads_pro` | Identificador único en Google Play |
| **Nombre \*** | `Flash SPI Tool Pro (Sin Anuncios de por Vida + Herramientas)` | Visible para el usuario |
| **Descripción \*** | Ver plantilla abajo | Detalla beneficios Pro |

---

### 🟡 Opción B: Modificar y Reutilizar el Identificador de Producto Existente (`donacion_pizza`)

Si en versiones previas diste de alta el identificador técnico `donacion_pizza` en Google Play Console:
1. Ve a **Monetizar con Play** ➔ **Productos** ➔ **Productos integrados en la aplicación**.
2. Haz clic sobre el producto `donacion_pizza` para editar sus metadatos.
3. **¿Qué puedes cambiar libremente?**
   - **Nombre:** Cámbialo a `Flash SPI Tool Pro (Sin Anuncios de por Vida + Herramientas)`.
   - **Descripción:** Actualízala con los beneficios Pro y la garantía de Sin anuncios de por vida.
   - **Precio:** Configúralo en `$5.00 USD` (o el valor que prefieras).
   - **Etiquetas:** Puedes colocar `flash-spi-pro`.
   - **Ícono:** Puedes subir el ícono de la app o de herramientas.
4. **¿Qué NO se puede cambiar?**
   - El texto del **ID del producto** (`donacion_pizza`), ya que es la clave primaria fija en los servidores de Google Play.
5. **¿Cómo lo maneja la app?**
   - El motor `BillingManager` de la aplicación incluye soporte dual y reconocerá `donacion_pizza` mediante `acknowledgePurchase` (compra permanente de por vida) en lugar de consumirlo, activando el estado Pro de por vida y la eliminación permanente de anuncios.

---

### Paso 2.3: Configuración de la «Opción de Compra» (Play Console Actualizada)

En la interfaz moderna de Google Play Console:

| Campo de Opción de Compra | Valor Recomendado | Reglas de Play Console |
| :--- | :--- | :--- |
| **ID de la opción de compra \*** | `flash-spi-pro` o `pro-lifetime` | **Obligatorio.** Solo letras minúsculas, números y guiones medios `-` (máx. 63 caracteres). |
| **Tipo de compra \*** | `Comprar` | Compra única de pago único (no recurrente). |
| **Etiquetas (Tags)** | `flash-spi-pro` o dejar **Vacío** | **Opcional.** Máx. 20 caracteres, letras minúsculas y guiones medios. |

---

### Paso 2.4: Nombres y Descripciones Localizados

#### 🇪🇸 Español (es-419 / es-ES) — Idioma Principal:
* **Nombre:**
  ```text
  Flash SPI Tool Pro (Sin Anuncios de por Vida + Herramientas)
  ```
* **Descripción:**
  ```text
  Desbloquea permanentemente todas las funciones Pro de laboratorio (Bóveda Antibrick con backup previo automático, Verificador Criptográfico MD5/SHA-1/SHA-256, Editor Hexadecimal en Vivo, inyector MAC Intel GbE con checksum 0xBABA, desbloqueo de registros SR1/SR2/SR3, activación de Quad Enable y flasheo delta por sectores 4K) y disfruta de Sin anuncios de por vida en toda la aplicación.
  ```

#### 🇺🇸 Inglés (en-US):
* **Name:**
  ```text
  Flash SPI Tool Pro (Ad-Free for Life + Lab Tools)
  ```
* **Description:**
  ```text
  Permanently unlock all Pro lab tools (Antibrick Safety Vault with automatic pre-flash backup, MD5/SHA-1/SHA-256 Cryptographic Verifier, Live Hex Editor, Intel GbE MAC injector with 0xBABA checksum, Status Register SR1/SR2/SR3 unlock, Quad Enable activation, and 4K sector delta flashing) with ad-free for life across the entire app.
  ```

---

### Paso 2.5: Fijar Precio y Activar
* **Precio Oficial Recomendado:** `$5.00 USD` (Google Play calculará automáticamente los equivalentes en moneda local para cada país con impuestos incluidos).
* Haz clic en **Guardar** y luego en **Activar producto**.

---

## 🧪 3. Configuración de Cuentas de Prueba (License Testing)

Para probar la compra in-app en tu teléfono sin gastar dinero real y con entrega inmediata:

1. En Google Play Console, ve a: **Configuración** ➔ **Licencias de prueba** (License Testing).
2. En la sección **Evaluadores con acceso a licencias**, agrega las direcciones de correo electrónico de tu cuenta de Google (las que tengas configuradas en tu teléfono Android de prueba).
3. En **Respuesta de prueba de licencia**, selecciona:
   - `RESPOND_NORMALLY` (Muestra la interfaz real de compra de Google Play, pero emite una tarjeta de prueba virtual sin cargos reales).
4. Guarda los cambios.

---

## 📢 4. Configuración del Bloque Recompensado en Google AdMob

La aplicación utiliza anuncios bonificados (**Rewarded Ads**) para otorgar el **Pase Pro de 15 Minutos**:

### Bloques de Anuncios Oficiales (App ID: `ca-app-pub-5141499161332805~8263601193`):
* **Adaptive Banner:** `ca-app-pub-5141499161332805/1518371626`
* **Interstitial Ad:** `ca-app-pub-5141499161332805/8275351662`
* **Rewarded Video Ad (Pase Pro 15 Minutos):** `ca-app-pub-5141499161332805/9025351389`
* **Native Ad (tarjeta nativa):** `ca-app-pub-5141499161332805/6748236478`

### En Google AdMob:
1. Inicia sesión en [Google AdMob](https://apps.admob.com/).
2. Ve a **Aplicaciones** ➔ Selecciona **Flash SPI Tool** (`com.diamon.curso`).
3. Ve a **Bloques de anuncios** ➔ **Añadir bloque de anuncios**.
4. Selecciona **Bonificado (Rewarded)**.
5. Nombre del bloque: `Pase Pro 15 Minutos Flash SPI Tool`.
6. Configuración de recompensa:
   - **Cantidad de recompensa:** `1`
   - **Elemento de recompensa:** `Pase Pro 15 Minutos`
7. Haz clic en **Crear bloque de anuncios** y copia el ID generado (`ca-app-pub-5141499161332805/9025351389`).

> **Nota:** si el bloque conserva un nombre antiguo de 12 horas en AdMob, no es necesario recrearlo: el identificador `9025351389` es el que usa la app y la duración real la define el código (`ProPassManager.DURACION_PASE_MS = 15 * 60 * 1000L`).

### 4.1 Consentimiento UMP y opciones de privacidad

La app integra el **User Messaging Platform (UMP)** de Google para cumplir con el RGPD y las normativas estatales de EE. UU.:

- **Solicitud al arrancar:** `ConsentManager.requestConsent()` actualiza la información de consentimiento y muestra el formulario que corresponda (`loadAndShowConsentFormIfRequired`).
- **Sin anuncios antes del consentimiento:** MobileAds no se inicializa ni se solicita ningún banner, nativo, intersticial o rewarded hasta que `canRequestAds()` devuelve `true`.
- **Opciones de privacidad:** el menú **"Opciones de privacidad"** solo aparece cuando UMP indica que es obligatorio (`PrivacyOptionsRequirementStatus.REQUIRED`); permite al usuario cambiar su decisión y vuelve a evaluar `canRequestAds()`.
- **Rechazo del consentimiento:** si el usuario no concede consentimiento, la app sigue funcionando con todas las herramientas gratuitas y Pro, pero no se solicitan anuncios.
- **Regiones sin requisito:** cuando UMP informa `NOT_REQUIRED`, los anuncios se habilitan de inmediato sin mostrar formulario.

---

## ☁️ 5. Persistencia Cloud del Pase de 15 Minutos en Firebase Firestore (Arquitectura PIC K150)

Para asegurar que los usuarios no pierdan sus minutos restantes al limpiar datos, cambiar de versión o reinstalar la app, el sistema implementa una **persistencia cuádruple multinivel** idéntica a `PIC-k150-Programing`:

1. **Nivel 1 — SharedPreferences:** Lectura y escritura inmediata en `pro_pass_prefs.xml` con latencia de 0 ms.
2. **Nivel 2 — Archivo Firmado Local con SHA-256:** Almacenado en `.flash_spi_pro_pass.dat` en el almacenamiento interno privado. Protege contra borrados de caché.
3. **Nivel 3 — Firebase Firestore Cloud:**
   - **Proyecto:** `pic-k150-programing-9d189` (compartido entre aplicaciones).
   - **Colección:** `pro_passes`
   - **Documento:** `deviceId` (`Settings.Secure.ANDROID_ID`).
   - **Campos:** `expireTime` (timestamp ms), `updatedAt` (ServerTimestamp), `active: true` y `app: "flash"` (identifica que el pase lo escribió Flash SPI Tool dentro de la colección compartida).
   - **Reglas de Seguridad:** Lectura y escritura directa para `/pro_passes/{deviceId}`.
   - **Restauración:** Al arrancar la app, si no hay pase activo en memoria local, consulta Firestore y restaura el tiempo si aún no ha expirado. La activación conserva el vencimiento mayor entre el pase actual y `ahora + 15 min`.
4. **Nivel 4 — Google Cloud Auto-Backup:** Incluido en `backup_rules.xml` y `data_extraction_rules.xml` para respaldos automáticos en Google Drive.

---

## ⚡ 6. Supresión Absoluta de Anuncios (Zero Ads / Sin anuncios de por vida)

Cuando el usuario tiene el **Pase Pro activo** (sea por compra in-app permanente con **Sin anuncios de por vida** o mediante el pase temporal de 15 minutos por video bonificado):
1. **Banner Adaptativo:** Se oculta de inmediato (`View.GONE`) y se detiene la recarga.
2. **Anuncio Nativo:** Se destruye la tarjeta nativa y no se vuelve a solicitar mientras el acceso Pro esté activo.
3. **Interstitials:** Se suprimen al 100% (el método `mostrarInterstitialConCooldown()` retorna `false` sin abrir anuncios entre operaciones).
4. **Menú Dinámico:** Muestra el tiempo restante con insignia de estado:
   - Con Pase temporal: `⭐ Pro 14m` (o `⭐ Pro 45s` en el último minuto)
   - Con Compra permanente: `👑 Pro Permanente`

---

## ✅ 7. Checklist de Validación Final

- [ ] Producto `remove_ads_pro` (o ID legacy `donacion_pizza`) creado y en estado **Activo** en Google Play Console.
- [ ] Opción de compra configurada como `Comprar` (precio ~$5.00 USD) para otorgar **Sin anuncios de por vida** y la suite Pro completa.
- [ ] Tu correo de Google registrado en **Evaluadores con acceso a licencias**.
- [ ] Bloque bonificado AdMob (`9025351389`) activo y enlazado para el Pase Pro de 15 minutos.
- [ ] Al ver video bonificado: se activa el temporizador de 15 minutos, se ocultan banner/nativo y se desbloquean las herramientas Pro; un pase activo mayor no se recorta.
- [ ] Al pulsar compra permanente: se abre la pasarela de Google Play, confirma sin consumir y persiste de por vida tras reinstalar.
- [ ] Verificación Criptográfica MD5 / SHA-256 (`🔒 Verificar Integridad MD5 / SHA-256`) y Comparador Hex Diff con hashes MD5 operativos desde el menú principal.
- [ ] Todas las funciones básicas de flasheo de silicio (lectura, escritura, borrado, verificación, detección) continúan operando de forma 100% gratuita y sin anuncios forzados.
