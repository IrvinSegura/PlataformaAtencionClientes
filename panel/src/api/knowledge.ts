// Knowledge base, knowledge gaps and taught answers (backend module knowledge)

import { pathSegment, request, uploadWithProgress } from './http-client';
import type { Page } from './page';

export type DocumentStatus = 'PENDING' | 'PROCESSING' | 'READY' | 'FAILED';

/** What a document being processed is doing now. */
export type DocumentStage = 'FETCHING' | 'EXTRACTING' | 'INDEXING';

export interface KnowledgeDocument {
  id: string;
  title: string;
  fileName: string;
  mediaType: string;
  sizeBytes: number;
  status: DocumentStatus;
  failureReason: string | null;
  chunkCount: number;
  createdAt: string;
  updatedAt: string;
  /** The page's address when the document is a web page; null for an uploaded file. */
  sourceUrl?: string | null;
  stage?: DocumentStage | null;
  /** Passages already indexed while the stage is INDEXING. */
  embeddedChunks?: number;
}

export interface WebPageInput {
  url: string;
  title?: string;
  /** Other pages of the same site linked from this one to import too, 0 to 30. */
  linkedPages: number;
}

export interface KnowledgeMatch {
  chunkId: string;
  documentId: string;
  documentTitle: string;
  content: string;
  similarity: number | null;
  keywordMatch: boolean;
  score: number;
}

export type GapStatus = 'OPEN' | 'RESOLVED' | 'DISMISSED';

export interface KnowledgeGap {
  id: string;
  question: string;
  status: GapStatus;
  occurrences: number;
  firstAskedAt: string;
  lastAskedAt: string;
  lastConversationId: string;
}

/** Unanswered questions on one topic: one document would answer them all. */
export interface DocumentSuggestion {
  title: string;
  occurrences: number;
  lastAskedAt: string;
  questions: KnowledgeGap[];
}

/** The knowledge base document that now holds an answer the team taught. */
export interface TaughtAnswer {
  documentId: string;
}

export const knowledgeApi = {
  list: (page: number, size = 20) =>
    request<Page<KnowledgeDocument>>(`/api/v1/knowledge/documents?page=${page}&size=${size}`),
  upload(file: File, title?: string): Promise<KnowledgeDocument> {
    const form = new FormData();
    form.append('file', file);
    const query = title ? `?title=${encodeURIComponent(title)}` : '';
    return request<KnowledgeDocument>(`/api/v1/knowledge/documents${query}`, { method: 'POST', body: form });
  },
  /** Same as upload, reporting the fraction of the file sent so far. */
  uploadWithProgress(file: File, onProgress: (fraction: number) => void, signal?: AbortSignal) {
    const form = new FormData();
    form.append('file', file);
    return uploadWithProgress<KnowledgeDocument>('/api/v1/knowledge/documents', form, onProgress, signal);
  },
  importWebPage: (input: WebPageInput) =>
    request<KnowledgeDocument>('/api/v1/knowledge/documents/web', { json: input }),
  remove: (documentId: string) => request<void>(`/api/v1/knowledge/documents/${pathSegment(documentId)}`, { method: 'DELETE' }),
  reprocess: (documentId: string) =>
    request<KnowledgeDocument>(`/api/v1/knowledge/documents/${pathSegment(documentId)}/reprocess`, { method: 'POST' }),
  search: (question: string, limit: number) =>
    request<KnowledgeMatch[]>('/api/v1/knowledge/search', { json: { question, limit } }),
};

export const gapsApi = {
  list: (status: GapStatus, page: number, size = 20) =>
    request<Page<KnowledgeGap>>(`/api/v1/knowledge-gaps?status=${status}&page=${page}&size=${size}`),
  changeStatus: (gapId: string, status: GapStatus) =>
    request<void>(`/api/v1/knowledge-gaps/${pathSegment(gapId)}/status`, { method: 'PUT', json: { status } }),
  suggestions: () => request<DocumentSuggestion[]>('/api/v1/knowledge-gaps/suggestions'),
  teach: (gapId: string, answer: string) =>
    request<TaughtAnswer>(`/api/v1/knowledge-gaps/${pathSegment(gapId)}/answer`, { json: { answer } }),
};
