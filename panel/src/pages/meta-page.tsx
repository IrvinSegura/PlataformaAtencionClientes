import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState, type FormEvent } from 'react';
import { channelsApi, type MetaChannel, type MetaChannelInput, type MetaPlatform } from '../api/channels';
import { ApiError } from '../api/http-client';
import { Icons } from '../components/icons';
import { Badge, CopyButton, ErrorAlert, PageHeader, Spinner } from '../components/ui';
import { formatDateTime } from '../format';
import { webhookUrl } from './whatsapp-page';

/** What changes between the Messenger and Instagram pages. */
const PLATFORMS: Record<
  MetaPlatform,
  { title: string; description: string; account: string; accountHint: string; setup: string; subject: string }
> = {
  MESSENGER: {
    title: 'Messenger',
    description:
      'El asistente también responde los mensajes de la página de Facebook de tu negocio.',
    account: 'ID de la página',
    accountHint: 'Está en la página de Facebook → Información → Transparencia de la página, o en Meta Business Suite.',
    setup: 'Messenger → Configuración de la API',
    subject: 'la página',
  },
  INSTAGRAM: {
    title: 'Instagram',
    description:
      'El asistente también responde los mensajes directos de tu cuenta de Instagram.',
    account: 'ID de la cuenta de Instagram',
    accountHint: 'El identificador numérico de la cuenta profesional, en Meta for Developers → Instagram → Configuración de la API.',
    setup: 'Instagram → Configuración de la API',
    subject: 'la cuenta',
  },
};

export function MetaPage({ platform }: { platform: MetaPlatform }) {
  const texts = PLATFORMS[platform];
  const queryKey = ['meta-channel', platform];
  // Here and not in the form, which starts afresh once the first connection is saved
  const [saved, setSaved] = useState(false);
  const channel = useQuery({
    queryKey,
    queryFn: async () => {
      try {
        return await channelsApi.meta(platform);
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
    <section aria-labelledby="meta-title">
      <PageHeader
        id="meta-title"
        eyebrow="Canales"
        title={texts.title}
        description={texts.description}
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
          <ConnectionForm
            key={`${platform}-${channel.data?.updatedAt ?? 'new'}`}
            platform={platform}
            channel={channel.data}
            onSaved={setSaved}
          />
          {saved && (
            <p className="alert alert-success" role="status">
              Conexión guardada.
            </p>
          )}
          {channel.data && <WebhookCard channel={channel.data} />}
          {channel.data && <DisconnectCard platform={platform} />}
        </>
      )}
      <p className="muted small">
        Meta solo permite responder dentro de las 24 horas siguientes al último mensaje del cliente; después, la
        respuesta se descarta hasta que vuelva a escribir.
      </p>
    </section>
  );
}

function ConnectionForm({
  platform,
  channel,
  onSaved,
}: {
  platform: MetaPlatform;
  channel: MetaChannel | null;
  onSaved: (saved: boolean) => void;
}) {
  const texts = PLATFORMS[platform];
  const queryClient = useQueryClient();
  const [input, setInput] = useState<MetaChannelInput>({
    accountId: channel?.accountId ?? '',
    displayName: channel?.displayName ?? '',
    accessToken: '',
    appSecret: '',
    enabled: channel?.enabled ?? true,
  });
  const save = useMutation({
    mutationFn: () => channelsApi.saveMeta(platform, input),
    onSuccess: (updated) => {
      queryClient.setQueryData(['meta-channel', platform], updated);
      onSaved(true);
    },
  });
  const set = <K extends keyof MetaChannelInput>(key: K, value: MetaChannelInput[K]) => {
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
    <form className="card form" onSubmit={onSubmit} aria-label={`Conexión con ${texts.title}`}>
      <div className="card-header">
        <div>
          <h2 className="card-title">{channel ? 'Conexión' : `Conectar ${texts.subject}`}</h2>
          <p>
            Usa una app de Meta con {texts.title}: el token está en {texts.setup} y el secreto, en Configuración → Básica.
          </p>
        </div>
      </div>
      <fieldset className="fieldset">
        <label>
          {texts.account}
          <input
            value={input.accountId}
            onChange={(event) => set('accountId', event.target.value)}
            required
            inputMode="numeric"
            pattern="\d{5,30}"
            className="mono"
            placeholder="123456789012345"
            aria-describedby="account-hint"
          />
          <span id="account-hint" className="hint">
            {texts.accountHint}
          </span>
        </label>
        <label>
          Nombre para mostrar
          <input
            value={input.displayName}
            onChange={(event) => set('displayName', event.target.value)}
            maxLength={100}
            placeholder="Mi Tienda"
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
            maxLength={2048}
            className="mono"
            aria-describedby={secretHint && 'meta-token-hint'}
          />
          {secretHint && (
            <span id="meta-token-hint" className="hint">
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
            maxLength={256}
            className="mono"
            aria-describedby="meta-secret-hint"
          />
          <span id="meta-secret-hint" className="hint">
            Con él se comprueba que cada mensaje viene de Meta. {secretHint}
          </span>
        </label>
        <label className="row">
          <input type="checkbox" checked={input.enabled} onChange={(event) => set('enabled', event.target.checked)} />
          Responder los mensajes que lleguen
        </label>
      </fieldset>
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

function WebhookCard({ channel }: { channel: MetaChannel }) {
  const url = webhookUrl(channel.webhookPath);
  return (
    <div className="card form">
      <div className="card-header">
        <div>
          <h2 className="card-title">Webhook en Meta</h2>
          <p>
            En tu app de Meta, en la configuración de webhooks de {PLATFORMS[channel.platform].title}, pon esta URL de
            devolución de llamada y este token de verificación, y suscribe {PLATFORMS[channel.platform].subject} a los
            campos <code>messages</code> y <code>messaging_postbacks</code>.
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

function DisconnectCard({ platform }: { platform: MetaPlatform }) {
  const queryClient = useQueryClient();
  const disconnect = useMutation({
    mutationFn: () => channelsApi.disconnectMeta(platform),
    onSuccess: () => queryClient.setQueryData(['meta-channel', platform], null),
  });
  const title = PLATFORMS[platform].title;
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
            if (window.confirm(`¿Desconectar ${title}? El asistente dejará de responder ahí.`)) {
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
