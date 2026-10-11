import { sveltekit } from '@sveltejs/kit/vite';
import { defineConfig } from 'vitest/config';

const gateway = process.env.GATEWAY_URL ?? 'http://localhost:8080';

export default defineConfig({
  plugins: [sveltekit()],
  server: {
    // The browser calls /graphql and /api on the same origin; Vite forwards them to the gateway.
    // Same origin means no CORS setup is needed.
    proxy: { '/graphql': gateway, '/api': gateway }
  },
  test: { include: ['src/**/*.test.ts'] }
});
