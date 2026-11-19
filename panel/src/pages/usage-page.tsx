import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { useState } from 'react';
import { Link } from 'react-router';
import { currentMonth, type Quota, usageApi } from '../api/billing';
import { Badge, Empty, ErrorAlert, PageHeader, Spinner } from '../components/ui';
import { formatMoney, formatNumber, METRIC_NAMES } from '../format';

const DIMENSION_NAMES: Record<string, string> = {
  WEB: 'Widget web',
  WHATSAPP: 'WhatsApp',
  EMAIL: 'Correo',
  MESSENGER: 'Messenger',
  INSTAGRAM: 'Instagram',
  inbound: 'recibidos',
  outbound: 'enviados',
};

export function UsagePage() {
  const [month, setMonth] = useState(currentMonth);
  const report = useQuery({
    queryKey: ['usage', month],
    queryFn: () => usageApi.month(month),
    placeholderData: keepPreviousData,
  });
  const data = report.data;

  return (
    <section aria-labelledby="usage-title">
      <PageHeader
        id="usage-title"
        eyebrow="Analítica"
        title="Consumo"
        description="Lo que consumió tu espacio de trabajo este mes y lo que costaría con tu plan actual."
        actions={
          <label className="row">
            <span className="muted small">Mes</span>
            <input
              type="month"
              value={month}
              max={currentMonth()}
              onChange={(event) => event.target.value && setMonth(event.target.value)}
              aria-label="Mes"
            />
          </label>
        }
      />
      <ErrorAlert error={report.error} />
      {report.isPending && <Spinner />}
      {data && data.lines.length === 0 && <Empty icon="Chart">Sin consumo en este mes.</Empty>}
      {data && data.lines.length > 0 && (
        <>
          <div className="card">
            <div className="card-header">
              <div>
                <h2 className="card-title">Total estimado</h2>
                <p className="usage-total" aria-label="Total estimado">
                  {formatMoney(data.estimatedTotal, data.currency)}
                </p>
                <p>
                  Plan {data.planName}: {formatMoney(data.monthlyPrice, data.currency)} al mes más el consumo extra.{' '}
                  <Link to="/billing">Ver planes</Link>
                </p>
              </div>
            </div>
            <div className="table-wrap">
              <table className="table">
                <thead>
                  <tr>
                    <th scope="col">Concepto</th>
                    <th scope="col" className="numeric">
                      Usado
                    </th>
                    <th scope="col" className="numeric">
                      Incluido
                    </th>
                    <th scope="col" className="numeric">
                      Extra
                    </th>
                  </tr>
                </thead>
                <tbody>
                  {data.quotas.map((quota) => (
                    <tr key={quota.metric}>
                      <td>
                        <div className="cell-title">{METRIC_NAMES[quota.metric]}</div>
                        {quota.exhausted && <Badge tone="danger">Límite alcanzado</Badge>}
                      </td>
                      <td className="numeric">{formatNumber(quota.used)}</td>
                      <td className="numeric">{quota.included === null ? 'Sin límite' : formatNumber(quota.included)}</td>
                      <td className="numeric">{extra(quota, data.currency)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>
          <h2 className="section-title">Detalle</h2>
          <div className="table-wrap">
            <table className="table">
              <thead>
                <tr>
                  <th scope="col">Concepto</th>
                  <th scope="col" className="numeric">
                    Cantidad
                  </th>
                </tr>
              </thead>
              <tbody>
                {data.lines.map((line) => (
                  <tr key={`${line.metric}:${line.dimension}`}>
                    <td>
                      <div className="cell-title">{METRIC_NAMES[line.metric] ?? line.metric}</div>
                      <div className="muted small mono">{DIMENSION_NAMES[line.dimension] ?? line.dimension}</div>
                    </td>
                    <td className="numeric">{formatNumber(line.quantity)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <h2 className="section-title">Por día</h2>
          <div className="table-wrap">
            <table className="table">
              <thead>
                <tr>
                  <th scope="col">Día</th>
                  <th scope="col" className="numeric">
                    Respuestas
                  </th>
                  <th scope="col" className="numeric">
                    Tokens (entrada / salida)
                  </th>
                  <th scope="col" className="numeric">
                    Acciones
                  </th>
                  <th scope="col" className="numeric">
                    WhatsApp
                  </th>
                </tr>
              </thead>
              <tbody>
                {data.days.map((day) => (
                  <tr key={day.day}>
                    <td className="mono">{day.day}</td>
                    <td className="numeric">{formatNumber(day.answers)}</td>
                    <td className="numeric">
                      {formatNumber(day.inputTokens)} / {formatNumber(day.outputTokens)}
                    </td>
                    <td className="numeric">{formatNumber(day.actionRuns)}</td>
                    <td className="numeric">{formatNumber(day.whatsappMessages)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </>
      )}
    </section>
  );
}

function extra(quota: Quota, currency: string): string {
  if (quota.overage > 0) {
    return `${formatNumber(quota.overage)} · ${formatMoney(quota.overageCost, currency)}`;
  }
  if (quota.overagePrice === null) {
    return '—';
  }
  return `${formatMoney(quota.overagePrice, currency)} c/u`;
}
