import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState, type FormEvent } from 'react';
import { type IssuedInvitation, type Member, teamApi } from '../api/identity';
import type { WorkspaceRole } from '../api/workspaces';
import { usePermissions, useSession } from '../auth/auth-context';
import { Icons } from '../components/icons';
import { Avatar, Badge, CopyButton, Empty, ErrorAlert, PageHeader, Spinner } from '../components/ui';
import { formatDateTime, ROLE_NAMES } from '../format';

/** The link the invited person opens; the token goes in the fragment, which browsers never send to any server. */
export function invitationLink(token: string): string {
  return `${window.location.origin}/invitations/accept#${token}`;
}

export function TeamPage() {
  const { canManageTeam } = usePermissions();
  const members = useQuery({ queryKey: ['members'], queryFn: teamApi.members });

  return (
    <section aria-labelledby="team-title">
      <PageHeader
        id="team-title"
        eyebrow="Administración"
        title="Equipo"
        description="Los administradores configuran el asistente; los agentes atienden conversaciones."
      />
      {canManageTeam && <InviteForm />}
      <h2 className="section-title">
        Miembros {members.data && <span className="count">{members.data.length}</span>}
      </h2>
      <ErrorAlert error={members.error} />
      {members.isPending && <Spinner />}
      {members.data && (
        <div className="table-wrap">
          <table className="table">
            <thead>
              <tr>
                <th scope="col">Nombre</th>
                <th scope="col">Rol</th>
                <th scope="col">Desde</th>
                <th scope="col" className="actions">
                  Acciones
                </th>
              </tr>
            </thead>
            <tbody>
              {members.data.map((member) => (
                <MemberRow key={member.userId} member={member} />
              ))}
            </tbody>
          </table>
        </div>
      )}
      {canManageTeam && <PendingInvitations />}
    </section>
  );
}

function MemberRow({ member }: { member: Member }) {
  const session = useSession();
  const { canChangeRoles } = usePermissions();
  const queryClient = useQueryClient();
  const onSuccess = () => queryClient.invalidateQueries({ queryKey: ['members'] });
  const changeRole = useMutation({
    mutationFn: (role: WorkspaceRole) => teamApi.changeRole(member.userId, role),
    onSuccess,
  });
  const remove = useMutation({ mutationFn: () => teamApi.remove(member.userId), onSuccess });
  const isMe = member.userId === session.userId;
  // Same rules as the backend's MemberPolicy, only to hide what would be refused
  const canRemove =
    !isMe &&
    member.role !== 'OWNER' &&
    (session.role === 'OWNER' || (session.role === 'ADMIN' && member.role === 'AGENT'));

  return (
    <tr>
      <td>
        <div className="person">
          <Avatar name={member.displayName} />
          <div>
            <div className="person-name">
              {member.displayName} {isMe && <Badge tone="info">Tú</Badge>}
            </div>
            <div className="person-email">{member.email}</div>
          </div>
        </div>
        <ErrorAlert error={changeRole.error ?? remove.error} />
      </td>
      <td>
        {canChangeRoles && member.role !== 'OWNER' ? (
          <select
            aria-label={`Rol de ${member.displayName}`}
            value={member.role}
            disabled={changeRole.isPending}
            onChange={(event) => changeRole.mutate(event.target.value as WorkspaceRole)}
          >
            <option value="ADMIN">{ROLE_NAMES.ADMIN}</option>
            <option value="AGENT">{ROLE_NAMES.AGENT}</option>
          </select>
        ) : (
          <Badge tone={member.role === 'OWNER' ? 'warning' : 'neutral'}>{ROLE_NAMES[member.role]}</Badge>
        )}
      </td>
      <td className="muted small">{formatDateTime(member.joinedAt)}</td>
      <td className="actions">
        {canRemove && (
          <button
            type="button"
            className="button button-sm button-ghost"
            disabled={remove.isPending}
            onClick={() => {
              if (window.confirm(`¿Quitar a ${member.displayName} del equipo?`)) {
                remove.mutate();
              }
            }}
          >
            Quitar
          </button>
        )}
      </td>
    </tr>
  );
}

function InviteForm() {
  const session = useSession();
  const queryClient = useQueryClient();
  const [email, setEmail] = useState('');
  const [role, setRole] = useState<WorkspaceRole>('AGENT');
  const [issued, setIssued] = useState<IssuedInvitation | null>(null);
  const invite = useMutation({
    mutationFn: () => teamApi.invite(email.trim(), role),
    onSuccess: (result) => {
      setIssued(result);
      setEmail('');
      void queryClient.invalidateQueries({ queryKey: ['invitations'] });
    },
  });
  const onSubmit = (event: FormEvent) => {
    event.preventDefault();
    setIssued(null);
    invite.mutate();
  };

  return (
    <div className="card">
      <div className="card-header">
        <div>
          <h2 className="card-title">Invitar a alguien</h2>
          <p>Genera un enlace de un solo uso; vence en 7 días.</p>
        </div>
      </div>
      <form className="invite-form" onSubmit={onSubmit}>
        <label className="grow">
          Email
          <input type="email" value={email} onChange={(event) => setEmail(event.target.value)} required maxLength={254} />
        </label>
        <label>
          Rol
          <select value={role} onChange={(event) => setRole(event.target.value as WorkspaceRole)}>
            <option value="AGENT">{ROLE_NAMES.AGENT}</option>
            {session.role === 'OWNER' && <option value="ADMIN">{ROLE_NAMES.ADMIN}</option>}
          </select>
        </label>
        <button type="submit" className="button button-primary" disabled={invite.isPending}>
          <Icons.Mail size={16} />
          {invite.isPending ? 'Creando…' : 'Crear invitación'}
        </button>
      </form>
      <ErrorAlert error={invite.error} />
      {issued && (
        <div className="alert alert-success issued" role="status">
          <p>
            Envía este enlace a {issued.invitation.email}. Solo se muestra ahora y vence el{' '}
            {formatDateTime(issued.invitation.expiresAt)}.
          </p>
          <div className="row">
            <code className="invitation-link">{invitationLink(issued.token)}</code>
            <CopyButton text={invitationLink(issued.token)} label="Copiar enlace" />
          </div>
        </div>
      )}
    </div>
  );
}

function PendingInvitations() {
  const queryClient = useQueryClient();
  const invitations = useQuery({ queryKey: ['invitations'], queryFn: teamApi.invitations });
  const revoke = useMutation({
    mutationFn: teamApi.revoke,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['invitations'] }),
  });
  return (
    <>
      <h2 className="section-title">
        Invitaciones pendientes {invitations.data && <span className="count">{invitations.data.length}</span>}
      </h2>
      <ErrorAlert error={invitations.error ?? revoke.error} />
      {invitations.data && invitations.data.length === 0 && <Empty icon="Mail">No hay invitaciones pendientes.</Empty>}
      {invitations.data && invitations.data.length > 0 && (
        <div className="table-wrap">
          <table className="table">
            <thead>
              <tr>
                <th scope="col">Email</th>
                <th scope="col">Rol</th>
                <th scope="col">Vence</th>
                <th scope="col" className="actions">
                  Acciones
                </th>
              </tr>
            </thead>
            <tbody>
              {invitations.data.map((invitation) => (
                <tr key={invitation.id}>
                  <td>
                    <div className="person">
                      <Avatar name={invitation.email} icon="Mail" size="sm" />
                      <span className="person-email">{invitation.email}</span>
                    </div>
                  </td>
                  <td>
                    <Badge>{ROLE_NAMES[invitation.role]}</Badge>
                  </td>
                  <td className="muted small">{formatDateTime(invitation.expiresAt)}</td>
                  <td className="actions">
                    <button
                      type="button"
                      className="button button-sm button-ghost"
                      disabled={revoke.isPending}
                      onClick={() => revoke.mutate(invitation.id)}
                    >
                      Revocar
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </>
  );
}
