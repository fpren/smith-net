/// <reference types="vitest" />
import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import { VitePWA } from 'vite-plugin-pwa'
import path from 'path'

export default defineConfig({
  plugins: [
    react(),
    VitePWA({
      registerType: 'autoUpdate',
      includeManifestIcons: false,
      manifest: {
        name: 'Smith Net',
        short_name: 'Smith Net',
        description: 'Smith Net console -- jobs, invoices, crew, and comm.',
        display: 'standalone',
        start_url: '/',
        scope: '/',
        theme_color: '#2F5FE8',
        background_color: '#F7F8FA',
        icons: [
          { src: 'pwa-192x192.png', sizes: '192x192', type: 'image/png' },
          { src: 'pwa-512x512.png', sizes: '512x512', type: 'image/png' },
          { src: 'maskable-icon-512x512.png', sizes: '512x512', type: 'image/png', purpose: 'maskable' },
        ],
      },
      workbox: {
        globPatterns: ['**/*.{js,css,html,wasm,svg,png,ico,woff2}'],
        navigateFallback: '/index.html',
        // Backend-owned paths: API, public proposal pages, invoice short
        // links, and uploaded media must reach the server, never the cached
        // SPA shell.
        navigateFallbackDenylist: [/^\/api/, /^\/p\//, /^\/i\//, /^\/media\//],
      },
      devOptions: { enabled: false },
    }),
  ],
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
      // smithnet_access cookie attached on the upgrade request -- the cookie
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
