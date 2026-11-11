const { get, post } = require('../../utils/request')
const { syncWeRun } = require('../../utils/wechat-data')

Page({
  data: {
    username: '',
    greeting: '',
    todaySteps: '--',
    todayCalories: 0,
    todaySportMinutes: 0,
    stepsPercent: 0,
    caloriesPercent: 0,
    sportPercent: 0,
    overview: {},
    quickTips: [],
    priorityRisks: [],
    latestBp: '--',
    weeklySportMinutes: 0,
    riskLabels: {
      BMI: 'BMI 风险',
      BLOOD_PRESSURE: '血压风险',
      DIABETES: '血糖风险',
      CARDIO: '心血管风险'
    }
  },

  onLoad() {
    this.initGreeting()
  },

  onShow() {
    const app = getApp()
    if (!app.checkLogin()) return
    this.setData({ username: app.globalData.userInfo?.nickname || app.globalData.userInfo?.username || '用户' })
    this.loadWeRunData()
    this.loadData()
  },

  initGreeting() {
    const hour = new Date().getHours()
    let greeting = '晚上好'
    if (hour < 12) greeting = '早上好'
    else if (hour < 18) greeting = '下午好'
    this.setData({ greeting })
  },

  // 展示服务端保存的数据；已授权时顺便同步，未授权时保留历史快照。
  async loadWeRunData() {
    try {
      const data = await syncWeRun()
      const count = data.todaySteps
      this.setData({ todaySteps: count == null ? '--' : count, stepsPercent: Math.min(Math.round((count || 0) / 10000 * 100), 100) })
    } catch (error) {
      this.setData({ todaySteps: '--', stepsPercent: 0 })
    }
  },

  async loadData() {
    try {
      const app = getApp()
      const userId = app.globalData.userInfo?.id
      if (!userId) return

      const [overview, healthRes, sportRes] = await Promise.all([
        get(`/api/smart-health/overview?userId=${userId}`, {}, { showLoading: false }).catch(() => ({})),
        get(`/api/health/records/${userId}?pageNum=1&pageSize=10`, {}, { showLoading: false }).catch(() => ({ records: [] })),
        get(`/api/sport/records/${userId}?pageNum=1&pageSize=50`, {}, { showLoading: false }).catch(() => ({ records: [] }))
      ])

      const healthRecords = healthRes.records || []
      const sportRecords = sportRes.records || []

      // 最新血压
      const latestRecord = healthRecords
        .filter(r => r.recordDate)
        .sort((a, b) => new Date(b.recordDate) - new Date(a.recordDate))[0]
      const latestBp = latestRecord?.bloodPressureSystolic
        ? `${latestRecord.bloodPressureSystolic}/${latestRecord.bloodPressureDiastolic}`
        : '--'

      // 今日运动时长
      const today = new Date().toISOString().slice(0, 10)
      const todaySport = sportRecords
        .filter(r => r.recordDate === today)
        .reduce((sum, r) => sum + Number(r.duration || 0), 0)

      // 今日消耗热量
      const todayCalories = sportRecords
        .filter(r => r.recordDate === today)
        .reduce((sum, r) => sum + Number(r.calories || 0), 0)

      // 近 7 天运动时长
      const threshold = new Date()
      threshold.setDate(threshold.getDate() - 6)
      const weeklySportMinutes = sportRecords
        .filter(r => r.recordDate && new Date(r.recordDate) >= threshold)
        .reduce((sum, r) => sum + Number(r.duration || 0), 0)

      // 风险排序
      const priorityRisks = [...(overview.riskAssessments || [])]
        .sort((a, b) => Number(b.riskScore || 0) - Number(a.riskScore || 0))
        .slice(0, 3)

      const sportPercent = Math.min(Math.round(todaySport / 60 * 100), 100)
      const caloriesPercent = Math.min(Math.round(todayCalories / 600 * 100), 100)

      this.setData({
        overview,
        quickTips: overview.quickTips || [],
        priorityRisks,
        latestBp,
        weeklySportMinutes,
        todaySportMinutes: todaySport,
        todayCalories,
        sportPercent,
        caloriesPercent
      })
    } catch (err) {
      console.error('加载数据失败:', err)
    }
  },

  levelText(level) {
    const map = { low: '低', medium: '中', high: '高' }
    return map[level] || level
  },

  // 导航
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

  goAiChat() {
    wx.navigateTo({ url: '/pages/ai-chat/ai-chat' })
  },

  goSmartHealth() {
    wx.navigateTo({ url: '/pages/smart-health/smart-health' })
  },

  goBmiCalculator() {
    wx.navigateTo({ url: '/pages/bmi-calculator/bmi-calculator' })
  },

  goGoalSetting() {
    wx.navigateTo({ url: '/pages/goal-setting/goal-setting' })
  },

  goReminder() {
    wx.navigateTo({ url: '/pages/reminder/reminder' })
  },

  goDoctorList() {
    wx.navigateTo({ url: '/pages/doctor-list/doctor-list' })
  },

  goRecords() {
    wx.switchTab({ url: '/pages/records/records' })
  },

  goProfile() {
    wx.navigateTo({ url: '/pages/user-profile/user-profile' })
  }
})
