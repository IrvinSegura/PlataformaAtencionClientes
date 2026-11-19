import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import type { OutcomeReport } from './api/conversations';
import { endSession } from './api/http-client';
import type { WorkspaceRole } from './api/workspaces';
import { App, createQueryClient } from './app';
import { fillDays, periodOf } from './pages/outcomes-page';
import { json, session, stubApi, type Handler } from './test/fake-api';

const empty = { items: [], page: 0, size: 20, total: 0 };

// Four conversations in the period: one the assistant solved alone on the 8th, three on the 10th it did not
const REPORT: OutcomeReport = {
  from: '2026-10-12',
  to: '2026-11-10',
  conversations: 4,
  resolvedByAssistant: 1,
  handedOff: 1,
  unresolved: 2,
  resolutionRate: 0.25,
  answered: 3,
  notAnswered: 1,
  failed: 0,
  helpful: 1,
  unhelpful: 0,
  helpfulRate: 1,
  ratings: 2,
  averageRating: 3,
  satisfaction: 0.5,
  ratingCounts: [0, 1, 0, 1, 0],
  days: [
    { day: '2026-11-08', conversations: 1, resolvedByAssistant: 1, handedOff: 0, unresolved: 0 },
    { day: '2026-11-10', conversations: 3, resolvedByAssistant: 0, handedOff: 1, unresolved: 2 },
  ],
  comments: [{ conversationId: 'conv-9', score: 4, comment: 'Muy rápido', ratedAt: '2026-11-10T10:00:00Z' }],
  models: [
    { tier: 'SIMPLE', reason: 'simple', answers: 8, notAnswered: 1, helpful: 2, unhelpful: 3, unhelpfulRate: 0.6 },
    { tier: 'COMPLEX', reason: 'reasoning', answers: 2, notAnswered: 0, helpful: 0, unhelpful: 0, unhelpfulRate: null },
  ],
  difficultQuestions: 2,
};

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

describe('Outcomes', () => {
  it('shows what the assistant solved alone, day by day, and what customers said', async () => {
    let asked = '';
    stubApi({
      ...baseRoutes(),
      'GET /api/v1/insights/outcomes': ({ url }) => {
        asked = url;
        return json(REPORT);
      },
    });
    const user = userEvent.setup();
    renderAt('/outcomes');

    const summary = await screen.findByRole('list', { name: 'Resumen' });
    expect(within(summary).getByText('25 %')).toBeDefined();
    expect(within(summary).getByText('1 de 4 conversaciones')).toBeDefined();
    expect(within(summary).getByText('3,0 / 5')).toBeDefined();
    const { from, to } = periodOf(30);
    expect(new URL(asked).searchParams.get('from')).toBe(from);
    expect(new URL(asked).searchParams.get('to')).toBe(to);
    // Every day of the period has a column, also those without conversations
    expect(screen.getAllByRole('img', { name: /conversaciones/ })).toHaveLength(30);
    expect(screen.getByRole('img', { name: /10 de noviembre: 3 conversaciones, 0 resueltas/ })).toBeDefined();
    expect(screen.getByText('Muy rápido')).toBeDefined();
    // The fast model's direct answers did not help often enough to look at the rule
    const fast = screen.getByRole('cell', { name: 'Pregunta directa' }).closest('tr')!;
    expect(within(fast).getByText('Rápido')).toBeDefined();
    expect(within(fast).getByText('Revisar')).toBeDefined();
    const precise = screen.getByRole('cell', { name: 'Pide comparar, explicar o razonar' }).closest('tr')!;
    expect(within(precise).getByText('Sin opiniones')).toBeDefined();
    expect(screen.getByRole('link', { name: '2 preguntas difíciles nuevas' }).getAttribute('href')).toBe(
      '/knowledge/difficult',
    );
    expect(screen.getByRole('link', { name: 'Ver conversación' }).getAttribute('href')).toBe('/conversations/conv-9');

    await user.selectOptions(screen.getByRole('combobox', { name: 'Periodo' }), '7');
    await vi.waitFor(() => expect(new URL(asked).searchParams.get('from')).toBe(periodOf(7).from));
  });

  it('is only for managers', async () => {
    stubApi({ ...baseRoutes('AGENT') });
    renderAt('/conversations');
    expect(await screen.findByRole('link', { name: 'Conversaciones' })).toBeDefined();
    expect(screen.queryByRole('link', { name: 'Resultados' })).toBeNull();
  });

  it('fills the days without conversations and counts periods in UTC', () => {
    expect(periodOf(7, new Date('2026-11-10T23:30:00-06:00'))).toEqual({ from: '2026-11-05', to: '2026-11-11' });
    const days = fillDays('2026-11-08', '2026-11-10', REPORT.days);
    expect(days.map((day) => day.conversations)).toEqual([1, 0, 3]);
  });
});
