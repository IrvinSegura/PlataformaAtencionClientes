import { ApiError } from './http-client';

// Operator-facing texts for the backend's stable error codes
const messages: Record<string, string> = {
  INVALID_CREDENTIALS: 'Email o contraseña incorrectos.',
  ACCOUNT_LOCKED: 'Demasiados intentos fallidos. Espera unos minutos e inténtalo de nuevo.',
  EMAIL_ALREADY_REGISTERED: 'Ya existe una cuenta con este email.',
  TERMS_NOT_ACCEPTED: 'Acepta los términos y condiciones para continuar. Si cambiaron, recarga la página para leerlos.',
  INVALID_EMAIL: 'El email no es válido.',
  INVALID_PROFILE: 'Revisa el nombre y los datos del perfil.',
  INVALID_REFRESH_TOKEN: 'Tu sesión expiró. Inicia sesión de nuevo.',
  UNAUTHORIZED: 'Tu sesión expiró. Inicia sesión de nuevo.',
  FORBIDDEN: 'Tu rol no permite esta acción.',
  DUPLICATE_DOCUMENT: 'Este archivo ya fue subido.',
  UNSUPPORTED_DOCUMENT:
    'Formato no admitido. Sube PDF, Word (.docx, .doc), Excel (.xlsx, .xls), HTML, Markdown (.md) o texto (.txt).',
  DUPLICATE_WEB_PAGE: 'Esta página ya está en la base de conocimiento. Usa «Reprocesar» para descargarla de nuevo.',
  INVALID_WEB_ADDRESS: 'La dirección no es válida. Escribe la URL completa de una página pública, como https://misitio.com/ayuda.',
  PLAN_LIMIT_REACHED: 'Se agotaron las respuestas del plan de este mes. Mejora el plan para seguir probando.',
  INVALID_QUESTION: 'Escribe una pregunta de hasta 2000 caracteres.',
  QUESTION_LIMIT_REACHED: 'La conversación llegó a su límite de preguntas; empieza una nueva.',
  INVALID_ATTENTION_SETTINGS:
    'Revisa los avisos: cada número lleva de 8 a 15 dígitos con su código de país (hasta tres), y una plantilla necesita su idioma.',
  INVALID_WHATSAPP_CHANNEL:
    'Revisa los datos: el identificador del número son solo dígitos y el token y el secreto son obligatorios al conectar.',
  WHATSAPP_CHANNEL_NOT_FOUND: 'El número de WhatsApp no está conectado.',
  INVALID_ACTION: 'Revisa la acción: nombre, descripción, parámetros y URL del webhook.',
  ACTION_NAME_TAKEN: 'Ya existe otra acción con ese nombre.',
  ACTION_LIMIT_REACHED: 'Llegaste al máximo de acciones de este espacio de trabajo.',
  ACTION_NOT_FOUND: 'La acción ya no existe.',
  ACTION_RUN_NOT_FOUND: 'Esa ejecución de la acción ya no existe.',
  ACTION_ALREADY_HANDLED: 'Esta acción ya fue confirmada o cancelada.',
  ENDPOINT_NOT_ALLOWED: 'La URL del webhook debe ser https y apuntar a un servidor público.',
  MESSAGE_NOT_FOUND: 'El mensaje ya no existe.',
  WORKSPACE_NOT_FOUND: 'El espacio de trabajo ya no existe.',
  WIDGET_NOT_FOUND: 'El widget no está activo.',
  DOCUMENT_TOO_LARGE: 'El archivo supera el tamaño máximo de 10 MB.',
  FILE_TOO_LARGE: 'El archivo supera el tamaño máximo de 10 MB.',
  EMPTY_DOCUMENT: 'El documento no tiene texto del que aprender.',
  INVALID_DOCUMENT: 'El documento no es válido.',
  UNREADABLE_DOCUMENT: 'No se pudo leer el documento.',
  DOCUMENT_BUSY: 'El documento se está procesando; inténtalo cuando termine.',
  DOCUMENT_NOT_FOUND: 'El documento ya no existe.',
  CONVERSATION_NOT_FOUND: 'La conversación no existe.',
  INVALID_WORKSPACE_SETTINGS:
    'Configuración no válida. Los orígenes deben tener la forma https://dominio[:puerto] (http:// solo para localhost).',
  EMBEDDING_UNAVAILABLE: 'El servicio de búsqueda no está disponible en este momento.',
  KNOWLEDGE_GAP_NOT_FOUND: 'Esta pregunta ya no está en la lista.',
  CONVERSATION_CLOSED: 'La conversación ya está cerrada.',
  CONVERSATION_ASSIGNED: 'Otra persona del equipo ya está atendiendo esta conversación.',
  CONVERSATION_NOT_TAKEN: 'Toma la conversación antes de responder.',
  INVALID_REPLY: 'La respuesta debe tener entre 1 y 4000 caracteres.',
  INVALID_INVITATION: 'La invitación no es válida: ya se usó, se revocó o venció. Pide una nueva.',
  INVALID_INVITATION_ROLE: 'Solo se puede invitar como administrador o agente.',
  MEMBER_ACTION_NOT_ALLOWED: 'Tu rol no permite hacer esto con este miembro.',
  MEMBER_NOT_FOUND: 'Esta persona ya no es miembro del espacio de trabajo.',
  OWNER_IS_PERMANENT: 'El rol del dueño no se puede cambiar ni quitar.',
  WORKSPACE_ACCESS_DENIED: 'Ya no eres miembro de ese espacio de trabajo.',
  PAYMENTS_NOT_CONFIGURED: 'Los pagos no están configurados en este servidor.',
  PAYMENTS_UNAVAILABLE: 'El servicio de pagos no responde. Inténtalo en unos minutos.',
  PLAN_NOT_FOR_SALE: 'Este plan no se puede contratar ahora.',
  PLAN_NOT_FOUND: 'Ese plan ya no existe.',
  NO_PAYMENT_ACCOUNT: 'Contrata un plan para administrar tus pagos.',
  INVALID_EMAIL_CHANNEL:
    'Revisa los datos del buzón: la dirección, los servidores IMAP y SMTP (nombres públicos) y sus puertos.',
  INVALID_META_CHANNEL:
    'Revisa los datos: el identificador es el número que muestra Meta y el token y el secreto son obligatorios al conectar.',
  EMAIL_CHANNEL_NOT_FOUND: 'El buzón no está conectado.',
  META_CHANNEL_NOT_FOUND: 'La cuenta no está conectada.',
  INVALID_INTEGRATION:
    'Revisa los datos: la tienda es su dirección mitienda.myshopify.com, la cuenta de Zendesk es su subdominio y el token es obligatorio al conectar.',
  INTEGRATION_NOT_FOUND: 'La integración no está conectada.',
  INVALID_PERIOD: 'Elige un periodo de hasta un año, con la fecha de inicio antes de la de fin.',
};

const weakPassword: Record<string, string> = {
  'Use at least 12 characters': 'Usa al menos 12 caracteres.',
  'Use at most 72 bytes': 'La contraseña es demasiado larga.',
  'Use more varied characters': 'Usa caracteres más variados.',
  'The password must not contain your email': 'La contraseña no debe contener tu email.',
};

/** A message an operator can act on, for any error a request may throw. */
export function errorMessage(error: unknown): string {
  if (!(error instanceof ApiError)) {
    return 'No se pudo conectar con el servidor. Revisa tu conexión.';
  }
  if (error.code === 'WEAK_PASSWORD') {
    return (error.detail && weakPassword[error.detail]) ?? 'La contraseña no es suficientemente segura.';
  }
  if (error.code === 'RATE_LIMITED') {
    return error.retryAfterSeconds
      ? `Demasiadas solicitudes. Inténtalo en ${error.retryAfterSeconds} s.`
      : 'Demasiadas solicitudes. Inténtalo en unos segundos.';
  }
  if (error.code === 'VALIDATION_FAILED') {
    const fields = error.violations.map((violation) => violation.field).join(', ');
    return fields ? `Revisa estos campos: ${fields}.` : 'Revisa los datos del formulario.';
  }
  const known = messages[error.code];
  if (known) {
    return known;
  }
  // The correlation id lets whoever runs the platform find this request in the logs
  return error.correlationId
    ? `Ocurrió un error inesperado (referencia ${error.correlationId}).`
    : 'Ocurrió un error inesperado.';
}
