// The workspace and its widget (backend module workspaces)

import { request } from './http-client';

export type WorkspaceRole = 'OWNER' | 'ADMIN' | 'AGENT';

export interface Workspace {
  id: string;
  name: string;
  widgetKey: string;
  allowedOrigins: string[];
  botName: string;
  welcomeMessage: string;
  createdAt: string;
  /** Why whoever runs the platform suspended the workspace; null while it is not. */
  suspension: WorkspaceSuspension | null;
}

export interface WorkspaceSuspension {
  reason: string;
  suspendedAt: string;
}

export interface WidgetSettings {
  botName: string;
  welcomeMessage: string;
  allowedOrigins: string[];
}

export interface WorkspaceMembership {
  workspaceId: string;
  workspaceName: string;
  role: WorkspaceRole;
}

export const workspaceApi = {
  get: () => request<Workspace>('/api/v1/workspace'),
  changeWidgetSettings: (settings: WidgetSettings) =>
    request<Workspace>('/api/v1/workspace/widget-settings', { method: 'PUT', json: settings }),
  rotateWidgetKey: () => request<Workspace>('/api/v1/workspace/widget-key/rotation', { method: 'POST' }),
};
