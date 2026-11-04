import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import tailwindcss from '@tailwindcss/vite';
import { visualizer } from 'rollup-plugin-visualizer';
import { fileURLToPath } from 'node:url';

const API = process.env.VITE_API_PROXY ?? 'http://localhost:8080';
const apiProxy = { '/api': { target: API, changeOrigin: false }, '/sitemap.xml': { target: API, changeOrigin: false } };

export default defineConfig(({ mode }) => ({
  plugins: [react(), tailwindcss(), mode === 'analyze' && visualizer({ filename: 'stats.html', gzipSize: true, brotliSize: true })],
  resolve: { alias: { '@': fileURLToPath(new URL('./src', import.meta.url)) } },
  // One browser origin in dev/preview: the Spring Boot API (backend/, port 8080) is proxied under /api,
  // and the backend also renders SEO heads for /c /u /s /t and the live sitemap.
  server: { port: 5173, proxy: apiProxy },
  preview: { port: 4173, proxy: apiProxy },
  build: {
    target: 'es2022',
    cssCodeSplit: true,
    sourcemap: false,
    manifest: true,
    modulePreload: { polyfill: false },
    rollupOptions: {
      output: {
        manualChunks(id) {
          if (id.includes('node_modules/react-dom') || id.includes('node_modules/react/') || id.includes('node_modules/scheduler')) return 'react';
          if (id.includes('node_modules/react-router')) return 'router';
          if (id.includes('node_modules/@tanstack')) return 'query';
          if (id.includes('node_modules/zod')) return 'zod';
        },
      },
    },
  },
  test: {
    environment: 'jsdom',
    setupFiles: ['./test/setup.ts'],
    include: ['test/**/*.test.{ts,tsx}', 'src/**/*.test.{ts,tsx}'],
    css: false,
  },
}));
