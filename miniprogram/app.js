// 小程序全局入口
App({
  globalData: {
    userInfo: null,
    token: '',
    // 后端地址，开发时填本地，上线时改为备案域名
    baseUrl: 'http://localhost:8080'
  },

  onLaunch() {
    // 启动时恢复登录态
    const token = wx.getStorageSync('token')
    const userInfo = wx.getStorageSync('userInfo')
    if (token) {
      this.globalData.token = token
      this.globalData.userInfo = userInfo ? JSON.parse(userInfo) : null
    }
  },

  // 设置登录态
  setLogin(token, userInfo) {
    this.globalData.token = token
    this.globalData.userInfo = userInfo
    wx.setStorageSync('token', token)
    wx.setStorageSync('userInfo', JSON.stringify(userInfo))
  },

  // 清除登录态
  clearLogin() {
    this.globalData.token = ''
    this.globalData.userInfo = null
    wx.removeStorageSync('token')
    wx.removeStorageSync('userInfo')
  },

  // 检查是否已登录，未登录则跳转登录页
  checkLogin() {
    if (!this.globalData.token) {
      wx.redirectTo({ url: '/pages/login/login' })
      return false
    }
    return true
  }
})
