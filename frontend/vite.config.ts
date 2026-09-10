import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],

  // Decision: production build output goes directly into Spring Boot's
  // static resources folder, so a single `mvn package` produces one jar
  // that serves both the API and the UI (same-origin, no CORS needed).
  // This only takes effect on `npm run build` -- never touched by `npm run dev`.
  build: {
    outDir: '../src/main/resources/static',
    emptyOutDir: true, // trap: without this, stale JS/CSS from a previous
    // build survives and you debug the wrong bundle
  },

  // Decision: dev-time only. `npm run dev` still needs to reach the real
  // backend on :8080. This proxy exists ONLY for local development speed
  // (HMR); it never runs during `npm run build`, so it has zero effect
  // on the production same-origin deployment above.
  server: {
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
})