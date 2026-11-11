const { wxLogin } = require('../../utils/auth')
const { post } = require('../../utils/request')
const { syncWeRun, saveWechatProfile } = require('../../utils/wechat-data')

Page({
  data: { valid: false, loading: false, done: false, cancelled: false, nickname: '', avatarPath: '', syncSteps: true, message: '' },
  onLoad(options) {
    let ticket = ''
    try { ticket = decodeURIComponent(options.scene || '') } catch { }
    this.ticket = /^[a-f0-9]{32}$/.test(ticket) ? ticket : ''
    this.setData({ valid: !!this.ticket })
  },
  onNickname(e) { this.setData({ nickname: e.detail.value }) },
  onChooseAvatar(e) { this.setData({ avatarPath: e.detail.avatarUrl }) },
  onSyncChange(e) { this.setData({ syncSteps: e.detail.value }) },
  async confirm() {
    if (!this.ticket || this.data.loading || this.data.done || this.data.cancelled) return
    this.setData({ loading: true, message: '' })
    let syncMessage = ''
    try {
      // 必须由用户点击确认后才登录，不因扫码进入页面而自动授权网页。
      await wxLogin()
      if (this.data.nickname.trim() || this.data.avatarPath) await saveWechatProfile(this.data.nickname, this.data.avatarPath)
      if (this.data.syncSteps) {
        try { await syncWeRun({ requestPermission: true }); syncMessage = '微信运动已同步。' }
        catch (error) { syncMessage = `${error.message || '步数暂未同步'}，可在运动记录页重试。` }
      }
      await post('/api/user/wechat/web-confirm', { ticket: this.ticket }, { showLoading: false, showError: false })
      this.setData({ done: true, message: `已确认网页登录。${syncMessage}` })
    } catch (error) {
      this.setData({ message: error.message || '确认失败，请在网页刷新二维码后重试' })
    } finally { this.setData({ loading: false }) }
  },
  cancel() { if (!this.data.loading) this.setData({ cancelled: true, message: '已取消，本次未授权网页登录。' }) },
  goHome() { wx.switchTab({ url: '/pages/dashboard/dashboard' }) }
})
