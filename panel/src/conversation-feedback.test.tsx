import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import type { ConversationSummary } from './api/conversations';
import { endSession } from './api/http-client';
import type { WorkspaceRole } from './api/workspaces';
import { App, createQueryClient } from './app';
import { json, session, stubApi, type Handler } from './test/fake-api';

const empty = { items: [], page: 0, size: 20, total: 0 };

const baseRoutes = (role: WorkspaceRole = 'OWNER'): Record<string, Handler> => ({
  'POST /api/v1/auth/refresh': () => json(session('token', role)),
  'GET /api/v1/me': () => json({ id: 'user-1', email: 'ana@mitienda.com', displayName: 'Ana Pérez' }),
  'GET /api/v1/me/workspaces': () => json([{ workspaceId: 'ws-1', workspaceName: 'Mi Tienda', role }]),
  'GET /api/v1/workspace': () => json({ id: 'ws-1', name: 'Mi Tienda' }),
  'GET /api/v1/conversations': () => json(empty),
  'GET /api/v1/conversations/events': () =>
    new Response('', { status: 200, headers: { 'Content-Type': 'text/event-stream' } }),
});

function renderAt(path: string) {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <App queryClient={createQueryClient()} />
    </MemoryRouter>,
  );
}

beforeEach(() => {
  endSession();
  vi.restoreAllMocks();
});

describe('Customer feedback in a conversation', () => {
  it('shows the rating, the comment and whether each answer helped', async () => {
    const conversation: ConversationSummary = {
      id: 'conv-1',
      status: 'CLOSED',
      questionCount: 2,
      origin: 'https://mitienda.com',
      startedAt: '2026-11-09T10:00:00Z',
      lastActivityAt: '2026-11-09T10:05:00Z',
      handling: 'ASSISTANT',
      agentId: null,
      agentName: null,
      handoffRequestedAt: null,
      channel: 'WEB',
      contactId: null,
      contactName: null,
      language: 'en',
    };
    const answer = (id: string, helpful: boolean | null) => ({
      id,
      author: 'ASSISTANT',
      content: `Respuesta ${id}`,
      outcome: 'ANSWERED',
      citations: [],
      usage: null,
      authorId: null,
      authorName: null,
      createdAt: '2026-11-09T10:05:00Z',
      helpful,
    });
    stubApi({
      ...baseRoutes(),
      'GET /api/v1/conversations/conv-1': () =>
        json({
          conversation,
          messages: [answer('m1', true), answer('m2', false), answer('m3', null)],
          rating: { score: 4, comment: 'Me resolvieron rápido', ratedAt: '2026-11-09T10:06:00Z' },
        }),
    });
    renderAt('/conversations/conv-1');

    expect(await screen.findByLabelText('4 de 5')).toBeDefined();
    expect(screen.getByText('Me resolvieron rápido')).toBeDefined();
    expect(screen.getByText('Inglés')).toBeDefined();
    expect(screen.getAllByText('Le ayudó')).toHaveLength(1);
    expect(screen.getAllByText('No le ayudó')).toHaveLength(1);
  });
});
