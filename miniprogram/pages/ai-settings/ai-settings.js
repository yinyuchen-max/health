const { get } = require('../../utils/request')
const { billingWebUrl } = require('../../config/environment')
Page({
  data: { loading: true, status: null, plans: [], message: '', error: '' },
  onShow() { if (getApp().checkLogin()) this.load() },
  async load() {
    this.setData({ loading: true, error: '' })
    try {
      const [status, catalog] = await Promise.all([get('/api/ai/access'), get('/api/ai/payments/catalog')])
      this.setData({ status, plans: catalog.plans.map(plan => ({ ...plan, price: (plan.amountCents / 100).toFixed(2) })),
        message: catalog.message })
    } catch (e) {
      this.setData({ status: null, plans: [], error: e.message || '服务状态暂不可用' })
    } finally { this.setData({ loading: false }) }
  },
  openBilling() { wx.setClipboardData({ data: billingWebUrl, success: () => wx.showModal({ title: '网页端支付', content: '网址已复制。请在浏览器打开，登录同一账号后使用微信或支付宝付款。', showCancel: false }) }) },
  chat() { wx.navigateTo({ url: '/pages/ai-chat/ai-chat' }) }
})
