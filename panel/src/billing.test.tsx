import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import type { BillingOverview, PlanView, TopUpView } from './api/billing';
import { endSession } from './api/http-client';
import type { WorkspaceRole } from './api/workspaces';
import { App, createQueryClient } from './app';
import { navigation } from './pages/billing-page';
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

const FREE: PlanView = {
  code: 'FREE',
  name: 'Gratis',
  monthlyPrice: 0,
  included: { ANSWERS: 100, ACTION_RUNS: 50 },
  overagePrices: {},
  purchasable: false,
};
const STARTER: PlanView = {
  code: 'STARTER',
  name: 'Inicial',
  monthlyPrice: 29,
  included: { ANSWERS: 2000, ACTION_RUNS: 500 },
  overagePrices: { ANSWERS: 0.02 },
  purchasable: true,
};

const TOP_UP: TopUpView = {
  code: 'ANSWERS_500',
  name: '500 respuestas extra',
  metric: 'ANSWERS',
  quantity: 500,
  price: 10,
  available: true,
  purchasable: true,
};

function overview(changes: Partial<BillingOverview> = {}): BillingOverview {
  return {
    plan: FREE,
    paidPlanCode: null,
    status: 'NONE',
    currentPeriodEnd: null,
    cancelAtPeriodEnd: false,
    graceEndsAt: null,
    paymentsEnabled: true,
    hasPaymentAccount: false,
    currency: 'USD',
    quotas: [
      { metric: 'ANSWERS', used: 100, included: 100, extra: 0, overagePrice: null, overage: 0, overageCost: 0, exhausted: true },
      { metric: 'ACTION_RUNS', used: 3, included: 50, extra: 0, overagePrice: null, overage: 0, overageCost: 0, exhausted: false },
    ],
    estimatedTotal: 0,
    plans: [FREE, STARTER],
    topUps: [],
    ...changes,
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
  vi.restoreAllMocks();
});

describe('Plan and payments', () => {
  it('shows the month against the plan and sends the owner to Stripe to buy a plan', async () => {
    const go = vi.spyOn(navigation, 'go').mockImplementation(() => undefined);
    let chosen: unknown = null;
    stubApi({
      ...baseRoutes(),
      'GET /api/v1/billing': () => json(overview()),
      'POST /api/v1/billing/plan': ({ body }) => {
        chosen = body;
        return json({ checkoutUrl: 'https://checkout.stripe.test/c/pay_1' });
      },
    });
    const user = userEvent.setup();
    renderAt('/billing');

    expect(await screen.findByRole('heading', { name: 'Plan Gratis' })).toBeDefined();
    expect(screen.getByText('Agotado')).toBeDefined();
    expect(screen.getByText('Aún no hay facturas.')).toBeDefined();
    const starter = screen.getByRole('article', { name: 'Plan Inicial' });
    expect(within(starter).getByText(/se detiene al límite/)).toBeDefined();
    await user.click(within(starter).getByRole('button', { name: 'Contratar Inicial' }));

    expect(chosen).toEqual({ plan: 'STARTER' });
    expect(go).toHaveBeenCalledWith('https://checkout.stripe.test/c/pay_1');
  });

  it('warns when a payment failed and lets the owner fix it in Stripe', async () => {
    const go = vi.spyOn(navigation, 'go').mockImplementation(() => undefined);
    stubApi({
      ...baseRoutes(),
      'GET /api/v1/billing': () =>
        json(
          overview({
            plan: STARTER,
            paidPlanCode: 'STARTER',
            status: 'PAST_DUE',
            graceEndsAt: '2026-11-17T10:00:00Z',
            currentPeriodEnd: '2026-12-01T00:00:00Z',
            hasPaymentAccount: true,
            estimatedTotal: 29,
          }),
        ),
      'GET /api/v1/billing/invoices': () =>
        json([
          {
            id: 'in_1',
            number: 'ACME-0001',
            status: 'open',
            total: 29,
            currency: 'USD',
            createdAt: '2026-11-01T00:00:00Z',
            periodStart: '2026-10-01T00:00:00Z',
            periodEnd: '2026-11-01T00:00:00Z',
            hostedUrl: 'https://invoice.stripe.test/i/1',
            pdfUrl: 'https://invoice.stripe.test/i/1.pdf',
          },
        ]),
      'POST /api/v1/billing/portal': () => json({ url: 'https://billing.stripe.test/p/1' }),
    });
    const user = userEvent.setup();
    renderAt('/billing');

    expect((await screen.findByRole('alert')).textContent).toMatch(/No se pudo cobrar tu plan/);
    expect(await screen.findByText('ACME-0001')).toBeDefined();
    expect(screen.getByText('Pendiente')).toBeDefined();
    expect(screen.getByRole('link', { name: 'PDF' }).getAttribute('href')).toBe('https://invoice.stripe.test/i/1.pdf');
    await user.click(screen.getByRole('button', { name: 'Administrar pagos' }));

    expect(go).toHaveBeenCalledWith('https://billing.stripe.test/p/1');
  });

  it('changes a live subscription in place', async () => {
    let plan = 'STARTER';
    stubApi({
      ...baseRoutes(),
      'GET /api/v1/billing': () =>
        json(
          overview({
            plan: STARTER,
            paidPlanCode: 'STARTER',
            status: 'ACTIVE',
            hasPaymentAccount: true,
            cancelAtPeriodEnd: plan === 'FREE',
            currentPeriodEnd: '2026-12-01T00:00:00Z',
          }),
        ),
      'GET /api/v1/billing/invoices': () => json([]),
      'POST /api/v1/billing/plan': ({ body }) => {
        plan = (body as { plan: string }).plan;
        return json({ checkoutUrl: null });
      },
    });
    const user = userEvent.setup();
    renderAt('/billing');

    const free = await screen.findByRole('article', { name: 'Plan Gratis' });
    await user.click(within(free).getByRole('button', { name: 'Cambiar al final del periodo' }));

    expect(await screen.findByText(/Cambio solicitado/)).toBeDefined();
    expect(await screen.findByText(/Tu plan termina el/)).toBeDefined();
    expect(plan).toBe('FREE');
  });

  it('explains when payments are off or the plan cannot be bought', async () => {
    stubApi({
      ...baseRoutes(),
      'GET /api/v1/billing': () => json(overview({ paymentsEnabled: false, plans: [FREE, { ...STARTER, purchasable: false }] })),
      'POST /api/v1/billing/plan': () => problem(422, 'PLAN_NOT_FOR_SALE'),
    });
    renderAt('/billing');

    expect(await screen.findByText(/Los pagos no están configurados/)).toBeDefined();
    const starter = screen.getByRole('article', { name: 'Plan Inicial' });
    expect(within(starter).getByRole('button', { name: 'Contratar Inicial' }).hasAttribute('disabled')).toBe(true);
  });

  it('shows administrators the plan without letting them change it', async () => {
    stubApi({ ...baseRoutes('ADMIN'), 'GET /api/v1/billing': () => json(overview()) });
    renderAt('/billing');

    expect(await screen.findByRole('heading', { name: 'Plan Gratis' })).toBeDefined();
    expect(screen.getByRole('link', { name: /Plan y pagos/ })).toBeDefined();
    expect(screen.queryByRole('button', { name: /Contratar/ })).toBeNull();
  });

  it('sells recharges to a plan that stops at its limit and recommends a bigger plan', async () => {
    const go = vi.spyOn(navigation, 'go').mockImplementation(() => undefined);
    let bought: unknown = null;
    stubApi({
      ...baseRoutes(),
      'GET /api/v1/billing': () =>
        json(
          overview({
            hasPaymentAccount: true,
            quotas: [
              { metric: 'ANSWERS', used: 580, included: 600, extra: 500, overagePrice: null, overage: 0, overageCost: 0, exhausted: false },
            ],
            topUps: [
              TOP_UP,
              { ...TOP_UP, code: 'ACTION_RUNS_200', name: '200 acciones extra', metric: 'ACTION_RUNS', purchasable: false },
              { ...TOP_UP, code: 'WHATSAPP_MESSAGES_500', name: '500 mensajes extra', available: false, purchasable: false },
            ],
          }),
        ),
      'GET /api/v1/billing/invoices': () => json([]),
      'POST /api/v1/billing/top-ups': ({ body }) => {
        bought = body;
        return json({ checkoutUrl: 'https://checkout.stripe.test/c/top_1' });
      },
    });
    const user = userEvent.setup();
    renderAt('/billing');

    expect(await screen.findByText(/incluye 500 de recargas/)).toBeDefined();
    expect(screen.queryByRole('article', { name: 'Recarga 500 mensajes extra' })).toBeNull();
    const runs = screen.getByRole('article', { name: 'Recarga 200 acciones extra' });
    expect(within(runs).getByRole('button', { name: 'Comprar recarga' }).hasAttribute('disabled')).toBe(true);
    expect(within(screen.getByRole('article', { name: 'Plan Inicial' })).getByText('Recomendado')).toBeDefined();

    const answers = screen.getByRole('article', { name: 'Recarga 500 respuestas extra' });
    expect(within(answers).getByText(/pago único/)).toBeDefined();
    await user.click(within(answers).getByRole('button', { name: 'Comprar recarga' }));

    expect(bought).toEqual({ topUp: 'ANSWERS_500' });
    expect(go).toHaveBeenCalledWith('https://checkout.stripe.test/c/top_1');
  });

  it('offers no recharges to a plan that bills past its limit', async () => {
    stubApi({
      ...baseRoutes(),
      'GET /api/v1/billing': () =>
        json(
          overview({
            plan: STARTER,
            paidPlanCode: 'STARTER',
            status: 'ACTIVE',
            hasPaymentAccount: true,
            quotas: [
              { metric: 'ANSWERS', used: 300, included: 2000, extra: 0, overagePrice: 0.02, overage: 0, overageCost: 0, exhausted: false },
            ],
            topUps: [{ ...TOP_UP, available: false, purchasable: false }],
          }),
        ),
      'GET /api/v1/billing/invoices': () => json([]),
    });
    renderAt('/billing');

    expect(await screen.findByRole('heading', { name: 'Plan Inicial' })).toBeDefined();
    expect(screen.queryByRole('heading', { name: 'Recargas' })).toBeNull();
    expect(screen.queryByText('Recomendado')).toBeNull();
  });

  it('hides the section from agents', async () => {
    stubApi(baseRoutes('AGENT'));
    renderAt('/conversations');

    expect(await screen.findByRole('link', { name: /Conversaciones/ })).toBeDefined();
    expect(screen.queryByRole('link', { name: /Plan y pagos/ })).toBeNull();
  });
});

describe('Assistant paused by the plan', () => {
  it('tells the team why the conversation came to them', async () => {
    stubApi({
      ...baseRoutes(),
      'GET /api/v1/conversations/conv-1': () =>
        json({
          conversation: {
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
          },
          messages: [
            {
              id: 'msg-1',
              author: 'SYSTEM',
              content: 'ASSISTANT_PAUSED',
              outcome: null,
              citations: [],
              usage: null,
              authorId: null,
              authorName: null,
              createdAt: '2026-11-09T10:05:00Z',
            },
          ],
          actionRuns: [],
        }),
    });
    renderAt('/conversations/conv-1');

    expect(await screen.findByText(/alcanzó su límite de respuestas del mes/)).toBeDefined();
  });
});
