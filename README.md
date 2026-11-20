<div align="center">

# Plataforma de Atención al Cliente con IA

**Un asistente que responde a tus clientes solo con lo que tu empresa sabe, cita sus fuentes y dice "no sé" en lugar de inventar.**

SaaS multi-tenant con RAG: cada empresa sube su base de conocimiento y atiende por widget web, WhatsApp, correo,
Messenger e Instagram, con traspaso a humano en vivo, acciones sobre sus sistemas y cobro por consumo.

[![CI](https://github.com/IrvinSegura/PlataformaAtencionClientes/actions/workflows/ci.yml/badge.svg)](https://github.com/IrvinSegura/PlataformaAtencionClientes/actions/workflows/ci.yml)
[![Calidad de respuestas](https://github.com/IrvinSegura/PlataformaAtencionClientes/actions/workflows/answer-quality.yml/badge.svg)](https://github.com/IrvinSegura/PlataformaAtencionClientes/actions/workflows/answer-quality.yml)
![Java 25](https://img.shields.io/badge/Java-25-ED8B00?logo=openjdk&logoColor=white)
![Spring Boot 4.1](https://img.shields.io/badge/Spring%20Boot-4.1-6DB33F?logo=springboot&logoColor=white)
![Spring Modulith](https://img.shields.io/badge/Spring%20Modulith-2.1-6DB33F?logo=spring&logoColor=white)
![PostgreSQL + pgvector](https://img.shields.io/badge/PostgreSQL-17%20%2B%20pgvector-4169E1?logo=postgresql&logoColor=white)
![React 19](https://img.shields.io/badge/React-19-61DAFB?logo=react&logoColor=black)
![TypeScript](https://img.shields.io/badge/TypeScript-6-3178C6?logo=typescript&logoColor=white)
![Claude](https://img.shields.io/badge/Claude-Haiku%20%7C%20Sonnet-D97757?logo=anthropic&logoColor=white)

[Capturas](#capturas) ·
[Funcionalidades](#funcionalidades) ·
[Inicio rápido](#inicio-rápido) ·
[Arquitectura](#arquitectura) ·
[Seguridad](#seguridad) ·
[Pruebas](#pruebas) ·
[Documentación](#documentación)

<img src="docs/images/conversation-detail.png" alt="Bandeja de conversaciones del panel: un agente atendió la conversación tras el traspaso a humano" width="900">

</div>

---

## Tabla de contenido

- [En pocas palabras](#en-pocas-palabras)
- [Capturas](#capturas)
- [Funcionalidades](#funcionalidades)
  - [El asistente y su conocimiento](#el-asistente-y-su-conocimiento)
  - [Widget embebible](#widget-embebible)
  - [Atención humana](#atención-humana)
  - [Canales](#canales)
  - [Acciones e integraciones](#acciones-e-integraciones)
  - [Modelo según la pregunta](#modelo-según-la-pregunta)
  - [Mejora continua](#mejora-continua)
  - [Analítica, consumo y cobro](#analítica-consumo-y-cobro)
  - [Cuentas, equipo y administración](#cuentas-equipo-y-administración)
  - [Idiomas](#idiomas)
- [Stack tecnológico](#stack-tecnológico)
- [Inicio rápido](#inicio-rápido)
- [Configuración](#configuración)
- [Arquitectura](#arquitectura)
- [Decisiones técnicas](#decisiones-técnicas)
- [Seguridad](#seguridad)
- [Pruebas](#pruebas)
- [Despliegue y operación](#despliegue-y-operación)
- [Estructura del repositorio](#estructura-del-repositorio)
- [Documentación](#documentación)

## En pocas palabras

| | |
|---|---|
| **Problema** | Los chatbots genéricos inventan respuestas, no saben cuándo pasar a una persona y no pueden hacer nada en los sistemas del negocio. |
| **Solución** | RAG con búsqueda híbrida y citas, respuesta fija de "no sé" sin llamar al modelo cuando no hay fuentes, traspaso a humano en vivo y acciones con confirmación del cliente. |
| **Para quién** | Negocios que atienden por su sitio web, WhatsApp, correo, Messenger o Instagram y quieren automatizar las preguntas repetidas sin perder el control. |
| **Cómo se integra** | Una etiqueta `<script>` de 10 KB gzip en cualquier sitio, o conectando el número, el buzón o la página desde el panel. |
| **Estado** | Fases 1 a 4 completas; Fase 5 en curso (memoria de clientes, temas, seguimientos, administración de la plataforma y demo pública). |

> [!TIP]
> Todo funciona **sin API keys**: los modos `offline` de chat y embeddings permiten desarrollar, probar y hacer demos
> localmente. Con `CHAT_MODEL_PROVIDER=anthropic` y `EMBEDDING_PROVIDER=voyage` responde Claude con embeddings
> multilingües. Las capturas de este README se tomaron en modo offline, por eso las respuestas citan el fragmento tal
> cual en lugar de redactarlo.

## Capturas

Capturas tomadas con el espacio de trabajo de ejemplo **Café Aurora** (`deploy/demo/`), una cafetería ficticia.

<table>
  <tr>
    <td width="50%"><img src="docs/images/widget-answer.png" alt="Widget respondiendo con su fuente"><br><sub><b>Widget en el sitio del cliente.</b> Responde en streaming con la cita a su fuente, 👍/👎 y calificación.</sub></td>
    <td width="50%"><img src="docs/images/widget-no-answer.png" alt="El widget dice que no tiene información"><br><sub><b>Dice "no sé" en lugar de inventar.</b> "¿Venden té?" no está en los documentos: no se llama al modelo y se ofrece una persona.</sub></td>
  </tr>
  <tr>
    <td><img src="docs/images/conversation-detail.png" alt="Conversación tomada por un agente"><br><sub><b>Bandeja en vivo.</b> Canal, idioma, temas, modelo, tokens y latencia por respuesta; el agente toma la conversación y responde.</sub></td>
    <td><img src="docs/images/playground.png" alt="Playground con inspector"><br><sub><b>Playground.</b> Probar antes de publicar: fragmentos recuperados, modelo elegido y por qué, tokens, latencia y costo.</sub></td>
  </tr>
  <tr>
    <td><img src="docs/images/knowledge.png" alt="Base de conocimiento"><br><sub><b>Base de conocimiento.</b> PDF, Word, Excel, HTML, Markdown, texto o páginas de un sitio, con su etapa de indexado en vivo.</sub></td>
    <td><img src="docs/images/gaps.png" alt="Preguntas sin respuesta"><br><sub><b>Preguntas sin respuesta.</b> Cada "no sé" se agrupa para enseñar la respuesta o escribir el documento que falta.</sub></td>
  </tr>
  <tr>
    <td><img src="docs/images/outcomes.png" alt="Resultados"><br><sub><b>Resultados.</b> Conversaciones resueltas por el asistente, traspasos, respuestas útiles y satisfacción por día.</sub></td>
    <td><img src="docs/images/dark-mode.png" alt="Panel en modo oscuro"><br><sub><b>Modo oscuro.</b> Una conversación que espera a una persona, con el motivo del modelo que respondió.</sub></td>
  </tr>
</table>

<details>
<summary><b>Ver más capturas</b> (acciones, integraciones, canales, avisos, seguimientos, consumo, cobro…)</summary>
<br>

| | |
|---|---|
| <img src="docs/images/actions.png" alt="Acciones del asistente"><br><sub><b>Acciones.</b> Webhooks firmados con HMAC; las que cambian algo piden confirmación.</sub> | <img src="docs/images/integrations.png" alt="Integraciones con Shopify y Zendesk"><br><sub><b>Integraciones.</b> Shopify y Zendesk con un token, sin escribir un webhook.</sub> |
| <img src="docs/images/widget-settings.png" alt="Configuración del widget"><br><sub><b>Widget.</b> Nombre, bienvenida, sitios permitidos y vista previa en vivo.</sub> | <img src="docs/images/whatsapp.png" alt="Canal de WhatsApp"><br><sub><b>WhatsApp.</b> Cloud API, con el modelo fijo por número: automático, rápido o preciso.</sub> |
| <img src="docs/images/attention.png" alt="Avisos y escalamiento"><br><sub><b>Avisos y escalamiento.</b> Clientes molestos al equipo y aviso al dueño por WhatsApp.</sub> | <img src="docs/images/follow-ups.png" alt="Seguimientos"><br><sub><b>Seguimientos.</b> Mensajes programados con plantillas y `{nombre}`.</sub> |
| <img src="docs/images/search.png" alt="Probar búsqueda"><br><sub><b>Probar búsqueda.</b> Qué fragmentos encontraría el asistente y por qué.</sub> | <img src="docs/images/usage.png" alt="Consumo del mes"><br><sub><b>Consumo.</b> Respuestas, tokens por modelo, acciones y mensajes frente al plan.</sub> |
| <img src="docs/images/billing.png" alt="Plan y pagos"><br><sub><b>Plan y pagos.</b> Planes y recargas con Stripe.</sub> | <img src="docs/images/login.png" alt="Inicio de sesión"><br><sub><b>Inicio de sesión.</b></sub> |
| <img src="docs/images/demo-page.png" alt="Página de demo pública"><br><sub><b>Demo pública.</b> `/demo` con el widget de Café Aurora.</sub> | |

</details>

## Funcionalidades

### El asistente y su conocimiento

- **Base de conocimiento multiformato.** PDF, Word (`.docx`, `.doc`), Excel (`.xlsx`, `.xls`, una línea por fila con
  sus encabezados: "Producto: Pizza grande · Precio: 189"), HTML (sin menús, scripts ni pies de navegación), Markdown
  y texto, en UTF-8 o en codificaciones antiguas como Windows-1252. El tipo se reconoce por el contenido, no por la
  extensión: un ZIP renombrado a `.pdf` se rechaza.
- **Ingesta asíncrona** (extracción, chunking con solapamiento, embeddings) con la etapa visible en el panel, reproceso
  y borrado. Arrastrar varios archivos con barra de subida.
- **Importar un sitio web.** Se pega la URL de una página y, si se quiere, hasta 30 páginas enlazadas del mismo sitio.
  Solo por `http`/`https` a los puertos 80 y 443, nunca a direcciones privadas, de loopback o de metadatos de la nube
  (también tras cada redirección), con límite de tamaño y de redirecciones. **Actualizar** la vuelve a descargar.
- **Búsqueda híbrida.** Vectorial (pgvector, HNSW coseno) + texto completo (`tsvector`) fusionadas con Reciprocal
  Rank Fusion. **Probar búsqueda** muestra qué fragmentos encontraría el asistente.
- **Respuestas fundamentadas.** Streaming SSE palabra por palabra, citas `[n]` a los documentos y respuesta fija de
  "no sé" **sin llamar al modelo** cuando no hay fuentes relevantes.
- **Playground.** Dueños y administradores prueban el asistente antes de publicarlo: streaming igual que en el widget y
  un inspector con los fragmentos recuperados (y cuáles usó el modelo), el modelo que respondió y por qué, tokens,
  latencia, costo y las acciones que habría llamado, sin ejecutarlas. No crea conversaciones.
- **Respuestas sin costo.** Un mensaje que solo saluda, agradece, se despide o dice "ok" (en cuatro idiomas) recibe una
  respuesta fija, sin buscar ni llamar al modelo, y no cuenta en el plan. Un "ok" a algo que preguntó el asistente sí
  pasa al modelo. Una pregunta repetida sin contexto previo recibe la respuesta ya escrita (0 tokens) mientras no
  cambien los documentos, las acciones ni el nombre del asistente, hasta 7 días; nunca se reutiliza una respuesta que
  usó una acción, una que un cliente marcó como inútil ni una que pudo usar la memoria de un cliente.

### Widget embebible

```html
<script src="https://tu-dominio/widget.js" data-widget-key="wk_..." async></script>
```

- Una etiqueta `<script>` de **31 KB (10 KB gzip)**, sin dependencias, aislado en **Shadow DOM**. La build falla si
  pasa de 32 KB.
- Respuesta en streaming, citas que abren su fuente, conversación que sobrevive a recargas, 👍/👎 por respuesta y
  calificación de 1 a 5.
- Solo funciona en los orígenes que el workspace permite. Opcionales: `data-position="left"` y
  `data-primary-color="#0f766e"`.

### Atención humana

- **Traspaso a humano.** El visitante pulsa **Hablar con una persona**; el asistente deja de responder y sus mensajes
  esperan al equipo. La conversación aparece al instante en **En espera**, un agente la toma, responde y la devuelve al
  asistente o la cierra. Ambos lados se actualizan en vivo por SSE, con avisos en la conversación ("Laura se unió…").
- **Avisos y escalamiento.** Un cliente que pide una persona o se nota molesto (quejas, insultos, mayúsculas, "!!!", en
  cuatro idiomas y sin llamar al modelo) pasa al equipo marcado como **Cliente molesto**, y el dueño recibe un WhatsApp
  con quién es, qué escribió y el enlace a la conversación, con plantilla aprobada para llegar fuera de la ventana de
  24 h. También avisa cuando el consumo del mes llega al 80 % y al 95 % del plan.
- **Clientes y su memoria.** Quien escribe por WhatsApp, correo, Messenger o Instagram se reconoce en cada
  conversación nueva. **Clientes** los busca por número, correo o nombre; la ficha guarda el nombre que el equipo le da
  y sus notas. El asistente recibe lo que el cliente dijo en los últimos 30 días (hasta 10 mensajes, sin saludos ni
  respuestas fallidas, recortado a 2400 caracteres) para no volver a preguntar lo que ya contó. Las notas del equipo
  nunca llegan al modelo y la ficha permite **no recordar** a un cliente. Los visitantes del widget son anónimos.
- **Temas de cada conversación.** Se etiquetan solas, sin llamar al modelo y en cuatro idiomas: estado del pedido,
  envíos, devoluciones, pagos, precios, disponibilidad, horarios y ubicación, cuenta o queja. La bandeja se filtra por
  tema y el equipo puede corregirlos.
- **Seguimientos.** Desde una conversación de WhatsApp, correo, Messenger o Instagram el equipo programa un mensaje
  ("Mañana", "En una semana" o una fecha), escrito a mano o desde una **plantilla** donde `{nombre}` se cambia por el
  nombre del cliente. Respeta la ventana de 24 h de Meta (WhatsApp envía la plantilla aprobada; Messenger e Instagram no
  envían) y muestra los no enviados con el motivo.

### Canales

| Canal | Cómo funciona |
|---|---|
| **Widget web** | SSE sobre `fetch`, orígenes permitidos por workspace, token secreto por conversación. |
| **WhatsApp** | Número de la Cloud API de WhatsApp Business. Mensajes firmados por Meta, botones de confirmación para acciones, traspaso escribiendo "quiero hablar con una persona". Cada número puede fijar el modelo. |
| **Correo** | Buzón por IMAP y SMTP con TLS (en Gmail, con contraseña de aplicación). Cada minuto lee los correos nuevos, quita el hilo citado y la firma y responde en el mismo hilo (`Re:`, `In-Reply-To`, `References`). Las acciones se confirman respondiendo **CONFIRMAR** o **CANCELAR**. Ignora respuestas automáticas, rebotes, listas de correo y correos que no pasan DMARC. |
| **Messenger e Instagram** | Página de Facebook o cuenta profesional con el token de la página y el secreto de la app. Webhook firmado, respuestas rápidas como botones, nombre desde el perfil y textos largos partidos (2000 caracteres en Messenger, 1000 bytes en Instagram). Respeta la ventana de 24 h. |

### Acciones e integraciones

- **Acciones propias.** Cada empresa describe acciones como "consultar un pedido" o "cambiar la dirección": nombre,
  cuándo usarlas, los datos que piden (con formato opcional) y la URL de su webhook. El asistente decide cuándo usarlas
  (tool use) y pide al cliente los datos que falten.
- **Confirmación explícita.** Las consultas se ejecutan al momento; las que cambian algo esperan a que el cliente pulse
  **Confirmar** (15 min), en el widget, WhatsApp, Messenger, Instagram o respondiendo el correo.
- **Firmadas y registradas.** Cada llamada lleva `X-SupportChat-Signature` (HMAC-SHA256) y queda en la conversación.
- **Shopify y Zendesk** listos con un token. Shopify consulta el estado de un pedido (pago, envío, total y seguimiento,
  nunca la dirección) y lo cancela si el cliente confirma, solo para quien da el correo del pedido. Zendesk abre un
  ticket a nombre del cliente tras confirmar.

### Modelo según la pregunta

```mermaid
flowchart LR
    Q[Pregunta] --> S{¿Saludo, gracias u ok?}
    S -- sí --> F[Respuesta fija · 0 tokens]
    S -- no --> R[Búsqueda híbrida]
    R --> N{¿Hay fuentes relevantes?}
    N -- no --> NS[No sé fijo · sin modelo]
    N -- sí --> C{¿Razonar, comparar, varias preguntas,<br>varios documentos o pregunta difícil?}
    C -- no --> H[Claude Haiku · rápido]
    C -- sí --> SO[Claude Sonnet · preciso]
    H -- "dijo no sé con fuentes" --> SO
```

- Las preguntas directas van a un modelo económico (Claude Haiku). Las que piden razonar, comparar o recomendar (con
  señales en cuatro idiomas), las hipotéticas, las que traen varias preguntas, las largas y las que combinan varios
  documentos van a uno más capaz (Claude Sonnet). Un "¿por qué?" sobre el caso del propio cliente se queda en el
  económico.
- Si el económico dice "no sé" teniendo fuentes, se reintenta **una vez** con el capaz.
- La elección usa reglas baratas, no otra llamada al modelo. El panel muestra qué modelo respondió y por qué, y cada
  número de WhatsApp puede fijar **Rápido** o **Preciso** en lugar de **Automático**.

### Mejora continua

- **Preguntas sin respuesta.** Cada "no sé" se agrupa por pregunta normalizada, con cuántas veces se hizo y la última
  conversación. Se resuelven al agregar el documento que las cubre (se reabren si alguien vuelve a preguntar) o se
  descartan.
- **Documentos sugeridos.** Las preguntas abiertas del mismo tema (por similitud de embeddings: "¿Hacen envíos a
  Colombia?" y "¿Envían a Colombia?") se juntan en un documento por escribir, el tema más preguntado primero. Se redacta
  en Markdown sobre un borrador y, al subirlo, sus preguntas quedan resueltas.
- **Preguntas difíciles.** Se guarda cada pregunta con la que el asistente batalló (el rápido no supo, 👎, "no
  entendí", o la repitió). Mientras siga abierta, esa pregunta y las parecidas van directo al modelo preciso. El equipo
  la enseña con **Enseñar respuesta**, que llega con un borrador escrito por el modelo preciso; si nadie la enseña, se
  cierra sola tras 3 buenas respuestas seguidas o 30 días tranquila. A las 5 repeticiones avisa al dueño.
- **Evaluación de calidad.** 12 casos con base de conocimiento propia miden si el asistente responde o dice "no sé"
  cuando corresponde, si incluye los datos esperados y si cita la fuente correcta. Corre en cada CI (offline) y cada
  semana contra Claude.

### Analítica, consumo y cobro

- **Resultados.** El cliente dice si cada respuesta le ayudó (👍/👎) y califica la conversación del 1 al 5. Se mide
  cuántas conversaciones resolvió el asistente sin ayuda, cuántas pasaron a una persona, las respuestas útiles, la
  calificación promedio y la satisfacción, por día; y las respuestas por modelo y motivo con su tasa de 👎.
- **Consumo.** Por día: respuestas (por canal), tokens (por modelo), acciones y mensajes de WhatsApp, frente a lo que
  incluye el plan.
- **Planes y cobro con Stripe.** Gratis, Inicial y Profesional (configurables). Checkout, cambio y cancelación al final
  del periodo, portal de tarjeta y facturas, excedentes reportados a los medidores de Stripe y **recargas** de pago único
  (p. ej. 500 respuestas por 10 USD). Al agotar un plan sin cobro extra, el asistente pasa las conversaciones al equipo.
  Si un pago falla, el plan sigue 7 días mientras Stripe reintenta. Al pasar del 80 % se recomienda el plan más barato
  que cubre el consumo.

### Cuentas, equipo y administración

- **Workspaces y roles.** El registro crea el workspace y su dueño con los **términos y condiciones** aceptados (se
  guarda cada versión y su fecha; si cambia, el panel pide aceptarla). Roles `OWNER`, `ADMIN` y `AGENT`; una persona
  puede pertenecer a varios workspaces y cambiar entre ellos sin volver a iniciar sesión.
- **Invitaciones** por email con enlace de un solo uso (7 días, solo se guarda su hash). El dueño gestiona a todos y
  cambia roles; un administrador solo invita y quita agentes. Lo que cada rol no puede hacer no se muestra.
- **Administración de la plataforma.** Quienes la operan (configurados por id de usuario) ven todos los workspaces con
  su plan y actividad, sin acceso a sus conversaciones ni a su base de conocimiento, y pueden **suspender** uno con un
  motivo y reactivarlo.
- **Demo pública.** `/demo` muestra el widget de Café Aurora; `deploy/demo/seed-demo.sh` la prepara con la propia API.
  Responde siempre con Claude Haiku, con tope diario y límite por visitante.

### Idiomas

El asistente responde en el idioma del cliente. Lo que la plataforma escribe por su cuenta (el "no sé", errores,
respuestas de acciones, avisos y textos del widget) sale en **español, inglés, portugués o francés**: primero el del
navegador (`Accept-Language`) y después el de los mensajes, detectado sin llamar al modelo. Con Voyage (multilingüe),
una pregunta en inglés encuentra documentos en español.

## Stack tecnológico

| Capa | Tecnologías |
|---|---|
| Backend | Java 25 (hilos virtuales), Spring Boot 4.1, Spring Modulith 2.1, `JdbcClient`, Flyway, Spring Security (JWT RS256) |
| Datos | PostgreSQL 17 + pgvector (HNSW), `tsvector`, Row-Level Security, `LISTEN/NOTIFY`; Redis opcional para rate limiting |
| IA | Claude (Haiku y Sonnet, streaming y tool use), Voyage AI (embeddings); modos offline sin API keys |
| Panel | React 19, React Router, TanStack Query, Vite, TypeScript, tipos generados desde OpenAPI |
| Widget | TypeScript sin framework, Shadow DOM, Vite, presupuesto de 32 KB |
| Pagos y canales | Stripe (checkout, portal, medidores, webhooks), WhatsApp Cloud API, Messenger/Instagram Graph API, IMAP/SMTP |
| Pruebas | JUnit, Testcontainers, ArchUnit, Spring Modulith tests, GreenMail, Vitest, Testing Library, Playwright, k6 |
| Operación | Docker Compose, Caddy (TLS automático), GitHub Actions, OpenTelemetry + Grafana Cloud, Oracle Cloud (arm64) |

## Inicio rápido

**Requisitos:** JDK 25, Docker y Node 24.

```bash
cp .env.example .env
```

Arranca el backend (perfil `dev`: genera llaves JWT al iniciar y activa Swagger). Con Docker abierto, levanta solo
PostgreSQL con pgvector (puerto 5450, solo localhost) y lo deja corriendo al detenerse:

```bash
cd backend && ./mvnw spring-boot:run
```

Arranca los frontends, cada uno en su terminal:

```bash
cd panel && npm install && npm run dev
```

```bash
cd widget && npm install && npm run dev
```

| Servicio | URL |
|---|---|
| Panel de operadores | http://localhost:5173 |
| Página de prueba del widget | http://localhost:5174 |
| API y Swagger | http://localhost:8080/swagger-ui.html |

Crea tu cuenta en el panel, sube documentos y, en **Widget**, agrega `http://localhost:5174` a los sitios permitidos y
pulsa **Abrir página de prueba**: simula el sitio de un cliente con el widget instalado.

<details>
<summary><b>Otras formas de ejecutarlo</b></summary>

- **IntelliJ:** ▶ en `SupportChatApplication` (con la raíz del repositorio o `backend/` como directorio de trabajo).
- **Todo en contenedores:** `docker compose --profile app up -d --build`.
- **Otra base de datos:** `DOCKER_COMPOSE_ENABLED=false` y `DB_URL`.
- **Datos de ejemplo:** `DEMO_URL=http://localhost:8080 DEMO_EMAIL=... DEMO_PASSWORD=... deploy/demo/seed-demo.sh`
  crea el workspace de Café Aurora con su base de conocimiento (requiere `curl` y `jq`).

</details>

> [!NOTE]
> En Windows, si el backend falla al arrancar con `Unable to establish loopback connection`, la carpeta temporal está
> llena de archivos `socket_*`. Bórralos o arranca con
> `./mvnw spring-boot:run "-Dspring-boot.run.jvmArguments=-Djdk.net.unixdomain.tmpdir=target"`.

## Configuración

Para respuestas reales con Claude y embeddings de Voyage, define en `.env`:

```properties
CHAT_MODEL_PROVIDER=anthropic
ANTHROPIC_API_KEY=...
EMBEDDING_PROVIDER=voyage
VOYAGE_API_KEY=...
```

> [!IMPORTANT]
> Si cambias de proveedor de embeddings, reprocesa los documentos: los vectores de proveedores distintos no son
> comparables.

<details>
<summary><b>Variables de entorno principales</b></summary>

| Variable | Por defecto | Para qué sirve |
|---|---|---|
| `CHAT_MODEL_PROVIDER` | `offline` | `anthropic` para usar Claude |
| `EMBEDDING_PROVIDER` | `hashing` | `voyage` para embeddings multilingües |
| `ANTHROPIC_SIMPLE_MODEL` | `claude-haiku-5-5` | Modelo para las preguntas simples |
| `ANTHROPIC_MODEL` | `claude-sonnet-5-5` | Modelo para las preguntas complejas |
| `MODEL_ROUTING_ENABLED` | `true` | `false` manda todo al modelo complejo |
| `ROUTING_SEVERAL_DOCUMENTS` | `3` | Cuántos documentos cuentan como "varios" |
| `ROUTING_DOCUMENT_CLOSENESS` | `0.25` | Qué tan cerca del mejor debe estar un documento para contar |
| `ROUTING_SAME_TEXT_OVERLAP` | `0.6` | Cuánto texto deben compartir dos páginas para contar como una |
| `ANSWER_MIN_SIMILARITY` | por modelo (0.35 Voyage, 0.2 hashing) | Umbral para considerar relevante una fuente |
| `ANSWER_REUSE_ENABLED` | `true` | `false` hace que el modelo reescriba cada respuesta repetida |
| `DIFFICULT_ANSWERED_WELL_TO_CLOSE` | `3` | Respuestas buenas seguidas para cerrar una pregunta difícil |
| `DIFFICULT_QUIET_PERIOD` | `30d` | Tiempo sin señales para cerrarla sola |
| `DIFFICULT_ALERT_AFTER` | `5` | Repeticiones para avisar al dueño por WhatsApp |
| `DIFFICULT_SAME_QUESTION_SIMILARITY` | 0.8 Voyage, 0.9 hashing | Cuándo dos preguntas significan lo mismo |
| `CUSTOMER_MEMORY_ENABLED` | `true` | Memoria de clientes que vuelven |
| `CUSTOMER_MEMORY_LOOK_BACK` | `30d` | Hasta cuándo atrás se recuerda |
| `PLATFORM_ADMIN_USER_IDS` | vacío | Ids de usuario (de `GET /api/v1/me`) que administran la plataforma |
| `SECRETS_ENCRYPTION_KEY` | clave de desarrollo | AES-256-GCM para tokens y contraseñas guardados |
| `RATE_LIMIT_STORE` | memoria | `redis` con varias réplicas |
| `STRIPE_SECRET_KEY`, `STRIPE_WEBHOOK_SECRET` | vacío | Cobro con Stripe (`scripts/stripe-setup.sh` crea productos y precios) |

Los umbrales `ROUTING_*` y `DIFFICULT_*` se calibran con unas semanas de datos en **Resultados**. En modo offline las
acciones también funcionan: se llama a una acción cuando la pregunta comparte palabras con su descripción y contiene
los datos que pide.

</details>

## Arquitectura

Monolito modular (**Spring Modulith**) con **arquitectura limpia** dentro de cada módulo. Los módulos solo se hablan
por su API pública y por eventos; las reglas se verifican en cada build (`ModularityTests`, `CleanArchitectureTests`).

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

### Flujo de una pregunta

```mermaid
sequenceDiagram
    autonumber
    participant V as Visitante (widget)
    participant API as conversations
    participant K as knowledge
    participant DB as PostgreSQL (RLS)
    participant M as Claude
    V->>API: POST /messages (token de la conversación)
    API->>API: Autentica, valida, aplica límites y registra
    API->>K: Búsqueda híbrida (vector + texto, RRF)
    K->>DB: SET app.workspace_id · consulta
    DB-->>K: Fragmentos del workspace
    alt Sin fuentes relevantes
        API-->>V: "No sé" fijo (sin modelo) · laguna registrada
    else Con fuentes
        API->>M: Prompt con fuentes delimitadas (modelo elegido por reglas)
        M-->>API: Streaming de tokens (y tool use si aplica)
        API-->>V: SSE delta… delta… done (citas, modelo, tokens)
        API->>DB: Guarda la respuesta y suma el consumo en la misma transacción
    end
```

### Módulos

| Módulo | Responsabilidad |
|---|---|
| `identity` | Registro, inicio de sesión, tokens, invitaciones, términos |
| `workspaces` | Workspaces, miembros, configuración del widget, suspensión |
| `knowledge` | Ingesta, extracción, chunking, embeddings, búsqueda, lagunas y documentos sugeridos |
| `conversations` | Chat en streaming, enrutamiento de modelo, traspaso, eventos en vivo, opiniones, clientes y temas |
| `actions` | Acciones del asistente, webhooks firmados, Shopify y Zendesk |
| `channels` | WhatsApp, correo, Messenger e Instagram |
| `followups` | Seguimientos programados y plantillas |
| `billing` | Consumo, planes, Stripe y recargas |
| `platform` | Administración de la plataforma |
| `shared` | Tenancy, errores RFC 9457, rate limiting, correlation id y textos por idioma |

Cada módulo se organiza así:

```
<módulo>/                 API pública del módulo (interfaces y records que otros módulos pueden usar)
  domain/model/           reglas de negocio puras, sin Spring ni base de datos
  domain/port/            interfaces que el dominio necesita (repositorios, modelos de IA)
  application/            casos de uso y transacciones
  infrastructure/         adaptadores: JDBC, clientes HTTP de IA, configuración
  web/                    controladores REST
```

Los textos que la plataforma escribe por sí misma (respuestas fijas, avisos, botones, alertas) viven en
`backend/src/main/resources/texts/<área>/<idioma>.properties`.

## Decisiones técnicas

<details open>
<summary><b>Aislamiento de tenants en la base de datos, no solo en el código</b></summary>

Las tablas con datos de clientes tienen Row-Level Security (`ENABLE` + `FORCE`). Cada transacción fija
`app.workspace_id`. Si alguien olvida el filtro, o no hay workspace ligado, la base devuelve **cero filas**: falla
cerrado. La aplicación se conecta con un rol que no es dueño de ninguna tabla; Flyway migra con el rol dueño.
</details>

<details>
<summary><b>JdbcClient en lugar de JPA</b></summary>

SQL explícito para pgvector, `tsvector`, `FOR UPDATE` y CTEs de búsqueda.
</details>

<details>
<summary><b>Ingesta con outbox</b></summary>

Con los eventos de Spring Modulith, si el proceso muere a la mitad el evento se reintenta al arrancar. Un documento
atascado en `PROCESSING` se recupera tras 10 minutos.
</details>

<details>
<summary><b>Hilos virtuales y validar antes de transmitir</b></summary>

Cada respuesta en streaming ocupa un hilo bloqueado mientras el modelo escribe, lo que es barato con hilos virtuales.
Ninguna transacción queda abierta durante la generación. La pregunta se autentica, valida y registra antes del primer
byte del stream, así los errores son respuestas HTTP normales; una vez iniciado, cualquier fallo se convierte en un
mensaje amable dentro del stream.
</details>

<details>
<summary><b>Widget sin framework</b></summary>

Se carga en cada página de cada cliente, así que es TypeScript puro con un presupuesto de 32 KB que la build hace
cumplir. Lee el SSE con `fetch` (EventSource no permite POST ni headers). Todo el texto del servidor se inserta como
texto, nunca como HTML. La conversación se crea con la primera pregunta, no al cargar la página. Si el token del
visitante expiró, abre una conversación nueva y reintenta una sola vez.
</details>

<details>
<summary><b>SSE en lugar de WebSockets, y varias instancias con <code>LISTEN/NOTIFY</code></b></summary>

El servidor solo empuja (cambios de estado y mensajes) y el cliente escribe con POST normales, así que SSE basta, pasa
por cualquier proxy y reutiliza la autenticación. Cada conexión se cierra a los 15 (panel) o 30 minutos (widget) para
que el cliente vuelva a autenticarse.

Cada cambio hace `pg_notify` dentro de su transacción, así que PostgreSQL lo entrega a todas las réplicas solo si
confirma y en orden de commit. El aviso lleva solo ids; la réplica que tiene streams abiertos lee el estado actual con
RLS y lo empuja. Cada réplica escucha con una conexión propia fuera del pool; si se cae, reconecta y cierra sus streams
para que los clientes recarguen lo perdido. No hace falta Redis: solo entra para el rate limiting con varias réplicas,
y si cae las peticiones pasan sin limitar en vez de fallar.
</details>

<details>
<summary><b>Acciones como webhooks, no como código</b></summary>

El modelo solo propone la llamada (nombre y datos). La plataforma valida los datos contra la definición (tipos,
obligatorios, formato) antes de enviar nada, y el resultado vuelve al modelo como datos entre etiquetas, nunca como
instrucciones. Un cambio propuesto se guarda como ejecución pendiente y solo corre cuando el cliente lo confirma: una
inyección de prompt no puede ejecutar un cambio por su cuenta, y el doble clic o dos pestañas no lo ejecutan dos veces
(la ejecución se reclama con un `UPDATE` condicional). La llamada HTTP ocurre fuera de cualquier transacción.

Shopify y Zendesk son acciones predefinidas (`shopify_*`, `zendesk_*`, nombres reservados) que pasan por el mismo
`ActionRunner`; solo cambia quién ejecuta la llamada: un `IntegrationGateway` habla con la API del proveedor.
</details>

<details>
<summary><b>El canal no cambia la conversación</b></summary>

WhatsApp, Messenger, Instagram y el correo son adaptadores del mismo caso de uso. `channels` traduce el webhook de Meta
a un mensaje entrante y las respuestas (`ConversationReply`) a mensajes de la Graph API. Las entregas repetidas se
descartan por id de mensaje. Si un número llega al límite de preguntas, la conversación pasa al equipo en lugar de
abrir otra, así el gasto sigue acotado.
</details>

<details>
<summary><b>Medición en la misma transacción y traspaso sin costo</b></summary>

El consumo se suma en contadores diarios dentro de la transacción que guarda la respuesta: no se cobra lo que no quedó
guardado. El precio se aplica al leer, con la lista vigente. Solo cuentan para el límite por conversación las
preguntas que responde el asistente, que son las que cuestan tokens.
</details>

<details>
<summary><b>Sesión del panel sin carreras</b></summary>

El access token vive solo en memoria y se renueva con la cookie al recibir un 401. Como el backend revoca toda la
sesión si un refresh token se usa dos veces, las renovaciones se comparten dentro de la pestaña y se ponen en fila
entre pestañas con la Web Locks API.
</details>

## Seguridad

| Amenaza | Defensa |
|---|---|
| Fuga entre empresas | RLS forzado + rol sin privilegios de dueño + llaves foráneas que incluyen el workspace (una fila no puede apuntar a la conversación de otra empresa) + pruebas que lo verifican contra PostgreSQL real |
| Prompt injection en documentos | Las fuentes van en etiquetas delimitadas y se neutralizan las que intenten cerrarlas. El prompt de sistema declara las fuentes como datos, no instrucciones. El nombre del workspace se sanea |
| Respuestas inventadas | Umbral de similitud propio de cada modelo de embeddings y selección de fuentes. Sin fuentes no se llama al modelo. El marcador `NO_ANSWER` nunca llega al visitante |
| Robo de sesión del panel | Access token JWT RS256 de 15 min. Refresh token opaco en cookie `HttpOnly` + `SameSite=Strict` + ruta limitada, rotado en cada uso, con detección de reutilización que revoca toda la familia |
| Invitaciones | El token viaja en el fragmento de la URL (`#`), que no llega a servidores, logs ni `Referer`, y el panel lo borra de la barra de direcciones. Se guarda hasheado y se bloquea la fila al aceptarla. Rate limiting por IP |
| CSRF | Endpoints con cookie exigen el header `X-CSRF-Protection` |
| Fuerza bruta | Bloqueo tras 5 intentos fallidos por 15 min. Tiempo de respuesta constante para emails inexistentes. Rate limiting por IP, usuario y widget |
| Enumeración de cuentas | El registro tiene su propio límite: 10 intentos cada 10 min por IP |
| Abuso del widget | CORS con los orígenes de cada workspace. Token secreto por conversación (solo se guarda su hash SHA-256, comparación en tiempo constante). Límite de preguntas por conversación |
| Contraseñas | BCrypt costo 12. Mínimo 12 caracteres, máximo 72 bytes |
| Acciones peligrosas | Los cambios requieren confirmación del cliente. Datos validados contra la definición. Webhooks `https` públicos: se rechazan IPs privadas, de loopback y link-local (SSRF). Timeout de 10 s y solo se leen 16 KB de respuesta |
| Webhooks falsificados | Llamadas salientes con `X-SupportChat-Signature: t=<segundos>,v1=<HMAC-SHA256 de "t.cuerpo">` y secreto por workspace. Entrantes de Meta solo con `X-Hub-Signature-256` válida |
| Integraciones | URL fijas de Shopify (`<tienda>.myshopify.com`) y Zendesk (`<subdominio>.zendesk.com`), sin SSRF. Un pedido solo se muestra o cancela si el correo coincide, y no se cancela uno ya enviado |
| Correo | Servidores IMAP/SMTP públicos y siempre con TLS verificado. Se ignoran correos que fallan DMARC, automáticos y del propio buzón; las respuestas salen con `Auto-Submitted: auto-replied` |
| Secretos guardados | Tokens de canales e integraciones, secreto de firma y contraseña del buzón cifrados con AES-256-GCM. Son de solo escritura: nunca vuelven al panel |
| Exposición | CSP `default-src 'none'`, `nosniff`, sin stack traces en errores. Actuator en puerto privado en producción. Contenedor sin root |

## Pruebas

| Parte | Comando | Qué cubre |
|---|---|---|
| Backend | `cd backend && ./mvnw verify` | **575 pruebas**: dominio, reglas de arquitectura y módulos, integración contra PostgreSQL real con pgvector (Testcontainers, con el rol restringido para ejercitar RLS), Claude contra un servidor SSE local, Stripe y Graph API simulados, correo contra GreenMail |
| Widget | `cd widget && npm test` | **33 pruebas**: lector SSE, citas, streaming, reintento con token expirado, traspaso en vivo, acciones, opiniones, idiomas y que el texto del servidor nunca se interprete como HTML |
| Panel | `cd panel && npm run lint && npm test` | **96 pruebas**: renovación de sesión, permisos por rol, bandeja en vivo, acciones, canales, cobro, resultados, Playground, base de conocimiento y más |
| De punta a punta | `cd e2e && npm ci && npx playwright install chromium && npm test` | **5 flujos con Playwright**: registro, subir documento, respuesta citada en el widget, "no sé" que llega a Preguntas sin respuesta, traspaso a humano en vivo y acceso |
| Carga | `loadtest/` | k6, también contra una copia de producción (dos réplicas, Caddy, Redis) |

<details>
<summary><b>Detalle de lo que cubren las pruebas del backend</b></summary>

Flujo de autenticación con rotación y reutilización de tokens, aislamiento entre workspaces, ingesta y búsqueda, chat
con streaming, citas y "no sé", preguntas difíciles (👎, "no entendí", repetir la pregunta, enseñar la respuesta y el
reporte por modelo), traspaso a humano (permisos, preguntas reenviadas, límites), invitaciones y equipo, preguntas sin
respuesta, eventos en vivo entre instancias (incluida la caída y reconexión de la conexión que escucha), acciones contra
un servidor HTTP local (firma, confirmación, cancelación, doble confirmación, otro visitante), Shopify y Zendesk
simulados, WhatsApp, Messenger e Instagram contra una Graph API simulada (verificación, entregas repetidas, firmas
inválidas), correo contra GreenMail real (IMAP, SMTP en el hilo, correos automáticos, CONFIRMAR por correo, contraseña
equivocada) y su lectura (hilos citados en varios idiomas, Outlook, firmas, HTML, DMARC), consumo y métricas de negocio,
trazas OTLP, enrutamiento de modelos, cobro contra un Stripe simulado (checkout, cambios, eventos repetidos o
desordenados, firmas falsas, periodo de gracia, recargas, medidores, portal y facturas), opiniones, resultados, agrupación
de preguntas por tema, detección de idioma, respuestas fijas y reutilizadas, extracción de Word, Excel, HTML y
Windows-1252, descarga de páginas (redirecciones, bucles, tamaño, direcciones privadas), Playground, clientes molestos,
avisos al dueño, términos y privacidad, memoria de clientes, temas, seguimientos, administración de la plataforma y la
demo pública.
</details>

La evaluación de calidad (`AnswerQualityEvaluationTest`) deja su reporte en `backend/target/eval/answer-quality.md` y
la CI lo publica en el resumen de cada ejecución. En modo offline el umbral es 0.9 por métrica y hoy acierta los 12
casos; el workflow semanal `answer-quality.yml` la corre contra Claude y necesita el secreto `ANTHROPIC_API_KEY` (y
`VOYAGE_API_KEY` para embeddings reales).

La CI (`ci.yml`) corre backend, widget y panel, falla si `widget.js` supera 32 KB, corre los flujos de punta a punta
después y guarda el reporte de Playwright si fallan.

## Despliegue y operación

- **Infraestructura:** una VM gratuita de Oracle Cloud (arm64) con Docker Compose y Caddy con TLS automático
  (DuckDNS como dominio gratuito).
- **Despliegue continuo:** GitHub Actions por SSH, con prueba de humo y vuelta atrás a cualquier versión.
- **Respaldos:** nocturnos y cifrados a Object Storage, con prueba de restauración.
- **Monitoreo:** métricas de negocio (respuestas, tokens, traspasos, opiniones, acciones, entregas por canal) y trazas
  OpenTelemetry hacia Grafana Cloud gratis, con un tablero y 12 alertas listas.

Todo el detalle está en [docs/operations.md](docs/operations.md).

## Estructura del repositorio

```
backend/   API (Java 25, Spring Boot, Spring Modulith)
widget/    widget.js para los sitios de los clientes (TypeScript sin framework, Vite)
panel/     panel de operadores (React, React Router, TanStack Query, Vite); src/api/ tiene un archivo por módulo
deploy/    producción en contenedores: Caddy, init de PostgreSQL, monitoreo, systemd, scripts y la demo pública
docs/      integraciones, API, operación, contrato OpenAPI y capturas
e2e/       pruebas de punta a punta con Playwright
loadtest/  prueba de carga con k6, también contra una copia de producción
scripts/   configuración única desde tu máquina (Stripe)
```

## Documentación

| Documento | Contenido |
|---|---|
| [Integraciones y canales](docs/integrations.md) | Acciones propias, Shopify y Zendesk, WhatsApp, correo, Messenger e Instagram, y avisos al dueño |
| [API: flujo de ejemplo y permisos](docs/api.md) | Un recorrido con `curl` y quién puede llamar cada endpoint |
| [Operación](docs/operations.md) | Producción, despliegue en Oracle Cloud, monitoreo y prueba de carga |
| [Contrato OpenAPI](docs/openapi.json) | La API de operadores tal como la sirve el backend |

El contrato OpenAPI se mantiene sincronizado: `OpenApiContractIntegrationTest` falla si la API cambia sin
actualizarlo (`./mvnw test -Dtest=OpenApiContractIntegrationTest -Dopenapi.update=true`); el panel genera
`src/api/openapi.ts` con `npm run api:types` y `src/api/contract.ts` hace fallar a `tsc` si sus tipos ya no coinciden
con la API, también si un campo que puede llegar nulo es uno que el panel da por presente. Los campos de respuesta que
pueden ser nulos se marcan `@Nullable` (JSpecify).

---

<div align="center">
<sub>Hecho por <a href="https://github.com/IrvinSegura">Irvin Segura</a>.</sub>
</div>
