import axios from 'axios'
import { runtime } from '../config/runtime'
import { useUserStore } from '../store/user'
import { apiBaseUrl, ensureSession, authState } from './auth'

const publicPaths = new Set(['/user/login', '/user/register', '/doctor/full-register', '/doctor/list', '/doctor/departments'])
const request = axios.create({ baseURL: apiBaseUrl, timeout: runtime.requestTimeoutMs,
  headers: { 'Content-Type': 'application/json' } })

request.interceptors.request.use(async config => {
  if (!publicPaths.has(config.url)) {
    const token = await ensureSession()
    if (token) config.headers.Authorization = `Bearer ${token}`
    else delete config.headers.Authorization
  }
  if (config.method === 'get') config.params = { ...config.params, _t: Date.now() }
  return config
})
request.interceptors.response.use(response => response.data || response, async error => {
  const config = error.config
  if (error.response?.status === 401 && config && !config._authRetried && !publicPaths.has(config.url)) {
    config._authRetried = true
    const sentToken = String(config.headers?.Authorization || '').replace(/^Bearer /, '')
    try {
      const token = await ensureSession(true, sentToken)
      if (token) return request(config)
    } catch (refreshError) {
      return Promise.reject({ message: refreshError.response?.data?.message || '认证服务暂时不可用',
        status: refreshError.response?.status })
    }
  }
  if (error.response?.status === 401 && !publicPaths.has(config?.url)) {
    // A newer sign-in must not be cleared by a delayed response from the old session.
    const sentToken = String(config?.headers?.Authorization || '').replace(/^Bearer /, '')
    if (!authState.token || sentToken === authState.token) {
      useUserStore().clearSession()
      window.location.assign('/')
    }
  }
  return Promise.reject({ message: error.response?.data?.message || error.message || '请求失败',
    status: error.response?.status })
})
export default request
