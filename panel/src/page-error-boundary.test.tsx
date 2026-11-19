import { render, screen } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { PageErrorBoundary } from './components/page-error-boundary';

function Broken(): never {
  throw new Error('Failed to fetch dynamically imported module');
}

afterEach(() => {
  vi.restoreAllMocks();
});

describe('PageErrorBoundary', () => {
  it('offers to reload a page that failed, and tries again on another page', () => {
    vi.spyOn(console, 'error').mockImplementation(() => {});
    const { rerender } = render(
      <PageErrorBoundary resetKey="/outcomes">
        <Broken />
      </PageErrorBoundary>,
    );
    expect(screen.getByRole('alert').textContent).toContain('No se pudo abrir esta página');
    expect(screen.getByRole('button', { name: 'Recargar el panel' })).toBeDefined();

    rerender(
      <PageErrorBoundary resetKey="/usage">
        <p>Uso del mes</p>
      </PageErrorBoundary>,
    );
    expect(screen.getByText('Uso del mes')).toBeDefined();
  });
});
