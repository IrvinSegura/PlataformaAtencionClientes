import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router';
import { describe, expect, it } from 'vitest';
import type { AttentionAlert, AttentionSettings, ConversationSummary } from './api/conversations';
import { errorMessage } from './api/error-messages';
import { ApiError } from './api/http-client';
import type { WorkspaceRole } from './api/workspaces';
import { App, createQueryClient } from './app';
import { json, problem, session, stubApi, type Handler } from './test/fake-api';

const empty = { items: [], page: 0, size: 20, total: 0 };

const DEFAULTS: AttentionSettings = {
  handOffFrustrated: true,
  alertsEnabled: false,
  alertPhones: [],
  alertOnRequest: true,
  alertOnFrustration: true,
  alertOnDifficult: true,
  alertOnUsage: true,
  template: null,
  templateLanguage: null,
};

const CONNECTED = {
  phoneNumberId: '123456789012345',
  displayPhoneNumber: '+52 55 0000 0000',
  enabled: true,
  webhookPath: '/api/v1/channels/whatsapp/key/webhook',
  verifyToken: 'verify',
  model: 'AUTOMATIC',
  updatedAt: '2026-11-10T10:00:00Z',
};

const routes = (role: WorkspaceRole = 'OWNER', extra: Record<string, Handler> = {}): Record<string, Handler> => ({
  'POST /api/v1/auth/refresh': () => json(session('token', role)),
  'GET /api/v1/me': () => json({ id: 'user-1', email: 'ana@mitienda.com', displayName: 'Ana Pérez' }),
  'GET /api/v1/me/workspaces': () => json([{ workspaceId: 'ws-1', workspaceName: 'Mi Tienda', role }]),
  'GET /api/v1/workspace': () => json({ id: 'ws-1', name: 'Mi Tienda' }),
  'GET /api/v1/conversations': () => json(empty),
  'GET /api/v1/conversations/events': () =>
    new Response('', { status: 200, headers: { 'Content-Type': 'text/event-stream' } }),
  'GET /api/v1/assistant/attention': () => json(DEFAULTS),
  'GET /api/v1/assistant/attention/alerts': () => json([]),
  'GET /api/v1/channels/whatsapp': () => json(CONNECTED),
  ...extra,
});

function renderAt(path: string) {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <App queryClient={createQueryClient()} />
    </MemoryRouter>,
  );
}

function alert(changes: Partial<AttentionAlert>): AttentionAlert {
  return {
    id: 'alert-1',
    conversationId: 'conv-1',
    channel: 'WHATSAPP',
    contactId: '5215551111',
    contactName: 'Lucía',
    reason: 'FRUSTRATED',
    excerpt: 'Esto no sirve para nada',
    createdAt: new Date().toISOString(),
    deliveries: [],
    ...changes,
  };
}

describe('Avisos y escalamiento', () => {
  it('turns on WhatsApp alerts with numbers and a template', async () => {
    const saved: unknown[] = [];
    stubApi(
      routes('OWNER', {
        'PUT /api/v1/assistant/attention': ({ body }) => {
          saved.push(body);
          return json({ ...(body as AttentionSettings), alertPhones: ['525512345678', '14155550100'] });
        },
      }),
    );
    const user = userEvent.setup();
    renderAt('/attention');

    expect(await screen.findByRole('heading', { name: 'Avisos y escalamiento' })).toBeDefined();
    expect(await screen.findByText('Avisos apagados')).toBeDefined();
    expect(screen.getByLabelText('Número 1').matches(':disabled')).toBe(true);

    await user.click(screen.getByLabelText('Avisarme por WhatsApp'));
    await user.type(screen.getByLabelText('Número 1'), '+52 55 1234 5678');
    await user.click(screen.getByRole('button', { name: /Agregar número/ }));
    await user.type(screen.getByLabelText('Número 2'), '+1 415 555 0100');
    await user.click(screen.getByLabelText('Un cliente se nota molesto'));
    await user.click(screen.getByText('Plantilla aprobada (opcional)'));
    await user.type(screen.getByLabelText('Nombre de la plantilla'), 'alerta_cliente');
    await user.type(screen.getByLabelText('Idioma'), 'es_MX');
    await user.click(screen.getByLabelText('Pasar al equipo a los clientes que se notan molestos'));
    await user.click(screen.getByRole('button', { name: 'Guardar cambios' }));

    expect(await screen.findByText('Cambios guardados.')).toBeDefined();
    expect(saved).toEqual([
      {
        handOffFrustrated: false,
        alertsEnabled: true,
        alertPhones: ['+52 55 1234 5678', '+1 415 555 0100'],
        alertOnRequest: true,
        alertOnFrustration: false,
        alertOnDifficult: true,
        alertOnUsage: true,
        template: 'alerta_cliente',
        templateLanguage: 'es_MX',
      },
    ]);
    expect(screen.getByText('Avisos activos')).toBeDefined();
  });

  it('lists recent alerts with whether each one arrived', async () => {
    stubApi(
      routes('OWNER', {
        'GET /api/v1/assistant/attention': () => json({ ...DEFAULTS, alertsEnabled: true, alertPhones: ['5215550000'] }),
        'GET /api/v1/assistant/attention/alerts': () =>
          json([
            alert({
              deliveries: [
                { recipient: '5215550000', status: 'SENT', detail: null, deliveredAt: '2026-11-10T10:00:01Z' },
                {
                  recipient: '5215559999',
                  status: 'REJECTED',
                  detail: 'WhatsApp no lo entregó: ese número no le ha escrito al negocio en las últimas 24 horas.',
                  deliveredAt: '2026-11-10T10:00:01Z',
                },
              ],
            }),
            alert({
              id: 'alert-2',
              conversationId: 'conv-2',
              channel: 'WEB',
              contactId: null,
              contactName: null,
              reason: 'ASKED_FOR_PERSON',
              excerpt: null,
            }),
          ]),
      }),
    );
    renderAt('/attention');

    const table = await screen.findByRole('table');
    const rows = within(table).getAllByRole('row');
    expect(within(rows[1]!).getByText('Lucía')).toBeDefined();
    expect(within(rows[1]!).getByText('Cliente molesto')).toBeDefined();
    expect(within(rows[1]!).getByText('Entregado')).toBeDefined();
    expect(within(rows[1]!).getByText('Rechazado')).toBeDefined();
    expect(within(rows[1]!).getByText(/últimas 24 horas/)).toBeDefined();
    expect(within(rows[1]!).getByRole('link', { name: 'Abrir' }).getAttribute('href')).toBe('/conversations/conv-1');
    expect(within(rows[2]!).getByText('Visitante del sitio web')).toBeDefined();
    expect(within(rows[2]!).getByText('Pidió una persona')).toBeDefined();
    expect(within(rows[2]!).getByText('Sin aviso')).toBeDefined();

    const stats = screen.getByRole('list', { name: 'Resumen de avisos' });
    expect(within(stats).getByText('1 de 2')).toBeDefined();
  });

  it('warns that alerts need a connected WhatsApp number', async () => {
    stubApi(routes('OWNER', { 'GET /api/v1/channels/whatsapp': () => problem(404, 'WHATSAPP_CHANNEL_NOT_FOUND') }));
    renderAt('/attention');

    expect(await screen.findByText(/no hay uno conectado y activo/)).toBeDefined();
    expect(screen.getByRole('link', { name: 'Conectar WhatsApp' }).getAttribute('href')).toBe('/channels/whatsapp');
  });

  it('is only for owners and administrators', async () => {
    stubApi(routes('AGENT'));
    renderAt('/conversations');

    expect(await screen.findByRole('link', { name: /Conversaciones/ })).toBeDefined();
    expect(screen.queryByRole('link', { name: /Avisos y escalamiento/ })).toBeNull();
  });

  it('marks upset customers in the inbox', async () => {
    const upset: ConversationSummary = {
      id: 'conv-1',
      status: 'OPEN',
      questionCount: 1,
      origin: null,
      startedAt: '2026-11-10T10:00:00Z',
      lastActivityAt: '2026-11-10T10:00:00Z',
      handling: 'WAITING',
      agentId: null,
      agentName: null,
      handoffRequestedAt: '2026-11-10T10:00:00Z',
      channel: 'WHATSAPP',
      contactId: '5215551111',
      contactName: 'Lucía',
      frustratedAt: '2026-11-10T10:00:00Z',
    };
    stubApi(routes('OWNER', { 'GET /api/v1/conversations': () => json({ ...empty, items: [upset], total: 1 }) }));
    renderAt('/conversations');

    expect(await screen.findByText('Cliente molesto')).toBeDefined();
  });

  it('explains invalid settings', () => {
    expect(errorMessage(new ApiError(422, 'INVALID_ATTENTION_SETTINGS', 'invalid'))).toMatch(/8 a 15 dígitos/);
  });
});
