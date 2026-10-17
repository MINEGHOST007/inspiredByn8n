import { defineConfig } from '@playwright/test';

const baseURL = process.env.W6W_BASE_URL ?? 'http://127.0.0.1:8080';

export default defineConfig({
  testDir: './tests',
  testMatch: 'demo-video.spec.js',
  fullyParallel: false,
  retries: 0,
  reporter: [['list'], ['html', { outputFolder: '../../../artifacts/demo_presentation/video-report', open: 'never' }]],
  outputDir: '../../../artifacts/demo_presentation/video-output',
  use: {
    baseURL,
    browserName: 'chromium',
    headless: true,
    viewport: { width: 1920, height: 1080 },
    deviceScaleFactor: 1,
    video: { mode: 'on', size: { width: 1920, height: 1080 } },
    screenshot: 'on',
    trace: 'retain-on-failure',
  },
  webServer: {
    command: 'powershell -NoProfile -ExecutionPolicy Bypass -File ../../../run-app.ps1',
    url: `${baseURL}/api/v1/health`,
    reuseExistingServer: !process.env.CI,
    timeout: 180_000,
  },
});
