import { defineConfig } from '@playwright/test';

export default defineConfig({
    testDir: '.',
    use: {
        baseURL: `http://127.0.0.1:${process.env.SONGARCHIVE_TEST_PORT ?? 8123}`,
        browserName: 'chromium',
        locale: 'en-US',
        launchOptions: process.env.PLAYWRIGHT_CHROMIUM_EXECUTABLE
            ? { executablePath: process.env.PLAYWRIGHT_CHROMIUM_EXECUTABLE }
            : {},
    },
});
