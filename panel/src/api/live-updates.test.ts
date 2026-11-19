import { QueryClient } from '@tanstack/react-query';
import { describe, expect, it } from 'vitest';
import type { ConversationDetail } from './conversations';
import { apply } from './live-updates';

const detail: ConversationDetail = {
  conversation: {
    id: 'conv-1',
    status: 'OPEN',
    questionCount: 0,
    origin: null,
    startedAt: '2026-11-09T10:00:00Z',
    lastActivityAt: '2026-11-09T10:00:00Z',
    handling: 'ASSISTANT',
    agentId: null,
    agentName: null,
    handoffRequestedAt: null,
    channel: 'WEB',
    contactId: null,
    contactName: null,
  },
  messages: [],
};

const visitorMessage = {
  conversationId: 'conv-1',
  message: {
    id: 'msg-1',
    author: 'VISITOR',
    content: 'Hola',
    outcome: null,
    citations: [],
    usage: null,
    authorId: null,
    authorName: null,
    createdAt: '2026-11-09T10:01:00Z',
  },
};

describe('apply', () => {
  it('appends a pushed message once, even if it arrives twice', () => {
    const client = new QueryClient();
    client.setQueryData(['conversation', 'conv-1'], detail);

    apply(client, 'message', JSON.stringify(visitorMessage));
    apply(client, 'message', JSON.stringify(visitorMessage));

    expect(client.getQueryData<ConversationDetail>(['conversation', 'conv-1'])?.messages).toHaveLength(1);
  });

  it('replaces the summary of an open conversation when its handling changes', () => {
    const client = new QueryClient();
    client.setQueryData(['conversation', 'conv-1'], detail);

    apply(client, 'conversation', JSON.stringify({ ...detail.conversation, handling: 'WAITING' }));

    expect(client.getQueryData<ConversationDetail>(['conversation', 'conv-1'])?.conversation.handling).toBe('WAITING');
  });

  it('ignores conversations that are not loaded', () => {
    const client = new QueryClient();
    apply(client, 'message', JSON.stringify(visitorMessage));
    expect(client.getQueryData(['conversation', 'conv-1'])).toBeUndefined();
  });
});
