# Consentimiento y privacidad — Flash SPI Tool

- **Paquete:** `com.diamon.curso`
- **Política de privacidad:** https://todoandroid.42web.io/privacy-policy.html
- **SDK de consentimiento:** Google User Messaging Platform (UMP) 4.0.0
- **Última actualización:** 2026-11-07

Este documento reúne los textos de consentimiento que deben configurarse en **AdMob → Privacidad y mensajes** y el comportamiento que implementa la app.

## Comportamiento implementado en la app

1. Al arrancar se llama a `requestConsentInfoUpdate` (UMP).
2. Si corresponde, se muestra el formulario de consentimiento.
3. `MobileAds.initialize` y cualquier petición de anuncio esperan a que el consentimiento esté resuelto.
4. Si el usuario no consiente, no se carga ningún anuncio.
5. «Opciones de privacidad» solo aparece cuando UMP indica que es obligatorio.
6. Al cerrar ese formulario se reaplica el estado: si acepta, se cargan los anuncios pendientes; si rechaza, se retiran.

## Mensaje de Normativas europeas

Configuración recomendada en AdMob:

- **Segmentación:** Países sujetos al RGPD (EEE, Reino Unido y Suiza).
- **No consentir:** Activado.
- **Idioma predeterminado:** Español; añade los demás idiomas de esta lista.

### Español

- **Título:** Tu privacidad
- **Texto:** Usamos tus datos para mostrar anuncios personalizados, medir su rendimiento y mejorar la aplicación. Puedes aceptar, rechazar o cambiar tus opciones cuando quieras desde el menú «Opciones de privacidad».
- **Botón aceptar:** Consentir
- **Botón rechazar:** No consentir
- **Enlace de opciones:** Opciones de privacidad
- **Enlace de política:** Política de privacidad

### English

- **Título:** Your privacy
- **Texto:** We use your data to show personalised ads, measure their performance and improve the app. You can accept, decline or change your choices at any time from the "Privacy options" menu.
- **Botón aceptar:** Consent
- **Botón rechazar:** Do not consent
- **Enlace de opciones:** Privacy options
- **Enlace de política:** Privacy policy

## Mensaje de Normativas estatales de EE. UU.

Se muestra únicamente desde el punto de entrada «Opciones de privacidad» en los estados regulados.

### Español

- **Título:** Tus opciones de privacidad en EE. UU.
- **Texto:** Puedes solicitar que no se venda ni se comparta tu información personal con fines de publicidad personalizada.
- **Enlace:** No vender ni compartir mi información personal
- **Botón excluirse:** Excluirme
- **Botón descartar:** Descartar

### English

- **Título:** Your US privacy choices
- **Texto:** You can request that your personal information is not sold or shared for personalised advertising.
- **Enlace:** Do Not Sell or Share My Personal Information
- **Botón excluirse:** Opt out
- **Botón descartar:** Dismiss

## Mensajes que NO aplican

- **IDFA:** exclusivo de iOS.
- **Corea:** AdMob no ofrece un mensaje específico para Android; solo aplican europeas y estatales de EE. UU.

## Notas

- La política de privacidad debe ser accesible públicamente y coincidir con lo que declara la ficha de Google Play.
- El host `todoandroid.42web.io` aplica un reto JavaScript antirrobots: los navegadores lo superan, pero conviene comprobar la URL con el validador de AdMob antes de publicar.
