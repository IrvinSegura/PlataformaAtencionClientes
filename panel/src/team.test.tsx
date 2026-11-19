import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router';
import { beforeEach, describe, expect, it } from 'vitest';
import { endSession } from './api/http-client';
import type { WorkspaceRole } from './api/workspaces';
import { App, createQueryClient } from './app';
import { json, problem, session, stubApi, type Handler } from './test/fake-api';

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
  sessionStorage.clear();
});

describe('Team', () => {
  it('creates an invitation and shows its link once', async () => {
    stubApi({
      ...baseRoutes(),
      'GET /api/v1/workspace/members': () =>
        json([{ userId: 'user-1', email: 'ana@mitienda.com', displayName: 'Ana Pérez', role: 'OWNER', joinedAt: '2026-01-01T00:00:00Z' }]),
      'GET /api/v1/workspace/invitations': () => json([]),
      'POST /api/v1/workspace/invitations': ({ body }) =>
        json(
          {
            invitation: {
              id: 'inv-1',
              email: (body as { email: string }).email,
              role: 'AGENT',
              createdAt: '2026-11-09T10:00:00Z',
              expiresAt: '2026-11-16T10:00:00Z',
            },
            token: 'secret-token',
          },
          201,
        ),
    });
    const user = userEvent.setup();
    renderAt('/team');

    expect(await screen.findByText('Tú')).toBeDefined();
    await user.type(screen.getByLabelText('Email'), 'luis@mitienda.com');
    await user.click(screen.getByRole('button', { name: 'Crear invitación' }));
    const notice = await screen.findByRole('status');
    expect(notice.textContent).toContain('luis@mitienda.com');
    expect(notice.textContent).toContain('/invitations/accept#secret-token');
  });

  it('joins a workspace from an invitation link with a new account', async () => {
    window.history.replaceState(null, '', '/invitations/accept#token-123');
    let signedIn = false;
    let accepted: unknown = null;
    stubApi({
      ...baseRoutes('AGENT'),
      'POST /api/v1/auth/refresh': () => (signedIn ? json(session('token', 'AGENT')) : problem(401, 'INVALID_REFRESH_TOKEN')),
      'POST /api/v1/invitations/preview': ({ body }) =>
        (body as { token: string }).token === 'token-123'
          ? json({ workspaceName: 'Mi Tienda', email: 'luis@mitienda.com', role: 'AGENT', accountExists: false })
          : problem(404, 'INVALID_INVITATION'),
      'POST /api/v1/invitations/acceptance': ({ body }) => {
        accepted = body;
        signedIn = true;
        return json(session('token', 'AGENT'));
      },
    });
    const user = userEvent.setup();
    renderAt('/invitations/accept');

    expect(await screen.findByText('Mi Tienda')).toBeDefined();
    // The token left the address bar as soon as it was read
    expect(window.location.hash).toBe('');
    await user.type(screen.getByLabelText('Tu nombre'), 'Luis');
    await user.type(screen.getByLabelText('Crea una contraseña'), 'correct horse battery staple');
    await user.click(screen.getByRole('checkbox', { name: /Acepto los términos y condiciones/ }));
    await user.click(screen.getByRole('button', { name: 'Unirme al equipo' }));
    expect(await screen.findByRole('heading', { name: 'Conversaciones' })).toBeDefined();
    expect(accepted).toMatchObject({ token: 'token-123', acceptTerms: true });
  });

  it('explains an invitation that no longer works', async () => {
    window.history.replaceState(null, '', '/invitations/accept#used-token');
    stubApi({
      ...baseRoutes(),
      'POST /api/v1/auth/refresh': () => problem(401, 'INVALID_REFRESH_TOKEN'),
      'POST /api/v1/invitations/preview': () => problem(404, 'INVALID_INVITATION'),
    });
    renderAt('/invitations/accept');
    expect((await screen.findByRole('alert')).textContent).toContain('ya se usó');
  });

  it('keeps the invitation when the page is reloaded after the token left the address bar', async () => {
    window.history.replaceState(null, '', '/invitations/accept#token-123');
    stubApi({
      ...baseRoutes(),
      'POST /api/v1/auth/refresh': () => problem(401, 'INVALID_REFRESH_TOKEN'),
      'POST /api/v1/invitations/preview': ({ body }) =>
        (body as { token: string }).token === 'token-123'
          ? json({ workspaceName: 'Mi Tienda', email: 'luis@mitienda.com', role: 'AGENT', accountExists: false })
          : problem(404, 'INVALID_INVITATION'),
    });
    const first = renderAt('/invitations/accept');
    expect(await screen.findByText('Mi Tienda')).toBeDefined();
    expect(window.location.hash).toBe('');
    first.unmount();

    renderAt('/invitations/accept');
    expect(await screen.findByText('Mi Tienda')).toBeDefined();
  });

  it('treats a mangled invitation link as an invalid invitation', async () => {
    window.history.replaceState(null, '', '/invitations/accept#token-%E0%A4%A');
    stubApi({
      ...baseRoutes(),
      'POST /api/v1/auth/refresh': () => problem(401, 'INVALID_REFRESH_TOKEN'),
      'POST /api/v1/invitations/preview': () => problem(404, 'INVALID_INVITATION'),
    });
    renderAt('/invitations/accept');
    expect((await screen.findByRole('alert')).textContent).toContain('ya se usó');
  });
});
