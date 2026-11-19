# API: flujo de ejemplo y permisos

Un recorrido con `curl` por la API del operador y quién puede llamar cada endpoint. Swagger está en `/swagger-ui.html` con el perfil `dev`.

## Flujo de ejemplo

```bash
# 1. Registro: devuelve el access token y deja el refresh token en una cookie HttpOnly
curl -s -H 'Content-Type: application/json' \
  -d '{"email":"demo@example.com","password":"correct horse battery staple","displayName":"Demo","workspaceName":"Mi Tienda","acceptTerms":true}' \
  http://localhost:8080/api/v1/auth/register

# 2. Permitir el widget en tu sitio (devuelve widgetKey)
curl -s -X PUT -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"botName":"Ana","welcomeMessage":"¡Hola!","allowedOrigins":["https://mitienda.com"]}' \
  http://localhost:8080/api/v1/workspace/widget-settings

# 3. Subir conocimiento
curl -s -H "Authorization: Bearer $TOKEN" -F file=@faq.md http://localhost:8080/api/v1/knowledge/documents
# o importar una página y hasta 30 enlazadas del mismo sitio
curl -s -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \n  -d '{"url":"https://mitienda.com/envios","linkedPages":5}' http://localhost:8080/api/v1/knowledge/documents/web

# 3b. Probarlo en el Playground: eventos delta y un done con el inspector (fuentes, modelo, costo, acciones)
#     "model" es AUTOMATIC, FAST o PRECISE; GET /api/v1/assistant/models lista qué modelo hay detrás de cada uno
curl -N -X POST -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \n  -d '{"question":"¿Cuánto tarda el envío?","history":[],"model":"PRECISE"}' \n  http://localhost:8080/api/v1/playground/answers

# 4. Como visitante: iniciar conversación y preguntar (la respuesta llega como eventos SSE delta/done,
#    o un evento forwarded si la atiende una persona)
curl -s -X POST -H 'Origin: https://mitienda.com' http://localhost:8080/api/v1/widget/$WIDGET_KEY/conversations
curl -N -X POST -H 'Origin: https://mitienda.com' -H "X-Conversation-Token: $VISITOR_TOKEN" \
  -H 'Content-Type: application/json' -d '{"question":"¿Cuánto tarda el envío?"}' \
  http://localhost:8080/api/v1/widget/$WIDGET_KEY/conversations/$CONVERSATION_ID/messages

# 5. Pedir una persona y escuchar los cambios en vivo (eventos state y message)
curl -s -X POST -H 'Origin: https://mitienda.com' -H "X-Conversation-Token: $VISITOR_TOKEN" \
  http://localhost:8080/api/v1/widget/$WIDGET_KEY/conversations/$CONVERSATION_ID/handoff
curl -N -H 'Origin: https://mitienda.com' -H "X-Conversation-Token: $VISITOR_TOKEN" \
  http://localhost:8080/api/v1/widget/$WIDGET_KEY/conversations/$CONVERSATION_ID/events

# 6. Como agente: tomar la conversación y responder
curl -s -X POST -H "Authorization: Bearer $TOKEN" \
  http://localhost:8080/api/v1/conversations/$CONVERSATION_ID/takeover
curl -s -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' -d '{"text":"Hola, te ayudo yo."}' \
  http://localhost:8080/api/v1/conversations/$CONVERSATION_ID/messages
```

Endpoints de la Fase 5:

| Endpoint | Quién |
|---|---|
| `GET /api/v1/customers?query=` | Todo el equipo |
| `GET`, `PUT /api/v1/conversations/{id}/customer` (`{"displayName","notes","remember"}`; `remember` vacío conserva el guardado) | Todo el equipo |
| `GET /api/v1/conversations?intent=SHIPPING`, `PUT /api/v1/conversations/{id}/intents` (`{"intents":["SHIPPING","RETURNS"]}`) | Todo el equipo |
| `GET /api/v1/follow-up-templates` | Todo el equipo |
| `POST /api/v1/follow-up-templates`, `PUT`/`DELETE .../follow-up-templates/{id}` (`{"name","body","whatsappTemplate","whatsappLanguage","whatsappNameParameter"}`) | Dueño y administradores |
| `GET /api/v1/follow-ups?status=SCHEDULED\|SENT\|FAILED\|CANCELLED`, `GET`/`POST /api/v1/conversations/{id}/follow-ups` (`{"templateId"}` o `{"text"}`, y `"dueAt"`), `POST /api/v1/follow-ups/{id}/cancellation` | Todo el equipo |
| `GET /api/v1/platform/me` (`{"platformAdmin":true}`) | Cualquier usuario autenticado |
| `GET /api/v1/platform/workspaces?query=`, `GET .../workspaces/{id}`, `POST`/`DELETE .../workspaces/{id}/suspension` (`{"reason":"..."}`) | Quienes administran la plataforma |

Endpoints de la Fase 4:

| Endpoint | Quién |
|---|---|
| `GET /api/v1/billing`, `GET /api/v1/billing/invoices` | Dueño y administradores |
| `POST /api/v1/billing/plan` (`{"plan":"STARTER"}`: devuelve `checkoutUrl` en la primera compra), `POST /api/v1/billing/top-ups` (`{"topUp":"ANSWERS_500"}`: devuelve `checkoutUrl`; solo planes que se detienen al límite de esa métrica), `POST /api/v1/billing/portal` | Dueño |
| `POST /api/v1/billing/stripe/webhook` | Stripe (firma `Stripe-Signature`) |
| `GET /api/v1/insights/outcomes?from=2026-11-01&to=2026-11-30` (días UTC, hasta 366; sin fechas, los últimos 30) | Dueño y administradores |
| `GET /api/v1/knowledge-gaps/suggestions` | Todo el equipo |
| `GET /api/v1/widget/{key}/config` (con `Accept-Language` de otro idioma, incluye `texts` para el widget) | Público |
| `PUT /api/v1/widget/{key}/conversations/{id}/messages/{messageId}/feedback` (`{"helpful":true}`), `PUT .../conversations/{id}/rating` (`{"score":5,"comment":"..."}`) | Visitante con su token |
| `GET`, `PUT`, `DELETE /api/v1/channels/email` (`{"address","displayName","imapHost","imapPort","smtpHost","smtpPort","username","password","enabled"}`; la vista incluye `checkedAt` y `lastError`) | Dueño y administradores |
| `GET`, `PUT`, `DELETE /api/v1/channels/messenger`, `/api/v1/channels/instagram` (`{"accountId","displayName","accessToken","appSecret","enabled"}`) | Dueño y administradores |
| `GET`, `POST /api/v1/channels/messenger/{channelKey}/webhook`, `/api/v1/channels/instagram/{channelKey}/webhook` | Meta (token de verificación y firma) |
| `GET /api/v1/actions/integrations`, `PUT`/`DELETE .../integrations/{shopify\|zendesk}` (`{"account","username","token","enabled"}`; el token vacío conserva el guardado) | Dueño y administradores |

Endpoints de la Fase 3:

| Endpoint | Quién |
|---|---|
| `GET /api/v1/actions` | Todo el equipo |
| `POST /api/v1/actions`, `PUT`/`DELETE .../actions/{id}`, `GET .../actions/signing-secret` | Dueño y administradores |
| `POST /api/v1/actions/signing-secret/rotation` | Dueño |
| `POST /api/v1/widget/{key}/conversations/{id}/actions/{runId}/confirmation`, `/cancellation` | Visitante con su token |
| `GET`, `PUT`, `DELETE /api/v1/channels/whatsapp` | Dueño y administradores |
| `GET`, `POST /api/v1/channels/whatsapp/{channelKey}/webhook` | Meta (token de verificación y firma) |
| `GET /api/v1/usage?month=2026-11` | Dueño y administradores |

Endpoints de la Fase 2 (todos documentados en Swagger):

| Endpoint | Quién |
|---|---|
| `GET /api/v1/conversations?handling=WAITING\|AGENT`, `GET /api/v1/conversations/events` (SSE) | Todo el equipo |
| `POST /api/v1/conversations/{id}/takeover`, `/release`, `/closure`, `/messages` | Todo el equipo; liberar o cerrar la de otro agente, solo dueño y administradores |
| `POST /api/v1/widget/{key}/conversations/{id}/handoff`, `/handoff/cancellation`, `GET .../events` (SSE) | Visitante con su token |
| `GET /api/v1/knowledge-gaps`, `PUT /api/v1/knowledge-gaps/{id}/status` | Ver: todos; resolver o descartar: dueño y administradores |
| `GET /api/v1/workspace/members`, `PUT .../members/{userId}/role`, `DELETE .../members/{userId}` | Ver: todos; cambiar rol: dueño; quitar: dueño (a cualquiera menos al dueño) y administradores (solo agentes) |
| `GET`, `POST /api/v1/workspace/invitations`, `DELETE .../invitations/{id}` | Dueño y administradores |
| `POST /api/v1/invitations/preview`, `/acceptance` | Público, con el token de la invitación |
| `GET /api/v1/me/workspaces`, `POST /api/v1/auth/workspace-switch` | Cualquier usuario autenticado |
| `POST /api/v1/me/terms-acceptance` (`{"version":"2026-11-11"}`: la que muestra `GET /api/v1/me` en `termsVersion`) | Cualquier usuario autenticado |
| `GET /legal/privacy`, `GET /legal/terms` | Público |
