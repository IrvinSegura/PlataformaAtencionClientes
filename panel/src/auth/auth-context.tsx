import { useQueryClient } from '@tanstack/react-query';
import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState, type ReactNode } from 'react';
import { onSessionChange, refreshSession, signOut } from '../api/http-client';
import { authApi, type RegisterInput, type Session } from '../api/identity';
import type { WorkspaceRole } from '../api/workspaces';

type AuthState = { status: 'loading' } | { status: 'signedOut' } | { status: 'signedIn'; session: Session };

interface AuthContextValue {
  state: AuthState;
  login(email: string, password: string): Promise<void>;
  register(input: RegisterInput): Promise<void>;
  logout(): Promise<void>;
}

const AuthContext = createContext<AuthContextValue | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [state, setState] = useState<AuthState>({ status: 'loading' });
  const queryClient = useQueryClient();

  const current = useRef<Session | null>(null);

  useEffect(() => {
    onSessionChange((session) => {
      const previous = current.current;
      current.current = session;
      // Nothing of the previous operator or workspace may remain visible after signing out, accepting an
      // invitation as someone else or switching workspace. Without a previous session there is nothing to hide, and
      // clearing would cancel what public pages (an invitation link) are already loading
      if (
        previous !== null &&
        (session === null || previous.userId !== session.userId || previous.workspaceId !== session.workspaceId)
      ) {
        queryClient.clear();
      }
      setState(session === null ? { status: 'signedOut' } : { status: 'signedIn', session });
    });
    // A reload loses the in-memory access token; the refresh cookie restores the session if it is still valid
    refreshSession().catch(() => setState({ status: 'signedOut' }));
  }, [queryClient]);

  const login = useCallback(async (email: string, password: string) => {
    await authApi.login(email, password);
  }, []);
  const register = useCallback(async (input: RegisterInput) => {
    await authApi.register(input);
  }, []);
  const logout = useCallback(() => signOut(), []);

  const value = useMemo(() => ({ state, login, register, logout }), [state, login, register, logout]);
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthContextValue {
  const context = useContext(AuthContext);
  if (context === null) {
    throw new Error('useAuth must be used inside AuthProvider');
  }
  return context;
}

/** The signed-in operator's session; only for components rendered behind RequireAuth. */
export function useSession(): Session {
  const { state } = useAuth();
  if (state.status !== 'signedIn') {
    throw new Error('useSession needs a signed-in operator');
  }
  return state.session;
}

const MANAGERS: readonly WorkspaceRole[] = ['OWNER', 'ADMIN'];

/** Mirrors the backend's rules, only to hide what the operator could not do anyway. */
export function usePermissions() {
  const { role } = useSession();
  return {
    canManageKnowledge: MANAGERS.includes(role),
    canEditWidget: MANAGERS.includes(role),
    canRotateWidgetKey: role === 'OWNER',
    canTriageGaps: MANAGERS.includes(role),
    canManageTeam: MANAGERS.includes(role),
    canChangeRoles: role === 'OWNER',
    // Release or close a conversation another member is answering
    canOverrideAgents: MANAGERS.includes(role),
    canManageActions: MANAGERS.includes(role),
    canRotateSigningSecret: role === 'OWNER',
    canManageChannels: MANAGERS.includes(role),
    canSeeUsage: MANAGERS.includes(role),
    // Every playground answer calls the model and counts against the plan
    canUsePlayground: MANAGERS.includes(role),
    canManageBilling: role === 'OWNER',
    canManageFollowUpTemplates: MANAGERS.includes(role),
  };
}
