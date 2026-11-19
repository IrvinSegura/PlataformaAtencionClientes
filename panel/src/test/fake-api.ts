import { vi } from 'vitest';
import type { SessionResponse } from '../api/identity';
import type { WorkspaceRole } from '../api/workspaces';

export type Handler = (request: { url: string; method: string; headers: Headers; body: unknown }) => Response | Promise<Response>;

export function json(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } });
}

export function problem(status: number, code: string): Response {
  return json({ status, code, detail: code }, status);
}

export function session(token: string, role: WorkspaceRole = 'OWNER'): SessionResponse {
  return {
    accessToken: token,
    accessTokenExpiresAt: '2030-01-01T00:00:00Z',
    userId: 'user-1',
    workspaceId: 'ws-1',
    role,
  };
}

/** Replaces fetch with routes keyed by "METHOD /path"; unknown routes answer 404. */
export function stubApi(routes: Record<string, Handler>) {
  const calls: { method: string; path: string; authorization: string | null }[] = [];
  const fetchMock = vi.fn(async (input: RequestInfo | URL, init: RequestInit = {}) => {
    const url = new URL(String(input), 'http://panel.test');
    const method = init.method ?? 'GET';
    const headers = new Headers(init.headers);
    calls.push({ method, path: url.pathname, authorization: headers.get('Authorization') });
    const handler = routes[`${method} ${url.pathname}`];
    if (!handler) {
      return problem(404, 'NOT_FOUND');
    }
    const body = typeof init.body === 'string' ? JSON.parse(init.body) : init.body;
    return handler({ url: url.toString(), method, headers, body });
  });
  vi.stubGlobal('fetch', fetchMock);
  return calls;
}
