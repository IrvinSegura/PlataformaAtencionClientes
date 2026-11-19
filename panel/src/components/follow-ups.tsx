import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState, type FormEvent } from 'react';
import { type FollowUp, followUpsApi, type FollowUpStatus } from '../api/follow-ups';
import { formatDateTime } from '../format';
import { Badge, ErrorAlert, Spinner, type Tone } from './ui';

export const FOLLOW_UP_STATUSES: Record<FollowUpStatus, { label: string; tone: Tone }> = {
  SCHEDULED: { label: 'Programado', tone: 'info' },
  SENT: { label: 'Enviado', tone: 'success' },
  FAILED: { label: 'No se envió', tone: 'danger' },
  CANCELLED: { label: 'Cancelado', tone: 'neutral' },
};

const FAILURES: Record<string, string> = {
  WINDOW_CLOSED: 'El cliente no escribió en las últimas 24 h y Meta no deja escribirle antes de que lo haga.',
  WINDOW_CLOSED_NO_TEMPLATE:
    'El cliente no escribió en las últimas 24 h: WhatsApp solo entrega una plantilla aprobada y esta no tiene una.',
  WEB_VISITOR: 'Los visitantes del widget son anónimos: no hay cómo escribirles después.',
  NO_TEXT: 'La plantilla solo dice el nombre del cliente y no lo conocemos: no quedaba nada que enviar.',
  CONVERSATION_GONE: 'La conversación del cliente ya no existe.',
  CHANNEL_UNAVAILABLE: 'El canal estaba desconectado o desactivado cuando salió. Revisa la conexión y prográmalo de nuevo.',
  DELIVERY_REFUSED:
    'El canal rechazó el mensaje (por ejemplo, una plantilla que WhatsApp no tiene aprobada o un número inválido).',
  SEND_ERROR: 'Algo falló al enviarlo. Prográmalo de nuevo; si se repite, avísanos.',
};

export function failureText(code: string | null): string {
  return code ? (FAILURES[code] ?? code) : '';
}

const MAX_TEXT = 4000;

/** The value of a datetime-local input for a moment some days from now, at 10:00 local time. */
function daysFromNow(days: number): string {
  const date = new Date();
  date.setDate(date.getDate() + days);
  date.setHours(10, 0, 0, 0);
  return localInput(date);
}

function localInput(date: Date): string {
  const pad = (value: number) => String(value).padStart(2, '0');
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(
    date.getMinutes(),
  )}`;
}

/** A follow-up's status and what happened, in one line. */
export function FollowUpState({ followUp }: { followUp: FollowUp }) {
  const status = FOLLOW_UP_STATUSES[followUp.status];
  return (
    <span className="row follow-up-state">
      <Badge tone={status.tone}>{status.label}</Badge>
      <span className="muted small">
        {followUp.status === 'SCHEDULED'
          ? `para el ${formatDateTime(followUp.dueAt)}`
          : followUp.finishedAt
            ? formatDateTime(followUp.finishedAt)
            : ''}
      </span>
      {followUp.failure && <span className="muted small">{failureText(followUp.failure)}</span>}
    </span>
  );
}

/** The follow-ups of a channel conversation, and a form to schedule another one. */
export function ConversationFollowUps({ conversationId }: { conversationId: string }) {
  const queryClient = useQueryClient();
  const followUps = useQuery({
    queryKey: ['follow-ups', 'conversation', conversationId],
    queryFn: () => followUpsApi.ofConversation(conversationId),
  });
  const cancel = useMutation({
    mutationFn: followUpsApi.cancel,
    onSuccess: () => void queryClient.invalidateQueries({ queryKey: ['follow-ups'] }),
  });
  const scheduled = followUps.data?.filter((followUp) => followUp.status === 'SCHEDULED').length ?? 0;
  return (
    <details className="citations follow-ups">
      <summary>
        Seguimientos{scheduled > 0 ? ` · ${scheduled} ${scheduled === 1 ? 'programado' : 'programados'}` : ''}
      </summary>
      <div className="stack follow-ups-body">
        <ErrorAlert error={followUps.error ?? cancel.error} />
        {followUps.isPending && <Spinner />}
        {followUps.data && followUps.data.length > 0 && (
          <ul className="follow-up-list">
            {followUps.data.map((followUp) => (
              <li key={followUp.id}>
                <p className="follow-up-text">{followUp.text}</p>
                <span className="row">
                  <FollowUpState followUp={followUp} />
                  <span className="muted small">de {followUp.createdByName}</span>
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
                </span>
              </li>
            ))}
          </ul>
        )}
        <ScheduleForm conversationId={conversationId} />
      </div>
    </details>
  );
}

function ScheduleForm({ conversationId }: { conversationId: string }) {
  const queryClient = useQueryClient();
  const templates = useQuery({ queryKey: ['follow-up-templates'], queryFn: followUpsApi.templates });
  const [templateId, setTemplateId] = useState('');
  const [text, setText] = useState('');
  const [dueAt, setDueAt] = useState(() => daysFromNow(3));
  const template = templates.data?.find((known) => known.id === templateId) ?? null;
  const schedule = useMutation({
    mutationFn: () =>
      followUpsApi.schedule(conversationId, {
        templateId: template ? template.id : null,
        text: template ? null : text.trim(),
        dueAt: new Date(dueAt).toISOString(),
      }),
    onSuccess: () => {
      setText('');
      void queryClient.invalidateQueries({ queryKey: ['follow-ups'] });
    },
  });
  const onSubmit = (event: FormEvent) => {
    event.preventDefault();
    schedule.mutate();
  };
  return (
    <form className="stack" onSubmit={onSubmit} aria-label="Programar seguimiento">
      <label>
        Plantilla
        <select value={templateId} onChange={(event) => setTemplateId(event.target.value)}>
          <option value="">Escribir un mensaje</option>
          {templates.data?.map((known) => (
            <option key={known.id} value={known.id}>
              {known.name}
            </option>
          ))}
        </select>
      </label>
      {template ? (
        <p className="follow-up-text muted">{template.body}</p>
      ) : (
        <label>
          Mensaje
          <textarea
            value={text}
            rows={2}
            maxLength={MAX_TEXT}
            placeholder="Hola {nombre}, ¿te llegó tu pedido?"
            onChange={(event) => setText(event.target.value)}
          />
          <span className="hint">{'{nombre}'} se cambia por el nombre del cliente.</span>
        </label>
      )}
      <div className="row follow-up-when">
        <label className="grow">
          Enviar el
          <input type="datetime-local" value={dueAt} required onChange={(event) => setDueAt(event.target.value)} />
        </label>
        <button type="button" className="button button-sm button-ghost" onClick={() => setDueAt(daysFromNow(1))}>
          Mañana
        </button>
        <button type="button" className="button button-sm button-ghost" onClick={() => setDueAt(daysFromNow(7))}>
          En una semana
        </button>
      </div>
      <div>
        <button
          type="submit"
          className="button button-sm button-primary"
          disabled={schedule.isPending || (!template && !text.trim()) || !dueAt}
        >
          {schedule.isPending ? 'Programando…' : 'Programar seguimiento'}
        </button>
      </div>
      <ErrorAlert error={schedule.error} />
    </form>
  );
}
