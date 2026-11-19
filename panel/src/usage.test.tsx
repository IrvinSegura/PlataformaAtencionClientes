import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router';
import { beforeEach, describe, expect, it } from 'vitest';
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
});

describe('Usage', () => {
  it('shows what the month used against the plan and its estimated cost', async () => {
    stubApi({
      ...baseRoutes(),
      'GET /api/v1/usage': () =>
        json({
          month: '2026-11',
          currency: 'USD',
          planCode: 'STARTER',
          planName: 'Inicial',
          monthlyPrice: 29,
          estimatedTotal: 30.5,
          lines: [
            { metric: 'ANSWERS', dimension: 'WHATSAPP', quantity: 2075 },
            { metric: 'INPUT_TOKENS', dimension: 'claude-sonnet-5-5', quantity: 400000 },
          ],
          quotas: [
            { metric: 'ANSWERS', used: 2075, included: 2000, extra: 0, overagePrice: 0.02, overage: 75, overageCost: 1.5, exhausted: false },
            { metric: 'ACTION_RUNS', used: 500, included: 500, extra: 0, overagePrice: null, overage: 0, overageCost: 0, exhausted: true },
          ],
          days: [
            { day: '2026-11-09', answers: 120, inputTokens: 400000, outputTokens: 30000, actionRuns: 4, whatsappMessages: 240 },
          ],
        }),
    });
    renderAt('/usage');

    expect(await screen.findByText('Tokens de entrada')).toBeDefined();
    expect(screen.getAllByText('Respuestas').length).toBe(3);
    expect(screen.getByLabelText('Total estimado').textContent).toMatch(/30[.,]50/);
    expect(screen.getByText(/Plan Inicial/)).toBeDefined();
    expect(screen.getByText('Límite alcanzado')).toBeDefined();
    expect(screen.getByText(/^75 · /)).toBeDefined();
    expect(screen.getByText('WhatsApp', { selector: '.mono' })).toBeDefined();
    expect(screen.getByText('2026-11-09')).toBeDefined();
  });
});
