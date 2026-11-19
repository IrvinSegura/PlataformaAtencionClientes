import { useState, type ReactNode } from 'react';
import { errorMessage } from '../api/error-messages';
import { Icons, type IconName } from './icons';

export type Tone = 'neutral' | 'info' | 'success' | 'warning' | 'danger' | 'primary';

export function Badge({ tone = 'neutral', dot = false, children }: { tone?: Tone; dot?: boolean; children: ReactNode }) {
  return (
    <span className={`badge badge-${tone}`}>
      {dot && <span className="badge-dot" aria-hidden="true" />}
      {children}
    </span>
  );
}

export function ErrorAlert({ error }: { error: unknown }) {
  if (!error) {
    return null;
  }
  return (
    <p className="alert alert-danger" role="alert">
      {errorMessage(error)}
    </p>
  );
}

export function Pagination({
  page,
  size,
  total,
  onChange,
}: {
  page: number;
  size: number;
  total: number;
  onChange: (page: number) => void;
}) {
  const pages = Math.max(1, Math.ceil(total / size));
  if (pages <= 1) {
    return null;
  }
  return (
    <nav className="pagination" aria-label="Paginación">
      <button type="button" className="button button-sm" disabled={page === 0} onClick={() => onChange(page - 1)}>
        <Icons.ChevronLeft size={16} />
        Anterior
      </button>
      <span className="muted">
        Página {page + 1} de {pages}
      </span>
      <button type="button" className="button button-sm" disabled={page + 1 >= pages} onClick={() => onChange(page + 1)}>
        Siguiente
        <Icons.ChevronRight size={16} />
      </button>
    </nav>
  );
}

export function CopyButton({ text, label = 'Copiar' }: { text: string; label?: string }) {
  const [copied, setCopied] = useState(false);
  const copy = async () => {
    try {
      await navigator.clipboard.writeText(text);
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    } catch {
      // Clipboard blocked (insecure context or permission); the text is still selectable
    }
  };
  return (
    <button type="button" className={copied ? 'button button-sm is-done' : 'button button-sm'} onClick={copy}>
      {copied ? <Icons.Check size={16} /> : <Icons.Copy size={16} />}
      {copied ? 'Copiado' : label}
    </button>
  );
}

/** What a list or page shows when it has nothing yet, with the next step to take. */
export function Empty({ icon = 'Inbox', children }: { icon?: IconName; children: ReactNode }) {
  const Icon = Icons[icon];
  return (
    <div className="empty">
      <span className="empty-icon">
        <Icon size={22} />
      </span>
      <p>{children}</p>
    </div>
  );
}

export function PageHeader({
  id,
  title,
  description,
  actions,
  eyebrow,
}: {
  id: string;
  title: string;
  description?: ReactNode;
  actions?: ReactNode;
  /** The section of the panel the page belongs to, shown above the title. */
  eyebrow?: string;
}) {
  return (
    <header className="page-header">
      <div>
        {eyebrow && <p className="page-eyebrow">{eyebrow}</p>}
        <h1 id={id}>{title}</h1>
        {description && <p className="page-description">{description}</p>}
      </div>
      {actions && <div className="page-actions">{actions}</div>}
    </header>
  );
}

const AVATAR_HUES = [262, 200, 160, 24, 330, 45, 290, 180];

/** Initials on a colour picked from the name, so the same person keeps the same colour everywhere. */
export function Avatar({ name, size = 'md', icon }: { name: string; size?: 'sm' | 'md' | 'lg'; icon?: IconName }) {
  let hash = 0;
  for (const char of name) {
    hash = (hash * 31 + char.charCodeAt(0)) | 0;
  }
  const hue = AVATAR_HUES[Math.abs(hash) % AVATAR_HUES.length];
  const Icon = icon ? Icons[icon] : null;
  return (
    <span className={`avatar avatar-${size}`} style={{ ['--avatar-hue' as string]: hue }} aria-hidden="true">
      {Icon ? <Icon size={size === 'sm' ? 14 : 16} /> : initials(name)}
    </span>
  );
}

export function initials(name: string): string {
  const words = name.trim().split(/\s+/).filter(Boolean);
  if (words.length === 0) {
    return '?';
  }
  const letters = words.length === 1 ? words[0]!.slice(0, 2) : words[0]![0]! + words[words.length - 1]![0]!;
  return letters.toUpperCase();
}

/** A bar from 0 to 1; without a value it shows that work is under way without saying how much is left. */
export function ProgressBar({
  value,
  tone,
  label,
}: {
  value?: number | null;
  tone?: 'success' | 'danger';
  label: string;
}) {
  const known = value !== undefined && value !== null && Number.isFinite(value);
  const percent = known ? Math.round(Math.min(1, Math.max(0, value)) * 100) : undefined;
  return (
    <div
      className={['progress', known ? '' : 'is-indeterminate', tone ? `is-${tone}` : ''].filter(Boolean).join(' ')}
      role="progressbar"
      aria-label={label}
      aria-valuemin={0}
      aria-valuemax={100}
      {...(percent !== undefined ? { 'aria-valuenow': percent } : {})}
    >
      <span style={{ width: `${percent ?? 100}%` }} />
    </div>
  );
}

/** A key figure at the top of a page. */
export function Stat({
  label,
  value,
  detail,
  icon,
}: {
  label: string;
  value: ReactNode;
  detail?: ReactNode;
  icon?: IconName;
}) {
  const Icon = icon ? Icons[icon] : null;
  return (
    <li className="stat">
      <span className="stat-label">
        {Icon && <Icon size={14} />}
        {label}
      </span>
      <span className="stat-value">{value}</span>
      {detail && <span className="stat-detail">{detail}</span>}
    </li>
  );
}

/** A card with a titled header; the body is whatever the card holds. */
export function Card({
  title,
  description,
  icon,
  actions,
  children,
  className,
  as = 'div',
  labelledBy,
}: {
  title?: ReactNode;
  description?: ReactNode;
  icon?: IconName;
  actions?: ReactNode;
  children?: ReactNode;
  className?: string;
  as?: 'div' | 'section';
  labelledBy?: string;
}) {
  const Icon = icon ? Icons[icon] : null;
  const Tag = as;
  return (
    <Tag className={['card', className].filter(Boolean).join(' ')} aria-labelledby={labelledBy}>
      {(title || actions) && (
        <div className="card-header">
          <div className="card-header-main">
            {Icon && (
              <span className="card-header-icon">
                <Icon size={18} />
              </span>
            )}
            <div>
              {title && (
                <h2 className="card-title" id={labelledBy}>
                  {title}
                </h2>
              )}
              {description && <p>{description}</p>}
            </div>
          </div>
          {actions}
        </div>
      )}
      {children}
    </Tag>
  );
}

export function Spinner({ label = 'Cargando…' }: { label?: string }) {
  return (
    <p className="loading" role="status">
      <span className="spinner" aria-hidden="true" />
      {label}
    </p>
  );
}
