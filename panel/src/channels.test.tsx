import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import type { Integration } from './api/actions';
import type { EmailChannel, MetaChannel } from './api/channels';
import type { ConversationSummary } from './api/conversations';
import { endSession } from './api/http-client';
import type { WorkspaceRole } from './api/workspaces';
import { App, createQueryClient } from './app';
import { channelOf, customerOf } from './pages/conversations-page';
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

const MAILBOX: EmailChannel = {
  address: 'soporte@mitienda.com',
  displayName: 'Soporte Mi Tienda',
  imapHost: 'imap.gmail.com',
  imapPort: 993,
  smtpHost: 'smtp.gmail.com',
  smtpPort: 465,
  username: 'soporte@mitienda.com',
  enabled: true,
  checkedAt: null,
  lastError: null,
  updatedAt: '2026-11-10T10:00:00Z',
};

function renderAt(path: string) {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <App queryClient={createQueryClient()} />
    </MemoryRouter>,
  );
}

function conversation(changes: Partial<ConversationSummary>): ConversationSummary {
  return {
    id: 'conv-1',
    status: 'OPEN',
    questionCount: 1,
    origin: null,
    startedAt: '2026-11-10T10:00:00Z',
    lastActivityAt: '2026-11-10T10:05:00Z',
    handling: 'ASSISTANT',
    agentId: null,
    agentName: null,
    handoffRequestedAt: null,
    channel: 'WEB',
    contactId: null,
    contactName: null,
    ...changes,
  };
}

beforeEach(() => {
  endSession();
  vi.restoreAllMocks();
});

describe('Email channel', () => {
  it('connects a Gmail mailbox with an app password, starting from Gmail’s servers', async () => {
    let sent: Record<string, unknown> | null = null;
    stubApi({
      ...baseRoutes(),
      'GET /api/v1/channels/email': () => problem(404, 'EMAIL_CHANNEL_NOT_FOUND'),
      'PUT /api/v1/channels/email': ({ body }) => {
        sent = body as Record<string, unknown>;
        return json(MAILBOX);
      },
    });
    const user = userEvent.setup();
    renderAt('/channels/email');

    await user.type(await screen.findByLabelText('Dirección de soporte'), 'soporte@mitienda.com');
    await user.type(screen.getByLabelText('Nombre del remitente'), 'Soporte Mi Tienda');
    expect(screen.getByLabelText<HTMLInputElement>('Servidor IMAP').value).toBe('imap.gmail.com');
    expect(screen.getByLabelText<HTMLInputElement>(/^Puerto SMTP/).value).toBe('465');
    await user.type(screen.getByLabelText(/^Contraseña/), 'abcd efgh ijkl mnop');
    await user.click(screen.getByRole('button', { name: 'Conectar' }));

    expect(await screen.findByText(/Buzón guardado/)).toBeDefined();
    expect(screen.getByText('Aún no se ha leído.')).toBeDefined();
    expect(sent).toEqual({
      address: 'soporte@mitienda.com',
      displayName: 'Soporte Mi Tienda',
      imapHost: 'imap.gmail.com',
      imapPort: 993,
      smtpHost: 'smtp.gmail.com',
      smtpPort: 465,
      username: '',
      password: 'abcd efgh ijkl mnop',
      enabled: true,
    });
  });

  it('shows why the mailbox could not be read and keeps the saved password when left blank', async () => {
    let sent: Record<string, unknown> | null = null;
    stubApi({
      ...baseRoutes(),
      'GET /api/v1/channels/email': () =>
        json({
          ...MAILBOX,
          checkedAt: '2026-11-10T10:05:00Z',
          lastError: 'The IMAP server refused the username or password',
        }),
      'PUT /api/v1/channels/email': ({ body }) => {
        sent = body as Record<string, unknown>;
        return json(MAILBOX);
      },
    });
    const user = userEvent.setup();
    renderAt('/channels/email');

    expect(await screen.findByText(/refused the username or password/)).toBeDefined();
    expect(screen.getByText('Con errores')).toBeDefined();
    expect(screen.getByText(/Déjala vacía para conservar la guardada/)).toBeDefined();
    await user.click(screen.getByRole('button', { name: 'Guardar cambios' }));

    await screen.findByText(/Buzón guardado/);
    expect(sent).toMatchObject({ password: '', username: '' });
  });

  it('explains an invalid mailbox', async () => {
    stubApi({
      ...baseRoutes(),
      'GET /api/v1/channels/email': () => problem(404, 'EMAIL_CHANNEL_NOT_FOUND'),
      'PUT /api/v1/channels/email': () => problem(422, 'INVALID_EMAIL_CHANNEL'),
    });
    const user = userEvent.setup();
    renderAt('/channels/email');

    await user.type(await screen.findByLabelText('Dirección de soporte'), 'soporte@mitienda.com');
    await user.type(screen.getByLabelText(/^Contraseña/), 'x');
    await user.click(screen.getByRole('button', { name: 'Conectar' }));

    expect(await screen.findByText(/Revisa los datos del buzón/)).toBeDefined();
  });
});

describe('Messenger and Instagram', () => {
  it('connects a Facebook Page and shows the webhook to set up at Meta', async () => {
    let sent: Record<string, unknown> | null = null;
    stubApi({
      ...baseRoutes(),
      'GET /api/v1/channels/messenger': () => problem(404, 'META_CHANNEL_NOT_FOUND'),
      'PUT /api/v1/channels/messenger': ({ body }) => {
        sent = body as Record<string, unknown>;
        return json({
          platform: 'MESSENGER',
          accountId: '1000001',
          displayName: 'Mi Tienda',
          enabled: true,
          webhookPath: '/api/v1/channels/messenger/key-1/webhook',
          verifyToken: 'verify-123',
          updatedAt: '2026-11-10T10:00:00Z',
        } satisfies MetaChannel);
      },
    });
    const user = userEvent.setup();
    renderAt('/channels/messenger');

    expect(await screen.findByRole('heading', { name: 'Messenger' })).toBeDefined();
    await user.type(await screen.findByLabelText(/^ID de la página/), '1000001');
    await user.type(screen.getByLabelText('Nombre para mostrar'), 'Mi Tienda');
    await user.type(screen.getByLabelText(/^Token de acceso/), 'token-abc');
    await user.type(screen.getByLabelText(/^Secreto de la app/), 'secret-abc');
    await user.click(screen.getByRole('button', { name: 'Conectar' }));

    expect(await screen.findByText(/\/api\/v1\/channels\/messenger\/key-1\/webhook$/)).toBeDefined();
    expect(screen.getByText('verify-123')).toBeDefined();
    expect(screen.getByText(/24 horas/)).toBeDefined();
    expect(sent).toEqual({
      accountId: '1000001',
      displayName: 'Mi Tienda',
      accessToken: 'token-abc',
      appSecret: 'secret-abc',
      enabled: true,
    });
  });

  it('uses the same page for Instagram, against its own endpoint', async () => {
    stubApi({
      ...baseRoutes(),
      'GET /api/v1/channels/instagram': () => problem(404, 'META_CHANNEL_NOT_FOUND'),
      'PUT /api/v1/channels/instagram': () => problem(422, 'INVALID_META_CHANNEL'),
    });
    const user = userEvent.setup();
    renderAt('/channels/instagram');

    expect(await screen.findByRole('heading', { name: 'Instagram' })).toBeDefined();
    await user.type(await screen.findByLabelText(/^ID de la cuenta de Instagram/), '1000002');
    await user.type(screen.getByLabelText(/^Token de acceso/), 'token-abc');
    await user.type(screen.getByLabelText(/^Secreto de la app/), 'secret-abc');
    await user.click(screen.getByRole('button', { name: 'Conectar' }));

    expect(await screen.findByText(/el identificador es el número que muestra Meta/)).toBeDefined();
  });

  it('offers the channels to owners and administrators only', async () => {
    stubApi(baseRoutes('AGENT'));
    renderAt('/conversations');

    expect(await screen.findByRole('link', { name: 'Widget' })).toBeDefined();
    expect(screen.queryByRole('link', { name: 'Correo' })).toBeNull();
    expect(screen.queryByRole('link', { name: 'Instagram' })).toBeNull();
  });
});

describe('Conversations from every channel', () => {
  it('names the customer and the channel they wrote from', async () => {
    expect(customerOf(conversation({ channel: 'EMAIL', contactId: 'lucia@example.com' }))).toEqual({
      label: 'lucia@example.com',
      icon: 'Mail',
    });
    expect(customerOf(conversation({ channel: 'INSTAGRAM', contactId: '2001' })).label).toBe(
      'Cliente de Instagram',
    );
    expect(channelOf(conversation({ channel: 'EMAIL', contactId: 'lucia@example.com' }))).toBe(
      'Correo · lucia@example.com',
    );
    expect(channelOf(conversation({ channel: 'MESSENGER', contactId: '2001' }))).toBe('Messenger');

    stubApi({
      ...baseRoutes(),
      'GET /api/v1/conversations': () =>
        json({
          ...empty,
          items: [conversation({ channel: 'MESSENGER', contactId: '2001', contactName: 'Lucía Pérez' })],
          total: 1,
        }),
    });
    renderAt('/conversations');
    expect(await screen.findByText('Lucía Pérez')).toBeDefined();
  });
});

describe('Integrations', () => {
  const shopify: Integration = {
    provider: 'SHOPIFY',
    connected: false,
    account: null,
    username: null,
    enabled: false,
    actions: [
      { name: 'shopify_order_status', title: 'Consultar un pedido', description: '', parameters: [], requiresConfirmation: false },
      { name: 'shopify_cancel_order', title: 'Cancelar un pedido', description: '', parameters: [], requiresConfirmation: true },
    ],
    updatedAt: null,
  };
  const zendesk: Integration = {
    provider: 'ZENDESK',
    connected: true,
    account: 'mitienda',
    username: 'agente@mitienda.com',
    enabled: true,
    actions: [
      { name: 'zendesk_create_ticket', title: 'Crear un ticket de soporte', description: '', parameters: [], requiresConfirmation: true },
    ],
    updatedAt: '2026-11-10T10:00:00Z',
  };

  it('connects Shopify with its token and lists the actions it gives the assistant', async () => {
    let sent: Record<string, unknown> | null = null;
    stubApi({
      ...baseRoutes(),
      'GET /api/v1/actions/integrations': () => json([shopify, zendesk]),
      'PUT /api/v1/actions/integrations/shopify': ({ body }) => {
        sent = body as Record<string, unknown>;
        return json({ ...shopify, connected: true, account: 'mitienda.myshopify.com', enabled: true, updatedAt: '2026-11-10T11:00:00Z' });
      },
    });
    const user = userEvent.setup();
    renderAt('/actions/integrations');

    const card = await screen.findByRole('form', { name: 'Shopify' });
    expect(within(card).getByText('Cancelar un pedido')).toBeDefined();
    expect(within(card).getByText('Pide confirmación')).toBeDefined();
    await user.type(within(card).getByLabelText('Tienda'), 'mitienda');
    await user.type(within(card).getByLabelText(/^Token de acceso/), 'shpat_123');
    await user.click(within(card).getByRole('button', { name: 'Conectar Shopify' }));

    expect(await screen.findByText('Shopify quedó conectada.')).toBeDefined();
    expect(sent).toEqual({ account: 'mitienda', username: '', token: 'shpat_123', enabled: true });
    const zendeskCard = screen.getByRole('form', { name: 'Zendesk' });
    expect(within(zendeskCard).getByText('Conectada')).toBeDefined();
    expect(within(zendeskCard).getByLabelText<HTMLInputElement>('Correo del agente').value).toBe('agente@mitienda.com');
  });

  it('explains an invalid store', async () => {
    stubApi({
      ...baseRoutes(),
      'GET /api/v1/actions/integrations': () => json([shopify, zendesk]),
      'PUT /api/v1/actions/integrations/shopify': () => problem(422, 'INVALID_INTEGRATION'),
    });
    const user = userEvent.setup();
    renderAt('/actions/integrations');

    const card = await screen.findByRole('form', { name: 'Shopify' });
    await user.type(within(card).getByLabelText('Tienda'), 'mitienda.com');
    await user.type(within(card).getByLabelText(/^Token de acceso/), 'shpat_123');
    await user.click(within(card).getByRole('button', { name: 'Conectar Shopify' }));

    expect(await within(card).findByText(/mitienda\.myshopify\.com, la cuenta de Zendesk/)).toBeDefined();
  });
});
