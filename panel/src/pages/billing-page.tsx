import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { useSearchParams } from 'react-router';
import {
  billingApi,
  type BillingOverview,
  type PlanView,
  type Quota,
  type SubscriptionStatus,
  type UsageMetric,
} from '../api/billing';
import { usePermissions } from '../auth/auth-context';
import { Icons } from '../components/icons';
import { Badge, Empty, ErrorAlert, PageHeader, Spinner, type Tone } from '../components/ui';
import { formatDateTime, formatMoney, formatNumber, METRIC_NAMES } from '../format';

const STATUSES: Record<SubscriptionStatus, { label: string; tone: Tone }> = {
  NONE: { label: 'Sin suscripción', tone: 'neutral' },
  ACTIVE: { label: 'Activa', tone: 'success' },
  TRIALING: { label: 'En prueba', tone: 'info' },
  PAST_DUE: { label: 'Pago pendiente', tone: 'warning' },
  UNPAID: { label: 'Sin pagar', tone: 'danger' },
  CANCELED: { label: 'Cancelada', tone: 'neutral' },
  INCOMPLETE: { label: 'Pago incompleto', tone: 'warning' },
  INCOMPLETE_EXPIRED: { label: 'Pago vencido', tone: 'neutral' },
  PAUSED: { label: 'En pausa', tone: 'warning' },
};

const LIVE: readonly SubscriptionStatus[] = ['ACTIVE', 'TRIALING', 'PAST_DUE', 'UNPAID', 'INCOMPLETE', 'PAUSED'];

/** Leaving for Stripe's pages; replaced in tests, where there is nowhere to go. */
export const navigation = {
  go(url: string) {
    window.location.assign(url);
  },
};

export function BillingPage() {
  const { canManageBilling } = usePermissions();
  const [params] = useSearchParams();
  const returnedFromCheckout = params.get('checkout') === 'success';
  const returnedFromTopUp = params.get('top-up') === 'success';
  const overview = useQuery({
    queryKey: ['billing'],
    queryFn: billingApi.overview,
    // Stripe confirms the purchase a few seconds after the checkout page sends the owner back
    refetchInterval: (query) => {
      const data = query.state.data;
      const waitingForPlan = returnedFromCheckout && data?.status === 'NONE';
      const waitingForTopUp = returnedFromTopUp && !data?.quotas.some((quota) => quota.extra > 0);
      return waitingForPlan || waitingForTopUp ? 3000 : false;
    },
  });
  const data = overview.data;

  return (
    <section aria-labelledby="billing-title">
      <PageHeader
        id="billing-title"
        eyebrow="Administración"
        title="Plan y pagos"
        description="Tu plan, lo que llevas usado este mes y tus facturas."
        actions={
          data && (
            <Badge tone={STATUSES[data.status].tone} dot>
              {STATUSES[data.status].label}
            </Badge>
          )
        }
      />
      <ErrorAlert error={overview.error} />
      {overview.isPending && <Spinner />}
      {params.get('checkout') === 'cancelled' && (
        <p className="alert alert-info" role="status">
          No se completó el pago; tu plan no cambió.
        </p>
      )}
      {returnedFromCheckout && data?.status === 'NONE' && (
        <p className="alert alert-info" role="status">
          Pago recibido. Tu plan se activa en cuanto Stripe lo confirme, en unos segundos.
        </p>
      )}
      {params.get('top-up') === 'cancelled' && (
        <p className="alert alert-info" role="status">
          No se completó el pago de la recarga; no se te cobró nada.
        </p>
      )}
      {returnedFromTopUp && data && !data.quotas.some((quota) => quota.extra > 0) && (
        <p className="alert alert-info" role="status">
          Pago recibido. La recarga se suma a tu plan en cuanto Stripe lo confirme, en unos segundos.
        </p>
      )}
      {data && (
        <>
          <Notices overview={data} />
          <CurrentPlan overview={data} canManage={canManageBilling} />
          <TopUps overview={data} canManage={canManageBilling} />
          <h2 className="section-title">Planes</h2>
          <Plans overview={data} canManage={canManageBilling} />
          <h2 className="section-title">Facturas</h2>
          <Invoices enabled={data.hasPaymentAccount} />
        </>
      )}
    </section>
  );
}

function Notices({ overview }: { overview: BillingOverview }) {
  const paidPlan = overview.plans.find((plan) => plan.code === overview.paidPlanCode)?.name ?? overview.paidPlanCode;
  return (
    <>
      {!overview.paymentsEnabled && (
        <p className="alert alert-info" role="status">
          Los pagos no están configurados en este servidor: todos los espacios de trabajo usan el plan {overview.plan.name}.
        </p>
      )}
      {overview.status === 'PAST_DUE' && overview.graceEndsAt && overview.plan.code === overview.paidPlanCode && (
        <p className="alert alert-warning" role="alert">
          No se pudo cobrar tu plan. Actualiza tu método de pago antes del {formatDateTime(overview.graceEndsAt)} o el
          espacio de trabajo pasará al plan {freePlanName(overview)}.
        </p>
      )}
      {overview.paidPlanCode && overview.plan.code !== overview.paidPlanCode && LIVE.includes(overview.status) && (
        <p className="alert alert-danger" role="alert">
          El pago del plan {paidPlan} sigue pendiente, así que se aplican los límites del plan {overview.plan.name}. Paga la
          factura pendiente para recuperarlo.
        </p>
      )}
      {overview.cancelAtPeriodEnd && overview.currentPeriodEnd && (
        <p className="alert alert-info" role="status">
          Tu plan termina el {formatDateTime(overview.currentPeriodEnd)}; después pasarás al plan {freePlanName(overview)}.
        </p>
      )}
    </>
  );
}

function CurrentPlan({ overview, canManage }: { overview: BillingOverview; canManage: boolean }) {
  const portal = useMutation({
    mutationFn: billingApi.portal,
    onSuccess: (session) => navigation.go(session.url),
  });
  return (
    <div className="card">
      <div className="card-header">
        <div>
          <h2 className="card-title">Plan {overview.plan.name}</h2>
          <p>
            {formatMoney(overview.plan.monthlyPrice, overview.currency)} al mes
            {overview.currentPeriodEnd && !overview.cancelAtPeriodEnd && (
              <> · se renueva el {formatDateTime(overview.currentPeriodEnd)}</>
            )}
          </p>
        </div>
        {canManage && overview.paymentsEnabled && overview.hasPaymentAccount && (
          <button type="button" className="button" onClick={() => portal.mutate()} disabled={portal.isPending}>
            <Icons.Card />
            Administrar pagos
          </button>
        )}
      </div>
      <ErrorAlert error={portal.error} />
      <ul className="quotas" aria-label="Uso del mes">
        {overview.quotas.map((quota) => (
          <QuotaRow key={quota.metric} quota={quota} currency={overview.currency} />
        ))}
      </ul>
      <p className="muted small">
        Estimado del mes:{' '}
        <strong aria-label="Estimado del mes">{formatMoney(overview.estimatedTotal, overview.currency)}</strong>
      </p>
    </div>
  );
}

function QuotaRow({ quota, currency }: { quota: Quota; currency: string }) {
  const name = METRIC_NAMES[quota.metric];
  if (quota.included === null) {
    return (
      <li>
        <span>{name}</span>
        <span className="muted small">{formatNumber(quota.used)} · sin límite</span>
      </li>
    );
  }
  const share = quota.included === 0 ? 1 : Math.min(1, quota.used / quota.included);
  return (
    <li className={quota.exhausted ? 'is-exhausted' : undefined}>
      <span>{name}</span>
      <span className="meter meter-wide" role="meter" aria-label={name} aria-valuemin={0}
        aria-valuemax={quota.included} aria-valuenow={Math.min(quota.used, quota.included)}>
        <span style={{ width: `${share * 100}%` }} />
      </span>
      <span className="small">
        {formatNumber(quota.used)} de {formatNumber(quota.included)}
        {quota.extra > 0 && <span className="muted"> · incluye {formatNumber(quota.extra)} de recargas</span>}
        {quota.overage > 0 && (
          <span className="muted">
            {' '}
            · {formatNumber(quota.overage)} extra = {formatMoney(quota.overageCost, currency)}
          </span>
        )}
        {quota.exhausted && <Badge tone="danger">Agotado</Badge>}
      </span>
    </li>
  );
}

/**
 * Extra units for this month, for a plan that stops at its limit: a way out when a busy month runs short, without
 * changing plans. Plans that bill the extra need none, so nothing is shown to them.
 */
function TopUps({ overview, canManage }: { overview: BillingOverview; canManage: boolean }) {
  const buy = useMutation({
    mutationFn: billingApi.buyTopUp,
    onSuccess: (purchase) => navigation.go(purchase.checkoutUrl),
  });
  const offered = overview.topUps.filter((topUp) => topUp.available);
  if (offered.length === 0) {
    return null;
  }
  return (
    <>
      <h2 className="section-title" id="top-ups-title">
        Recargas
      </h2>
      <p className="muted small">
        Un pago único que suma unidades a tu plan hasta el fin de este mes, sin cambiar de plan.
      </p>
      <ErrorAlert error={buy.error} />
      <div className="plans top-ups" aria-labelledby="top-ups-title">
        {offered.map((topUp) => (
          <article key={topUp.code} className="card plan" aria-label={`Recarga ${topUp.name}`}>
            <h3 className="card-title">{topUp.name}</h3>
            <p className="plan-price">
              {formatMoney(topUp.price, overview.currency)}
              <span className="muted small"> · pago único</span>
            </p>
            <p className="muted small">
              +{formatNumber(topUp.quantity)} {lowerFirst(METRIC_NAMES[topUp.metric])} hasta el fin de mes
            </p>
            {canManage && (
              <button
                type="button"
                className="button"
                disabled={!topUp.purchasable || buy.isPending}
                onClick={() => buy.mutate(topUp.code)}
              >
                Comprar recarga
              </button>
            )}
          </article>
        ))}
      </div>
    </>
  );
}

/**
 * The cheapest plan above the current one that includes more of what is running short, once a limited metric
 * reaches 80 % of what the month includes.
 */
function recommendedPlan(overview: BillingOverview): string | null {
  const short = overview.quotas.filter(
    (quota) => quota.included !== null && (quota.exhausted || quota.used >= quota.included * 0.8),
  );
  if (short.length === 0) {
    return null;
  }
  const better = overview.plans
    .filter((plan) => plan.purchasable && plan.monthlyPrice > overview.plan.monthlyPrice)
    .filter((plan) =>
      short.every((quota) => {
        const included = plan.included[quota.metric];
        // Unlimited, billed past its limit, or simply more than now
        return (
          included === undefined || plan.overagePrices[quota.metric] !== undefined || included > (quota.included ?? 0)
        );
      }),
    )
    .sort((left, right) => left.monthlyPrice - right.monthlyPrice);
  return better[0]?.code ?? null;
}

function Plans({ overview, canManage }: { overview: BillingOverview; canManage: boolean }) {
  const client = useQueryClient();
  const [changed, setChanged] = useState(false);
  const choose = useMutation({
    mutationFn: billingApi.choosePlan,
    onSuccess: async (change) => {
      if (change.checkoutUrl) {
        navigation.go(change.checkoutUrl);
        return;
      }
      setChanged(true);
      await client.invalidateQueries({ queryKey: ['billing'] });
    },
  });
  const live = overview.paidPlanCode !== null && LIVE.includes(overview.status);
  const subscribedTo = live && !overview.cancelAtPeriodEnd ? overview.paidPlanCode : overview.plan.code;
  const recommended = recommendedPlan(overview);

  return (
    <>
      <ErrorAlert error={choose.error} />
      {changed && (
        <p className="alert alert-success" role="status">
          Cambio solicitado. Se aplica en cuanto Stripe lo confirme.
        </p>
      )}
      <div className="plans">
        {overview.plans.map((plan) => {
          const current = plan.code === subscribedTo;
          const free = plan.monthlyPrice === 0 && Object.keys(plan.overagePrices).length === 0;
          // The free plan is reached by ending a live subscription, never bought
          const available = free ? live && !overview.cancelAtPeriodEnd : plan.purchasable;
          return (
            <article key={plan.code} className={`card plan${current ? ' is-current' : ''}`} aria-label={`Plan ${plan.name}`}>
              <h3 className="card-title">
                {plan.name}
                {plan.code === recommended && !current && <Badge tone="success">Recomendado</Badge>}
              </h3>
              <p className="plan-price">
                {formatMoney(plan.monthlyPrice, overview.currency)}
                <span className="muted small"> / mes</span>
              </p>
              <PlanTerms plan={plan} currency={overview.currency} />
              {current ? (
                <Badge tone="info">Tu plan</Badge>
              ) : (
                canManage && (
                  <button
                    type="button"
                    className={`button${free ? '' : ' button-primary'}`}
                    disabled={!available || choose.isPending}
                    onClick={() => choose.mutate(plan.code)}
                  >
                    {free ? 'Cambiar al final del periodo' : live ? `Cambiar a ${plan.name}` : `Contratar ${plan.name}`}
                  </button>
                )
              )}
            </article>
          );
        })}
      </div>
    </>
  );
}

function PlanTerms({ plan, currency }: { plan: PlanView; currency: string }) {
  const metrics = Object.keys(plan.included) as UsageMetric[];
  return (
    <ul className="plan-terms">
      {metrics.map((metric) => {
        const overage = plan.overagePrices[metric];
        return (
          <li key={metric}>
            {formatNumber(plan.included[metric] ?? 0)} {lowerFirst(METRIC_NAMES[metric])}
            <span className="muted small">
              {overage === undefined ? ' · se detiene al límite' : ` · luego ${formatMoney(overage, currency)} c/u`}
            </span>
          </li>
        );
      })}
    </ul>
  );
}

function Invoices({ enabled }: { enabled: boolean }) {
  const invoices = useQuery({ queryKey: ['billing', 'invoices'], queryFn: billingApi.invoices, enabled });
  if (!enabled || invoices.data?.length === 0) {
    return <Empty icon="File">Aún no hay facturas.</Empty>;
  }
  return (
    <>
      <ErrorAlert error={invoices.error} />
      {invoices.isPending && <Spinner />}
      {invoices.data && (
        <div className="table-wrap">
          <table className="table">
            <thead>
              <tr>
                <th scope="col">Factura</th>
                <th scope="col">Fecha</th>
                <th scope="col">Estado</th>
                <th scope="col" className="numeric">
                  Total
                </th>
                <th scope="col">
                  <span className="sr-only">Descargar</span>
                </th>
              </tr>
            </thead>
            <tbody>
              {invoices.data.map((invoice) => (
                <tr key={invoice.id}>
                  <td className="mono">{invoice.number ?? invoice.id}</td>
                  <td>{invoice.createdAt ? formatDateTime(invoice.createdAt) : '—'}</td>
                  <td>
                    <Badge tone={invoice.status === 'paid' ? 'success' : invoice.status === 'open' ? 'warning' : 'neutral'}>
                      {INVOICE_STATUSES[invoice.status] ?? invoice.status}
                    </Badge>
                  </td>
                  <td className="numeric">{formatMoney(invoice.total, invoice.currency)}</td>
                  <td className="row">
                    {invoice.hostedUrl && (
                      <a href={invoice.hostedUrl} target="_blank" rel="noreferrer">
                        Ver
                      </a>
                    )}
                    {invoice.pdfUrl && (
                      <a href={invoice.pdfUrl} target="_blank" rel="noreferrer">
                        PDF
                      </a>
                    )}
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

const INVOICE_STATUSES: Record<string, string> = {
  draft: 'Borrador',
  open: 'Pendiente',
  paid: 'Pagada',
  uncollectible: 'Incobrable',
  void: 'Anulada',
};

/** "Mensajes de WhatsApp" → "mensajes de WhatsApp": only the first letter, so names keep their capitals. */
function lowerFirst(text: string): string {
  return text.charAt(0).toLowerCase() + text.slice(1);
}

function freePlanName(overview: BillingOverview): string {
  return overview.plans.find((plan) => plan.monthlyPrice === 0)?.name ?? 'gratuito';
}
