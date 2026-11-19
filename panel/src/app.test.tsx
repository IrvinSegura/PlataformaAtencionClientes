import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router';
import { beforeEach, describe, expect, it } from 'vitest';
import { endSession } from './api/http-client';
import type { WorkspaceRole } from './api/workspaces';
import { App, createQueryClient } from './app';
import { json, problem, session, stubApi, type Handler } from './test/fake-api';

const workspace = {
  id: 'ws-1',
  name: 'Mi Tienda',
  widgetKey: 'wk_123',
  allowedOrigins: ['https://mitienda.com'],
  botName: 'Ana',
  welcomeMessage: '¡Hola!',
  createdAt: '2026-01-01T00:00:00Z',
};

const signedInRoutes = (role: WorkspaceRole = 'OWNER'): Record<string, Handler> => ({
  'POST /api/v1/auth/refresh': () => json(session('token', role)),
  'GET /api/v1/me': () => json({ id: 'user-1', email: 'ana@mitienda.com', displayName: 'Ana Pérez' }),
  'GET /api/v1/workspace': () => json(workspace),
  'GET /api/v1/conversations': () => json({ items: [], page: 0, size: 20, total: 0 }),
  'GET /api/v1/knowledge/documents': () =>
    json({
      items: [
        {
          id: 'doc-1',
          title: 'Preguntas frecuentes',
          fileName: 'faq.md',
          mediaType: 'text/markdown',
          sizeBytes: 2048,
          status: 'READY',
          failureReason: null,
          chunkCount: 12,
          createdAt: '2026-01-01T00:00:00Z',
          updatedAt: '2026-01-01T00:00:00Z',
        },
      ],
      page: 0,
      size: 20,
      total: 1,
    }),
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

describe('App', () => {
  it('sends visitors without a session to the login page and lets them in', async () => {
    let signedIn = false;
    stubApi({
      ...signedInRoutes(),
      'POST /api/v1/auth/refresh': () => (signedIn ? json(session('token')) : problem(401, 'INVALID_REFRESH_TOKEN')),
      'POST /api/v1/auth/login': ({ body }) => {
        const { email, password } = body as { email: string; password: string };
        signedIn = email === 'ana@mitienda.com' && password === 'correct horse battery staple';
        return signedIn ? json(session('token')) : problem(401, 'INVALID_CREDENTIALS');
      },
    });
    const user = userEvent.setup();
    renderAt('/knowledge');

    expect(await screen.findByRole('heading', { name: 'Iniciar sesión' })).toBeDefined();
    await user.type(screen.getByLabelText('Email'), 'ana@mitienda.com');
    await user.type(screen.getByLabelText('Contraseña'), 'wrong password!');
    await user.click(screen.getByRole('button', { name: 'Entrar' }));
    expect((await screen.findByRole('alert')).textContent).toBe('Email o contraseña incorrectos.');

    await user.clear(screen.getByLabelText('Contraseña'));
    await user.type(screen.getByLabelText('Contraseña'), 'correct horse battery staple');
    await user.click(screen.getByRole('button', { name: 'Entrar' }));
    // Back where the operator wanted to go
    expect(await screen.findByRole('heading', { name: 'Base de conocimiento' })).toBeDefined();
  });

  it('restores the session from the refresh cookie and lists documents with upload for owners', async () => {
    stubApi(signedInRoutes('OWNER'));
    renderAt('/knowledge');
    const row = (await screen.findByText('Preguntas frecuentes')).closest('tr')!;
    expect(within(row).getByText('Listo')).toBeDefined();
    expect(screen.getByRole('heading', { name: 'Agregar conocimiento' })).toBeDefined();
    expect(within(row).getByRole('button', { name: 'Eliminar' })).toBeDefined();
  });

  it('hides knowledge management from agents', async () => {
    stubApi(signedInRoutes('AGENT'));
    renderAt('/knowledge');
    await screen.findByText('Preguntas frecuentes');
    expect(screen.queryByRole('heading', { name: 'Agregar conocimiento' })).toBeNull();
    expect(screen.queryByRole('button', { name: 'Eliminar' })).toBeNull();
  });

  it('shows the embed snippet with the widget key', async () => {
    stubApi(signedInRoutes());
    renderAt('/widget');
    const snippet = await screen.findByText(/data-widget-key="wk_123"/);
    expect(snippet.textContent).toContain('<script src=');
    expect((screen.getByLabelText('Nombre del asistente') as HTMLInputElement).value).toBe('Ana');
  });

  it('names the tab after the page and takes the focus to the content of each page the operator opens', async () => {
    stubApi(signedInRoutes());
    const user = userEvent.setup();
    renderAt('/knowledge');
    await screen.findByText('Preguntas frecuentes');
    expect(document.title).toBe('Base de conocimiento · Mi Tienda');
    const main = screen.getByRole('main');
    expect(screen.getByRole('link', { name: 'Saltar al contenido' }).getAttribute('href')).toBe('#content');
    expect(document.activeElement).not.toBe(main);

    await user.click(screen.getByRole('link', { name: 'Widget' }));
    await screen.findByLabelText('Nombre del asistente');
    expect(document.title).toBe('Widget web · Mi Tienda');
    expect(document.activeElement).toBe(main);
  });
});
