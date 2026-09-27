const { get } = require('../../utils/request')

Page({
  data: {
    currentMonth: '',
    weekDays: ['一', '二', '三', '四', '五', '六', '日'],
    daysInMonth: [],
    today: 0,
    selectedDay: 0,
    // 今日数据
    dietCount: 0,
    dietCalories: 0,
    sportMinutes: 0,
    sportCalories: 0,
    sleepHours: 0,
    sleepMinutes: 0,
    sleepQuality: '良好',
    latestWeight: '',
    latestBmi: '',
    showAddMenu: false
  },

  onShow() {
    this.initCalendar()
    this.loadTodayData()
  },

  initCalendar() {
    const now = new Date()
    const year = now.getFullYear()
    const month = now.getMonth() + 1
    const today = now.getDate()
    const daysInMonth = new Date(year, month, 0).getDate()

    const days = []
    for (let i = 1; i <= daysInMonth; i++) {
      days.push(i)
    }

    this.setData({
      currentMonth: `${year}年${month}月`,
      daysInMonth: days,
      today: today,
      selectedDay: today
    })
  },

  selectDay(e) {
    const day = e.currentTarget.dataset.day
    this.setData({ selectedDay: day })
  },

  async loadTodayData() {
    try {
      const app = getApp()
      const userId = app.globalData.userInfo?.id
      if (!userId) return

      const today = new Date().toISOString().slice(0, 10)

      // 并行加载各类数据
      const [healthRes, sportRes] = await Promise.all([
        get(`/api/health/records/${userId}?pageNum=1&pageSize=100`, {}, { showLoading: false }).catch(() => ({ records: [] })),
        get(`/api/sport/records/${userId}?pageNum=1&pageSize=100`, {}, { showLoading: false }).catch(() => ({ records: [] }))
      ])

      const healthRecords = healthRes.records || []
      const sportRecords = sportRes.records || []

      // 今日饮食（用健康记录中的体重数据估算）
      const todayHealth = healthRecords.filter(r => r.recordDate === today)
      const latestWeight = todayHealth.length > 0 ? todayHealth[todayHealth.length - 1].weight : ''

      // 今日运动
      const todaySport = sportRecords.filter(r => r.recordDate === today)
      const sportMinutes = todaySport.reduce((sum, r) => sum + Number(r.duration || 0), 0)
      const sportCalories = todaySport.reduce((sum, r) => sum + Number(r.calories || 0), 0)

      // BMI
      let latestBmi = ''
      if (latestWeight) {
        const overview = await get(`/api/smart-health/overview?userId=${userId}`, {}, { showLoading: false }).catch(() => ({}))
        latestBmi = overview.bmi || ''
      }

      this.setData({
        dietCount: 0,
        dietCalories: 0,
        sportMinutes,
        sportCalories,
        latestWeight: latestWeight || '--',
        latestBmi: latestBmi || '--'
      })
    } catch (err) {
      console.error('加载今日数据失败:', err)
    }
  },

  showAddMenu() {
    this.setData({ showAddMenu: true })
  },

  hideAddMenu() {
    this.setData({ showAddMenu: false })
  },

  goDietRecord() {
    this.setData({ showAddMenu: false })
    wx.navigateTo({ url: '/pages/diet-record/diet-record' })
  },

  goSportRecord() {
    this.setData({ showAddMenu: false })
    wx.navigateTo({ url: '/pages/sport-record/sport-record' })
  },

  goSleepRecord() {
    this.setData({ showAddMenu: false })
    wx.navigateTo({ url: '/pages/sleep-record/sleep-record' })
  },

  goHealthRecord() {
    this.setData({ showAddMenu: false })
    wx.navigateTo({ url: '/pages/health-record/health-record' })
  }
})
