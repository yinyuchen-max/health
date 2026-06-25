import axios from 'axios'
import { useUserStore } from '../store/user'

const isLikelyJwt = (value) => typeof value === 'string' && value.trim().split('.').length === 3
const configuredBaseUrl = import.meta.env.VITE_API_BASE_URL
const shouldUseViteProxy = typeof window !== 'undefined'
  && configuredBaseUrl === '/api'
  && window.location.port === '3000'
const resolvedBaseUrl = shouldUseViteProxy
  ? '/api'
  : (configuredBaseUrl === '/api' ? 'http://localhost:8080/api' : (configuredBaseUrl || 'http://localhost:8080'))

// Create reusable request instance
const request = axios.create({
  // 本地开发使用空字符串（走Vite proxy）
  // 生产环境通过环境变量 VITE_API_BASE_URL 配置后端地址
  baseURL: resolvedBaseUrl,
  timeout: 15000,
  headers: {
    'Content-Type': 'application/json'
  }
})

// Performance optimization: Request cache for GET requests
const requestCache = new Map()

// Request interceptor with optimized user store access
request.interceptors.request.use(
  config => {
    const userStore = useUserStore()
    if (isLikelyJwt(userStore.token)) {
      config.headers.Authorization = `Bearer ${userStore.token.trim()}`
    } else if (userStore.token) {
      userStore.logout()
    }

    // Add cache-busting parameter for GET requests to prevent stale data
    if (config.method === 'get') {
      config.params = {
        ...config.params,
        _t: Date.now() // Cache busting
      }
    }

    return config
  },
  error => {
    console.warn('Request configuration error:', error.message)
    return Promise.reject(error)
  }
)

// Response interceptor with graceful error handling
request.interceptors.response.use(
  response => {
    // Extract data efficiently
    return response.data || response
  },
  error => {
    // Handle authentication errors gracefully
    if (error.response?.status === 401) {
      try {
        const userStore = useUserStore()
        userStore.logout()
        // Use router push instead of location change for better SPA experience
        if (window.VueRouter) {
          window.VueRouter.push('/')
        } else {
          window.location.href = '/'
        }
      } catch (logoutError) {
        console.error('Logout failed:', logoutError)
        window.location.href = '/'
      }
    }

    // Provide meaningful error messages for debugging
    const errorMessage = error.response?.data?.message ||
                        error.message ||
                        'Network request failed'

    console.warn('API request failed:', errorMessage)
    return Promise.reject({
      message: errorMessage,
      status: error.response?.status,
      originalError: error
    })
  }
)

export default request
