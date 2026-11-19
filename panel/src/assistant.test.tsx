import { fireEvent, render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router';
import { describe, expect, it, vi } from 'vitest';
import type { PlaygroundResult } from './api/conversations';
import { errorMessage } from './api/error-messages';
import { ApiError } from './api/http-client';
import type { KnowledgeDocument } from './api/knowledge';
import type { WorkspaceRole } from './api/workspaces';
import { App, createQueryClient } from './app';
import { rejectReason } from './pages/knowledge-page';
import { MAX_HISTORY, playgroundHistory, type Turn } from './pages/playground-page';
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
  'GET /api/v1/assistant/models': () =>
    json([
      { preference: 'AUTOMATIC', models: ['claude-haiku-5-5', 'claude-sonnet-5-5'] },
      { preference: 'FAST', models: ['claude-haiku-5-5'] },
      { preference: 'PRECISE', models: ['claude-sonnet-5-5'] },
    ]),
});

function renderAt(path: string) {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <App queryClient={createQueryClient()} />
    </MemoryRouter>,
  );
}

function document(changes: Partial<KnowledgeDocument>): KnowledgeDocument {
  return {
    id: 'doc-1',
    title: 'Preguntas frecuentes',
    fileName: 'faq.md',
    mediaType: 'text/markdown',
    sizeBytes: 2048,
    status: 'READY',
    failureReason: null,
    chunkCount: 4,
    createdAt: '2026-11-10T10:00:00Z',
    updatedAt: '2026-11-10T10:00:00Z',
    sourceUrl: null,
    stage: null,
    embeddedChunks: 0,
    ...changes,
  };
}

function stream(events: { event: string; data: unknown }[]): Response {
  const body = events.map(({ event, data }) => `event: ${event}\ndata: ${JSON.stringify(data)}\n\n`).join('');
  return new Response(body, { status: 200, headers: { 'Content-Type': 'text/event-stream' } });
}

const RESULT: PlaygroundResult = {
  outcome: 'ANSWERED',
  text: 'El envío tarda de 3 a 5 días hábiles [1]',
  citations: [],
  sources: [
    {
      number: 1,
      documentId: 'doc-1',
      documentTitle: 'Envíos',
      content: 'El envío nacional tarda de 3 a 5 días hábiles.',
      similarity: 0.82,
      keywordMatch: true,
      usedByModel: true,
    },
    {
      number: null,
      documentId: 'doc-2',
      documentTitle: 'Devoluciones',
      content: 'Aceptamos devoluciones en 30 días.',
      similarity: 0.31,
      keywordMatch: false,
      usedByModel: false,
    },
  ],
  toolCalls: [
    { name: 'order_status', description: 'Consulta un pedido', input: { order: 'A-1' }, requiresConfirmation: false, known: true },
  ],
  model: {
    model: 'claude-sonnet-5-5',
    tier: 'COMPLEX',
    reason: 'chosen-precise',
    preference: 'PRECISE',
    inputTokens: 1520,
    outputTokens: 48,
    estimatedCostUsd: 0.00528,
    modelCalls: 2,
  },
  latencyMillis: 1830,
};

describe('Playground', () => {
  it('streams the answer and shows what produced it in the inspector', async () => {
    const sent: unknown[] = [];
    stubApi({
      ...baseRoutes(),
      'POST /api/v1/playground/answers': ({ body }) => {
        sent.push(body);
        return stream([
          { event: 'delta', data: { text: 'El envío tarda ' } },
          { event: 'delta', data: { text: 'de 3 a 5 días hábiles [1]' } },
          { event: 'done', data: RESULT },
        ]);
      },
    });
    const user = userEvent.setup();
    renderAt('/playground');

    await user.selectOptions(await screen.findByLabelText('Modelo'), 'PRECISE');
    await user.type(screen.getByLabelText('Pregunta del cliente'), '¿Cuánto tarda el envío?');
    await user.click(screen.getByRole('button', { name: 'Preguntar' }));

    expect(await screen.findByText('El envío tarda de 3 a 5 días hábiles [1]')).toBeDefined();
    const inspector = screen.getByRole('complementary', { name: 'Inspector' });
    expect(within(inspector).getByText('claude-sonnet-5-5')).toBeDefined();
    expect(within(inspector).getByText('1520 / 48')).toBeDefined();
    expect(within(inspector).getByText('Elegido por ti: Preciso')).toBeDefined();
    expect(within(inspector).getByText('1 de 2 usados')).toBeDefined();
    expect(within(inspector).getByText('Enviado al modelo')).toBeDefined();
    expect(within(inspector).getByText('order_status')).toBeDefined();
    expect(sent[0]).toEqual({ question: '¿Cuánto tarda el envío?', history: [], model: 'PRECISE' });

    await user.type(screen.getByLabelText('Pregunta del cliente'), '¿Y a Monterrey?');
    await user.click(screen.getByRole('button', { name: 'Preguntar' }));
    await screen.findAllByText('El envío tarda de 3 a 5 días hábiles [1]');
    // Earlier turns go along so follow-up questions keep their context
    expect((sent[1] as { history: unknown[] }).history).toEqual([
      { fromCustomer: true, text: '¿Cuánto tarda el envío?' },
      { fromCustomer: false, text: 'El envío tarda de 3 a 5 días hábiles [1]' },
    ]);
  });

  it('sends only the latest finished turns, so a long test is never refused', () => {
    const turns: Turn[] = Array.from({ length: 30 }, (_, index) => ({
      id: index,
      fromCustomer: index % 2 === 0,
      text: `turno ${index}`,
    }));
    turns.push({ id: 30, fromCustomer: true, text: 'falló' }, { id: 31, fromCustomer: false, text: '', error: 'Error' });
    turns.push({ id: 32, fromCustomer: false, text: 'a medias', streaming: true });

    const history = playgroundHistory(turns);

    expect(history).toHaveLength(MAX_HISTORY);
    expect(history[0]).toEqual({ fromCustomer: false, text: 'turno 11' });
    expect(history.at(-1)).toEqual({ fromCustomer: true, text: 'falló' });
  });

  it('says when the plan has no answers left', async () => {
    stubApi({
      ...baseRoutes(),
      'POST /api/v1/playground/answers': () => json({ status: 409, code: 'PLAN_LIMIT_REACHED' }, 409),
    });
    const user = userEvent.setup();
    renderAt('/playground');

    await user.type(await screen.findByLabelText('Pregunta del cliente'), 'Hola');
    await user.click(screen.getByRole('button', { name: 'Preguntar' }));

    expect(await screen.findByText(/Se agotaron las respuestas del plan/)).toBeDefined();
  });

  it('is offered to managers only', async () => {
    stubApi({ ...baseRoutes('AGENT'), 'GET /api/v1/knowledge/documents': () => json(empty) });
    renderAt('/knowledge');

    expect(await screen.findByRole('heading', { name: 'Base de conocimiento' })).toBeDefined();
    expect(screen.queryByRole('link', { name: /Playground/ })).toBeNull();
    expect(screen.queryByRole('link', { name: /Probar asistente/ })).toBeNull();
  });
});

describe('WhatsApp model', () => {
  it('saves the model the number answers with', async () => {
    let sent: Record<string, unknown> | null = null;
    const channel = {
      phoneNumberId: '1234567890',
      displayPhoneNumber: null,
      enabled: true,
      webhookPath: '/api/v1/channels/whatsapp/key-1/webhook',
      verifyToken: 'verify-123',
      model: 'AUTOMATIC',
      updatedAt: '2026-11-09T10:00:00Z',
    };
    stubApi({
      ...baseRoutes(),
      'GET /api/v1/channels/whatsapp': () => json(channel),
      'PUT /api/v1/channels/whatsapp': ({ body }) => {
        sent = body as Record<string, unknown>;
        return json({ ...channel, model: 'FAST', updatedAt: '2026-11-10T10:00:00Z' });
      },
    });
    const user = userEvent.setup();
    renderAt('/channels/whatsapp');

    const fast = await screen.findByRole('radio', { name: /Rápido/ });
    expect((screen.getByRole('radio', { name: /Automático/ }) as HTMLInputElement).checked).toBe(true);
    // The models behind each choice come from the server
    expect(await screen.findByText('claude-haiku-5-5 · claude-sonnet-5-5')).toBeDefined();
    await user.click(fast);
    await user.click(screen.getByRole('button', { name: 'Guardar cambios' }));

    expect(await screen.findByText('Conexión guardada.')).toBeDefined();
    expect(sent).toMatchObject({ phoneNumberId: '1234567890', model: 'FAST' });
  });
});

describe('Knowledge sources', () => {
  it('imports a web page with the pages it links to', async () => {
    let sent: unknown = null;
    stubApi({
      ...baseRoutes(),
      'GET /api/v1/knowledge/documents': () => json(empty),
      'POST /api/v1/knowledge/documents/web': ({ body }) => {
        sent = body;
        return json(document({ status: 'PENDING', sourceUrl: 'https://mitienda.com/ayuda' }), 202);
      },
    });
    const user = userEvent.setup();
    renderAt('/knowledge');

    await user.click(await screen.findByRole('tab', { name: 'Importar sitio web' }));
    await user.type(screen.getByLabelText('Dirección de la página'), 'https://mitienda.com/ayuda');
    fireEvent.change(screen.getByRole('slider'), { target: { value: '5' } });
    await user.click(screen.getByRole('button', { name: 'Importar' }));

    expect(await screen.findByText(/Página en cola/)).toBeDefined();
    expect(sent).toEqual({ url: 'https://mitienda.com/ayuda', linkedPages: 5 });
  });

  it('shows the stage of a document being processed and links web pages', async () => {
    stubApi({
      ...baseRoutes(),
      'GET /api/v1/knowledge/documents': () =>
        json({
          ...empty,
          total: 2,
          items: [
            document({ id: 'doc-1', title: 'Catálogo', status: 'PROCESSING', stage: 'INDEXING', chunkCount: 10, embeddedChunks: 3 }),
            document({ id: 'doc-2', title: 'Centro de ayuda', sourceUrl: 'https://mitienda.com/ayuda', fileName: 'ayuda' }),
          ],
        }),
    });
    renderAt('/knowledge');

    expect(await screen.findByText('Indexando · 3 de 10')).toBeDefined();
    expect(screen.getByRole('progressbar', { name: 'Indexando' }).getAttribute('aria-valuenow')).toBe('30');
    expect(screen.getByRole('link', { name: /mitienda\.com\/ayuda/ }).getAttribute('href')).toBe('https://mitienda.com/ayuda');
    const row = screen.getByText('Centro de ayuda').closest('tr')!;
    expect(within(row).getByRole('button', { name: 'Actualizar' })).toBeDefined();
  });

  it('uploads dropped files with their progress', async () => {
    stubApi({ ...baseRoutes(), 'GET /api/v1/knowledge/documents': () => json(empty) });
    const requests: FakeUpload[] = [];
    class FakeUpload {
      upload: { onprogress: ((event: ProgressEvent) => void) | null } = { onprogress: null };
      onload: (() => void) | null = null;
      onerror: (() => void) | null = null;
      onabort: (() => void) | null = null;
      status = 0;
      responseText = '';
      headers: Record<string, string> = {};
      url = '';
      body: FormData | null = null;
      constructor() {
        requests.push(this);
      }
      open(_method: string, url: string) {
        this.url = url;
      }
      setRequestHeader(name: string, value: string) {
        this.headers[name] = value;
      }
      getAllResponseHeaders() {
        return 'content-type: application/json\r\n';
      }
      abort() {}
      send(body: FormData) {
        this.body = body;
      }
      respond(status: number, body: unknown) {
        this.status = status;
        this.responseText = JSON.stringify(body);
        this.onload?.();
      }
    }
    vi.stubGlobal('XMLHttpRequest', FakeUpload);
    renderAt('/knowledge');

    const input = await screen.findByLabelText('Archivos para la base de conocimiento');
    const word = new File(['contenido'], 'politicas.docx', {
      type: 'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
    });
    const image = new File(['png'], 'logo.png', { type: 'image/png' });
    fireEvent.change(input, { target: { files: [word, image] } });

    // Only the supported file is sent; the other is refused before any upload
    expect(await screen.findByText('Formato no admitido')).toBeDefined();
    expect(requests).toHaveLength(1);
    expect(requests[0]!.headers.Authorization).toBe('Bearer token');
    expect((requests[0]!.body!.get('file') as File).name).toBe('politicas.docx');

    requests[0]!.upload.onprogress?.({ lengthComputable: true, loaded: 40, total: 100 } as ProgressEvent);
    expect(await screen.findByText('40 %')).toBeDefined();
    requests[0]!.respond(202, document({ status: 'PENDING' }));
    expect(await screen.findByText('Recibido')).toBeDefined();
  });

  it('checks the format and size before uploading', () => {
    expect(rejectReason(new File(['x'], 'precios.xlsx'))).toBeNull();
    expect(rejectReason(new File(['x'], 'pagina.HTML'))).toBeNull();
    expect(rejectReason(new File(['x'], 'foto.jpg'))).toBe('Formato no admitido');
    expect(rejectReason(new File([], 'vacio.txt'))).toBe('El archivo está vacío');
  });
});

describe('New error codes', () => {
  it('are explained in Spanish', () => {
    expect(errorMessage(new ApiError(409, 'DUPLICATE_WEB_PAGE', null))).toMatch(/ya está en la base/);
    expect(errorMessage(new ApiError(400, 'INVALID_WEB_ADDRESS', null))).toMatch(/dirección no es válida/);
    expect(errorMessage(new ApiError(415, 'UNSUPPORTED_DOCUMENT', null))).toMatch(/Word.*Excel/);
    expect(errorMessage(new ApiError(409, 'PLAN_LIMIT_REACHED', null))).toMatch(/Se agotaron/);
  });
});
