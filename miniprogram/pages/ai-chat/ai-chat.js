const { streamRequest, get, del, post } = require('../../utils/request')
const { isEmergency, mentionsAppointment } = require('../../utils/ai-assistant')

Page({
  data: {
    messages: [],
    inputText: '',
    loading: false,
    scrollToView: '',
    aiAllowed: false,
    accessMessage: '正在检查 AI 服务资格…',
    conflictCount: 0,
    emergency: false,
    quickQuestions: [
      { icon: '🩺', text: '最近总是头晕怎么办？' },
      { icon: '🍎', text: '帮我制定健康饮食计划' },
      { icon: '💪', text: '适合初学者的运动建议' },
      { icon: '😴', text: '如何改善睡眠质量？' },
      { icon: '📊', text: '分析我的健康数据趋势' },
      { icon: '🏥', text: '我想预约医生' }
    ]
  },

  requestTask: null,

  onShow() {
    if (getApp().checkLogin()) {
      this.loadAccess()
      this.loadHistory()
      this.loadConflicts()
    }
  },

  async loadAccess() {
    try {
      const status = await get('/api/ai/access', {}, { showLoading: false, showError: false })
      const allowed = !!(status.allowed && status.configured)
      this.setData({
        aiAllowed: allowed,
        accessMessage: !status.allowed ? '请先开通 AI 服务；管理员可免费使用。' : !status.configured ? '平台 AI 服务暂未配置，请联系管理员。' : ''
      })
    } catch (error) {
      this.setData({ aiAllowed: false, accessMessage: error.message || '暂时无法读取 AI 服务资格' })
    }
  },

  async loadHistory() {
    try {
      const rows = await get('/api/chat/history', {}, { showLoading: false, showError: false })
      if (Array.isArray(rows) && rows.length) {
        this.setData({ messages: rows.map(item => this.decorate({ role: item.role, content: item.content })) })
        this.refreshFlags()
        this.scrollToBottom()
      }
    } catch (error) {
      console.error('读取对话失败', error)
    }
  },

  async loadConflicts() {
    try {
      const view = await get('/api/chat/memories', {}, { showLoading: false, showError: false })
      const count = (view.memories || []).filter(item => item.status === 'CONFLICT').length
      this.setData({ conflictCount: count })
    } catch (error) {
      this.setData({ conflictCount: 0 })
    }
  },

  decorate(message) {
    return { ...message, appointment: false, contextNote: message.contextNote || '' }
  },

  refreshFlags() {
    const messages = this.data.messages
    const emergency = messages.some(item => item.role === 'user' && isEmergency(item.content))
    const last = messages.length - 1
    if (last >= 0 && messages[last].role === 'assistant') {
      const user = [...messages].reverse().find(item => item.role === 'user')
      messages[last].appointment = mentionsAppointment(messages[last].content) || mentionsAppointment(user && user.content)
    }
    this.setData({ messages, emergency })
  },

  onInput(e) {
    this.setData({ inputText: e.detail.value })
  },

  sendQuick(e) {
    if (!this.data.aiAllowed) {
      wx.showToast({ title: '请先开通 AI 服务', icon: 'none' })
      return
    }
    const text = e.currentTarget.dataset.text
    this.setData({ inputText: text })
    this.sendMessage()
  },

  sendAppointment() {
    this.setData({ inputText: '我想预约医生' })
    this.sendMessage()
  },

  sendMessage() {
    const { inputText, loading, aiAllowed } = this.data
    if (this._sending || !inputText.trim() || loading) return
    if (!aiAllowed) {
      wx.showToast({ title: '请先开通 AI 服务', icon: 'none' })
      return
    }
    this._sending = true

    const userMsg = { role: 'user', content: inputText.trim(), appointment: false, contextNote: '' }
    const messages = [...this.data.messages, userMsg]
    messages.push({ role: 'assistant', content: '', streaming: true, appointment: false, contextNote: '' })

    this.setData({ messages, inputText: '', loading: true })
    this.refreshFlags()
    this.scrollToBottom()
    this.streamInto(messages.length - 1, userMsg.content)
  },

  regenerate() {
    const messages = this.data.messages
    let userIndex = -1
    for (let i = messages.length - 1; i >= 0; i -= 1) {
      if (messages[i].role === 'user') { userIndex = i; break }
    }
    if (userIndex < 0 || this.data.loading) return
    const text = messages[userIndex].content
    const next = messages.slice(0, userIndex + 1)
    next.push({ role: 'assistant', content: '', streaming: true, appointment: false, contextNote: '' })
    this._sending = true
    this.setData({ messages: next, loading: true })
    this.streamInto(next.length - 1, text)
  },

  streamInto(idx, messageText) {
    let fullContent = ''
    let streamError = ''
    this.requestTask = streamRequest({
      url: '/api/chat/send/stream',
      data: { message: messageText },
      onChunk: (text) => {
        const lines = text.split('\n')
        let currentEvent = ''
        for (const line of lines) {
          if (line.startsWith('event:')) {
            currentEvent = line.slice(6).trim()
          } else if (line.startsWith('data:')) {
            const dataStr = line.slice(5).trim()
            if (currentEvent === 'token') {
              try {
                const delta = JSON.parse(dataStr)
                if (delta) {
                  fullContent += delta
                  this.setData({ [`messages[${idx}].content`]: fullContent })
                  this.scrollToBottom()
                }
              } catch (e) {}
            } else if (currentEvent === 'context') {
              try { this.setData({ [`messages[${idx}].contextNote`]: JSON.parse(dataStr) }) } catch (e) {}
            } else if (currentEvent === 'error') {
              try { streamError = JSON.parse(dataStr) } catch (e) { streamError = dataStr || 'AI 服务出错' }
            }
          }
        }
      },
      onDone: () => {
        this._sending = false
        const patch = { [`messages[${idx}].streaming`]: false, loading: false }
        if (streamError) patch[`messages[${idx}].content`] = String(streamError)
        this.setData(patch)
        this.refreshFlags()
        this.requestTask = null
      },
      onError: (err) => {
        console.error('流式请求错误:', err)
        this._sending = false
        this.setData({
          [`messages[${idx}].content`]: err.message || '抱歉，请求失败，请重试。',
          [`messages[${idx}].streaming`]: false,
          loading: false
        })
        this.requestTask = null
      }
    })
  },

  stopStreaming() {
    this._sending = false
    if (this.requestTask) {
      this.requestTask.abort()
      this.requestTask = null
    }
    const idx = this.data.messages.length - 1
    if (idx >= 0) this.setData({ [`messages[${idx}].streaming`]: false, loading: false })
  },

  copyMessage(e) {
    wx.setClipboardData({ data: e.currentTarget.dataset.text || '' })
  },

  async rateMessage(e) {
    const rating = e.currentTarget.dataset.rating
    const excerpt = e.currentTarget.dataset.text || ''
    try {
      await post('/api/chat/feedback', { rating, excerpt: excerpt.slice(0, 500) }, { showLoading: false })
      if (rating === 'INACCURATE') {
        wx.showModal({ title: '已记下', content: '请到 AI 服务页纠正不准确的记忆。', confirmText: '去纠正', success: (result) => {
          if (result.confirm) wx.navigateTo({ url: '/pages/ai-settings/ai-settings' })
        } })
      } else wx.showToast({ title: '已记下', icon: 'success' })
    } catch (error) {
      wx.showToast({ title: error.message || '评价失败', icon: 'none' })
    }
  },

  clearHistory() {
    wx.showModal({
      title: '清空对话',
      content: '将删除网页、小程序和服务器上的这段对话，并清除 AI 已记住的长期记忆。',
      success: async (result) => {
        if (!result.confirm) return
        try {
          await del('/api/chat/history', {}, { showLoading: true })
          this.setData({ messages: [], conflictCount: 0, emergency: false })
          wx.showToast({ title: '已清空', icon: 'success' })
        } catch (error) {
          wx.showToast({ title: error.message || '清空失败', icon: 'none' })
        }
      }
    })
  },

  scrollToBottom() {
    const idx = this.data.messages.length - 1
    this.setData({ scrollToView: `msg-${idx}` })
  }
})
