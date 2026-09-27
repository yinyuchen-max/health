Page({
  data: {
    articles: [
      { id: 1, title: '如何科学减脂，健康瘦身', views: 1234, emoji: '🏋️' },
      { id: 2, title: '早餐这样吃，营养又健康', views: 356, emoji: '🥗' },
      { id: 3, title: '提升睡眠质量的8个小技巧', views: 2341, emoji: '😴' },
      { id: 4, title: '办公室人群如何运动', views: 567, emoji: '' }
    ]
  },

  readArticle(e) {
    const id = e.currentTarget.dataset.id
    wx.showToast({ title: '文章详情开发中', icon: 'none' })
  },

  goBmiCalculator() {
    wx.navigateTo({ url: '/pages/bmi-calculator/bmi-calculator' })
  },

  goCalorieCalculator() {
    wx.showToast({ title: '热量计算器开发中', icon: 'none' })
  },

  goWaterReminder() {
    wx.navigateTo({ url: '/pages/reminder/reminder' })
  },

  goMoreTools() {
    wx.showToast({ title: '更多工具开发中', icon: 'none' })
  }
})
