import { readServerSentEvents } from './sse';
import type {
  AnswerDone,
  ConversationState,
  MessageView,
  StartedConversation,
  StoredSession,
  WidgetConfig,
} from './types';

const CONVERSATION_TOKEN_HEADER = 'X-Conversation-Token';

/** An error answer of the API, carrying the stable code of its Problem Details body. */
export class WidgetApiError extends Error {
  constructor(
    readonly status: number,
    readonly code: string | null,
    readonly retryAfterSeconds: number | null,
  ) {
    super(`Widget API error ${status}${code ? ` (${code})` : ''}`);
    this.name = 'WidgetApiError';
  }
}

export interface AnswerHandlers {
  onText(delta: string): void;
  onDone(done: AnswerDone): void;
  /** The message was kept for a person of the team instead of being answered by the assistant. */
  onForwarded(): void;
}

export type LiveEvent = { type: 'state'; state: ConversationState } | { type: 'message'; message: MessageView };

/**
 * Client of the public widget API. Visitors are anonymous: requests carry no cookies, and the conversation token is
 * the only proof that a visitor owns a conversation.
 */
export class WidgetApi {
  private readonly base: string;

  constructor(apiUrl: string, widgetKey: string) {
    this.base = `${apiUrl.replace(/\/+$/, '')}/api/v1/widget/${encodeURIComponent(widgetKey)}`;
  }

  async config(): Promise<WidgetConfig> {
    return json<WidgetConfig>(await send(`${this.base}/config`, { method: 'GET' }));
  }

  async start(): Promise<StartedConversation> {
    return json<StartedConversation>(await send(`${this.base}/conversations`, { method: 'POST' }));
  }

  async history(session: StoredSession): Promise<MessageView[]> {
    const response = await send(`${this.base}/conversations/${encodeURIComponent(session.conversationId)}/messages`, {
      method: 'GET',
      headers: { [CONVERSATION_TOKEN_HEADER]: session.visitorToken },
    });
    return json<MessageView[]>(response);
  }

  /**
   * Asks a question and streams the answer. Errors found before the stream starts (wrong token, limit reached,
   * invalid question) reject with a {@link WidgetApiError}; once streaming, the server reports failures as a polite
   * answer instead.
   */
  async ask(session: StoredSession, question: string, handlers: AnswerHandlers, signal?: AbortSignal): Promise<void> {
    const response = await send(`${this.base}/conversations/${encodeURIComponent(session.conversationId)}/messages`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Accept: 'text/event-stream',
        [CONVERSATION_TOKEN_HEADER]: session.visitorToken,
      },
      body: JSON.stringify({ question }),
      ...(signal ? { signal } : {}),
    });
    if (response.body === null) {
      throw new WidgetApiError(response.status, null, null);
    }
    let finished = false;
    for await (const event of readServerSentEvents(response.body)) {
      if (event.event === 'delta') {
        handlers.onText((JSON.parse(event.data) as { text: string }).text);
      } else if (event.event === 'done') {
        finished = true;
        handlers.onDone(JSON.parse(event.data) as AnswerDone);
      } else if (event.event === 'forwarded') {
        finished = true;
        handlers.onForwarded();
      }
    }
    if (!finished) {
      // The connection dropped mid-answer (network, proxy timeout)
      throw new WidgetApiError(0, 'STREAM_INTERRUPTED', null);
    }
  }

  /** Asks for a person of the team; the assistant stops answering until someone takes over or the visitor cancels. */
  async requestHandoff(session: StoredSession): Promise<ConversationState> {
    return json<ConversationState>(await send(this.conversation(session, '/handoff'), post(session)));
  }

  async cancelHandoff(session: StoredSession): Promise<ConversationState> {
    return json<ConversationState>(await send(this.conversation(session, '/handoff/cancellation'), post(session)));
  }

  /** Confirms or rejects a change the assistant proposed; resolves with the assistant's reply. */
  async respondToAction(session: StoredSession, runId: string, confirmed: boolean): Promise<MessageView> {
    const path = `/actions/${encodeURIComponent(runId)}/${confirmed ? 'confirmation' : 'cancellation'}`;
    return json<MessageView>(await send(this.conversation(session, path), post(session)));
  }

  /** Says whether an answer helped; saying it again replaces it. */
  async judgeAnswer(session: StoredSession, messageId: string, helpful: boolean): Promise<void> {
    await send(this.conversation(session, `/messages/${encodeURIComponent(messageId)}/feedback`), put(session, { helpful }));
  }

  /** Rates the conversation from 1 to 5; rating again replaces it. */
  async rate(session: StoredSession, score: number): Promise<void> {
    await send(this.conversation(session, '/rating'), put(session, { score }));
  }

  /**
   * Who answers and what the team writes, pushed by the server: the current state first, then every change. Ends
   * when the server closes the stream (every 30 minutes) or the signal aborts.
   */
  async *events(session: StoredSession, signal: AbortSignal): AsyncGenerator<LiveEvent> {
    const response = await send(this.conversation(session, '/events'), {
      method: 'GET',
      headers: { Accept: 'text/event-stream', [CONVERSATION_TOKEN_HEADER]: session.visitorToken },
      signal,
    });
    if (response.body === null) {
      return;
    }
    for await (const event of readServerSentEvents(response.body)) {
      if (event.event === 'state') {
        yield { type: 'state', state: JSON.parse(event.data) as ConversationState };
      } else if (event.event === 'message') {
        yield { type: 'message', message: JSON.parse(event.data) as MessageView };
      }
    }
  }

  private conversation(session: StoredSession, path: string): string {
    return `${this.base}/conversations/${encodeURIComponent(session.conversationId)}${path}`;
  }
}

function put(session: StoredSession, body: unknown): RequestInit {
  return {
    method: 'PUT',
    headers: { [CONVERSATION_TOKEN_HEADER]: session.visitorToken, 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  };
}

function post(session: StoredSession): RequestInit {
  return { method: 'POST', headers: { [CONVERSATION_TOKEN_HEADER]: session.visitorToken } };
}

async function send(url: string, init: RequestInit): Promise<Response> {
  // Anonymous on purpose: the host site's cookies must never reach the API
  const response = await fetch(url, { ...init, credentials: 'omit', cache: 'no-store' });
  if (!response.ok) {
    throw await toError(response);
  }
  return response;
}

async function json<T>(response: Response): Promise<T> {
  return (await response.json()) as T;
}

async function toError(response: Response): Promise<WidgetApiError> {
  let code: string | null = null;
  try {
    const body = (await response.json()) as { code?: unknown };
    code = typeof body.code === 'string' ? body.code : null;
  } catch {
    // Not a Problem Details body (e.g. a proxy error page)
  }
  const retryAfter = Number.parseInt(response.headers.get('Retry-After') ?? '', 10);
  return new WidgetApiError(response.status, code, Number.isFinite(retryAfter) ? retryAfter : null);
}
