const { logout } = require('../../utils/auth')
const { get } = require('../../utils/request')

Page({
  data: {
    userInfo: {},
    userInitial: '',
    roleLabel: '',
    familyNoticeCount: 0
  },

  onShow() {
    const app = getApp()
    if (!app.checkLogin()) return

    const userInfo = app.globalData.userInfo || {}
    const username = userInfo.username || '用户'
    const userInitial = username.slice(0, 1).toUpperCase()
    const roleMap = { admin: '管理员', user: '普通用户', doctor: '医生' }
    const roleLabel = roleMap[userInfo.role] || '普通用户'

    this.setData({ userInfo, userInitial, roleLabel })
    this.loadFamilyNotices()
  },

  async loadFamilyNotices() {
    try {
      const [members, reminders] = await Promise.all([
        get('/api/family/members', {}, { showLoading: false, showError: false }),
        get('/api/family/reminders', {}, { showLoading: false, showError: false })
      ])
      const familyNoticeCount = members.filter(m => m.status === 'PENDING' && m.incoming).length
        + reminders.filter(r => !r.readStatus).length
      this.setData({ familyNoticeCount })
    } catch { this.setData({ familyNoticeCount: 0 }) }
  },

  goFamily() {
    wx.navigateTo({ url: '/pages/family/family' })
  },

  goHealthRecord() {
    wx.navigateTo({ url: '/pages/health-record/health-record' })
  },

  goSportRecord() {
    wx.navigateTo({ url: '/pages/sport-record/sport-record' })
  },

  goDietRecord() {
    wx.navigateTo({ url: '/pages/diet-record/diet-record' })
  },

  goSleepRecord() {
    wx.navigateTo({ url: '/pages/sleep-record/sleep-record' })
  },

  goHistoryRecord() {
    wx.navigateTo({ url: '/pages/history-record/history-record' })
  },

  goGoalSetting() {
    wx.navigateTo({ url: '/pages/goal-setting/goal-setting' })
  },

  goProfile() {
    wx.navigateTo({ url: '/pages/user-profile/user-profile' })
  },

  goAiSettings() { wx.navigateTo({ url: '/pages/ai-settings/ai-settings' }) },

  goReminder() {
    wx.navigateTo({ url: '/pages/reminder/reminder' })
  },

  // 订阅消息授权
  subscribeMessage() {
    wx.requestSubscribeMessage({
      tmplIds: ['YOUR_TEMPLATE_ID'],
      success(res) {
        console.log('订阅结果:', res)
        wx.showToast({ title: '授权成功', icon: 'success' })
      },
      fail(err) {
        console.error('订阅失败:', err)
        wx.showToast({ title: '授权失败', icon: 'none' })
      }
    })
  },

  handleLogout() {
    wx.showModal({
      title: '确认退出',
      content: '确定要退出登录吗？',
      success: (res) => {
        if (res.confirm) {
          logout()
        }
      }
    })
  }
})
