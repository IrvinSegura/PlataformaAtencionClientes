import react from '@vitejs/plugin-react';
import { defineConfig } from 'vitest/config';

// In development the API is proxied, so the panel and the API share one origin: no CORS, and the refresh token
// cookie (Path=/api/v1/auth, SameSite=Strict) is a plain first-party cookie. API_URL points it at another backend,
// e.g. the one the end-to-end tests start
// The config runs in Node; the project's types are the browser's, so only this is declared
declare const process: { env: Record<string, string | undefined> };
const api = process.env.API_URL ?? 'http://localhost:8080';

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    strictPort: true,
    proxy: {
      '/api': api,
      // The public legal pages the backend serves, linked from sign-up
      '/legal': api,
    },
  },
  test: {
    environment: 'jsdom',
    setupFiles: ['src/test/setup.ts'],
  },
});
