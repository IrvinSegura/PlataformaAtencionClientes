import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router';
import { beforeEach, describe, expect, it } from 'vitest';
import type { ConversationSummary, Customer } from './api/conversations';
import { endSession } from './api/http-client';
import { App, createQueryClient } from './app';
import { json, session, stubApi, type Handler } from './test/fake-api';

const empty = { items: [], page: 0, size: 20, total: 0 };

const baseRoutes = (): Record<string, Handler> => ({
  'POST /api/v1/auth/refresh': () => json(session('token', 'AGENT')),
  'GET /api/v1/me': () => json({ id: 'user-1', email: 'ana@mitienda.com', displayName: 'Ana Pérez' }),
  'GET /api/v1/me/workspaces': () => json([{ workspaceId: 'ws-1', workspaceName: 'Mi Tienda', role: 'AGENT' }]),
  'GET /api/v1/workspace': () => json({ id: 'ws-1', name: 'Mi Tienda' }),
  'GET /api/v1/conversations': () => json(empty),
  'GET /api/v1/conversations/events': () =>
    new Response('', { status: 200, headers: { 'Content-Type': 'text/event-stream' } }),
});

const conversation = (id: string, startedAt: string): ConversationSummary => ({
  id,
  status: 'CLOSED',
  questionCount: 2,
  origin: null,
  startedAt,
  lastActivityAt: startedAt,
  handling: 'ASSISTANT',
  agentId: null,
  agentName: null,
  handoffRequestedAt: null,
  channel: 'WHATSAPP',
  contactId: '5215551111',
  contactName: 'Lucía',
});

const CURRENT = { ...conversation('conv-2', '2026-11-10T10:00:00Z'), status: 'OPEN' as const };
const EARLIER = conversation('conv-1', '2026-11-02T10:00:00Z');

const CUSTOMER: Customer = {
  channel: 'WHATSAPP',
  contactId: '5215551111',
  contactName: 'Lucía',
  displayName: null,
  notes: '',
  remember: true,
  updatedAt: null,
  updatedByName: null,
  conversations: 2,
  firstSeenAt: EARLIER.startedAt,
  lastSeenAt: CURRENT.lastActivityAt,
  history: [CURRENT, EARLIER],
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

describe('Customers', () => {
  it('shows a WhatsApp customer across conversations and keeps the team notes', async () => {
    let saved: unknown = null;
    stubApi({
      ...baseRoutes(),
      'GET /api/v1/conversations/conv-2': () => json({ conversation: CURRENT, messages: [] }),
      'GET /api/v1/conversations/conv-2/customer': () => json(CUSTOMER),
      'PUT /api/v1/conversations/conv-2/customer': ({ body }) => {
        saved = body;
        return json({
          ...CUSTOMER,
          ...(body as object),
          updatedAt: '2026-11-11T10:00:00Z',
          updatedByName: 'Ana Pérez',
        });
      },
    });
    const user = userEvent.setup();
    renderAt('/conversations/conv-2');

    const summary = await screen.findByText(/Cliente · 2 conversaciones desde/);
    await user.click(summary);
    const card = summary.closest('details')!;
    // Only the other conversations are listed, each one a link to it
    expect(within(card).getAllByRole('link')).toHaveLength(1);
    expect(within(card).getByRole('link').getAttribute('href')).toBe('/conversations/conv-1');

    const form = within(card).getByRole('form', { name: 'Datos del cliente' });
    await user.type(within(form).getByRole('textbox', { name: 'Nombre para el equipo' }), 'Lucía Pérez');
    await user.type(within(form).getByRole('textbox', { name: /Notas del equipo/ }), 'Prefiere la tarde');
    await user.click(within(form).getByRole('checkbox', { name: /recuerda sus conversaciones/ }));
    await user.click(within(form).getByRole('button', { name: 'Guardar' }));

    expect(await within(card).findByText(/Último cambio de Ana Pérez/)).toBeDefined();
    expect(saved).toEqual({ displayName: 'Lucía Pérez', notes: 'Prefiere la tarde', remember: false });
  });

  it('web visitors have no customer card', async () => {
    stubApi({
      ...baseRoutes(),
      'GET /api/v1/conversations/conv-web': () =>
        json({
          conversation: { ...CURRENT, id: 'conv-web', channel: 'WEB', contactId: null, contactName: null, origin: 'https://shop.example.com' },
          messages: [],
        }),
    });
    renderAt('/conversations/conv-web');

    expect(await screen.findByRole('heading', { name: 'shop.example.com' })).toBeDefined();
    expect(screen.queryByText(/Cliente ·/)).toBeNull();
  });

  it('lists customers, searches them and opens their latest conversation', async () => {
    const searches: (string | null)[] = [];
    stubApi({
      ...baseRoutes(),
      'GET /api/v1/customers': ({ url }) => {
        searches.push(new URL(url).searchParams.get('query'));
        return json({
          ...empty,
          total: 1,
          items: [
            {
              channel: 'WHATSAPP',
              contactId: '5215551111',
              contactName: 'Lucía',
              displayName: 'Lucía Pérez',
              conversations: 2,
              firstSeenAt: EARLIER.startedAt,
              lastSeenAt: CURRENT.lastActivityAt,
              latestConversationId: 'conv-2',
            },
          ],
        });
      },
    });
    const user = userEvent.setup();
    renderAt('/customers');

    const link = await screen.findByRole('link', { name: /Lucía Pérez/ });
    expect(link.getAttribute('href')).toBe('/conversations/conv-2');
    expect(screen.getByText('WhatsApp · +5215551111')).toBeDefined();

    await user.type(screen.getByRole('searchbox'), '5551111');
    await user.click(screen.getByRole('button', { name: 'Buscar' }));
    await screen.findByRole('link', { name: /Lucía Pérez/ });
    expect(searches).toEqual([null, '5551111']);
  });
});

describe('Conversation topics', () => {
  it('filters conversations by topic and lets the team correct them', async () => {
    const lists: (string | null)[] = [];
    let saved: unknown = null;
    const tagged = { ...CURRENT, intents: ['SHIPPING', 'PRICING'] as ConversationSummary['intents'] };
    stubApi({
      ...baseRoutes(),
      'GET /api/v1/conversations': ({ url }) => {
        lists.push(new URL(url).searchParams.get('intent'));
        return json({ ...empty, total: 1, items: [tagged] });
      },
      'GET /api/v1/conversations/conv-2': () => json({ conversation: tagged, messages: [] }),
      'GET /api/v1/conversations/conv-2/customer': () => json(CUSTOMER),
      'PUT /api/v1/conversations/conv-2/intents': ({ body }) => {
        saved = body;
        return json({ ...tagged, intents: (body as { intents: string[] }).intents, intentsEdited: true });
      },
    });
    const user = userEvent.setup();
    renderAt('/conversations/conv-2');

    const heading = await screen.findByRole('heading', { name: 'Lucía' });
    const detail = heading.closest('article')!;
    expect(within(detail).getByText('Envíos')).toBeDefined();
    expect(within(detail).getByText('Precios y promociones')).toBeDefined();

    await user.selectOptions(screen.getByRole('combobox', { name: 'Tema' }), 'RETURNS');
    expect(lists).toContain('RETURNS');

    await user.click(within(detail).getByRole('button', { name: 'Cambiar temas' }));
    const form = within(detail).getByRole('form', { name: 'Temas de la conversación' });
    await user.click(within(form).getByRole('checkbox', { name: 'Precios y promociones' }));
    await user.click(within(form).getByRole('checkbox', { name: 'Devoluciones' }));
    await user.click(within(form).getByRole('button', { name: 'Guardar temas' }));

    expect(await within(detail).findByText('(elegidos por el equipo)')).toBeDefined();
    // Sent in the panel's fixed order, whatever order they were ticked in
    expect(saved).toEqual({ intents: ['SHIPPING', 'RETURNS'] });
  });
});
