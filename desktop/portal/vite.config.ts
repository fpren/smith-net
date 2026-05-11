/// <reference types="vitest" />
import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import path from 'path'

export default defineConfig({
  plugins: [react()],
  resolve: {
    alias: {
      '@': path.resolve(__dirname, './src/dashboard'),
      '@console': path.resolve(__dirname, './src/console'),
    },
  },
  server: {
    port: 5173,
    proxy: {
      '/api': 'http://localhost:3030',
    },
  },
  test: {
    globals: true,
    environment: 'jsdom',
    setupFiles: './src/console/test/setup.ts',
    include: ['src/**/*.test.{ts,tsx}'],
  },
})
