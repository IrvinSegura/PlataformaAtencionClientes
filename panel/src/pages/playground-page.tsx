import { useQueryClient } from '@tanstack/react-query';
import { useEffect, useRef, useState, type FormEvent, type KeyboardEvent } from 'react';
import { Link } from 'react-router';
import {
  type AnswerOutcome,
  type ModelPreference,
  type ModelTier,
  playgroundApi,
  type PlaygroundResult,
  type PlaygroundTurn,
} from '../api/conversations';
import { errorMessage } from '../api/error-messages';
import { Icons } from '../components/icons';
import { MODEL_CHOICES } from '../components/model-picker';
import { Avatar, Badge, Empty, PageHeader, type Tone } from '../components/ui';

export interface Turn {
  id: number;
  fromCustomer: boolean;
  text: string;
  result?: PlaygroundResult;
  streaming?: boolean;
  error?: string;
}

const SUGGESTIONS = ['¿Cuánto tarda el envío?', '¿Cuál es su política de devoluciones?', '¿En qué horario atienden?'];

const OUTCOMES: Record<AnswerOutcome, { label: string; tone: Tone }> = {
  ANSWERED: { label: 'Respondida', tone: 'success' },
  NO_ANSWER: { label: 'Sin respuesta en la base', tone: 'warning' },
  FAILED: { label: 'Falló', tone: 'danger' },
  SMALL_TALK: { label: 'Cortesía (gratis)', tone: 'neutral' },
};

const TIERS: Record<ModelTier, string> = { SIMPLE: 'Rápido', COMPLEX: 'Preciso' };

/** Why the router chose a tier, as the backend's ModelRouter and ModelSelection name it. */
export const ROUTING_REASONS: Record<string, string> = {
  simple: 'Pregunta directa',
  'long-question': 'Pregunta larga',
  'several-questions': 'Varias preguntas a la vez',
  reasoning: 'Pide comparar, explicar o razonar',
  'several-documents': 'Combina varios documentos',
  'escalated-no-answer': 'Escalada: el rápido no encontró respuesta',
  'routing-disabled': 'Enrutamiento desactivado',
  'chosen-fast': 'Elegido por ti: Rápido',
  'chosen-precise': 'Elegido por ti: Preciso',
  reused: 'Respuesta repetida, reutilizada sin costo',
  hypothetical: 'Plantea un caso hipotético',
  comparison: 'Compara opciones',
  'learned-difficult': 'Aprendida: antes no ayudó con el rápido',
  'customer-unsatisfied': 'El cliente dijo que la respuesta no le sirvió',
};

/** Most earlier turns the API accepts; keep in line with PlaygroundController.MAX_HISTORY. */
export const MAX_HISTORY = 20;

/**
 * The earlier turns sent along with a question: only finished ones, and only the latest ones, so a long test
 * conversation is never refused for its length.
 */
export function playgroundHistory(turns: readonly Turn[]): PlaygroundTurn[] {
  return turns
    .filter((turn) => !turn.error && !turn.streaming && turn.text.trim() !== '')
    .slice(-MAX_HISTORY)
    .map((turn) => ({ fromCustomer: turn.fromCustomer, text: turn.text }));
}

const usd = new Intl.NumberFormat('es', { style: 'currency', currency: 'USD', minimumFractionDigits: 4, maximumFractionDigits: 4 });
const integer = new Intl.NumberFormat('es');
const percent = new Intl.NumberFormat('es', { style: 'percent', maximumFractionDigits: 0 });

export function PlaygroundPage() {
  const queryClient = useQueryClient();
  const [turns, setTurns] = useState<Turn[]>([]);
  const [question, setQuestion] = useState('');
  const [model, setModel] = useState<ModelPreference>('AUTOMATIC');
  const [selected, setSelected] = useState<number | null>(null);
  const nextId = useRef(1);
  const abort = useRef<AbortController | null>(null);
  const transcript = useRef<HTMLOListElement>(null);
  const busy = turns.some((turn) => turn.streaming);

  useEffect(() => () => abort.current?.abort(), []);
  useEffect(() => {
    const list = transcript.current;
    if (list && typeof list.scrollTo === 'function') {
      list.scrollTo({ top: list.scrollHeight });
    }
  }, [turns]);

  const ask = async (text: string) => {
    const asked = text.trim();
    if (!asked || busy) {
      return;
    }
    const history = playgroundHistory(turns);
    const questionId = nextId.current++;
    const answerId = nextId.current++;
    setTurns((current) => [
      ...current,
      { id: questionId, fromCustomer: true, text: asked },
      { id: answerId, fromCustomer: false, text: '', streaming: true },
    ]);
    setSelected(answerId);
    setQuestion('');
    const change = (patch: Partial<Turn> | ((turn: Turn) => Partial<Turn>)) =>
      setTurns((current) =>
        current.map((turn) =>
          turn.id === answerId ? { ...turn, ...(typeof patch === 'function' ? patch(turn) : patch) } : turn,
        ),
      );
    const controller = new AbortController();
    abort.current = controller;
    try {
      const result = await playgroundApi.answer(
        { question: asked, history, model },
        (delta) => change((turn) => ({ text: turn.text + delta })),
        controller.signal,
      );
      change({ text: result.text, result, streaming: false });
      // Each answer counts against the plan, so the usage pages must not show a stale number
      void queryClient.invalidateQueries({ queryKey: ['usage'] });
      void queryClient.invalidateQueries({ queryKey: ['billing'] });
    } catch (error) {
      if (controller.signal.aborted) {
        return;
      }
      change({ streaming: false, error: errorMessage(error) });
    }
  };

  const reset = () => {
    abort.current?.abort();
    setTurns([]);
    setSelected(null);
  };

  const onSubmit = (event: FormEvent) => {
    event.preventDefault();
    void ask(question);
  };
  const onKeyDown = (event: KeyboardEvent<HTMLTextAreaElement>) => {
    if (event.key === 'Enter' && !event.shiftKey) {
      event.preventDefault();
      void ask(question);
    }
  };

  const inspected = turns.find((turn) => turn.id === selected && !turn.fromCustomer) ?? null;

  return (
    <section aria-labelledby="playground-title" className="playground-page">
      <PageHeader
        id="playground-title"
        eyebrow="Asistente"
        title="Playground"
        description="Prueba el asistente como un cliente y mira fragmentos, acciones, tokens y costo. Nada se guarda."
        actions={
          <button type="button" className="button" onClick={reset} disabled={turns.length === 0}>
            <Icons.Eraser size={16} />
            Nueva prueba
          </button>
        }
      />
      <div className="playground">
        <div className="playground-chat">
          <div className="playground-toolbar">
            <label className="row small">
              <Icons.Cpu size={16} />
              <span className="muted">Modelo</span>
              <select
                value={model}
                onChange={(event) => setModel(event.target.value as ModelPreference)}
                aria-label="Modelo"
                disabled={busy}
              >
                {(Object.keys(MODEL_CHOICES) as ModelPreference[]).map((choice) => (
                  <option key={choice} value={choice}>
                    {MODEL_CHOICES[choice].label}
                  </option>
                ))}
              </select>
            </label>
            <span className="hint grow">{MODEL_CHOICES[model].text}</span>
          </div>
          {turns.length === 0 ? (
            <div className="transcript">
              <Empty icon="Flask">
                Pregunta como un cliente. Cuenta en tu consumo.
              </Empty>
              <div className="suggested">
                {SUGGESTIONS.map((suggestion) => (
                  <button key={suggestion} type="button" className="button button-sm" onClick={() => void ask(suggestion)}>
                    {suggestion}
                  </button>
                ))}
              </div>
            </div>
          ) : (
            <ol className="transcript" ref={transcript} aria-live="polite" aria-label="Conversación de prueba">
              {turns.map((turn) => (
                <PlaygroundMessage
                  key={turn.id}
                  turn={turn}
                  selected={turn.id === selected}
                  onSelect={() => setSelected(turn.id)}
                />
              ))}
            </ol>
          )}
          <form className="reply-box" onSubmit={onSubmit}>
            <label>
              Pregunta del cliente
              <textarea
                value={question}
                onChange={(event) => setQuestion(event.target.value)}
                onKeyDown={onKeyDown}
                maxLength={2000}
                rows={1}
                placeholder="¿Cuánto tarda el envío a Monterrey?"
              />
            </label>
            <button type="submit" className="button button-primary" disabled={busy || question.trim() === ''}>
              <Icons.Send size={16} />
              {busy ? 'Respondiendo…' : 'Preguntar'}
            </button>
          </form>
        </div>
        <Inspector turn={inspected} />
      </div>
    </section>
  );
}

function PlaygroundMessage({ turn, selected, onSelect }: { turn: Turn; selected: boolean; onSelect: () => void }) {
  if (turn.fromCustomer) {
    return (
      <li className="message message-visitor">
        <Avatar name="Cliente" size="sm" icon="Users" />
        <div className="bubble">
          <div className="bubble-meta">
            <strong>Cliente de prueba</strong>
          </div>
          <p className="bubble-text">{turn.text}</p>
        </div>
      </li>
    );
  }
  const outcome = turn.result ? OUTCOMES[turn.result.outcome] : null;
  return (
    <li className={selected ? 'message message-assistant is-selected' : 'message message-assistant'}>
      <Avatar name="Asistente" size="sm" icon="Bot" />
      <div className="bubble">
        <button type="button" className="bubble-button" onClick={onSelect} aria-pressed={selected} aria-label="Ver en el inspector">
          <div className="bubble-meta">
            <strong>Asistente</strong>
            {outcome && (
              <Badge tone={outcome.tone} dot>
                {outcome.label}
              </Badge>
            )}
            {turn.result?.model.tier && <Badge>{TIERS[turn.result.model.tier]}</Badge>}
          </div>
          {turn.text ? (
            <p className="bubble-text">{turn.text}</p>
          ) : turn.streaming ? (
            <span className="typing" aria-label="Escribiendo">
              <span />
              <span />
              <span />
            </span>
          ) : null}
        </button>
        {turn.error && (
          <p className="alert alert-danger" role="alert">
            {turn.error}
          </p>
        )}
      </div>
    </li>
  );
}

function Inspector({ turn }: { turn: Turn | null }) {
  if (!turn) {
    return (
      <aside className="inspector" aria-label="Inspector">
        <div className="card">
          <div className="inspector-head">
            <h2>
              <Icons.Sliders size={16} />
              Inspector
            </h2>
          </div>
          <p className="muted small">
            Elige una respuesta para ver fragmentos, modelo, tokens y costo.
          </p>
        </div>
      </aside>
    );
  }
  const result = turn.result;
  if (!result) {
    return (
      <aside className="inspector" aria-label="Inspector">
        <div className="card">
          <div className="inspector-head">
            <h2>
              <Icons.Sliders size={16} />
              Inspector
            </h2>
          </div>
          <p className="muted small">{turn.error ? 'Esta respuesta falló; no hay nada que inspeccionar.' : 'Generando la respuesta…'}</p>
        </div>
      </aside>
    );
  }
  const { model } = result;
  const used = result.sources.filter((source) => source.usedByModel).length;
  return (
    <aside className="inspector" aria-label="Inspector">
      <div className="card">
        <div className="inspector-head">
          <h2>
            <Icons.Cpu size={16} />
            Modelo y costo
          </h2>
          <Badge tone={OUTCOMES[result.outcome].tone} dot>
            {OUTCOMES[result.outcome].label}
          </Badge>
        </div>
        <ul className="inspector-metrics">
          <li>
            <span>Modelo</span>
            <strong className="mono">{model.model ?? 'Ninguno'}</strong>
          </li>
          <li>
            <span>Nivel</span>
            <strong>{model.tier ? TIERS[model.tier] : '—'}</strong>
          </li>
          <li>
            <span>Tokens entrada / salida</span>
            <strong>
              {integer.format(model.inputTokens)} / {integer.format(model.outputTokens)}
            </strong>
          </li>
          <li>
            <span>Costo estimado</span>
            <strong>{model.estimatedCostUsd === null ? 'Sin precio' : usd.format(model.estimatedCostUsd)}</strong>
          </li>
          <li>
            <span>Tiempo</span>
            <strong>{(result.latencyMillis / 1000).toFixed(1)} s</strong>
          </li>
          <li>
            <span>Llamadas al modelo</span>
            <strong>{model.modelCalls}</strong>
          </li>
        </ul>
        {model.reason && (
          <p className="small muted" style={{ margin: '12px 0 0' }}>
            Por qué este nivel: <strong>{ROUTING_REASONS[model.reason] ?? model.reason}</strong>
          </p>
        )}
      </div>
      <div className="card">
        <div className="inspector-head">
          <h2>
            <Icons.Layers size={16} />
            Fragmentos recuperados
          </h2>
          <span className="muted small">
            {used} de {result.sources.length} usados
          </span>
        </div>
        {result.sources.length === 0 ? (
          <p className="muted small">
            La búsqueda no encontró nada relacionado. Si la pregunta es habitual,{' '}
            <Link to="/knowledge">agrega un documento</Link> que la responda.
          </p>
        ) : (
          <ol className="sources">
            {result.sources.map((source, index) => (
              <li key={`${source.documentId}-${index}`} className={source.usedByModel ? 'source is-used' : 'source'}>
                <div className="source-top">
                  {source.number !== null && <Badge tone="primary">[{source.number}]</Badge>}
                  <strong title={source.documentTitle}>{source.documentTitle}</strong>
                  {source.similarity !== null && (
                    <span className="match-score" title="Similitud con la pregunta">
                      <span className="meter">
                        <span style={{ width: `${Math.max(0, Math.min(1, source.similarity)) * 100}%` }} />
                      </span>
                      {percent.format(Math.max(0, source.similarity))}
                    </span>
                  )}
                </div>
                <div className="row" style={{ marginTop: 6 }}>
                  {source.usedByModel ? <Badge tone="success">Enviado al modelo</Badge> : <Badge>Descartado</Badge>}
                  {source.keywordMatch && <Badge tone="info">Coincidencia de palabras</Badge>}
                </div>
                <p>{source.content}</p>
              </li>
            ))}
          </ol>
        )}
      </div>
      <div className="card">
        <div className="inspector-head">
          <h2>
            <Icons.Zap size={16} />
            Acciones que habría usado
          </h2>
        </div>
        {result.toolCalls.length === 0 ? (
          <p className="muted small">El modelo no pidió ninguna acción para esta respuesta.</p>
        ) : (
          <ul className="tool-calls">
            {result.toolCalls.map((call, index) => (
              <li key={`${call.name}-${index}`} className="tool-call">
                <div className="row">
                  <strong className="mono">{call.name}</strong>
                  {!call.known && <Badge tone="danger">No existe</Badge>}
                  {call.requiresConfirmation && <Badge tone="warning">Pide confirmación</Badge>}
                  <Badge tone="info">Simulada</Badge>
                </div>
                {call.description && <p className="muted small" style={{ margin: '4px 0 0' }}>{call.description}</p>}
                <pre>{JSON.stringify(call.input, null, 2)}</pre>
              </li>
            ))}
          </ul>
        )}
      </div>
    </aside>
  );
}
