import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { WidgetApiError, type AnswerHandlers, type LiveEvent, type WidgetApi } from './api';
import { SessionStore } from './session-store';
import { strings } from './strings';
import type { MessageView, PendingAction, StoredSession } from './types';
import { ChatWidget } from './widget';

const KEY = 'test-key';
// translate() changes the shared texts; each test starts from the Spanish ones
const SPANISH = { ...strings, notices: { ...strings.notices } };

function fakeApi(overrides: Partial<Record<keyof WidgetApi, unknown>> = {}) {
  let started = 0;
  const defaults = {
    config: vi.fn(async () => ({ botName: 'Ana', welcomeMessage: '¡Hola! ¿En qué te ayudo?' })),
    start: vi.fn(async () => {
      started += 1;
      return { conversationId: `conv-${started}`, visitorToken: `token-${started}`, startedAt: '2026-01-01T00:00:00Z' };
    }),
    history: vi.fn(async (): Promise<MessageView[]> => []),
    ask: vi.fn(async (_session: StoredSession, _question: string, handlers: AnswerHandlers) => {
      handlers.onText('Llega en 3 días ');
      handlers.onText('[1].');
      handlers.onDone({
        messageId: 'm1',
        outcome: 'ANSWERED',
        citations: [{ number: 1, documentId: 'd1', documentTitle: 'Envíos', chunkId: 'c1', excerpt: 'Los envíos tardan 3 días' }],
      });
    }),
    // The live stream: the state of the conversation, then whatever the test pushes, until the widget stops listening
    events: vi.fn(async function* (session: StoredSession, signal: AbortSignal): AsyncGenerator<LiveEvent> {
      yield { type: 'state', state: { conversationId: session.conversationId, status: 'OPEN', handling: 'ASSISTANT', agentName: null } };
      while (!signal.aborted) {
        if (ending) {
          ending = false;
          return;
        }
        if (queued.length > 0) {
          yield queued.shift()!;
          continue;
        }
        await new Promise<void>((resolve) => {
          wake = resolve;
          signal.addEventListener('abort', () => resolve());
        });
      }
    }),
    requestHandoff: vi.fn(async (session: StoredSession) => ({
      conversationId: session.conversationId,
      status: 'OPEN' as const,
      handling: 'WAITING' as const,
      agentName: null,
    })),
    respondToAction: vi.fn(async (_session: StoredSession, _runId: string, confirmed: boolean): Promise<MessageView> => ({
      ...stored('r1', 'ASSISTANT', confirmed ? 'Tu pedido 123456 fue cancelado.' : 'De acuerdo, no hice ningún cambio.'),
    })),
    judgeAnswer: vi.fn(async (_session: StoredSession, _messageId: string, _helpful: boolean) => {}),
    rate: vi.fn(async (_session: StoredSession, _score: number) => {}),
    cancelHandoff: vi.fn(async (session: StoredSession) => ({
      conversationId: session.conversationId,
      status: 'OPEN' as const,
      handling: 'ASSISTANT' as const,
      agentName: null,
    })),
  };
  return { ...defaults, ...overrides } as typeof defaults;
}

const queued: LiveEvent[] = [];
let wake: () => void = () => {};
let ending = false;

function pushLive(event: LiveEvent): void {
  queued.push(event);
  wake();
}

/** The server ends the open stream, as it does every 30 minutes or when it loses track of events. */
function endLive(): void {
  ending = true;
  wake();
}

function stored(id: string, author: MessageView['author'], content: string, authorName: string | null = null): MessageView {
  return { id, author, content, outcome: author === 'ASSISTANT' ? 'ANSWERED' : null, citations: null, authorName, createdAt: '' };
}

function mount(api: ReturnType<typeof fakeApi>) {
  const widget = new ChatWidget(
    { apiUrl: 'http://api.test', widgetKey: KEY },
    { api: api as unknown as WidgetApi, store: new SessionStore(KEY) },
  );
  widget.mount();
  const root = widget.host.shadowRoot!;
  return {
    widget,
    root,
    input: root.querySelector('textarea')!,
    messages: () => [...root.querySelectorAll('.msg')].map((node) => node.querySelector('.body')?.textContent ?? node.textContent),
    notices: () => [...root.querySelectorAll('.notice')].map((node) => node.textContent),
  };
}

async function ask(view: ReturnType<typeof mount>, question: string) {
  view.input.value = question;
  await view.widget.submit();
}

beforeEach(() => {
  localStorage.clear();
  sessionStorage.clear();
  queued.length = 0;
  ending = false;
});

afterEach(() => {
  document.body.replaceChildren();
  Object.assign(strings, { ...SPANISH, notices: { ...SPANISH.notices } });
});

describe('ChatWidget', () => {
  it('shows the greeting when opened and keeps its markup inside a shadow root', async () => {
    const view = mount(fakeApi());
    await view.widget.open();
    expect(view.root.querySelector('.title')?.textContent).toBe('Ana');
    expect(view.messages()).toEqual(['¡Hola! ¿En qué te ayudo?']);
    expect(document.querySelector('.panel')).toBeNull();
  });

  it('tells screen readers who wrote each message and what the message box has left', async () => {
    const view = mount(fakeApi());
    await view.widget.open();
    await ask(view, '¿Cuánto tarda el envío?');
    const speakers = [...view.root.querySelectorAll('.msg > .sr-only')].map((label) => label.textContent);
    expect(speakers).toEqual(['Ana: ', 'Tú: ', 'Ana: ']);
    const counter = view.root.getElementById(view.input.getAttribute('aria-describedby')!)!;
    view.input.value = 'x'.repeat(1900);
    view.input.dispatchEvent(new Event('input'));
    expect(counter.hidden).toBe(false);
    expect(counter.textContent).toBe('100 caracteres restantes');
  });

  it('keeps the focus in the chat when the rating it was on goes away', async () => {
    const view = mount(fakeApi());
    await view.widget.open();
    await ask(view, '¿Cuánto tarda el envío?');
    const star = view.root.querySelectorAll<HTMLButtonElement>('.rating button')[3]!;
    star.focus();
    star.click();
    // As a browser does with a disabled button; jsdom leaves the focus on it
    expect(star.disabled).toBe(true);
    star.blur();
    await vi.waitFor(() => expect(view.root.querySelector('.rating')?.textContent).toBe('¡Gracias por tu opinión!'));
    expect(view.root.activeElement).toBe(view.input);
  });

  it('writes dark text on a light brand colour', () => {
    vi.stubGlobal('CSS', { supports: () => true });
    try {
      const light = new ChatWidget({ apiUrl: 'http://api.test', widgetKey: KEY, primaryColor: '#facc15' }, { api: fakeApi() as unknown as WidgetApi });
      light.mount();
      expect(light.host.style.getPropertyValue('--sc-on-primary')).toBe('#16161d');
      const dark = new ChatWidget({ apiUrl: 'http://api.test', widgetKey: KEY, primaryColor: '#1e3a8a' }, { api: fakeApi() as unknown as WidgetApi });
      dark.mount();
      expect(dark.host.style.getPropertyValue('--sc-on-primary')).toBe('');
    } finally {
      vi.unstubAllGlobals();
    }
  });

  it('starts a conversation on the first question and streams the answer with its sources', async () => {
    const api = fakeApi();
    const view = mount(api);
    await view.widget.open();
    await ask(view, '¿Cuánto tarda el envío?');

    expect(api.start).toHaveBeenCalledTimes(1);
    expect(view.messages().slice(1)).toEqual(['¿Cuánto tarda el envío?', 'Llega en 3 días 1.']);
    const cite = view.root.querySelector<HTMLButtonElement>('.cite')!;
    expect(cite.getAttribute('aria-label')).toBe('Fuente 1');
    cite.click();
    expect(view.root.querySelector('details')?.open).toBe(true);
    expect(JSON.parse(localStorage.getItem(`supportchat:${KEY}:conversation`)!)).toEqual({
      conversationId: 'conv-1',
      visitorToken: 'token-1',
    });

    await ask(view, 'Otra pregunta');
    expect(api.start).toHaveBeenCalledTimes(1);
  });

  it('never interprets server text as HTML', async () => {
    const api = fakeApi({
      config: vi.fn(async () => ({ botName: '<img src=x onerror=alert(1)>', welcomeMessage: '<b>hola</b>' })),
    });
    const view = mount(api);
    await view.widget.open();
    expect(view.root.querySelector('img')).toBeNull();
    expect(view.root.querySelector('b')).toBeNull();
    expect(view.messages()).toEqual(['<b>hola</b>']);
  });

  it('starts a new conversation once when the stored one has expired', async () => {
    localStorage.setItem(`supportchat:${KEY}:conversation`, JSON.stringify({ conversationId: 'old', visitorToken: 'old' }));
    const api = fakeApi();
    const expired = new WidgetApiError(401, 'INVALID_CONVERSATION_TOKEN', null);
    const answer = api.ask.getMockImplementation()!;
    api.ask.mockImplementation(async (session, question, handlers) => {
      if (session.conversationId === 'old') {
        throw expired;
      }
      return answer(session, question, handlers);
    });
    api.history.mockRejectedValue(expired);
    const view = mount(api);
    await view.widget.open();
    await ask(view, 'Hola');

    expect(api.start).toHaveBeenCalledTimes(1);
    expect(view.notices()).toEqual([]);
    expect(view.messages().at(-1)).toBe('Llega en 3 días 1.');
  });

  it('gives the question back when the conversation reached its limit', async () => {
    const api = fakeApi({
      ask: vi.fn(async () => {
        throw new WidgetApiError(422, 'QUESTION_LIMIT_REACHED', null);
      }),
    });
    const view = mount(api);
    await view.widget.open();
    await ask(view, 'Pregunta 41');

    expect(view.input.value).toBe('Pregunta 41');
    expect(view.messages()).toHaveLength(1);
    expect(view.notices()).toEqual(['Esta conversación terminó. Escribe de nuevo para empezar otra.']);
    expect(localStorage.getItem(`supportchat:${KEY}:conversation`)).toBeNull();
  });

  it('redraws the stored conversation after a reload', async () => {
    localStorage.setItem(`supportchat:${KEY}:conversation`, JSON.stringify({ conversationId: 'c', visitorToken: 't' }));
    const api = fakeApi({
      history: vi.fn(async (): Promise<MessageView[]> => [
        { id: '1', author: 'VISITOR', content: '¿Horario?', outcome: null, citations: null, authorName: null, createdAt: '' },
        { id: '2', author: 'ASSISTANT', content: 'De 9 a 6.', outcome: 'ANSWERED', citations: [], authorName: null, createdAt: '' },
      ]),
    });
    const view = mount(api);
    await view.widget.open();
    expect(view.messages().slice(1)).toEqual(['¿Horario?', 'De 9 a 6.']);
  });

  it('asks for a person, keeps messages for the team and shows the reply that arrives live', async () => {
    const api = fakeApi({
      ask: vi.fn(async (_session: StoredSession, _question: string, handlers: AnswerHandlers) => handlers.onForwarded()),
    });
    const view = mount(api);
    await view.widget.open();
    const handoff = view.root.querySelector<HTMLButtonElement>('.handoff-button')!;
    expect(handoff.textContent).toBe('Hablar con una persona');

    handoff.click();
    await vi.waitFor(() => expect(api.requestHandoff).toHaveBeenCalled());
    // No conversation yet: asking for a person starts one
    expect(api.start).toHaveBeenCalledTimes(1);
    await vi.waitFor(() => expect(handoff.textContent).toBe('Volver al asistente'));
    expect(view.root.querySelector('.handoff-status')?.textContent).toContain('una persona del equipo');

    await ask(view, '¿Me ayudas con mi pedido?');
    // The visitor's message stays, with no answer bubble from the assistant
    expect(view.messages().slice(1)).toEqual(['¿Me ayudas con mi pedido?']);

    pushLive({ type: 'state', state: { conversationId: 'conv-1', status: 'OPEN', handling: 'AGENT', agentName: 'Laura' } });
    pushLive({
      type: 'message',
      message: { id: 'a1', author: 'AGENT', content: 'Hola, soy Laura.', outcome: null, citations: null, authorName: 'Laura', createdAt: '' },
    });
    await vi.waitFor(() => expect(view.messages()).toContain('Hola, soy Laura.'));
    expect(view.root.querySelector('.handoff-status')?.textContent).toBe('Te atiende Laura');
    expect(handoff.hidden).toBe(true);
  });

  it('shows what the team wrote while the live stream was reconnecting, without repeating anything', async () => {
    const history: MessageView[] = [];
    const api = fakeApi({ history: vi.fn(async () => [...history]) });
    const view = mount(api);
    await view.widget.open();
    await ask(view, '¿Cuánto tarda el envío?');
    // Each stream catches up once it is open
    await vi.waitFor(() => expect(api.history).toHaveBeenCalledTimes(1));

    history.push(
      stored('q1', 'VISITOR', '¿Cuánto tarda el envío?'),
      stored('m1', 'ASSISTANT', 'Llega en 3 días [1].'),
      stored('s1', 'SYSTEM', 'AGENT_JOINED', 'Laura'),
      stored('a1', 'AGENT', 'Te escribo yo, soy Laura.', 'Laura'),
    );
    endLive();

    // The widget waits a little before reopening a stream that ended at once
    await vi.waitFor(() => expect(view.messages()).toContain('Te escribo yo, soy Laura.'), { timeout: 5_000 });
    expect(api.events).toHaveBeenCalledTimes(2);
    expect(view.root.querySelector('.event')?.textContent).toContain('Laura');
    expect(view.messages().filter((text) => text === '¿Cuánto tarda el envío?')).toHaveLength(1);
    // The welcome and the one answer
    expect(view.root.querySelectorAll('.msg.assistant .body')).toHaveLength(2);

    // A reply pushed live after catching up is not drawn twice by the next catch-up
    pushLive({ type: 'message', message: stored('a2', 'AGENT', '¿Algo más?', 'Laura') });
    await vi.waitFor(() => expect(view.messages()).toContain('¿Algo más?'));
    expect(view.messages().filter((text) => text === 'Te escribo yo, soy Laura.')).toHaveLength(1);
  });

  it('forgets the conversation when the team closes it', async () => {
    const view = mount(fakeApi());
    await view.widget.open();
    await ask(view, '¿Cuánto tarda el envío?');
    expect(localStorage.getItem(`supportchat:${KEY}:conversation`)).not.toBeNull();

    pushLive({
      type: 'message',
      message: { id: 's1', author: 'SYSTEM', content: 'CONVERSATION_CLOSED', outcome: null, citations: null, authorName: null, createdAt: '' },
    });
    await vi.waitFor(() => expect(view.root.querySelector('.event')?.textContent).toContain('La conversación terminó'));
    pushLive({ type: 'state', state: { conversationId: 'conv-1', status: 'CLOSED', handling: 'ASSISTANT', agentName: null } });
    await vi.waitFor(() => expect(localStorage.getItem(`supportchat:${KEY}:conversation`)).toBeNull());
  });

  it('tells the visitor a person takes over when the assistant is paused', async () => {
    const view = mount(fakeApi());
    await view.widget.open();
    await ask(view, '¿Cuánto tarda el envío?');

    pushLive({
      type: 'message',
      message: { id: 's1', author: 'SYSTEM', content: 'ASSISTANT_PAUSED', outcome: null, citations: null, authorName: null, createdAt: '' },
    });
    await vi.waitFor(() => expect(view.root.querySelector('.event')?.textContent).toContain('Una persona del equipo'));
  });

  it('disables the chat when the widget key is unknown or the site is not allowed', async () => {
    const view = mount(fakeApi({ config: vi.fn(async () => Promise.reject(new TypeError('Failed to fetch'))) }));
    await view.widget.open();
    expect(view.input.disabled).toBe(true);
    expect(view.notices()).toEqual(['El chat no está disponible en este momento.']);
  });

  it('speaks the language of the browser when the backend sends the texts for it', async () => {
    const api = fakeApi({
      config: vi.fn(async () => ({
        botName: 'Ana',
        welcomeMessage: 'Hi! How can I help?',
        texts: {
          open: 'Open help chat',
          inputPlaceholder: 'Type your question…',
          send: 'Send',
          talkToPerson: 'Talk to a person',
          rateOption: '{} out of 5',
          'notices.AGENT_JOINED': '{} joined the conversation.',
          unknownKey: 'ignored',
        },
      })),
    });
    const view = mount(api);
    await view.widget.open();

    expect(view.input.placeholder).toBe('Type your question…');
    expect(view.root.querySelector('.launcher')?.getAttribute('aria-label')).toBe('Open help chat');
    expect(view.root.querySelector('.send')?.getAttribute('aria-label')).toBe('Send');
    expect(view.root.querySelector('.handoff-button')?.textContent).toBe('Talk to a person');

    await ask(view, 'How long does shipping take?');
    expect(view.root.querySelector('.rating button')?.getAttribute('aria-label')).toBe('1 out of 5');
    pushLive({ type: 'message', message: stored('s1', 'SYSTEM', 'AGENT_JOINED', 'Laura') });
    await vi.waitFor(() => expect(view.root.querySelector('.event')?.textContent).toBe('Laura joined the conversation.'));
  });

  describe('customer feedback', () => {
    const verdicts = (root: ShadowRoot) => [...root.querySelectorAll<HTMLButtonElement>('.verdict button')];
    const stars = (root: ShadowRoot) => [...root.querySelectorAll<HTMLButtonElement>('.rating button')];

    it('lets the visitor say whether an answer helped and change their mind', async () => {
      const api = fakeApi();
      const view = mount(api);
      await view.widget.open();
      await ask(view, '¿Cuánto tarda el envío?');

      const [helpful, notHelpful] = verdicts(view.root) as [HTMLButtonElement, HTMLButtonElement];
      expect(helpful.getAttribute('aria-label')).toBe('Me ayudó');
      expect(helpful.getAttribute('aria-pressed')).toBe('false');
      notHelpful.click();
      await vi.waitFor(() => expect(notHelpful.getAttribute('aria-pressed')).toBe('true'));
      expect(api.judgeAnswer).toHaveBeenCalledWith({ conversationId: 'conv-1', visitorToken: 'token-1' }, 'm1', false);

      helpful.click();
      await vi.waitFor(() => expect(helpful.getAttribute('aria-pressed')).toBe('true'));
      expect(notHelpful.getAttribute('aria-pressed')).toBe('false');
    });

    it('shows the verdict already given after a reload, and none on answers the assistant did not know', async () => {
      localStorage.setItem(`supportchat:${KEY}:conversation`, JSON.stringify({ conversationId: 'c', visitorToken: 't' }));
      const api = fakeApi({
        history: vi.fn(async (): Promise<MessageView[]> => [
          stored('1', 'VISITOR', '¿Horario?'),
          { ...stored('2', 'ASSISTANT', 'De 9 a 6.'), helpful: true },
          stored('3', 'VISITOR', '¿Venden boletos?'),
          { ...stored('4', 'ASSISTANT', 'No tengo esa información.'), outcome: 'NO_ANSWER' },
        ]),
      });
      const view = mount(api);
      await view.widget.open();

      const buttons = verdicts(view.root);
      expect(buttons).toHaveLength(2);
      expect(buttons[0]!.getAttribute('aria-pressed')).toBe('true');
    });

    it('asks for a rating once, also after the team closes the conversation', async () => {
      const api = fakeApi();
      const view = mount(api);
      await view.widget.open();
      const rating = view.root.querySelector<HTMLElement>('.rating')!;
      expect(rating.hidden).toBe(true);

      await ask(view, '¿Cuánto tarda el envío?');
      expect(rating.hidden).toBe(false);
      expect(stars(view.root).map((star) => star.getAttribute('aria-label'))).toEqual([
        '1 de 5', '2 de 5', '3 de 5', '4 de 5', '5 de 5',
      ]);

      pushLive({ type: 'state', state: { conversationId: 'conv-1', status: 'CLOSED', handling: 'ASSISTANT', agentName: null } });
      await vi.waitFor(() => expect(localStorage.getItem(`supportchat:${KEY}:conversation`)).toBeNull());
      stars(view.root)[4]!.click();
      await vi.waitFor(() => expect(rating.textContent).toBe('¡Gracias por tu opinión!'));
      expect(api.rate).toHaveBeenCalledWith({ conversationId: 'conv-1', visitorToken: 'token-1' }, 5);

      // A new conversation can be rated on its own
      await ask(view, '¿Y a Monterrey?');
      expect(rating.hidden).toBe(false);
      expect(stars(view.root)).toHaveLength(5);
    });

    it('does not ask again for a conversation already rated, and reports a rating that failed', async () => {
      localStorage.setItem(`supportchat:${KEY}:conversation`, JSON.stringify({ conversationId: 'c', visitorToken: 't' }));
      localStorage.setItem(`supportchat:${KEY}:rated`, 'c');
      const history = vi.fn(async (): Promise<MessageView[]> => [stored('2', 'ASSISTANT', 'De 9 a 6.')]);
      const rated = mount(fakeApi({ history }));
      await rated.widget.open();
      expect(rated.root.querySelector<HTMLElement>('.rating')!.hidden).toBe(true);
      document.body.replaceChildren();

      localStorage.removeItem(`supportchat:${KEY}:rated`);
      const api = fakeApi({ history, rate: vi.fn(async () => Promise.reject(new TypeError('Failed to fetch'))) });
      const view = mount(api);
      await view.widget.open();
      stars(view.root)[2]!.click();
      await vi.waitFor(() => expect(view.notices()).toEqual(['No pudimos guardar tu opinión. Inténtalo de nuevo.']));
      expect(stars(view.root).every((star) => !star.disabled)).toBe(true);
    });
  });

  describe('changes the assistant proposes', () => {
    const pending: PendingAction = {
      runId: 'run-1',
      title: 'Cancelar un pedido',
      arguments: { order_number: '123456' },
      expiresAt: '2030-01-01T00:00:00Z',
    };
    const proposing = () =>
      vi.fn(async (_session: StoredSession, _question: string, handlers: AnswerHandlers) => {
        handlers.onText('Para continuar, confirma la acción con el botón.');
        handlers.onDone({ messageId: 'm1', outcome: 'ANSWERED', citations: [], pendingAction: pending });
      });
    const buttons = (root: ShadowRoot) =>
      [...root.querySelectorAll<HTMLButtonElement>('.action button')].map((button) => button.textContent);
    const press = (root: ShadowRoot, label: string) =>
      [...root.querySelectorAll<HTMLButtonElement>('.action button')].find((button) => button.textContent === label)!.click();

    it('runs a change only once the visitor confirms it', async () => {
      const api = fakeApi({ ask: proposing() });
      const view = mount(api);
      await view.widget.open();
      await ask(view, 'Quiero cancelar mi pedido 123456');

      const card = view.root.querySelector('.action')!;
      expect(card.getAttribute('aria-label')).toBe('Cancelar un pedido');
      expect(card.textContent).toContain('order_number: 123456');
      expect(buttons(view.root)).toEqual(['Confirmar', 'Cancelar']);
      expect(api.respondToAction).not.toHaveBeenCalled();

      press(view.root, 'Confirmar');
      await vi.waitFor(() => expect(view.messages()).toContain('Tu pedido 123456 fue cancelado.'));
      expect(api.respondToAction).toHaveBeenCalledWith({ conversationId: 'conv-1', visitorToken: 'token-1' }, 'run-1', true);
      expect(buttons(view.root)).toEqual([]);
    });

    it('lets the visitor say no', async () => {
      const api = fakeApi({ ask: proposing() });
      const view = mount(api);
      await view.widget.open();
      await ask(view, 'Quiero cancelar mi pedido 123456');

      press(view.root, 'Cancelar');
      await vi.waitFor(() => expect(view.messages()).toContain('De acuerdo, no hice ningún cambio.'));
      expect(api.respondToAction).toHaveBeenCalledWith(expect.anything(), 'run-1', false);
    });

    it('draws the buttons again after a reload while the change still waits', async () => {
      localStorage.setItem(`supportchat:${KEY}:conversation`, JSON.stringify({ conversationId: 'c1', visitorToken: 't1' }));
      const view = mount(
        fakeApi({
          history: vi.fn(async (): Promise<MessageView[]> => [
            stored('v1', 'VISITOR', 'Quiero cancelar mi pedido 123456'),
            { ...stored('a1', 'ASSISTANT', 'Para continuar, confirma la acción con el botón.'), pendingAction: pending },
          ]),
        }),
      );
      await view.widget.open();

      expect(buttons(view.root)).toEqual(['Confirmar', 'Cancelar']);
    });

    it('says so when the change was already answered or expired', async () => {
      const view = mount(
        fakeApi({
          ask: proposing(),
          respondToAction: vi.fn(async () => Promise.reject(new WidgetApiError(409, 'ACTION_ALREADY_HANDLED', null))),
        }),
      );
      await view.widget.open();
      await ask(view, 'Quiero cancelar mi pedido 123456');

      press(view.root, 'Confirmar');
      await vi.waitFor(() => expect(view.notices()).toEqual(['Esta acción ya no está disponible.']));
      expect(buttons(view.root)).toEqual([]);
    });

    it('keeps the buttons when the answer could not be sent', async () => {
      const view = mount(
        fakeApi({ ask: proposing(), respondToAction: vi.fn(async () => Promise.reject(new TypeError('Failed to fetch'))) }),
      );
      await view.widget.open();
      await ask(view, 'Quiero cancelar mi pedido 123456');

      press(view.root, 'Confirmar');
      await vi.waitFor(() => expect(view.notices()).toEqual(['No pudimos enviar tu respuesta. Inténtalo de nuevo.']));
      expect([...view.root.querySelectorAll<HTMLButtonElement>('.action button')].every((button) => !button.disabled)).toBe(true);
    });
  });
});
