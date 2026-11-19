import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState, type FormEvent } from 'react';
import { useSearchParams } from 'react-router';
import { platformApi, type PlatformWorkspace } from '../api/platform';
import { Badge, Empty, ErrorAlert, PageHeader, Pagination, Spinner } from '../components/ui';
import { formatDateTime, formatNumber, formatRelative } from '../format';

const PAGE_SIZE = 20;

/** Every workspace of the platform, for whoever runs it: owner, plan, activity, and suspending one. */
export function PlatformPage() {
  const access = useQuery({ queryKey: ['platform-access'], queryFn: platformApi.me, staleTime: Infinity });
  const [params, setParams] = useSearchParams();
  const page = Math.max(0, Number(params.get('page')) || 0);
  const query = params.get('q') ?? '';
  const [text, setText] = useState(query);
  const list = useQuery({
    queryKey: ['platform-workspaces', query, page],
    queryFn: () => platformApi.workspaces(query, page, PAGE_SIZE),
    enabled: access.data?.platformAdmin === true,
    placeholderData: keepPreviousData,
  });
  const go = (next: { q?: string; page?: number }) => {
    const search = new URLSearchParams();
    const q = next.q ?? query;
    if (q) {
      search.set('q', q);
    }
    if (next.page) {
      search.set('page', String(next.page));
    }
    setParams(search);
  };
  const onSubmit = (event: FormEvent) => {
    event.preventDefault();
    go({ q: text.trim(), page: 0 });
  };

  if (access.data && !access.data.platformAdmin) {
    return (
      <Empty icon="Lock">Esta sección es solo para quienes administran la plataforma.</Empty>
    );
  }
  return (
    <section className="stack" aria-labelledby="platform-title">
      <PageHeader
        id="platform-title"
        eyebrow="Plataforma"
        title="Espacios de trabajo"
        description="Todos los negocios que usan la plataforma: quién es su dueño, su plan, cuánto lo usan y si están suspendidos. Aquí no se ven sus conversaciones ni su base de conocimiento."
      />
      <form className="row" role="search" onSubmit={onSubmit}>
        <label className="grow">
          <span className="sr-only">Buscar espacio de trabajo</span>
          <input
            type="search"
            value={text}
            maxLength={100}
            placeholder="Nombre del negocio"
            onChange={(event) => setText(event.target.value)}
          />
        </label>
        <button type="submit" className="button">
          Buscar
        </button>
      </form>
      <ErrorAlert error={access.error ?? list.error} />
      {(access.isPending || list.isPending) && <Spinner />}
      {list.data && list.data.items.length === 0 && (
        <Empty icon="Layers">{query ? 'Ningún espacio de trabajo coincide.' : 'Aún no hay espacios de trabajo.'}</Empty>
      )}
      {list.data && list.data.items.length > 0 && (
        <div className="table-wrap">
          <table className="table">
            <thead>
              <tr>
                <th>Negocio</th>
                <th>Dueño</th>
                <th>Plan</th>
                <th>Últimos 30 días</th>
                <th>Estado</th>
              </tr>
            </thead>
            <tbody>
              {list.data.items.map((workspace) => (
                <WorkspaceRow key={workspace.id} workspace={workspace} />
              ))}
            </tbody>
          </table>
        </div>
      )}
      {list.data && (
        <Pagination page={page} size={PAGE_SIZE} total={list.data.total} onChange={(next) => go({ page: next })} />
      )}
    </section>
  );
}

function WorkspaceRow({ workspace }: { workspace: PlatformWorkspace }) {
  const { plan } = workspace;
  return (
    <tr>
      <td>
        <strong>{workspace.name}</strong>
        <div className="muted small">
          Desde el {formatDateTime(workspace.createdAt)} · {workspace.members}{' '}
          {workspace.members === 1 ? 'miembro' : 'miembros'}
        </div>
      </td>
      <td>
        {workspace.ownerEmail ? (
          <>
            {workspace.ownerName}
            <div className="muted small">
              <a href={`mailto:${workspace.ownerEmail}`}>{workspace.ownerEmail}</a>
            </div>
          </>
        ) : (
          <span className="muted">Sin dueño</span>
        )}
      </td>
      <td>
        {plan.planName}
        <div className="muted small">
          {formatNumber(plan.answersThisMonth)}
          {plan.answersIncluded !== null ? ` de ${formatNumber(plan.answersIncluded)}` : ''} respuestas este mes
          {plan.subscriptionStatus === 'PAST_DUE' && ' · pago pendiente'}
        </div>
      </td>
      <td>
        {formatNumber(workspace.conversationsLastMonth)}{' '}
        {workspace.conversationsLastMonth === 1 ? 'conversación' : 'conversaciones'}
        <div className="muted small">
          {workspace.lastActivityAt ? `Última, ${formatRelative(workspace.lastActivityAt)}` : 'Sin actividad'}
        </div>
      </td>
      <td>
        <Suspension workspace={workspace} />
      </td>
    </tr>
  );
}

function Suspension({ workspace }: { workspace: PlatformWorkspace }) {
  const queryClient = useQueryClient();
  const [open, setOpen] = useState(false);
  const [reason, setReason] = useState('');
  const done = () => {
    setOpen(false);
    setReason('');
    void queryClient.invalidateQueries({ queryKey: ['platform-workspaces'] });
  };
  const suspend = useMutation({ mutationFn: () => platformApi.suspend(workspace.id, reason.trim()), onSuccess: done });
  const reinstate = useMutation({ mutationFn: () => platformApi.reinstate(workspace.id), onSuccess: done });

  if (workspace.suspension) {
    return (
      <div className="stack platform-status">
        <Badge tone="danger">Suspendido</Badge>
        <span className="muted small">
          {formatDateTime(workspace.suspension.suspendedAt)}: {workspace.suspension.reason}
        </span>
        <div>
          <button
            type="button"
            className="button button-sm"
            disabled={reinstate.isPending}
            onClick={() => {
              if (window.confirm(`¿Reactivar «${workspace.name}»? Su asistente vuelve a responder.`)) {
                reinstate.mutate();
              }
            }}
          >
            Reactivar
          </button>
        </div>
        <ErrorAlert error={reinstate.error} />
      </div>
    );
  }
  return (
    <div className="stack platform-status">
      <Badge tone="success">Activo</Badge>
      {open ? (
        <form
          className="stack"
          aria-label={`Suspender ${workspace.name}`}
          onSubmit={(event) => {
            event.preventDefault();
            suspend.mutate();
          }}
        >
          <label>
            Motivo
            <textarea
              value={reason}
              rows={2}
              maxLength={500}
              required
              placeholder="Envío de spam a sus clientes"
              onChange={(event) => setReason(event.target.value)}
            />
            <span className="hint">El dueño lo ve en su panel. Su asistente deja de responder.</span>
          </label>
          <div className="row">
            <button
              type="submit"
              className="button button-sm button-danger"
              disabled={suspend.isPending || !reason.trim()}
            >
              Suspender
            </button>
            <button type="button" className="button button-sm button-ghost" onClick={() => setOpen(false)}>
              Cancelar
            </button>
          </div>
          <ErrorAlert error={suspend.error} />
        </form>
      ) : (
        <div>
          <button type="button" className="button button-sm button-ghost" onClick={() => setOpen(true)}>
            Suspender…
          </button>
        </div>
      )}
    </div>
  );
}
