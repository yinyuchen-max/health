const { get, put, del } = require('../../utils/request')
Page({
  data: { loading: true, busy: false, configured: false, embeddingConfigured: false, providerIndex: 0,
    providers: ['DeepSeek', '阿里云百炼（北京）'], model: 'deepseek-flash', apiKey: '', embeddingApiKey: '', clearEmbeddingKey: false },
  onShow() { if (getApp().checkLogin()) this.load() },
  onHide() { this.clearSecrets() },
  onUnload() { this.clearSecrets() },
  clearSecrets() { this.setData({ apiKey: '', embeddingApiKey: '', clearEmbeddingKey: false }) },
  async load() {
    this.setData({ loading: true })
    try {
      const s = await get('/api/ai/settings')
      this.setData({ configured: s.configured, embeddingConfigured: s.embeddingConfigured, providerIndex: s.provider === 'dashscope' ? 1 : 0, model: s.model })
    } catch {} finally { this.setData({ loading: false }) }
  },
  providerChange(e) {
    const providerIndex = Number(e.detail.value)
    this.setData({ providerIndex, model: providerIndex ? 'qwen-plus' : 'deepseek-flash', apiKey: '' })
  },
  input(e) { const field = e.currentTarget.dataset.field; if (['model', 'apiKey', 'embeddingApiKey'].includes(field)) this.setData({ [field]: e.detail.value }) },
  clearEmbedding(e) { this.setData({ clearEmbeddingKey: e.detail.value }) },
  async save() {
    if (this.data.busy) return
    this.setData({ busy: true })
    try {
      await put('/api/ai/settings', { provider: this.data.providerIndex ? 'dashscope' : 'deepseek', model: this.data.model,
        apiKey: this.data.apiKey, embeddingApiKey: this.data.embeddingApiKey, clearEmbeddingKey: this.data.clearEmbeddingKey })
      this.clearSecrets(); await this.load(); wx.showToast({ title: '已保存', icon: 'success' })
    } catch {} finally { this.setData({ busy: false }) }
  },
  remove() {
    wx.showModal({ title: '删除全部 AI 密钥', content: '删除后健康报告改用规则分析。', success: async (r) => {
      if (!r.confirm) return
      this.setData({ busy: true })
      try { await del('/api/ai/settings'); this.clearSecrets(); await this.load(); wx.showToast({ title: '已删除' }) }
      catch {} finally { this.setData({ busy: false }) }
    } })
  }
})
