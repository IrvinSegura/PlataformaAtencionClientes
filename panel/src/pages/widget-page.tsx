import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState, type FormEvent } from 'react';
import { type Workspace, workspaceApi } from '../api/workspaces';
import { usePermissions } from '../auth/auth-context';
import { Icons } from '../components/icons';
import { CopyButton, ErrorAlert, PageHeader, Spinner } from '../components/ui';
import { config } from '../config';

export function WidgetPage() {
  const workspace = useQuery({ queryKey: ['workspace'], queryFn: workspaceApi.get });
  return (
    <section aria-labelledby="widget-title">
      <PageHeader
        id="widget-title"
        eyebrow="Canales"
        title="Widget"
        description="Cómo se presenta el asistente y en qué sitios se puede instalar."
      />
      <ErrorAlert error={workspace.error} />
      {workspace.isPending && <Spinner />}
      {workspace.data && <WidgetSettings key={workspace.data.id} workspace={workspace.data} />}
    </section>
  );
}

function WidgetSettings({ workspace }: { workspace: Workspace }) {
  const { canEditWidget } = usePermissions();
  const queryClient = useQueryClient();
  const [botName, setBotName] = useState(workspace.botName);
  const [welcomeMessage, setWelcomeMessage] = useState(workspace.welcomeMessage);
  const [origins, setOrigins] = useState(workspace.allowedOrigins.join('\n'));
  const save = useMutation({
    mutationFn: () =>
      workspaceApi.changeWidgetSettings({
        botName: botName.trim(),
        welcomeMessage: welcomeMessage.trim(),
        allowedOrigins: origins
          .split(/[\s,]+/)
          .map((origin) => origin.trim())
          .filter(Boolean),
      }),
    onSuccess: (updated) => {
      queryClient.setQueryData(['workspace'], updated);
      // Show what the server stored: origins come back normalized (lowercase, no default port, no duplicates)
      setBotName(updated.botName);
      setWelcomeMessage(updated.welcomeMessage);
      setOrigins(updated.allowedOrigins.join('\n'));
    },
  });

  const onSubmit = (event: FormEvent) => {
    event.preventDefault();
    save.mutate();
  };

  return (
    <div className="widget-layout">
      <div className="widget-main">
        <form className="card form" onSubmit={onSubmit}>
          <div className="card-header">
            <div>
              <h2 className="card-title">Apariencia y sitios permitidos</h2>
              <p>La vista previa de la derecha cambia mientras escribes.</p>
            </div>
          </div>
          <fieldset disabled={!canEditWidget} className="fieldset">
            <label>
              Nombre del asistente
              <input value={botName} onChange={(event) => setBotName(event.target.value)} required maxLength={40} />
            </label>
            <label>
              Mensaje de bienvenida
              <textarea
                value={welcomeMessage}
                onChange={(event) => setWelcomeMessage(event.target.value)}
                required
                maxLength={300}
                rows={3}
              />
            </label>
            <label>
              Sitios donde se puede instalar (uno por línea)
              <textarea
                value={origins}
                onChange={(event) => setOrigins(event.target.value)}
                rows={4}
                placeholder={'https://www.mitienda.com\nhttps://ayuda.mitienda.com'}
                aria-describedby="origins-hint"
                spellCheck={false}
                className="mono"
              />
              <span id="origins-hint" className="hint">
                Solo el origen: protocolo, dominio y puerto, sin rutas. Usa https:// (http:// solo para localhost). Un
                sitio que no esté en la lista no podrá usar el widget. Máximo 20.
              </span>
            </label>
          </fieldset>
          <ErrorAlert error={save.error} />
          {save.isSuccess && (
            <p className="alert alert-success" role="status">
              Cambios guardados. Los sitios los verán en un minuto como máximo.
            </p>
          )}
          {canEditWidget ? (
            <div className="form-footer">
              <button type="submit" className="button button-primary" disabled={save.isPending}>
                <Icons.Check size={16} />
                {save.isPending ? 'Guardando…' : 'Guardar'}
              </button>
            </div>
          ) : (
            <p className="muted small">Solo el dueño o un administrador pueden cambiar esta configuración.</p>
          )}
        </form>
        <InstallCard workspace={workspace} />
      </div>
      <Preview botName={botName.trim() || workspace.botName} welcomeMessage={welcomeMessage.trim() || ' '} />
    </div>
  );
}

/** A static picture of the chat window as visitors will see it, drawn from the values being edited. */
function Preview({ botName, welcomeMessage }: { botName: string; welcomeMessage: string }) {
  return (
    <aside className="preview" aria-label="Vista previa del widget">
      <div className="preview-label">
        <Icons.Sparkles size={14} />
        Vista previa
      </div>
      <div className="preview-window" aria-hidden="true">
        <div className="preview-head">
          <span className="logo-mark">
            <Icons.Bot size={16} />
          </span>
          <div>
            <strong>{botName}</strong>
            <span>En línea · responde al instante</span>
          </div>
        </div>
        <div className="preview-body">
          <div className="preview-msg bot">{welcomeMessage}</div>
          <div className="preview-msg visitor">¿Cuánto tarda el envío?</div>
          <div className="preview-msg bot">El envío nacional tarda de 3 a 5 días hábiles [1].</div>
        </div>
        <div className="preview-input">
          <span>Escribe tu pregunta…</span>
          <i>
            <Icons.Send size={15} />
          </i>
        </div>
      </div>
      <div className="preview-launcher" aria-hidden="true">
        <span>
          <Icons.Chat size={22} />
        </span>
      </div>
    </aside>
  );
}

function InstallCard({ workspace }: { workspace: Workspace }) {
  const { canRotateWidgetKey } = usePermissions();
  const queryClient = useQueryClient();
  const rotate = useMutation({
    mutationFn: workspaceApi.rotateWidgetKey,
    onSuccess: (updated) => queryClient.setQueryData(['workspace'], updated),
  });
  const snippet = `<script src="${config.widgetScriptUrl}" data-widget-key="${workspace.widgetKey}" data-api-url="${config.widgetApiUrl}" async></script>`;
  const demoUrl = config.widgetDemoUrl ? `${config.widgetDemoUrl}/?key=${encodeURIComponent(workspace.widgetKey)}` : null;

  return (
    <div className="card form">
      <div className="card-header">
        <div>
          <h2 className="card-title">Instalación</h2>
          <p>Dos minutos y el asistente aparece en tu sitio.</p>
        </div>
      </div>
      <ol className="steps">
        <li>
          <div>
            <strong>Agrega tu sitio a la lista de permitidos</strong>
            <p className="muted small">
              Arriba, en «Sitios donde se puede instalar».
              {demoUrl && (
                <>
                  {' '}
                  Para la página de prueba, agrega <code>{new URL(config.widgetDemoUrl).origin}</code>.
                </>
              )}
            </p>
          </div>
        </li>
        <li>
          <div>
            <strong>Pega el código</strong>
            <p className="muted small">
              Antes de <code>&lt;/body&gt;</code> en cada página de tu sitio:
            </p>
            <pre className="snippet">
              <code>{snippet}</code>
            </pre>
            <div className="row" style={{ marginTop: 10 }}>
              <CopyButton text={snippet} label="Copiar código" />
              {demoUrl && (
                <a className="button button-sm" href={demoUrl} target="_blank" rel="noopener noreferrer">
                  <Icons.External size={15} />
                  Abrir página de prueba
                </a>
              )}
            </div>
          </div>
        </li>
      </ol>
      <h3 className="subtitle">Clave del widget</h3>
      <div className="key-row">
        <code className="key">{workspace.widgetKey}</code>
        <CopyButton text={workspace.widgetKey} />
      </div>
      <div className="danger-zone">
        <p className="muted small">
          La clave no es secreta: identifica tu espacio de trabajo y solo funciona en los sitios permitidos. Cámbiala si
          alguien la está usando sin permiso; los sitios con la clave anterior dejarán de funcionar.
        </p>
        {canRotateWidgetKey && (
          <button
            type="button"
            className="button button-sm button-danger"
            disabled={rotate.isPending}
            onClick={() => {
              if (window.confirm('¿Cambiar la clave? El widget dejará de funcionar en todos los sitios hasta que actualices el código.')) {
                rotate.mutate();
              }
            }}
          >
            <Icons.Key size={15} />
            Cambiar clave
          </button>
        )}
      </div>
      <ErrorAlert error={rotate.error} />
    </div>
  );
}
