import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Fragment, useState, type FormEvent } from 'react';
import { Link, useSearchParams } from 'react-router';
import { type DocumentSuggestion, gapsApi, type GapStatus, knowledgeApi, type KnowledgeGap } from '../api/knowledge';
import { usePermissions } from '../auth/auth-context';
import { Icons } from '../components/icons';
import { TeachAnswerForm } from '../components/teach-answer';
import { Empty, ErrorAlert, PageHeader, Pagination, Spinner } from '../components/ui';
import { formatDateTime } from '../format';

const PAGE_SIZE = 20;

const TABS: { status: GapStatus; label: string; empty: string }[] = [
  {
    status: 'OPEN',
    label: 'Abiertas',
    empty: 'No hay preguntas sin respuesta. Cuando el asistente no sepa algo, aparecerá aquí.',
  },
  {
    status: 'RESOLVED',
    label: 'Resueltas',
    empty: 'Aún no marcas ninguna pregunta como resuelta.',
  },
  {
    status: 'DISMISSED',
    label: 'Descartadas',
    empty: 'No hay preguntas descartadas.',
  },
];

/**
 * Questions the assistant could not answer, grouped by wording and sorted by how often visitors ask them: the list
 * of what to document next.
 */
export function GapsPage() {
  const [params, setParams] = useSearchParams();
  const suggesting = params.get('view') === 'suggestions';
  const tab = TABS.find((candidate) => candidate.status === params.get('status')) ?? TABS[0]!;
  const page = Math.max(0, Number(params.get('page')) || 0);
  const gaps = useGapsQuery(tab.status, page, !suggesting);

  return (
    <section aria-labelledby="gaps-title">
      <PageHeader
        id="gaps-title"
        eyebrow="Asistente"
        title="Preguntas sin respuesta"
        description="Preguntas que tus documentos no responden. Enséñale la respuesta o agrega un documento."
      />
      <div className="tabs" role="tablist" aria-label="Estado">
        {TABS.map((candidate) => (
          <button
            key={candidate.status}
            type="button"
            role="tab"
            aria-selected={!suggesting && candidate.status === tab.status}
            className={!suggesting && candidate.status === tab.status ? 'tab active' : 'tab'}
            onClick={() => setParams(candidate.status === 'OPEN' ? {} : { status: candidate.status })}
          >
            {candidate.label}
          </button>
        ))}
        <button
          type="button"
          role="tab"
          aria-selected={suggesting}
          className={suggesting ? 'tab active' : 'tab'}
          onClick={() => setParams({ view: 'suggestions' })}
        >
          Documentos sugeridos
        </button>
      </div>
      {suggesting ? (
        <Suggestions />
      ) : (
        <GapList
          tab={tab}
          page={page}
          gaps={gaps}
          onPage={(next) =>
            setParams({
              ...(tab.status === 'OPEN' ? {} : { status: tab.status }),
              ...(next ? { page: String(next) } : {}),
            })
          }
        />
      )}
    </section>
  );
}

function useGapsQuery(status: GapStatus, page: number, enabled: boolean) {
  return useQuery({
    queryKey: ['knowledge-gaps', status, page],
    queryFn: () => gapsApi.list(status, page, PAGE_SIZE),
    placeholderData: keepPreviousData,
    enabled,
  });
}

function GapList({
  tab,
  page,
  gaps,
  onPage,
}: {
  tab: (typeof TABS)[number];
  page: number;
  gaps: ReturnType<typeof useGapsQuery>;
  onPage: (page: number) => void;
}) {
  return (
    <>
      <ErrorAlert error={gaps.error} />
      {gaps.isPending && <Spinner />}
      {gaps.data && gaps.data.items.length === 0 && (
        <Empty icon={tab.status === 'OPEN' ? 'Check' : 'Question'}>{tab.empty}</Empty>
      )}
      {gaps.data && gaps.data.items.length > 0 && (
        <div className="table-wrap">
          <table className="table">
            <thead>
              <tr>
                <th scope="col">Pregunta</th>
                <th scope="col" className="numeric">
                  Veces
                </th>
                <th scope="col">Última vez</th>
                <th scope="col" className="actions">
                  Acciones
                </th>
              </tr>
            </thead>
            <tbody>
              {gaps.data.items.map((gap) => (
                <GapRow key={gap.id} gap={gap} />
              ))}
            </tbody>
          </table>
        </div>
      )}
      {gaps.data && <Pagination page={page} size={PAGE_SIZE} total={gaps.data.total} onChange={onPage} />}
    </>
  );
}

/**
 * Open questions grouped by topic, the most asked first: each group is one document to write. Writing it here uploads
 * it to the knowledge base and marks its questions as resolved.
 */
function Suggestions() {
  const suggestions = useQuery({
    queryKey: ['knowledge-gaps', 'suggestions'],
    queryFn: gapsApi.suggestions,
  });
  return (
    <>
      <p className="muted small suggestions-intro">
        Agrupamos las preguntas abiertas que tratan del mismo tema. Un documento por grupo basta para que el asistente
        las responda todas.
      </p>
      <ErrorAlert error={suggestions.error} />
      {suggestions.isPending && <Spinner />}
      {suggestions.data && suggestions.data.length === 0 && (
        <Empty icon="Check">
          No hay temas pendientes. Cuando el asistente no sepa algo, aquí verás qué documento escribir.
        </Empty>
      )}
      {suggestions.data && suggestions.data.length > 0 && (
        <ul className="suggestions">
          {suggestions.data.map((suggestion) => (
            <SuggestionCard key={suggestion.questions[0]!.id} suggestion={suggestion} />
          ))}
        </ul>
      )}
    </>
  );
}

function SuggestionCard({ suggestion }: { suggestion: DocumentSuggestion }) {
  const { canManageKnowledge, canTriageGaps } = usePermissions();
  const [writing, setWriting] = useState(false);
  const count = suggestion.questions.length;
  return (
    <li className="card suggestion">
      <div className="card-header">
        <div>
          <h2 className="card-title">{suggestion.title}</h2>
          <p>
            {count} {count === 1 ? 'pregunta' : 'preguntas'} · {suggestion.occurrences}{' '}
            {suggestion.occurrences === 1 ? 'vez' : 'veces'} · última {formatDateTime(suggestion.lastAskedAt)}
          </p>
        </div>
        {canManageKnowledge && canTriageGaps && !writing && (
          <button type="button" className="button button-sm button-primary" onClick={() => setWriting(true)}>
            <Icons.File size={15} />
            Escribir documento
          </button>
        )}
      </div>
      <ul className="suggestion-questions">
        {suggestion.questions.map((question) => (
          <li key={question.id}>
            <span>{question.question}</span>
            <span className="occurrences" title="Veces que la preguntaron">
              {question.occurrences}
            </span>
          </li>
        ))}
      </ul>
      {writing && <DocumentDraft suggestion={suggestion} onCancel={() => setWriting(false)} />}
    </li>
  );
}

/** A first draft that lists the questions it must answer; the questions also help the search find it. */
export function draftFor(suggestion: DocumentSuggestion): string {
  const questions = suggestion.questions.map((question) => `- ${question.question}`).join('\n');
  return `# ${suggestion.title}\n\nPreguntas que responde este documento:\n${questions}\n\n## Respuesta\n\n`;
}

function DocumentDraft({ suggestion, onCancel }: { suggestion: DocumentSuggestion; onCancel: () => void }) {
  const queryClient = useQueryClient();
  const [title, setTitle] = useState(suggestion.title);
  const draft = draftFor(suggestion);
  const [text, setText] = useState(draft);
  const publish = useMutation({
    mutationFn: async () => {
      const file = new File([text], fileNameFor(title), {
        type: 'text/markdown',
      });
      await knowledgeApi.upload(file, title.trim());
      // Uploaded first: if marking fails, the document is there and the questions can be resolved by hand
      await Promise.all(suggestion.questions.map((question) => gapsApi.changeStatus(question.id, 'RESOLVED')));
    },
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['knowledge-gaps'] });
      void queryClient.invalidateQueries({ queryKey: ['documents'] });
    },
  });
  const unchanged = text.trim() === draft.trim();
  const onSubmit = (event: FormEvent) => {
    event.preventDefault();
    if (!unchanged && title.trim()) {
      publish.mutate();
    }
  };
  return (
    <form className="form suggestion-draft" onSubmit={onSubmit} aria-label={`Documento para «${suggestion.title}»`}>
      <label className="grow">
        Título
        <input value={title} onChange={(event) => setTitle(event.target.value)} maxLength={200} required />
      </label>
      <label className="grow">
        Contenido (Markdown)
        <textarea
          className="mono"
          rows={12}
          value={text}
          onChange={(event) => setText(event.target.value)}
          aria-describedby={`draft-hint-${suggestion.questions[0]!.id}`}
        />
        <span id={`draft-hint-${suggestion.questions[0]!.id}`} className="muted small">
          Escribe la respuesta debajo de «Respuesta». Al subirlo, el asistente lo usará y estas preguntas quedarán
          resueltas.
        </span>
      </label>
      <ErrorAlert error={publish.error} />
      <div className="form-footer">
        <button
          type="submit"
          className="button button-primary"
          disabled={publish.isPending || unchanged || !title.trim()}
        >
          <Icons.Upload size={16} />
          {publish.isPending ? 'Subiendo…' : 'Subir y marcar resueltas'}
        </button>
        <button type="button" className="button button-ghost" disabled={publish.isPending} onClick={onCancel}>
          Cancelar
        </button>
      </div>
    </form>
  );
}

/** "Envíos a Colombia" → "envios-a-colombia.md"; the backend reads the format from the extension. */
export function fileNameFor(title: string): string {
  const slug = title
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '')
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/^-+|-+$/g, '')
    .slice(0, 60);
  return `${slug || 'documento'}.md`;
}

function GapRow({ gap }: { gap: KnowledgeGap }) {
  const { canTriageGaps, canManageKnowledge } = usePermissions();
  const queryClient = useQueryClient();
  const [teaching, setTeaching] = useState(false);
  const change = useMutation({
    mutationFn: (status: GapStatus) => gapsApi.changeStatus(gap.id, status),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['knowledge-gaps'] }),
  });
  return (
    <Fragment>
      <tr>
        <td>
          <div className="gap-question">{gap.question}</div>
          <div className="gap-links">
            <Link to={`/knowledge/search?q=${encodeURIComponent(gap.question)}`}>Probar búsqueda</Link>
            <Link to={`/conversations/${gap.lastConversationId}`}>Ver última conversación</Link>
          </div>
          <ErrorAlert error={change.error} />
        </td>
        <td className="numeric">
          <span className="occurrences">{gap.occurrences}</span>
        </td>
        <td className="muted small">{formatDateTime(gap.lastAskedAt)}</td>
        <td className="actions">
          {canTriageGaps &&
            (gap.status === 'OPEN' ? (
              <>
                {canManageKnowledge && !teaching && (
                  <button type="button" className="button button-sm button-primary" onClick={() => setTeaching(true)}>
                    <Icons.Sparkles size={15} />
                    Enseñar
                  </button>
                )}
                <button
                  type="button"
                  className="button button-sm"
                  disabled={change.isPending}
                  onClick={() => change.mutate('RESOLVED')}
                >
                  <Icons.Check size={15} />
                  Resuelta
                </button>
                <button
                  type="button"
                  className="button button-sm button-ghost"
                  disabled={change.isPending}
                  onClick={() => change.mutate('DISMISSED')}
                >
                  Descartar
                </button>
              </>
            ) : (
              <button
                type="button"
                className="button button-sm"
                disabled={change.isPending}
                onClick={() => change.mutate('OPEN')}
              >
                Reabrir
              </button>
            ))}
        </td>
      </tr>
      {teaching && (
        <tr className="teach-row">
          <td colSpan={4}>
            <TeachAnswerForm
              question={gap.question}
              teach={(answer) => gapsApi.teach(gap.id, answer)}
              invalidate={['knowledge-gaps']}
              onDone={() => setTeaching(false)}
            />
          </td>
        </tr>
      )}
    </Fragment>
  );
}
