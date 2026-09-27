const { streamRequest } = require('../../utils/request')

Page({
  data: {
    messages: [],
    inputText: '',
    loading: false,
    scrollToView: '',
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

  onInput(e) {
    this.setData({ inputText: e.detail.value })
  },

  sendQuick(e) {
    const text = e.currentTarget.dataset.text
    this.setData({ inputText: text })
    this.sendMessage()
  },

  sendMessage() {
    const { inputText, loading } = this.data
    if (!inputText.trim() || loading) return

    const userMsg = { role: 'user', content: inputText.trim() }
    const messages = [...this.data.messages, userMsg]
    const assistantMsg = { role: 'assistant', content: '', streaming: true }
    messages.push(assistantMsg)

    this.setData({
      messages,
      inputText: '',
      loading: true
    })
    this.scrollToBottom()

    // 构造历史消息（不含当前正在流式的 assistant 消息）
    const history = messages.slice(0, -1).map(m => ({
      role: m.role,
      content: m.content
    }))

    let fullContent = ''

    // 只发送最后一条用户消息
    const lastUserMsg = history.filter(m => m.role === 'user').pop()
    const messageText = lastUserMsg ? lastUserMsg.content : inputText.trim()

    this.requestTask = streamRequest({
      url: '/api/chat/send/stream',
      data: { message: messageText },
      onChunk: (text) => {
        // 解析后端 SSE 格式：event:token data:"text" 或 event:done data:"fullText"
        const lines = text.split('\n')
        let currentEvent = ''
        for (const line of lines) {
          if (line.startsWith('event:')) {
            currentEvent = line.slice(6).trim()
          } else if (line.startsWith('data:')) {
            const dataStr = line.slice(5).trim()
            if (currentEvent === 'token') {
              // token 事件：data 是 JSON 字符串 "text"
              try {
                const delta = JSON.parse(dataStr)
                if (delta) {
                  fullContent += delta
                  const idx = this.data.messages.length - 1
                  this.setData({
                    [`messages[${idx}].content`]: fullContent
                  })
                  this.scrollToBottom()
                }
              } catch (e) {
                // 忽略解析错误
              }
            } else if (currentEvent === 'done') {
              // done 事件：流结束
            } else if (currentEvent === 'error') {
              try {
                const errMsg = JSON.parse(dataStr)
                console.error('AI 错误:', errMsg)
              } catch (e) {}
            }
          }
        }
      },
      onDone: () => {
        const idx = this.data.messages.length - 1
        this.setData({
          [`messages[${idx}].streaming`]: false,
          loading: false
        })
        this.requestTask = null
      },
      onError: (err) => {
        console.error('流式请求错误:', err)
        const idx = this.data.messages.length - 1
        this.setData({
          [`messages[${idx}].content`]: '抱歉，请求失败，请重试。',
          [`messages[${idx}].streaming`]: false,
          loading: false
        })
        this.requestTask = null
      }
    })
  },

  stopStreaming() {
    if (this.requestTask) {
      this.requestTask.abort()
      this.requestTask = null
    }
    const idx = this.data.messages.length - 1
    if (idx >= 0) {
      this.setData({
        [`messages[${idx}].streaming`]: false,
        loading: false
      })
    }
  },

  scrollToBottom() {
    const idx = this.data.messages.length - 1
    this.setData({ scrollToView: `msg-${idx}` })
  }
})
