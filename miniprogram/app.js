const { resolveBaseUrl } = require('./config/environment')
const runtime = require('./config/runtime')
let refreshPromise
let generation = 0

App({
  globalData: { userInfo: null, token: '', refreshToken: '', expiresAt: 0, baseUrl: resolveBaseUrl(wx) },
  onLaunch() {
    const session = wx.getStorageSync('authSession')
    // Old standalone JWTs cannot be restored after the authentication upgrade.
    wx.removeStorageSync('token')
    if (session?.refreshToken && session.refreshExpiresAt > Date.now()) {
      Object.assign(this.globalData, session)
      const userInfo = wx.getStorageSync('userInfo')
      try { this.globalData.userInfo = userInfo ? JSON.parse(userInfo) : null } catch { this.globalData.userInfo = null }
    } else this.clearLogin()
  },
  setLogin(tokens, userInfo) {
    if (!tokens?.token || !tokens.refreshToken || !(tokens.expiresIn > 0)) throw new Error('登录响应异常')
    const session = { token: tokens.token, refreshToken: tokens.refreshToken,
      expiresAt: Date.now() + tokens.expiresIn * 1000, refreshExpiresAt: tokens.refreshExpiresAt }
    Object.assign(this.globalData, session)
    wx.setStorageSync('authSession', session)
    if (userInfo !== undefined) {
      generation++
      this.globalData.userInfo = userInfo
      wx.setStorageSync('userInfo', JSON.stringify(userInfo))
    }
  },
  clearLogin() {
    generation++
    this.globalData.token = ''; this.globalData.refreshToken = ''; this.globalData.expiresAt = 0
    this.globalData.userInfo = null
    wx.removeStorageSync('authSession'); wx.removeStorageSync('token'); wx.removeStorageSync('userInfo')
  },
  ensureAccess(force = false, failedToken = '') {
    if (refreshPromise) return refreshPromise
    if (failedToken && this.globalData.token && failedToken !== this.globalData.token) return Promise.resolve(this.globalData.token)
    if (!force && this.globalData.token && this.globalData.expiresAt > Date.now() + 30000) return Promise.resolve(this.globalData.token)
    if (!this.globalData.refreshToken) return Promise.resolve('')
    if (!refreshPromise) {
      const version = generation
      refreshPromise = new Promise((resolve, reject) => {
        wx.request({
          url: `${this.globalData.baseUrl}/api/user/native-refresh`, method: 'POST',
          data: { refreshToken: this.globalData.refreshToken }, header: { 'Content-Type': 'application/json' },
          timeout: runtime.requestTimeoutMs,
          success: res => {
            if (version !== generation) { resolve(''); return }
            if (res.statusCode === 200 && res.data?.code === 200) {
              this.setLogin(res.data.data); resolve(this.globalData.token)
            } else if (res.statusCode === 401) { this.clearLogin(); resolve('') }
            else reject({ status: res.statusCode, message: '认证服务暂时不可用，请稍后重试' })
          }, fail: () => reject({ message: '网络请求失败，请稍后重试' })
        })
      }).finally(() => { refreshPromise = undefined })
    }
    return refreshPromise
  },
  checkLogin() {
    if (!this.globalData.refreshToken) { wx.redirectTo({ url: '/pages/login/login' }); return false }
    return true
  }
})
