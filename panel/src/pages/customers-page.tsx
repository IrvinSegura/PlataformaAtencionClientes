import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { useState, type FormEvent } from 'react';
import { Link, useSearchParams } from 'react-router';
import { type CustomerListItem, customersApi } from '../api/conversations';
import { Avatar, Empty, ErrorAlert, PageHeader, Pagination, Spinner } from '../components/ui';
import { formatDateTime, formatRelative } from '../format';
import { CHANNEL_NAMES, channelOf, customerOf } from './conversations-page';

const PAGE_SIZE = 20;

/** Customers who write through WhatsApp, email, Messenger or Instagram, recognized across conversations. */
export function CustomersPage() {
  const [params, setParams] = useSearchParams();
  const page = Math.max(0, Number(params.get('page')) || 0);
  const query = params.get('q') ?? '';
  const [text, setText] = useState(query);
  const list = useQuery({
    queryKey: ['customers', query, page],
    queryFn: () => customersApi.list(query, page, PAGE_SIZE),
    placeholderData: keepPreviousData,
  });
  const go = (next: { q?: string; page?: number }) => {
    const search = new URLSearchParams();
    const q = next.q ?? query;
    if (q) {
      search.set('q', q);
    }
    if (next.page) {
      search.set('page', String(next.page));
    }
    setParams(search);
  };
  const onSubmit = (event: FormEvent) => {
    event.preventDefault();
    go({ q: text.trim(), page: 0 });
  };

  return (
    <section className="stack" aria-labelledby="customers-title">
      <PageHeader
        id="customers-title"
        eyebrow="Atención"
        title="Clientes"
        description="Quienes escriben por WhatsApp, correo, Messenger o Instagram, con todas sus conversaciones. Los visitantes del widget son anónimos y no aparecen aquí."
      />
      <form className="row" role="search" onSubmit={onSubmit}>
        <label className="grow">
          <span className="sr-only">Buscar cliente</span>
          <input
            type="search"
            value={text}
            maxLength={120}
            placeholder="Número, correo o nombre"
            onChange={(event) => setText(event.target.value)}
          />
        </label>
        <button type="submit" className="button">
          Buscar
        </button>
      </form>
      <ErrorAlert error={list.error} />
      {list.isPending && <Spinner />}
      {list.data && list.data.items.length === 0 && (
        <Empty icon="Users">
          {query ? 'Ningún cliente coincide con la búsqueda.' : 'Aún no escribe ningún cliente por un canal.'}
        </Empty>
      )}
      {list.data && list.data.items.length > 0 && (
        <div className="table-wrap">
          <table className="table">
            <thead>
              <tr>
                <th>Cliente</th>
                <th>Canal</th>
                <th>Conversaciones</th>
                <th>Desde</th>
                <th>Última actividad</th>
              </tr>
            </thead>
            <tbody>
              {list.data.items.map((customer) => (
                <CustomerRow key={`${customer.channel}:${customer.contactId}`} customer={customer} />
              ))}
            </tbody>
          </table>
        </div>
      )}
      {list.data && (
        <Pagination page={page} size={PAGE_SIZE} total={list.data.total} onChange={(next) => go({ page: next })} />
      )}
    </section>
  );
}

function CustomerRow({ customer }: { customer: CustomerListItem }) {
  // Drawn like the conversations, with the team's name first
  const contact = {
    channel: customer.channel,
    contactId: customer.contactId,
    contactName: customer.displayName ?? customer.contactName,
    origin: null,
  };
  const who = customerOf(contact);
  return (
    <tr>
      <td>
        <Link className="row" to={`/conversations/${customer.latestConversationId}`}>
          <Avatar name={who.label} icon={who.icon} size="sm" />
          {who.label}
        </Link>
      </td>
      <td title={CHANNEL_NAMES[customer.channel]}>{channelOf(contact)}</td>
      <td>{customer.conversations}</td>
      <td>{formatDateTime(customer.firstSeenAt)}</td>
      <td title={formatDateTime(customer.lastSeenAt)}>{formatRelative(customer.lastSeenAt)}</td>
    </tr>
  );
}
