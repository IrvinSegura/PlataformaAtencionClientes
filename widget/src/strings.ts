// Visitor-facing texts in Spanish. Other languages come from the backend with the widget's config (see translate)
export const strings = {
  open: 'Abrir chat de ayuda',
  close: 'Cerrar chat',
  subtitle: 'En línea · responde al instante',
  inputLabel: 'Escribe tu pregunta',
  inputPlaceholder: 'Escribe tu pregunta…',
  send: 'Enviar',
  sources: 'Fuentes',
  source: (number: number) => `Fuente ${number}`,
  thinking: 'Escribiendo…',
  unavailable: 'El chat no está disponible en este momento.',
  networkError: 'No se pudo conectar. Revisa tu conexión e inténtalo de nuevo.',
  tooFast: 'Estás enviando mensajes muy rápido.',
  retryIn: (seconds: number) => `Inténtalo en ${seconds} s.`,
  conversationEnded: 'Esta conversación terminó. Escribe de nuevo para empezar otra.',
  invalidQuestion: (max: number) => `La pregunta debe tener entre 1 y ${max} caracteres.`,
  interrupted: 'La respuesta se interrumpió. Inténtalo de nuevo.',
  characters: (left: number) => `${left} caracteres restantes`,
  talkToPerson: 'Hablar con una persona',
  backToAssistant: 'Volver al asistente',
  waiting: 'Te comunicamos con una persona del equipo. Puedes seguir escribiendo.',
  withAgent: (name: string) => `Te atiende ${name}`,
  team: 'Equipo',
  // Read by screen readers before each message, since only colour and side tell who wrote it
  you: 'Tú',
  assistant: 'Asistente',
  handoffFailed: 'No pudimos avisar al equipo. Inténtalo de nuevo.',
  confirm: 'Confirmar',
  cancel: 'Cancelar',
  actionGone: 'Esta acción ya no está disponible.',
  actionFailed: 'No pudimos enviar tu respuesta. Inténtalo de nuevo.',
  helpful: 'Me ayudó',
  notHelpful: 'No me ayudó',
  rateQuestion: '¿Cómo te atendimos?',
  rateOption: (score: number) => `${score} de 5`,
  thanks: '¡Gracias por tu opinión!',
  feedbackFailed: 'No pudimos guardar tu opinión. Inténtalo de nuevo.',
  // Codes of SYSTEM messages (backend SystemNotice)
  notices: {
    HANDOFF_REQUESTED: () => 'Pediste hablar con una persona.',
    HANDOFF_CANCELLED: () => 'Volviste con el asistente.',
    AGENT_JOINED: (name: string) => `${name} se unió a la conversación.`,
    AGENT_LEFT: (name: string) => `${name} salió; te atiende de nuevo el asistente.`,
    CONVERSATION_CLOSED: () => 'La conversación terminó. Escribe de nuevo para empezar otra.',
    // The plan ran out of answers: the visitor only needs to know a person takes over
    ASSISTANT_PAUSED: () => 'Una persona del equipo te responderá en breve.',
  } as Record<string, (name: string) => string>,
};

/**
 * Replaces the texts with those of the visitor's language, keyed as above ("send", "notices.AGENT_JOINED"). A text
 * that takes a value marks its place with {}.
 */
export function translate(texts: Record<string, string> = {}): void {
  for (const [key, text] of Object.entries(texts)) {
    const [first, notice] = key.split('.');
    const group = (notice ? strings.notices : strings) as Record<string, unknown>;
    const name = notice ?? first!;
    const current = group[name];
    if (typeof current === 'function') {
      group[name] = (value: unknown) => text.replace('{}', String(value));
    } else if (typeof current === 'string') {
      group[name] = text;
    }
  }
}
