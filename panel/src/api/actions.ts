// Assistant actions and integrations (backend module actions)

import { pathSegment, request } from './http-client';

export type ActionRunStatus =
  | 'PENDING_CONFIRMATION'
  | 'RUNNING'
  | 'SUCCEEDED'
  | 'FAILED'
  | 'CANCELLED'
  | 'EXPIRED'
  | 'REJECTED';

export interface ActionRun {
  id: string;
  actionName: string;
  actionTitle: string;
  messageId: string;
  arguments: Record<string, unknown>;
  status: ActionRunStatus;
  httpStatus: number | null;
  error: string | null;
  latencyMs: number | null;
  createdAt: string;
  expiresAt: string | null;
  finishedAt: string | null;
}

export type ParameterType = 'STRING' | 'NUMBER' | 'BOOLEAN';

export interface ActionParameter {
  name: string;
  type: ParameterType;
  description: string;
  required: boolean;
  pattern: string | null;
}

export interface AssistantAction {
  id: string;
  name: string;
  title: string;
  description: string;
  endpointUrl: string;
  parameters: ActionParameter[];
  requiresConfirmation: boolean;
  enabled: boolean;
  createdAt: string;
  updatedAt: string;
}

export type IntegrationProvider = 'SHOPIFY' | 'ZENDESK';

/** An action an integration gives the assistant once connected. */
export interface IntegrationActionDefinition {
  name: string;
  title: string;
  description: string;
  parameters: ActionParameter[];
  requiresConfirmation: boolean;
}

/** A ready-made integration; the token is never sent back. */
export interface Integration {
  provider: IntegrationProvider;
  connected: boolean;
  /** The Shopify store (mitienda.myshopify.com) or the Zendesk subdomain; null until connected. */
  account: string | null;
  /** The Zendesk agent's email; null for Shopify. */
  username: string | null;
  enabled: boolean;
  actions: IntegrationActionDefinition[];
  updatedAt: string | null;
}

/** A blank token keeps the saved one. */
export interface IntegrationInput {
  account: string;
  username: string;
  token: string;
  enabled: boolean;
}

export type AssistantActionInput = Omit<AssistantAction, 'id' | 'createdAt' | 'updatedAt'>;

export interface SigningSecret {
  value: string;
  rotatedAt: string;
}

export const actionsApi = {
  list: () => request<AssistantAction[]>('/api/v1/actions'),
  create: (action: AssistantActionInput) => request<AssistantAction>('/api/v1/actions', { json: action }),
  update: (actionId: string, action: AssistantActionInput) =>
    request<AssistantAction>(`/api/v1/actions/${pathSegment(actionId)}`, { method: 'PUT', json: action }),
  remove: (actionId: string) => request<void>(`/api/v1/actions/${pathSegment(actionId)}`, { method: 'DELETE' }),
  signingSecret: () => request<SigningSecret>('/api/v1/actions/signing-secret'),
  rotateSigningSecret: () => request<SigningSecret>('/api/v1/actions/signing-secret/rotation', { method: 'POST' }),
  integrations: () => request<Integration[]>('/api/v1/actions/integrations'),
  saveIntegration: (provider: IntegrationProvider, input: IntegrationInput) =>
    request<Integration>(`/api/v1/actions/integrations/${provider.toLowerCase()}`, { method: 'PUT', json: input }),
  disconnectIntegration: (provider: IntegrationProvider) =>
    request<void>(`/api/v1/actions/integrations/${provider.toLowerCase()}`, { method: 'DELETE' }),
};
