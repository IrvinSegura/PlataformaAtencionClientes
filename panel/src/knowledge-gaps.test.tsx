import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
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

describe('Knowledge gaps', () => {
  it('lists unanswered questions by frequency and lets managers resolve them', async () => {
    let status = 'OPEN';
    stubApi({
      ...baseRoutes(),
      'GET /api/v1/knowledge-gaps': ({ url }) =>
        json(
          new URL(url).searchParams.get('status') === status
            ? {
                ...empty,
                total: 1,
                items: [
                  {
                    id: 'gap-1',
                    question: '¿Aceptan Bitcoin?',
                    status,
                    occurrences: 7,
                    firstAskedAt: '2026-11-01T10:00:00Z',
                    lastAskedAt: '2026-11-09T10:00:00Z',
                    lastConversationId: 'conv-9',
                  },
                ],
              }
            : empty,
        ),
      'PUT /api/v1/knowledge-gaps/gap-1/status': ({ body }) => {
        status = (body as { status: string }).status;
        return new Response(null, { status: 204 });
      },
    });
    const user = userEvent.setup();
    renderAt('/knowledge/gaps');

    const row = (await screen.findByText('¿Aceptan Bitcoin?')).closest('tr')!;
    expect(within(row).getByText('7')).toBeDefined();
    expect(within(row).getByRole('link', { name: 'Probar búsqueda' }).getAttribute('href')).toBe(
      '/knowledge/search?q=%C2%BFAceptan%20Bitcoin%3F',
    );
    await user.click(within(row).getByRole('button', { name: 'Resuelta' }));
    expect(await screen.findByText(/No hay preguntas sin respuesta/)).toBeDefined();
  });

  it('lets agents read the list without triage actions', async () => {
    stubApi({
      ...baseRoutes('AGENT'),
      'GET /api/v1/knowledge-gaps': () =>
        json({
          ...empty,
          total: 1,
          items: [
            {
              id: 'gap-1',
              question: '¿Aceptan Bitcoin?',
              status: 'OPEN',
              occurrences: 2,
              firstAskedAt: '2026-11-01T10:00:00Z',
              lastAskedAt: '2026-11-09T10:00:00Z',
              lastConversationId: 'conv-9',
            },
          ],
        }),
    });
    renderAt('/knowledge/gaps');
    await screen.findByText('¿Aceptan Bitcoin?');
    expect(screen.queryByRole('button', { name: 'Resuelta' })).toBeNull();
  });
});
