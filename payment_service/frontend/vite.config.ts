import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

const backendTarget = process.env.VITE_PROXY_TARGET ?? 'http://localhost:5002'

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5174,
    host: true,
    proxy: {
      '/v1/pay': {
        target: backendTarget,
        changeOrigin: true,
        secure: false,
      },
      '/v1/payments': {
        target: backendTarget,
        changeOrigin: true,
        secure: false,
      },
      '/v1/users': {
        target: backendTarget,
        changeOrigin: true,
        secure: false,
      },
    },
  },
})
