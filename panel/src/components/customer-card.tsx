import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState, type FormEvent } from 'react';
import { Link } from 'react-router';
import { type Customer, type CustomerInput, customersApi } from '../api/conversations';
import { formatDateTime, formatRelative } from '../format';
import { Badge, ErrorAlert, Spinner } from './ui';

const MAX_NOTES = 4000;
const MAX_NAME = 120;

/**
 * Who the customer of a channel conversation is across all their conversations: the team's name and notes for them,
 * whether the assistant remembers them, and what they talked about before.
 */
export function CustomerCard({ conversationId }: { conversationId: string }) {
  const customer = useQuery({
    queryKey: ['customer', conversationId],
    queryFn: () => customersApi.ofConversation(conversationId),
  });
  if (customer.error) {
    return <ErrorAlert error={customer.error} />;
  }
  if (!customer.data) {
    return <Spinner label="Cargando cliente…" />;
  }
  const data = customer.data;
  const earlier = data.history.filter((conversation) => conversation.id !== conversationId);
  return (
    <details className="citations customer-card">
      <summary>
        Cliente · {data.conversations} {data.conversations === 1 ? 'conversación' : 'conversaciones'} desde{' '}
        {formatDateTime(data.firstSeenAt)}
        {data.notes && ' · con notas'}
      </summary>
      <div className="stack customer-card-body">
        {earlier.length > 0 && (
          <div>
            <h3 className="customer-card-heading">Conversaciones anteriores</h3>
            <ul className="customer-history">
              {earlier.map((conversation) => (
                <li key={conversation.id}>
                  <Link to={`/conversations/${conversation.id}`}>{formatDateTime(conversation.startedAt)}</Link>
                  <span className="muted small">
                    {conversation.questionCount} {conversation.questionCount === 1 ? 'pregunta' : 'preguntas'} · última
                    actividad {formatRelative(conversation.lastActivityAt)}
                  </span>
                  {conversation.status === 'OPEN' && <Badge tone="success">Abierta</Badge>}
                </li>
              ))}
            </ul>
          </div>
        )}
        <CustomerForm key={data.updatedAt ?? 'new'} conversationId={conversationId} customer={data} />
      </div>
    </details>
  );
}

function CustomerForm({ conversationId, customer }: { conversationId: string; customer: Customer }) {
  const queryClient = useQueryClient();
  const [input, setInput] = useState<CustomerInput>({
    displayName: customer.displayName,
    notes: customer.notes,
    remember: customer.remember,
  });
  const save = useMutation({
    mutationFn: () => customersApi.edit(conversationId, input),
    onSuccess: (saved) => {
      queryClient.setQueryData(['customer', conversationId], saved);
      void queryClient.invalidateQueries({ queryKey: ['customers'] });
    },
  });
  const onSubmit = (event: FormEvent) => {
    event.preventDefault();
    save.mutate();
  };
  return (
    <form className="stack" onSubmit={onSubmit} aria-label="Datos del cliente">
      <label>
        Nombre para el equipo
        <input
          value={input.displayName ?? ''}
          maxLength={MAX_NAME}
          placeholder={customer.contactName ?? ''}
          onChange={(event) => setInput({ ...input, displayName: event.target.value || null })}
        />
      </label>
      <label>
        Notas del equipo
        <textarea
          value={input.notes}
          maxLength={MAX_NOTES}
          rows={3}
          placeholder="Por ejemplo: prefiere entregas por la tarde."
          onChange={(event) => setInput({ ...input, notes: event.target.value })}
        />
        <span className="hint">Solo las ve el equipo: no llegan al cliente ni al asistente.</span>
      </label>
      <label className="row">
        <input
          type="checkbox"
          checked={input.remember}
          onChange={(event) => setInput({ ...input, remember: event.target.checked })}
        />
        El asistente recuerda sus conversaciones anteriores
      </label>
      <div className="row">
        <button type="submit" className="button button-sm button-primary" disabled={save.isPending}>
          {save.isPending ? 'Guardando…' : 'Guardar'}
        </button>
        {customer.updatedAt && (
          <span className="muted small">
            Último cambio de {customer.updatedByName ?? 'alguien del equipo'}, {formatRelative(customer.updatedAt)}
          </span>
        )}
      </div>
      <ErrorAlert error={save.error} />
    </form>
  );
}
