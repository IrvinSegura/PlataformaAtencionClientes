import type { UsageMetric } from './api/billing';
import type { WorkspaceRole } from './api/workspaces';

export const LANGUAGE_NAMES: Record<string, string> = { es: 'Español', en: 'Inglés', pt: 'Portugués', fr: 'Francés' };

export const ROLE_NAMES: Record<WorkspaceRole, string> = { OWNER: 'Dueño', ADMIN: 'Administrador', AGENT: 'Agente' };

export const METRIC_NAMES: Record<UsageMetric, string> = {
  ANSWERS: 'Respuestas',
  INPUT_TOKENS: 'Tokens de entrada',
  OUTPUT_TOKENS: 'Tokens de salida',
  ACTION_RUNS: 'Acciones ejecutadas',
  WHATSAPP_MESSAGES: 'Mensajes de WhatsApp',
};

const dateTime = new Intl.DateTimeFormat('es', { dateStyle: 'medium', timeStyle: 'short' });
const number = new Intl.NumberFormat('es');

export function formatDateTime(iso: string): string {
  return dateTime.format(new Date(iso));
}

const relative = new Intl.RelativeTimeFormat('es', { numeric: 'auto' });
const shortDate = new Intl.DateTimeFormat('es', { day: 'numeric', month: 'short' });

/** "hace 5 min", "ayer"; past a week, the date. */
export function formatRelative(iso: string, now: number = Date.now()): string {
  const seconds = Math.round((new Date(iso).getTime() - now) / 1000);
  const absolute = Math.abs(seconds);
  if (absolute < 60) {
    return 'ahora';
  }
  if (absolute < 3600) {
    return relative.format(Math.round(seconds / 60), 'minute');
  }
  if (absolute < 86_400) {
    return relative.format(Math.round(seconds / 3600), 'hour');
  }
  if (absolute < 7 * 86_400) {
    return relative.format(Math.round(seconds / 86_400), 'day');
  }
  return shortDate.format(new Date(iso));
}

/** The host of a site's origin, which is what people recognise: "shop.example.com". */
export function hostOf(origin: string | null): string {
  if (!origin) {
    return 'Origen desconocido';
  }
  try {
    return new URL(origin).host;
  } catch {
    return origin;
  }
}

export function formatNumber(value: number): string {
  return number.format(value);
}

export function formatBytes(bytes: number): string {
  if (bytes < 1024) {
    return `${bytes} B`;
  }
  if (bytes < 1024 * 1024) {
    return `${(bytes / 1024).toFixed(1)} KB`;
  }
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}

/** An amount of money in the price list's currency, with enough decimals for fractions of a cent. */
export function formatMoney(value: number, currency: string): string {
  return new Intl.NumberFormat('es', {
    style: 'currency',
    currency,
    minimumFractionDigits: 2,
    maximumFractionDigits: value !== 0 && Math.abs(value) < 1 ? 4 : 2,
  }).format(value);
}

export function formatPercent(value: number): string {
  return `${Math.round(value * 100)} %`;
}
