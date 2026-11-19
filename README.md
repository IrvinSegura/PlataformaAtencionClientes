# Plataforma de Atención al Cliente con IA (RAG multi-tenant)

Plataforma SaaS donde cada empresa sube su base de conocimiento (PDF, Word, Excel, HTML, Markdown, texto o las páginas de su sitio web) y obtiene un asistente
que responde a sus clientes **solo con esa información**, citando las fuentes y diciendo "no sé" cuando no la
tiene, en lugar de inventar. Se integra en cualquier sitio con un widget embebible.

> **Estado: Fase 4 completa.** Además de lo anterior, cada workspace paga su plan con Stripe y ve cuántas
> conversaciones resolvió el asistente. El asistente atiende por el widget, WhatsApp, correo, Messenger e Instagram,
> y usa Shopify y Zendesk sin escribir un webhook. La plataforma se monitorea con Grafana Cloud, se despliega sola en
> una VM gratuita de Oracle Cloud y tiene una prueba de carga reproducible.
>
> **Fase 5 en curso:** el asistente recuerda a los clientes que vuelven, cada conversación se etiqueta con su tema,
> el equipo programa seguimientos, quien opera la plataforma la administra desde el panel y hay una demo pública.

## Qué hace hoy

| Capacidad | Detalle |
|---|---|
| Cuentas y workspaces | Registro crea el workspace y su dueño, con los **términos y condiciones** aceptados (públicos en `/legal/terms`; se guarda cada versión aceptada y su fecha). Si la versión cambia, el panel pide aceptarla antes de seguir. Roles `OWNER`, `ADMIN` y `AGENT`. Una persona puede pertenecer a varios workspaces y cambiar entre ellos sin volver a iniciar sesión |
| Equipo e invitaciones | Dueños y administradores invitan por email con un enlace de un solo uso (7 días, solo se guarda su hash). Quien ya tiene cuenta entra con su contraseña; quien no, la crea al aceptar. El dueño gestiona a todos y cambia roles; un administrador solo invita y quita agentes; nadie toca al dueño |
| Base de conocimiento | Subida de PDF, Word (`.docx`, `.doc`), Excel (`.xlsx`, `.xls`, una línea por fila con sus encabezados: "Producto: Pizza grande · Precio: 189"), HTML (sin menús, scripts ni pies de navegación), Markdown y texto, en UTF-8 o en codificaciones antiguas como Windows-1252. El tipo se reconoce por el contenido, no por la extensión: un ZIP renombrado a `.pdf` se rechaza. Ingesta asíncrona (extracción, chunking con solapamiento, embeddings) con la etapa visible en el panel, reproceso y borrado |
| Importar un sitio web | Se pega la URL de una página y, si se quiere, hasta 30 páginas enlazadas del mismo sitio. Se descarga solo por `http`/`https` a los puertos 80 y 443, nunca a direcciones privadas, de loopback o de metadatos de la nube (también tras cada redirección), con límite de tamaño y de redirecciones. **Actualizar** vuelve a descargarla |
| Playground | Dueños y administradores prueban el asistente con sus propias preguntas antes de publicarlo: la respuesta llega en streaming igual que en el widget y un inspector muestra los fragmentos recuperados (y cuáles usó el modelo), el modelo que respondió y por qué, tokens, latencia, costo y las acciones que habría llamado, sin ejecutarlas. No crea conversaciones; cada respuesta cuenta en el consumo del plan |
| Búsqueda híbrida | Vectorial (pgvector, HNSW coseno) + texto completo (tsvector) fusionadas con Reciprocal Rank Fusion |
| Respuestas fundamentadas | Streaming SSE palabra por palabra, citas `[n]` a los documentos, respuesta fija de "no sé" sin llamar al modelo cuando no hay fuentes relevantes |
| Widget embebible | Una etiqueta `<script>` de 31 KB (10 KB gzip), sin dependencias, aislado en Shadow DOM. Respuesta en streaming, citas que abren su fuente, conversación que sobrevive a recargas, solo en los orígenes que el workspace permite |
| Traspaso a humano | El visitante pulsa **Hablar con una persona**; el asistente deja de responder y sus mensajes esperan al equipo. En el panel la conversación aparece al instante en **En espera**, un agente la toma, responde y la devuelve al asistente o la cierra. Ambos lados se actualizan en vivo por SSE, con avisos en la conversación ("Laura se unió…") |
| Avisos y escalamiento | Un cliente que pide una persona o se nota molesto (quejas, insultos, mayúsculas, "!!!", en cuatro idiomas y sin llamar al modelo) pasa al equipo marcado como **Cliente molesto**, y el dueño recibe un WhatsApp con quién es, qué escribió y el enlace a la conversación, con plantilla aprobada para llegar fuera de la ventana de 24 h. Un aviso por conversación y motivo, con su entrega visible en el panel. También avisa cuando el consumo del mes llega al 80 % y al 95 % del plan |
| Clientes y su memoria | Quien escribe por WhatsApp, correo, Messenger o Instagram se reconoce en cada conversación nueva. **Clientes** los busca por número, correo o nombre y muestra todas sus conversaciones; en cada conversación, la ficha del cliente guarda el nombre que el equipo le da y sus notas. El asistente recibe lo que el cliente dijo en los últimos 30 días (hasta 10 mensajes, sin saludos ni respuestas fallidas, recortado a 2400 caracteres) para no volver a preguntar lo que ya contó. Las notas del equipo nunca llegan al modelo, una respuesta que pudo usar esa memoria nunca se reutiliza para otro cliente, y la ficha permite **no recordar** a un cliente. Los visitantes del widget son anónimos y no se recuerdan |
| Temas de cada conversación | Cada conversación se etiqueta sola, sin llamar al modelo y en cuatro idiomas, con lo que trata: estado del pedido, envíos, devoluciones, pagos, precios, disponibilidad, horarios y ubicación, cuenta o queja. La bandeja se filtra por tema y el equipo corrige los temas de una conversación; desde ese momento ya no se agregan solos |
| Seguimientos | Desde una conversación de WhatsApp, correo, Messenger o Instagram el equipo programa un mensaje para más tarde ("Mañana", "En una semana" o una fecha), escrito a mano o desde una **plantilla** donde `{nombre}` se cambia por el nombre del cliente. Cada minuto salen los que tocan, en la conversación abierta del cliente o en una nueva, a nombre de quien lo programó. Se respeta la ventana de 24 h de Meta: pasadas las 24 h, WhatsApp envía la plantilla aprobada que tenga la plantilla del seguimiento y Messenger e Instagram no envían. **Seguimientos** muestra los programados, enviados, cancelados y los que no se enviaron con el motivo en español |
| Preguntas sin respuesta | Cada "no sé" del asistente se agrupa por pregunta normalizada, con cuántas veces se hizo y la última conversación. Se marcan como resueltas al agregar el documento que las cubre (se reabren si alguien vuelve a preguntar) o se descartan |
| Documentos sugeridos | Las preguntas abiertas que tratan del mismo tema (por similitud de embeddings: "¿Hacen envíos a Colombia?" y "¿Envían a Colombia?") se juntan en un solo documento por escribir, el tema más preguntado primero. Desde el panel se redacta en Markdown sobre un borrador con las preguntas, se sube a la base de conocimiento y sus preguntas quedan resueltas |
| Resultados | El cliente dice si cada respuesta le ayudó (👍/👎) y califica la conversación del 1 al 5 con un comentario opcional, también después de que el equipo la cierra. **Resultados** muestra cuántas conversaciones resolvió el asistente sin ayuda (respondió, nadie del equipo intervino, la última respuesta no se marcó como inútil y la calificación, si hubo, fue de 3 o más), cuántas pasaron a una persona, las respuestas útiles, la calificación promedio y la satisfacción (4 o 5), por día y con los comentarios recientes |
| Evaluación de calidad | 12 casos con base de conocimiento propia miden si el asistente responde o dice "no sé" cuando corresponde, si incluye los datos esperados y si cita la fuente correcta. Corre en cada CI (modo offline) y cada semana contra Claude |
| Panel de operadores | React, con un diseño empresarial: barra lateral agrupada por área (Atención, Asistente, Canales, Analítica, Administración), barra superior con la ruta y los clientes en espera, modo oscuro y sin dependencias de fuentes externas. Conversaciones con fuentes y consumo por respuesta (modelo, tokens, latencia), base de conocimiento (arrastrar varios archivos con barra de subida, etapa de indexado en vivo, importación de sitios web, reproceso, borrado), Playground, probador de búsqueda, configuración del widget con su código de instalación, preguntas sin respuesta y equipo. Lo que cada rol no puede hacer no se muestra |
| Acciones del asistente | Cada empresa describe acciones como "consultar un pedido" o "cambiar la dirección": nombre, cuándo usarlas, los datos que piden (con formato opcional) y la URL de su webhook. El asistente decide cuándo usarlas (tool use) y pide al cliente los datos que falten. Las consultas se ejecutan al momento. Las que cambian algo esperan a que el cliente pulse **Confirmar** (15 min), en el widget o en WhatsApp. Cada llamada va firmada con HMAC y queda registrada en la conversación |
| Shopify y Zendesk | Integraciones listas que se conectan con un token, sin escribir un webhook. Con Shopify el asistente consulta el estado de un pedido (pago, envío, total y seguimiento, nunca la dirección) y lo cancela si el cliente lo confirma; solo muestra o cancela un pedido a quien da el correo con que se hizo (en el canal de correo, el del remitente). Con Zendesk abre un ticket a nombre del cliente, también tras confirmar |
| WhatsApp | Se conecta un número de la Cloud API de WhatsApp Business desde el panel. Los mensajes entrantes (firmados por Meta) siguen el mismo flujo del widget: base de conocimiento, acciones con botones de confirmación y traspaso a humano (escribiendo "quiero hablar con una persona"). Las respuestas del equipo salen por WhatsApp |
| Correo | Se conecta el buzón de soporte por IMAP y SMTP con TLS (en Gmail, con una **contraseña de aplicación**). Cada minuto se leen los correos nuevos, sin marcarlos como leídos; los que llegaron antes de conectar no se responden. Se quita el hilo citado y la firma, y la respuesta sale en el mismo hilo (`Re:`, `In-Reply-To`, `References`). Las acciones que cambian algo se confirman respondiendo **CONFIRMAR** o **CANCELAR**. No se contestan respuestas automáticas, rebotes, listas de correo ni correos que no pasan DMARC. El panel muestra cuándo se leyó el buzón y por qué falló |
| Messenger e Instagram | Se conecta la página de Facebook o la cuenta profesional de Instagram con el token de la página y el secreto de una app de Meta. Comparten el webhook firmado y la Send API: botones de confirmación como respuestas rápidas, nombre del cliente desde su perfil y textos largos partidos en varios mensajes (2000 caracteres en Messenger, 1000 bytes en Instagram). Meta solo deja responder durante 24 h tras el último mensaje del cliente, así que una respuesta del equipo más tarde se descarta |
| Modelo según la pregunta | Las preguntas directas van a un modelo económico (Claude Haiku, esfuerzo bajo). Las que piden razonar, comparar o recomendar (con señales en español, inglés, portugués y francés), las hipotéticas ("si compro 3 y uno llega roto, ¿me devuelven todo?"), las que comparan ("¿por qué mi plan cuesta más que el de mi amigo?"), las que traen varias preguntas, las largas y las que de verdad combinan varios documentos van a uno más capaz (Claude Sonnet). Un "¿por qué?" sobre el caso del propio cliente ("¿por qué no ha llegado mi pedido?") se queda en el económico, y para contar documentos solo valen los casi tan relevantes como el mejor (medido sobre la escala del proveedor de embeddings), sin contar dos veces las páginas que repiten el mismo texto. Si el económico dice "no sé" teniendo fuentes, se reintenta una vez con el capaz. El asistente además **aprende de las preguntas difíciles** (ver abajo). Cada número de WhatsApp puede fijar **Rápido** (siempre el económico) o **Preciso** (siempre el capaz) en lugar de **Automático**, y el Playground deja comparar las tres opciones. El panel muestra qué modelo respondió y por qué |
| Preguntas difíciles | Se guarda cada pregunta con la que el asistente batalló: el modelo rápido no supo, el cliente marcó 👎, dijo "no entendí" o "no me sirvió" (en cuatro idiomas) o volvió a hacer la misma pregunta. Mientras siga abierta, esa pregunta y las parecidas van directo al modelo preciso, y si el cliente muestra que la respuesta no le sirvió, su siguiente mensaje también. Dos preguntas cuentan como la misma si comparten casi todas sus palabras o si significan lo mismo según los embeddings ("¿cuánto tarda el envío?" y "¿en cuántos días llega mi paquete?"). Si nadie la enseña, se cierra sola (**Cerradas solas**) cuando el preciso la responde bien 3 veces seguidas o tras 30 días sin volver a ser difícil, y se reabre con la siguiente señal; así no cuesta de más para siempre. Al repetirse 5 veces sin respuesta enseñada, el dueño recibe un aviso por WhatsApp. En **Preguntas difíciles** se ven la señal, cuántas veces pasó y lo que respondió el asistente; el equipo escribe la respuesta correcta con **Enseñar respuesta** (también desde Preguntas sin respuesta), que llega con un borrador escrito por el modelo preciso a partir de los documentos para solo corregirlo, y queda en la base de conocimiento como un documento. **Resultados** muestra las respuestas por modelo y motivo con su tasa de 👎, para saber qué ajustar |
| Respuestas sin costo | Un mensaje que solo saluda, agradece, se despide o dice "ok" (en español, inglés, portugués y francés) recibe una respuesta fija, sin buscar ni llamar al modelo, y no cuenta en el plan ni como pregunta sin respuesta. Un "ok" a algo que preguntó el asistente sí pasa al modelo. Una pregunta repetida, sin contexto previo, recibe la respuesta que el modelo ya escribió (0 tokens) mientras no cambien los documentos, las acciones ni el nombre del asistente, hasta 7 días; nunca se reutiliza una respuesta que usó una acción ni una que un cliente marcó como inútil |
| Administración de la plataforma | Quienes operan la plataforma (configurados por id de usuario, nunca por correo, porque el registro no comprueba direcciones) ven en el panel todos los workspaces con su dueño y correo, plan, respuestas del mes y conversaciones de los últimos 30 días, sin acceso a sus conversaciones ni a su base de conocimiento. Pueden **suspender** uno con un motivo: su asistente deja de responder y pasa las conversaciones al equipo, igual que un plan agotado, y sus miembros ven el motivo en el panel hasta que se **reactiva** |
| Demo pública | `/demo` muestra el widget de Café Aurora, una cafetería ficticia con su catálogo, envíos, pagos y guía de preparación. `deploy/demo/seed-demo.sh` la prepara con la propia API. Responde siempre con Claude Haiku, tiene un tope diario de respuestas y cada visitante tiene su propio límite de preguntas |
| Despliegue | Una VM gratuita de Oracle Cloud (arm64) con Docker Compose, Caddy con TLS automático, respaldos nocturnos cifrados a Object Storage y despliegue desde GitHub Actions por SSH, con prueba de humo y vuelta atrás a cualquier versión |
| Monitoreo | Métricas de negocio (respuestas, tokens, traspasos, opiniones, acciones, entregas por canal) y trazas OpenTelemetry hacia Grafana Cloud gratis, con un tablero y 12 alertas listas |
| Consumo | Cada workspace registra por día las respuestas (por canal), los tokens (por modelo), las acciones ejecutadas y los mensajes de WhatsApp. **Consumo** muestra el mes frente a lo que incluye su plan |
| Planes y cobro | Planes con cantidades incluidas al mes (gratis, Inicial, Profesional; configurables). El dueño contrata en el checkout de Stripe, cambia de plan o lo cancela al final del periodo, y administra tarjeta y facturas en el portal de Stripe. Lo que pasa del incluido se reporta a los medidores de Stripe y se cobra en la factura. Al llegar al límite de un plan sin cobro extra, el asistente pasa las conversaciones al equipo, las acciones se rechazan y los mensajes de WhatsApp no se contestan, salvo que el dueño compre una **recarga**: un pago único en Stripe que suma unidades (p. ej. 500 respuestas por 10 USD) hasta el fin del mes, sin cambiar de plan. Cuando una cuota pasa del 80 %, **Plan y pagos** marca como **Recomendado** el plan más barato que la cubre. Si un pago falla, el plan sigue 7 días mientras Stripe reintenta; después aplica el gratuito |
| Idiomas | El asistente responde en el idioma en que escribe el cliente. Lo que la plataforma escribe por su cuenta (el "no sé", los errores, las respuestas de las acciones, los avisos de WhatsApp y los textos del widget) sale en español, inglés, portugués o francés: al principio en el del navegador (`Accept-Language`) y después en el de los mensajes del cliente, que se detecta sin llamar al modelo. Con embeddings de Voyage, que son multilingües, una pregunta en inglés encuentra documentos en español; los embeddings offline solo comparan textos del mismo idioma |
| Proveedores | Claude (Anthropic) y Voyage AI, o modos **offline** que funcionan sin API keys para desarrollo, pruebas y demos |

## Arquitectura

Monolito modular (Spring Modulith) con arquitectura limpia dentro de cada módulo. Los módulos solo se hablan por su
API pública y por eventos; las reglas se verifican en cada build (`ModularityTests`, `CleanArchitectureTests`).

```mermaid
flowchart LR
    W[Widget en el sitio del cliente] -->|SSE| WC[conversations]
    P[Panel de operadores] -->|JWT| ID[identity]
    P --> K[knowledge]
    P --> WC
    WC -->|KnowledgeSearch| K
    WC -->|evento QuestionUnanswered: lagunas| K
    WC -->|Workspaces| WS[workspaces]
    ID -->|Workspaces| WS
    K -->|evento DocumentQueued| K
    K --> DB[(PostgreSQL + pgvector + RLS)]
    WC --> DB
    WC --> LLM[Claude / offline]
    K --> EMB[Voyage / hashing]
    WA[WhatsApp / Messenger / Instagram] -->|webhook firmado| CH[channels]
    MB[Buzón IMAP/SMTP] <-->|lectura cada minuto y respuestas| CH
    CH -->|ChannelConversations| WC
    WC -->|ConversationReply| CH
    CH --> WA
    WC -->|AssistantActions| AC[actions]
    AC -->|HTTPS firmado| BIZ[Sistemas de la empresa]
    WC -->|UsageMeter| BI[billing]
    CH --> BI
    AC --> BI
    BI -->|suspensión| WS
    PL[platform] -->|WorkspacePlans| BI
    PL -->|ConversationActivity| WC
    PL -->|Workspaces| WS
    PL -->|OperatorDirectory| ID
    FU[followups] -->|CustomerMessages| WC
    FU -->|OperatorDirectory| ID
```

Cada módulo (`identity`, `workspaces`, `knowledge`, `conversations`, `followups`, `actions`, `channels`, `billing`,
`platform`) se organiza así:

```
<módulo>/                 API pública del módulo (interfaces y records que otros módulos pueden usar)
  domain/model/           reglas de negocio puras, sin Spring ni base de datos
  domain/port/            interfaces que el dominio necesita (repositorios, modelos de IA)
  application/            casos de uso y transacciones
  infrastructure/         adaptadores: JDBC, clientes HTTP de IA, configuración
  web/                    controladores REST
```

`shared` contiene lo transversal: tenancy, errores (RFC 9457), rate limiting, correlation id y la carga de los textos
que la plataforma escribe por sí misma (respuestas fijas, avisos, botones, alertas), que viven en
`backend/src/main/resources/texts/<área>/<idioma>.properties`.

Los frontends viven junto al backend:

```
backend/   API (Java 25, Spring Boot)
widget/    widget.js para los sitios de los clientes (TypeScript sin framework, Vite)
panel/     panel de operadores (React, React Router, TanStack Query, Vite); src/api/ tiene un archivo por módulo
deploy/    todo lo que corre en contenedores: producción, init de PostgreSQL, monitoreo y la demo pública
docs/      integraciones, API y operación
loadtest/  prueba de carga con k6, también contra una copia de producción (dos réplicas, Caddy, Redis)
scripts/   configuración única desde tu máquina (Stripe)
```

### Decisiones técnicas relevantes

- **Aislamiento de tenants en la base de datos, no solo en el código.** Las tablas con datos de clientes tienen
  Row-Level Security (`ENABLE` + `FORCE`). Cada transacción fija `app.workspace_id`. Si alguien olvida el filtro, o no
  hay workspace ligado, la base devuelve **cero filas**: falla cerrado. La aplicación se conecta con un rol que no es
  dueño de ninguna tabla. Flyway migra con el rol dueño.
- **JdbcClient en lugar de JPA**: SQL explícito para pgvector, tsvector, `FOR UPDATE` y CTEs de búsqueda.
- **Ingesta con outbox** (eventos de Spring Modulith): si el proceso muere a la mitad, el evento se reintenta al
  arrancar. Un documento atascado en `PROCESSING` se recupera tras 10 minutos.
- **Hilos virtuales**: cada respuesta en streaming ocupa un hilo bloqueado mientras el modelo escribe, lo que es
  barato con hilos virtuales. Ninguna transacción queda abierta durante la generación.
- **Validar antes de transmitir**: la pregunta se autentica, valida y registra antes del primer byte del stream, así
  los errores son respuestas HTTP normales. Una vez iniciado el stream, cualquier fallo se convierte en un mensaje
  amable dentro de él.
- **Widget sin framework**: se carga en cada página de cada cliente, así que es TypeScript puro con un presupuesto de
  32 KB que la build hace cumplir. Lee el SSE con `fetch` (EventSource no permite POST ni headers). Todo el texto del
  servidor se inserta como texto, nunca como HTML. La conversación se crea con la primera pregunta, no al cargar la
  página. Si el token del visitante expiró, abre una conversación nueva y reintenta una sola vez.
- **Actualizaciones en vivo con SSE, no WebSockets**: el servidor solo empuja (cambios de estado y mensajes) y el
  cliente escribe con POST normales, así que SSE basta, pasa por cualquier proxy y reutiliza la autenticación de
  siempre. Cada conexión se cierra a los 15 (panel) o 30 minutos (widget) para que el cliente vuelva a autenticarse.
- **Varias instancias con PostgreSQL `LISTEN/NOTIFY`**: cada cambio hace `pg_notify` dentro de su transacción, así
  que PostgreSQL lo entrega a todas las réplicas solo si confirma y en orden de commit. El aviso lleva solo ids
  (tipo, workspace, conversación, mensaje); la réplica que tiene streams abiertos lee el estado actual con RLS y lo
  empuja, y las demás no consultan nada. Cada réplica escucha con una conexión propia fuera del pool; si se cae,
  reconecta y cierra sus streams para que los clientes recarguen lo que se perdió: el panel vuelve a pedir sus
  listas y el widget, al abrir cada stream, trae del historial las respuestas del equipo que no tenga en pantalla. No
  hace falta Redis. Redis solo entra para el rate limiting con varias réplicas (`RATE_LIMIT_STORE=redis`), y si cae las
  peticiones pasan sin limitar en vez de fallar.
- **El traspaso no consume preguntas**: solo cuentan para el límite por conversación las que responde el asistente,
  que son las que cuestan tokens. Los avisos del sistema se guardan como códigos y cada cliente los traduce.
- **Acciones como webhooks, no como código**: el modelo solo propone la llamada (nombre y datos). La plataforma
  valida los datos contra la definición (tipos, obligatorios, formato) antes de enviar nada, y el resultado vuelve
  al modelo como datos entre etiquetas, nunca como instrucciones. Un cambio propuesto se guarda como ejecución
  pendiente y solo corre cuando el cliente lo confirma. Así una inyección de prompt no puede ejecutar un cambio por
  su cuenta, y el doble clic o dos pestañas no lo ejecutan dos veces (la ejecución se reclama con un `UPDATE`
  condicional). La llamada HTTP ocurre fuera de cualquier transacción.
- **Integraciones encima de las acciones**: Shopify y Zendesk son acciones predefinidas (`shopify_*`, `zendesk_*`,
  nombres reservados) que pasan por el mismo `ActionRunner`: validación, confirmación, registro y consumo. Solo cambia
  quién ejecuta la llamada: un `IntegrationGateway` habla con la API del proveedor (GraphQL Admin de Shopify, REST de
  Zendesk) en vez de con el webhook de la empresa.
- **El canal no cambia la conversación**: WhatsApp, Messenger, Instagram y el correo son adaptadores del mismo caso
  de uso. El módulo `channels`
  traduce el webhook de Meta a un mensaje entrante y las respuestas (`ConversationReply`) a mensajes de la Graph API,
  con botones para confirmar acciones. La conversación abierta de cada número se reutiliza mientras siga activa. Las
  entregas repetidas de Meta se descartan por id de mensaje. Si un número llega al límite de preguntas, la
  conversación pasa al equipo en lugar de abrir otra, así el gasto sigue acotado.
- **Enrutamiento sin otra llamada al modelo**: la elección del modelo usa reglas baratas sobre la pregunta y las
  fuentes recuperadas (palabras que piden razonar, varias preguntas, longitud, documentos distintos), no un
  clasificador que costaría tokens en cada pregunta. El único reintento es el de un "no sé" con fuentes.
- **Medición en la misma transacción**: el consumo se suma en contadores diarios dentro de la transacción que guarda
  la respuesta, así que no se cobra lo que no quedó guardado. El precio se aplica al leer, con la lista vigente.
- **Sesión del panel sin carreras**: el access token vive solo en memoria y se renueva con la cookie al recibir un 401.
  Como el backend revoca toda la sesión si un refresh token se usa dos veces, las renovaciones se comparten dentro de
  la pestaña y se ponen en fila entre pestañas con la Web Locks API.

## Seguridad

| Amenaza | Defensa |
|---|---|
| Fuga entre empresas | RLS forzado + rol sin privilegios de dueño + llaves foráneas que incluyen el workspace (una fila no puede apuntar a la conversación de otra empresa) + pruebas que lo verifican contra PostgreSQL real |
| Prompt injection en documentos | Las fuentes van en etiquetas delimitadas y se neutralizan las que intenten cerrarlas. El prompt de sistema declara las fuentes como datos, no instrucciones. El nombre del workspace se sanea |
| Respuestas inventadas | Umbral de similitud propio de cada modelo de embeddings (0.35 Voyage, 0.2 hashing; `ANSWER_MIN_SIMILARITY` lo fija a mano) y selección de fuentes. Sin fuentes no se llama al modelo. El marcador `NO_ANSWER` nunca llega al visitante |
| Robo de sesión del panel | Access token JWT RS256 de 15 min. Refresh token opaco en cookie `HttpOnly` + `SameSite=Strict` + ruta limitada, rotado en cada uso, con detección de reutilización que revoca toda la familia |
| Invitaciones | El token viaja en el fragmento de la URL (`#`), que el navegador no envía a servidores ni deja en logs ni en `Referer`, y el panel lo borra de la barra de direcciones. Se envía en el cuerpo, se guarda hasheado y se bloquea la fila al aceptarla para que no se use dos veces. Rate limiting por IP |
| CSRF | Endpoints con cookie exigen el header `X-CSRF-Protection` (un formulario de otro sitio no puede enviarlo) |
| Fuerza bruta | Bloqueo tras 5 intentos fallidos por 15 min. Tiempo de respuesta constante para emails inexistentes. Rate limiting por IP, usuario y widget |
| Enumeración de cuentas | El registro responde si un email ya tiene cuenta (no se verifica el correo al registrarse), así que tiene su propio límite: 10 intentos cada 10 min por IP |
| Abuso del widget | CORS con los orígenes de cada workspace. Token secreto por conversación (solo se guarda su hash SHA-256, comparación en tiempo constante). Límite de preguntas por conversación |
| Contraseñas | BCrypt costo 12. Mínimo 12 caracteres, máximo 72 bytes (lo que BCrypt no trunca) |
| Acciones peligrosas | Los cambios requieren confirmación explícita del cliente. Los datos se validan contra la definición antes de llamar. Los webhooks deben ser `https` públicos: se rechazan IPs privadas, de loopback y link-local (SSRF). Timeout de 10 s y solo se leen los primeros 16 KB de la respuesta |
| Webhooks falsificados | Cada llamada a la empresa lleva `X-SupportChat-Signature: t=<segundos>,v1=<HMAC-SHA256 hex de "t.cuerpo">` con un secreto por workspace (solo el dueño lo rota). Los mensajes de WhatsApp, Messenger e Instagram se aceptan solo con una firma `X-Hub-Signature-256` válida del secreto de la app (comparación en tiempo constante) |
| Integraciones | Las URL de Shopify (`<tienda>.myshopify.com`) y Zendesk (`<subdominio>.zendesk.com`) son fijas: el workspace solo elige la tienda o el subdominio, así que no puede apuntar a otra dirección (sin SSRF). Un pedido de Shopify solo se muestra o se cancela si el correo coincide con el del pedido, y no se cancela uno ya enviado. Solo dueño y administradores las conectan |
| Correo | Los servidores IMAP y SMTP deben ser nombres públicos (se rechazan IPs privadas y de loopback al guardar y antes de cada conexión), siempre con TLS verificando el certificado. Se ignoran los correos que fallan DMARC, los automáticos (`Auto-Submitted`, `List-Id`, `Precedence`, mailer-daemon) y los del propio buzón, para no responder a suplantaciones ni entrar en bucles con otros contestadores; las respuestas del asistente salen con `Auto-Submitted: auto-replied`. Una acción solo se confirma respondiendo al correo que la propuso, cuyo `Message-ID` lleva la ejecución |
| Secretos guardados | El secreto de firma, los tokens de WhatsApp, Messenger, Instagram, Shopify y Zendesk y la contraseña del buzón se cifran con AES-256-GCM (`SECRETS_ENCRYPTION_KEY`). Son de solo escritura: nunca vuelven al panel |
| Exposición | CSP `default-src 'none'`, `nosniff`, sin stack traces en errores. Actuator en puerto privado en producción. Contenedor sin root |

## Cómo ejecutarlo

Requisitos: JDK 25 y Docker.

1. Copia la configuración local:

   ```bash
   cp .env.example .env
   ```

2. Arranca el backend (perfil `dev`: genera llaves JWT al iniciar, Swagger activo). Con Docker abierto, al arrancar
   levanta solo PostgreSQL con pgvector (`docker compose up`, puerto 5450, solo localhost), espera a que esté listo y
   lo deja corriendo al detenerse:

   ```bash
   cd backend && ./mvnw spring-boot:run
   ```

   Desde IntelliJ basta con ▶ en `SupportChatApplication` (funciona con la raíz del repositorio o `backend/` como
   directorio de trabajo). Para usar otra base de datos, define `DOCKER_COMPOSE_ENABLED=false` y `DB_URL`. O todo en
   contenedores: `docker compose --profile app up -d --build`.

3. Explora la API en http://localhost:8080/swagger-ui.html.

4. Arranca los frontends (requieren Node 24), cada uno en su terminal:

   ```bash
   cd panel && npm install && npm run dev
   ```

   ```bash
   cd widget && npm install && npm run dev
   ```

   El panel queda en http://localhost:5173 (en desarrollo llama a la API a través del proxy de Vite). Crea tu cuenta,
   sube documentos y, en **Widget**, agrega `http://localhost:5174` a los sitios permitidos y pulsa **Abrir página de
   prueba**: es una página que simula el sitio de un cliente con el widget instalado. El servidor del widget también
   sirve `/widget.js`, así que el código de instalación del panel funciona en cualquier página local.

> En Windows, si el backend falla al arrancar con `Unable to establish loopback connection`, la carpeta temporal está
> llena de archivos `socket_*`. Bórralos o arranca con
> `./mvnw spring-boot:run "-Dspring-boot.run.jvmArguments=-Djdk.net.unixdomain.tmpdir=target"`.

Para respuestas reales con Claude y embeddings de Voyage, define en `.env`:

```
CHAT_MODEL_PROVIDER=anthropic
ANTHROPIC_API_KEY=...
EMBEDDING_PROVIDER=voyage
VOYAGE_API_KEY=...
```

Si cambias de proveedor de embeddings, reprocesa los documentos: los vectores de proveedores distintos no son
comparables.

Con Claude, las preguntas simples van a `ANTHROPIC_SIMPLE_MODEL` (por defecto `claude-haiku-5-5`) y las complejas a
`ANTHROPIC_MODEL` (por defecto `claude-sonnet-5-5`). `MODEL_ROUTING_ENABLED=false` manda todo al segundo. Cuántos documentos cuentan como "varios" se ajusta con
`ROUTING_SEVERAL_DOCUMENTS` (3), qué tan cerca del mejor debe estar uno para contar con `ROUTING_DOCUMENT_CLOSENESS`
(0.25) y cuánto texto deben compartir dos páginas para contar como una con `ROUTING_SAME_TEXT_OVERLAP` (0.6); el
reporte de Resultados sirve para calibrarlos.
`ANSWER_REUSE_ENABLED=false` hace que el modelo vuelva a escribir cada respuesta aunque la pregunta se repita.
Las preguntas difíciles se cierran solas tras `DIFFICULT_ANSWERED_WELL_TO_CLOSE` (3) respuestas buenas seguidas del
modelo preciso o `DIFFICULT_QUIET_PERIOD` (`30d`) sin volver a ser difíciles, avisan por WhatsApp al llegar a
`DIFFICULT_ALERT_AFTER` (5) veces, y dos preguntas significan lo mismo desde `DIFFICULT_SAME_QUESTION_SIMILARITY`
(vacío: 0.8 con Voyage, 0.9 con hashing); calíbralos con unas semanas de datos en Resultados. En modo
offline las acciones también funcionan: se llama a una acción cuando la pregunta comparte palabras con su
descripción y contiene los datos que pide (por ejemplo, un número de pedido que cumple su formato).

La memoria de los clientes se apaga con `CUSTOMER_MEMORY_ENABLED=false` y `CUSTOMER_MEMORY_LOOK_BACK` (`30d`) fija
hasta cuándo atrás se recuerda. `PLATFORM_ADMIN_USER_IDS` lista, separados por comas, los ids de usuario (los da
`GET /api/v1/me`) de quienes administran la plataforma; vacío, nadie la administra.

### Más documentación

- [Integraciones y canales](docs/integrations.md): acciones propias, Shopify y Zendesk, WhatsApp, correo, Messenger e Instagram, y avisos al dueño.
- [API: flujo de ejemplo y permisos](docs/api.md): un recorrido con `curl` y quién puede llamar cada endpoint.
- [Operación](docs/operations.md): producción, despliegue en Oracle Cloud, monitoreo y prueba de carga.
- [Contrato OpenAPI](docs/openapi.json): la API de operadores tal como la sirve el backend. `OpenApiContractIntegrationTest`
  falla si la API cambia sin actualizarlo (`./mvnw test -Dtest=OpenApiContractIntegrationTest -Dopenapi.update=true`);
  el panel genera `src/api/openapi.ts` con `npm run api:types` y `src/api/contract.ts` hace fallar a `tsc` si sus tipos
  ya no coinciden con la API, también si un campo que puede llegar nulo es uno que el panel da por presente. Los
  campos de respuesta que pueden ser nulos se marcan `@Nullable` (JSpecify) y las pruebas fallan si otro llega nulo.

## Pruebas

```bash
cd backend && ./mvnw verify
```

575 pruebas: unitarias de dominio, reglas de arquitectura y módulos, integración contra **PostgreSQL real con
pgvector** (Testcontainers) conectando con el rol restringido para que RLS se ejercite de verdad, y del adaptador de
Claude contra un servidor SSE local. Cubren el flujo de autenticación con rotación y reutilización de tokens, el
aislamiento entre workspaces, la ingesta y búsqueda, y el chat con streaming, citas y "no sé", las preguntas difíciles (👎, "no entendí", repetir la pregunta, enseñar la respuesta y que la siguiente ya la use, el reporte por modelo), el traspaso a humano (permisos, preguntas reenviadas, límites), las
invitaciones y el equipo, las preguntas sin respuesta, los eventos en vivo entre instancias (incluida la caída y
reconexión de la conexión que escucha), las acciones contra un servidor HTTP local (firma, confirmación, cancelación,
doble confirmación, otro visitante), Shopify y Zendesk simulados (consulta solo con el correo del pedido,
cancelación confirmada, pedido ya enviado, ticket a nombre del cliente, token que se conserva), WhatsApp, Messenger e Instagram contra una Graph API simulada (verificación, respuesta con fuentes,
entregas repetidas, traspaso, firmas inválidas, otra página de la misma app), el correo contra un servidor GreenMail
real (lectura por IMAP desde el último mensaje, respuesta por SMTP en el hilo, correos automáticos ignorados,
confirmar una acción respondiendo CONFIRMAR, buzón con contraseña equivocada) y la lectura de correos (hilo citado
en varios idiomas, Outlook, firmas, HTML, DMARC), el consumo registrado, las métricas de negocio (sin etiquetas por workspace), el endpoint de métricas abierto solo en el puerto de gestión y las trazas enviadas por OTLP, el
enrutamiento de modelos y el cobro contra
un Stripe simulado (checkout, cambio y cancelación de plan, eventos firmados repetidos o desordenados, firmas
falsas o viejas, periodo de gracia, pausa del asistente al agotar el plan, recargas pagadas una vez (también con pago diferido) que suben el límite del mes, reporte a los medidores una sola vez,
portal, facturas y permisos), las opiniones de los clientes (solo sobre respuestas del asistente de su propia
conversación, calificaciones inválidas, otro visitante), el reporte de resultados con sus periodos y permisos, y la
agrupación de preguntas sin respuesta por tema, la detección de idioma y las respuestas fijas, los saludos y agradecimientos sin modelo, las respuestas reutilizadas (que dejan de darse al cambiar un documento, en una pregunta de seguimiento o tras una mala opinión), los avisos de
WhatsApp y los textos del widget en el idioma del cliente, la extracción de Word, Excel, HTML y textos en
Windows-1252 (y el rechazo de archivos renombrados), la descarga de páginas contra un servidor HTTP local (redirecciones,
bucles, tamaño, direcciones privadas y de metadatos rechazadas), la importación de un sitio con sus páginas enlazadas,
el Playground (streaming, inspector, modelo elegido, consumo sin crear conversaciones) y el modelo fijado por número
de WhatsApp, la detección de clientes molestos en cuatro idiomas, los avisos al dueño por WhatsApp (texto, plantilla,
número fuera de la ventana de 24 h, un solo aviso por conversación), el traspaso automático de un cliente molesto, la política de privacidad y los términos públicos
(sin sesión, con sus estilos permitidos y el nombre y el correo del operador escapados) y la aceptación de los términos
(sin ella no se crea la cuenta ni por registro ni por invitación, y una versión nueva se acepta de nuevo), la memoria de los clientes (lo que se recuerda y lo que no, la ficha y las notas del equipo), los temas detectados en cuatro idiomas con su filtro y su corrección, los seguimientos (ventana de 24 h, plantilla aprobada de WhatsApp, cancelación, envío a su hora), la administración de la plataforma (cerrada para todos los demás, suspensión que pausa al asistente y reactivación) y la demo pública (siempre el modelo económico, tope diario).

La evaluación de calidad (`AnswerQualityEvaluationTest`) deja su reporte en `backend/target/eval/answer-quality.md`
y la CI lo publica en el resumen de cada ejecución. En modo offline el umbral es 0.9 por métrica y hoy acierta los 12
casos (1.00 en decisión, datos y citas); el workflow semanal `answer-quality.yml` la corre contra Claude con umbral 0.9 y necesita el
secreto `ANTHROPIC_API_KEY` (y `VOYAGE_API_KEY` si quieres embeddings reales). También se lanza a mano desde Actions.

```bash
cd widget && npm test
```

```bash
cd panel && npm run lint && npm test
```

Widget (33 pruebas): lector SSE (eventos partidos entre fragmentos, caracteres multibyte, finales de línea `\r\n` y
`\r`), citas, y el widget completo con una API simulada: streaming, reintento con token expirado, límite alcanzado,
historial tras recargar, el traspaso a humano con mensajes en vivo, recuperar lo que el equipo escribió mientras se
reconectaba sin repetir nada, el cierre por el equipo, confirmar o rechazar acciones (también tras recargar, ya
atendidas o sin red), el aviso de que lo atenderá una persona, las opiniones (👍/👎 que se pueden cambiar, la calificación que se pide una
vez y también tras el cierre, errores de red), los textos en el idioma del navegador y que el texto del servidor nunca se interprete como HTML. Panel (96 pruebas): una sola
renovación para llamadas simultáneas, reintento tras 401, errores RFC 9457, login, restauración de sesión, permisos
por rol, aceptar invitaciones, tomar y responder conversaciones, aplicar eventos en vivo sin duplicados, crear acciones y ver su secreto,
conectar WhatsApp, el correo (servidores de Gmail por defecto, error de lectura, contraseña que se conserva), Messenger e
Instagram, conectar Shopify y Zendesk, los nombres de cada canal en las conversaciones, el consumo del mes frente al plan, las conversaciones de WhatsApp con modelo y acciones, y **Plan y
pagos** (contratar en Stripe, cambiar o cancelar, aviso de pago fallido, portal, facturas, permisos), **Resultados**
(indicadores, una columna por día del periodo, cambio de periodo, solo para dueño y administradores), la calificación
y las opiniones en la conversación, los documentos sugeridos (redactar, subir y resolver sus preguntas), las preguntas difíciles (enseñar su respuesta, sin botón para agentes), el Playground (streaming, inspector, historial,
límite del plan, oculto para agentes), arrastrar archivos con su barra de progreso y la etapa de indexado, importar un
sitio web, el modelo de cada número de WhatsApp y **Avisos y escalamiento** (guardar números y plantilla, avisos recientes
con su entrega, aviso sin WhatsApp conectado, cliente molesto en la bandeja) los motivos de error de documentos y páginas en español, los clientes y su ficha, el filtro y la corrección de temas, los seguimientos y sus plantillas (sin edición para agentes), la administración de la plataforma (oculta para los demás) y el aviso de workspace suspendido. La CI corre las tres partes y falla si `widget.js` supera 32 KB.

```bash
cd e2e && npm ci && npx playwright install chromium && npm test
```

De punta a punta (Playwright, 5 flujos): un negocio se registra desde el panel, sube un documento y permite su sitio;
en ese sitio el widget responde con el dato del documento y lo cita, el cliente califica y el panel muestra la
conversación con su opinión; una pregunta fuera de los documentos recibe "no tengo información" y aparece en
**Preguntas sin respuesta**; el cliente pide una persona, la bandeja lo muestra en vivo, el operador la toma, responde
y la cierra, y el widget lo ve sin recargar; cerrar sesión, contraseña equivocada y volver a la página pedida; y el
widget en un sitio no permitido. Playwright levanta el backend (perfil dev, modelo offline), el panel y la página de
prueba del widget en los puertos 8097, 5183 y 5184 para no chocar con los de desarrollo; si ya están corriendo los
reutiliza. La CI lo corre después de las otras tres partes y guarda el reporte si falla.

## Hoja de ruta

- **Fase 1 (completa):** backend, widget embebible y panel de operadores.
- **Fase 2 (completa):** traspaso a humano en vivo, preguntas sin respuesta, evaluaciones de calidad en CI, equipo
  con invitaciones y cambio de workspace.
- **Fase 3 (completa):** acciones con tool use y confirmación, canal de WhatsApp, medición de consumo por workspace
  y elección del modelo según la pregunta.
- **Fase 4 (completa):** cobro con Stripe, resultados y opiniones de los clientes, widget en el idioma del visitante,
  documentos sugeridos, canales de correo, Messenger e Instagram, integraciones con Shopify y Zendesk, monitoreo con
  Grafana Cloud, despliegue en Oracle Cloud y prueba de carga.
- **Fase 5 (en curso):** memoria y ficha del cliente, temas de cada conversación con filtro, seguimientos con
  plantillas, administración de la plataforma con suspensión de workspaces y demo pública con Claude Haiku.
  Pendiente: calibrar los umbrales de las preguntas difíciles (`DIFFICULT_*`) y del enrutamiento (`ROUTING_*`) con
  semanas de datos reales del reporte de Resultados.
