const { get, post, put, del } = require('../../utils/request')
const { syncWeRun } = require('../../utils/wechat-data')

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
    todaySteps: null,
    lastSyncAt: '',
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
    if (!getApp().checkLogin()) return
    this.loadWeRunData().catch(() => {})
    this.loadRecords()
  },

  async loadWeRunData(requestPermission = false) {
    const data = await syncWeRun({ requestPermission })
    const steps = data.todaySteps
    this.setData({
      todaySteps: steps,
      todayCalories: steps == null ? '--' : Math.round(steps * 0.04),
      todayDistance: steps == null ? '--' : (steps * 0.7 / 1000).toFixed(1),
      lastSyncAt: data.lastSyncAt ? data.lastSyncAt.replace('T', ' ') : ''
    })
  },

  async syncWeRun() {
    if (this.syncing) return
    this.syncing = true
    wx.showLoading({ title: '同步中...' })
    try {
      await this.loadWeRunData(true)
      wx.hideLoading()
      wx.showToast({ title: '同步成功', icon: 'success' })
    } catch (error) {
      wx.hideLoading()
      wx.showToast({ title: error.message || '同步失败，请重试', icon: 'none' })
    } finally { this.syncing = false }
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
      const today = new Date(Date.now() + 8 * 60 * 60 * 1000).toISOString().slice(0, 10)
      this.setData({ records, todaySportMinutes: records.filter(r => r.recordDate === today).reduce((sum, r) => sum + Number(r.duration || 0), 0) })
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
