import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router';
import { beforeEach, describe, expect, it } from 'vitest';
import type { ConversationSummary } from './api/conversations';
import type { FollowUp, FollowUpTemplate } from './api/follow-ups';
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

const TEMPLATE: FollowUpTemplate = {
  id: 'tpl-1',
  name: 'Pedido entregado',
  body: 'Hola {nombre}, ¿te llegó tu pedido?',
  whatsapp: { name: 'seguimiento_pedido', language: 'es_MX', nameParameter: true },
  createdAt: '2026-11-01T10:00:00Z',
  updatedAt: '2026-11-01T10:00:00Z',
};

const CONVERSATION: ConversationSummary = {
  id: 'conv-1',
  status: 'OPEN',
  questionCount: 1,
  origin: null,
  startedAt: '2026-11-10T10:00:00Z',
  lastActivityAt: '2026-11-10T10:00:00Z',
  handling: 'ASSISTANT',
  agentId: null,
  agentName: null,
  handoffRequestedAt: null,
  channel: 'WHATSAPP',
  contactId: '5215551111',
  contactName: 'Lucía',
};

const followUp = (overrides: Partial<FollowUp>): FollowUp => ({
  id: 'fu-1',
  conversationId: 'conv-1',
  templateId: 'tpl-1',
  templateName: 'Pedido entregado',
  text: TEMPLATE.body,
  whatsappTemplate: 'seguimiento_pedido',
  dueAt: '2026-11-14T16:00:00Z',
  status: 'SCHEDULED',
  failure: null,
  createdByName: 'Ana Pérez',
  createdAt: '2026-11-11T10:00:00Z',
  finishedAt: null,
  sentConversationId: null,
  channel: 'WHATSAPP',
  contactId: '5215551111',
  contactName: 'Lucía',
  ...overrides,
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

describe('Follow-ups', () => {
  it('schedules a follow-up from a template in the conversation', async () => {
    let scheduled: { templateId: string | null; text: string | null; dueAt: string } | null = null;
    stubApi({
      ...baseRoutes('AGENT'),
      'GET /api/v1/conversations/conv-1': () => json({ conversation: CONVERSATION, messages: [] }),
      'GET /api/v1/conversations/conv-1/customer': () =>
        json({
          channel: 'WHATSAPP',
          contactId: '5215551111',
          contactName: 'Lucía',
          displayName: null,
          notes: '',
          remember: true,
          updatedAt: null,
          updatedByName: null,
          conversations: 1,
          firstSeenAt: CONVERSATION.startedAt,
          lastSeenAt: CONVERSATION.lastActivityAt,
          history: [CONVERSATION],
        }),
      'GET /api/v1/conversations/conv-1/follow-ups': () => json(scheduled ? [followUp({})] : []),
      'GET /api/v1/follow-up-templates': () => json([TEMPLATE]),
      'POST /api/v1/conversations/conv-1/follow-ups': ({ body }) => {
        scheduled = body as typeof scheduled;
        return json(followUp({}), 201);
      },
    });
    const user = userEvent.setup();
    renderAt('/conversations/conv-1');

    const detail = (await screen.findByRole('heading', { name: 'Lucía' })).closest('article')!;
    await user.click(await within(detail).findByText('Seguimientos'));
    const form = within(detail).getByRole('form', { name: 'Programar seguimiento' });
    await user.selectOptions(await within(form).findByRole('combobox', { name: 'Plantilla' }), 'tpl-1');
    expect(within(form).getByText(TEMPLATE.body)).toBeDefined();
    await user.click(within(form).getByRole('button', { name: 'En una semana' }));
    await user.click(within(form).getByRole('button', { name: 'Programar seguimiento' }));

    expect(await within(detail).findByText(/1 programado/)).toBeDefined();
    expect(scheduled!.templateId).toBe('tpl-1');
    expect(scheduled!.text).toBeNull();
    const inAWeek = new Date(scheduled!.dueAt).getTime() - Date.now();
    expect(inAWeek).toBeGreaterThan(5 * 24 * 3600 * 1000);
    expect(inAWeek).toBeLessThan(8 * 24 * 3600 * 1000);
  });

  it('lists follow-ups by status, explains failures and lets managers keep templates', async () => {
    let created: unknown = null;
    stubApi({
      ...baseRoutes('OWNER'),
      'GET /api/v1/follow-ups': ({ url }) => {
        const status = new URL(url).searchParams.get('status');
        return json(
          status === 'FAILED'
            ? { ...empty, total: 1, items: [followUp({ status: 'FAILED', failure: 'WINDOW_CLOSED_NO_TEMPLATE', finishedAt: '2026-11-14T16:00:00Z' })] }
            : { ...empty, total: 1, items: [followUp({})] },
        );
      },
      'GET /api/v1/follow-up-templates': () => json(created ? [TEMPLATE] : []),
      'POST /api/v1/follow-up-templates': ({ body }) => {
        created = body;
        return json(TEMPLATE, 201);
      },
    });
    const user = userEvent.setup();
    renderAt('/follow-ups');

    const row = (await screen.findByRole('link', { name: 'Lucía' })).closest('tr')!;
    expect(within(row).getByText('Programado')).toBeDefined();
    await user.click(screen.getByRole('tab', { name: 'No enviados' }));
    expect(await screen.findByText(/WhatsApp solo entrega una plantilla aprobada/)).toBeDefined();

    await user.click(screen.getByRole('button', { name: 'Nueva plantilla' }));
    const form = screen.getByRole('form', { name: 'Nueva plantilla' });
    await user.type(within(form).getByRole('textbox', { name: 'Nombre' }), 'Pedido entregado');
    await user.type(within(form).getByRole('textbox', { name: 'Mensaje' }), 'Hola {{nombre}, ¿te llegó tu pedido?');
    await user.type(within(form).getByRole('textbox', { name: 'Nombre de la plantilla aprobada' }), 'seguimiento_pedido');
    await user.type(within(form).getByRole('textbox', { name: 'Idioma' }), 'es_MX');
    await user.click(within(form).getByRole('checkbox', { name: /nombre del cliente/ }));
    await user.click(within(form).getByRole('button', { name: 'Guardar plantilla' }));

    expect(await screen.findByText(/se envía la plantilla aprobada «seguimiento_pedido»/)).toBeDefined();
    expect(created).toEqual({
      name: 'Pedido entregado',
      body: 'Hola {nombre}, ¿te llegó tu pedido?',
      whatsappTemplate: 'seguimiento_pedido',
      whatsappLanguage: 'es_MX',
      whatsappNameParameter: true,
    });
  });

  it('agents schedule follow-ups but do not edit templates', async () => {
    stubApi({
      ...baseRoutes('AGENT'),
      'GET /api/v1/follow-ups': () => json(empty),
      'GET /api/v1/follow-up-templates': () => json([TEMPLATE]),
    });
    renderAt('/follow-ups');

    expect(await screen.findByText('Pedido entregado')).toBeDefined();
    expect(screen.queryByRole('button', { name: 'Nueva plantilla' })).toBeNull();
    expect(screen.queryByRole('button', { name: 'Editar' })).toBeNull();
  });
});
