const { get } = require('../../utils/request')

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

  async loadData() {
    this.setData({ loading: true })
    try {
      const app = getApp()
      const userId = app.globalData.userInfo?.id
      if (!userId) return

      const overview = await get(`/api/smart-health/overview?userId=${userId}`, {}, { showLoading: false })

      // 处理风险评估列表
      const riskList = (overview.riskAssessments || [])
        .sort((a, b) => Number(b.riskScore || 0) - Number(a.riskScore || 0))

      this.setData({ overview, riskList, loading: false })
    } catch (err) {
      console.error('加载智能健康分析失败:', err)
      this.setData({ loading: false })
    }
  }
})
