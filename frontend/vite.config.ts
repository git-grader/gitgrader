import { defineConfig } from 'vitest/config';
import react from '@vitejs/plugin-react';

export default defineConfig({
  base: '/',
  plugins: [react()],
  server: {
    proxy: {
      '/api': 'http://localhost:8080',
      '/actuator': 'http://localhost:8080',
      // Spring Security's form login and logout are served by the backend at the root
      // rather than under /api, so without them here a sign-in from the dev server is
      // answered by the SPA fallback and fails with 404.
      //
      // Only the POST may be proxied. `/login` is also a client route, and forwarding
      // its GET would return the backend's built index.html, whose hashed asset paths
      // do not exist on the dev server - the page then renders nothing at all.
      '/login': {
        target: 'http://localhost:8080',
        bypass: (req) => (req.method === 'POST' ? undefined : '/index.html')
      },
      '/logout': {
        target: 'http://localhost:8080',
        bypass: (req) => (req.method === 'POST' ? undefined : '/index.html')
      }
    }
  },
  build: {
    outDir: 'dist',
    sourcemap: true,
    rollupOptions: {
      output: {
        // Rolldown replaced the object form of manualChunks with named groups matched
        // against the module id, so the split is expressed as paths rather than as a
        // list of package names.
        codeSplitting: {
          groups: [
            { name: 'mui', test: /node_modules[\\/](@mui|@emotion)[\\/]/ },
            {
              name: 'vendor',
              test: /node_modules[\\/](react|react-dom|react-router|@tanstack[\\/]react-query|zod)[\\/]/
            }
          ]
        }
      }
    }
  },
  test: {
    environment: 'jsdom',
    setupFiles: ['./tests/setup.ts'],
    globals: true,
    coverage: {
      provider: 'v8'
    }
  }
});
