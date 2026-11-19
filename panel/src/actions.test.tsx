import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router';
import { beforeEach, describe, expect, it } from 'vitest';
import type { AssistantAction } from './api/actions';
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

const orderStatus: AssistantAction = {
  id: 'act-1',
  name: 'order_status',
  title: 'Consultar un pedido',
  description: 'Consulta el estado de un pedido por su número.',
  endpointUrl: 'https://api.mitienda.com/orders/status',
  parameters: [{ name: 'order_number', type: 'STRING', description: 'Número de pedido', required: true, pattern: null }],
  requiresConfirmation: false,
  enabled: true,
  createdAt: '2026-11-09T10:00:00Z',
  updatedAt: '2026-11-09T10:00:00Z',
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

describe('Assistant actions', () => {
  it('lets a manager describe a new action and the data it needs', async () => {
    let actions: AssistantAction[] = [];
    let sent: Record<string, unknown> | null = null;
    stubApi({
      ...baseRoutes(),
      'GET /api/v1/actions': () => json(actions),
      'POST /api/v1/actions': ({ body }) => {
        sent = body as Record<string, unknown>;
        actions = [{ ...orderStatus, ...(body as object) }];
        return json(actions[0], 201);
      },
    });
    const user = userEvent.setup();
    renderAt('/actions');

    await user.click(await screen.findByRole('button', { name: 'Nueva acción' }));
    await user.type(screen.getByLabelText('Nombre para mostrar'), 'Consultar un pedido');
    await user.type(screen.getByLabelText(/^Identificador/), 'order_status');
    await user.type(screen.getByLabelText('Cuándo usarla'), 'Consulta el estado de un pedido por su número.');
    const url = screen.getByLabelText(/^URL del webhook/);
    await user.clear(url);
    await user.type(url, 'https://api.mitienda.com/orders/status');
    await user.click(screen.getByRole('button', { name: 'Agregar dato' }));
    const parameter = screen.getByRole('group', { name: 'Dato 1' });
    await user.type(within(parameter).getByLabelText('Nombre'), 'order_number');
    await user.type(within(parameter).getByLabelText('Descripción'), 'Número de pedido');
    await user.click(screen.getByRole('button', { name: 'Guardar' }));

    expect(await screen.findByText('order_status')).toBeDefined();
    expect(sent).toMatchObject({
      name: 'order_status',
      endpointUrl: 'https://api.mitienda.com/orders/status',
      requiresConfirmation: false,
      parameters: [{ name: 'order_number', type: 'STRING', required: true, pattern: null }],
    });
    expect(screen.queryByRole('form', { name: 'Nueva acción' })).toBeNull();
  });

  it('shows agents the actions without letting them change anything', async () => {
    stubApi({ ...baseRoutes('AGENT'), 'GET /api/v1/actions': () => json([orderStatus]) });
    renderAt('/actions');

    expect(await screen.findByText('Consultar un pedido')).toBeDefined();
    expect(screen.queryByRole('button', { name: 'Nueva acción' })).toBeNull();
    expect(screen.queryByRole('button', { name: /Editar/ })).toBeNull();
    expect(screen.queryByRole('button', { name: 'Mostrar secreto' })).toBeNull();
    // Channels and billing are for managers only
    expect(screen.queryByRole('link', { name: /WhatsApp/ })).toBeNull();
    expect(screen.queryByRole('link', { name: /Consumo/ })).toBeNull();
  });

  it('reveals the signing secret on request', async () => {
    stubApi({
      ...baseRoutes(),
      'GET /api/v1/actions': () => json([orderStatus]),
      'GET /api/v1/actions/signing-secret': () =>
        json({ value: 'whsec_123', rotatedAt: '2026-11-09T10:00:00Z' }),
    });
    const user = userEvent.setup();
    renderAt('/actions');

    await user.click(await screen.findByRole('button', { name: 'Mostrar secreto' }));
    expect(await screen.findByText('whsec_123')).toBeDefined();
    expect(screen.getByRole('button', { name: /Cambiar secreto/ })).toBeDefined();
  });
});
