const { wxLogin } = require('../../utils/auth')

Page({
  data: {
    wxLoading: false
  },

  // 微信一键登录
  async handleWxLogin() {
    this.setData({ wxLoading: true })
    try {
      await wxLogin()
      wx.switchTab({ url: '/pages/dashboard/dashboard' })
    } catch (err) {
      console.error('微信登录失败:', err)
      wx.showToast({ title: err.message || '微信登录失败', icon: 'none' })
    } finally {
      this.setData({ wxLoading: false })
    }
  }
})
