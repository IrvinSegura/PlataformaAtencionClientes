// Conversations, customers, attention, playground and outcomes (backend module conversations)

import type { ActionRun } from './actions';
import { openStream, pathSegment, request } from './http-client';
import type { TaughtAnswer } from './knowledge';
import type { Page } from './page';
import { readServerSentEvents } from './sse';

/** Which Claude model answers: the platform per question, always the fast one, or always the precise one. */
export type ModelPreference = 'AUTOMATIC' | 'FAST' | 'PRECISE';

/** What happens when a customer needs a person. */
export interface AttentionSettings {
  /** An upset customer stops talking to the assistant and waits for the team. */
  handOffFrustrated: boolean;
  alertsEnabled: boolean;
  /** WhatsApp numbers to alert, digits only with the country code; at most three. */
  alertPhones: string[];
  alertOnRequest: boolean;
  alertOnFrustration: boolean;
  /** Alert when a question the assistant does not answer well keeps coming back. */
  alertOnDifficult: boolean;
  /** Alert when the month's usage reaches 80 % and 95 % of what the plan includes. */
  alertOnUsage: boolean;
  /** An approved WhatsApp template, needed to reach a number that has not written in 24 hours. */
  template: string | null;
  templateLanguage: string | null;
}

export type AttentionReason = 'ASKED_FOR_PERSON' | 'FRUSTRATED';

export type AlertDeliveryStatus = 'SENT' | 'REJECTED' | 'FAILED' | 'NO_CHANNEL';

export interface AttentionAlert {
  id: string;
  conversationId: string;
  channel: ConversationChannel;
  contactId: string | null;
  contactName: string | null;
  reason: AttentionReason;
  excerpt: string | null;
  createdAt: string;
  /** One per number alerted; empty when nobody was. */
  deliveries: { recipient: string; status: AlertDeliveryStatus; detail: string | null; deliveredAt: string }[];
}

export interface ModelChoice {
  preference: ModelPreference;
  /** The Claude models behind the choice; two for the automatic one. */
  models: string[];
}

export interface PlaygroundTurn {
  fromCustomer: boolean;
  text: string;
}

export interface InspectedSource {
  /** The [n] the model saw it as, or null when selection dropped it. */
  number: number | null;
  documentId: string;
  documentTitle: string;
  content: string;
  similarity: number | null;
  keywordMatch: boolean;
  usedByModel: boolean;
}

export interface SimulatedAction {
  name: string;
  description: string | null;
  input: Record<string, unknown>;
  requiresConfirmation: boolean;
  known: boolean;
}

export interface PlaygroundModelReport {
  model: string | null;
  tier: ModelTier | null;
  reason: string | null;
  preference: ModelPreference;
  inputTokens: number;
  outputTokens: number;
  estimatedCostUsd: number | null;
  modelCalls: number;
}

/** The inspector of one playground answer. */
export interface PlaygroundResult {
  outcome: AnswerOutcome;
  text: string;
  citations: Citation[];
  sources: InspectedSource[];
  toolCalls: SimulatedAction[];
  model: PlaygroundModelReport;
  latencyMillis: number;
}

export type ConversationStatus = 'OPEN' | 'CLOSED';

/** SMALL_TALK: a fixed courtesy reply to a greeting, a thanks or a goodbye, with no model call. */
export type AnswerOutcome = 'ANSWERED' | 'NO_ANSWER' | 'FAILED' | 'SMALL_TALK';

/** Who answers: the assistant, nobody yet (the visitor asked for a person) or a member of the team. */
export type ConversationHandling = 'ASSISTANT' | 'WAITING' | 'AGENT';

/** Where the customer writes from. */
export type ConversationChannel = 'WEB' | 'WHATSAPP' | 'EMAIL' | 'MESSENGER' | 'INSTAGRAM';

export interface ConversationSummary {
  id: string;
  status: ConversationStatus;
  questionCount: number;
  origin: string | null;
  startedAt: string;
  lastActivityAt: string;
  handling: ConversationHandling;
  agentId: string | null;
  agentName: string | null;
  handoffRequestedAt: string | null;
  channel: ConversationChannel;
  /** The customer's WhatsApp number, email address or Meta page-scoped id; null on the web. */
  contactId: string | null;
  contactName: string | null;
  // The customer's language as an ISO 639-1 code ("en"); null until their browser or messages tell
  language?: string | null;
  /** When a message of the customer first read as upset; null while they seem calm. */
  frustratedAt?: string | null;
  /** What the conversation is about, tagged from the customer's words or set by the team. */
  intents?: ConversationIntent[];
  /** Whether the team set the topics by hand; later messages add none. */
  intentsEdited?: boolean;
}

export type ConversationIntent =
  | 'ORDER_STATUS'
  | 'SHIPPING'
  | 'RETURNS'
  | 'PAYMENTS'
  | 'PRICING'
  | 'AVAILABILITY'
  | 'HOURS_AND_LOCATION'
  | 'ACCOUNT'
  | 'COMPLAINT';

/** A customer who writes through a channel, recognized by their number or address across conversations. */
export interface Customer {
  channel: ConversationChannel;
  contactId: string;
  /** The name the channel reported last. */
  contactName: string | null;
  /** The name the team gave the customer. */
  displayName: string | null;
  /** For the team only: never shown to the customer or the assistant. */
  notes: string;
  /** Whether the assistant reads the customer's earlier conversations. */
  remember: boolean;
  updatedAt: string | null;
  updatedByName: string | null;
  conversations: number;
  firstSeenAt: string;
  lastSeenAt: string;
  /** Their latest conversations, most recently active first. */
  history: ConversationSummary[];
}

export interface CustomerInput {
  displayName: string | null;
  notes: string;
  remember: boolean;
}

export interface CustomerListItem {
  channel: ConversationChannel;
  contactId: string;
  contactName: string | null;
  displayName: string | null;
  conversations: number;
  firstSeenAt: string;
  lastSeenAt: string;
  latestConversationId: string;
}

export interface Citation {
  number: number;
  documentId: string;
  documentTitle: string;
  chunkId: string;
  excerpt: string;
}

export type ModelTier = 'SIMPLE' | 'COMPLEX';

export interface ModelUsage {
  model: string;
  inputTokens: number;
  outputTokens: number;
  latencyMillis: number;
  /** Null for answers older than model routing. */
  tier: ModelTier | null;
  routingReason: string | null;
}

/** SYSTEM messages carry one of these codes as their content. */
export type SystemNotice =
  | 'HANDOFF_REQUESTED'
  | 'HANDOFF_CANCELLED'
  | 'AGENT_JOINED'
  | 'AGENT_LEFT'
  | 'CONVERSATION_CLOSED'
  | 'ASSISTANT_PAUSED';

export interface OperatorMessage {
  id: string;
  author: 'VISITOR' | 'ASSISTANT' | 'AGENT' | 'SYSTEM';
  content: string;
  outcome: AnswerOutcome | null;
  citations: Citation[] | null;
  usage: ModelUsage | null;
  authorId: string | null;
  authorName: string | null;
  createdAt: string;
  actionRunId?: string | null;
  // Assistant answers: whether the customer said it helped, null when they said nothing
  helpful?: boolean | null;
}

export interface ConversationRating {
  score: number;
  comment: string | null;
  ratedAt: string;
}

export interface ConversationDetail {
  conversation: ConversationSummary;
  messages: OperatorMessage[];
  actionRuns?: ActionRun[];
  rating?: ConversationRating | null;
}

export interface OutcomeDay {
  day: string;
  conversations: number;
  resolvedByAssistant: number;
  handedOff: number;
  unresolved: number;
}

export interface RatingComment {
  conversationId: string;
  score: number;
  comment: string;
  ratedAt: string;
}

/** Rates are shares from 0 to 1, null when there was nothing to divide by. */
export interface OutcomeReport {
  from: string;
  to: string;
  conversations: number;
  resolvedByAssistant: number;
  handedOff: number;
  unresolved: number;
  resolutionRate: number | null;
  answered: number;
  notAnswered: number;
  failed: number;
  helpful: number;
  unhelpful: number;
  helpfulRate: number | null;
  ratings: number;
  averageRating: number | null;
  satisfaction: number | null;
  // Ratings given with each score, from 1 to 5
  ratingCounts: number[];
  days: OutcomeDay[];
  comments: RatingComment[];
  // Answers of each model tier and reason it was chosen for, with what customers said of them
  models: ModelQuality[];
  // Questions found difficult for the first time in the period
  difficultQuestions: number;
}

export interface ModelQuality {
  tier: ModelTier | null;
  reason: string;
  answers: number;
  notAnswered: number;
  helpful: number;
  unhelpful: number;
  unhelpfulRate: number | null;
}

/** Why a question was found difficult: the latest sign of it. */
export type DifficultReason = 'ESCALATED' | 'UNHELPFUL' | 'ASKED_AGAIN' | 'UNSATISFIED';

export type DifficultStatus = 'OPEN' | 'TAUGHT' | 'DISMISSED' | 'RESOLVED';

/** Why a question closed by itself. */
export type DifficultResolution = 'ANSWERED_WELL' | 'QUIET';

/** A question the assistant struggled with; while open, it goes straight to the precise model. */
export interface DifficultQuestion {
  id: string;
  question: string;
  reason: DifficultReason;
  status: DifficultStatus;
  lastAnswer: string | null;
  conversationId: string | null;
  occurrences: number;
  firstSeenAt: string;
  lastSeenAt: string;
  /** Why it closed by itself; null unless RESOLVED. */
  resolution: DifficultResolution | null;
  /** When the team was told by WhatsApp that it keeps coming back. */
  alertedAt: string | null;
}

/** An answer drafted from the knowledge base; null when the documents do not hold it. */
export interface DraftAnswer {
  answer: string | null;
}

export const difficultApi = {
  list: (status: DifficultStatus, page: number, size = 20) =>
    request<Page<DifficultQuestion>>(`/api/v1/difficult-questions?status=${status}&page=${page}&size=${size}`),
  changeStatus: (questionId: string, status: DifficultStatus) =>
    request<void>(`/api/v1/difficult-questions/${pathSegment(questionId)}/status`, { method: 'PUT', json: { status } }),
  teach: (questionId: string, answer: string) =>
    request<TaughtAnswer>(`/api/v1/difficult-questions/${pathSegment(questionId)}/answer`, { json: { answer } }),
  /** Calls the precise model: it counts as a playground answer of the plan. */
  draft: (questionId: string) =>
    request<DraftAnswer>(`/api/v1/difficult-questions/${pathSegment(questionId)}/draft`, { method: 'POST' }),
};

export const insightsApi = {
  /** From and to are UTC days ("2026-11-01"), both included. */
  outcomes: (from: string, to: string) =>
    request<OutcomeReport>(`/api/v1/insights/outcomes?from=${encodeURIComponent(from)}&to=${encodeURIComponent(to)}`),
};

export const conversationsApi = {
  list: (page: number, size = 20, handling?: ConversationHandling, intent?: ConversationIntent) =>
    request<Page<ConversationSummary>>(
      `/api/v1/conversations?page=${page}&size=${size}${handling ? `&handling=${handling}` : ''}${
        intent ? `&intent=${intent}` : ''
      }`,
    ),
  setIntents: (conversationId: string, intents: ConversationIntent[]) =>
    request<ConversationSummary>(`/api/v1/conversations/${pathSegment(conversationId)}/intents`, {
      method: 'PUT',
      json: { intents },
    }),
  get: (conversationId: string) => request<ConversationDetail>(`/api/v1/conversations/${pathSegment(conversationId)}`),
  takeOver: (conversationId: string) =>
    request<ConversationSummary>(`/api/v1/conversations/${pathSegment(conversationId)}/takeover`, { method: 'POST' }),
  release: (conversationId: string) =>
    request<ConversationSummary>(`/api/v1/conversations/${pathSegment(conversationId)}/release`, { method: 'POST' }),
  close: (conversationId: string) =>
    request<ConversationSummary>(`/api/v1/conversations/${pathSegment(conversationId)}/closure`, { method: 'POST' }),
  reply: (conversationId: string, text: string) =>
    request<OperatorMessage>(`/api/v1/conversations/${pathSegment(conversationId)}/messages`, { json: { text } }),
};

export const customersApi = {
  list: (query: string, page: number, size = 20) =>
    request<Page<CustomerListItem>>(
      `/api/v1/customers?page=${page}&size=${size}${query ? `&query=${encodeURIComponent(query)}` : ''}`,
    ),
  /** A customer is reached through one of their conversations, so their number never travels in a URL. */
  ofConversation: (conversationId: string) =>
    request<Customer>(`/api/v1/conversations/${pathSegment(conversationId)}/customer`),
  edit: (conversationId: string, input: CustomerInput) =>
    request<Customer>(`/api/v1/conversations/${pathSegment(conversationId)}/customer`, { method: 'PUT', json: input }),
};

export const assistantApi = {
  models: () => request<ModelChoice[]>('/api/v1/assistant/models'),
};

export const attentionApi = {
  settings: () => request<AttentionSettings>('/api/v1/assistant/attention'),
  save: (settings: AttentionSettings) =>
    request<AttentionSettings>('/api/v1/assistant/attention', { method: 'PUT', json: settings }),
  alerts: () => request<AttentionAlert[]>('/api/v1/assistant/attention/alerts'),
};

export interface PlaygroundQuestion {
  question: string;
  history: PlaygroundTurn[];
  model: ModelPreference;
}

export const playgroundApi = {
  /**
   * Asks the assistant as a customer would. The answer arrives in pieces through onText; the returned promise holds
   * the inspector once the answer is complete.
   */
  async answer(input: PlaygroundQuestion, onText: (delta: string) => void, signal: AbortSignal): Promise<PlaygroundResult> {
    const response = await openStream('/api/v1/playground/answers', signal, { json: input });
    if (!response.body) {
      throw new Error('The answer stream is empty');
    }
    for await (const event of readServerSentEvents(response.body)) {
      if (event.event === 'delta') {
        onText((JSON.parse(event.data) as { text: string }).text);
      } else if (event.event === 'done') {
        return JSON.parse(event.data) as PlaygroundResult;
      }
    }
    throw new Error('The answer stream ended before the answer was complete');
  },
};
