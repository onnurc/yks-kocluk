import { defineConfig } from 'vitest/config'
import react from '@vitejs/plugin-react'

// https://vite.dev/config/
export default defineConfig({
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
        target: 'http://localhost:8080',
        ws: true,
        changeOrigin: true,
      },
    },
  },
  test: {
    environment: 'jsdom',
    setupFiles: './src/test/setup.ts',
  },
})
