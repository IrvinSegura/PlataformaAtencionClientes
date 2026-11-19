import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router';
import { beforeEach, describe, expect, it } from 'vitest';
import type { ConversationSummary, OperatorMessage } from './api/conversations';
import { endSession } from './api/http-client';
import type { WorkspaceRole } from './api/workspaces';
import { App, createQueryClient } from './app';
import { json, problem, session, stubApi, type Handler } from './test/fake-api';

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
});

describe('WhatsApp', () => {
  it('connects a number and shows what to configure at Meta', async () => {
    let sent: Record<string, unknown> | null = null;
    stubApi({
      ...baseRoutes(),
      'GET /api/v1/channels/whatsapp': () => problem(404, 'WHATSAPP_CHANNEL_NOT_FOUND'),
      'PUT /api/v1/channels/whatsapp': ({ body }) => {
        sent = body as Record<string, unknown>;
        return json({
          phoneNumberId: '1234567890',
          displayPhoneNumber: '+52 55 1234 5678',
          enabled: true,
          webhookPath: '/api/v1/channels/whatsapp/key-1/webhook',
          verifyToken: 'verify-123',
          updatedAt: '2026-11-09T10:00:00Z',
        });
      },
    });
    const user = userEvent.setup();
    renderAt('/channels/whatsapp');

    await user.type(await screen.findByLabelText('Identificador del número'), '1234567890');
    await user.type(screen.getByLabelText('Número para mostrar'), '+52 55 1234 5678');
    await user.type(screen.getByLabelText(/^Token de acceso/), 'token-abc');
    await user.type(screen.getByLabelText(/^Secreto de la app/), 'secret-abc');
    await user.click(screen.getByRole('button', { name: 'Conectar' }));

    expect(await screen.findByText(/\/api\/v1\/channels\/whatsapp\/key-1\/webhook$/)).toBeDefined();
    expect(screen.getByText('verify-123')).toBeDefined();
    expect(screen.getByText('Conexión guardada.')).toBeDefined();
    expect(sent).toMatchObject({ phoneNumberId: '1234567890', accessToken: 'token-abc', appSecret: 'secret-abc' });
  });
});

describe('WhatsApp conversations', () => {
  it('names the customer and shows the model and the actions behind each answer', async () => {
    const conversation: ConversationSummary = {
      id: 'conv-1',
      status: 'OPEN',
      questionCount: 1,
      origin: null,
      startedAt: '2026-11-09T10:00:00Z',
      lastActivityAt: '2026-11-09T10:05:00Z',
      handling: 'ASSISTANT',
      agentId: null,
      agentName: null,
      handoffRequestedAt: null,
      channel: 'WHATSAPP',
      contactId: '5215551111',
      contactName: 'Lucía',
    };
    const answer: OperatorMessage = {
      id: 'msg-2',
      author: 'ASSISTANT',
      content: 'Tu pedido 123456 fue enviado ayer.',
      outcome: 'ANSWERED',
      citations: [],
      usage: {
        model: 'claude-haiku-5-5',
        inputTokens: 900,
        outputTokens: 40,
        latencyMillis: 800,
        tier: 'SIMPLE',
        routingReason: 'simple',
      },
      authorId: null,
      authorName: null,
      createdAt: '2026-11-09T10:05:00Z',
      actionRunId: 'run-1',
    };
    stubApi({
      ...baseRoutes(),
      'GET /api/v1/conversations': () => json({ ...empty, items: [conversation], total: 1 }),
      'GET /api/v1/conversations/conv-1': () =>
        json({
          conversation,
          messages: [answer],
          actionRuns: [
            {
              id: 'run-1',
              actionName: 'order_status',
              actionTitle: 'Consultar un pedido',
              messageId: 'msg-2',
              arguments: { order_number: '123456' },
              status: 'SUCCEEDED',
              httpStatus: 200,
              error: null,
              latencyMs: 120,
              createdAt: '2026-11-09T10:05:00Z',
              expiresAt: null,
              finishedAt: '2026-11-09T10:05:00Z',
            },
          ],
        }),
    });
    renderAt('/conversations/conv-1');

    expect(await screen.findByRole('heading', { name: 'Lucía' })).toBeDefined();
    expect(screen.getByText('WhatsApp · +5215551111')).toBeDefined();
    expect(screen.getByText(/modelo económico \(pregunta directa\)/)).toBeDefined();
    expect(screen.getByText('Consultar un pedido: hecha')).toBeDefined();
    expect(screen.getByText('{"order_number":"123456"}')).toBeDefined();
  });
});
