import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

const proxyTarget = process.env.VITE_PROXY_TARGET ?? 'http://localhost:8081'

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    host: true,
    allowedHosts: ['app.pt', 'transactions.pt', 'iam.pt', 'keycloak.pt'],
    proxy: {
      '/v1': {
        target: proxyTarget,
        changeOrigin: true,
        secure: false
      }
    }
  }
})
