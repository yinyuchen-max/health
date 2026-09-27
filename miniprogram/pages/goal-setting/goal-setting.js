Page({
  data: {
    goals: {
      steps: 10000,
      exerciseMinutes: 60,
      waterMl: 2000,
      sleepHours: 8,
      calories: 1800
    }
  },

  onLoad() {
    this.loadGoals()
  },

  loadGoals() {
    try {
      const app = getApp()
      const userId = app.globalData.userInfo?.id
      const stored = wx.getStorageSync(`goals_${userId}`)
      if (stored) {
        this.setData({ goals: stored })
      }
    } catch (err) {
      console.error('加载目标失败:', err)
    }
  },

  onGoalChange(e) {
    const field = e.currentTarget.dataset.field
    this.setData({ [`goals.${field}`]: e.detail.value })
  },

  saveGoals() {
    try {
      const app = getApp()
      const userId = app.globalData.userInfo?.id
      wx.setStorageSync(`goals_${userId}`, this.data.goals)
      wx.showToast({ title: '保存成功', icon: 'success' })
    } catch (err) {
      wx.showToast({ title: '保存失败', icon: 'none' })
    }
  }
})
