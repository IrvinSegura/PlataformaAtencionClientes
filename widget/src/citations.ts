import type { Citation } from './types';

export type AnswerPart = { kind: 'text'; text: string } | { kind: 'citation'; citation: Citation };

/**
 * Splits an answer into text and the [n] markers that point at its sources. Markers without a matching citation
 * stay as plain text, so a stray "[3]" written by the model never becomes a dead link.
 */
export function splitCitations(content: string, citations: readonly Citation[]): AnswerPart[] {
  const byNumber = new Map(citations.map((citation) => [citation.number, citation]));
  const parts: AnswerPart[] = [];
  let text = '';
  let last = 0;
  for (const match of content.matchAll(/\[(\d{1,3})\]/g)) {
    const citation = byNumber.get(Number(match[1]));
    if (citation === undefined) {
      continue;
    }
    text += content.slice(last, match.index);
    if (text) {
      parts.push({ kind: 'text', text });
      text = '';
    }
    parts.push({ kind: 'citation', citation });
    last = match.index + match[0].length;
  }
  text += content.slice(last);
  if (text) {
    parts.push({ kind: 'text', text });
  }
  return parts;
}

/** Citations in the order the answer mentions them, each once. */
export function citedSources(content: string, citations: readonly Citation[]): Citation[] {
  const seen = new Set<number>();
  const sources: Citation[] = [];
  for (const part of splitCitations(content, citations)) {
    if (part.kind === 'citation' && !seen.has(part.citation.number)) {
      seen.add(part.citation.number);
      sources.push(part.citation);
    }
  }
  return sources;
}
