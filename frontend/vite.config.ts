import { defineConfig } from 'vite';
import { svelte } from '@sveltejs/vite-plugin-svelte';
import path from 'path';

export default defineConfig({
  plugins: [svelte()],
  build: {
    outDir: path.resolve(import.meta.dirname, '../server/src/main/resources/public'),
    emptyOutDir: true,
    target: 'esnext'
  },
  server: {
    port: 5173,
    proxy: {
      '/api': 'http://127.0.0.1:7777',
      '/ws': {
        target: 'ws://127.0.0.1:7777',
        ws: true
      }
    }
  }
});
