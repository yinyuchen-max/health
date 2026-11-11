const { get, put } = require('../../utils/request')
const { sleepMinutes } = require('../../utils/ai-assistant')

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

  async loadSleepData() {
    try {
      const app = getApp()
      const userId = app.globalData.userInfo?.id
      const key = `sleep_${userId}_${this.data.selectedDate}`
      const stored = wx.getStorageSync(key)
      if (stored) this.setData(stored)
      const rows = await get('/api/lifestyle/sleep', { limit: 14 }, { showLoading: false, showError: false })
      const match = (rows || []).find(item => item.date === this.data.selectedDate)
      if (match) {
        const patch = this.fromServer(match)
        this.setData(patch)
        wx.setStorageSync(key, { ...this.data, ...patch })
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

  fromServer(match) {
    const split = (total) => ({ hours: Math.floor((total || 0) / 60), minutes: (total || 0) % 60 })
    const sleep = split(match.sleepMinutes)
    const deep = split(match.deepMinutes)
    const light = split(match.lightMinutes)
    const rem = split(match.remMinutes)
    return {
      bedTime: match.bedTime, wakeTime: match.wakeTime, sleepQuality: match.quality,
      sleepHours: sleep.hours, sleepMinutes: sleep.minutes,
      deepHours: deep.hours, deepMinutes: deep.minutes,
      lightHours: light.hours, lightMinutes: light.minutes,
      remHours: rem.hours, remMinutes: rem.minutes
    }
  },

  async saveSleep() {
    const data = this.data
    const minutes = sleepMinutes(data.bedTime, data.wakeTime)
    const stage = (hours, mins) => Number(hours || 0) * 60 + Number(mins || 0)
    let deepMinutes = stage(data.deepHours, data.deepMinutes)
    let lightMinutes = stage(data.lightHours, data.lightMinutes)
    let remMinutes = stage(data.remHours, data.remMinutes)
    if (deepMinutes + lightMinutes + remMinutes > minutes) {
      deepMinutes = null
      lightMinutes = null
      remMinutes = null
    }
    try {
      await put('/api/lifestyle/sleep', {
        date: data.selectedDate, bedTime: data.bedTime, wakeTime: data.wakeTime,
        sleepMinutes: minutes, quality: data.sleepQuality, deepMinutes, lightMinutes, remMinutes
      }, { showLoading: true, showError: false })
      const app = getApp()
      wx.setStorageSync(`sleep_${app.globalData.userInfo?.id}_${data.selectedDate}`, data)
      wx.showToast({ title: '已保存，AI 会用到', icon: 'success' })
    } catch (err) {
      wx.showToast({ title: err.message || '保存失败', icon: 'none' })
    }
  }
})
