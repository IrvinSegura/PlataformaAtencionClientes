import { beforeEach, describe, expect, it, vi } from 'vitest';
import { json, problem, session, stubApi } from '../test/fake-api';
import { ApiError, endSession, onSessionChange, refreshSession, request, startSession } from './http-client';

beforeEach(() => {
  endSession();
  onSessionChange(() => {});
});

describe('refreshSession', () => {
  it('sends one refresh for concurrent callers, because a reused refresh token revokes the login', async () => {
    const calls = stubApi({ 'POST /api/v1/auth/refresh': () => json(session('fresh')) });
    const results = await Promise.all([refreshSession(), refreshSession(), refreshSession()]);
    expect(calls.filter((call) => call.path === '/api/v1/auth/refresh')).toHaveLength(1);
    expect(results.every((result) => result?.userId === 'user-1')).toBe(true);
  });

  it('sends the anti-CSRF header with the cookie', async () => {
    const seen: (string | null)[] = [];
    stubApi({
      'POST /api/v1/auth/refresh': ({ headers }) => {
        seen.push(headers.get('X-CSRF-Protection'));
        return json(session('fresh'));
      },
    });
    await refreshSession();
    expect(seen).toEqual(['1']);
    expect(vi.mocked(fetch).mock.calls[0]?.[1]?.credentials).toBe('include');
  });

  it('ends the session when the refresh token is no longer valid', async () => {
    stubApi({ 'POST /api/v1/auth/refresh': () => problem(401, 'INVALID_REFRESH_TOKEN') });
    const listener = vi.fn();
    onSessionChange(listener);
    expect(await refreshSession()).toBeNull();
    expect(listener).toHaveBeenCalledWith(null);
  });
});

describe('request', () => {
  it('renews an expired access token once and repeats the request', async () => {
    startSession(session('old'));
    const calls = stubApi({
      'GET /api/v1/workspace': ({ headers }) =>
        headers.get('Authorization') === 'Bearer new' ? json({ name: 'Mi Tienda' }) : problem(401, 'UNAUTHORIZED'),
      'POST /api/v1/auth/refresh': () => json(session('new')),
    });
    expect(await request('/api/v1/workspace')).toEqual({ name: 'Mi Tienda' });
    expect(calls.map((call) => `${call.method} ${call.path} ${call.authorization}`)).toEqual([
      'GET /api/v1/workspace Bearer old',
      'POST /api/v1/auth/refresh null',
      'GET /api/v1/workspace Bearer new',
    ]);
  });

  it('exposes the code, violations and retry delay of a Problem Details error', async () => {
    startSession(session('token'));
    stubApi({
      'PUT /api/v1/workspace/widget-settings': () =>
        new Response(JSON.stringify({ code: 'RATE_LIMITED', correlationId: 'abc' }), {
          status: 429,
          headers: { 'Retry-After': '7' },
        }),
    });
    const error = await request('/api/v1/workspace/widget-settings', { method: 'PUT', json: {} }).catch((caught) => caught);
    expect(error).toBeInstanceOf(ApiError);
    expect(error).toMatchObject({ status: 429, code: 'RATE_LIMITED', retryAfterSeconds: 7, correlationId: 'abc' });
  });
});
