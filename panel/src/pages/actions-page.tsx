import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState, type FormEvent } from 'react';
import {
  type ActionParameter,
  actionsApi,
  type AssistantAction,
  type AssistantActionInput,
  type ParameterType,
} from '../api/actions';
import { usePermissions } from '../auth/auth-context';
import { Icons } from '../components/icons';
import { Badge, CopyButton, Empty, ErrorAlert, PageHeader, Spinner } from '../components/ui';
import { formatDateTime } from '../format';

const TYPE_NAMES: Record<ParameterType, string> = { STRING: 'Texto', NUMBER: 'Número', BOOLEAN: 'Sí / no' };

const NEW_ACTION: AssistantActionInput = {
  name: '',
  title: '',
  description: '',
  endpointUrl: 'https://',
  parameters: [],
  requiresConfirmation: false,
  enabled: true,
};

export function ActionsPage() {
  const { canManageActions } = usePermissions();
  const actions = useQuery({ queryKey: ['actions'], queryFn: actionsApi.list });
  // null: no form open; 'new': adding one; otherwise the action being edited
  const [editing, setEditing] = useState<AssistantAction | 'new' | null>(null);

  return (
    <section aria-labelledby="actions-title">
      <PageHeader
        id="actions-title"
        eyebrow="Asistente"
        title="Acciones"
        description="Lo que el asistente puede hacer en tus sistemas, como consultar o cambiar un pedido."
        actions={
          canManageActions &&
          editing === null && (
            <button type="button" className="button button-primary" onClick={() => setEditing('new')}>
              <Icons.Zap size={16} />
              Nueva acción
            </button>
          )
        }
      />
      {editing !== null && (
        <ActionForm
          key={editing === 'new' ? 'new' : editing.id}
          action={editing === 'new' ? null : editing}
          onDone={() => setEditing(null)}
        />
      )}
      <ErrorAlert error={actions.error} />
      {actions.isPending && <Spinner />}
      {actions.data && actions.data.length === 0 && (
        <Empty icon="Zap">
          Sin acciones, el asistente solo responde con tus documentos.
        </Empty>
      )}
      {actions.data && actions.data.length > 0 && (
        <div className="table-wrap">
          <table className="table">
            <thead>
              <tr>
                <th scope="col">Acción</th>
                <th scope="col">Datos que pide</th>
                <th scope="col">Estado</th>
                {canManageActions && (
                  <th scope="col" className="actions">
                    Acciones
                  </th>
                )}
              </tr>
            </thead>
            <tbody>
              {actions.data.map((action) => (
                <ActionRow key={action.id} action={action} onEdit={() => setEditing(action)} />
              ))}
            </tbody>
          </table>
        </div>
      )}
      {canManageActions && <SigningSecretCard />}
    </section>
  );
}

function ActionRow({ action, onEdit }: { action: AssistantAction; onEdit: () => void }) {
  const { canManageActions } = usePermissions();
  const queryClient = useQueryClient();
  const remove = useMutation({
    mutationFn: () => actionsApi.remove(action.id),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['actions'] }),
  });
  return (
    <tr>
      <td>
        <div className="cell-title">{action.title}</div>
        <div className="muted small mono">{action.name}</div>
        <div className="muted small">{action.description}</div>
      </td>
      <td>
        {action.parameters.length === 0
          ? 'Ninguno'
          : action.parameters.map((parameter) => (
              <div key={parameter.name} className="small">
                <span className="mono">{parameter.name}</span> · {TYPE_NAMES[parameter.type]}
                {parameter.required ? '' : ' (opcional)'}
              </div>
            ))}
      </td>
      <td>
        <span className="row">
          <Badge tone={action.enabled ? 'success' : 'neutral'} dot>
            {action.enabled ? 'Activa' : 'Pausada'}
          </Badge>
          {action.requiresConfirmation && <Badge tone="warning">Pide confirmación</Badge>}
        </span>
      </td>
      {canManageActions && (
        <td className="actions">
          <span className="row">
            <button type="button" className="button button-sm" onClick={onEdit}>
              Editar
            </button>
            <button
              type="button"
              className="button button-sm button-danger"
              disabled={remove.isPending}
              onClick={() => {
                if (window.confirm(`¿Eliminar «${action.title}»? El asistente dejará de usarla.`)) {
                  remove.mutate();
                }
              }}
            >
              <Icons.Trash size={15} />
              Eliminar
            </button>
          </span>
          <ErrorAlert error={remove.error} />
        </td>
      )}
    </tr>
  );
}

function ActionForm({ action, onDone }: { action: AssistantAction | null; onDone: () => void }) {
  const queryClient = useQueryClient();
  const [input, setInput] = useState<AssistantActionInput>(() =>
    action
      ? {
          name: action.name,
          title: action.title,
          description: action.description,
          endpointUrl: action.endpointUrl,
          parameters: action.parameters,
          requiresConfirmation: action.requiresConfirmation,
          enabled: action.enabled,
        }
      : NEW_ACTION,
  );
  const save = useMutation({
    mutationFn: () => {
      const payload: AssistantActionInput = {
        ...input,
        parameters: input.parameters.map((parameter) => ({
          ...parameter,
          pattern: parameter.type === 'STRING' && parameter.pattern?.trim() ? parameter.pattern.trim() : null,
        })),
      };
      return action ? actionsApi.update(action.id, payload) : actionsApi.create(payload);
    },
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['actions'] });
      onDone();
    },
  });
  const set = <K extends keyof AssistantActionInput>(key: K, value: AssistantActionInput[K]) =>
    setInput((current) => ({ ...current, [key]: value }));
  const setParameter = (index: number, changes: Partial<ActionParameter>) =>
    set(
      'parameters',
      input.parameters.map((parameter, position) => (position === index ? { ...parameter, ...changes } : parameter)),
    );

  const onSubmit = (event: FormEvent) => {
    event.preventDefault();
    save.mutate();
  };

  return (
    <form className="card form" onSubmit={onSubmit} aria-label={action ? 'Editar acción' : 'Nueva acción'}>
      <div className="card-header">
        <div>
          <h2 className="card-title">{action ? `Editar «${action.title}»` : 'Nueva acción'}</h2>
          <p>El asistente lee la descripción para decidir cuándo usarla; escríbela como se la explicarías a alguien.</p>
        </div>
      </div>
      <fieldset className="fieldset">
        <label>
          Nombre para mostrar
          <input
            value={input.title}
            onChange={(event) => set('title', event.target.value)}
            required
            maxLength={80}
            placeholder="Consultar un pedido"
          />
        </label>
        <label>
          Identificador
          <input
            value={input.name}
            onChange={(event) => set('name', event.target.value)}
            required
            pattern="[a-z][a-z0-9_]{1,63}"
            className="mono"
            placeholder="order_status"
            aria-describedby="action-name-hint"
          />
          <span id="action-name-hint" className="hint">
            Minúsculas, números y guiones bajos. Llega a tu webhook en el campo «action».
          </span>
        </label>
        <label>
          Cuándo usarla
          <textarea
            value={input.description}
            onChange={(event) => set('description', event.target.value)}
            required
            maxLength={500}
            rows={2}
            placeholder="Consulta el estado de un pedido por su número."
          />
        </label>
        <label>
          URL del webhook
          <input
            type="url"
            value={input.endpointUrl}
            onChange={(event) => set('endpointUrl', event.target.value)}
            required
            className="mono"
            aria-describedby="action-url-hint"
          />
          <span id="action-url-hint" className="hint">
            Una dirección https pública. Recibe un POST firmado con el secreto de abajo.
          </span>
        </label>
      </fieldset>
      <h3 className="subtitle">Datos que el asistente debe pedir</h3>
      {input.parameters.length === 0 && <p className="muted small">Ninguno: la acción no necesita datos.</p>}
      {input.parameters.map((parameter, index) => (
        <fieldset key={index} className="fieldset" aria-label={`Dato ${index + 1}`}>
          <div className="row">
            <label className="grow">
              Nombre
              <input
                value={parameter.name}
                onChange={(event) => setParameter(index, { name: event.target.value })}
                required
                pattern="[a-z][a-z0-9_]{0,63}"
                className="mono"
                placeholder="order_number"
              />
            </label>
            <label>
              Tipo
              <select
                value={parameter.type}
                onChange={(event) => setParameter(index, { type: event.target.value as ParameterType })}
              >
                {Object.entries(TYPE_NAMES).map(([value, label]) => (
                  <option key={value} value={value}>
                    {label}
                  </option>
                ))}
              </select>
            </label>
          </div>
          <label>
            Descripción
            <input
              value={parameter.description}
              onChange={(event) => setParameter(index, { description: event.target.value })}
              required
              maxLength={200}
              placeholder="El número de pedido, de 5 a 10 dígitos"
            />
          </label>
          {parameter.type === 'STRING' && (
            <label>
              Formato (expresión regular, opcional)
              <input
                value={parameter.pattern ?? ''}
                onChange={(event) => setParameter(index, { pattern: event.target.value })}
                className="mono"
                placeholder="\d{5,10}"
              />
            </label>
          )}
          <div className="row">
            <label className="row">
              <input
                type="checkbox"
                checked={parameter.required}
                onChange={(event) => setParameter(index, { required: event.target.checked })}
              />
              Obligatorio
            </label>
            <button
              type="button"
              className="button button-sm button-ghost"
              onClick={() =>
                set(
                  'parameters',
                  input.parameters.filter((_, position) => position !== index),
                )
              }
            >
              <Icons.Trash size={15} />
              Quitar
            </button>
          </div>
        </fieldset>
      ))}
      {input.parameters.length < 10 && (
        <div>
          <button
            type="button"
            className="button button-sm"
            onClick={() =>
              set('parameters', [
                ...input.parameters,
                { name: '', type: 'STRING', description: '', required: true, pattern: null },
              ])
            }
          >
            Agregar dato
          </button>
        </div>
      )}
      <fieldset className="fieldset">
        <label className="row">
          <input
            type="checkbox"
            checked={input.requiresConfirmation}
            onChange={(event) => set('requiresConfirmation', event.target.checked)}
          />
          Pedir confirmación al cliente antes de ejecutarla (para acciones que cambian algo)
        </label>
        <label className="row">
          <input type="checkbox" checked={input.enabled} onChange={(event) => set('enabled', event.target.checked)} />
          Activa
        </label>
      </fieldset>
      <ErrorAlert error={save.error} />
      <div className="form-footer">
        <button type="button" className="button" onClick={onDone}>
          Cancelar
        </button>
        <button type="submit" className="button button-primary" disabled={save.isPending}>
          <Icons.Check size={16} />
          {save.isPending ? 'Guardando…' : 'Guardar'}
        </button>
      </div>
    </form>
  );
}

function SigningSecretCard() {
  const { canRotateSigningSecret } = usePermissions();
  const queryClient = useQueryClient();
  const [shown, setShown] = useState(false);
  const secret = useQuery({ queryKey: ['signing-secret'], queryFn: actionsApi.signingSecret, enabled: shown });
  const rotate = useMutation({
    mutationFn: actionsApi.rotateSigningSecret,
    onSuccess: (updated) => {
      queryClient.setQueryData(['signing-secret'], updated);
      setShown(true);
    },
  });
  return (
    <div className="card form">
      <div className="card-header">
        <div>
          <h2 className="card-title">Verificar las llamadas</h2>
          <p>
            Cada llamada lleva la cabecera <code>X-SupportChat-Signature: t=…,v1=…</code>, donde v1 es el HMAC-SHA256
            en hexadecimal de <code>t + "." + cuerpo</code> con este secreto. Rechaza las que no coincidan o tengan
            más de 5 minutos.
          </p>
        </div>
      </div>
      {!shown ? (
        <div>
          <button type="button" className="button button-sm" onClick={() => setShown(true)}>
            <Icons.Key size={15} />
            Mostrar secreto
          </button>
        </div>
      ) : (
        <>
          {secret.isPending && <Spinner />}
          {secret.data && (
            <>
              <div className="key-row">
                <code className="key">{secret.data.value}</code>
                <CopyButton text={secret.data.value} />
              </div>
              <p className="muted small">Creado el {formatDateTime(secret.data.rotatedAt)}.</p>
            </>
          )}
        </>
      )}
      <ErrorAlert error={secret.error ?? rotate.error} />
      {canRotateSigningSecret && (
        <div className="danger-zone">
          <p className="muted small">
            Cámbialo si se filtró. Tus webhooks rechazarán las llamadas hasta que usen el nuevo.
          </p>
          <button
            type="button"
            className="button button-sm button-danger"
            disabled={rotate.isPending}
            onClick={() => {
              if (window.confirm('¿Cambiar el secreto? Tus webhooks deben actualizarse para seguir funcionando.')) {
                rotate.mutate();
              }
            }}
          >
            <Icons.Refresh size={15} />
            Cambiar secreto
          </button>
        </div>
      )}
    </div>
  );
}
