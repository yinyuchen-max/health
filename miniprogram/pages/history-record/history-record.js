const { get } = require('../../utils/request')

Page({
  data: {
    records: [],
    loading: false,
    typeIndex: 0,
    typeOptions: ['全部类型', '健康记录', '运动记录', '提醒记录'],
    typeValues: ['', 'health', 'sport', 'reminder'],
    typeLabels: {
      health: '健康记录',
      sport: '运动记录',
      reminder: '提醒记录'
    },
    startDate: '',
    endDate: ''
  },

  onShow() {
    this.loadRecords()
  },

  async loadRecords() {
    this.setData({ loading: true })
    try {
      const app = getApp()
      const userId = app.globalData.userInfo?.id
      if (!userId) return

      const { typeIndex, typeValues, startDate, endDate } = this.data
      const type = typeValues[typeIndex]

      let url = `/api/history/records/${userId}?pageNum=1&pageSize=100`
      if (type) url += `&type=${type}`
      if (startDate) url += `&startDate=${startDate}`
      if (endDate) url += `&endDate=${endDate}`

      const res = await get(url, {}, { showLoading: false })
      const records = res.records || []
      this.setData({ records, loading: false })
    } catch (err) {
      console.error('加载历史记录失败:', err)
      this.setData({ loading: false })
    }
  },

  onTypeChange(e) {
    this.setData({ typeIndex: e.detail.value })
    this.loadRecords()
  },

  onStartDateChange(e) {
    this.setData({ startDate: e.detail.value })
    this.loadRecords()
  },

  onEndDateChange(e) {
    this.setData({ endDate: e.detail.value })
    this.loadRecords()
  }
})
