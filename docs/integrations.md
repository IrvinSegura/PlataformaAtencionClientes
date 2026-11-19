# Integraciones y canales

Cómo conectar acciones propias, Shopify y Zendesk, WhatsApp, correo, Messenger e Instagram, y cómo avisa la plataforma al dueño. La configuración general está en el [README](../README.md#cómo-ejecutarlo).

## Acciones: qué recibe tu webhook

```http
POST https://api.mitienda.com/orders/status
Content-Type: application/json
X-SupportChat-Signature: t=1762732800,v1=5f2c…

{"action":"order_status","runId":"…","workspaceId":"…","conversationId":"…","channel":"WEB",
 "customerId":null,"arguments":{"order_number":"123456"},"sentAt":"2026-11-10T00:00:00Z"}
```

Comprueba la firma (HMAC-SHA256 en hexadecimal de `t + "." + cuerpo` con el secreto de **Acciones → Verificar las
llamadas**) y rechaza las de más de 5 minutos. Responde un 2xx con JSON. El asistente redacta la respuesta al cliente
a partir de ese JSON. Si incluye `{"message": "…"}`, en una acción confirmada ese texto se muestra tal cual. En
WhatsApp, `customerId` es el número del cliente. En desarrollo (`dev`) se permiten webhooks `http` en esta máquina.

## Shopify y Zendesk

En el panel, **Integraciones**:

- **Shopify**: en el admin de la tienda, **Configuración → Apps y canales de venta → Desarrollar apps**, crea una app
  con los permisos `read_orders` y `write_orders`, instálala y copia el token de acceso de la API de administración
  (`shpat_…`). En el panel pon la tienda (`mitienda` o `mitienda.myshopify.com`) y el token.
  `SHOPIFY_API_VERSION` cambia la versión de la API (por defecto `2025-07`).
- **Zendesk**: en **Centro de administración → Apps e integraciones → API de Zendesk**, activa el acceso por token y
  agrega uno. En el panel pon el subdominio (`mitienda` de `mitienda.zendesk.com`), el correo del agente con el que se
  crean los tickets y el token.

Las acciones aparecen en el asistente en cuanto la integración está activa. Si una acción propia se llama igual que
una de integración, se rechaza (los prefijos `shopify_` y `zendesk_` están reservados).

## WhatsApp

1. En Meta for Developers crea una app con el producto WhatsApp y un número de la Cloud API.
2. En el panel, **WhatsApp**: pega el identificador del número, un token de acceso (de un usuario del sistema, para
   que no caduque) y el secreto de la app.
3. Copia la URL del webhook y el token de verificación que muestra el panel a la configuración de WhatsApp de tu app
   en Meta y suscríbete al campo `messages`. La URL debe ser pública con TLS; en local, usa un túnel hacia el puerto
   8080.
4. Elige el modelo que responde en ese número: **Automático** (según la pregunta), **Rápido** o **Preciso**.
   Los celulares de México llegan en el webhook como `521` y diez dígitos; el backend les responde a `52` y diez
   dígitos, el único formato que la Cloud API entrega (y el que guarda la lista de destinatarios de un número de
   prueba).
5. Mientras la app de Meta no esté publicada, solo llegan los webhooks de prueba del panel de Meta, no los mensajes
   reales. Para publicarla, en **Configuración de la app → Básica** pon como URL de la política de privacidad
   `https://<tu-dominio>/legal/privacy` y como URL de eliminación de datos
   `https://<tu-dominio>/legal/privacy#eliminacion`, elige una categoría y sube un ícono de 1024×1024. Esa página
   es pública, la sirve el backend y nombra a quien opera la plataforma con `LEGAL_OPERATOR_NAME` y
   `LEGAL_CONTACT_EMAIL`; en producción el backend no arranca sin el correo (en desarrollo usa uno de ejemplo).
   Los términos y condiciones están en `/legal/terms`, con la ley aplicable en `LEGAL_JURISDICTION` (por defecto,
   México y los tribunales de la Ciudad de México). Ambos textos son una base: revísalos con un abogado antes de cobrar. Si cambias
   `backend/src/main/resources/legal/terms.html`, sube `LEGAL_TERMS_VERSION` y `LEGAL_TERMS_UPDATED`: cada usuario
   tendrá que aceptar la nueva versión en el panel.

## Avisos al dueño y clientes molestos

En **Avisos y escalamiento** (dueños y administradores) se decide qué pasa cuando un cliente necesita a una persona:

- **Clientes molestos**: el asistente reconoce quejas, insultos, mayúsculas sostenidas, `!!!`, emojis de enojo y frases
  como "ya te dije" o "nadie me responde" en español, inglés, portugués y francés, sin llamar al modelo. Hacen falta
  dos indicios (una queja fuerte cuenta por dos), así que "¿me lo envían otra vez?" no cuenta. Por defecto la
  conversación pasa al equipo y aparece en **En espera** marcada como **Cliente molesto**; si se desactiva, el asistente
  sigue respondiendo y solo se avisa.
- **Avisos por WhatsApp**: hasta tres números reciben, desde el número de WhatsApp conectado, quién es el cliente, qué
  escribió y el enlace a la conversación (`PANEL_URL`), cuando pide una persona (botón del widget o mensaje) o se nota
  molesto. Llega un aviso por conversación y motivo, y cada aviso cuenta como mensaje de WhatsApp enviado.
- WhatsApp solo entrega un texto libre a un número que le escribió al negocio en las últimas 24 horas. Para recibirlos
  siempre, crea en WhatsApp Manager una plantilla de categoría **Utilidad** con tres variables (`{{1}}` el cliente,
  `{{2}}` qué pasó, `{{3}}` el enlace) y pon su nombre e idioma (`es_MX`) en el panel.
- **Preguntas difíciles que se repiten**: cuando una pregunta que el asistente no responde bien llega a 5 veces sin que
  nadie le enseñe la respuesta, los mismos números reciben una vez la pregunta y el enlace a **Preguntas difíciles**
  (con plantilla, `{{1}}` es "Tus clientes" y `{{2}}` lo que pasó). Se apaga con su casilla en **Avisarme cuando**.
- **Consumo del plan**: cuando las respuestas, acciones o mensajes de WhatsApp del mes llegan al 80 % y al 95 % de lo
  que incluye el plan, los mismos números reciben una vez cada aviso, con cuánto llevan, qué se detiene al llegar al
  límite (o que lo adicional se cobra aparte) y el enlace a **Plan y pagos** para comprar una recarga o cambiar de
  plan. Dueños y administradores ven además un aviso en todas las páginas del panel desde el 80 %. Una recarga sube el
  límite del mes, y los avisos del 80 % y 95 % vuelven a contar sobre el nuevo total. Se apaga con su casilla en **Avisarme cuando**.
- La tabla **Avisos recientes** muestra cada cliente que necesitó a alguien y si el aviso se entregó, fue rechazado (con
  el motivo) o no había WhatsApp conectado.

La API es `GET/PUT /api/v1/assistant/attention` y `GET /api/v1/assistant/attention/alerts`.

## Correo (Gmail u otro proveedor)

1. En la cuenta de Google del buzón activa la verificación en dos pasos y crea una contraseña de aplicación en
   **Cuenta de Google → Seguridad → Contraseñas de aplicaciones** (la contraseña normal no sirve). Comprueba que IMAP
   esté activado en la configuración de Gmail.
2. En el panel, **Correo**: la dirección de soporte, el nombre del remitente y la contraseña de aplicación. Los
   servidores vienen como `imap.gmail.com:993` y `smtp.gmail.com:465`; con otro proveedor usa los suyos (465 usa TLS
   directo y cualquier otro puerto, como 587, exige STARTTLS). El usuario vacío es la propia dirección.
3. A partir de ahí el buzón se lee cada minuto (`supportchat.channels.email.read-delay`). Si falla, el panel muestra
   el motivo. Para confirmar una acción que el asistente propone, el cliente responde a ese correo con **CONFIRMAR**
   o **CANCELAR** (también *confirm*/*cancel*, *sí*/*no* y sus equivalentes en portugués y francés).

## Messenger e Instagram

1. En Meta for Developers crea una app con el producto Messenger (o Instagram) y genera un token de acceso de la
   página (en Instagram, de la página vinculada a la cuenta profesional).
2. En el panel, **Messenger** o **Instagram**: pega el ID de la página o de la cuenta, el token y el secreto de la app.
3. Copia la URL del webhook y el token de verificación a la configuración de webhooks de la app y suscribe la página
   a `messages` y `messaging_postbacks`. Las respuestas se envían con `messaging_type: RESPONSE`, válido solo dentro
   de las 24 h siguientes al último mensaje del cliente. `META_GRAPH_BASE_URL` cambia la versión de la Graph API.
