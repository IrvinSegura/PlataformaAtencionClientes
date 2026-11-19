import { cleanup, configure } from '@testing-library/react';
import { afterEach, vi } from 'vitest';

// Pages load on first use (lazy), and their first import can take over the default second on a busy machine, such as
// CI running the backend's tests at the same time
configure({ asyncUtilTimeout: 3000 });

afterEach(() => {
  cleanup();
  vi.restoreAllMocks();
  vi.unstubAllGlobals();
});
