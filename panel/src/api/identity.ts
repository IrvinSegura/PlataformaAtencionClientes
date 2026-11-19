// Operators: sessions, profile, the team and invitations (backend module identity)

import { INVITATION_ACCEPTANCE_PATH, pathSegment, request, startSession } from './http-client';
import type { WorkspaceMembership, WorkspaceRole } from './workspaces';

export interface SessionResponse {
  accessToken: string;
  accessTokenExpiresAt: string;
  userId: string;
  workspaceId: string;
  role: WorkspaceRole;
}

export type Session = Omit<SessionResponse, 'accessToken'>;

export interface OperatorProfile {
  id: string;
  email: string;
  displayName: string;
  /** The terms of service in force. */
  termsVersion: string;
  /** Whether the operator accepted them; the panel asks until they do. */
  termsAccepted: boolean;
}

export interface Member {
  userId: string;
  email: string;
  displayName: string;
  role: WorkspaceRole;
  joinedAt: string;
}

export interface Invitation {
  id: string;
  email: string;
  role: WorkspaceRole;
  createdAt: string;
  expiresAt: string;
}

/** The token is shown once, right after inviting; the server keeps only its hash. */
export interface IssuedInvitation {
  invitation: Invitation;
  token: string;
}

export interface InvitationPreview {
  workspaceName: string;
  email: string;
  role: WorkspaceRole;
  accountExists: boolean;
}

export interface RegisterInput {
  email: string;
  password: string;
  displayName: string;
  workspaceName: string;
  /** The terms of service in force; no account is created without them. */
  acceptTerms: boolean;
}

/** Public pages the backend serves, outside the panel. */
export const LEGAL_LINKS = {
  terms: '/legal/terms',
  privacy: '/legal/privacy',
};

export const authApi = {
  async login(email: string, password: string): Promise<Session> {
    return startSession(await request<SessionResponse>('/api/v1/auth/login', { json: { email, password } }));
  },
  async register(input: RegisterInput): Promise<Session> {
    return startSession(await request<SessionResponse>('/api/v1/auth/register', { json: input }));
  },
  async switchWorkspace(workspaceId: string): Promise<Session> {
    return startSession(await request<SessionResponse>('/api/v1/auth/workspace-switch', { json: { workspaceId } }));
  },
  me: () => request<OperatorProfile>('/api/v1/me'),
  /** Accepts the version of the terms that was shown, which must still be the one in force. */
  acceptTerms: (version: string) =>
    request<OperatorProfile>('/api/v1/me/terms-acceptance', { json: { version } }),
  workspaces: () => request<WorkspaceMembership[]>('/api/v1/me/workspaces'),
};

export const teamApi = {
  members: () => request<Member[]>('/api/v1/workspace/members'),
  changeRole: (userId: string, role: WorkspaceRole) =>
    request<void>(`/api/v1/workspace/members/${pathSegment(userId)}/role`, { method: 'PUT', json: { role } }),
  remove: (userId: string) => request<void>(`/api/v1/workspace/members/${pathSegment(userId)}`, { method: 'DELETE' }),
  invitations: () => request<Invitation[]>('/api/v1/workspace/invitations'),
  invite: (email: string, role: WorkspaceRole) =>
    request<IssuedInvitation>('/api/v1/workspace/invitations', { json: { email, role } }),
  revoke: (invitationId: string) =>
    request<void>(`/api/v1/workspace/invitations/${pathSegment(invitationId)}`, { method: 'DELETE' }),
};

export const invitationsApi = {
  preview: (token: string) => request<InvitationPreview>('/api/v1/invitations/preview', { json: { token } }),
  /** A new account needs the terms accepted; an existing one may accept them here too. */
  async accept(token: string, displayName: string, password: string, acceptTerms: boolean): Promise<Session> {
    return startSession(
      await request<SessionResponse>(INVITATION_ACCEPTANCE_PATH, {
        json: { token, displayName, password, acceptTerms },
      }),
    );
  },
};
