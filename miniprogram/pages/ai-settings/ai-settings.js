const { get, put, del } = require('../../utils/request')
const { billingWebUrl } = require('../../config/environment')
Page({
  data: { loading: true, status: null, plans: [], message: '', error: '', memories: [], memoriesLoaded: false, memoryLoading: false, memoryError: '' },
  onShow() { if (getApp().checkLogin()) { this.load(); this.loadMemories() } },
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
  chat() { wx.navigateTo({ url: '/pages/ai-chat/ai-chat' }) },
  async loadMemories() {
    this.setData({ memoryLoading: true, memoryError: '' })
    try {
      const view = await get('/api/chat/memories', {}, { showLoading: false, showError: false })
      this.setData({ memories: view.memories || [], memoriesLoaded: true })
    } catch (error) {
      this.setData({ memoryError: error.message || '记忆暂时读不到' })
    } finally { this.setData({ memoryLoading: false }) }
  },
  editMemory(e) {
    const { id, content } = e.currentTarget.dataset
    wx.showModal({
      title: '修改并确认记忆', editable: true, content: content || '', placeholderText: '确认后的说法',
      success: async (result) => {
        if (!result.confirm || !result.content || !result.content.trim()) return
        try {
          await put(`/api/chat/memories/${id}`, { content: result.content.trim() })
          wx.showToast({ title: '已保存', icon: 'success' })
          this.loadMemories()
        } catch (error) { wx.showToast({ title: error.message || '保存失败', icon: 'none' }) }
      }
    })
  },
  deleteMemory(e) {
    const id = e.currentTarget.dataset.id
    wx.showModal({
      title: '删除这条记忆？', content: '删除后，仍包含这句话的对话上下文也会从后续回答中拿掉。',
      success: async (result) => {
        if (!result.confirm) return
        try {
          await del(`/api/chat/memories/${id}`)
          this.loadMemories()
        } catch (error) { wx.showToast({ title: error.message || '删除失败', icon: 'none' }) }
      }
    })
  }
})
