import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router';
import { beforeEach, describe, expect, it } from 'vitest';
import { endSession } from './api/http-client';
import { App, createQueryClient } from './app';
import { json, problem, session, stubApi, type Handler } from './test/fake-api';

const empty = { items: [], page: 0, size: 20, total: 0 };

function signedIn(termsAccepted: boolean): Record<string, Handler> {
  return {
    'POST /api/v1/auth/refresh': () => json(session('token', 'OWNER')),
    'GET /api/v1/me': () =>
      json({ id: 'user-1', email: 'ana@mitienda.com', displayName: 'Ana Pérez', termsVersion: '2026-11-11', termsAccepted }),
    'GET /api/v1/me/workspaces': () => json([{ workspaceId: 'ws-1', workspaceName: 'Mi Tienda', role: 'OWNER' }]),
    'GET /api/v1/workspace': () => json({ id: 'ws-1', name: 'Mi Tienda' }),
    'GET /api/v1/conversations': () => json(empty),
    'GET /api/v1/conversations/events': () =>
      new Response('', { status: 200, headers: { 'Content-Type': 'text/event-stream' } }),
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

describe('Terms of service', () => {
  it('are accepted on signing up, with both legal pages a click away', async () => {
    let registered: unknown = null;
    let session_ = false;
    stubApi({
      ...signedIn(true),
      'POST /api/v1/auth/refresh': () =>
        session_ ? json(session('token', 'OWNER')) : problem(401, 'INVALID_REFRESH_TOKEN'),
      'POST /api/v1/auth/register': ({ body }) => {
        registered = body;
        session_ = true;
        return json(session('token', 'OWNER'), 201);
      },
    });
    const user = userEvent.setup();
    renderAt('/register');

    const terms = await screen.findByRole('checkbox', { name: /Acepto los términos y condiciones/ });
    expect(terms.hasAttribute('required')).toBe(true);
    const links = screen.getAllByRole('link', { name: /términos y condiciones/i });
    expect(links.every((link) => link.getAttribute('href') === '/legal/terms')).toBe(true);
    const privacy = screen.getAllByRole('link', { name: /política de privacidad/i });
    expect(privacy.every((link) => link.getAttribute('href') === '/legal/privacy')).toBe(true);

    await user.type(screen.getByLabelText('Nombre de tu empresa'), 'Mi Tienda');
    await user.type(screen.getByLabelText('Tu nombre'), 'Ana');
    await user.type(screen.getByLabelText('Email'), 'ana@mitienda.com');
    await user.type(screen.getByLabelText(/^Contraseña/), 'correct horse battery staple');
    await user.click(terms);
    await user.click(screen.getByRole('button', { name: 'Crear cuenta' }));

    await screen.findByRole('heading', { name: 'Conversaciones' });
    expect(registered).toMatchObject({ email: 'ana@mitienda.com', acceptTerms: true });
  });

  it('stand in for the panel until an operator who has not accepted them does', async () => {
    let accepted = false;
    let version: unknown = null;
    stubApi({
      ...signedIn(false),
      'GET /api/v1/me': () =>
        json({
          id: 'user-1',
          email: 'ana@mitienda.com',
          displayName: 'Ana Pérez',
          termsVersion: '2026-11-11',
          termsAccepted: accepted,
        }),
      'POST /api/v1/me/terms-acceptance': ({ body }) => {
        version = (body as { version: string }).version;
        accepted = true;
        return json({
          id: 'user-1',
          email: 'ana@mitienda.com',
          displayName: 'Ana Pérez',
          termsVersion: '2026-11-11',
          termsAccepted: true,
        });
      },
    });
    const user = userEvent.setup();
    renderAt('/conversations');

    expect(await screen.findByRole('heading', { name: 'Términos y condiciones' })).toBeDefined();
    expect(screen.queryByRole('heading', { name: 'Conversaciones' })).toBeNull();
    const accept = screen.getByRole('button', { name: 'Aceptar y continuar' });
    expect(accept.hasAttribute('disabled')).toBe(true);

    await user.click(screen.getByRole('checkbox', { name: /He leído y acepto/ }));
    await user.click(accept);

    expect(await screen.findByRole('heading', { name: 'Conversaciones' })).toBeDefined();
    expect(version).toBe('2026-11-11');
  });
});
