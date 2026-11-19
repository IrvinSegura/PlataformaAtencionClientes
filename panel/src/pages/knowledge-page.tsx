import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useId, useRef, useState, type DragEvent, type FormEvent } from 'react';
import { useSearchParams } from 'react-router';
import { errorMessage } from '../api/error-messages';
import { failureReasonText } from '../api/failure-reasons';
import { type DocumentStatus, knowledgeApi, type KnowledgeDocument } from '../api/knowledge';
import { usePermissions } from '../auth/auth-context';
import { Icons } from '../components/icons';
import { Badge, Empty, ErrorAlert, PageHeader, Pagination, ProgressBar, Spinner, Stat, type Tone } from '../components/ui';
import { formatBytes, formatDateTime } from '../format';

const PAGE_SIZE = 20;
const MAX_FILE_BYTES = 10 * 1024 * 1024;
const MAX_LINKED_PAGES = 30;
// While a document is being ingested its status changes within seconds
const PROCESSING_REFRESH_MS = 2_000;

/** Formats the backend reads; the type is checked again there from the file's content. */
export const ACCEPTED_EXTENSIONS = ['.pdf', '.docx', '.doc', '.xlsx', '.xls', '.html', '.htm', '.md', '.markdown', '.txt'];
const FORMAT_BADGES = ['PDF', 'Word', 'Excel', 'HTML', 'Markdown', 'TXT'];

const STATUSES: Record<DocumentStatus, { label: string; tone: Tone }> = {
  PENDING: { label: 'En cola', tone: 'info' },
  PROCESSING: { label: 'Procesando', tone: 'info' },
  READY: { label: 'Listo', tone: 'success' },
  FAILED: { label: 'Falló', tone: 'danger' },
};

export function KnowledgePage() {
  const { canManageKnowledge } = usePermissions();
  const [params, setParams] = useSearchParams();
  const page = Math.max(0, Number(params.get('page')) || 0);
  const documents = useQuery({
    queryKey: ['documents', page],
    queryFn: () => knowledgeApi.list(page, PAGE_SIZE),
    placeholderData: keepPreviousData,
    refetchInterval: (query) =>
      query.state.data?.items.some((document) => document.status === 'PENDING' || document.status === 'PROCESSING')
        ? PROCESSING_REFRESH_MS
        : false,
  });
  const items = documents.data?.items ?? [];
  const partial = documents.data !== undefined && documents.data.total > items.length;

  return (
    <section aria-labelledby="knowledge-title">
      <PageHeader
        id="knowledge-title"
        eyebrow="Asistente"
        title="Base de conocimiento"
        description="El asistente solo responde con estos documentos y páginas; si algo no está, lo dice."
      />
      {documents.data && documents.data.total > 0 && (
        <ul className="stats" aria-label="Resumen">
          <Stat icon="Book" label="Fuentes" value={documents.data.total} />
          <Stat
            icon="Check"
            label="Listas para responder"
            value={items.filter((document) => document.status === 'READY').length}
            detail={partial ? 'En esta página' : undefined}
          />
          <Stat
            icon="Layers"
            label="Fragmentos indexados"
            value={items.reduce((sum, document) => sum + (document.status === 'READY' ? document.chunkCount : 0), 0)}
            detail={partial ? 'En esta página' : undefined}
          />
          <Stat
            icon="Alert"
            label="Con error"
            value={items.filter((document) => document.status === 'FAILED').length}
            detail={partial ? 'En esta página' : undefined}
          />
        </ul>
      )}
      {canManageKnowledge && <AddKnowledge />}
      <ErrorAlert error={documents.error} />
      {documents.isPending && <Spinner />}
      {items.length > 0 && (
        <h2 className="section-title">
          Fuentes <span className="count">{documents.data?.total}</span>
        </h2>
      )}
      {documents.data && items.length === 0 && (
        <Empty icon="Book">
          Todavía no hay documentos. Sube preguntas frecuentes, políticas, manuales o catálogos, o importa las páginas de
          ayuda de tu sitio web.
        </Empty>
      )}
      {items.length > 0 && (
        <div className="table-wrap">
          <table className="table">
            <thead>
              <tr>
                <th scope="col">Fuente</th>
                <th scope="col">Estado</th>
                <th scope="col" className="numeric">
                  Fragmentos
                </th>
                <th scope="col" className="numeric">
                  Tamaño
                </th>
                <th scope="col">Agregado</th>
                {canManageKnowledge && (
                  <th scope="col">
                    <span className="sr-only">Acciones</span>
                  </th>
                )}
              </tr>
            </thead>
            <tbody>
              {items.map((document) => (
                <DocumentRow key={document.id} document={document} canManage={canManageKnowledge} />
              ))}
            </tbody>
          </table>
        </div>
      )}
      {documents.data && (
        <Pagination
          page={page}
          size={PAGE_SIZE}
          total={documents.data.total}
          onChange={(next) => setParams(next ? { page: String(next) } : {})}
        />
      )}
    </section>
  );
}

type Source = 'files' | 'web';

function AddKnowledge() {
  const [source, setSource] = useState<Source>('files');
  const titleId = useId();
  return (
    <section className="card" aria-labelledby={titleId}>
      <div className="card-header">
        <div className="card-header-main">
          <span className="card-header-icon">
            <Icons.Plus size={18} />
          </span>
          <div>
            <h2 className="card-title" id={titleId}>
              Agregar conocimiento
            </h2>
            <p>Cada fuente se divide en fragmentos y se indexa en segundos; el asistente la usa en cuanto está lista.</p>
          </div>
        </div>
      </div>
      <div className="tabs" role="tablist" aria-label="Tipo de fuente">
        <button
          type="button"
          role="tab"
          aria-selected={source === 'files'}
          className={source === 'files' ? 'tab active' : 'tab'}
          onClick={() => setSource('files')}
        >
          <Icons.Upload size={15} />
          Subir archivos
        </button>
        <button
          type="button"
          role="tab"
          aria-selected={source === 'web'}
          className={source === 'web' ? 'tab active' : 'tab'}
          onClick={() => setSource('web')}
        >
          <Icons.Globe size={15} />
          Importar sitio web
        </button>
      </div>
      {source === 'files' ? <FileUploader /> : <WebImportForm />}
    </section>
  );
}

interface QueuedUpload {
  key: string;
  file: File;
  progress: number;
  state: 'uploading' | 'done' | 'failed';
  error?: string;
}

function extensionOf(name: string): string {
  const dot = name.lastIndexOf('.');
  return dot < 0 ? '' : name.slice(dot).toLowerCase();
}

/** Problems the browser can see before sending anything; the backend checks the rest. */
export function rejectReason(file: File): string | null {
  if (!ACCEPTED_EXTENSIONS.includes(extensionOf(file.name))) {
    return 'Formato no admitido';
  }
  if (file.size > MAX_FILE_BYTES) {
    return 'Supera el máximo de 10 MB';
  }
  if (file.size === 0) {
    return 'El archivo está vacío';
  }
  return null;
}

function FileUploader() {
  const queryClient = useQueryClient();
  const input = useRef<HTMLInputElement>(null);
  const [dragging, setDragging] = useState(false);
  const [queue, setQueue] = useState<QueuedUpload[]>([]);
  const hintId = useId();

  const update = (key: string, change: Partial<QueuedUpload>) =>
    setQueue((current) => current.map((item) => (item.key === key ? { ...item, ...change } : item)));

  const add = (files: FileList | File[]) => {
    const added: QueuedUpload[] = Array.from(files).map((file, index) => {
      const reason = rejectReason(file);
      return {
        key: `${Date.now()}-${index}-${file.name}`,
        file,
        progress: 0,
        state: reason ? 'failed' : 'uploading',
        ...(reason ? { error: reason } : {}),
      };
    });
    setQueue((current) => [...added, ...current.filter((item) => item.state === 'uploading')]);
    for (const item of added) {
      if (item.state !== 'uploading') {
        continue;
      }
      knowledgeApi
        .uploadWithProgress(item.file, (fraction) => update(item.key, { progress: fraction }))
        .then(() => {
          update(item.key, { state: 'done', progress: 1 });
          void queryClient.invalidateQueries({ queryKey: ['documents'] });
        })
        .catch((error: unknown) => update(item.key, { state: 'failed', error: errorMessage(error) }));
    }
    if (input.current) {
      input.current.value = '';
    }
  };

  const onDrop = (event: DragEvent) => {
    event.preventDefault();
    setDragging(false);
    if (event.dataTransfer.files.length > 0) {
      add(event.dataTransfer.files);
    }
  };

  return (
    <div>
      <label
        className={dragging ? 'dropzone is-dragging' : 'dropzone'}
        onDragOver={(event) => {
          event.preventDefault();
          setDragging(true);
        }}
        onDragLeave={() => setDragging(false)}
        onDrop={onDrop}
      >
        <span className="dropzone-icon">
          <Icons.Upload size={20} />
        </span>
        <p className="dropzone-title">
          Arrastra archivos aquí o <span>elígelos de tu equipo</span>
        </p>
        <span className="dropzone-formats" id={hintId}>
          {FORMAT_BADGES.map((format) => (
            <Badge key={format}>{format}</Badge>
          ))}
          <span className="hint">· hasta 10 MB cada uno</span>
        </span>
        <input
          ref={input}
          type="file"
          multiple
          accept={ACCEPTED_EXTENSIONS.join(',')}
          aria-label="Archivos para la base de conocimiento"
          aria-describedby={hintId}
          onChange={(event) => event.target.files && add(event.target.files)}
        />
      </label>
      {queue.length > 0 && (
        <ul className="upload-queue" aria-label="Subidas">
          {queue.map((item) => (
            <UploadItem key={item.key} item={item} />
          ))}
        </ul>
      )}
    </div>
  );
}

function UploadItem({ item }: { item: QueuedUpload }) {
  const percent = Math.round(item.progress * 100);
  const state =
    item.state === 'uploading'
      ? percent >= 100
        ? 'Procesando…'
        : `${percent} %`
      : item.state === 'done'
        ? 'Recibido'
        : (item.error ?? 'Falló');
  return (
    <li className={`upload-item is-${item.state}`}>
      <span className="file-icon">
        <Icons.File size={16} />
      </span>
      <div className="upload-item-body">
        <div className="upload-item-top">
          <span className="upload-item-name">{item.file.name}</span>
          <span className="upload-item-state" role={item.state === 'failed' ? 'alert' : undefined}>
            {state}
          </span>
        </div>
        <ProgressBar
          label={`Subida de ${item.file.name}`}
          value={item.state === 'failed' ? 1 : item.progress}
          {...(item.state === 'done' ? { tone: 'success' as const } : item.state === 'failed' ? { tone: 'danger' as const } : {})}
        />
      </div>
      <span className="muted small">{formatBytes(item.file.size)}</span>
    </li>
  );
}

function WebImportForm() {
  const queryClient = useQueryClient();
  const [url, setUrl] = useState('');
  const [title, setTitle] = useState('');
  const [linkedPages, setLinkedPages] = useState(0);
  const importPage = useMutation({
    mutationFn: () => knowledgeApi.importWebPage({ url: url.trim(), linkedPages, ...(title.trim() ? { title: title.trim() } : {}) }),
    onSuccess: () => {
      setUrl('');
      setTitle('');
      void queryClient.invalidateQueries({ queryKey: ['documents'] });
    },
  });
  const onSubmit = (event: FormEvent) => {
    event.preventDefault();
    importPage.mutate();
  };

  return (
    <form className="form" onSubmit={onSubmit} aria-label="Importar una página web">
      <div className="web-import">
        <label>
          Dirección de la página
          <input
            type="url"
            inputMode="url"
            value={url}
            onChange={(event) => {
              setUrl(event.target.value);
              importPage.reset();
            }}
            required
            maxLength={2048}
            placeholder="https://misitio.com/preguntas-frecuentes"
          />
        </label>
        <label>
          Título (opcional)
          <input value={title} onChange={(event) => setTitle(event.target.value)} maxLength={200} placeholder="El de la página" />
        </label>
      </div>
      <label className="range-field">
        <span>
          Páginas enlazadas del mismo sitio: <output>{linkedPages === 0 ? 'solo esta página' : linkedPages}</output>
        </span>
        <input
          type="range"
          min={0}
          max={MAX_LINKED_PAGES}
          value={linkedPages}
          onChange={(event) => setLinkedPages(Number(event.target.value))}
          aria-describedby="linked-pages-hint"
        />
        <span id="linked-pages-hint" className="hint">
          Se importan también hasta {MAX_LINKED_PAGES} páginas de tu sitio enlazadas desde esta. Solo se descargan páginas
          públicas; «Reprocesar» las vuelve a descargar para actualizarlas.
        </span>
      </label>
      <ErrorAlert error={importPage.error} />
      {importPage.isSuccess && (
        <p className="alert alert-success" role="status">
          Página en cola. Se descargará e indexará en segundo plano; sigue su avance en la tabla.
        </p>
      )}
      <div className="form-footer">
        <button type="submit" className="button button-primary" disabled={importPage.isPending || url.trim() === ''}>
          <Icons.Globe size={16} />
          {importPage.isPending ? 'Importando…' : 'Importar'}
        </button>
      </div>
    </form>
  );
}

const STAGES = { FETCHING: 'Descargando la página', EXTRACTING: 'Extrayendo el texto', INDEXING: 'Indexando' } as const;

function StageProgress({ document }: { document: KnowledgeDocument }) {
  if (document.status === 'PENDING') {
    return <ProgressBar label="En cola" />;
  }
  const stage = document.stage ?? null;
  const embedded = document.embeddedChunks ?? 0;
  const total = document.chunkCount;
  const indexing = stage === 'INDEXING' && total > 0;
  return (
    <div className="stage">
      <ProgressBar label={stage ? STAGES[stage] : 'Procesando'} value={indexing ? embedded / total : null} />
      <span className="stage-label">
        {stage ? STAGES[stage] : 'Procesando'}
        {indexing && ` · ${embedded} de ${total}`}
      </span>
    </div>
  );
}

function DocumentRow({ document, canManage }: { document: KnowledgeDocument; canManage: boolean }) {
  const queryClient = useQueryClient();
  const refresh = () => queryClient.invalidateQueries({ queryKey: ['documents'] });
  const remove = useMutation({ mutationFn: () => knowledgeApi.remove(document.id), onSuccess: refresh });
  const reprocess = useMutation({ mutationFn: () => knowledgeApi.reprocess(document.id), onSuccess: refresh });
  const status = STATUSES[document.status];
  const busy = document.status === 'PENDING' || document.status === 'PROCESSING';
  const error = remove.error ?? reprocess.error;
  const web = Boolean(document.sourceUrl);

  return (
    <tr>
      <td>
        <div className="cell-with-icon">
          <span className={web ? 'file-icon is-web' : 'file-icon'}>{web ? <Icons.Globe size={17} /> : <Icons.File size={17} />}</span>
          <div style={{ minWidth: 0 }}>
            <div className="cell-title">{document.title}</div>
            {web ? (
              <a className="source-link" href={document.sourceUrl!} target="_blank" rel="noreferrer noopener">
                {document.sourceUrl}
                <Icons.External size={12} />
              </a>
            ) : (
              <div className="muted small">{document.fileName}</div>
            )}
          </div>
        </div>
        {error ? <ErrorAlert error={error} /> : null}
      </td>
      <td>
        {busy ? (
          <StageProgress document={document} />
        ) : (
          <Badge tone={status.tone} dot>
            {status.label}
          </Badge>
        )}
        {document.failureReason && <div className="failure">{failureReasonText(document.failureReason)}</div>}
      </td>
      <td className="numeric">{document.status === 'READY' ? document.chunkCount : '—'}</td>
      <td className="numeric muted">{document.sizeBytes > 0 ? formatBytes(document.sizeBytes) : '—'}</td>
      <td className="muted small">{formatDateTime(document.createdAt)}</td>
      {canManage && (
        <td className="actions">
          <button
            type="button"
            className="button button-sm"
            disabled={busy || reprocess.isPending}
            onClick={() => reprocess.mutate()}
            title={web ? 'Descargar la página de nuevo' : 'Volver a extraer y fragmentar el documento'}
          >
            <Icons.Refresh size={15} />
            {web ? 'Actualizar' : 'Reprocesar'}
          </button>
          <button
            type="button"
            className="button button-sm button-danger"
            disabled={remove.isPending}
            onClick={() => {
              if (window.confirm(`¿Eliminar "${document.title}"? El asistente dejará de usarlo de inmediato.`)) {
                remove.mutate();
              }
            }}
          >
            <Icons.Trash size={15} />
            Eliminar
          </button>
        </td>
      )}
    </tr>
  );
}
