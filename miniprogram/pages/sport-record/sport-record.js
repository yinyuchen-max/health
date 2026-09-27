const { get, post, put, del } = require('../../utils/request')

// 运动类型配置（带emoji）
const SPORT_TYPES = [
  { name: '跑步', emoji: '' },
  { name: '游泳', emoji: '🏊' },
  { name: '骑行', emoji: '🚴' },
  { name: '健身', emoji: '' },
  { name: '瑜伽', emoji: '🧘' },
  { name: '篮球', emoji: '🏀' },
  { name: '足球', emoji: '' },
  { name: '跳绳', emoji: '🪢' },
  { name: '爬山', emoji: '⛰️' },
  { name: '散步', emoji: '🚶' },
  { name: '舞蹈', emoji: '💃' },
  { name: '其他', emoji: '' }
]

Page({
  data: {
    records: [],
    showForm: false,
    editingId: null,
    saving: false,
    // 微信运动数据
    todaySteps: 0,
    todayCalories: 0,
    todayDistance: 0,
    todaySportMinutes: 0,
    // 快速运动
    quickSports: [
      { name: '跑步', emoji: '🏃', duration: 30, calories: 300 },
      { name: '散步', emoji: '🚶', duration: 20, calories: 80 },
      { name: '骑行', emoji: '🚴', duration: 30, calories: 250 },
      { name: '健身', emoji: '💪', duration: 45, calories: 350 },
      { name: '瑜伽', emoji: '🧘', duration: 30, calories: 120 },
      { name: '跳绳', emoji: '🪢', duration: 15, calories: 200 },
      { name: '游泳', emoji: '🏊', duration: 30, calories: 300 },
      { name: '爬山', emoji: '⛰️', duration: 60, calories: 400 }
    ],
    sportTypeIndex: 0,
    sportTypeOptions: SPORT_TYPES,
    intensityIndex: 1,
    intensityOptions: ['低', '中', '高'],
    form: {
      sportType: '跑步',
      duration: 30,
      calories: '',
      intensity: '中',
      recordDate: '',
      notes: ''
    }
  },

  onShow() {
    this.loadWeRunData()
    this.loadRecords()
  },

  // 获取微信运动数据
  async loadWeRunData() {
    try {
      const authRes = await new Promise(resolve => {
        wx.getSetting({
          success(res) {
            if (res.authSetting['scope.werun']) {
              resolve(true)
            } else {
              wx.authorize({ scope: 'scope.werun', success: () => resolve(true), fail: () => resolve(false) })
            }
          },
          fail: () => resolve(false)
        })
      })

      if (!authRes) return

      const runRes = await new Promise((resolve, reject) => {
        wx.getWeRunData({ success: resolve, fail: reject })
      })

      const app = getApp()
      const token = app.globalData.token
      if (token && runRes.encryptedData) {
        // 统一走 request 封装（自动携带 JWT、401 跳登录）
        // 注意后端真实路径为 /api/user/wechat/decrypt-run-data（UserController 类级 @RequestMapping("/api/user")）
        const data = await post('/api/user/wechat/decrypt-run-data', {
          encryptedData: runRes.encryptedData,
          iv: runRes.iv
        }, { showLoading: false, showError: false })

        if (data && data.stepInfoList) {
          const steps = data.stepInfoList
          const lastStep = steps[steps.length - 1]?.step || 0
          const distance = (lastStep * 0.7 / 1000).toFixed(1) // 步数转公里
          const calories = Math.round(lastStep * 0.04) // 粗略估算

          // 获取今日运动时长
          const userId = app.globalData.userInfo?.id
          const today = new Date().toISOString().slice(0, 10)
          const sportRes = await get(`/api/sport/records/${userId}?pageNum=1&pageSize=100`, {}, { showLoading: false }).catch(() => ({ records: [] }))
          const todaySport = (sportRes.records || []).filter(r => r.recordDate === today)
          const todaySportMinutes = todaySport.reduce((sum, r) => sum + Number(r.duration || 0), 0)

          this.setData({
            todaySteps: lastStep,
            todayCalories: calories,
            todayDistance: distance,
            todaySportMinutes
          })
        }
      }
    } catch (err) {
      console.log('微信运动数据获取失败:', err)
    }
  },

  // 同步微信运动
  syncWeRun() {
    this.loadWeRunData()
    wx.showToast({ title: '同步中...', icon: 'loading' })
  },

  async loadRecords() {
    try {
      const app = getApp()
      const userId = app.globalData.userInfo?.id
      if (!userId) return
      const res = await get(`/api/sport/records/${userId}?pageNum=1&pageSize=100`)
      const records = (res.records || []).map(r => {
        const sportType = SPORT_TYPES.find(s => s.name === r.sportType)
        return { ...r, emoji: sportType?.emoji || '🏃' }
      })
      this.setData({ records })
    } catch (err) {
      console.error('加载运动记录失败:', err)
    }
  },

  // 快速添加
  quickAdd(e) {
    const sport = e.currentTarget.dataset.sport
    const sportTypeIndex = SPORT_TYPES.findIndex(s => s.name === sport.name)
    this.setData({
      showForm: true,
      editingId: null,
      sportTypeIndex: sportTypeIndex >= 0 ? sportTypeIndex : 0,
      intensityIndex: 1,
      form: {
        sportType: sport.name,
        duration: sport.duration,
        calories: String(sport.calories),
        intensity: '中',
        recordDate: new Date().toISOString().slice(0, 10),
        notes: ''
      }
    })
  },

  openForm() {
    this.setData({
      showForm: true,
      editingId: null,
      sportTypeIndex: 0,
      intensityIndex: 1,
      form: {
        sportType: '跑步',
        duration: 30,
        calories: '',
        intensity: '中',
        recordDate: new Date().toISOString().slice(0, 10),
        notes: ''
      }
    })
  },

  closeForm() {
    this.setData({ showForm: false, editingId: null })
  },

  selectSportType(e) {
    const idx = e.currentTarget.dataset.index
    this.setData({
      sportTypeIndex: idx,
      'form.sportType': SPORT_TYPES[idx].name
    })
  },

  selectIntensity(e) {
    const idx = e.currentTarget.dataset.index
    this.setData({
      intensityIndex: idx,
      'form.intensity': this.data.intensityOptions[idx]
    })
  },

  onDurationChange(e) {
    this.setData({ 'form.duration': e.detail.value })
  },

  onDateChange(e) {
    this.setData({ 'form.recordDate': e.detail.value })
  },

  onInput(e) {
    const field = e.currentTarget.dataset.field
    this.setData({ [`form.${field}`]: e.detail.value })
  },

  editRecord(e) {
    const id = e.currentTarget.dataset.id
    const record = this.data.records.find(r => r.id === id)
    if (!record) return

    const sportTypeIndex = SPORT_TYPES.findIndex(s => s.name === record.sportType)
    const intensityIndex = this.data.intensityOptions.indexOf(record.intensity)

    this.setData({
      showForm: true,
      editingId: id,
      sportTypeIndex: sportTypeIndex >= 0 ? sportTypeIndex : 0,
      intensityIndex: intensityIndex >= 0 ? intensityIndex : 1,
      form: {
        sportType: record.sportType || '跑步',
        duration: Number(record.duration) || 30,
        calories: record.calories ? String(record.calories) : '',
        intensity: record.intensity || '中',
        recordDate: record.recordDate || '',
        notes: record.notes || ''
      }
    })
  },

  async deleteRecord(e) {
    const id = e.currentTarget.dataset.id
    const res = await new Promise(resolve => {
      wx.showModal({ title: '确认删除', content: '确定要删除这条运动记录吗？', success: resolve })
    })
    if (!res.confirm) return

    try {
      await del(`/api/sport/record/${id}`)
      wx.showToast({ title: '删除成功', icon: 'success' })
      this.loadRecords()
    } catch (err) {
      console.error('删除失败:', err)
    }
  },

  async submitForm() {
    const { form, editingId } = this.data
    if (!form.recordDate) {
      wx.showToast({ title: '请选择日期', icon: 'none' })
      return
    }

    this.setData({ saving: true })
    try {
      const data = {
        sportType: form.sportType,
        duration: Number(form.duration),
        calories: form.calories ? Number(form.calories) : null,
        intensity: form.intensity,
        recordDate: form.recordDate,
        notes: form.notes
      }

      console.log('提交运动数据:', data)

      if (editingId) {
        await put(`/api/sport/record/${editingId}`, data)
        wx.showToast({ title: '更新成功', icon: 'success' })
      } else {
        await post('/api/sport/record', data)
        wx.showToast({ title: '添加成功', icon: 'success' })
      }

      this.closeForm()
      this.loadRecords()
    } catch (err) {
      console.error('保存失败:', err)
      wx.showToast({ title: err.message || '保存失败', icon: 'none' })
    } finally {
      this.setData({ saving: false })
    }
  }
})
