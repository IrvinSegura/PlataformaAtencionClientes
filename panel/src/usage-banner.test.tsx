import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router';
import { beforeEach, describe, expect, it } from 'vitest';
import type { Quota, UsageReport } from './api/billing';
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

function quota(used: number, overrides: Partial<Quota> = {}): Quota {
  return {
    metric: 'ANSWERS',
    used,
    included: 100,
    extra: 0,
    overagePrice: null,
    overage: 0,
    overageCost: 0,
    exhausted: false,
    ...overrides,
  };
}

function report(...quotas: Quota[]): UsageReport {
  return {
    month: '2026-11',
    currency: 'USD',
    planCode: 'FREE',
    planName: 'Gratis',
    monthlyPrice: 0,
    lines: [],
    quotas,
    estimatedTotal: 0,
    days: [],
  };
}

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

describe('Usage banner', () => {
  it('warns at 80 % of the plan and says what stops at the limit', async () => {
    stubApi({
      ...baseRoutes(),
      'GET /api/v1/usage': () =>
        json(report(quota(85), quota(10, { metric: 'WHATSAPP_MESSAGES', included: 1000 }))),
    });
    renderAt('/conversations');

    const banner = await screen.findByText(/Llevas el 85 % de las respuestas del asistente/);
    expect(banner.textContent).toContain('(85 de 100)');
    expect(banner.textContent).toContain('el asistente deja de responder');
    expect(screen.getByRole('link', { name: 'Recargar o cambiar de plan' }).getAttribute('href')).toBe('/billing');
  });

  it('says the extra is billed on a plan that keeps working past its limit', async () => {
    stubApi({
      ...baseRoutes(),
      'GET /api/v1/usage': () => json(report(quota(1900, { included: 2000, overagePrice: 0.02 }))),
    });
    renderAt('/conversations');

    expect(await screen.findByText(/Lo que pase del límite se cobra aparte/)).toBeDefined();
    expect(screen.getByRole('link', { name: 'Ver planes' })).toBeDefined();
  });

  it('shows the most used metric once one runs out', async () => {
    stubApi({
      ...baseRoutes(),
      'GET /api/v1/usage': () =>
        json(report(quota(90), quota(1, { metric: 'ACTION_RUNS', included: 1, exhausted: true }))),
    });
    renderAt('/conversations');

    expect(await screen.findByText(/Se acabaron las acciones de tu plan este mes/)).toBeDefined();
    expect(screen.queryByText(/respuestas del asistente/)).toBeNull();
  });

  it('stays quiet below 80 %', async () => {
    const calls = stubApi({ ...baseRoutes(), 'GET /api/v1/usage': () => json(report(quota(79))) });
    renderAt('/conversations');

    await screen.findAllByText('Mi Tienda');
    await new Promise((resolve) => setTimeout(resolve, 50));
    expect(calls.some((call) => call.path === '/api/v1/usage')).toBe(true);
    expect(screen.queryByText(/Llevas el/)).toBeNull();
  });

  it('is not shown to agents, who cannot see usage', async () => {
    const calls = stubApi({ ...baseRoutes('AGENT'), 'GET /api/v1/usage': () => json(report(quota(99))) });
    renderAt('/conversations');

    await screen.findAllByText('Mi Tienda');
    expect(screen.queryByText(/Llevas el/)).toBeNull();
    expect(calls.some((call) => call.path === '/api/v1/usage')).toBe(false);
  });
});
