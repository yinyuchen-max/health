const { get, post } = require('../../utils/request')

Page({
  data: {
    username: '',
    greeting: '',
    todaySteps: 0,
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
    this.setData({ username: app.globalData.userInfo?.username || '用户' })
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

  // 获取微信运动步数
  async loadWeRunData() {
    try {
      // 先请求微信运动授权
      const authRes = await new Promise((resolve, reject) => {
        wx.getSetting({
          success(res) {
            if (res.authSetting['scope.werun']) {
              resolve(true)
            } else {
              wx.authorize({
                scope: 'scope.werun',
                success: () => resolve(true),
                fail: () => resolve(false)
              })
            }
          },
          fail: () => resolve(false)
        })
      })

      if (!authRes) {
        // 未授权，使用默认值
        this.setData({
          todaySteps: 0,
          stepsPercent: 0
        })
        return
      }

      // 获取微信运动加密数据
      const runRes = await new Promise((resolve, reject) => {
        wx.getWeRunData({
          success: resolve,
          fail: reject
        })
      })

      // 将加密数据发送到后端解密获取步数
      // 统一走 request 封装：自动拼 baseUrl、携带 JWT、401 跳登录
      // 注意后端真实路径为 /api/user/wechat/decrypt-run-data（UserController 类级 @RequestMapping("/api/user")）
      const app = getApp()
      const token = app.globalData.token
      if (token && runRes.encryptedData) {
        const data = await post('/api/user/wechat/decrypt-run-data', {
          encryptedData: runRes.encryptedData,
          iv: runRes.iv
        }, { showLoading: false, showError: false })

        const steps = data && data.stepInfoList
        if (steps && steps.length > 0) {
          const today = new Date().toISOString().slice(0, 10)
          // timestamp 为秒级 Unix 时间戳，需换算成日期字符串后再与 today 比较
          const todayData = steps.find(s => new Date(s.timestamp * 1000).toISOString().slice(0, 10) === today)
          const stepCount = todayData ? todayData.step : (steps[steps.length - 1].step || 0)
          const stepsPercent = Math.min(Math.round(stepCount / 10000 * 100), 100)
          this.setData({
            todaySteps: stepCount,
            stepsPercent
          })
        }
      }
    } catch (err) {
      console.log('微信运动数据获取失败:', err)
      // 失败时使用默认值
      this.setData({ todaySteps: 0, stepsPercent: 0 })
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
