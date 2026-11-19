import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState, type FormEvent } from 'react';
import { channelsApi, type EmailChannel, type EmailChannelInput } from '../api/channels';
import { ApiError } from '../api/http-client';
import { Icons } from '../components/icons';
import { Badge, ErrorAlert, PageHeader, Spinner } from '../components/ui';
import { formatDateTime } from '../format';

const QUERY_KEY = ['email-channel'];

/** Gmail's servers; other providers publish theirs in their help pages. */
export const GMAIL_DEFAULTS = { imapHost: 'imap.gmail.com', imapPort: 993, smtpHost: 'smtp.gmail.com', smtpPort: 465 };

export function EmailPage() {
  // Here and not in the form, which starts afresh once the first connection is saved
  const [saved, setSaved] = useState(false);
  const channel = useQuery({
    queryKey: QUERY_KEY,
    queryFn: async () => {
      try {
        return await channelsApi.email();
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
    <section aria-labelledby="email-title">
      <PageHeader
        id="email-title"
        eyebrow="Canales"
        title="Correo"
        description="El asistente responde los correos de tu buzón de soporte en el mismo hilo."
        actions={channel.data && <StatusBadge channel={channel.data} />}
      />
      <ErrorAlert error={channel.error} />
      {channel.isPending && <Spinner />}
      {channel.isSuccess && (
        <>
          {channel.data && <ReadStatus channel={channel.data} />}
          <ConnectionForm key={channel.data?.updatedAt ?? 'new'} channel={channel.data} onSaved={setSaved} />
          {saved && (
            <p className="alert alert-success" role="status">
              Buzón guardado. Se leerá en el próximo minuto; los correos anteriores no se responden.
            </p>
          )}
          {channel.data && <DisconnectCard />}
        </>
      )}
    </section>
  );
}

function StatusBadge({ channel }: { channel: EmailChannel }) {
  if (!channel.enabled) {
    return (
      <Badge tone="neutral" dot>
        Pausado
      </Badge>
    );
  }
  return channel.lastError ? (
    <Badge tone="danger" dot>
      Con errores
    </Badge>
  ) : (
    <Badge tone="success" dot>
      Conectado
    </Badge>
  );
}

function ReadStatus({ channel }: { channel: EmailChannel }) {
  if (channel.lastError) {
    return (
      <div className="alert alert-danger" role="alert">
        <strong>No se pudo leer el buzón</strong>
        {channel.checkedAt && ` (${formatDateTime(channel.checkedAt)})`}: {channel.lastError}
      </div>
    );
  }
  return (
    <p className="muted small">
      {channel.checkedAt ? `Leído por última vez el ${formatDateTime(channel.checkedAt)}.` : 'Aún no se ha leído.'}
    </p>
  );
}

function ConnectionForm({
  channel,
  onSaved,
}: {
  channel: EmailChannel | null;
  onSaved: (saved: boolean) => void;
}) {
  const queryClient = useQueryClient();
  const [input, setInput] = useState<EmailChannelInput>({
    address: channel?.address ?? '',
    displayName: channel?.displayName ?? '',
    imapHost: channel?.imapHost ?? GMAIL_DEFAULTS.imapHost,
    imapPort: channel?.imapPort ?? GMAIL_DEFAULTS.imapPort,
    smtpHost: channel?.smtpHost ?? GMAIL_DEFAULTS.smtpHost,
    smtpPort: channel?.smtpPort ?? GMAIL_DEFAULTS.smtpPort,
    // The address is the username unless the provider says otherwise
    username: channel && channel.username !== channel.address ? channel.username : '',
    password: '',
    enabled: channel?.enabled ?? true,
  });
  const save = useMutation({
    mutationFn: () => channelsApi.saveEmail(input),
    onSuccess: (updated) => {
      queryClient.setQueryData(QUERY_KEY, updated);
      onSaved(true);
    },
  });
  const set = <K extends keyof EmailChannelInput>(key: K, value: EmailChannelInput[K]) => {
    onSaved(false);
    setInput((current) => ({ ...current, [key]: value }));
  };
  const onSubmit = (event: FormEvent) => {
    event.preventDefault();
    save.mutate();
  };

  return (
    <form className="card form" onSubmit={onSubmit} aria-label="Conexión del buzón">
      <div className="card-header">
        <div>
          <h2 className="card-title">{channel ? 'Buzón' : 'Conectar un buzón'}</h2>
          <p>
            Usa IMAP y SMTP con TLS. Con Gmail, crea una <strong>contraseña de aplicación</strong>; la normal no funciona.
          </p>
        </div>
      </div>
      <fieldset className="fieldset">
        <label>
          Dirección de soporte
          <input
            type="email"
            value={input.address}
            onChange={(event) => set('address', event.target.value)}
            required
            maxLength={254}
            placeholder="soporte@mitienda.com"
          />
        </label>
        <label>
          Nombre del remitente
          <input
            value={input.displayName}
            onChange={(event) => set('displayName', event.target.value)}
            maxLength={100}
            placeholder="Soporte Mi Tienda"
          />
        </label>
        <div className="form-row">
          <label>
            Servidor IMAP
            <input
              value={input.imapHost}
              onChange={(event) => set('imapHost', event.target.value)}
              required
              maxLength={253}
              className="mono"
            />
          </label>
          <label>
            Puerto IMAP
            <input
              type="number"
              value={input.imapPort}
              onChange={(event) => set('imapPort', Number(event.target.value))}
              required
              min={1}
              max={65535}
              className="mono"
            />
          </label>
        </div>
        <div className="form-row">
          <label>
            Servidor SMTP
            <input
              value={input.smtpHost}
              onChange={(event) => set('smtpHost', event.target.value)}
              required
              maxLength={253}
              className="mono"
            />
          </label>
          <label>
            Puerto SMTP
            <input
              type="number"
              value={input.smtpPort}
              onChange={(event) => set('smtpPort', Number(event.target.value))}
              required
              min={1}
              max={65535}
              className="mono"
              aria-describedby="smtp-port-hint"
            />
            <span id="smtp-port-hint" className="hint">
              465 usa TLS directo; cualquier otro (587) exige STARTTLS.
            </span>
          </label>
        </div>
        <label>
          Usuario
          <input
            value={input.username}
            onChange={(event) => set('username', event.target.value)}
            maxLength={254}
            autoComplete="off"
            aria-describedby="username-hint"
          />
          <span id="username-hint" className="hint">
            Déjalo vacío para usar la dirección, como en Gmail.
          </span>
        </label>
        <label>
          Contraseña
          <input
            type="password"
            autoComplete="off"
            value={input.password}
            onChange={(event) => set('password', event.target.value)}
            required={!channel}
            maxLength={512}
            className="mono"
            aria-describedby="password-hint"
          />
          <span id="password-hint" className="hint">
            Con Gmail, la contraseña de aplicación de 16 letras.
            {channel && ' Déjala vacía para conservar la guardada.'}
          </span>
        </label>
        <label className="row">
          <input type="checkbox" checked={input.enabled} onChange={(event) => set('enabled', event.target.checked)} />
          Responder los correos que lleguen
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

function DisconnectCard() {
  const queryClient = useQueryClient();
  const disconnect = useMutation({
    mutationFn: channelsApi.disconnectEmail,
    onSuccess: () => queryClient.setQueryData(QUERY_KEY, null),
  });
  return (
    <div className="card danger-zone">
      <p className="muted small">
        Al desconectar se borra la contraseña y el buzón deja de leerse. Las conversaciones anteriores se conservan.
      </p>
      <ErrorAlert error={disconnect.error} />
      <div>
        <button
          type="button"
          className="button button-sm button-danger"
          disabled={disconnect.isPending}
          onClick={() => {
            if (window.confirm('¿Desconectar el buzón? El asistente dejará de responder los correos.')) {
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
