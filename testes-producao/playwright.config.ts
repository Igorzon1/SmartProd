import { defineConfig } from '@playwright/test';

export default defineConfig({
  testDir: '.',
  testMatch: /e2e\.spec\.ts/,
  timeout: 90_000,
  expect: { timeout: 15_000 },
  fullyParallel: false,
  workers: 1,
  retries: 0,
  reporter: [['list']],
  use: {
    baseURL: process.env.SMARTPROD_URL,
    channel: process.env.PW_CHANNEL || 'chrome',
    headless: true,
    viewport: { width: 1180, height: 820 },
    locale: 'pt-BR',
    timezoneId: 'America/Sao_Paulo',
    ignoreHTTPSErrors: false,
    screenshot: 'only-on-failure',
    trace: 'off',
  },
});
