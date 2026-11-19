import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState, type FormEvent } from 'react';
import { Link, useSearchParams } from 'react-router';
import {
  followUpsApi,
  type FollowUpStatus,
  type FollowUpTemplate,
  type FollowUpTemplateInput,
} from '../api/follow-ups';
import { usePermissions } from '../auth/auth-context';
import { FollowUpState } from '../components/follow-ups';
import { Card, Empty, ErrorAlert, PageHeader, Pagination, Spinner } from '../components/ui';
import { customerOf } from './conversations-page';

const PAGE_SIZE = 20;

const TABS: { value: FollowUpStatus; label: string }[] = [
  { value: 'SCHEDULED', label: 'Programados' },
  { value: 'SENT', label: 'Enviados' },
  { value: 'FAILED', label: 'No enviados' },
  { value: 'CANCELLED', label: 'Cancelados' },
];

const EMPTY_TEMPLATE: FollowUpTemplateInput = {
  name: '',
  body: '',
  whatsappTemplate: null,
  whatsappLanguage: null,
  whatsappNameParameter: false,
};

/** Every follow-up of the workspace, and the templates they are written from. */
export function FollowUpsPage() {
  const [params, setParams] = useSearchParams();
  const status = TABS.find((tab) => tab.value === params.get('status'))?.value ?? 'SCHEDULED';
  const page = Math.max(0, Number(params.get('page')) || 0);
  const queryClient = useQueryClient();
  const list = useQuery({
    queryKey: ['follow-ups', status, page],
    queryFn: () => followUpsApi.list(status, page, PAGE_SIZE),
    placeholderData: keepPreviousData,
  });
  const cancel = useMutation({
    mutationFn: followUpsApi.cancel,
    onSuccess: () => void queryClient.invalidateQueries({ queryKey: ['follow-ups'] }),
  });
  const go = (next: { status?: FollowUpStatus; page?: number }) => {
    const search = new URLSearchParams();
    search.set('status', next.status ?? status);
    if (next.page) {
      search.set('page', String(next.page));
    }
    setParams(search);
  };

  return (
    <section className="stack" aria-labelledby="follow-ups-title">
      <PageHeader
        id="follow-ups-title"
        eyebrow="Atención"
        title="Seguimientos"
        description="Mensajes que el equipo programa para escribirle a un cliente más tarde, por WhatsApp, correo, Messenger o Instagram. Se programan desde la conversación."
      />
      <div className="tabs" role="tablist" aria-label="Estado de los seguimientos">
        {TABS.map((tab) => (
          <button
            key={tab.value}
            type="button"
            role="tab"
            aria-selected={tab.value === status}
            className={tab.value === status ? 'tab active' : 'tab'}
            onClick={() => go({ status: tab.value, page: 0 })}
          >
            {tab.label}
          </button>
        ))}
      </div>
      <ErrorAlert error={list.error ?? cancel.error} />
      {list.isPending && <Spinner />}
      {list.data && list.data.items.length === 0 && (
        <Empty icon="Clock">
          {status === 'SCHEDULED'
            ? 'No hay seguimientos programados. Prográmalos desde una conversación de WhatsApp, correo, Messenger o Instagram.'
            : 'Nada por aquí.'}
        </Empty>
      )}
      {list.data && list.data.items.length > 0 && (
        <div className="table-wrap">
          <table className="table">
            <thead>
              <tr>
                <th>Cliente</th>
                <th>Mensaje</th>
                <th>Estado</th>
                <th>Programó</th>
                <th>
                  <span className="sr-only">Acciones</span>
                </th>
              </tr>
            </thead>
            <tbody>
              {list.data.items.map((followUp) => (
                <tr key={followUp.id}>
                  <td>
                    <Link to={`/conversations/${followUp.sentConversationId ?? followUp.conversationId}`}>
                      {customerOf({ ...followUp, origin: null }).label}
                    </Link>
                  </td>
                  <td className="follow-up-text">
                    {followUp.templateName && <strong>{followUp.templateName}: </strong>}
                    {followUp.text}
                  </td>
                  <td>
                    <FollowUpState followUp={followUp} />
                  </td>
                  <td>{followUp.createdByName}</td>
                  <td>
                    {followUp.status === 'SCHEDULED' && (
                      <button
                        type="button"
                        className="button button-sm button-ghost"
                        disabled={cancel.isPending}
                        onClick={() => cancel.mutate(followUp.id)}
                      >
                        Cancelar
                      </button>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
      {list.data && (
        <Pagination page={page} size={PAGE_SIZE} total={list.data.total} onChange={(next) => go({ page: next })} />
      )}
      <Templates />
    </section>
  );
}

function Templates() {
  const { canManageFollowUpTemplates } = usePermissions();
  const queryClient = useQueryClient();
  const templates = useQuery({ queryKey: ['follow-up-templates'], queryFn: followUpsApi.templates });
  const [editing, setEditing] = useState<FollowUpTemplate | 'new' | null>(null);
  const remove = useMutation({
    mutationFn: followUpsApi.deleteTemplate,
    onSuccess: () => void queryClient.invalidateQueries({ queryKey: ['follow-up-templates'] }),
  });
  return (
    <Card
      as="section"
      title="Plantillas"
      labelledBy="follow-up-templates-title"
      description="Mensajes que se repiten, para programarlos en dos clics. {nombre} se cambia por el nombre del cliente."
      actions={
        canManageFollowUpTemplates && editing === null ? (
          <button type="button" className="button button-sm" onClick={() => setEditing('new')}>
            Nueva plantilla
          </button>
        ) : undefined
      }
    >
      <ErrorAlert error={templates.error ?? remove.error} />
      {templates.isPending && <Spinner />}
      {editing !== null && (
        <TemplateForm
          key={editing === 'new' ? 'new' : editing.id}
          template={editing === 'new' ? null : editing}
          onDone={() => setEditing(null)}
        />
      )}
      {templates.data && templates.data.length === 0 && editing === null && (
        <p className="muted small">Aún no hay plantillas.</p>
      )}
      <ul className="follow-up-list">
        {templates.data?.map((template) => (
          <li key={template.id}>
            <strong>{template.name}</strong>
            <p className="follow-up-text">{template.body}</p>
            {template.whatsapp && (
              <span className="muted small">
                Después de 24 h en WhatsApp se envía la plantilla aprobada «{template.whatsapp.name}» (
                {template.whatsapp.language})
              </span>
            )}
            {canManageFollowUpTemplates && (
              <span className="row">
                <button type="button" className="button button-sm button-ghost" onClick={() => setEditing(template)}>
                  Editar
                </button>
                <button
                  type="button"
                  className="button button-sm button-ghost"
                  disabled={remove.isPending}
                  onClick={() => {
                    if (window.confirm(`¿Borrar la plantilla «${template.name}»? Los seguimientos ya programados se envían igual.`)) {
                      remove.mutate(template.id);
                    }
                  }}
                >
                  Borrar
                </button>
              </span>
            )}
          </li>
        ))}
      </ul>
    </Card>
  );
}

function TemplateForm({ template, onDone }: { template: FollowUpTemplate | null; onDone: () => void }) {
  const queryClient = useQueryClient();
  const [input, setInput] = useState<FollowUpTemplateInput>(
    template
      ? {
          name: template.name,
          body: template.body,
          whatsappTemplate: template.whatsapp?.name ?? null,
          whatsappLanguage: template.whatsapp?.language ?? null,
          whatsappNameParameter: template.whatsapp?.nameParameter ?? false,
        }
      : EMPTY_TEMPLATE,
  );
  const save = useMutation({
    mutationFn: () =>
      template ? followUpsApi.updateTemplate(template.id, input) : followUpsApi.createTemplate(input),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['follow-up-templates'] });
      onDone();
    },
  });
  const onSubmit = (event: FormEvent) => {
    event.preventDefault();
    save.mutate();
  };
  return (
    <form className="stack" onSubmit={onSubmit} aria-label={template ? 'Editar plantilla' : 'Nueva plantilla'}>
      <label>
        Nombre
        <input
          value={input.name}
          maxLength={80}
          required
          placeholder="Pedido entregado"
          onChange={(event) => setInput({ ...input, name: event.target.value })}
        />
      </label>
      <label>
        Mensaje
        <textarea
          value={input.body}
          maxLength={4000}
          rows={3}
          required
          placeholder="Hola {nombre}, ¿te llegó tu pedido?"
          onChange={(event) => setInput({ ...input, body: event.target.value })}
        />
      </label>
      <fieldset className="fieldset">
        <legend>WhatsApp después de 24 h (opcional)</legend>
        <p className="hint">
          Si el cliente no escribió en las últimas 24 horas, WhatsApp solo entrega una plantilla aprobada en WhatsApp
          Manager. Sin ella, el seguimiento no se envía.
        </p>
        <div className="form-grid">
          <label>
            Nombre de la plantilla aprobada
            <input
              value={input.whatsappTemplate ?? ''}
              placeholder="seguimiento_pedido"
              onChange={(event) => setInput({ ...input, whatsappTemplate: event.target.value || null })}
            />
          </label>
          <label>
            Idioma
            <input
              value={input.whatsappLanguage ?? ''}
              placeholder="es_MX"
              onChange={(event) => setInput({ ...input, whatsappLanguage: event.target.value || null })}
            />
          </label>
        </div>
        <label className="row">
          <input
            type="checkbox"
            checked={input.whatsappNameParameter}
            onChange={(event) => setInput({ ...input, whatsappNameParameter: event.target.checked })}
          />
          Su variable {'{{1}}'} es el nombre del cliente
        </label>
      </fieldset>
      <div className="row">
        <button type="submit" className="button button-sm button-primary" disabled={save.isPending}>
          {save.isPending ? 'Guardando…' : 'Guardar plantilla'}
        </button>
        <button type="button" className="button button-sm button-ghost" onClick={onDone}>
          Cancelar
        </button>
      </div>
      <ErrorAlert error={save.error} />
    </form>
  );
}
