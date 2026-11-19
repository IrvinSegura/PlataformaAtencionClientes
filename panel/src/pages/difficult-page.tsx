import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Fragment, useState } from 'react';
import { Link, useSearchParams } from 'react-router';
import {
  difficultApi,
  type DifficultQuestion,
  type DifficultReason,
  type DifficultResolution,
  type DifficultStatus,
} from '../api/conversations';
import { usePermissions } from '../auth/auth-context';
import { Icons } from '../components/icons';
import { TeachAnswerForm } from '../components/teach-answer';
import { Badge, Empty, ErrorAlert, PageHeader, Pagination, Spinner, type Tone } from '../components/ui';
import { formatDateTime } from '../format';

const PAGE_SIZE = 20;

const TABS: { status: DifficultStatus; label: string; empty: string }[] = [
  {
    status: 'OPEN',
    label: 'Por enseñar',
    empty: 'Nada por ahora. Cuando una respuesta no ayude a un cliente, la pregunta aparecerá aquí.',
  },
  {
    status: 'TAUGHT',
    label: 'Enseñadas',
    empty: 'Aún no enseñas ninguna respuesta.',
  },
  {
    status: 'RESOLVED',
    label: 'Cerradas solas',
    empty: 'Ninguna se ha cerrado sola todavía.',
  },
  {
    status: 'DISMISSED',
    label: 'Descartadas',
    empty: 'No hay preguntas descartadas.',
  },
];

export const DIFFICULT_REASONS: Record<DifficultReason, { label: string; tone: Tone }> = {
  UNHELPFUL: { label: 'Marcó «no me ayudó»', tone: 'danger' },
  UNSATISFIED: { label: 'Dijo que no le sirvió', tone: 'danger' },
  ASKED_AGAIN: { label: 'Volvió a preguntar', tone: 'warning' },
  ESCALATED: { label: 'El rápido no supo', tone: 'info' },
};

const RESOLUTIONS: Record<DifficultResolution, string> = {
  ANSWERED_WELL: 'El preciso la respondió bien varias veces seguidas',
  QUIET: 'Dejó de repetirse',
};

/**
 * Questions the assistant struggled with: the fast model could not answer them, or the customer showed the answer did
 * not help. While open they go straight to the precise model; teaching the answer puts it in the knowledge base, and
 * an untaught one closes by itself once it stops needing the precise model.
 */
export function DifficultPage() {
  const [params, setParams] = useSearchParams();
  const tab = TABS.find((candidate) => candidate.status === params.get('status')) ?? TABS[0]!;
  const page = Math.max(0, Number(params.get('page')) || 0);
  const questions = useQuery({
    queryKey: ['difficult-questions', tab.status, page],
    queryFn: () => difficultApi.list(tab.status, page, PAGE_SIZE),
    placeholderData: keepPreviousData,
  });

  return (
    <section aria-labelledby="difficult-title">
      <PageHeader
        id="difficult-title"
        eyebrow="Asistente"
        title="Preguntas difíciles"
        description="Respuestas que no ayudaron. Mientras sigan aquí, las responde el modelo preciso; enséñale la correcta. Si nadie la enseña, se cierra sola cuando el preciso la responde bien varias veces seguidas o tras un mes sin repetirse."
      />
      <div className="tabs" role="tablist" aria-label="Estado">
        {TABS.map((candidate) => (
          <button
            key={candidate.status}
            type="button"
            role="tab"
            aria-selected={candidate.status === tab.status}
            className={candidate.status === tab.status ? 'tab active' : 'tab'}
            onClick={() => setParams(candidate.status === 'OPEN' ? {} : { status: candidate.status })}
          >
            {candidate.label}
          </button>
        ))}
      </div>
      <ErrorAlert error={questions.error} />
      {questions.isPending && <Spinner />}
      {questions.data && questions.data.items.length === 0 && (
        <Empty icon={tab.status === 'OPEN' ? 'Check' : 'Question'}>{tab.empty}</Empty>
      )}
      {questions.data && questions.data.items.length > 0 && (
        <div className="table-wrap">
          <table className="table">
            <thead>
              <tr>
                <th scope="col">Pregunta</th>
                <th scope="col">Señal</th>
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
              {questions.data.items.map((question) => (
                <DifficultRow key={question.id} question={question} />
              ))}
            </tbody>
          </table>
        </div>
      )}
      {questions.data && (
        <Pagination
          page={page}
          size={PAGE_SIZE}
          total={questions.data.total}
          onChange={(next) =>
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

function DifficultRow({ question }: { question: DifficultQuestion }) {
  const { canTriageGaps, canManageKnowledge } = usePermissions();
  const queryClient = useQueryClient();
  const [teaching, setTeaching] = useState(false);
  const change = useMutation({
    mutationFn: (status: DifficultStatus) => difficultApi.changeStatus(question.id, status),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['difficult-questions'] }),
  });
  const reason = DIFFICULT_REASONS[question.reason];
  return (
    <Fragment>
      <tr>
        <td>
          <div className="gap-question">{question.question}</div>
          {question.lastAnswer && (
            <details className="last-answer">
              <summary>Lo que respondió el asistente</summary>
              <p>{question.lastAnswer}</p>
            </details>
          )}
          <div className="gap-links">
            <Link to={`/knowledge/search?q=${encodeURIComponent(question.question)}`}>Probar búsqueda</Link>
            {question.conversationId && <Link to={`/conversations/${question.conversationId}`}>Ver conversación</Link>}
          </div>
          <ErrorAlert error={change.error} />
        </td>
        <td>
          <Badge tone={reason.tone}>{reason.label}</Badge>
          {question.resolution && <div className="muted small">{RESOLUTIONS[question.resolution]}</div>}
          {question.alertedAt && (
            <div className="muted small">Te avisamos por WhatsApp el {formatDateTime(question.alertedAt)}</div>
          )}
        </td>
        <td className="numeric">
          <span className="occurrences">{question.occurrences}</span>
        </td>
        <td className="muted small">{formatDateTime(question.lastSeenAt)}</td>
        <td className="actions">
          {canTriageGaps &&
            (question.status === 'OPEN' || question.status === 'RESOLVED' ? (
              <>
                {canManageKnowledge && !teaching && (
                  <button type="button" className="button button-sm button-primary" onClick={() => setTeaching(true)}>
                    <Icons.Sparkles size={15} />
                    Enseñar
                  </button>
                )}
                {question.status === 'OPEN' ? (
                  <button
                    type="button"
                    className="button button-sm button-ghost"
                    disabled={change.isPending}
                    onClick={() => change.mutate('DISMISSED')}
                  >
                    Descartar
                  </button>
                ) : (
                  <button
                    type="button"
                    className="button button-sm button-ghost"
                    disabled={change.isPending}
                    onClick={() => change.mutate('OPEN')}
                  >
                    Reabrir
                  </button>
                )}
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
          <td colSpan={5}>
            <TeachAnswerForm
              question={question.question}
              teach={(answer) => difficultApi.teach(question.id, answer)}
              draft={{
                key: question.id,
                load: () => difficultApi.draft(question.id).then((draft) => draft.answer),
              }}
              invalidate={['difficult-questions']}
              onDone={() => setTeaching(false)}
            />
          </td>
        </tr>
      )}
    </Fragment>
  );
}
