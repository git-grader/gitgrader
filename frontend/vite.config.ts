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
    // Maven copies dist/ into the jar and /assets/** is permitAll, so a production map
    // is published, cached immutably for a year, and was 5.6 MB of the 7.4 MB payload.
    // The sources are Apache-2.0 and already on GitHub, so nothing is being hidden here;
    // what is avoided is shipping a debug artifact to every operator. Build locally with
    // `npx vite build --sourcemap` when a production stack trace needs resolving.
    sourcemap: false,
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
    restoreMocks: true,
    // The default is 5s, which the page tests clear comfortably on their own and exceed
    // once v8 instruments every render. Raised for the suite rather than sprinkled over
    // the tests that happened to notice first.
    testTimeout: 20_000,
    // A fixed zone with a real, non-zero offset and a daylight-saving change. The
    // datetime-local conversions are only meaningful where local time differs from UTC,
    // so leaving this to the machine would make those tests pass everywhere except CI,
    // which runs in UTC and would silently assert nothing.
    env: { TZ: 'Europe/Zurich' },
    coverage: {
      provider: 'v8',
      // Without this the report only counts files a test already imported, which made
      // the figure read ~71% while most of src/ was never measured at all. Counting the
      // whole tree is what makes the number mean "how much of the app is covered".
      include: ['src/**'],
      // A ratchet set just under today's real numbers, not a target being missed.
      // It only does that job while it is kept there: left at 18/11/8/19 while the
      // suite grew to 29/21/15/30, it had ten points of slack, and every test the
      // frontend has for the result and registration pages could have been deleted
      // without the build noticing. Now 60/52/45/61, so these sit a couple of points
      // under - close enough to catch a deletion, not so close that an unrelated
      // refactor fails the build.
      thresholds: { statements: 58, branches: 50, functions: 43, lines: 59 }
    }
  }
});
