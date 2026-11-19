import { useMutation } from '@tanstack/react-query';
import { useState, type FormEvent } from 'react';
import { useSearchParams } from 'react-router';
import { knowledgeApi } from '../api/knowledge';
import { Icons } from '../components/icons';
import { Badge, Empty, ErrorAlert, PageHeader } from '../components/ui';
import { formatPercent } from '../format';

/** Shows the passages the assistant would read for a question, to understand an answer or find missing knowledge. */
export function SearchPage() {
  // ?q= comes from "Probar búsqueda" on an unanswered question
  const [params] = useSearchParams();
  const [question, setQuestion] = useState(() => (params.get('q') ?? '').slice(0, 2000));
  const [limit, setLimit] = useState(5);
  const search = useMutation({ mutationFn: () => knowledgeApi.search(question.trim(), limit) });

  const onSubmit = (event: FormEvent) => {
    event.preventDefault();
    if (question.trim()) {
      search.mutate();
    }
  };

  return (
    <section aria-labelledby="search-title">
      <PageHeader
        id="search-title"
        eyebrow="Asistente"
        title="Probar búsqueda"
        description="Busca como lo haría un cliente y mira qué fragmentos encontraría el asistente."
      />
      <form className="card search-form" onSubmit={onSubmit}>
        <label className="grow">
          Pregunta
          <span className="search-field">
            <Icons.Search size={16} />
            <input
              value={question}
              onChange={(event) => setQuestion(event.target.value)}
              required
              maxLength={2000}
              placeholder="¿Cuánto tarda el envío a Monterrey?"
            />
          </span>
        </label>
        <label>
          Resultados
          <select value={limit} onChange={(event) => setLimit(Number(event.target.value))}>
            {[3, 5, 10, 20].map((value) => (
              <option key={value} value={value}>
                {value}
              </option>
            ))}
          </select>
        </label>
        <button type="submit" className="button button-primary" disabled={search.isPending || !question.trim()}>
          {search.isPending ? 'Buscando…' : 'Buscar'}
        </button>
      </form>
      <ErrorAlert error={search.error} />
      {search.data && search.data.length === 0 && (
        <Empty icon="Search">Ningún fragmento coincide. El asistente respondería que no tiene esa información.</Empty>
      )}
      <ol className="matches">
        {search.data?.map((match) => (
          <li key={match.chunkId} className="card match">
            <div className="match-header">
              <span className="match-rank" aria-hidden="true" />
              <strong>{match.documentTitle}</strong>
              {match.keywordMatch && <Badge tone="success">Coincide por palabras</Badge>}
              {match.similarity !== null && (
                <span className="match-score">
                  Similitud {formatPercent(match.similarity)}
                  <span className="meter" aria-hidden="true">
                    <span style={{ width: `${Math.max(0, Math.min(1, match.similarity)) * 100}%` }} />
                  </span>
                </span>
              )}
            </div>
            <p className="match-content">{match.content}</p>
          </li>
        ))}
      </ol>
    </section>
  );
}
