import axios from 'axios'
import { reactive } from 'vue'
import { runtime } from '../config/runtime'

export const apiBaseUrl = import.meta.env.VITE_API_BASE_URL?.trim() || '/api'
const client = axios.create({ baseURL: apiBaseUrl, timeout: runtime.requestTimeoutMs, withCredentials: true })
export const authState = reactive({ token: '', expiresAt: 0 })
let csrfPromise
let refreshPromise
let restoreAttempted = false
let generation = 0
let closing = false

// Remove tokens saved by the old application. No authentication credentials are persisted by the web client.
localStorage.removeItem('token')
localStorage.removeItem('userInfo')

function safeError(error) {
  const message = error.response?.data?.message || error.message || '认证请求失败'
  return { message, response: { status: error.response?.status, data: { message } } }
}
async function csrfHeaders() {
  if (!csrfPromise) {
    // 只合并正在进行的请求；Cookie 被另一标签页更新后不能沿用旧校验值。
    csrfPromise = client.get('/user/csrf').then(({ data }) => {
      if ((data.code !== 200 && data.code !== 0) || data.data?.headerName !== 'X-CSRF-TOKEN'
          || typeof data.data?.token !== 'string' || !data.data.token) {
        throw new Error(data.message || '登录验证获取失败，请重试')
      }
      return { [data.data.headerName]: data.data.token }
    }).catch(error => { throw safeError(error) })
      .finally(() => { csrfPromise = undefined })
  }
  return csrfPromise
}
export function clearSession() {
  generation++
  authState.token = ''
  authState.expiresAt = 0
  restoreAttempted = true
}
function accept(data) {
  if (typeof data?.token !== 'string' || data.token.split('.').length !== 3 || !(data.expiresIn > 0)) {
    throw new Error('登录响应格式无效')
  }
  authState.token = data.token
  authState.expiresAt = Date.now() + data.expiresIn * 1000
  restoreAttempted = true
  return data.token
}
async function cookiePost(url, data = {}) {
  try {
    let response
    try { response = await client.post(url, data, { headers: await csrfHeaders() }) }
    catch (error) {
      // 服务端明确说明 CSRF 失效时，只更新验证值并重试一次。
      if (error.response?.status !== 403 || error.response?.data?.data?.reason !== 'CSRF') throw error
      response = await client.post(url, data, { headers: await csrfHeaders() })
    }
    if (response.data?.code !== 200 && response.data?.code !== 0) {
      throw new Error(response.data?.message || '认证请求失败')
    }
    return response
  } catch (error) { throw safeError(error) }
}
// Web Locks serialize cookie rotation across tabs; the promise also coalesces requests within a tab.
async function withRefreshLock(operation) {
  if (globalThis.navigator?.locks) return navigator.locks.request('health-refresh', operation)
  return operation()
}
export async function login(credentials) {
  generation++
  closing = false
  const { data } = await withRefreshLock(() => cookiePost('/user/login', credentials))
  return accept(data.data)
}
export function ensureSession(force = false, failedToken = '') {
  if (closing) return Promise.resolve('')
  if (failedToken && authState.token && authState.token !== failedToken) return Promise.resolve(authState.token)
  if (!force && authState.token && authState.expiresAt > Date.now() + 30000) return Promise.resolve(authState.token)
  if (!authState.token && restoreAttempted) return Promise.resolve('')
  if (!refreshPromise) {
    const version = generation
    refreshPromise = withRefreshLock(async () => {
      if (closing || generation !== version) return ''
      const { data } = await cookiePost('/user/refresh')
      if (generation !== version || closing) return ''
      return accept(data.data)
    }).catch(error => {
      if (error.response?.status === 401 && generation === version) { clearSession(); return '' }
      // 503/network errors do not erase a valid session or trigger a login redirect.
      throw error
    }).finally(() => { refreshPromise = undefined })
  }
  return refreshPromise
}
export async function logout() {
  closing = true
  try {
    if (refreshPromise) await refreshPromise.catch(() => {})
    await withRefreshLock(() => cookiePost('/user/web-logout'))
    clearSession()
  } finally { closing = false }
}
export async function authenticatedFetch(url, options = {}) {
  const token = await ensureSession()
  const send = access => fetch(url, {
    ...options, headers: { ...options.headers, ...(access ? { Authorization: `Bearer ${access}` } : {}) }
  })
  let response = await send(token)
  if (response.status === 401) {
    const renewed = await ensureSession(true, token)
    if (renewed) response = await send(renewed)
  }
  return response
}
