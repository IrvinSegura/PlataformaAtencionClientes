import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useEffect, useLayoutEffect, useRef, useState, type FormEvent } from 'react';
import { Link, useParams, useSearchParams } from 'react-router';
import type { ActionRun, ActionRunStatus } from '../api/actions';
import {
  type AnswerOutcome,
  type ConversationChannel,
  type ConversationDetail as Detail,
  type ConversationHandling,
  type ConversationIntent,
  conversationsApi,
  type ConversationSummary,
  type ModelUsage,
  type OperatorMessage,
  type SystemNotice,
} from '../api/conversations';
import { usePermissions, useSession } from '../auth/auth-context';
import { CustomerCard } from '../components/customer-card';
import { ConversationFollowUps } from '../components/follow-ups';
import { Icons } from '../components/icons';
import { Avatar, Badge, Empty, ErrorAlert, PageHeader, Pagination, Spinner, type Tone } from '../components/ui';
import { LANGUAGE_NAMES, formatDateTime, formatNumber, formatRelative, hostOf } from '../format';

const PAGE_SIZE = 20;

/** How each channel is named and drawn. */
export const CHANNEL_NAMES: Record<ConversationChannel, string> = {
  WEB: 'Widget web',
  WHATSAPP: 'WhatsApp',
  EMAIL: 'Correo',
  MESSENGER: 'Messenger',
  INSTAGRAM: 'Instagram',
};

const CHANNEL_ICONS = {
  WEB: 'Globe',
  WHATSAPP: 'Phone',
  EMAIL: 'Mail',
  MESSENGER: 'Messenger',
  INSTAGRAM: 'Instagram',
} as const satisfies Record<ConversationChannel, string>;

type ChannelIcon = (typeof CHANNEL_ICONS)[ConversationChannel];

/** What says who a customer is, in a conversation or in the list of customers. */
type Contact = Pick<ConversationSummary, 'channel' | 'contactId' | 'contactName' | 'origin'>;

/**
 * Who the customer is: the website they wrote from, or their name, number or address on the other channels.
 * Messenger and Instagram ids mean nothing to a person, so the channel stands in when the name is unknown.
 */
export function customerOf(conversation: Contact): { label: string; icon: ChannelIcon } {
  const icon = CHANNEL_ICONS[conversation.channel];
  switch (conversation.channel) {
    case 'WEB':
      return { label: hostOf(conversation.origin), icon };
    case 'WHATSAPP':
      return { label: conversation.contactName ?? `+${conversation.contactId ?? ''}`, icon };
    case 'EMAIL':
      return { label: conversation.contactName ?? conversation.contactId ?? '', icon };
    default:
      return { label: conversation.contactName ?? `Cliente de ${CHANNEL_NAMES[conversation.channel]}`, icon };
  }
}

/** The channel and, where it means something to a person, the customer's number or address. */
export function channelOf(conversation: Contact): string {
  const name = CHANNEL_NAMES[conversation.channel];
  if (conversation.channel === 'WHATSAPP' && conversation.contactId) {
    return `${name} · +${conversation.contactId}`;
  }
  if (conversation.channel === 'EMAIL' && conversation.contactId) {
    return `${name} · ${conversation.contactId}`;
  }
  return name;
}

const ROUTING_REASONS: Record<string, string> = {
  simple: 'pregunta directa',
  reasoning: 'pide razonar',
  'several-questions': 'varias preguntas',
  'long-question': 'pregunta larga',
  'several-documents': 'combina varios documentos',
  'escalated-no-answer': 'el modelo económico no encontró respuesta',
  'routing-disabled': 'selección automática desactivada',
  reused: 'respuesta repetida, reutilizada sin costo',
  hypothetical: 'plantea un caso hipotético',
  comparison: 'compara opciones',
  'learned-difficult': 'antes no ayudó con el rápido',
  'customer-unsatisfied': 'el cliente dijo que la respuesta no le sirvió',
};

const RUN_STATUSES: Record<ActionRunStatus, { label: string; tone: Tone }> = {
  PENDING_CONFIRMATION: { label: 'Esperando confirmación', tone: 'warning' },
  RUNNING: { label: 'En curso', tone: 'info' },
  SUCCEEDED: { label: 'Hecha', tone: 'success' },
  FAILED: { label: 'Falló', tone: 'danger' },
  CANCELLED: { label: 'Cancelada', tone: 'neutral' },
  EXPIRED: { label: 'Expiró', tone: 'neutral' },
  REJECTED: { label: 'Rechazada', tone: 'danger' },
};
// Changes arrive through the live stream; this only covers a stream that is down for a while
const FALLBACK_REFRESH_MS = 60_000;
const MAX_REPLY_LENGTH = 4000;

const OUTCOMES: Record<AnswerOutcome, { label: string; tone: Tone }> = {
  ANSWERED: { label: 'Respondida', tone: 'success' },
  NO_ANSWER: { label: 'Sin respuesta', tone: 'warning' },
  FAILED: { label: 'Falló', tone: 'danger' },
  SMALL_TALK: { label: 'Cortesía (gratis)', tone: 'neutral' },
};

const NOTICES: Record<SystemNotice, (name: string) => string> = {
  HANDOFF_REQUESTED: () => 'El visitante pidió hablar con una persona.',
  HANDOFF_CANCELLED: () => 'El visitante volvió con el asistente.',
  AGENT_JOINED: (name) => `${name} tomó la conversación.`,
  AGENT_LEFT: (name) => `${name} devolvió la conversación al asistente.`,
  CONVERSATION_CLOSED: () => 'La conversación se cerró.',
  ASSISTANT_PAUSED: () => 'El plan alcanzó su límite de respuestas del mes: el asistente pasó la conversación al equipo.',
};

/** What each topic is called, in the order the panel lists them. */
export const INTENT_NAMES: Record<ConversationIntent, string> = {
  ORDER_STATUS: 'Estado del pedido',
  SHIPPING: 'Envíos',
  RETURNS: 'Devoluciones',
  PAYMENTS: 'Pagos y facturas',
  PRICING: 'Precios y promociones',
  AVAILABILITY: 'Disponibilidad',
  HOURS_AND_LOCATION: 'Horarios y sucursales',
  ACCOUNT: 'Cuenta',
  COMPLAINT: 'Queja',
};

const INTENTS = Object.keys(INTENT_NAMES) as ConversationIntent[];

const FILTERS: { value: ConversationHandling | null; label: string }[] = [
  { value: null, label: 'Todas' },
  { value: 'WAITING', label: 'En espera' },
  { value: 'AGENT', label: 'Atendidas por el equipo' },
];

export function ConversationsPage() {
  const { conversationId } = useParams();
  const [params, setParams] = useSearchParams();
  const page = Math.max(0, Number(params.get('page')) || 0);
  const handlingParam = params.get('handling');
  const handling = FILTERS.find((filter) => filter.value === handlingParam)?.value ?? null;
  const intentParam = params.get('intent');
  const intent = INTENTS.find((value) => value === intentParam) ?? null;
  const list = useQuery({
    queryKey: ['conversations', handling, intent, page],
    queryFn: () => conversationsApi.list(page, PAGE_SIZE, handling ?? undefined, intent ?? undefined),
    placeholderData: keepPreviousData,
    refetchInterval: FALLBACK_REFRESH_MS,
  });
  const query = (next: { page?: number; handling?: ConversationHandling | null; intent?: ConversationIntent | null }) => {
    const search = new URLSearchParams();
    const nextHandling = next.handling === undefined ? handling : next.handling;
    if (nextHandling) {
      search.set('handling', nextHandling);
    }
    const nextIntent = next.intent === undefined ? intent : next.intent;
    if (nextIntent) {
      search.set('intent', nextIntent);
    }
    if (next.page) {
      search.set('page', String(next.page));
    }
    const text = search.toString();
    return text ? `?${text}` : '';
  };

  return (
    <section className="inbox-page" aria-labelledby="conversations-title">
      <PageHeader
        id="conversations-title"
        eyebrow="Atención"
        title="Conversaciones"
        description="Lo que preguntan tus clientes, en vivo. Toma una conversación para responder tú mismo."
      />
      <div className={conversationId ? 'inbox has-selection' : 'inbox'}>
        <div className="inbox-list">
          <div className="inbox-list-head">
            <div className="tabs" role="tablist" aria-label="Filtrar conversaciones">
              {FILTERS.map((filter) => (
                <button
                  key={filter.label}
                  type="button"
                  role="tab"
                  aria-selected={filter.value === handling}
                  className={filter.value === handling ? 'tab active' : 'tab'}
                  onClick={() => setParams(new URLSearchParams(query({ handling: filter.value, page: 0 }).slice(1)))}
                >
                  {filter.label}
                </button>
              ))}
            </div>
            <label className="intent-filter">
              <span className="sr-only">Tema</span>
              <select
                value={intent ?? ''}
                onChange={(event) =>
                  setParams(
                    new URLSearchParams(
                      query({ intent: (event.target.value || null) as ConversationIntent | null, page: 0 }).slice(1),
                    ),
                  )
                }
              >
                <option value="">Todos los temas</option>
                {INTENTS.map((value) => (
                  <option key={value} value={value}>
                    {INTENT_NAMES[value]}
                  </option>
                ))}
              </select>
            </label>
          </div>
          <div className="inbox-scroll">
            <ErrorAlert error={list.error} />
            {list.isPending && <Spinner />}
            {list.data && list.data.items.length === 0 && (
              <Empty icon={handling === 'WAITING' ? 'Headset' : 'Inbox'}>
                {intent
                  ? `Ninguna conversación sobre «${INTENT_NAMES[intent]}» con este filtro.`
                  : handling === 'WAITING'
                    ? 'Nadie está esperando a una persona.'
                    : handling === 'AGENT'
                      ? 'El equipo no está atendiendo ninguna conversación.'
                      : 'Aún no hay conversaciones. Aparecerán aquí cuando un visitante escriba en el widget.'}
              </Empty>
            )}
            <ul className="list">
              {list.data?.items.map((conversation) => (
                <li key={conversation.id}>
                  <Link
                    to={`/conversations/${conversation.id}${query({ page })}`}
                    className={conversation.id === conversationId ? 'list-item active' : 'list-item'}
                    aria-current={conversation.id === conversationId ? 'page' : undefined}
                  >
                    <Avatar name={customerOf(conversation).label} icon={customerOf(conversation).icon} />
                    <span className="list-item-body">
                      <span className="list-item-top">
                        <span className="list-item-title">{customerOf(conversation).label}</span>
                        <time
                          className="list-item-time"
                          dateTime={conversation.lastActivityAt}
                          title={formatDateTime(conversation.lastActivityAt)}
                        >
                          {formatRelative(conversation.lastActivityAt)}
                        </time>
                      </span>
                      <span className="list-item-meta">
                        {conversation.questionCount} {conversation.questionCount === 1 ? 'pregunta' : 'preguntas'}
                      </span>
                      <span className="row">
                        <HandlingBadge conversation={conversation} />
                        {conversation.frustratedAt && <FrustratedBadge />}
                        {conversation.status === 'CLOSED' && <Badge>Cerrada</Badge>}
                        {(conversation.intents ?? []).map((value) => (
                          <Badge key={value} tone="primary">
                            {INTENT_NAMES[value]}
                          </Badge>
                        ))}
                      </span>
                    </span>
                  </Link>
                </li>
              ))}
            </ul>
            {list.data && (
              <Pagination
                page={page}
                size={PAGE_SIZE}
                total={list.data.total}
                onChange={(next) => setParams(new URLSearchParams(query({ page: next }).slice(1)))}
              />
            )}
          </div>
        </div>
        <div className="inbox-detail">
          {conversationId ? (
            <>
              <Link className="inbox-back button button-sm button-ghost" to={`/conversations${query({ page })}`}>
                <Icons.ChevronLeft size={16} />
                Todas las conversaciones
              </Link>
              <ConversationDetail key={conversationId} id={conversationId} />
            </>
          ) : (
            <Empty icon="Chat">
              Elige una conversación para ver sus mensajes y fuentes.
            </Empty>
          )}
        </div>
      </div>
    </section>
  );
}

function FrustratedBadge() {
  return (
    <span title="Un mensaje del cliente sonó molesto">
      <Badge tone="danger">Cliente molesto</Badge>
    </span>
  );
}

function HandlingBadge({ conversation }: { conversation: ConversationSummary }) {
  if (conversation.status === 'CLOSED') {
    return null;
  }
  if (conversation.handling === 'WAITING') {
    return (
      <Badge tone="warning" dot>
        Espera a una persona
      </Badge>
    );
  }
  if (conversation.handling === 'AGENT') {
    return (
      <Badge tone="info" dot>
        Atiende {conversation.agentName}
      </Badge>
    );
  }
  return null;
}

function ConversationDetail({ id }: { id: string }) {
  const detail = useQuery({
    queryKey: ['conversation', id],
    queryFn: () => conversationsApi.get(id),
    refetchInterval: FALLBACK_REFRESH_MS,
  });
  if (detail.error) {
    return <ErrorAlert error={detail.error} />;
  }
  if (!detail.data) {
    return <Spinner label="Cargando conversación…" />;
  }
  const { conversation, messages, actionRuns = [], rating = null } = detail.data;
  const customer = customerOf(conversation);
  const runs = new Map(actionRuns.map((run) => [run.id, run]));
  const usage = messages.reduce(
    (total, message) => ({
      input: total.input + (message.usage?.inputTokens ?? 0),
      output: total.output + (message.usage?.outputTokens ?? 0),
    }),
    { input: 0, output: 0 },
  );

  return (
    <article className="conversation" aria-labelledby="conversation-title">
      <header className="detail-header">
        <div className="detail-title">
          <div className="detail-title-text">
            <Avatar name={customer.label} icon={customer.icon} size="lg" />
            <div>
              <h2 id="conversation-title">{customer.label}</h2>
              <span className="row">
                <Badge tone={conversation.status === 'OPEN' ? 'success' : 'neutral'} dot>
                  {conversation.status === 'OPEN' ? 'Abierta' : 'Cerrada'}
                </Badge>
                {conversation.frustratedAt && <FrustratedBadge />}
              </span>
            </div>
          </div>
          <HandoffActions conversation={conversation} />
        </div>
        <dl className="facts">
          <div>
            <dt>Canal</dt>
            <dd>
              {channelOf(conversation)}
            </dd>
          </div>
          <div>
            <dt>Inicio</dt>
            <dd>{formatDateTime(conversation.startedAt)}</dd>
          </div>
          <div>
            <dt>Atiende</dt>
            <dd>
              {conversation.handling === 'AGENT'
                ? conversation.agentName
                : conversation.handling === 'WAITING'
                  ? 'Nadie aún (espera a una persona)'
                  : 'El asistente'}
            </dd>
          </div>
          {conversation.language && (
            <div>
              <dt>Idioma</dt>
              <dd>{LANGUAGE_NAMES[conversation.language] ?? conversation.language}</dd>
            </div>
          )}
          <div>
            <dt>Preguntas</dt>
            <dd>{conversation.questionCount}</dd>
          </div>
          <div>
            <dt>Tokens (entrada / salida)</dt>
            <dd>
              {formatNumber(usage.input)} / {formatNumber(usage.output)}
            </dd>
          </div>
          {rating && (
            <div>
              <dt>Calificación</dt>
              <dd>
                <span aria-label={`${rating.score} de 5`}>
                  <span aria-hidden="true">{'★'.repeat(rating.score)}{'☆'.repeat(5 - rating.score)}</span>
                </span>
              </dd>
            </div>
          )}
        </dl>
        {rating?.comment && (
          <blockquote className="rating-comment">
            <p>{rating.comment}</p>
            <footer className="muted small">Comentario del cliente · {formatDateTime(rating.ratedAt)}</footer>
          </blockquote>
        )}
        <IntentEditor conversation={conversation} />
        {conversation.contactId && <CustomerCard conversationId={conversation.id} />}
        {conversation.contactId && <ConversationFollowUps conversationId={conversation.id} />}
        {actionRuns.length > 0 && <ActionRuns runs={actionRuns} />}
      </header>
      <Transcript messages={messages} runs={runs} />
      <ReplyBox conversation={conversation} />
    </article>
  );
}

/** Follows new messages, unless the operator scrolled up to read something older. */
function Transcript({ messages, runs }: { messages: OperatorMessage[]; runs: Map<string, ActionRun> }) {
  const list = useRef<HTMLOListElement>(null);
  const atBottom = useRef(true);
  // Before paint, so the conversation opens on its latest message without a visible jump
  useLayoutEffect(() => {
    const element = list.current;
    if (element && atBottom.current) {
      element.scrollTop = element.scrollHeight;
    }
  }, [messages.length]);
  useEffect(() => {
    // Live only once the history is on screen, so opening a conversation does not read all of it aloud; then each new
    // message from the customer or the assistant is
    if (messages.length > 0) {
      list.current?.setAttribute('aria-live', 'polite');
    }
  }, [messages.length]);
  return (
    <ol
      className="transcript"
      ref={list}
      aria-label="Mensajes de la conversación"
      aria-relevant="additions"
      // Scrollable: reachable with the keyboard so the arrows scroll it
      tabIndex={0}
      onScroll={(event) => {
        const element = event.currentTarget;
        atBottom.current = element.scrollHeight - element.scrollTop - element.clientHeight < 80;
      }}
    >
      {messages.map((message) => (
        <Message key={message.id} message={message} run={message.actionRunId ? runs.get(message.actionRunId) : undefined} />
      ))}
    </ol>
  );
}

function useConversationAction(id: string, action: (id: string) => Promise<ConversationSummary>) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: () => action(id),
    onSuccess: (summary) => {
      queryClient.setQueryData<Detail>(['conversation', id], (detail) =>
        detail ? { ...detail, conversation: summary } : detail,
      );
      void queryClient.invalidateQueries({ queryKey: ['conversation', id] });
      void queryClient.invalidateQueries({ queryKey: ['conversations'] });
    },
  });
}

function HandoffActions({ conversation }: { conversation: ConversationSummary }) {
  const { userId } = useSession();
  const { canOverrideAgents } = usePermissions();
  const takeOver = useConversationAction(conversation.id, conversationsApi.takeOver);
  const release = useConversationAction(conversation.id, conversationsApi.release);
  const close = useConversationAction(conversation.id, conversationsApi.close);
  if (conversation.status === 'CLOSED') {
    return null;
  }
  const mine = conversation.handling === 'AGENT' && conversation.agentId === userId;
  const someoneElses = conversation.handling === 'AGENT' && !mine;
  const pending = takeOver.isPending || release.isPending || close.isPending;
  return (
    <div className="handoff-actions">
      <div className="row">
        {conversation.handling !== 'AGENT' && (
          <button
            type="button"
            className="button button-sm button-primary"
            disabled={pending}
            onClick={() => takeOver.mutate()}
          >
            <Icons.Headset size={16} />
            Tomar conversación
          </button>
        )}
        {(mine || (someoneElses && canOverrideAgents)) && (
          <button type="button" className="button button-sm" disabled={pending} onClick={() => release.mutate()}>
            <Icons.Bot size={16} />
            Devolver al asistente
          </button>
        )}
        {(!someoneElses || canOverrideAgents) && (
          <button
            type="button"
            className="button button-sm button-ghost"
            disabled={pending}
            onClick={() => {
              if (window.confirm('¿Cerrar la conversación? El visitante tendrá que empezar una nueva.')) {
                close.mutate();
              }
            }}
          >
            <Icons.X size={16} />
            Cerrar conversación
          </button>
        )}
      </div>
      <ErrorAlert error={takeOver.error ?? release.error ?? close.error} />
    </div>
  );
}

/** The conversation's topics, which the team corrects by hand. */
function IntentEditor({ conversation }: { conversation: ConversationSummary }) {
  const intents = conversation.intents ?? [];
  const [editing, setEditing] = useState(false);
  const [chosen, setChosen] = useState<ConversationIntent[]>(intents);
  const queryClient = useQueryClient();
  const save = useMutation({
    mutationFn: () => conversationsApi.setIntents(conversation.id, INTENTS.filter((value) => chosen.includes(value))),
    onSuccess: (summary) => {
      setEditing(false);
      queryClient.setQueryData<Detail>(['conversation', conversation.id], (detail) =>
        detail ? { ...detail, conversation: summary } : detail,
      );
      void queryClient.invalidateQueries({ queryKey: ['conversations'] });
    },
  });
  if (!editing) {
    return (
      <div className="row intent-row">
        <span className="muted small">Temas:</span>
        {intents.length === 0 && <span className="muted small">ninguno</span>}
        {intents.map((value) => (
          <Badge key={value} tone="primary">
            {INTENT_NAMES[value]}
          </Badge>
        ))}
        {conversation.intentsEdited && <span className="muted small">(elegidos por el equipo)</span>}
        <button
          type="button"
          className="button button-sm button-ghost"
          onClick={() => {
            setChosen(intents);
            setEditing(true);
          }}
        >
          Cambiar temas
        </button>
      </div>
    );
  }
  return (
    <form
      className="intent-editor"
      aria-label="Temas de la conversación"
      onSubmit={(event) => {
        event.preventDefault();
        save.mutate();
      }}
    >
      <fieldset className="intent-options">
        <legend className="muted small">Temas de la conversación</legend>
        {INTENTS.map((value) => (
          <label key={value} className="row">
            <input
              type="checkbox"
              checked={chosen.includes(value)}
              onChange={(event) =>
                setChosen(event.target.checked ? [...chosen, value] : chosen.filter((known) => known !== value))
              }
            />
            {INTENT_NAMES[value]}
          </label>
        ))}
      </fieldset>
      <p className="hint">Al guardar, los mensajes siguientes ya no agregan temas a esta conversación.</p>
      <div className="row">
        <button type="submit" className="button button-sm button-primary" disabled={save.isPending}>
          {save.isPending ? 'Guardando…' : 'Guardar temas'}
        </button>
        <button type="button" className="button button-sm button-ghost" onClick={() => setEditing(false)}>
          Cancelar
        </button>
      </div>
      <ErrorAlert error={save.error} />
    </form>
  );
}

function ReplyBox({ conversation }: { conversation: ConversationSummary }) {
  const { userId } = useSession();
  const [text, setText] = useState('');
  const queryClient = useQueryClient();
  const reply = useMutation({
    mutationFn: () => conversationsApi.reply(conversation.id, text.trim()),
    onSuccess: (message) => {
      setText('');
      queryClient.setQueryData<Detail>(['conversation', conversation.id], (detail) =>
        detail && !detail.messages.some((known) => known.id === message.id)
          ? { ...detail, messages: [...detail.messages, message] }
          : detail,
      );
    },
  });
  if (conversation.status === 'CLOSED' || conversation.handling !== 'AGENT' || conversation.agentId !== userId) {
    return null;
  }
  const onSubmit = (event: FormEvent) => {
    event.preventDefault();
    if (text.trim()) {
      reply.mutate();
    }
  };
  return (
    <form className="reply-box" onSubmit={onSubmit}>
      <label className="grow">
        Respuesta al visitante
        <textarea
          value={text}
          onChange={(event) => setText(event.target.value)}
          maxLength={MAX_REPLY_LENGTH}
          rows={2}
          placeholder="Escribe tu respuesta… (Enter envía, Shift+Enter agrega una línea)"
          onKeyDown={(event) => {
            // Enter sends, Shift+Enter adds a line
            if (event.key === 'Enter' && !event.shiftKey) {
              event.preventDefault();
              event.currentTarget.form?.requestSubmit();
            }
          }}
        />
      </label>
      <button type="submit" className="button button-primary" disabled={reply.isPending || !text.trim()}>
        <Icons.Send size={16} />
        {reply.isPending ? 'Enviando…' : 'Enviar'}
      </button>
      <ErrorAlert error={reply.error} />
    </form>
  );
}

function ActionRuns({ runs }: { runs: ActionRun[] }) {
  return (
    <details className="citations">
      <summary>
        {runs.length} {runs.length === 1 ? 'acción del asistente' : 'acciones del asistente'}
      </summary>
      <ul className="action-runs">
        {runs.map((run) => (
          <li key={run.id}>
            <strong>{run.actionTitle}</strong>
            <Badge tone={RUN_STATUSES[run.status].tone}>{RUN_STATUSES[run.status].label}</Badge>
            <span className="mono">{JSON.stringify(run.arguments)}</span>
            {run.error && <span className="muted">{run.error}</span>}
            <time dateTime={run.createdAt} title={formatDateTime(run.createdAt)} className="muted">
              {formatRelative(run.createdAt)}
            </time>
          </li>
        ))}
      </ul>
    </details>
  );
}

function usageLine(usage: ModelUsage): string {
  const tier = usage.tier === 'SIMPLE' ? 'modelo económico' : usage.tier === 'COMPLEX' ? 'modelo avanzado' : null;
  const reason = usage.routingReason ? (ROUTING_REASONS[usage.routingReason] ?? usage.routingReason) : null;
  return [
    usage.model,
    tier && (reason ? `${tier} (${reason})` : tier),
    `${formatNumber(usage.inputTokens)} → ${formatNumber(usage.outputTokens)} tokens`,
    `${formatNumber(usage.latencyMillis)} ms`,
  ]
    .filter(Boolean)
    .join(' · ');
}

function Message({ message, run }: { message: OperatorMessage; run?: ActionRun }) {
  const time = (
    <time dateTime={message.createdAt} title={formatDateTime(message.createdAt)}>
      {formatRelative(message.createdAt)}
    </time>
  );
  if (message.author === 'SYSTEM') {
    const notice = NOTICES[message.content as SystemNotice];
    return (
      <li className="notice">
        <span>{notice ? notice(message.authorName ?? 'Alguien del equipo') : message.content}</span>
        <span aria-hidden="true">·</span>
        {time}
      </li>
    );
  }
  if (message.author === 'VISITOR') {
    return (
      <li className="message message-visitor">
        <div className="bubble">
          <div className="bubble-meta">
            <strong>Visitante</strong> · {time}
          </div>
          <p className="bubble-text">{message.content}</p>
        </div>
      </li>
    );
  }
  if (message.author === 'AGENT') {
    const name = message.authorName ?? 'Equipo';
    return (
      <li className="message message-agent">
        <Avatar name={name} size="sm" />
        <div className="bubble">
          <div className="bubble-meta">
            <strong>{name} · equipo</strong> · {time}
          </div>
          <p className="bubble-text">{message.content}</p>
        </div>
      </li>
    );
  }
  const outcome = message.outcome ? OUTCOMES[message.outcome] : null;
  return (
    <li className="message message-assistant">
      <Avatar name="Asistente" icon="Bot" size="sm" />
      <div className="bubble">
        <div className="bubble-meta">
          <strong>Asistente</strong> · {time} {outcome && <Badge tone={outcome.tone}>{outcome.label}</Badge>}{' '}
          {message.helpful === true && <Badge tone="success">Le ayudó</Badge>}
          {message.helpful === false && <Badge tone="danger">No le ayudó</Badge>}{' '}
          {run && (
            <Badge tone={RUN_STATUSES[run.status].tone}>
              {run.actionTitle}: {RUN_STATUSES[run.status].label.toLowerCase()}
            </Badge>
          )}
        </div>
        <p className="bubble-text">{message.content}</p>
        {message.citations && message.citations.length > 0 && (
          <details className="citations">
            <summary>
              {message.citations.length} {message.citations.length === 1 ? 'fuente' : 'fuentes'}
            </summary>
            <ol>
              {message.citations.map((citation) => (
                <li key={citation.number} value={citation.number}>
                  <strong>{citation.documentTitle}</strong>
                  <p className="muted small">{citation.excerpt}</p>
                </li>
              ))}
            </ol>
          </details>
        )}
        {message.usage && (
          <div className="usage">{usageLine(message.usage)}</div>
        )}
      </div>
    </li>
  );
}
