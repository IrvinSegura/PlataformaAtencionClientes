import type { StoredSession } from './types';

/**
 * Remembers the visitor's conversation across page loads of the host site, one per widget key. Storage can be
 * unavailable (private mode, blocked site data); the widget then works for the current page view only.
 */
export class SessionStore {
  private readonly conversationKey: string;
  private readonly openKey: string;
  private readonly ratedKey: string;

  constructor(widgetKey: string) {
    this.conversationKey = `supportchat:${widgetKey}:conversation`;
    this.openKey = `supportchat:${widgetKey}:open`;
    this.ratedKey = `supportchat:${widgetKey}:rated`;
  }

  load(): StoredSession | null {
    const raw = safe(() => localStorage.getItem(this.conversationKey));
    if (!raw) {
      return null;
    }
    try {
      const value = JSON.parse(raw) as Partial<StoredSession>;
      if (typeof value.conversationId === 'string' && typeof value.visitorToken === 'string') {
        return { conversationId: value.conversationId, visitorToken: value.visitorToken };
      }
    } catch {
      // Corrupted entry; treated as no conversation
    }
    this.clear();
    return null;
  }

  save(session: StoredSession): void {
    safe(() => localStorage.setItem(this.conversationKey, JSON.stringify(session)));
  }

  clear(): void {
    safe(() => localStorage.removeItem(this.conversationKey));
  }

  /** Whether the visitor already rated this conversation, so they are not asked again. */
  rated(conversationId: string): boolean {
    return safe(() => localStorage.getItem(this.ratedKey)) === conversationId;
  }

  markRated(conversationId: string): void {
    safe(() => localStorage.setItem(this.ratedKey, conversationId));
  }

  /** Whether the chat was open, so it stays open while the visitor moves between pages of the same tab. */
  wasOpen(): boolean {
    return safe(() => sessionStorage.getItem(this.openKey)) === '1';
  }

  rememberOpen(open: boolean): void {
    safe(() => (open ? sessionStorage.setItem(this.openKey, '1') : sessionStorage.removeItem(this.openKey)));
  }
}

function safe<T>(action: () => T): T | null {
  try {
    return action();
  } catch {
    return null;
  }
}
