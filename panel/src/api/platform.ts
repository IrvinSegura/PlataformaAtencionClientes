// Administration of the platform itself (backend module platform)

import { pathSegment, request } from './http-client';
import type { WorkspaceSuspension } from './workspaces';

/** The plan of a workspace and the month's answers, as the platform administration sees them. */
export interface PlatformPlan {
  planCode: string;
  planName: string;
  monthlyPrice: number;
  /** ACTIVE, PAST_DUE, CANCELED... or NONE on the free plan. */
  subscriptionStatus: string;
  answersThisMonth: number;
  /** null when unlimited. */
  answersIncluded: number | null;
}

export interface PlatformWorkspace {
  id: string;
  name: string;
  createdAt: string;
  members: number;
  ownerEmail: string | null;
  ownerName: string | null;
  plan: PlatformPlan;
  conversationsLastMonth: number;
  lastActivityAt: string | null;
  suspension: WorkspaceSuspension | null;
}

export interface PlatformAccess {
  platformAdmin: boolean;
}

export interface PlatformWorkspacePage {
  items: PlatformWorkspace[];
  total: number;
}

/** Administration of the platform itself; only the users configured to run it get past /me. */
export const platformApi = {
  me: () => request<PlatformAccess>('/api/v1/platform/me'),
  workspaces: (query: string, page: number, size = 20) =>
    request<PlatformWorkspacePage>(
      `/api/v1/platform/workspaces?page=${page}&size=${size}${query ? `&query=${encodeURIComponent(query)}` : ''}`,
    ),
  suspend: (workspaceId: string, reason: string) =>
    request<PlatformWorkspace>(`/api/v1/platform/workspaces/${pathSegment(workspaceId)}/suspension`, { json: { reason } }),
  reinstate: (workspaceId: string) =>
    request<PlatformWorkspace>(`/api/v1/platform/workspaces/${pathSegment(workspaceId)}/suspension`, { method: 'DELETE' }),
};
