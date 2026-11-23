import { defineConfig } from 'vite';
import pkg from './package.json' with { type: 'json' };

export default defineConfig({
  define: {
    __APP_VERSION__: JSON.stringify(pkg.version),
  },
  base: './',
  server: {
    host: '0.0.0.0',
    port: 5173,
    allowedHosts: true,
    strictPort: false,
  },
  preview: {
    host: '0.0.0.0',
    port: 4173,
    allowedHosts: true,
    strictPort: false,
  },
  build: {
    target: 'es2020',
    chunkSizeWarningLimit: 6000,
  },
});
