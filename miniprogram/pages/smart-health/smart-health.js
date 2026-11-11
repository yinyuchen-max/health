const { get, post } = require('../../utils/request')
const runtime = require('../../config/runtime')
const { reminderDraft } = require('../../utils/ai-assistant')

Page({
  data: {
    overview: {},
    riskList: [],
    loading: false,
    riskLabels: {
      BMI: 'BMI 风险',
      BLOOD_PRESSURE: '血压风险',
      DIABETES: '血糖风险',
      CARDIO: '心血管风险'
    }
  },

  onShow() {
    this.loadData()
  },

  loadData() { return this.loadOverview(false) },
  regenerate() { return this.loadOverview(true) },

  async loadOverview(refresh) {
    this.setData({ loading: true })
    try {
      const app = getApp()
      const userId = app.globalData.userInfo?.id
      if (!userId) { this.setData({ loading: false }); return }

      const overview = await get(`/api/smart-health/overview?userId=${userId}&refresh=${refresh ? 'true' : 'false'}`, {}, { showLoading: false, timeout: runtime.smartHealthTimeoutMs })

      // 处理风险评估列表
      const riskList = (overview.riskAssessments || [])
        .sort((a, b) => Number(b.riskScore || 0) - Number(a.riskScore || 0))

      this.setData({ overview, riskList, loading: false })
    } catch (err) {
      console.error('加载智能健康分析失败:', err)
      this.setData({ loading: false })
    }
  },

  addReminder(e) {
    const text = e.currentTarget.dataset.text
    const draft = reminderDraft(text)
    if (!draft) {
      wx.showToast({ title: '这条建议请到提醒页手动添加', icon: 'none' })
      return
    }
    const app = getApp()
    post('/api/reminder/preferences', { ...draft, userId: app.globalData.userInfo?.id }).then(() => {
      wx.showToast({ title: '已添加到提醒', icon: 'success' })
    }).catch(error => wx.showToast({ title: error.message || '添加失败', icon: 'none' }))
  }
})
