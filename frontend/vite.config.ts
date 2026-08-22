// defineConfig comes from vitest/config (it carries the `test` block's types); loadEnv is a
// plain Vite export that vitest/config does not re-export, hence the two separate imports.
import { defineConfig } from 'vitest/config'
import { loadEnv } from 'vite'
import react from '@vitejs/plugin-react'

// https://vite.dev/config/
export default defineConfig(({ mode }) => {
  // The /ws proxy target has to be the same backend VITE_API_BASE_URL points at, or REST and
  // WebSocket traffic silently go to different places. Reading it from .env (gitignored) keeps
  // that single source of truth out of this tracked file — otherwise moving the backend to
  // another port means editing both, and the vite.config.ts half shows up as a local
  // modification that conflicts on every pull (see docs/handoff.md "Port 8080 is intercepted
  // by a local proxy" for why anyone needs a non-default port here at all).
  const env = loadEnv(mode, process.cwd(), 'VITE_')
  const backendOrigin = env.VITE_API_BASE_URL || 'http://localhost:8080'

  return {
    plugins: [react()],
    // sockjs-client is a CommonJS/Node-era library that references the Node `global` object;
    // browsers (and Vite's dev/build output) don't have one, so alias it to globalThis.
    define: {
      global: 'globalThis',
    },
    server: {
      proxy: {
        // Dev-only: sockjs-client's capability-check XHR (GET /ws/info) unconditionally sends
        // withCredentials=true when it detects a cross-origin URL, which the backend's CORS
        // config rejects (allowCredentials is deliberately false there — see SecurityConfig).
        // sockjs-client has no option to disable that, so instead make the request same-origin
        // by proxying it through the Vite dev server; useConversationSocket.ts connects to a
        // same-origin /ws URL only in dev for this reason (see its comment).
        '/ws': {
          target: backendOrigin,
          ws: true,
          changeOrigin: true,
        },
      },
    },
    test: {
      environment: 'jsdom',
      setupFiles: './src/test/setup.ts',
    },
  }
})
