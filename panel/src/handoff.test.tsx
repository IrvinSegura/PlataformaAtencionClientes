import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router';
import { beforeEach, describe, expect, it } from 'vitest';
import type { ConversationSummary, OperatorMessage } from './api/conversations';
import { endSession } from './api/http-client';
import type { WorkspaceRole } from './api/workspaces';
import { App, createQueryClient } from './app';
import { json, session, stubApi, type Handler } from './test/fake-api';

const empty = { items: [], page: 0, size: 20, total: 0 };

const conversation = (overrides: Partial<ConversationSummary> = {}): ConversationSummary => ({
  id: 'conv-1',
  status: 'OPEN',
  questionCount: 1,
  origin: 'https://mitienda.com',
  startedAt: '2026-11-09T10:00:00Z',
  lastActivityAt: '2026-11-09T10:05:00Z',
  handling: 'WAITING',
  agentId: null,
  agentName: null,
  handoffRequestedAt: '2026-11-09T10:05:00Z',
  channel: 'WEB',
  contactId: null,
  contactName: null,
  ...overrides,
});

const message = (overrides: Partial<OperatorMessage>): OperatorMessage => ({
  id: 'msg-1',
  author: 'VISITOR',
  content: '¿Puedo hablar con alguien?',
  outcome: null,
  citations: [],
  usage: null,
  authorId: null,
  authorName: null,
  createdAt: '2026-11-09T10:05:00Z',
  ...overrides,
});

const baseRoutes = (role: WorkspaceRole = 'OWNER'): Record<string, Handler> => ({
  'POST /api/v1/auth/refresh': () => json(session('token', role)),
  'GET /api/v1/me': () => json({ id: 'user-1', email: 'ana@mitienda.com', displayName: 'Ana Pérez' }),
  'GET /api/v1/me/workspaces': () => json([{ workspaceId: 'ws-1', workspaceName: 'Mi Tienda', role }]),
  'GET /api/v1/workspace': () => json({ id: 'ws-1', name: 'Mi Tienda' }),
  'GET /api/v1/conversations': () => json(empty),
  // The live stream is not under test here: answering with an empty stream keeps it quiet
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
});

describe('Human handoff', () => {
  it('lets an operator take a waiting conversation over and reply', async () => {
    let current = conversation();
    let messages: OperatorMessage[] = [message({})];
    const calls = stubApi({
      ...baseRoutes(),
      'GET /api/v1/conversations': () => json({ ...empty, items: [current], total: 1 }),
      'GET /api/v1/conversations/conv-1': () => json({ conversation: current, messages }),
      'POST /api/v1/conversations/conv-1/takeover': () => {
        current = conversation({ handling: 'AGENT', agentId: 'user-1', agentName: 'Ana Pérez' });
        messages = [...messages, message({ id: 'msg-2', author: 'SYSTEM', content: 'AGENT_JOINED', authorName: 'Ana Pérez' })];
        return json(current);
      },
      'POST /api/v1/conversations/conv-1/messages': ({ body }) =>
        json(message({ id: 'msg-3', author: 'AGENT', content: (body as { text: string }).text, authorName: 'Ana Pérez' }), 201),
    });
    const user = userEvent.setup();
    renderAt('/conversations/conv-1');

    expect((await screen.findAllByText('Espera a una persona')).length).toBeGreaterThan(0);
    await user.click(screen.getByRole('button', { name: 'Tomar conversación' }));
    expect(await screen.findByText(/Ana Pérez tomó la conversación/)).toBeDefined();

    await user.type(screen.getByLabelText('Respuesta al visitante'), 'Hola, soy Ana. ¿En qué te ayudo?');
    await user.click(screen.getByRole('button', { name: 'Enviar' }));
    expect(await screen.findByText('Hola, soy Ana. ¿En qué te ayudo?')).toBeDefined();
    expect(calls.some((call) => call.method === 'POST' && call.path.endsWith('/messages'))).toBe(true);
  });

  it('shows who holds a conversation and hides the reply box from everyone else', async () => {
    const held = conversation({ handling: 'AGENT', agentId: 'user-2', agentName: 'Pedro' });
    stubApi({
      ...baseRoutes('AGENT'),
      'GET /api/v1/conversations/conv-1': () => json({ conversation: held, messages: [message({})] }),
    });
    renderAt('/conversations/conv-1');

    expect(await screen.findByText('Pedro')).toBeDefined();
    expect(screen.queryByLabelText('Respuesta al visitante')).toBeNull();
    expect(screen.queryByRole('button', { name: 'Tomar conversación' })).toBeNull();
    // An agent may not free a conversation someone else is answering
    expect(screen.queryByRole('button', { name: 'Devolver al asistente' })).toBeNull();
  });
});
