Page({
  data: {
    selectedDate: '',
    bedTime: '23:00',
    wakeTime: '07:00',
    sleepHours: 7,
    sleepMinutes: 20,
    sleepQuality: '良好',
    qualityOptions: ['优秀', '良好', '一般', '较差'],
    deepHours: 2,
    deepMinutes: 10,
    lightHours: 4,
    lightMinutes: 20,
    remHours: 0,
    remMinutes: 20,
    deepPercent: 30,
    lightPercent: 55,
    remPercent: 15
  },

  onLoad() {
    const today = new Date().toISOString().slice(0, 10)
    this.setData({ selectedDate: today })
    this.loadSleepData()
  },

  onDateChange(e) {
    this.setData({ selectedDate: e.detail.value })
    this.loadSleepData()
  },

  loadSleepData() {
    try {
      const app = getApp()
      const userId = app.globalData.userInfo?.id
      const key = `sleep_${userId}_${this.data.selectedDate}`
      const stored = wx.getStorageSync(key)
      if (stored) {
        this.setData(stored)
      }
    } catch (err) {
      console.error('加载睡眠数据失败:', err)
    }
  },

  onBedTimeChange(e) {
    this.setData({ bedTime: e.detail.value })
    this.calcSleepDuration()
  },

  onWakeTimeChange(e) {
    this.setData({ wakeTime: e.detail.value })
    this.calcSleepDuration()
  },

  calcSleepDuration() {
    const { bedTime, wakeTime } = this.data
    const [bH, bM] = bedTime.split(':').map(Number)
    const [wH, wM] = wakeTime.split(':').map(Number)
    let totalMin = (wH * 60 + wM) - (bH * 60 + bM)
    if (totalMin < 0) totalMin += 24 * 60
    const hours = Math.floor(totalMin / 60)
    const minutes = totalMin % 60
    this.setData({ sleepHours: hours, sleepMinutes: minutes })
  },

  selectQuality(e) {
    this.setData({ sleepQuality: e.currentTarget.dataset.quality })
  },

  saveSleep() {
    try {
      const app = getApp()
      const userId = app.globalData.userInfo?.id
      const key = `sleep_${userId}_${this.data.selectedDate}`
      wx.setStorageSync(key, this.data)
      wx.showToast({ title: '保存成功', icon: 'success' })
    } catch (err) {
      wx.showToast({ title: '保存失败', icon: 'none' })
    }
  }
})
