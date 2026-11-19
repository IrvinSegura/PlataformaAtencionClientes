import { config } from '../config';
import type { Session, SessionResponse } from './identity';

const AUTH_PATH = '/api/v1/auth/';
// Any value works: the backend only checks that the header exists, which a cross-site form cannot add
const CSRF_HEADER = { 'X-CSRF-Protection': '1' };
const REFRESH_LOCK = 'supportchat-refresh';
export const INVITATION_ACCEPTANCE_PATH = '/api/v1/invitations/acceptance';

export interface FieldViolation {
  field: string;
  message: string;
}

/** An error answer of the API, read from its Problem Details body. */
export class ApiError extends Error {
  constructor(
    readonly status: number,
    readonly code: string,
    readonly detail: string | null,
    readonly violations: FieldViolation[] = [],
    readonly retryAfterSeconds: number | null = null,
    readonly correlationId: string | null = null,
  ) {
    super(detail ?? `API error ${status} (${code})`);
    this.name = 'ApiError';
  }
}

/*
 * The access token lives only in memory: a script injected into the page could use it while the tab is open, but
 * cannot carry it away for later, and it expires in 15 minutes. The refresh token stays in an HttpOnly cookie that
 * JavaScript never sees.
 */
let accessToken: string | null = null;
let refreshInFlight: Promise<Session | null> | null = null;
let listener: (session: Session | null) => void = () => {};

export function onSessionChange(callback: (session: Session | null) => void): void {
  listener = callback;
}

export function startSession(response: SessionResponse): Session {
  accessToken = response.accessToken;
  const session: Session = {
    accessTokenExpiresAt: response.accessTokenExpiresAt,
    userId: response.userId,
    workspaceId: response.workspaceId,
    role: response.role,
  };
  listener(session);
  return session;
}

function currentToken(): string | null {
  return accessToken;
}

export function endSession(): void {
  accessToken = null;
  listener(null);
}

/**
 * Trades the refresh cookie for a new access token. The backend rotates the cookie on every use and treats a reused
 * one as stolen, revoking the whole login, so two refreshes must never race: calls in this tab share one request,
 * and the Web Locks API queues them across tabs (the next tab then sends the cookie the first one received).
 */
export function refreshSession(): Promise<Session | null> {
  refreshInFlight ??= withLock(async () => {
    const response = await fetch(url(`${AUTH_PATH}refresh`), {
      method: 'POST',
      credentials: 'include',
      headers: CSRF_HEADER,
    });
    if (response.status === 401) {
      endSession();
      return null;
    }
    if (!response.ok) {
      throw await toApiError(response);
    }
    return startSession((await response.json()) as SessionResponse);
  }).finally(() => {
    refreshInFlight = null;
  });
  return refreshInFlight;
}

export async function signOut(): Promise<void> {
  try {
    await fetch(url(`${AUTH_PATH}logout`), { method: 'POST', credentials: 'include', headers: CSRF_HEADER });
  } finally {
    endSession();
  }
}

export interface RequestOptions {
  method?: string;
  json?: unknown;
  body?: BodyInit;
  signal?: AbortSignal;
}

/** Calls the API as the signed-in operator, renewing the access token once if it expired. */
export async function request<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const usedToken = accessToken;
  let response = await send(path, options, usedToken);
  if (response.status === 401 && usedToken !== null) {
    // Another request may have renewed the token meanwhile; only refresh if this one is still the current token
    if (currentToken() === usedToken) {
      await refreshSession();
    }
    const renewed = currentToken();
    if (renewed !== null) {
      response = await send(path, options, renewed);
    }
  }
  if (!response.ok) {
    throw await toApiError(response);
  }
  if (response.status === 204 || response.headers.get('Content-Length') === '0') {
    return undefined as T;
  }
  return (await response.json()) as T;
}

/**
 * Opens a long-lived response, such as an event stream, as the signed-in operator, renewing the access token once if
 * it expired. The caller reads and closes the body.
 */
export async function openStream(
  path: string,
  signal: AbortSignal,
  options: Omit<RequestOptions, 'signal'> = {},
): Promise<Response> {
  const usedToken = accessToken;
  let response = await send(path, { ...options, signal }, usedToken);
  if (response.status === 401 && usedToken !== null) {
    if (currentToken() === usedToken) {
      await refreshSession();
    }
    const renewed = currentToken();
    if (renewed !== null) {
      response = await send(path, { ...options, signal }, renewed);
    }
  }
  if (!response.ok) {
    throw await toApiError(response);
  }
  return response;
}

/**
 * Sends a form with a file as the signed-in operator and reports how much of it has gone out; fetch cannot report
 * upload progress, so this one request uses XMLHttpRequest. Renews the access token once if it expired.
 *
 * @param onProgress called with the fraction sent, from 0 to 1
 */
export async function uploadWithProgress<T>(
  path: string,
  form: FormData,
  onProgress: (fraction: number) => void,
  signal?: AbortSignal,
): Promise<T> {
  const usedToken = accessToken;
  let response = await sendForm(path, form, usedToken, onProgress, signal);
  if (response.status === 401 && usedToken !== null) {
    if (currentToken() === usedToken) {
      await refreshSession();
    }
    const renewed = currentToken();
    if (renewed !== null) {
      onProgress(0);
      response = await sendForm(path, form, renewed, onProgress, signal);
    }
  }
  if (!response.ok) {
    throw await toApiError(response);
  }
  return (await response.json()) as T;
}

function sendForm(
  path: string,
  form: FormData,
  token: string | null,
  onProgress: (fraction: number) => void,
  signal?: AbortSignal,
): Promise<Response> {
  return new Promise((resolve, reject) => {
    const xhr = new XMLHttpRequest();
    xhr.open('POST', url(path));
    if (token !== null) {
      xhr.setRequestHeader('Authorization', `Bearer ${token}`);
    }
    xhr.upload.onprogress = (event) => {
      if (event.lengthComputable && event.total > 0) {
        onProgress(event.loaded / event.total);
      }
    };
    xhr.onload = () => {
      const headers = new Headers();
      for (const line of xhr.getAllResponseHeaders().trim().split(/[\r\n]+/)) {
        const colon = line.indexOf(':');
        if (colon > 0) {
          headers.append(line.slice(0, colon).trim(), line.slice(colon + 1).trim());
        }
      }
      // 204 and friends must not carry a body in a Response
      const body = xhr.status === 204 || xhr.status === 205 ? null : xhr.responseText;
      resolve(new Response(body, { status: xhr.status, headers }));
    };
    xhr.onerror = () => reject(new TypeError('Network request failed'));
    xhr.onabort = () => reject(new DOMException('Aborted', 'AbortError'));
    signal?.addEventListener('abort', () => xhr.abort(), { once: true });
    xhr.send(form);
  });
}

function send(path: string, options: RequestOptions, token: string | null): Promise<Response> {
  const headers: Record<string, string> = {};
  if (token !== null) {
    headers.Authorization = `Bearer ${token}`;
  }
  let body = options.body;
  if (options.json !== undefined) {
    headers['Content-Type'] = 'application/json';
    body = JSON.stringify(options.json);
  }
  // The refresh cookie only matters to the auth endpoints and to accepting an invitation, which signs in
  const usesCookie = path.startsWith(AUTH_PATH) || path === INVITATION_ACCEPTANCE_PATH;
  if (path.startsWith(AUTH_PATH)) {
    Object.assign(headers, CSRF_HEADER);
  }
  return fetch(url(path), {
    method: options.method ?? (body === undefined ? 'GET' : 'POST'),
    headers,
    credentials: usesCookie ? 'include' : 'same-origin',
    cache: 'no-store',
    ...(body !== undefined ? { body } : {}),
    ...(options.signal ? { signal: options.signal } : {}),
  });
}

function url(path: string): string {
  return `${config.apiBaseUrl}${path}`;
}

async function withLock<T>(task: () => Promise<T>): Promise<T> {
  if (typeof navigator !== 'undefined' && navigator.locks) {
    return navigator.locks.request(REFRESH_LOCK, task);
  }
  return task();
}

async function toApiError(response: Response): Promise<ApiError> {
  let body: Record<string, unknown> = {};
  try {
    body = (await response.json()) as Record<string, unknown>;
  } catch {
    // Not a Problem Details body (e.g. a proxy error page)
  }
  const retryAfter = Number.parseInt(response.headers.get('Retry-After') ?? '', 10);
  return new ApiError(
    response.status,
    typeof body.code === 'string' ? body.code : `HTTP_${response.status}`,
    typeof body.detail === 'string' ? body.detail : null,
    Array.isArray(body.violations) ? (body.violations as FieldViolation[]) : [],
    Number.isFinite(retryAfter) ? retryAfter : null,
    typeof body.correlationId === 'string' ? body.correlationId : null,
  );
}

/** Escapes a value placed inside a URL path. */
export const pathSegment = (value: string) => encodeURIComponent(value);
