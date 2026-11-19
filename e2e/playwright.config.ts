import { defineConfig, devices } from '@playwright/test';
import { API_URL, PANEL_URL, WIDGET_URL } from './support/env';

// End-to-end flows across the panel, the widget and the backend, each started here on its own port:
//   cd e2e && npm ci && npx playwright install chromium && npm test
// The backend runs with its dev profile (the offline model, the local database it starts with Docker Compose); it
// needs JAVA_HOME pointing at a JDK 25. Servers already running on these ports are reused outside CI.
const port = (url: string) => new URL(url).port;
const windows = process.platform === 'win32';

export default defineConfig({
  testDir: './tests',
  // Each flow signs up its own workspace, but they all share one backend
  fullyParallel: false,
  workers: 1,
  retries: process.env.CI ? 1 : 0,
  timeout: 90_000,
  expect: { timeout: 15_000 },
  forbidOnly: Boolean(process.env.CI),
  reporter: [['list'], ['html', { open: 'never' }]],
  use: {
    ...devices['Desktop Chrome'],
    locale: 'es-MX',
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
  },
  projects: [{ name: 'chromium' }],
  webServer: [
    {
      command: `${windows ? '.\\mvnw.cmd' : './mvnw'} -q spring-boot:run "-Dspring-boot.run.jvmArguments=-Djdk.net.unixdomain.tmpdir=target"`,
      cwd: '../backend',
      url: `${API_URL}/actuator/health`,
      env: {
        SERVER_PORT: port(API_URL),
        // The panel's dev server proxies the API but keeps the browser's Origin
        CORS_ALLOWED_ORIGINS: new URL(PANEL_URL).origin,
        PANEL_URL,
        // The flows sign up several accounts and start conversations from one address in a few seconds
        SUPPORTCHAT_RATE_LIMIT_ENABLED: 'false',
      },
      reuseExistingServer: !process.env.CI,
      // The first run compiles the backend and pulls the database image
      timeout: 300_000,
    },
    {
      command: `npm run dev -- --port ${port(PANEL_URL)} --strictPort`,
      cwd: '../panel',
      url: PANEL_URL,
      env: { API_URL },
      reuseExistingServer: !process.env.CI,
      timeout: 120_000,
    },
    {
      command: `npm run dev -- --port ${port(WIDGET_URL)} --strictPort`,
      cwd: '../widget',
      url: WIDGET_URL,
      reuseExistingServer: !process.env.CI,
      timeout: 120_000,
    },
  ],
});
