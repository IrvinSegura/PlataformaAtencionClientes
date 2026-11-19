import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { endSession } from './api/http-client';
import type { DocumentSuggestion } from './api/knowledge';
import type { WorkspaceRole } from './api/workspaces';
import { App, createQueryClient } from './app';
import { draftFor, fileNameFor } from './pages/gaps-page';
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

describe('Suggested documents', () => {
  const suggestion: DocumentSuggestion = {
    title: '¿Hacen envíos a Colombia?',
    occurrences: 3,
    lastAskedAt: '2026-11-10T09:00:00Z',
    questions: [
      gap('gap-1', '¿Hacen envíos a Colombia?', 2),
      gap('gap-2', '¿Envían a Colombia?', 1),
    ],
  };

  it('turns a group of unanswered questions into a document and resolves them', async () => {
    let uploaded: { url: string; file: File | null } | null = null;
    const resolved: string[] = [];
    stubApi({
      ...baseRoutes(),
      'GET /api/v1/knowledge-gaps/suggestions': () => json(uploaded ? [] : [suggestion]),
      'POST /api/v1/knowledge/documents': ({ url, body }) => {
        uploaded = { url, file: (body as FormData).get('file') as File };
        return json({ id: 'doc-1' }, 201);
      },
      ...Object.fromEntries(
        suggestion.questions.map((question) => [
          `PUT /api/v1/knowledge-gaps/${question.id}/status`,
          ({ body }: { body: unknown }) => {
            expect(body).toEqual({ status: 'RESOLVED' });
            resolved.push(question.id);
            return new Response(null, { status: 204 });
          },
        ]),
      ),
    });
    const user = userEvent.setup();
    renderAt('/knowledge/gaps?view=suggestions');

    expect(await screen.findByRole('heading', { name: '¿Hacen envíos a Colombia?' })).toBeDefined();
    expect(screen.getByText('2 preguntas · 3 veces', { exact: false })).toBeDefined();
    await user.click(screen.getByRole('button', { name: 'Escribir documento' }));
    const send = screen.getByRole('button', { name: 'Subir y marcar resueltas' });
    // The bare draft answers nothing yet
    expect((send as HTMLButtonElement).disabled).toBe(true);
    const content = screen.getByRole('textbox', { name: /Contenido/ });
    expect((content as HTMLTextAreaElement).value).toBe(draftFor(suggestion));
    await user.type(content, 'Sí, enviamos a Colombia en 7 días hábiles.');
    await user.click(send);

    expect(await screen.findByText(/No hay temas pendientes/)).toBeDefined();
    expect(new URL(uploaded!.url).searchParams.get('title')).toBe('¿Hacen envíos a Colombia?');
    expect(uploaded!.file!.name).toBe('hacen-envios-a-colombia.md');
    expect(await uploaded!.file!.text()).toContain('## Respuesta\n\nSí, enviamos a Colombia en 7 días hábiles.');
    expect(resolved.sort()).toEqual(['gap-1', 'gap-2']);
  });

  it('lets agents read the suggestions without writing documents', async () => {
    stubApi({ ...baseRoutes('AGENT'), 'GET /api/v1/knowledge-gaps/suggestions': () => json([suggestion]) });
    renderAt('/knowledge/gaps?view=suggestions');
    expect(await screen.findByText('¿Envían a Colombia?')).toBeDefined();
    expect(screen.queryByRole('button', { name: 'Escribir documento' })).toBeNull();
  });

  it('names the file after the title', () => {
    expect(fileNameFor('¿Envíos a Colombia?')).toBe('envios-a-colombia.md');
    expect(fileNameFor('¿?')).toBe('documento.md');
  });
});

function gap(id: string, question: string, occurrences: number) {
  return {
    id,
    question,
    status: 'OPEN' as const,
    occurrences,
    firstAskedAt: '2026-11-01T09:00:00Z',
    lastAskedAt: '2026-11-10T09:00:00Z',
    lastConversationId: 'conv-1',
  };
}
