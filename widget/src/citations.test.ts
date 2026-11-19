import { describe, expect, it } from 'vitest';
import { citedSources, splitCitations } from './citations';
import type { Citation } from './types';

const citation = (number: number): Citation => ({
  number,
  documentId: `doc-${number}`,
  documentTitle: `Doc ${number}`,
  chunkId: `chunk-${number}`,
  excerpt: `Excerpt ${number}`,
});

describe('splitCitations', () => {
  it('turns known markers into citations and keeps the text around them', () => {
    const parts = splitCitations('Envío en 3 días [1]. Gratis [2][1].', [citation(1), citation(2)]);
    expect(parts.map((part) => (part.kind === 'text' ? part.text : `#${part.citation.number}`))).toEqual([
      'Envío en 3 días ',
      '#1',
      '. Gratis ',
      '#2',
      '#1',
      '.',
    ]);
  });

  it('leaves markers without a citation as text', () => {
    expect(splitCitations('Ver [7] y [x]', [citation(1)])).toEqual([{ kind: 'text', text: 'Ver [7] y [x]' }]);
  });
});

describe('citedSources', () => {
  it('lists each cited source once, in order of first mention', () => {
    const sources = citedSources('A [2] B [1] C [2]', [citation(1), citation(2), citation(3)]);
    expect(sources.map((source) => source.number)).toEqual([2, 1]);
  });
});
