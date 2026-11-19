// Shapes of the public widget API (backend: WidgetConfigController and WidgetConversationController)

export interface WidgetConfig {
  botName: string;
  welcomeMessage: string;
  // The widget's texts in the browser's language, when it is not Spanish
  texts?: Record<string, string>;
}

export interface StartedConversation {
  conversationId: string;
  visitorToken: string;
  startedAt: string;
}

/** SMALL_TALK: a fixed courtesy reply to a greeting, a thanks or a goodbye. */
export type AnswerOutcome = 'ANSWERED' | 'NO_ANSWER' | 'FAILED' | 'SMALL_TALK';

export interface Citation {
  number: number;
  documentId: string;
  documentTitle: string;
  chunkId: string;
  excerpt: string;
}

/** A change the assistant proposed; it runs only when the visitor confirms it. */
export interface PendingAction {
  runId: string;
  title: string;
  arguments: Record<string, unknown>;
  expiresAt: string;
}

export interface MessageView {
  id: string;
  // AGENT: a person of the team; SYSTEM: a notice whose content is a code such as AGENT_JOINED
  author: 'VISITOR' | 'ASSISTANT' | 'AGENT' | 'SYSTEM';
  content: string;
  outcome: AnswerOutcome | null;
  citations: Citation[] | null;
  authorName: string | null;
  createdAt: string;
  // Only while it still waits for the visitor
  pendingAction?: PendingAction | null;
  // Assistant answers: whether the visitor said it helped
  helpful?: boolean | null;
}

/** Who answers the visitor: the assistant, nobody yet (waiting for a person) or a person of the team. */
export type Handling = 'ASSISTANT' | 'WAITING' | 'AGENT';

export interface ConversationState {
  conversationId: string;
  status: 'OPEN' | 'CLOSED';
  handling: Handling;
  agentName: string | null;
}

export interface AnswerDone {
  messageId: string;
  outcome: AnswerOutcome;
  citations: Citation[];
  pendingAction?: PendingAction | null;
}

/** The conversation this visitor's browser owns: its id and the secret that proves it. */
export interface StoredSession {
  conversationId: string;
  visitorToken: string;
}
