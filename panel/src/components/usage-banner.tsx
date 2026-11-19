import { useQuery } from '@tanstack/react-query';
import { Link, useLocation } from 'react-router';
import { currentMonth, type Quota, usageApi, type UsageMetric } from '../api/billing';
import { usePermissions } from '../auth/auth-context';
import { formatNumber } from '../format';

// The shares the owner is also told by WhatsApp
const WARN_AT = 0.8;
const REFRESH_MS = 5 * 60 * 1000;

const NAMES: Partial<Record<UsageMetric, string>> = {
  ANSWERS: 'respuestas del asistente',
  ACTION_RUNS: 'acciones',
  WHATSAPP_MESSAGES: 'mensajes de WhatsApp',
};

const STOPS: Partial<Record<UsageMetric, string>> = {
  ANSWERS: 'el asistente deja de responder y tu equipo atiende las conversaciones',
  ACTION_RUNS: 'el asistente deja de ejecutar acciones',
  WHATSAPP_MESSAGES: 'los mensajes de WhatsApp de tus clientes dejan de atenderse',
};

/**
 * Tells owners and administrators, on every page, that the month's usage reached 80 % of what the plan includes, or
 * ran out, before customers notice. The plan page already shows it, so it stays out of there.
 */
export function UsageBanner() {
  const { canSeeUsage } = usePermissions();
  const { pathname } = useLocation();
  const month = currentMonth();
  const report = useQuery({
    queryKey: ['usage', month],
    queryFn: () => usageApi.month(month),
    enabled: canSeeUsage,
    refetchInterval: REFRESH_MS,
  });
  const quota = closestToLimit(report.data?.quotas ?? []);
  if (!canSeeUsage || !quota || pathname.startsWith('/billing')) {
    return null;
  }
  const included = quota.included ?? 0;
  const name = NAMES[quota.metric] ?? quota.metric;
  const percent = Math.floor((quota.used * 100) / included);
  const counts = `${formatNumber(quota.used)} de ${formatNumber(included)}`;
  let message: string;
  if (quota.exhausted) {
    message = `Se acabaron las ${name} de tu plan este mes (${counts}): ${STOPS[quota.metric] ?? 'se detiene'} hasta el próximo mes.`;
  } else if (quota.used >= included) {
    message = `Ya pasaste las ${name} que incluye tu plan este mes (${counts}); lo adicional se cobra aparte.`;
  } else {
    message =
      `Llevas el ${percent} % de las ${name} de tu plan este mes (${counts}). ` +
      (quota.overagePrice !== null
        ? 'Lo que pase del límite se cobra aparte.'
        : `Al llegar al límite, ${STOPS[quota.metric] ?? 'se detiene'}.`);
  }
  return (
    <div className={`alert ${quota.exhausted ? 'alert-danger' : 'alert-warning'} usage-banner`} role="status">
      <p>{message}</p>
      <Link to="/billing" className="button button-sm">
        {/* A plan that stops at its limit can buy a recharge; one that bills the extra only changes plans */}
        {quota.overagePrice === null ? 'Recargar o cambiar de plan' : 'Ver planes'}
      </Link>
    </div>
  );
}

/** The limited metric with the highest share used, once it reaches the warning share. */
function closestToLimit(quotas: Quota[]): Quota | null {
  let closest: Quota | null = null;
  let highest = WARN_AT;
  for (const quota of quotas) {
    if (!quota.included || NAMES[quota.metric] === undefined) {
      continue;
    }
    const share = quota.used / quota.included;
    if (share >= highest) {
      closest = quota;
      highest = share;
    }
  }
  return closest;
}
