import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useId, useState, type FormEvent } from 'react';
import type { TaughtAnswer } from '../api/knowledge';
import { Icons } from './icons';
import { ErrorAlert } from './ui';

const MAX_ANSWER = 4000;
// A draft costs a model call: opening the form again within this time shows the same one
const DRAFT_KEPT_MS = 30 * 60 * 1000;

/** Where a proposed answer comes from; {@code key} tells drafts of different questions apart. */
export interface DraftSource {
  key: string;
  // Resolves to null when the documents do not hold the answer
  load: () => Promise<string | null>;
}

/**
 * Where the team writes the right answer to a question. It goes into the knowledge base as a small document, so the
 * assistant finds it the next time a customer asks the same. With a draft source, the form starts from an answer
 * written from the documents, so the team only corrects it.
 */
export function TeachAnswerForm({
  question,
  teach,
  invalidate,
  onDone,
  draft,
}: {
  question: string;
  teach: (answer: string) => Promise<TaughtAnswer>;
  // Query keys to refresh once the answer is taught
  invalidate: string[];
  onDone: () => void;
  draft?: DraftSource;
}) {
  const queryClient = useQueryClient();
  const hintId = useId();
  // Null until the team types: the draft fills the field until then, and never overwrites what they wrote
  const [typed, setTyped] = useState<string | null>(null);
  const proposal = useQuery({
    queryKey: ['answer-draft', draft?.key],
    queryFn: () => (draft ? draft.load() : Promise.resolve(null)),
    enabled: draft !== undefined,
    staleTime: DRAFT_KEPT_MS,
    gcTime: DRAFT_KEPT_MS,
    retry: false,
  });
  const answer = typed ?? proposal.data ?? '';
  const save = useMutation({
    mutationFn: () => teach(answer.trim()),
    onSuccess: () => {
      for (const key of [...invalidate, 'documents']) {
        void queryClient.invalidateQueries({ queryKey: [key] });
      }
      onDone();
    },
  });
  const onSubmit = (event: FormEvent) => {
    event.preventDefault();
    if (answer.trim()) {
      save.mutate();
    }
  };
  return (
    <form className="form teach-answer" onSubmit={onSubmit} aria-label={`Respuesta para «${question}»`}>
      <label className="grow">
        Respuesta correcta
        <textarea
          rows={4}
          value={answer}
          maxLength={MAX_ANSWER}
          onChange={(event) => setTyped(event.target.value)}
          aria-describedby={hintId}
          aria-busy={draft !== undefined && typed === null && proposal.isFetching}
          autoFocus
          required
        />
        <span id={hintId} className="muted small">
          {draft !== undefined && typed === null && <DraftStatus proposal={proposal} />}
          Escríbela como se la dirías al cliente. Se guarda en tu base de conocimiento y el asistente la usará desde
          ahora.
        </span>
      </label>
      <ErrorAlert error={save.error} />
      <div className="form-footer">
        <button type="submit" className="button button-primary" disabled={save.isPending || !answer.trim()}>
          <Icons.Sparkles size={16} />
          {save.isPending ? 'Guardando…' : 'Enseñar respuesta'}
        </button>
        <button type="button" className="button button-ghost" disabled={save.isPending} onClick={onDone}>
          Cancelar
        </button>
      </div>
    </form>
  );
}

function DraftStatus({ proposal }: { proposal: { isPending: boolean; isError: boolean; data?: string | null } }) {
  if (proposal.isPending) {
    return <>Preparando un borrador con tus documentos… Puedes escribir mientras tanto. </>;
  }
  if (proposal.isError) {
    return <>No se pudo preparar un borrador; escríbela tú. </>;
  }
  if (proposal.data) {
    return <>Borrador escrito a partir de tus documentos: revísalo y corrígelo antes de enseñarlo. </>;
  }
  return <>Tus documentos no tienen esta respuesta, así que no hay borrador. </>;
}
