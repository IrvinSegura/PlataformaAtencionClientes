import { useQuery } from '@tanstack/react-query';
import { Suspense, useEffect, useRef, useState } from 'react';
import { Link, NavLink, Outlet, useLocation, useNavigate } from 'react-router';
import { conversationsApi } from '../api/conversations';
import { errorMessage } from '../api/error-messages';
import { authApi } from '../api/identity';
import { useLiveConversations } from '../api/live-updates';
import { platformApi } from '../api/platform';
import { workspaceApi, type WorkspaceSuspension } from '../api/workspaces';
import { useAuth, usePermissions, useSession } from '../auth/auth-context';
import { ROLE_NAMES } from '../format';
import { Icons, type IconName } from './icons';
import { PageErrorBoundary } from './page-error-boundary';
import { TermsGate } from './terms-gate';
import { Avatar, Spinner } from './ui';
import { UsageBanner } from './usage-banner';

interface NavItem {
  to: string;
  label: string;
  icon: IconName;
  end?: boolean;
  visible?: boolean;
  tag?: string;
}

interface NavGroup {
  label: string;
  items: NavItem[];
}

/** Where each page sits in the panel, for the breadcrumbs of the top bar. */
const SECTIONS: { prefix: string; section: string; page: string }[] = [
  { prefix: '/conversations', section: 'Atención', page: 'Conversaciones' },
  { prefix: '/customers', section: 'Atención', page: 'Clientes' },
  { prefix: '/follow-ups', section: 'Atención', page: 'Seguimientos' },
  { prefix: '/attention', section: 'Atención', page: 'Avisos y escalamiento' },
  { prefix: '/playground', section: 'Asistente', page: 'Playground' },
  { prefix: '/knowledge/gaps', section: 'Asistente', page: 'Preguntas sin respuesta' },
  { prefix: '/knowledge/difficult', section: 'Asistente', page: 'Preguntas difíciles' },
  { prefix: '/knowledge/search', section: 'Asistente', page: 'Probar búsqueda' },
  { prefix: '/knowledge', section: 'Asistente', page: 'Base de conocimiento' },
  { prefix: '/actions/integrations', section: 'Asistente', page: 'Integraciones' },
  { prefix: '/actions', section: 'Asistente', page: 'Acciones' },
  { prefix: '/widget', section: 'Canales', page: 'Widget web' },
  { prefix: '/channels/whatsapp', section: 'Canales', page: 'WhatsApp' },
  { prefix: '/channels/email', section: 'Canales', page: 'Correo' },
  { prefix: '/channels/messenger', section: 'Canales', page: 'Messenger' },
  { prefix: '/channels/instagram', section: 'Canales', page: 'Instagram' },
  { prefix: '/outcomes', section: 'Analítica', page: 'Resultados' },
  { prefix: '/usage', section: 'Analítica', page: 'Consumo' },
  { prefix: '/billing', section: 'Administración', page: 'Plan y pagos' },
  { prefix: '/team', section: 'Administración', page: 'Equipo' },
  { prefix: '/platform', section: 'Plataforma', page: 'Espacios de trabajo' },
];

export function sectionOf(pathname: string) {
  return SECTIONS.find((entry) => pathname === entry.prefix || pathname.startsWith(`${entry.prefix}/`)) ?? null;
}

export function Layout() {
  const { logout } = useAuth();
  const session = useSession();
  const { canManageActions, canManageChannels, canSeeUsage, canUsePlayground } = usePermissions();
  const profile = useQuery({ queryKey: ['me'], queryFn: authApi.me, staleTime: Infinity });
  const workspace = useQuery({ queryKey: ['workspace'], queryFn: workspaceApi.get });
  const platform = useQuery({ queryKey: ['platform-access'], queryFn: platformApi.me, staleTime: Infinity });
  // The live stream invalidates every ['conversations', ...] query, this count included
  const waiting = useQuery({
    queryKey: ['conversations', 'WAITING', 'count'],
    queryFn: () => conversationsApi.list(0, 1, 'WAITING'),
    refetchInterval: 60_000,
  });
  useLiveConversations(true);
  const waitingCount = waiting.data?.total ?? 0;
  const displayName = profile.data?.displayName ?? '';
  const content = usePageChanges(workspace.data?.name);
  const { pathname } = useLocation();

  const groups: NavGroup[] = [
    {
      label: 'Atención',
      items: [
        { to: '/conversations', label: 'Conversaciones', icon: 'Inbox' },
        { to: '/customers', label: 'Clientes', icon: 'Users' },
        { to: '/follow-ups', label: 'Seguimientos', icon: 'Clock' },
        { to: '/attention', label: 'Avisos y escalamiento', icon: 'Bell', visible: canManageChannels },
      ],
    },
    {
      label: 'Asistente',
      items: [
        { to: '/playground', label: 'Playground', icon: 'Flask', visible: canUsePlayground, tag: 'Nuevo' },
        { to: '/knowledge', label: 'Base de conocimiento', icon: 'Book', end: true },
        { to: '/knowledge/gaps', label: 'Preguntas sin respuesta', icon: 'Question' },
        { to: '/knowledge/difficult', label: 'Preguntas difíciles', icon: 'Sparkles' },
        { to: '/knowledge/search', label: 'Probar búsqueda', icon: 'Search' },
        { to: '/actions', label: 'Acciones', icon: 'Zap', end: true },
        { to: '/actions/integrations', label: 'Integraciones', icon: 'Plug', visible: canManageActions },
      ],
    },
    {
      label: 'Canales',
      items: [
        { to: '/widget', label: 'Widget', icon: 'Chat' },
        { to: '/channels/whatsapp', label: 'WhatsApp', icon: 'Phone', visible: canManageChannels },
        { to: '/channels/email', label: 'Correo', icon: 'Mail', visible: canManageChannels },
        { to: '/channels/messenger', label: 'Messenger', icon: 'Messenger', visible: canManageChannels },
        { to: '/channels/instagram', label: 'Instagram', icon: 'Instagram', visible: canManageChannels },
      ],
    },
    {
      label: 'Analítica',
      items: [
        { to: '/outcomes', label: 'Resultados', icon: 'Star', visible: canSeeUsage },
        { to: '/usage', label: 'Consumo', icon: 'Chart', visible: canSeeUsage },
      ],
    },
    {
      label: 'Administración',
      items: [
        { to: '/billing', label: 'Plan y pagos', icon: 'Card', visible: canSeeUsage },
        { to: '/team', label: 'Equipo', icon: 'Users' },
      ],
    },
    {
      label: 'Plataforma',
      items: [
        { to: '/platform', label: 'Espacios de trabajo', icon: 'Layers', visible: platform.data?.platformAdmin === true },
      ],
    },
  ];

  return (
    <div className="shell">
      <a className="skip-link" href="#content">
        Saltar al contenido
      </a>
      <aside className="sidebar">
        <div className="brand">
          <span className="logo-mark">
            <Icons.Chat size={17} />
          </span>
          <span className="brand-text">
            <span className="brand-name">{workspace.data?.name ?? 'Panel'}</span>
            <span className="brand-sub">Centro de atención</span>
          </span>
        </div>
        <WorkspaceSwitcher />
        <nav aria-label="Secciones">
          {groups.map((group) => {
            const items = group.items.filter((item) => item.visible !== false);
            if (items.length === 0) {
              return null;
            }
            return (
              <div className="nav-section" key={group.label}>
                <div className="nav-label">{group.label}</div>
                {items.map((item) => {
                  const Icon = Icons[item.icon];
                  return (
                    <NavLink key={item.to} to={item.to} end={item.end}>
                      <Icon size={17} />
                      {item.label}
                      {item.to === '/conversations' && waitingCount > 0 && (
                        <span className="nav-count">
                          <span aria-hidden="true">{waitingCount}</span>
                          <span className="sr-only">, {waitingCount} en espera</span>
                        </span>
                      )}
                      {item.tag && (
                        <span className="nav-tag" aria-hidden="true">
                          {item.tag}
                        </span>
                      )}
                    </NavLink>
                  );
                })}
              </div>
            );
          })}
        </nav>
        <div className="account">
          <Avatar name={displayName || '?'} />
          <div className="account-text">
            <div className="account-name">{displayName}</div>
            <div className="account-meta">
              {ROLE_NAMES[session.role]} · {profile.data?.email}
            </div>
          </div>
          <button
            type="button"
            className="button button-ghost button-icon"
            onClick={() => void logout()}
            aria-label="Cerrar sesión"
            title="Cerrar sesión"
          >
            <Icons.Logout />
          </button>
        </div>
      </aside>
      <div className="main">
        <TopBar workspaceName={workspace.data?.name} waitingCount={waitingCount} canUsePlayground={canUsePlayground} />
        <main className="content" id="content" tabIndex={-1} ref={content}>
          {profile.data?.termsAccepted === false ? (
            <TermsGate version={profile.data.termsVersion} />
          ) : (
            <>
              {workspace.data?.suspension ? (
                <SuspensionBanner suspension={workspace.data.suspension} />
              ) : (
                <UsageBanner />
              )}
              {/* A page opened for the first time loads inside the layout, which stays in place */}
              <PageErrorBoundary resetKey={pathname}>
                <Suspense fallback={<Spinner />}>
                  <Outlet />
                </Suspense>
              </PageErrorBoundary>
            </>
          )}
        </main>
      </div>
    </div>
  );
}

/**
 * Names the browser tab after the page and, when the operator moves to another page, takes the keyboard and screen
 * reader focus to its content, as a full page load would. Moving within a page (opening a conversation of the inbox)
 * leaves the focus where it is.
 */
function usePageChanges(workspaceName: string | undefined) {
  const { pathname } = useLocation();
  const current = sectionOf(pathname);
  const content = useRef<HTMLElement>(null);
  const shown = useRef<string | null>(null);
  useEffect(() => {
    document.title = [current?.page, workspaceName ?? 'Panel de atención'].filter(Boolean).join(' · ');
  }, [current?.page, workspaceName]);
  const page = current?.prefix ?? pathname;
  useEffect(() => {
    // Not on the first page: the browser already starts there
    if (shown.current !== null && shown.current !== page) {
      content.current?.focus();
    }
    shown.current = page;
  }, [page]);
  return content;
}

/** A suspended workspace's assistant answers nothing; every member is told why. */
function SuspensionBanner({ suspension }: { suspension: WorkspaceSuspension }) {
  return (
    <div className="alert alert-danger usage-banner" role="alert">
      <p>
        Este espacio de trabajo está suspendido: {suspension.reason}. El asistente no responde y tu equipo atiende las
        conversaciones. Para reactivarlo, contacta a quien administra la plataforma.
      </p>
    </div>
  );
}

function TopBar({
  workspaceName,
  waitingCount,
  canUsePlayground,
}: {
  workspaceName: string | undefined;
  waitingCount: number;
  canUsePlayground: boolean;
}) {
  const { pathname } = useLocation();
  const current = sectionOf(pathname);
  return (
    <header className="topbar">
      <nav aria-label="Ruta">
        <ol className="breadcrumbs">
          <li>{workspaceName ?? 'Panel'}</li>
          {current && <li>{current.section}</li>}
          {current && <li aria-current="page">{current.page}</li>}
        </ol>
      </nav>
      <div className="topbar-actions">
        {waitingCount > 0 && !pathname.startsWith('/conversations') && (
          <Link className="status-pill" to="/conversations">
            <span className="badge-dot" aria-hidden="true" />
            {waitingCount === 1 ? '1 cliente espera' : `${waitingCount} clientes esperan`}
          </Link>
        )}
        {canUsePlayground && !pathname.startsWith('/playground') && (
          <Link className="button button-sm" to="/playground">
            <Icons.Flask size={15} />
            <span>Probar asistente</span>
          </Link>
        )}
      </div>
    </header>
  );
}

/** Only shown to operators who belong to more than one workspace. */
function WorkspaceSwitcher() {
  const session = useSession();
  const navigate = useNavigate();
  const [error, setError] = useState<unknown>(null);
  const [pending, setPending] = useState(false);
  const workspaces = useQuery({ queryKey: ['my-workspaces'], queryFn: authApi.workspaces, staleTime: 60_000 });
  if (!workspaces.data || workspaces.data.length < 2) {
    return null;
  }
  const switchTo = async (workspaceId: string) => {
    setError(null);
    setPending(true);
    try {
      await authApi.switchWorkspace(workspaceId);
      navigate('/conversations');
    } catch (caught) {
      setError(caught);
    } finally {
      setPending(false);
    }
  };
  return (
    <label className="workspace-switcher">
      <span className="muted small">Espacio de trabajo</span>
      <select value={session.workspaceId} disabled={pending} onChange={(event) => void switchTo(event.target.value)}>
        {workspaces.data.map((membership) => (
          <option key={membership.workspaceId} value={membership.workspaceId}>
            {membership.workspaceName} ({ROLE_NAMES[membership.role]})
          </option>
        ))}
      </select>
      {error !== null && (
        <span className="alert alert-danger" role="alert">
          {errorMessage(error)}
        </span>
      )}
    </label>
  );
}
