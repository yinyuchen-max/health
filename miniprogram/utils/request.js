/**
 * 小程序请求封装（对标前端 axios 拦截器）
 * 功能：
 *  1. 自动携带 JWT Token
 *  2. 401 自动跳转登录页
 *  3. 统一错误处理
 *  4. 支持流式请求（enableChunked，用于 AI 聊天）
 */

const app = getApp()
const runtime = require('../config/runtime')

/**
 * 通用请求
 * @param {Object} options - { url, method, data, header, showLoading, showError }
 * @returns {Promise}
 */
async function request(options = {}) {
  const {
    url,
    method = 'GET',
    data = {},
    header = {},
    showLoading = true,
    showError = true,
    timeout = runtime.requestTimeoutMs
  } = options

  // 拼接完整 URL
  const fullUrl = url.startsWith('http') ? url : `${app.globalData.baseUrl}${url}`

  // 默认请求头
  const defaultHeader = {
    'Content-Type': 'application/json'
  }

  // 携带 JWT
  const nativeAuth = ['/api/user/wechat-login', '/api/user/native-login', '/api/user/native-refresh', '/api/user/native-logout'].includes(url)
  const token = !nativeAuth && app.ensureAccess ? await app.ensureAccess() : app.globalData.token
  if (token) {
    defaultHeader['Authorization'] = `Bearer ${token}`
  }

  if (showLoading) {
    wx.showLoading({ title: '加载中...', mask: true })
  }

  return new Promise((resolve, reject) => {
    wx.request({
      url: fullUrl,
      method,
      data,
      header: { ...defaultHeader, ...header },
      timeout,
      async success(res) {
        if (showLoading) wx.hideLoading()

        const { statusCode, data: resData } = res

        // 401 未登录，跳转登录页
        if (statusCode === 401) {
          if (!nativeAuth && !options._authRetried && app.ensureAccess) {
            try {
              const renewed = await app.ensureAccess(true, token)
              if (renewed) { resolve(await request({ ...options, _authRetried: true })); return }
            } catch (error) { reject(error); return }
          }
          app.clearLogin()
          wx.redirectTo({ url: '/pages/login/login' })
          reject({ message: '登录已过期，请重新登录', status: 401 })
          return
        }

        // 业务层判断（后端统一返回 { code, message, data }）
        if (resData && resData.code !== undefined) {
          if (resData.code === 200 || resData.code === 0) {
            resolve(resData.data !== undefined ? resData.data : resData)
          } else {
            const errMsg = resData.message || '请求失败'
            if (showError) wx.showToast({ title: errMsg, icon: 'none' })
            reject({ message: errMsg, status: statusCode, data: resData })
          }
        } else {
          // 非标准响应，直接返回
          resolve(resData)
        }
      },
      fail(err) {
        if (showLoading) wx.hideLoading()
        const errMsg = err.errMsg || '网络请求失败'
        if (showError) wx.showToast({ title: errMsg, icon: 'none' })
        reject({ message: errMsg, originalError: err })
      }
    })
  })
}

/**
 * 流式请求（用于 AI 聊天）
 * 小程序通过 enableChunked 接收分块数据
 * @param {Object} options - { url, data, onChunk, onDone, onError }
 */
function streamRequest(options = {}) {
  const { url, data = {}, onChunk, onDone, onError } = options
  const fullUrl = url.startsWith('http') ? url : `${app.globalData.baseUrl}${url}`
  let task
  let cancelled = false
  let buffer = ''
  const send = (token, retried = false) => {
    if (cancelled) return
    task = wx.request({
      url: fullUrl, method: 'POST', data,
      header: { 'Content-Type': 'application/json', ...(token ? { Authorization: `Bearer ${token}` } : {}) },
      enableChunked: true, timeout: runtime.streamTimeoutMs,
      async success(res) {
        if (cancelled) return
        if (res.statusCode === 401 && !retried && app.ensureAccess) {
          try {
            const renewed = await app.ensureAccess(true, token)
            if (renewed) { buffer = ''; send(renewed, true); return }
          } catch (error) { onError?.(error); return }
        }
        if (res.statusCode >= 400) { onError?.({ message: res.statusCode === 401 ? '请重新登录' : res.statusCode === 402 ? '请前往 AI 服务页面开通或续费' : '请求失败', status: res.statusCode }); return }
        onDone?.()
      },
      fail(error) { if (!cancelled) onError?.(error) }
    })
    task.onChunkReceived(res => {
      if (cancelled) return
      buffer += arrayBufferToString(res.data)
      // Deliver only SSE frames, never authentication-error JSON.
      let end
      while ((end = buffer.indexOf('\n\n')) !== -1) {
        const frame = buffer.slice(0, end + 2)
        buffer = buffer.slice(end + 2)
        if (/^(event:|data:|:)/m.test(frame)) onChunk?.(frame)
      }
    })
  }
  if (app.ensureAccess) app.ensureAccess().then(token => send(token)).catch(error => onError?.(error))
  else send(app.globalData.token)
  return { abort() { cancelled = true; task?.abort() } }
}

/**
 * ArrayBuffer 转 UTF-8 字符串
 */
function arrayBufferToString(buffer) {
  const uint8Array = new Uint8Array(buffer)
  let result = ''
  // 使用 TextDecoder 解码 UTF-8
  if (typeof TextDecoder !== 'undefined') {
    const decoder = new TextDecoder('utf-8')
    result = decoder.decode(uint8Array)
  } else {
    // 降级：手动解码 UTF-8
    for (let i = 0; i < uint8Array.length; i++) {
      result += String.fromCharCode(uint8Array[i])
    }
    try {
      result = decodeURIComponent(escape(result))
    } catch (e) {
      // 忽略解码错误
    }
  }
  return result
}

// 便捷方法
const get = (url, data, options = {}) => request({ url, method: 'GET', data, ...options })
const post = (url, data, options = {}) => request({ url, method: 'POST', data, ...options })
const put = (url, data, options = {}) => request({ url, method: 'PUT', data, ...options })
const del = (url, data, options = {}) => request({ url, method: 'DELETE', data, ...options })

module.exports = {
  request,
  streamRequest,
  get,
  post,
  put,
  del
}
