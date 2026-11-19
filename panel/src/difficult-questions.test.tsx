import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router';
import { beforeEach, describe, expect, it } from 'vitest';
import type { DifficultQuestion } from './api/conversations';
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

const QUESTION: DifficultQuestion = {
  id: 'dq-1',
  question: 'Si compro 3 y uno llega roto, ¿me devuelven todo?',
  reason: 'UNHELPFUL',
  status: 'OPEN',
  lastAnswer: 'Tienes 30 días para devolver un producto.',
  conversationId: 'conv-7',
  occurrences: 4,
  firstSeenAt: '2026-11-01T10:00:00Z',
  lastSeenAt: '2026-11-10T10:00:00Z',
  resolution: null,
  alertedAt: null,
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

describe('Difficult questions', () => {
  it('shows why each question was difficult and teaches its answer', async () => {
    let taught: unknown = null;
    stubApi({
      ...baseRoutes(),
      'GET /api/v1/difficult-questions': () => json(taught ? empty : { ...empty, total: 1, items: [QUESTION] }),
      'POST /api/v1/difficult-questions/dq-1/draft': () => json({ answer: null }),
      'POST /api/v1/difficult-questions/dq-1/answer': ({ body }) => {
        taught = body;
        return json({ documentId: 'doc-9' }, 201);
      },
    });
    const user = userEvent.setup();
    renderAt('/knowledge/difficult');

    const row = (await screen.findByText(QUESTION.question)).closest('tr')!;
    expect(within(row).getByText('Marcó «no me ayudó»')).toBeDefined();
    expect(within(row).getByText('4')).toBeDefined();
    expect(within(row).getByText('Tienes 30 días para devolver un producto.')).toBeDefined();
    expect(within(row).getByRole('link', { name: 'Ver conversación' }).getAttribute('href')).toBe('/conversations/conv-7');

    await user.click(within(row).getByRole('button', { name: 'Enseñar' }));
    const form = screen.getByRole('form', { name: `Respuesta para «${QUESTION.question}»` });
    expect(await within(form).findByText(/no hay borrador/)).toBeDefined();
    await user.type(within(form).getByRole('textbox', { name: /Respuesta correcta/ }), 'Te devolvemos solo el que llegó roto.');
    await user.click(within(form).getByRole('button', { name: 'Enseñar respuesta' }));

    expect(await screen.findByText(/Nada por ahora/)).toBeDefined();
    expect(taught).toEqual({ answer: 'Te devolvemos solo el que llegó roto.' });
  });

  it('starts the answer from a draft written from the documents', async () => {
    let taught: unknown = null;
    let drafts = 0;
    stubApi({
      ...baseRoutes(),
      'GET /api/v1/difficult-questions': () => json(taught ? empty : { ...empty, total: 1, items: [QUESTION] }),
      'POST /api/v1/difficult-questions/dq-1/draft': () => {
        drafts += 1;
        return json({ answer: 'Te devolvemos el producto que llegó roto.' });
      },
      'POST /api/v1/difficult-questions/dq-1/answer': ({ body }) => {
        taught = body;
        return json({ documentId: 'doc-9' }, 201);
      },
    });
    const user = userEvent.setup();
    renderAt('/knowledge/difficult');

    const row = (await screen.findByText(QUESTION.question)).closest('tr')!;
    await user.click(within(row).getByRole('button', { name: 'Enseñar' }));
    const form = screen.getByRole('form', { name: `Respuesta para «${QUESTION.question}»` });
    const field = within(form).getByRole('textbox', { name: /Respuesta correcta/ });
    expect(await within(form).findByText(/Borrador escrito a partir de tus documentos/)).toBeDefined();
    expect((field as HTMLTextAreaElement).value).toBe('Te devolvemos el producto que llegó roto.');

    // The team corrects it before teaching it
    await user.type(field, ' El envío de regreso es gratis.');
    await user.click(within(form).getByRole('button', { name: 'Enseñar respuesta' }));

    expect(await screen.findByText(/Nada por ahora/)).toBeDefined();
    expect(taught).toEqual({ answer: 'Te devolvemos el producto que llegó roto. El envío de regreso es gratis.' });
    expect(drafts).toBe(1);
  });

  it('shows why a question closed by itself and lets the team open it again', async () => {
    let reopened: unknown = null;
    stubApi({
      ...baseRoutes(),
      'GET /api/v1/difficult-questions': () =>
        json({
          ...empty,
          total: 1,
          items: [{ ...QUESTION, status: 'RESOLVED', resolution: 'ANSWERED_WELL', alertedAt: '2026-11-05T10:00:00Z' }],
        }),
      'PUT /api/v1/difficult-questions/dq-1/status': ({ body }) => {
        reopened = body;
        return new Response(null, { status: 204 });
      },
    });
    const user = userEvent.setup();
    renderAt('/knowledge/difficult?status=RESOLVED');

    const row = (await screen.findByText(QUESTION.question)).closest('tr')!;
    expect(within(row).getByText('El preciso la respondió bien varias veces seguidas')).toBeDefined();
    expect(within(row).getByText(/Te avisamos por WhatsApp/)).toBeDefined();
    expect(within(row).getByRole('button', { name: 'Enseñar' })).toBeDefined();
    await user.click(within(row).getByRole('button', { name: 'Reabrir' }));
    expect(reopened).toEqual({ status: 'OPEN' });
  });

  it('lets agents read the list without teaching', async () => {
    stubApi({
      ...baseRoutes('AGENT'),
      'GET /api/v1/difficult-questions': () => json({ ...empty, total: 1, items: [QUESTION] }),
    });
    renderAt('/knowledge/difficult');
    await screen.findByText(QUESTION.question);
    expect(screen.queryByRole('button', { name: 'Enseñar' })).toBeNull();
  });
});
