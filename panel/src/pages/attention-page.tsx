import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState, type FormEvent } from 'react';
import { Link } from 'react-router';
import { channelsApi } from '../api/channels';
import {
  type AlertDeliveryStatus,
  type AttentionAlert,
  attentionApi,
  type AttentionReason,
  type AttentionSettings,
} from '../api/conversations';
import { ApiError } from '../api/http-client';
import { Icons } from '../components/icons';
import { Badge, Card, Empty, ErrorAlert, PageHeader, Spinner, Stat, type Tone } from '../components/ui';
import { formatDateTime, formatRelative } from '../format';

const MAX_PHONES = 3;

const REASONS: Record<AttentionReason, { label: string; tone: Tone }> = {
  ASKED_FOR_PERSON: { label: 'Pidió una persona', tone: 'info' },
  FRUSTRATED: { label: 'Cliente molesto', tone: 'danger' },
};

const DELIVERIES: Record<AlertDeliveryStatus, { label: string; tone: Tone }> = {
  SENT: { label: 'Entregado', tone: 'success' },
  REJECTED: { label: 'Rechazado', tone: 'warning' },
  FAILED: { label: 'Falló', tone: 'danger' },
  NO_CHANNEL: { label: 'Sin WhatsApp', tone: 'neutral' },
};

/** "+52 55 1234 5678" for display; the API keeps digits only. */
export function formatPhone(digits: string): string {
  return `+${digits}`;
}

export function AttentionPage() {
  // Here and not in the form, which starts afresh with what was saved
  const [saved, setSaved] = useState(false);
  const settings = useQuery({ queryKey: ['attention-settings'], queryFn: attentionApi.settings });
  const alerts = useQuery({ queryKey: ['attention-alerts'], queryFn: attentionApi.alerts, refetchInterval: 30_000 });
  const whatsApp = useQuery({
    queryKey: ['whatsapp-channel'],
    queryFn: async () => {
      try {
        return await channelsApi.whatsApp();
      } catch (error) {
        if (error instanceof ApiError && error.status === 404) {
          return null;
        }
        throw error;
      }
    },
  });

  return (
    <section aria-labelledby="attention-title">
      <PageHeader
        id="attention-title"
        eyebrow="Atención"
        title="Avisos y escalamiento"
        description="Qué pasa cuando un cliente pide una persona o se molesta, y a quién avisar por WhatsApp."
        actions={
          settings.data && (
            <Badge tone={settings.data.alertsEnabled ? 'success' : 'neutral'} dot>
              {settings.data.alertsEnabled ? 'Avisos activos' : 'Avisos apagados'}
            </Badge>
          )
        }
      />
      <ErrorAlert error={settings.error} />
      {alerts.data && <AlertStats alerts={alerts.data} />}
      {settings.isPending && <Spinner />}
      {settings.data && (
        <SettingsForm
          key={JSON.stringify(settings.data)}
          settings={settings.data}
          saved={saved}
          onSaved={setSaved}
          whatsAppConnected={whatsApp.isSuccess ? whatsApp.data !== null && whatsApp.data.enabled : undefined}
        />
      )}
      <RecentAlerts alerts={alerts.data} error={alerts.error} pending={alerts.isPending} />
    </section>
  );
}

function AlertStats({ alerts }: { alerts: AttentionAlert[] }) {
  const asked = alerts.filter((alert) => alert.reason === 'ASKED_FOR_PERSON').length;
  const upset = alerts.length - asked;
  const deliveries = alerts.flatMap((alert) => alert.deliveries);
  const delivered = deliveries.filter((delivery) => delivery.status === 'SENT').length;
  return (
    <ul className="stats" aria-label="Resumen de avisos">
      <Stat label="Clientes que necesitaron a alguien" value={alerts.length} icon="Bell" detail="Últimos 50" />
      <Stat label="Pidieron una persona" value={asked} icon="Headset" />
      <Stat label="Se notaron molestos" value={upset} icon="Alert" />
      <Stat
        label="Avisos entregados"
        value={deliveries.length === 0 ? '—' : `${delivered} de ${deliveries.length}`}
        icon="Phone"
      />
    </ul>
  );
}

function SettingsForm({
  settings,
  saved,
  onSaved: setSaved,
  whatsAppConnected,
}: {
  settings: AttentionSettings;
  saved: boolean;
  onSaved: (saved: boolean) => void;
  /** Undefined while unknown. */
  whatsAppConnected: boolean | undefined;
}) {
  const queryClient = useQueryClient();
  const [input, setInput] = useState<AttentionSettings>(settings);
  const [phones, setPhones] = useState<string[]>(
    settings.alertPhones.length > 0 ? settings.alertPhones.map(formatPhone) : [''],
  );
  const save = useMutation({
    mutationFn: () =>
      attentionApi.save({
        ...input,
        alertPhones: phones.map((phone) => phone.trim()).filter(Boolean),
        template: input.template?.trim() || null,
        templateLanguage: input.template?.trim() ? input.templateLanguage?.trim() || null : null,
      }),
    onSuccess: (updated) => {
      queryClient.setQueryData(['attention-settings'], updated);
      setSaved(true);
    },
  });
  const set = <K extends keyof AttentionSettings>(key: K, value: AttentionSettings[K]) => {
    setSaved(false);
    setInput((current) => ({ ...current, [key]: value }));
  };
  const setPhone = (index: number, value: string) => {
    setSaved(false);
    setPhones((current) => current.map((phone, i) => (i === index ? value : phone)));
  };
  const onSubmit = (event: FormEvent) => {
    event.preventDefault();
    save.mutate();
  };

  return (
    <form className="stack attention-form" onSubmit={onSubmit} aria-label="Avisos y escalamiento">
      <Card
        as="section"
        className="form"
        icon="Alert"
        title="Clientes molestos"
        labelledBy="frustration-title"
        description="Detecta quejas, insultos, mayúsculas y “!!!” en cuatro idiomas, sin gastar en el modelo."
      >
        <label className="row">
          <input
            type="checkbox"
            checked={input.handOffFrustrated}
            onChange={(event) => set('handOffFrustrated', event.target.checked)}
          />
          Pasar al equipo a los clientes que se notan molestos
        </label>
        <p className="hint">
          El cliente recibe el aviso de que lo atenderá una persona y la conversación aparece en <strong>En espera</strong>{' '}
          marcada como <Badge tone="danger">Cliente molesto</Badge>. Si lo desactivas, el asistente sigue respondiendo y
          solo se te avisa.
        </p>
      </Card>

      <Card
        as="section"
        className="form"
        icon="Bell"
        title="Avisos por WhatsApp"
        labelledBy="alerts-title"
        description="Te llega quién es el cliente, qué escribió y el enlace a la conversación, una vez por motivo."
      >
        {whatsAppConnected === false && (
          <p className="alert alert-warning" role="status">
            Los avisos salen del número de WhatsApp del negocio, y no hay uno conectado y activo.{' '}
            <Link to="/channels/whatsapp">Conectar WhatsApp</Link>
          </p>
        )}
        <label className="row">
          <input
            type="checkbox"
            checked={input.alertsEnabled}
            onChange={(event) => set('alertsEnabled', event.target.checked)}
          />
          Avisarme por WhatsApp
        </label>
        <fieldset className="fieldset" disabled={!input.alertsEnabled}>
          <div className="field" role="group" aria-labelledby="alert-phones-title">
            <span id="alert-phones-title">Números que reciben el aviso</span>
            <span className="hint">Con código de país, por ejemplo +52 55 1234 5678. Hasta tres.</span>
            {phones.map((phone, index) => (
              <div className="key-row" key={index}>
                <input
                  type="tel"
                  inputMode="tel"
                  value={phone}
                  onChange={(event) => setPhone(index, event.target.value)}
                  placeholder="+52 55 1234 5678"
                  aria-label={`Número ${index + 1}`}
                  required={input.alertsEnabled && index === 0}
                  maxLength={32}
                />
                {phones.length > 1 && (
                  <button
                    type="button"
                    className="button button-sm button-ghost"
                    aria-label={`Quitar el número ${index + 1}`}
                    onClick={() => {
                      setSaved(false);
                      setPhones((current) => current.filter((_, i) => i !== index));
                    }}
                  >
                    <Icons.X size={15} />
                  </button>
                )}
              </div>
            ))}
            {phones.length < MAX_PHONES && (
              <div>
                <button
                  type="button"
                  className="button button-sm"
                  onClick={() => {
                    setSaved(false);
                    setPhones((current) => [...current, '']);
                  }}
                >
                  <Icons.Plus size={15} />
                  Agregar número
                </button>
              </div>
            )}
          </div>
          <div className="field" role="group" aria-labelledby="alert-reasons-title">
            <span id="alert-reasons-title">Avisarme cuando</span>
            <label className="row">
              <input
                type="checkbox"
                checked={input.alertOnRequest}
                onChange={(event) => set('alertOnRequest', event.target.checked)}
              />
              Un cliente pide hablar con una persona
            </label>
            <label className="row">
              <input
                type="checkbox"
                checked={input.alertOnFrustration}
                onChange={(event) => set('alertOnFrustration', event.target.checked)}
              />
              Un cliente se nota molesto
            </label>
            <label className="row">
              <input
                type="checkbox"
                checked={input.alertOnDifficult}
                onChange={(event) => set('alertOnDifficult', event.target.checked)}
              />
              Una pregunta difícil se repite 5 veces sin que le enseñes la respuesta
            </label>
            <label className="row">
              <input
                type="checkbox"
                checked={input.alertOnUsage}
                onChange={(event) => set('alertOnUsage', event.target.checked)}
              />
              Tu plan llega al 80 % y al 95 % de lo que incluye en el mes
            </label>
          </div>
          <details className="disclosure" open={Boolean(settings.template)}>
            <summary>Plantilla aprobada (opcional)</summary>
            <p className="hint">
              WhatsApp solo entrega un mensaje libre a un número que le escribió al negocio en las últimas 24 horas.
              Para recibir los avisos siempre, crea en WhatsApp Manager una plantilla de categoría{' '}
              <strong>Utilidad</strong> con tres variables, por ejemplo:{' '}
              <code>Cliente que necesita atención: {'{{1}}'} {'{{2}}'}. Atiéndelo aquí: {'{{3}}'}</code>
            </p>
            <div className="form-grid">
              <label>
                Nombre de la plantilla
                <input
                  value={input.template ?? ''}
                  onChange={(event) => set('template', event.target.value)}
                  className="mono"
                  placeholder="alerta_cliente"
                  pattern="[a-z0-9_]+"
                  maxLength={512}
                />
              </label>
              <label>
                Idioma
                <input
                  value={input.templateLanguage ?? ''}
                  onChange={(event) => set('templateLanguage', event.target.value)}
                  className="mono"
                  placeholder="es_MX"
                  required={Boolean(input.template?.trim())}
                  maxLength={16}
                />
              </label>
            </div>
          </details>
        </fieldset>
      </Card>

      <ErrorAlert error={save.error} />
      {saved && (
        <p className="alert alert-success" role="status">
          Cambios guardados.
        </p>
      )}
      <div className="form-footer">
        <button type="submit" className="button button-primary" disabled={save.isPending}>
          <Icons.Check size={16} />
          {save.isPending ? 'Guardando…' : 'Guardar cambios'}
        </button>
      </div>
    </form>
  );
}

function customerOf(alert: AttentionAlert): string {
  if (alert.channel === 'WEB') {
    return 'Visitante del sitio web';
  }
  if (alert.contactName) {
    return alert.contactName;
  }
  return alert.channel === 'WHATSAPP' && alert.contactId ? formatPhone(alert.contactId) : 'Cliente';
}

function RecentAlerts({
  alerts,
  error,
  pending,
}: {
  alerts: AttentionAlert[] | undefined;
  error: unknown;
  pending: boolean;
}) {
  return (
    <Card
      as="section"
      title="Avisos recientes"
      icon="Clock"
      labelledBy="recent-alerts-title"
      description="Los clientes que pidieron una persona o se notaron molestos, y si el aviso llegó."
    >
      <ErrorAlert error={error} />
      {pending && <Spinner />}
      {alerts && alerts.length === 0 && (
        <Empty icon="Bell">Todavía ningún cliente ha pedido una persona ni se ha notado molesto.</Empty>
      )}
      {alerts && alerts.length > 0 && (
        <div className="table-wrap">
          <table className="table">
            <thead>
              <tr>
                <th scope="col">Cuándo</th>
                <th scope="col">Cliente</th>
                <th scope="col">Motivo</th>
                <th scope="col">Lo que escribió</th>
                <th scope="col">Aviso</th>
                <th scope="col">
                  <span className="sr-only">Conversación</span>
                </th>
              </tr>
            </thead>
            <tbody>
              {alerts.map((alert) => (
                <tr key={alert.id}>
                  <td>
                    <time dateTime={alert.createdAt} title={formatDateTime(alert.createdAt)}>
                      {formatRelative(alert.createdAt)}
                    </time>
                  </td>
                  <td>{customerOf(alert)}</td>
                  <td>
                    <Badge tone={REASONS[alert.reason].tone}>{REASONS[alert.reason].label}</Badge>
                  </td>
                  <td className="muted small">{alert.excerpt ?? '—'}</td>
                  <td>
                    {alert.deliveries.length === 0 ? (
                      <span className="muted small">Sin aviso</span>
                    ) : (
                      <ul className="delivery-list">
                        {alert.deliveries.map((delivery) => (
                          <li key={delivery.recipient}>
                            <Badge tone={DELIVERIES[delivery.status].tone} dot>
                              {DELIVERIES[delivery.status].label}
                            </Badge>{' '}
                            <span className="mono small">{formatPhone(delivery.recipient)}</span>
                            {delivery.detail && <span className="hint">{delivery.detail}</span>}
                          </li>
                        ))}
                      </ul>
                    )}
                  </td>
                  <td>
                    <Link className="button button-sm" to={`/conversations/${alert.conversationId}`}>
                      Abrir
                    </Link>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </Card>
  );
}
