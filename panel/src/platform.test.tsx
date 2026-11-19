import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { endSession } from './api/http-client';
import type { PlatformWorkspace } from './api/platform';
import type { WorkspaceSuspension } from './api/workspaces';
import { App, createQueryClient } from './app';
import { json, session, stubApi, type Handler } from './test/fake-api';

const empty = { items: [], page: 0, size: 20, total: 0 };

const baseRoutes = (platformAdmin: boolean, suspension: WorkspaceSuspension | null = null): Record<string, Handler> => ({
  'POST /api/v1/auth/refresh': () => json(session('token', 'OWNER')),
  'GET /api/v1/me': () => json({ id: 'user-1', email: 'ana@mitienda.com', displayName: 'Ana Pérez' }),
  'GET /api/v1/me/workspaces': () => json([{ workspaceId: 'ws-1', workspaceName: 'Mi Tienda', role: 'OWNER' }]),
  'GET /api/v1/workspace': () => json({ id: 'ws-1', name: 'Mi Tienda', suspension }),
  'GET /api/v1/platform/me': () => json({ platformAdmin }),
  'GET /api/v1/conversations': () => json(empty),
  'GET /api/v1/conversations/events': () =>
    new Response('', { status: 200, headers: { 'Content-Type': 'text/event-stream' } }),
});

const WORKSPACE: PlatformWorkspace = {
  id: 'ws-2',
  name: 'Zapatería Luna',
  createdAt: '2026-10-01T10:00:00Z',
  members: 3,
  ownerEmail: 'luna@zapateria.mx',
  ownerName: 'Marta Luna',
  plan: {
    planCode: 'starter',
    planName: 'Inicial',
    monthlyPrice: 499,
    subscriptionStatus: 'ACTIVE',
    answersThisMonth: 420,
    answersIncluded: 1000,
  },
  conversationsLastMonth: 87,
  lastActivityAt: '2026-11-10T18:00:00Z',
  suspension: null,
};

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

afterEach(() => {
  vi.restoreAllMocks();
});

describe('Platform administration', () => {
  it('lets whoever runs the platform see every workspace and suspend one', async () => {
    let suspension: WorkspaceSuspension | null = null;
    let requested: unknown = null;
    stubApi({
      ...baseRoutes(true),
      'GET /api/v1/platform/workspaces': () => json({ items: [{ ...WORKSPACE, suspension }], total: 1 }),
      'POST /api/v1/platform/workspaces/ws-2/suspension': ({ body }) => {
        requested = body;
        suspension = { reason: 'Envío de spam', suspendedAt: '2026-11-11T12:00:00Z' };
        return json({ ...WORKSPACE, suspension });
      },
      'DELETE /api/v1/platform/workspaces/ws-2/suspension': () => {
        suspension = null;
        return json(WORKSPACE);
      },
    });
    vi.spyOn(window, 'confirm').mockReturnValue(true);
    const user = userEvent.setup();
    renderAt('/platform');

    const row = (await screen.findByText('Zapatería Luna')).closest('tr')!;
    expect(screen.getByRole('link', { name: 'Espacios de trabajo' })).toBeDefined();
    expect(within(row).getByText('luna@zapateria.mx')).toBeDefined();
    expect(within(row).getByText(/420 de 1[ ,.]?000 respuestas este mes/)).toBeDefined();
    expect(within(row).getByText(/87 conversaciones/)).toBeDefined();

    await user.click(within(row).getByRole('button', { name: 'Suspender…' }));
    const form = within(row).getByRole('form', { name: 'Suspender Zapatería Luna' });
    await user.type(within(form).getByRole('textbox', { name: /Motivo/ }), 'Envío de spam');
    await user.click(within(form).getByRole('button', { name: 'Suspender' }));

    expect(await within(row).findByText('Suspendido')).toBeDefined();
    expect(requested).toEqual({ reason: 'Envío de spam' });
    await user.click(within(row).getByRole('button', { name: 'Reactivar' }));
    expect(await within(row).findByText('Activo')).toBeDefined();
  });

  it('is hidden from everyone else', async () => {
    stubApi(baseRoutes(false));
    renderAt('/platform');

    expect(await screen.findByText(/solo para quienes administran la plataforma/)).toBeDefined();
    expect(screen.queryByRole('link', { name: 'Espacios de trabajo' })).toBeNull();
  });

  it('tells the members of a suspended workspace why', async () => {
    stubApi(baseRoutes(false, { reason: 'Envío de spam', suspendedAt: '2026-11-11T12:00:00Z' }));
    renderAt('/conversations');

    const banner = await screen.findByRole('alert');
    expect(banner.textContent).toMatch(/está suspendido: Envío de spam/);
  });
});
