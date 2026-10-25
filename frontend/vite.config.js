import { defineConfig, loadEnv } from 'vite';
import react from '@vitejs/plugin-react';

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '');
  return {
    plugins: [react()],
    server: {
      port: 5173,
      host: true, // reachable from phones on the same Wi-Fi
      proxy: {
        // In dev, /api calls go through Vite to Spring Boot, so no CORS setup is needed for the API.
        '/api': {
          target: env.VITE_DEV_API_PROXY || 'http://localhost:8080',
          changeOrigin: true,
        },
      },
    },
    preview: {
      proxy: {
        '/api': {
          target: env.VITE_DEV_API_PROXY || 'http://localhost:8080',
          changeOrigin: true,
        },
      },
    },
    build: {
      target: 'es2020',
      sourcemap: false,
      chunkSizeWarningLimit: 1600, // model-viewer bundles three.js
    },
  };
});
