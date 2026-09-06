# Guía de Implementación: Donaciones con Google Play Billing ("Invítame una Pizza 🍕 $5")

Esta guía describe cómo está implementada la donación de $5 en el código de **Flash SPI Tool** y los pasos exactos que debes seguir en **Google Play Console** para activarla y recibir pagos.

---

## 📌 1. Resumen de lo implementado en la aplicación

1. **Librería de Facturación:** Se integró Google Play Billing Library (`com.android.billingclient:billing:7.1.1`).
2. **Permiso en Manifest:** `<uses-permission android:name="com.android.vending.BILLING" />`.
3. **Manejador de Compras (`BillingManager.java`):**
   - Conexión asíncrona con los servicios de Google Play.
   - Consulta el precio localizado del producto (`donacion_pizza`).
   - Inicia la pasarela de pago nativa de Google.
   - **Consumo automático (`consumeAsync`):** Al tratarse de una donación ("invitar una pizza"), el producto se marca como *consumido* inmediatamente después del pago. Esto es indispensable para:
     - Evitar que Google reembolse el dinero a los 3 días por falta de confirmación.
     - Permitir que el mismo usuario pueda donarte otra pizza en el futuro si lo desea.
4. **Menú de la App:** Nueva opción **"Apoyar con una Pizza 🍕 ($5)"** en el menú principal (`main_menu.xml`) con soporte en español e inglés.

---

## ❓ 2. ¿Puedo subir el `.aab` directo a Producción y saltarme la prueba interna?

### Respuesta corta:
**Sí, técnicamente puedes subirlo directo a Producción, pero debes tener MUCHO cuidado con el orden de los pasos.**

### ¿Por qué existe este dilema en Google Play Console?
* Google Play Console **bloquea** la sección *"Productos integrados"* hasta que subes al menos un paquete (`.aab`) que contenga el permiso `com.android.vending.BILLING`.
* Si subes el AAB directamente a **Producción** y le das a *"Enviar a revisión"*:
  1. La versión pasará a cola de revisión humana de Google (suele tardar de 2 a 7 días).
  2. Si el revisor de Google abre la app, toca "Invitar una pizza" y el producto aún no está creado/activo en la consola, **te rechazarán la actualización** por "compra integrada rota".
  3. No podrás probar la compra con tu cuenta de prueba antes de que esté pública.

### Las dos formas de hacerlo:

#### Opción A (Recomendada por Google y la más rápida): Subir primero a "Pruebas internas"
1. Subes el `.aab` a la pista de **Pruebas internas (Internal testing)**.
2. **Ventaja:** Esta pista **NO requiere revisión manual de Google**; se procesa automáticamente en 2 minutos.
3. Apenas se procesa, la pestaña *Productos integrados* se desbloquea al instante.
4. Creas el producto `donacion_pizza`, lo activas y lo pruebas en tu móvil con tarjeta de prueba (gratis).
5. Una vez verificado, en la misma consola haces clic en **"Promocionar versión" > "Producción"**. No necesitas volver a compilar ni resubir nada.

#### Opción B: Subir directo a Producción (Sin pasar por pruebas internas)
Si prefieres no usar la pista interna:
1. Ve a **Producción** > **Crear nueva versión**.
2. Sube el `.aab`.
3. **¡IMPORTANTE! NO HAGAS CLIC EN "Guardar y enviar a revisión".** Déjalo como **Borrador (Draft)**.
4. Con el AAB cargado en el borrador, Google ya detecta el permiso `BILLING`.
5. Ve en el menú lateral a **Monetizar con Play** > **Productos integrados**.
6. Crea el producto `donacion_pizza`, ponle precio ($5) y haz clic en **Activar**.
7. Vuelve a tu borrador de Producción y ahora sí haz clic en **Revisar y enviar a producción**.

---

## 🛠️ 3. Paso a Paso en Google Play Console

### Paso 1: Configurar tu Perfil de Pagos (Merchant Account)
* En Google Play Console, ve a **Configuración** (menú izquierdo inferior) > **Perfil de pagos**.
* Si no lo tienes configurado, completa los datos fiscales y asocia tu cuenta bancaria donde Google te transferirá los ingresos de las donaciones.

---

### Paso 2: Crear el Producto Integrado en la Consola
Una vez subido el AAB (ya sea en Pruebas Internas o como Borrador de Producción):

1. En el menú lateral izquierdo, ve a **Monetizar con Play** > **Productos integrados** (In-app products).
2. Haz clic en el botón **Crear producto**.
3. Configura exactamente los siguientes valores:
   * **ID de producto:** `donacion_pizza` *(debe ser idéntico al del código)*
   * **Nombre:** `Invítame una pizza 🍕`
   * **Descripción:** `Apoya el desarrollo continuo de Flash SPI Tool.`
   * **Estado:** Activo
   * **Precio:** Haz clic en *Fijar precio* e ingresa `5.00` USD (Google convertirá el equivalente a las monedas locales de todos los países automáticamente).
4. Haz clic en **Guardar** y luego en **Activar producto**.

---

### Paso 3: Probar la compra gratis (License Testing)
Para asegurarte de que todo funciona sin gastar tu propio dinero:

1. En Google Play Console, ve a **Configuración** > **Acceso y cuentas** > **Cuentas con acceso de prueba** (License Testing).
2. Añade la dirección de correo de Gmail con la que tienes configurado tu teléfono personal.
3. En el campo **Respuesta de la licencia**, déjalo en `RESPOND_NORMALLY`.
4. Abre la app en tu teléfono:
   * Al tocar la opción de la pizza en el menú, Google Play abrirá una ventana que dirá:
     > *"Tarjeta de prueba, siempre aprueba"* (Test card, always approves).
   * Al completar el flujo, no se te cobrará nada y la app mostrará el mensaje de *"¡Muchas Gracias! ❤️"*.

---

## 📦 4. Compilación del AAB para Producción

Para compilar el Android App Bundle listo para subir a Google Play:

```bash
# 1. Configurar SDK si es un entorno nuevo
bash setup-sdk.sh

# 2. Generar el AAB de release
./gradlew bundleRelease
```

El archivo `.aab` generado estará ubicado en la carpeta de compilación configurada (`/tmp/calculo/outputs/bundle/release/app-release.aab` o `app/build/outputs/bundle/release/app-release.aab`).
Subes ese archivo a Google Play Console y sigues los pasos de esta guía.

---

## 📝 5. Notas de la Versión para Google Play Console (Copiar y Pegar)

Cuando subas la versión a Google Play Console, puedes copiar y pegar directamente este texto en la sección **"Notas de la versión"**:

### 🇪🇸 Español (es-419 / es-ES):
```text
• Nueva opción en el menú: "Apoyar con una Pizza 🍕 ($5)" mediante Google Play Billing.
• Detección automática de precios en moneda local según tu país.
• Consumo inmediato de compra que permite realizar aportes múltiples.
• Iconografía mejorada y optimizaciones internas de estabilidad.
```

### 🇺🇸 English (en-US):
```text
• New menu option: Support with a Pizza 🍕 ($5) via Google Play Billing.
• Automatic localized currency and price detection.
• Instant purchase consumption allowing multiple voluntary donations.
• Improved iconography and internal stability optimizations.
```

