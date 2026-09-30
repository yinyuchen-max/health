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
    // 提高 chunk 体积警告阈值，避免无意义告警
    chunkSizeWarningLimit: 1200,
    rollupOptions: {
      output: {
        manualChunks(id) {
          if (id.includes('node_modules')) {
            // echarts/zrender 体积大且仅部分页面使用，单独拆包按需加载
            if (id.includes('echarts') || id.includes('zrender')) return 'echarts'
            if (id.includes('element-plus') || id.includes('@element-plus')) return 'element-plus'
            if (id.includes('vue') || id.includes('pinia')) return 'vue-core'
            return 'vendor'
          }
        }
      }
    }
  },
  optimizeDeps: {
    include: ['vue', 'pinia', 'element-plus', 'axios']
  }
})