// Usage, plans and payments (backend module billing)

import { request } from './http-client';

export type UsageMetric = 'ANSWERS' | 'INPUT_TOKENS' | 'OUTPUT_TOKENS' | 'ACTION_RUNS' | 'WHATSAPP_MESSAGES';

export interface UsageLine {
  metric: UsageMetric;
  dimension: string;
  quantity: number;
}

/** A limited metric this month under the plan that applies. */
export interface Quota {
  metric: UsageMetric;
  used: number;
  /** Null when the plan does not limit it; counts the recharges bought this month. */
  included: number | null;
  /** How many of the included units recharges added this month. */
  extra: number;
  /** Null when the metric stops at its included quantity. */
  overagePrice: number | null;
  overage: number;
  overageCost: number;
  exhausted: boolean;
}

export interface UsageDay {
  day: string;
  answers: number;
  inputTokens: number;
  outputTokens: number;
  actionRuns: number;
  whatsappMessages: number;
}

export interface UsageReport {
  month: string;
  currency: string;
  planCode: string;
  planName: string;
  monthlyPrice: number;
  lines: UsageLine[];
  quotas: Quota[];
  estimatedTotal: number;
  days: UsageDay[];
}

export type SubscriptionStatus =
  | 'NONE'
  | 'ACTIVE'
  | 'TRIALING'
  | 'PAST_DUE'
  | 'UNPAID'
  | 'CANCELED'
  | 'INCOMPLETE'
  | 'INCOMPLETE_EXPIRED'
  | 'PAUSED';

export interface PlanView {
  code: string;
  name: string;
  monthlyPrice: number;
  included: Partial<Record<UsageMetric, number>>;
  overagePrices: Partial<Record<UsageMetric, number>>;
  /** Whether it can be bought now: payments are on and Stripe has its prices. */
  purchasable: boolean;
}

export interface BillingOverview {
  /** The plan whose terms apply now. */
  plan: PlanView;
  /** The plan paid for, which differs from plan once payments stopped; null when none was ever bought. */
  paidPlanCode: string | null;
  status: SubscriptionStatus;
  currentPeriodEnd: string | null;
  cancelAtPeriodEnd: boolean;
  /** When a failed payment drops the workspace to the free plan, if not settled before. */
  graceEndsAt: string | null;
  paymentsEnabled: boolean;
  hasPaymentAccount: boolean;
  currency: string;
  quotas: Quota[];
  estimatedTotal: number;
  plans: PlanView[];
  topUps: TopUpView[];
}

/** Extra units of a metric, paid once and added for the month it is paid in. */
export interface TopUpView {
  code: string;
  name: string;
  metric: UsageMetric;
  quantity: number;
  price: number;
  /** The plan that applies stops at this metric's limit, so the recharge helps. */
  available: boolean;
  /** Available, and Stripe sells it. */
  purchasable: boolean;
}

export interface Invoice {
  id: string;
  number: string | null;
  status: string;
  total: number;
  currency: string;
  createdAt: string | null;
  periodStart: string | null;
  periodEnd: string | null;
  hostedUrl: string | null;
  pdfUrl: string | null;
}

/** The current month in UTC, as the backend counts it: "2026-11". */
export function currentMonth(now: Date = new Date()): string {
  return now.toISOString().slice(0, 7);
}

/** A first purchase answers with Stripe's checkout address; a plan change applies in place and answers none. */
export interface PlanChange {
  checkoutUrl: string | null;
}

/** Stripe's checkout address, where the recharge is paid once. */
export interface TopUpPurchase {
  checkoutUrl: string;
}

export interface PortalSession {
  url: string;
}

export const usageApi = {
  month: (month: string) => request<UsageReport>(`/api/v1/usage?month=${encodeURIComponent(month)}`),
};

export const billingApi = {
  overview: () => request<BillingOverview>('/api/v1/billing'),
  choosePlan: (plan: string) => request<PlanChange>('/api/v1/billing/plan', { json: { plan } }),
  buyTopUp: (topUp: string) => request<TopUpPurchase>('/api/v1/billing/top-ups', { json: { topUp } }),
  portal: () => request<PortalSession>('/api/v1/billing/portal', { method: 'POST' }),
  invoices: () => request<Invoice[]>('/api/v1/billing/invoices'),
};
