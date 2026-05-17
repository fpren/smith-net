/// <reference types="vitest" />
import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import path from 'path'

export default defineConfig({
  plugins: [react()],
  resolve: {
    alias: {
      '@console': path.resolve(__dirname, './src/console'),
    },
  },
  server: {
    port: 5173,
    proxy: {
      // ws:true so the WS upgrade at /api/ws (used by wsClient) is forwarded
      // to the backend. Same-origin from the browser's view keeps the
      // smithnet_access cookie attached on the upgrade request — the cookie
      // is scoped to Path=/api, so connecting via /api/ws is required.
      '/api': {
        target: 'http://localhost:3030',
        ws: true,
        changeOrigin: true,
      },
    },
  },
  test: {
    globals: true,
    environment: 'jsdom',
    setupFiles: './src/console/test/setup.ts',
    include: ['src/**/*.test.{ts,tsx}'],
  },
})
