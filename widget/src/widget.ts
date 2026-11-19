import { WidgetApi, WidgetApiError } from './api';
import { citedSources, splitCitations } from './citations';
import { SessionStore } from './session-store';
import { strings, translate } from './strings';
import { styles } from './styles';
import type {
  AnswerDone,
  Citation,
  ConversationState,
  Handling,
  MessageView,
  PendingAction,
  StoredSession,
} from './types';

// Same limit as the backend (Message.MAX_QUESTION_LENGTH); checked here only to answer faster
export const MAX_QUESTION_LENGTH = 2000;
const COUNTER_THRESHOLD = 200;

// Static markup only: user and server text is always inserted as text nodes, never as HTML
const CHAT_ICON =
  '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"/></svg>';
const CLOSE_ICON =
  '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" aria-hidden="true"><path d="M18 6 6 18M6 6l12 12"/></svg>';
const SEND_ICON =
  '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="m22 2-7 20-4-9-9-4z"/><path d="M22 2 11 13"/></svg>';

export interface WidgetOptions {
  apiUrl: string;
  widgetKey: string;
  position?: 'left' | 'right';
  primaryColor?: string;
}

export interface WidgetDependencies {
  api?: WidgetApi;
  store?: SessionStore;
}

export class ChatWidget {
  readonly host: HTMLElement;
  private readonly root: ShadowRoot;
  private readonly api: WidgetApi;
  private readonly store: SessionStore;
  private readonly launcher: HTMLButtonElement;
  private readonly panel: HTMLElement;
  private readonly title: HTMLElement;
  private readonly log: HTMLElement;
  private readonly form: HTMLFormElement;
  private readonly input: HTMLTextAreaElement;
  private readonly sendButton: HTMLButtonElement;
  private readonly counter: HTMLElement;
  private readonly handoffStatus: HTMLElement;
  private readonly handoffButton: HTMLButtonElement;
  private readonly rating: HTMLElement;
  private session: StoredSession | null;
  private initialization: Promise<void> | null = null;
  private available = true;
  private busy = false;
  private handling: Handling = 'ASSISTANT';
  private agentName = '';
  // Who answers, said to screen readers before each answer since only colour and side tell the speakers apart
  private botName = '';
  // Messages already on screen; history and the live stream can both deliver one
  private readonly shown = new Set<string>();
  private live: AbortController | null = null;
  // The conversation the rating bar rates; kept after the team closes it, a natural moment to rate
  private rateTarget: StoredSession | null = null;

  constructor(options: WidgetOptions, dependencies: WidgetDependencies = {}) {
    this.api = dependencies.api ?? new WidgetApi(options.apiUrl, options.widgetKey);
    this.store = dependencies.store ?? new SessionStore(options.widgetKey);
    this.session = this.store.load();

    this.host = document.createElement('div');
    this.host.id = 'supportchat-widget';
    if (options.position === 'left') {
      this.host.dataset.position = 'left';
    }
    if (options.primaryColor && isColor(options.primaryColor)) {
      this.host.style.setProperty('--sc-primary', options.primaryColor);
    }
    this.root = this.host.attachShadow({ mode: 'open' });
    const style = document.createElement('style');
    style.textContent = styles;

    this.panel = element('section', 'panel');
    this.panel.id = 'supportchat-panel';
    this.panel.hidden = true;
    attr(this.panel, 'role', 'dialog');
    attr(this.panel, 'aria-labelledby', 'supportchat-title');

    const header = element('header', 'header');
    this.title = element('h2', 'title');
    this.title.id = 'supportchat-title';
    const closeButton = button('close', strings.close, CLOSE_ICON);
    closeButton.addEventListener('click', () => this.close());
    const avatar = element('span', 'avatar');
    avatar.innerHTML = CHAT_ICON;
    const identity = element('div', 'identity');
    identity.append(this.title, element('p', 'subtitle', strings.subtitle));
    header.append(avatar, identity, closeButton);

    this.log = element('div', 'log');
    attr(this.log, 'role', 'log');
    attr(this.log, 'aria-live', 'polite');

    this.form = document.createElement('form');
    this.form.className = 'composer';
    const label = element('label', 'sr-only', strings.inputLabel) as HTMLLabelElement;
    label.htmlFor = 'supportchat-input';
    this.input = document.createElement('textarea');
    this.input.id = 'supportchat-input';
    this.input.className = 'input';
    this.input.rows = 1;
    this.input.maxLength = MAX_QUESTION_LENGTH;
    this.input.placeholder = strings.inputPlaceholder;
    this.sendButton = button('send', strings.send, SEND_ICON);
    this.sendButton.type = 'submit';
    this.form.append(label, this.input, this.sendButton);
    this.counter = element('div', 'counter');
    this.counter.id = 'supportchat-counter';
    this.counter.hidden = true;
    attr(this.input, 'aria-describedby', this.counter.id);
    const handoff = element('div', 'handoff');
    this.handoffStatus = element('p', 'handoff-status');
    attr(this.handoffStatus, 'role', 'status');
    this.handoffButton = element('button', 'handoff-button', strings.talkToPerson) as HTMLButtonElement;
    this.handoffButton.type = 'button';
    this.handoffButton.addEventListener('click', () => void this.toggleHandoff());
    handoff.append(this.handoffStatus, this.handoffButton);
    this.rating = element('div', 'rating');
    this.rating.hidden = true;
    attr(this.rating, 'role', 'group');
    this.panel.append(header, this.log, this.rating, handoff, this.form, this.counter);

    this.launcher = button('launcher', strings.open, CHAT_ICON);
    attr(this.launcher, 'aria-controls', this.panel.id);
    attr(this.launcher, 'aria-expanded', 'false');
    this.launcher.addEventListener('click', () => (this.panel.hidden ? this.open() : this.close()));

    this.root.append(style, this.panel, this.launcher);
    this.bindComposer();
    this.updateComposer();
    this.updateHandoff();
  }

  mount(parent: HTMLElement = document.body): void {
    parent.append(this.host);
    this.contrastPrimary();
    if (this.store.wasOpen()) {
      void this.open({ focus: false });
    }
  }

  async open({ focus = true }: { focus?: boolean } = {}): Promise<void> {
    this.panel.hidden = false;
    attr(this.launcher, 'aria-expanded', 'true');
    this.store.rememberOpen(true);
    if (focus) {
      this.input.focus();
    }
    this.initialization ??= this.initialize();
    await this.initialization;
  }

  close(): void {
    this.panel.hidden = true;
    attr(this.launcher, 'aria-expanded', 'false');
    this.store.rememberOpen(false);
    this.launcher.focus();
  }

  /** Loads the greeting and, when the visitor already has a conversation, what was said so far. */
  private async initialize(): Promise<void> {
    try {
      const config = await this.api.config();
      if (config.texts) {
        translate(config.texts);
        this.relabel();
      }
      this.title.textContent = config.botName;
      this.botName = config.botName;
      const welcome = new AnswerBubble(this.speaker());
      welcome.append(config.welcomeMessage);
      welcome.finish({ messageId: '', outcome: 'SMALL_TALK', citations: [] });
      this.log.append(welcome.element);
    } catch {
      // Unknown key, or a site the workspace has not allowed (the browser then blocks the response)
      this.available = false;
      this.updateComposer();
      this.notice(strings.unavailable, true);
      return;
    }
    if (this.session === null) {
      return;
    }
    try {
      for (const message of await this.api.history(this.session)) {
        this.renderStored(message);
      }
      this.scrollToEnd();
      this.startLive();
    } catch (error) {
      if (error instanceof WidgetApiError && (error.status === 401 || error.status === 404)) {
        // Expired or deleted: the next question starts a new conversation
        this.forgetSession();
      }
    }
  }

  /**
   * Listens for the team: who answers, and what a person writes. Reconnects when the server ends the stream, waiting
   * longer after each quick failure; stops for good when the conversation is gone. A stream only carries what
   * happens after it opens, so each one starts by catching up with what the team wrote while none was open.
   */
  private startLive(): void {
    if (this.session === null || this.live !== null) {
      return;
    }
    const controller = new AbortController();
    this.live = controller;
    const session = this.session;
    void (async () => {
      let failures = 0;
      while (!controller.signal.aborted) {
        const openedAt = Date.now();
        let caughtUp = false;
        try {
          for await (const event of this.api.events(session, controller.signal)) {
            if (event.type === 'state') {
              this.applyState(event.state);
              // The first state means the server is already pushing: anything older is in the history. Reading
              // the stream waits meanwhile, so newer messages still arrive after the missed ones
              if (!caughtUp && this.session === session) {
                caughtUp = true;
                await this.catchUp(session);
              }
            } else {
              this.renderStored(event.message);
              this.scrollToEnd();
            }
          }
        } catch (error) {
          if (error instanceof WidgetApiError && (error.status === 401 || error.status === 404)) {
            if (this.session === session) {
              this.forgetSession();
            }
            return;
          }
        }
        failures = Date.now() - openedAt < 5_000 ? failures + 1 : 0;
        if (failures > 0) {
          await pause(Math.min(30_000, 1_000 * 2 ** failures), controller.signal);
        }
      }
    })();
  }

  /**
   * Shows the team's replies and notices missed while disconnected. Only those: the visitor's own messages and the
   * assistant's answers reach the screen through the request that sent them, and the visitor's have no id here.
   */
  private async catchUp(session: StoredSession): Promise<void> {
    let missed: MessageView[];
    try {
      missed = (await this.api.history(session)).filter(
        (message) => (message.author === 'AGENT' || message.author === 'SYSTEM') && !this.shown.has(message.id),
      );
    } catch {
      // The stream itself reports an expired conversation; a passing failure is retried on the next reconnection
      return;
    }
    for (const message of missed) {
      this.renderStored(message);
    }
    if (missed.length > 0) {
      this.scrollToEnd();
    }
  }

  private applyState(state: ConversationState): void {
    if (state.status === 'CLOSED') {
      // The next question starts a new conversation
      this.forgetSession();
      return;
    }
    this.handling = state.handling;
    this.agentName = state.agentName ?? '';
    this.updateHandoff();
  }

  /** Words what was drawn before the texts in the visitor's language arrived. */
  private relabel(): void {
    const find = (selector: string) => this.root.querySelector(selector)!;
    attr(this.launcher, 'aria-label', strings.open);
    attr(find('.close'), 'aria-label', strings.close);
    find('.subtitle').textContent = strings.subtitle;
    this.form.querySelector('.sr-only')!.textContent = strings.inputLabel;
    this.input.placeholder = strings.inputPlaceholder;
    attr(this.sendButton, 'aria-label', strings.send);
    this.updateHandoff();
  }

  private updateHandoff(): void {
    const status =
      this.handling === 'WAITING' ? strings.waiting : this.handling === 'AGENT' ? strings.withAgent(this.agentName) : '';
    this.handoffStatus.textContent = status;
    this.handoffStatus.hidden = status === '';
    this.handoffButton.textContent = this.handling === 'WAITING' ? strings.backToAssistant : strings.talkToPerson;
    // A person who already answers is not asked for again; the visitor waits for them to finish
    this.handoffButton.hidden = !this.available || this.handling === 'AGENT';
  }

  private async toggleHandoff(): Promise<void> {
    if (this.handoffButton.disabled) {
      return;
    }
    const release = this.hold([this.handoffButton]);
    try {
      await this.initialization;
      const session = this.session ?? (await this.startConversation());
      this.applyState(
        this.handling === 'WAITING' ? await this.api.cancelHandoff(session) : await this.api.requestHandoff(session),
      );
    } catch (error) {
      if (error instanceof WidgetApiError && error.code === 'CONVERSATION_CLOSED') {
        this.forgetSession();
      }
      this.notice(strings.handoffFailed, true);
    } finally {
      release(false);
    }
  }

  private bindComposer(): void {
    this.form.addEventListener('submit', (event) => {
      event.preventDefault();
      void this.submit();
    });
    this.input.addEventListener('keydown', (event) => {
      // Enter sends, Shift+Enter adds a line; never while an input method is composing a character
      if (event.key === 'Enter' && !event.shiftKey && !event.isComposing) {
        event.preventDefault();
        void this.submit();
      }
    });
    this.input.addEventListener('input', () => {
      this.input.style.height = 'auto';
      this.input.style.height = `${this.input.scrollHeight}px`;
      this.updateComposer();
    });
    this.panel.addEventListener('keydown', (event) => {
      if (event.key === 'Escape') {
        this.close();
      }
    });
  }

  private updateComposer(): void {
    const left = MAX_QUESTION_LENGTH - this.input.value.length;
    this.counter.hidden = left > COUNTER_THRESHOLD;
    this.counter.textContent = strings.characters(left);
    this.input.disabled = !this.available;
    this.sendButton.disabled = !this.available || this.busy || this.input.value.trim() === '';
    this.handoffButton.hidden = !this.available || this.handling === 'AGENT';
  }

  async submit(): Promise<void> {
    const question = this.input.value.trim();
    if (question === '' || this.busy || !this.available) {
      return;
    }
    await this.initialization;
    this.busy = true;
    this.input.value = '';
    this.input.style.height = 'auto';
    this.updateComposer();
    const asked = visitorBubble(question);
    const answer = new AnswerBubble(this.speaker());
    this.log.append(asked, answer.element);
    attr(this.log, 'aria-busy', 'true');
    this.scrollToEnd();
    try {
      await this.askWithRetry(question, answer);
      if (answer.forwarded) {
        // A person answers; their reply arrives through the live stream
        answer.element.remove();
      } else if (answer.done && answer.done.outcome !== 'FAILED') {
        this.addVerdict(answer, answer.done.messageId, null);
        // A reply to "hola" has given no help to rate yet
        if (answer.done.outcome !== 'SMALL_TALK') {
          this.offerRating();
        }
      }
    } catch (error) {
      if (answer.hasText) {
        answer.interrupt();
      } else {
        // Nothing was answered: give the question back so sending it again is one key press
        asked.remove();
        answer.element.remove();
        this.input.value = question;
      }
      this.notice(describe(error), true);
    } finally {
      this.busy = false;
      attr(this.log, 'aria-busy', 'false');
      this.updateComposer();
      this.scrollToEnd();
    }
  }

  private async askWithRetry(question: string, answer: AnswerBubble): Promise<void> {
    let retried = false;
    for (;;) {
      const session = this.session ?? (await this.startConversation());
      try {
        await this.api.ask(session, question, {
          onText: (delta) => {
            answer.append(delta);
            this.scrollToEnd();
          },
          onDone: (done) => {
            this.shown.add(done.messageId);
            answer.finish(done);
            this.renderPendingAction(done.pendingAction);
          },
          onForwarded: () => {
            answer.forwarded = true;
          },
        });
        return;
      } catch (error) {
        if (!(error instanceof WidgetApiError)) {
          throw error;
        }
        if (error.code === 'INVALID_CONVERSATION_TOKEN' || error.code === 'CONVERSATION_NOT_FOUND') {
          // The stored conversation expired; one fresh conversation is enough to tell whether it was only that
          this.forgetSession();
          if (!retried && !answer.hasText) {
            retried = true;
            continue;
          }
        } else if (error.code === 'CONVERSATION_CLOSED' || error.code === 'QUESTION_LIMIT_REACHED') {
          this.forgetSession();
        }
        throw error;
      }
    }
  }

  private async startConversation(): Promise<StoredSession> {
    const started = await this.api.start();
    this.session = { conversationId: started.conversationId, visitorToken: started.visitorToken };
    this.rateTarget = null;
    this.rating.hidden = true;
    this.store.save(this.session);
    this.startLive();
    return this.session;
  }

  private forgetSession(): void {
    this.session = null;
    this.store.clear();
    this.live?.abort();
    this.live = null;
    this.handling = 'ASSISTANT';
    this.updateHandoff();
  }

  private renderStored(message: MessageView): void {
    if (this.shown.has(message.id)) {
      return;
    }
    this.shown.add(message.id);
    if (message.author === 'VISITOR') {
      this.log.append(visitorBubble(message.content));
      return;
    }
    if (message.author === 'SYSTEM') {
      const notice = strings.notices[message.content];
      if (notice) {
        this.log.append(element('div', 'event', notice(message.authorName ?? strings.team)));
      }
      return;
    }
    if (message.author === 'AGENT') {
      const bubble = element('div', 'msg agent');
      bubble.append(element('div', 'author', message.authorName ?? strings.team), element('div', 'body', message.content));
      this.log.append(bubble);
      this.offerRating();
      return;
    }
    const answer = new AnswerBubble(this.speaker());
    answer.append(message.content);
    const outcome = message.outcome ?? 'ANSWERED';
    answer.finish({ messageId: message.id, outcome, citations: message.citations ?? [] });
    this.log.append(answer.element);
    if (outcome !== 'FAILED') {
      this.addVerdict(answer, message.id, message.helpful ?? null);
      if (outcome !== 'SMALL_TALK') {
        this.offerRating();
      }
    }
    this.renderPendingAction(message.pendingAction);
  }

  /** Lets the visitor say whether an answer helped; saying it again changes it. */
  private addVerdict(answer: AnswerBubble, messageId: string, helpful: boolean | null): void {
    const session = this.session;
    if (session === null || answer.done?.outcome !== 'ANSWERED') {
      return;
    }
    const verdict = element('div', 'verdict');
    const choices = [true, false].map((value) => {
      const choice = element('button', '', value ? '\u{1F44D}' : '\u{1F44E}') as HTMLButtonElement;
      choice.type = 'button';
      attr(choice, 'aria-label', value ? strings.helpful : strings.notHelpful);
      attr(choice, 'aria-pressed', String(helpful === value));
      choice.addEventListener('click', () => void judge(value));
      return choice;
    });
    const judge = async (value: boolean): Promise<void> => {
      const release = this.hold(choices);
      try {
        await this.api.judgeAnswer(session, messageId, value);
        choices.forEach((choice, index) => attr(choice, 'aria-pressed', String((index === 0) === value)));
      } catch {
        this.notice(strings.feedbackFailed, true);
        this.scrollToEnd();
      } finally {
        release(false);
      }
    };
    verdict.append(...choices);
    answer.element.append(verdict);
  }

  /** Asks for a 1 to 5 rating once the conversation has an answer, unless the visitor already gave one. */
  private offerRating(): void {
    const target = this.session ?? this.rateTarget;
    if (target === null || this.store.rated(target.conversationId)) {
      return;
    }
    if (this.rateTarget?.conversationId === target.conversationId && !this.rating.hidden) {
      return;
    }
    this.rateTarget = target;
    attr(this.rating, 'aria-label', strings.rateQuestion);
    const stars = [1, 2, 3, 4, 5].map((score) => {
      const star = element('button', '', '\u2605') as HTMLButtonElement;
      star.type = 'button';
      attr(star, 'aria-label', strings.rateOption(score));
      star.addEventListener('click', () => void this.rate(score, stars));
      return star;
    });
    this.rating.replaceChildren(element('span', '', strings.rateQuestion), ...stars);
    this.rating.hidden = false;
  }

  private async rate(score: number, stars: HTMLButtonElement[]): Promise<void> {
    const target = this.rateTarget;
    if (target === null) {
      return;
    }
    const release = this.hold(stars);
    try {
      await this.api.rate(target, score);
      this.store.markRated(target.conversationId);
      this.rating.replaceChildren(element('span', '', strings.thanks));
      release(true);
    } catch {
      release(false);
      this.notice(strings.feedbackFailed, true);
      this.scrollToEnd();
    }
  }

  /** What the assistant is about to change, with the buttons that confirm or reject it. */
  private renderPendingAction(pending: PendingAction | null | undefined): void {
    if (!pending) {
      return;
    }
    const card = element('div', 'action');
    attr(card, 'role', 'group');
    attr(card, 'aria-label', pending.title);
    const details = document.createElement('ul');
    for (const [name, value] of Object.entries(pending.arguments)) {
      details.append(element('li', '', `${name}: ${String(value)}`));
    }
    const buttons = element('div', 'action-buttons');
    const choices = [true, false].map((confirmed) => {
      const choice = element('button', confirmed ? 'handoff-button confirm' : 'handoff-button',
        confirmed ? strings.confirm : strings.cancel) as HTMLButtonElement;
      choice.type = 'button';
      choice.addEventListener('click', () => void respond(confirmed));
      return choice;
    });
    const respond = async (confirmed: boolean): Promise<void> => {
      const session = this.session;
      if (session === null) {
        return;
      }
      const release = this.hold(choices);
      try {
        const reply = await this.api.respondToAction(session, pending.runId, confirmed);
        buttons.remove();
        release(true);
        this.renderStored(reply);
      } catch (error) {
        if (error instanceof WidgetApiError && (error.status === 404 || error.status === 409)) {
          // Already answered (in another tab), expired, or the conversation ended
          buttons.remove();
          release(true);
          this.notice(strings.actionGone, true);
        } else {
          release(false);
          this.notice(strings.actionFailed, true);
        }
      }
      this.scrollToEnd();
    };
    buttons.append(...choices);
    card.append(element('strong', '', pending.title), details, buttons);
    this.log.append(card);
  }

  private speaker(): string {
    return this.botName || strings.assistant;
  }

  /**
   * Disables buttons while their request runs. The browser takes the focus off a disabled button, so where it was is
   * noted first: it comes back when they are enabled again, or goes on to the message box when they are gone.
   *
   * @returns what to call once the request ends, saying whether the buttons were removed
   */
  private hold(buttons: HTMLButtonElement[]): (gone: boolean) => void {
    const focused = buttons.find((button) => button === this.root.activeElement);
    buttons.forEach((button) => (button.disabled = true));
    return (gone) => {
      buttons.forEach((button) => (button.disabled = false));
      if (focused) {
        (gone ? this.input : focused).focus();
      }
    };
  }

  /**
   * Picks dark or light text for the business's colour, which the visitor's messages and the buttons are painted
   * with, so it always reads: white on a yellow brand is unreadable.
   */
  private contrastPrimary(): void {
    const primary = this.host.style.getPropertyValue('--sc-primary');
    if (!primary) {
      return;
    }
    // The browser turns any colour syntax (a name, hex, hsl) into rgb() for an element on the page
    const probe = document.createElement('i');
    probe.style.color = primary;
    this.root.append(probe);
    const [r = 0, g = 0, b = 0] = getComputedStyle(probe).color.match(/\d+/g)?.map(Number) ?? [];
    probe.remove();
    // WCAG relative luminance (the curve's linear toe left out): above 0.179 near-black contrasts more than white
    const light = (channel: number) => ((channel / 255 + 0.055) / 1.055) ** 2.4;
    if (0.2126 * light(r) + 0.7152 * light(g) + 0.0722 * light(b) > 0.179) {
      this.host.style.setProperty('--sc-on-primary', '#16161d');
    }
  }

  private notice(text: string, error = false): void {
    this.log.append(element('div', error ? 'notice error' : 'notice', text));
  }

  private scrollToEnd(): void {
    this.log.scrollTop = this.log.scrollHeight;
  }
}

/** One assistant answer: plain text while it streams, then text with linked citations and its sources. */
class AnswerBubble {
  readonly element = element('div', 'msg assistant pending');
  forwarded = false;
  done: AnswerDone | null = null;
  private readonly body = element('div', 'body');
  private content = '';

  constructor(speaker: string) {
    this.body.dataset.thinking = strings.thinking;
    this.element.append(said(speaker), this.body);
  }

  get hasText(): boolean {
    return this.content !== '';
  }

  append(delta: string): void {
    this.content += delta;
    this.body.append(delta);
  }

  finish(done: AnswerDone): void {
    this.done = done;
    this.element.classList.remove('pending');
    if (done.outcome === 'FAILED') {
      this.element.classList.add('failed');
    }
    const sources = citedSources(this.content, done.citations);
    if (sources.length === 0) {
      return;
    }
    const list = document.createElement('ol');
    const items = new Map<number, HTMLLIElement>();
    for (const source of sources) {
      const item = document.createElement('li');
      item.value = source.number;
      const details = document.createElement('details');
      details.append(element('summary', '', source.documentTitle), element('p', '', source.excerpt));
      item.append(details);
      items.set(source.number, item);
      list.append(item);
    }
    this.body.replaceChildren(...splitCitations(this.content, done.citations).map((part) =>
      part.kind === 'text' ? document.createTextNode(part.text) : this.citationButton(part.citation, items)));
    const footer = element('div', 'sources');
    footer.append(element('p', 'sources-title', strings.sources), list);
    this.element.append(footer);
  }

  interrupt(): void {
    this.element.classList.remove('pending');
    this.element.classList.add('failed');
  }

  private citationButton(citation: Citation, items: Map<number, HTMLLIElement>): HTMLButtonElement {
    const cite = element('button', 'cite', String(citation.number)) as HTMLButtonElement;
    cite.type = 'button';
    attr(cite, 'aria-label', strings.source(citation.number));
    cite.title = citation.documentTitle;
    cite.addEventListener('click', () => {
      const item = items.get(citation.number);
      if (!item) {
        return;
      }
      items.forEach((other) => other.classList.toggle('highlight', other === item));
      const details = item.querySelector('details');
      if (details) {
        details.open = true;
      }
      item.scrollIntoView?.({ block: 'nearest' });
    });
    return cite;
  }
}

function visitorBubble(text: string): HTMLElement {
  const bubble = element('div', 'msg visitor');
  bubble.append(said(strings.you), element('div', 'body', text));
  return bubble;
}

/** Who wrote a message, for screen readers only. */
function said(speaker: string): HTMLElement {
  return element('span', 'sr-only', `${speaker}: `);
}

function describe(error: unknown): string {
  if (!(error instanceof WidgetApiError)) {
    return strings.networkError;
  }
  switch (error.code) {
    case 'RATE_LIMITED':
      return error.retryAfterSeconds ? `${strings.tooFast} ${strings.retryIn(error.retryAfterSeconds)}` : strings.tooFast;
    case 'CONVERSATION_CLOSED':
    case 'QUESTION_LIMIT_REACHED':
    case 'INVALID_CONVERSATION_TOKEN':
    case 'CONVERSATION_NOT_FOUND':
      return strings.conversationEnded;
    case 'INVALID_QUESTION':
    case 'VALIDATION_FAILED':
      return strings.invalidQuestion(MAX_QUESTION_LENGTH);
    case 'STREAM_INTERRUPTED':
      return strings.interrupted;
    default:
      return error.status === 404 ? strings.unavailable : strings.networkError;
  }
}

function element(tag: string, className: string, text?: string): HTMLElement {
  const node = document.createElement(tag);
  if (className) {
    node.className = className;
  }
  if (text !== undefined) {
    node.textContent = text;
  }
  return node;
}

// Shorter than calling setAttribute everywhere once minified; the widget loads on every page of every customer site
function attr(node: Element, name: string, value: string): void {
  node.setAttribute(name, value);
}

function button(className: string, label: string, icon: string): HTMLButtonElement {
  const node = document.createElement('button');
  node.type = 'button';
  node.className = className;
  attr(node, 'aria-label', label);
  node.innerHTML = icon;
  return node;
}

function pause(ms: number, signal: AbortSignal): Promise<void> {
  return new Promise((resolve) => {
    const timer = setTimeout(resolve, ms);
    signal.addEventListener('abort', () => {
      clearTimeout(timer);
      resolve();
    });
  });
}

function isColor(value: string): boolean {
  return typeof CSS !== 'undefined' && typeof CSS.supports === 'function' && CSS.supports('color', value);
}
