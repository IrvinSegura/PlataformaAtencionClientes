import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { useState, type CSSProperties, type ReactNode } from 'react';
import { Link, useSearchParams } from 'react-router';
import { insightsApi, type ModelQuality, type OutcomeDay, type OutcomeReport } from '../api/conversations';
import { Badge, Empty, ErrorAlert, PageHeader, Spinner } from '../components/ui';
import { formatDateTime, formatNumber, formatPercent } from '../format';
import { ROUTING_REASONS } from './playground-page';

const PERIODS = [7, 30, 90] as const;
// A reason whose answers customers call unhelpful this often, with enough opinions to tell, deserves a look
const REVIEW_RATE = 0.3;
const REVIEW_MIN_OPINIONS = 3;
const TIER_NAMES = { SIMPLE: 'Rápido', COMPLEX: 'Preciso' } as const;
const DEFAULT_PERIOD = 30;

// Stacked bottom to top in this order; each keeps its colour whatever the period shows
const SERIES = [
  { key: 'resolvedByAssistant', label: 'Resueltas por el asistente', className: 'series-1' },
  { key: 'handedOff', label: 'Pasaron a una persona', className: 'series-2' },
  { key: 'unresolved', label: 'Sin resolver', className: 'series-3' },
] as const;

const shortDay = new Intl.DateTimeFormat('es', { day: 'numeric', month: 'short', timeZone: 'UTC' });
const longDay = new Intl.DateTimeFormat('es', { weekday: 'long', day: 'numeric', month: 'long', timeZone: 'UTC' });
const decimal = new Intl.NumberFormat('es', { minimumFractionDigits: 1, maximumFractionDigits: 1 });

/** The last {@code days} UTC days, today included, as the backend counts them. */
export function periodOf(days: number, now: Date = new Date()): { from: string; to: string } {
  const to = new Date(Date.UTC(now.getUTCFullYear(), now.getUTCMonth(), now.getUTCDate()));
  const from = new Date(to.getTime() - (days - 1) * 86_400_000);
  return { from: isoDay(from), to: isoDay(to) };
}

/**
 * How well customers were helped: what the assistant solved alone, what needed a person, and what customers said.
 */
export function OutcomesPage() {
  const [params, setParams] = useSearchParams();
  const requested = Number(params.get('days'));
  const days = PERIODS.find((period) => period === requested) ?? DEFAULT_PERIOD;
  const { from, to } = periodOf(days);
  const report = useQuery({
    queryKey: ['outcomes', from, to],
    queryFn: () => insightsApi.outcomes(from, to),
    placeholderData: keepPreviousData,
  });
  const data = report.data;

  return (
    <section aria-labelledby="outcomes-title">
      <PageHeader
        id="outcomes-title"
        eyebrow="Analítica"
        title="Resultados"
        description="Cuántas conversaciones resolvió el asistente, cuántas pasaron a una persona y qué opinaron."
        actions={
          <label className="row">
            <span className="muted small">Periodo</span>
            <select
              value={days}
              aria-label="Periodo"
              onChange={(event) => setParams(Number(event.target.value) === DEFAULT_PERIOD ? {} : { days: event.target.value })}
            >
              {PERIODS.map((period) => (
                <option key={period} value={period}>
                  Últimos {period} días
                </option>
              ))}
            </select>
          </label>
        }
      />
      <ErrorAlert error={report.error} />
      {report.isPending && <Spinner />}
      {data && data.conversations === 0 && data.ratings === 0 && (
        <Empty icon="Chart">Sin conversaciones en este periodo. Cuando los clientes escriban, verás aquí los resultados.</Empty>
      )}
      {data && (data.conversations > 0 || data.ratings > 0) && (
        <div className="outcomes">
          <Headlines report={data} />
          <div className="card">
            <div className="card-header">
              <div>
                <h2 className="card-title">Conversaciones por día</h2>
                <p>
                  Del {shortDay.format(utc(data.from))} al {shortDay.format(utc(data.to))}
                </p>
              </div>
            </div>
            <DailyChart from={data.from} to={data.to} days={data.days} />
          </div>
          <ModelsCard report={data} />
          <div className="outcomes-columns">
            <Ratings report={data} />
            <Comments report={data} />
          </div>
        </div>
      )}
    </section>
  );
}

function Headlines({ report }: { report: OutcomeReport }) {
  const opinions = report.helpful + report.unhelpful;
  return (
    <ul className="kpis" aria-label="Resumen">
      <Kpi
        label="Resueltas por el asistente"
        value={report.resolutionRate === null ? '—' : formatPercent(report.resolutionRate)}
        detail={`${formatNumber(report.resolvedByAssistant)} de ${formatNumber(report.conversations)} conversaciones`}
      />
      <Kpi
        label="Pasaron a una persona"
        value={formatNumber(report.handedOff)}
        detail={`${formatNumber(report.unresolved)} quedaron sin resolver`}
      />
      <Kpi
        label="Respuestas útiles"
        value={report.helpfulRate === null ? '—' : formatPercent(report.helpfulRate)}
        detail={opinions === 0 ? 'Nadie opinó aún' : `${formatNumber(report.helpful)} de ${formatNumber(opinions)} opiniones`}
      />
      <Kpi
        label="Calificación promedio"
        value={report.averageRating === null ? '—' : `${decimal.format(report.averageRating)} / 5`}
        detail={
          report.satisfaction === null
            ? 'Sin calificaciones'
            : `${formatPercent(report.satisfaction)} dio 4 o 5 · ${formatNumber(report.ratings)} ${report.ratings === 1 ? 'calificación' : 'calificaciones'}`
        }
      />
      <Kpi
        label="Preguntas sin respuesta"
        value={formatNumber(report.notAnswered)}
        detail={<Link to="/knowledge/gaps?view=suggestions">Ver qué documentar</Link>}
      />
    </ul>
  );
}

/**
 * What customers said of the answers of each model and the reason it was chosen: a reason whose fast-model answers
 * are often unhelpful sends that model questions it cannot answer.
 */
function ModelsCard({ report }: { report: OutcomeReport }) {
  return (
    <div className="card">
      <div className="card-header">
        <div>
          <h2 className="card-title">Respuestas por modelo</h2>
          <p>
            Qué modelo respondió, por qué y cuántas veces el cliente dijo que no le ayudó.{' '}
            {report.difficultQuestions > 0 && (
              <Link to="/knowledge/difficult">
                {formatNumber(report.difficultQuestions)}{' '}
                {report.difficultQuestions === 1 ? 'pregunta difícil nueva' : 'preguntas difíciles nuevas'}
              </Link>
            )}
          </p>
        </div>
      </div>
      {report.models.length === 0 ? (
        <p className="muted small">Aún no hay respuestas del modelo en este periodo.</p>
      ) : (
        <div className="table-wrap">
          <table className="table">
            <thead>
              <tr>
                <th scope="col">Modelo</th>
                <th scope="col">Motivo</th>
                <th scope="col" className="numeric">
                  Respuestas
                </th>
                <th scope="col" className="numeric">
                  Sin respuesta
                </th>
                <th scope="col" className="numeric">
                  No me ayudó
                </th>
              </tr>
            </thead>
            <tbody>
              {report.models.map((row) => (
                <ModelRow key={`${row.tier ?? 'none'}-${row.reason}`} row={row} />
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}

function ModelRow({ row }: { row: ModelQuality }) {
  const opinions = row.helpful + row.unhelpful;
  const review = row.unhelpfulRate !== null && row.unhelpfulRate >= REVIEW_RATE && opinions >= REVIEW_MIN_OPINIONS;
  return (
    <tr>
      <td>{row.tier ? TIER_NAMES[row.tier] : '—'}</td>
      <td>{ROUTING_REASONS[row.reason] ?? row.reason}</td>
      <td className="numeric">{formatNumber(row.answers)}</td>
      <td className="numeric">{formatNumber(row.notAnswered)}</td>
      <td className="numeric">
        {row.unhelpfulRate === null ? (
          <span className="muted">Sin opiniones</span>
        ) : (
          <>
            {formatPercent(row.unhelpfulRate)}{' '}
            <span className="muted small">
              ({formatNumber(row.unhelpful)} de {formatNumber(opinions)})
            </span>
            {review && (
              <>
                {' '}
                <Badge tone="warning">Revisar</Badge>
              </>
            )}
          </>
        )}
      </td>
    </tr>
  );
}

function Kpi({ label, value, detail }: { label: string; value: string; detail: ReactNode }) {
  return (
    <li className="kpi">
      <span className="kpi-label">{label}</span>
      <strong className="kpi-value">{value}</strong>
      <span className="kpi-detail">{detail}</span>
    </li>
  );
}

/**
 * Stacked columns, one per day of the period; days without conversations stay empty so the time axis is honest.
 * Hovering or focusing a column shows its numbers; the same numbers are in the table below.
 */
function DailyChart({ from, to, days }: { from: string; to: string; days: OutcomeDay[] }) {
  const [active, setActive] = useState<number | null>(null);
  const series = fillDays(from, to, days);
  const max = Math.max(1, ...series.map((day) => day.conversations));
  const top = niceCeiling(max);
  const shown = active === null ? null : series[active];

  return (
    <div className="chart">
      <ul className="chart-legend" aria-label="Leyenda">
        {SERIES.map((item) => (
          <li key={item.key}>
            <span className={`swatch ${item.className}`} aria-hidden="true" />
            {item.label}
          </li>
        ))}
      </ul>
      <div className="chart-plot" onMouseLeave={() => setActive(null)}>
        <div className="chart-axis" aria-hidden="true">
          <span>{formatNumber(top)}</span>
          <span>{formatNumber(top / 2)}</span>
          <span>0</span>
        </div>
        <div
          className="chart-columns"
          style={{ gridTemplateColumns: `repeat(${series.length}, minmax(0, 1fr))`, gap: series.length > 45 ? 1 : 2 }}
        >
          {series.map((day, index) => (
            <div
              key={day.day}
              className={active === index ? 'chart-column is-active' : 'chart-column'}
              tabIndex={0}
              role="img"
              aria-label={describeDay(day)}
              onMouseEnter={() => setActive(index)}
              onFocus={() => setActive(index)}
              onBlur={() => setActive(null)}
            >
              <div className="chart-stack" style={{ height: `${(day.conversations / top) * 100}%` }}>
                {SERIES.map((item) =>
                  day[item.key] > 0 ? (
                    <span key={item.key} className={item.className} style={{ flexGrow: day[item.key] }} />
                  ) : null,
                )}
              </div>
            </div>
          ))}
          {shown && (
            <div className="chart-tooltip" role="status" style={tooltipPlacement(active!, series.length)}>
              <strong>{longDay.format(utc(shown.day))}</strong>
              <span>{formatNumber(shown.conversations)} conversaciones</span>
              {SERIES.map((item) => (
                <span key={item.key} className="chart-tooltip-row">
                  <span className={`swatch ${item.className}`} aria-hidden="true" />
                  {item.label}: {formatNumber(shown[item.key])}
                </span>
              ))}
            </div>
          )}
        </div>
      </div>
      <div className="chart-dates" aria-hidden="true">
        <span>{shortDay.format(utc(from))}</span>
        <span>{shortDay.format(utc(to))}</span>
      </div>
      <details className="chart-table">
        <summary>Ver como tabla</summary>
        <div className="table-wrap">
          <table className="table">
            <thead>
              <tr>
                <th scope="col">Día</th>
                <th scope="col" className="numeric">
                  Conversaciones
                </th>
                {SERIES.map((item) => (
                  <th key={item.key} scope="col" className="numeric">
                    {item.label}
                  </th>
                ))}
              </tr>
            </thead>
            <tbody>
              {days.map((day) => (
                <tr key={day.day}>
                  <td>{shortDay.format(utc(day.day))}</td>
                  <td className="numeric">{formatNumber(day.conversations)}</td>
                  {SERIES.map((item) => (
                    <td key={item.key} className="numeric">
                      {formatNumber(day[item.key])}
                    </td>
                  ))}
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </details>
    </div>
  );
}

function Ratings({ report }: { report: OutcomeReport }) {
  const most = Math.max(1, ...report.ratingCounts);
  return (
    <div className="card">
      <div className="card-header">
        <div>
          <h2 className="card-title">Calificaciones</h2>
          <p>Del 1 al 5, al final de la conversación.</p>
        </div>
      </div>
      {report.ratings === 0 ? (
        <p className="muted small">Ningún cliente calificó en este periodo.</p>
      ) : (
        <ul className="rating-bars">
          {[5, 4, 3, 2, 1].map((score) => {
            const count = report.ratingCounts[score - 1] ?? 0;
            return (
              <li key={score} aria-label={`${score} de 5: ${count}`}>
                <span className="rating-score" aria-hidden="true">
                  {score} ★
                </span>
                <span className="rating-track" aria-hidden="true">
                  {count > 0 && <span style={{ width: `${(count / most) * 100}%` }} />}
                </span>
                <span className="rating-count" aria-hidden="true">
                  {formatNumber(count)}
                </span>
              </li>
            );
          })}
        </ul>
      )}
    </div>
  );
}

function Comments({ report }: { report: OutcomeReport }) {
  return (
    <div className="card">
      <div className="card-header">
        <div>
          <h2 className="card-title">Comentarios recientes</h2>
          <p>Lo que escribieron al calificar.</p>
        </div>
      </div>
      {report.comments.length === 0 ? (
        <p className="muted small">Sin comentarios en este periodo.</p>
      ) : (
        <ul className="comments">
          {report.comments.map((comment) => (
            <li key={comment.conversationId}>
              <p>{comment.comment}</p>
              <span className="muted small">
                {comment.score} de 5 · {formatDateTime(comment.ratedAt)} ·{' '}
                <Link to={`/conversations/${comment.conversationId}`}>Ver conversación</Link>
              </span>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}

/** Every day from {@code from} to {@code to}, with zeros where nothing happened. */
export function fillDays(from: string, to: string, days: OutcomeDay[]): OutcomeDay[] {
  const known = new Map(days.map((day) => [day.day, day]));
  const filled: OutcomeDay[] = [];
  for (let time = utc(from).getTime(); time <= utc(to).getTime(); time += 86_400_000) {
    const day = isoDay(new Date(time));
    filled.push(known.get(day) ?? { day, conversations: 0, resolvedByAssistant: 0, handedOff: 0, unresolved: 0 });
  }
  return filled;
}

/**
 * Centred over its column, except near the edges: there it lines up with the column's outer side so it never leaves
 * the chart.
 */
function tooltipPlacement(index: number, columns: number): CSSProperties {
  const share = (fraction: number) => `${(fraction / columns) * 100}%`;
  if (index < columns / 4) {
    return { left: share(index) };
  }
  if (index >= (columns * 3) / 4) {
    return { right: share(columns - index - 1) };
  }
  return { left: share(index + 0.5), transform: 'translateX(-50%)' };
}

/** The axis top:the next even number at or above the tallest column, so the middle line is a whole number. */
function niceCeiling(max: number): number {
  return max <= 2 ? 2 : max % 2 === 0 ? max : max + 1;
}

function describeDay(day: OutcomeDay): string {
  return `${longDay.format(utc(day.day))}: ${day.conversations} conversaciones, ${day.resolvedByAssistant} resueltas por el asistente, ${day.handedOff} pasaron a una persona, ${day.unresolved} sin resolver`;
}

function utc(day: string): Date {
  return new Date(`${day}T00:00:00Z`);
}

function isoDay(date: Date): string {
  return date.toISOString().slice(0, 10);
}
