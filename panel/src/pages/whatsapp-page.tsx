import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState, type FormEvent } from 'react';
import { channelsApi, type WhatsAppChannel, type WhatsAppChannelInput } from '../api/channels';
import { ApiError } from '../api/http-client';
import { Icons } from '../components/icons';
import { ModelPicker } from '../components/model-picker';
import { Badge, CopyButton, ErrorAlert, PageHeader, Spinner } from '../components/ui';
import { config } from '../config';
import { formatDateTime } from '../format';

/** Meta calls the webhook on the public API, the same one the widget talks to. */
export function webhookUrl(path: string): string {
  const base = config.widgetApiUrl || config.apiBaseUrl || window.location.origin;
  return `${base.replace(/\/+$/, '')}${path}`;
}

export function WhatsAppPage() {
  // Here and not in the form, which starts afresh once the first connection is saved
  const [saved, setSaved] = useState(false);
  const channel = useQuery({
    queryKey: ['whatsapp-channel'],
    queryFn: async () => {
      try {
        return await channelsApi.whatsApp();
      } catch (error) {
        // Not connected yet
        if (error instanceof ApiError && error.status === 404) {
          return null;
        }
        throw error;
      }
    },
  });

  return (
    <section aria-labelledby="whatsapp-title">
      <PageHeader
        id="whatsapp-title"
        eyebrow="Canales"
        title="WhatsApp"
        description="El asistente también responde en el número de WhatsApp de tu negocio."
        actions={
          channel.data && (
            <Badge tone={channel.data.enabled ? 'success' : 'neutral'} dot>
              {channel.data.enabled ? 'Conectado' : 'Pausado'}
            </Badge>
          )
        }
      />
      <ErrorAlert error={channel.error} />
      {channel.isPending && <Spinner />}
      {channel.isSuccess && (
        <>
          <ConnectionForm key={channel.data?.updatedAt ?? 'new'} channel={channel.data} onSaved={setSaved} />
          {saved && (
            <p className="alert alert-success" role="status">
              Conexión guardada.
            </p>
          )}
          {channel.data && <WebhookCard channel={channel.data} />}
          {channel.data && <DisconnectCard />}
        </>
      )}
    </section>
  );
}

function ConnectionForm({
  channel,
  onSaved,
}: {
  channel: WhatsAppChannel | null;
  onSaved: (saved: boolean) => void;
}) {
  const queryClient = useQueryClient();
  const [input, setInput] = useState<WhatsAppChannelInput>({
    phoneNumberId: channel?.phoneNumberId ?? '',
    displayPhoneNumber: channel?.displayPhoneNumber ?? '',
    accessToken: '',
    appSecret: '',
    enabled: channel?.enabled ?? true,
    model: channel?.model ?? 'AUTOMATIC',
  });
  const save = useMutation({
    mutationFn: () => channelsApi.saveWhatsApp(input),
    onSuccess: (updated) => {
      queryClient.setQueryData(['whatsapp-channel'], updated);
      onSaved(true);
    },
  });
  const set = <K extends keyof WhatsAppChannelInput>(key: K, value: WhatsAppChannelInput[K]) => {
    onSaved(false);
    setInput((current) => ({ ...current, [key]: value }));
  };
  const onSubmit = (event: FormEvent) => {
    event.preventDefault();
    save.mutate();
  };
  // The secrets are never sent back; when connected, blank keeps the saved ones
  const secretHint = channel ? 'Déjalo vacío para conservar el guardado.' : undefined;

  return (
    <form className="card form" onSubmit={onSubmit} aria-label="Conexión con WhatsApp">
      <div className="card-header">
        <div>
          <h2 className="card-title">{channel ? 'Conexión' : 'Conectar un número'}</h2>
          <p>
            Usa un número de la API de WhatsApp Business (Cloud API). Los datos están en Meta for Developers, en tu
            app → WhatsApp → Configuración de la API.
          </p>
        </div>
      </div>
      <fieldset className="fieldset">
        <label>
          Identificador del número
          <input
            value={input.phoneNumberId}
            onChange={(event) => set('phoneNumberId', event.target.value)}
            required
            inputMode="numeric"
            pattern="\d{5,30}"
            className="mono"
            placeholder="123456789012345"
          />
        </label>
        <label>
          Número para mostrar
          <input
            value={input.displayPhoneNumber}
            onChange={(event) => set('displayPhoneNumber', event.target.value)}
            maxLength={30}
            placeholder="+52 55 1234 5678"
          />
        </label>
        <label>
          Token de acceso
          <input
            type="password"
            autoComplete="off"
            value={input.accessToken}
            onChange={(event) => set('accessToken', event.target.value)}
            required={!channel}
            className="mono"
            aria-describedby={secretHint && 'access-token-hint'}
          />
          {secretHint && (
            <span id="access-token-hint" className="hint">
              {secretHint}
            </span>
          )}
        </label>
        <label>
          Secreto de la app
          <input
            type="password"
            autoComplete="off"
            value={input.appSecret}
            onChange={(event) => set('appSecret', event.target.value)}
            required={!channel}
            className="mono"
            aria-describedby="app-secret-hint"
          />
          <span id="app-secret-hint" className="hint">
            Con él se comprueba que cada mensaje viene de Meta. {secretHint}
          </span>
        </label>
        <label className="row">
          <input type="checkbox" checked={input.enabled} onChange={(event) => set('enabled', event.target.checked)} />
          Responder los mensajes que lleguen
        </label>
      </fieldset>
      <div className="field" role="group" aria-labelledby="whatsapp-model-title">
        <span id="whatsapp-model-title">Modelo que responde en este número</span>
        <span className="hint">Puedes probar cada opción antes en el Playground con tus propias preguntas.</span>
        <ModelPicker name="whatsapp-model" value={input.model} onChange={(model) => set('model', model)} />
      </div>
      <ErrorAlert error={save.error} />
      <div className="form-footer">
        <button type="submit" className="button button-primary" disabled={save.isPending}>
          <Icons.Check size={16} />
          {save.isPending ? 'Guardando…' : channel ? 'Guardar cambios' : 'Conectar'}
        </button>
      </div>
    </form>
  );
}

function WebhookCard({ channel }: { channel: WhatsAppChannel }) {
  const url = webhookUrl(channel.webhookPath);
  return (
    <div className="card form">
      <div className="card-header">
        <div>
          <h2 className="card-title">Webhook en Meta</h2>
          <p>
            En tu app de Meta, en WhatsApp → Configuración, pon esta URL de devolución de llamada y este token de
            verificación, y suscríbete al campo <code>messages</code>.
          </p>
        </div>
      </div>
      <div>
        <div className="muted small">URL de devolución de llamada</div>
        <div className="key-row">
          <code className="key">{url}</code>
          <CopyButton text={url} />
        </div>
      </div>
      <div>
        <div className="muted small">Token de verificación</div>
        <div className="key-row">
          <code className="key">{channel.verifyToken}</code>
          <CopyButton text={channel.verifyToken} />
        </div>
      </div>
      <p className="muted small">Actualizado el {formatDateTime(channel.updatedAt)}.</p>
    </div>
  );
}

function DisconnectCard() {
  const queryClient = useQueryClient();
  const disconnect = useMutation({
    mutationFn: channelsApi.disconnectWhatsApp,
    onSuccess: () => queryClient.setQueryData(['whatsapp-channel'], null),
  });
  return (
    <div className="card danger-zone">
      <p className="muted small">
        Al desconectar se borran el token y el secreto; los mensajes que lleguen después se ignoran. Las
        conversaciones anteriores se conservan.
      </p>
      <ErrorAlert error={disconnect.error} />
      <div>
        <button
          type="button"
          className="button button-sm button-danger"
          disabled={disconnect.isPending}
          onClick={() => {
            if (window.confirm('¿Desconectar WhatsApp? El asistente dejará de responder en ese número.')) {
              disconnect.mutate();
            }
          }}
        >
          <Icons.X size={15} />
          Desconectar
        </button>
      </div>
    </div>
  );
}
