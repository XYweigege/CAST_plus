import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react(), tailwindcss()],
  server: {
    proxy: {
      // Java 后端：REST + SSE(/api/notify/stream) 都走 /api 前缀
      '/api': {
        target: 'http://localhost:3001',
        changeOrigin: true
      }
    }
  }
})
