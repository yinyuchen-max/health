const { get, put } = require('../../utils/request')

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

  async loadGoals() {
    const app = getApp()
    const userId = app.globalData.userInfo?.id
    try {
      const stored = wx.getStorageSync(`goals_${userId}`)
      if (stored) this.setData({ goals: stored })
    } catch (err) { console.error('加载本地目标失败:', err) }
    try {
      const remote = await get('/api/lifestyle/goals', {}, { showLoading: false, showError: false })
      if (remote && remote.saved) {
        const goals = { steps: remote.steps, exerciseMinutes: remote.exerciseMinutes, waterMl: remote.waterMl, sleepHours: remote.sleepHours, calories: remote.calories }
        this.setData({ goals })
        wx.setStorageSync(`goals_${userId}`, goals)
      }
    } catch (err) { console.error('加载服务器目标失败:', err) }
  },

  onGoalChange(e) {
    const field = e.currentTarget.dataset.field
    this.setData({ [`goals.${field}`]: e.detail.value })
  },

  async saveGoals() {
    const goals = this.data.goals
    const payload = {
      steps: Number(goals.steps), exerciseMinutes: Number(goals.exerciseMinutes),
      waterMl: Number(goals.waterMl), sleepHours: Number(goals.sleepHours), calories: Number(goals.calories)
    }
    try {
      await put('/api/lifestyle/goals', payload, { showLoading: true, showError: false })
      const app = getApp()
      wx.setStorageSync(`goals_${app.globalData.userInfo?.id}`, payload)
      this.setData({ goals: payload })
      wx.showToast({ title: '已保存，AI 会用到', icon: 'success' })
    } catch (err) {
      wx.showToast({ title: err.message || '保存失败', icon: 'none' })
    }
  }
})
