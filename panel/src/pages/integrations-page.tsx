import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState, type FormEvent } from 'react';
import { actionsApi, type Integration, type IntegrationInput, type IntegrationProvider } from '../api/actions';
import { Icons } from '../components/icons';
import { Badge, ErrorAlert, PageHeader, Spinner } from '../components/ui';
import { formatDateTime } from '../format';

const QUERY_KEY = ['integrations'];

/** What each provider asks for and where the operator finds it. */
const PROVIDERS: Record<
  IntegrationProvider,
  { name: string; intro: string; account: string; accountPlaceholder: string; token: string; tokenHint: string }
> = {
  SHOPIFY: {
    name: 'Shopify',
    intro:
      'El asistente consulta el estado de los pedidos de tu tienda y los cancela si el cliente lo confirma. Solo muestra un pedido a quien da el correo con que se hizo.',
    account: 'Tienda',
    accountPlaceholder: 'mitienda.myshopify.com',
    token: 'Token de acceso de la API de administración',
    tokenHint:
      'En Shopify: Configuración → Apps y canales de venta → Desarrollar apps → crea una app con los permisos read_orders y write_orders e instálala.',
  },
  ZENDESK: {
    name: 'Zendesk',
    intro:
      'Cuando el cliente necesita al equipo y acepta que le escriban después, el asistente abre un ticket en Zendesk a su nombre, con lo que contó.',
    account: 'Subdominio',
    accountPlaceholder: 'mitienda',
    token: 'Token de la API',
    tokenHint: 'En Zendesk: Centro de administración → Apps e integraciones → API de Zendesk → agrega un token.',
  },
};

export function IntegrationsPage() {
  const integrations = useQuery({ queryKey: QUERY_KEY, queryFn: actionsApi.integrations });

  return (
    <section aria-labelledby="integrations-title">
      <PageHeader
        id="integrations-title"
        eyebrow="Asistente"
        title="Integraciones"
        description="Conecta Shopify o Zendesk con su token y el asistente usa sus acciones, sin webhooks."
      />
      <ErrorAlert error={integrations.error} />
      {integrations.isPending && <Spinner />}
      {integrations.data?.map((integration) => (
        <IntegrationCard key={integration.provider} integration={integration} />
      ))}
    </section>
  );
}

function inputOf(integration: Integration): IntegrationInput {
  return {
    account: integration.account ?? '',
    username: integration.username ?? '',
    token: '',
    enabled: integration.connected ? integration.enabled : true,
  };
}

function IntegrationCard({ integration }: { integration: Integration }) {
  const texts = PROVIDERS[integration.provider];
  const queryClient = useQueryClient();
  const [saved, setSaved] = useState(false);
  const [input, setInput] = useState<IntegrationInput>(() => inputOf(integration));
  // The card stays mounted across saves so its confirmation shows; the form restarts from what the server kept.
  const replace = (updated: Integration) => {
    queryClient.setQueryData<Integration[]>(QUERY_KEY, (current) =>
      current?.map((item) => (item.provider === updated.provider ? updated : item)),
    );
    setInput(inputOf(updated));
  };
  const save = useMutation({
    mutationFn: () => actionsApi.saveIntegration(integration.provider, input),
    onSuccess: (updated) => {
      replace(updated);
      setSaved(true);
    },
  });
  const disconnect = useMutation({
    mutationFn: () => actionsApi.disconnectIntegration(integration.provider),
    onSuccess: () =>
      replace({ ...integration, connected: false, account: null, username: null, enabled: false, updatedAt: null }),
  });
  const set = <K extends keyof IntegrationInput>(key: K, value: IntegrationInput[K]) => {
    setSaved(false);
    setInput((current) => ({ ...current, [key]: value }));
  };
  const onSubmit = (event: FormEvent) => {
    event.preventDefault();
    save.mutate();
  };
  const id = integration.provider.toLowerCase();

  return (
    <form className="card form" onSubmit={onSubmit} aria-labelledby={`${id}-title`}>
      <div className="card-header">
        <div>
          <h2 className="card-title" id={`${id}-title`}>
            {texts.name}
          </h2>
          <p>{texts.intro}</p>
        </div>
        {integration.connected && (
          <Badge tone={integration.enabled ? 'success' : 'neutral'} dot>
            {integration.enabled ? 'Conectada' : 'Pausada'}
          </Badge>
        )}
      </div>
      <ul className="integration-actions" aria-label={`Acciones de ${texts.name}`}>
        {integration.actions.map((action) => (
          <li key={action.name}>
            <Icons.Zap size={15} />
            <span>{action.title}</span>
            {action.requiresConfirmation && <Badge tone="warning">Pide confirmación</Badge>}
          </li>
        ))}
      </ul>
      <fieldset className="fieldset">
        <label>
          {texts.account}
          <input
            value={input.account}
            onChange={(event) => set('account', event.target.value)}
            required
            maxLength={253}
            className="mono"
            placeholder={texts.accountPlaceholder}
          />
        </label>
        {integration.provider === 'ZENDESK' && (
          <label>
            Correo del agente
            <input
              type="email"
              value={input.username}
              onChange={(event) => set('username', event.target.value)}
              required
              maxLength={254}
              placeholder="agente@mitienda.com"
            />
          </label>
        )}
        <label>
          {texts.token}
          <input
            type="password"
            autoComplete="off"
            value={input.token}
            onChange={(event) => set('token', event.target.value)}
            required={!integration.connected}
            maxLength={512}
            className="mono"
            aria-describedby={`${id}-token-hint`}
          />
          <span id={`${id}-token-hint`} className="hint">
            {texts.tokenHint}
            {integration.connected && ' Déjalo vacío para conservar el guardado.'}
          </span>
        </label>
        <label className="row">
          <input type="checkbox" checked={input.enabled} onChange={(event) => set('enabled', event.target.checked)} />
          Dejar que el asistente use estas acciones
        </label>
      </fieldset>
      <ErrorAlert error={save.error ?? disconnect.error} />
      {saved && (
        <p className="alert alert-success" role="status">
          {texts.name} quedó conectada.
        </p>
      )}
      <div className="form-footer">
        <button type="submit" className="button button-primary" disabled={save.isPending}>
          <Icons.Check size={16} />
          {save.isPending ? 'Guardando…' : integration.connected ? 'Guardar cambios' : `Conectar ${texts.name}`}
        </button>
        {integration.connected && (
          <button
            type="button"
            className="button button-sm button-danger"
            disabled={disconnect.isPending}
            onClick={() => {
              if (window.confirm(`¿Desconectar ${texts.name}? El asistente dejará de usar sus acciones.`)) {
                disconnect.mutate();
              }
            }}
          >
            <Icons.X size={15} />
            Desconectar
          </button>
        )}
      </div>
      {integration.updatedAt && (
        <p className="muted small">Actualizada el {formatDateTime(integration.updatedAt)}.</p>
      )}
    </form>
  );
}
