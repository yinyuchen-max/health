import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  server: {
    host: '0.0.0.0',
    port: 3000,
    allowedHosts: [
      '.ngrok-free.dev'
    ],
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
        secure: false
      }
    }
  },
  build: {
    rollupOptions: {
      output: {
        manualChunks: {
          vendor: ['vue', 'pinia', 'element-plus'],
          utils: ['axios']
        }
      }
    }
  },
  optimizeDeps: {
    include: ['vue', 'pinia', 'element-plus', 'axios']
  }
})