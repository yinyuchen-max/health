<template>
  <div class="ai-chat-page">
    <!-- 顶部窄标题条 -->
    <div class="chat-header">
      <div class="header-left">
        <div class="header-text">
          <h2>医学问答</h2>
          <p>由大模型回答。胸痛、呼吸困难、严重出血或昏迷，请立即拨打 120。</p>
        </div>
      </div>
      <el-button
        v-if="messages.length > 0"
        text
        class="clear-btn"
        @click="handleClearHistory"
      >
        <el-icon><Delete /></el-icon>清空对话
      </el-button>
    </div>

    <el-alert v-if="emergency" type="error" :closable="false" title="如果这是正在发生的紧急情况，请立即拨打 120 或就近急诊。这里的回复不能代替急救。" />
    <!-- 消息滚动区 -->
    <div class="chat-scroll" ref="scrollRef">
      <div class="chat-column">
        <!-- 欢迎页 -->
        <div v-if="messages.length === 0" class="welcome">
          <h3>今天想问什么？</h3>
          <p class="welcome-sub">描述症状、用药或检查结果。点下面的问题可以直接发出。</p>
          <div class="quick-list">
            <button
              v-for="q in quickQuestions"
              :key="q.text"
              class="quick-card"
              @click="sendMessage(q.text)"
            >
              {{ q.text }}
            </button>
          </div>
        </div>

        <!-- 消息列表 -->
        <div
          v-for="(msg, idx) in messages"
          :key="idx"
          :class="['msg-row', msg.role]"
        >
          <!-- AI 消息 -->
          <template v-if="msg.role === 'assistant'">
            <div class="avatar assistant-avatar">
              <el-icon :size="18"><ChatDotRound /></el-icon>
            </div>
            <div class="msg-main">
              <div class="msg-meta">
                <span class="msg-name">医学问答</span>
                <span v-if="msg.streaming && !msg.content" class="typing-label">
                  <span class="label-dot"></span>正在思考
                </span>
                <span v-else-if="msg.streaming" class="typing-label">
                  <span class="label-dot"></span>正在输入
                </span>
                <span v-else class="msg-time">{{ msg.time }}</span>
              </div>
              <!-- 思考过程面板 -->
              <div v-if="msg.thinking" class="thinking-panel">
                <div class="thinking-header" @click="toggleThinking(idx)">
                  <el-icon :size="14" class="think-icon"><Cpu /></el-icon>
                  <span v-if="!msg.content" class="thinking-live-text">
                    正在思考<el-icon :size="12" class="think-pulse"><Loading /></el-icon>
                  </span>
                  <span v-else>已深度思考 {{ msg.thinkingDuration || '—' }} 秒</span>
                  <el-icon :size="14" class="chevron" :class="{ rotated: msg.thinkingOpen }"><ArrowDown /></el-icon>
                </div>
                <div v-if="!msg.content || msg.thinkingOpen" class="thinking-body">{{ msg.thinking }}</div>
              </div>
              <div class="bubble assistant-bubble" :class="{ 'typing-bubble': msg.streaming && !msg.content }">
                <template v-if="msg.streaming && !msg.content">
                  <span class="dot"></span><span class="dot"></span><span class="dot"></span>
                </template>
                <template v-else>
                  <div class="bubble-text" v-html="renderMarkdown(msg.content)"></div>
                  <span v-if="msg.streaming" class="stream-cursor"></span>
                </template>
              </div>
              <div v-if="!msg.streaming && !loading" class="msg-actions">
                <button class="action-btn" @click="copyMessage(msg)">
                  <el-icon :size="13"><CopyDocument /></el-icon>复制
                </button>
                <button class="action-btn" @click="regenerate(idx)">
                  <el-icon :size="13"><Refresh /></el-icon>重新生成
                </button>
                <button class="action-btn" @click="rateMessage(msg, 'USEFUL')">有帮助</button>
                <button class="action-btn" @click="rateMessage(msg, 'INACCURATE')">需要纠正</button>
              </div>
              <p v-if="msg.contextNote" class="context-note">{{ msg.contextNote }}</p>
              <div v-if="showAppointmentCard(idx)" class="appointment-card">
                <div class="appt-info">
                  <span class="appt-icon">🏥</span>
                  <div class="appt-text">
                    <div class="appt-title">需要预约医生？</div>
                    <div class="appt-sub">说出你的症状，AI 帮你完成预约</div>
                  </div>
                </div>
                <el-button type="primary" size="small" round @click="sendMessage('我想预约医生')">
                  <el-icon :size="13"><FirstAidKit /></el-icon>立即预约
                </el-button>
              </div>
            </div>
          </template>

          <!-- 用户消息 -->
          <template v-else>
            <div class="msg-main user-main">
              <div class="bubble user-bubble">{{ msg.content }}</div>
            </div>
            <div class="avatar user-avatar">{{ userInitial }}</div>
          </template>
        </div>
      </div>
    </div>

    <!-- 底部输入区 -->
    <div class="chat-footer">
      <div class="input-panel">
        <el-input
          v-model="inputText"
          type="textarea"
          :autosize="{ minRows: 1, maxRows: 6 }"
          placeholder="描述症状、用药或检查结果"
          resize="none"
          :disabled="loading"
          class="chat-textarea"
          @keydown.enter="onEnterKey"
        />
        <div class="input-toolbar">
          <span class="input-hint">Enter 发送 · Shift+Enter 换行 · 内容由 AI 生成，仅供参考，不能代替医生诊断。胸痛、呼吸困难、严重出血或昏迷请立即拨打 120。</span>
          <div class="input-buttons">
            <el-button
              v-if="loading"
              type="danger"
              round
              size="small"
              @click="stopStreaming"
            >
              <el-icon><VideoPause /></el-icon>停止
            </el-button>
            <el-button
              v-else
              type="primary"
              round
              :disabled="!inputText.trim()"
              @click="handleSend()"
            >
              <el-icon><Promotion /></el-icon>发送
            </el-button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { authenticatedFetch } from '../utils/auth'
import { computed, nextTick, onMounted, onBeforeUnmount, ref, watch } from 'vue'
import {
  ArrowDown,
  ChatDotRound,
  CopyDocument,
  Cpu,
  Delete,
  FirstAidKit,
  Loading,
  Promotion,
  Refresh,
  VideoPause
} from '@element-plus/icons-vue'
import request from '../utils/request'
import { renderMarkdown } from '../utils/markdown'
import { useUserStore } from '../store/user'
import { ElMessageBox, ElMessage } from 'element-plus'
import { clearServerChat, isEmergency, loadChatHistory, mentionsAppointment, sendChatFeedback } from '../utils/ai-assistant'

const MEMBERSHIP_REPLY = '您还未开通会员，暂时无法使用 AI 问答。开通会员后即可继续提问。'
const UNAVAILABLE_REPLY = 'AI 问答暂时不可用，请稍后再试。'

const userStore = useUserStore()
const messages = ref([])
const inputText = ref('')
const loading = ref(false)
const access = ref(null)
const emergency = computed(() => messages.value.some(item => item.role === 'user' && isEmergency(item.content)))
const loadAccess = async () => {
  try {
    const response = await request.get('/ai/access')
    if (response.code !== 200) throw new Error(response.message || '读取服务状态失败')
    access.value = {
      allowed: !!response.data.allowed,
      configured: !!response.data.configured
    }
  } catch {
    access.value = { error: true }
  }
}
const localBlockReply = () => {
  if (!access.value || access.value.error) return ''
  if (!access.value.allowed) return MEMBERSHIP_REPLY
  if (!access.value.configured) return UNAVAILABLE_REPLY
  return ''
}
const scrollRef = ref(null)

let abortController = null
let abortedByUser = false
let rafPending = false

const userInitial = computed(() =>
  (userStore.userInfo?.username || '我').slice(0, 1).toUpperCase()
)

// 最后一条 AI 消息的索引（预约卡片只显示在最新一条 AI 回复下方）
const lastAssistantIdx = computed(() => {
  for (let i = messages.value.length - 1; i >= 0; i--) {
    if (messages.value[i].role === 'assistant') return i
  }
  return -1
})

const previousUserText = (idx) => {
  for (let i = idx - 1; i >= 0; i -= 1) {
    if (messages.value[i].role === 'user') return messages.value[i].content
  }
  return ''
}
const showAppointmentCard = (idx) =>
  idx === lastAssistantIdx.value && !loading.value
  && (mentionsAppointment(messages.value[idx]?.content) || mentionsAppointment(previousUserText(idx)))

const quickQuestions = [
  { text: '感冒和流感怎么区分？' },
  { text: '血压偏高，日常生活要注意什么？' },
  { text: '这个症状需要马上去医院吗？' },
  { text: '感冒药可以和降压药一起吃吗？' }
]

/* ---------------- 本地历史 ---------------- */
const historyKey = () => `ai-chat-history-${userStore.userInfo?.id || 'default'}`

const loadLocalMessages = () => {
  const saved = localStorage.getItem(historyKey())
  if (!saved) return
  try { messages.value = JSON.parse(saved) } catch (e) { console.error('加载对话历史失败:', e) }
}
const loadMessages = async () => {
  try {
    const remote = await loadChatHistory(request)
    if (remote.length) {
      messages.value = remote
      return
    }
  } catch (e) {
    console.error('读取服务器对话失败:', e)
  }
  loadLocalMessages()
}

const saveMessages = () => {
  try {
    const compact = messages.value.map(({ role, content, time, thinking, thinkingDuration }) => ({
      role,
      content,
      time,
      thinking: thinking || undefined,
      thinkingDuration: thinkingDuration || undefined
    }))
    localStorage.setItem(historyKey(), JSON.stringify(compact))
  } catch (e) {
    console.error('保存对话历史失败:', e)
  }
}

const clearMessages = () => {
  localStorage.removeItem(historyKey())
  messages.value = []
}

const handleClearHistory = async () => {
  try {
    await ElMessageBox.confirm(
      '将删除这段对话，清空后无法恢复。',
      '清空对话',
      { confirmButtonText: '确定', cancelButtonText: '取消', type: 'warning' }
    )
    await clearServerChat(request)
    clearMessages()
    ElMessage.success('对话已清空')
  } catch (error) {
    if (error === 'cancel' || error === 'close') return
    ElMessage.error(error.message || '清空失败，服务器上的对话还在')
  }
}

/* ---------------- 工具函数 ---------------- */
const formatTime = () =>
  new Date().toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })

const scrollToBottom = async (smooth = true) => {
  await nextTick()
  if (scrollRef.value) {
    scrollRef.value.scrollTo({
      top: scrollRef.value.scrollHeight,
      behavior: smooth ? 'smooth' : 'auto'
    })
  }
}

const scheduleScroll = () => {
  if (rafPending) return
  rafPending = true
  requestAnimationFrame(() => {
    rafPending = false
    scrollToBottom()
  })
}

const rateMessage = async (msg, rating) => {
  try {
    await sendChatFeedback(request, rating, msg.content)
    if (rating === 'INACCURATE') {
      ElMessage.success('已记下，这条回复需要纠正')
      return
    }
    ElMessage.success('谢谢，已记下这条回复有帮助')
  } catch (e) {
    ElMessage.error(e.message || '评价失败')
  }
}

const copyMessage = async (msg) => {
  try {
    await navigator.clipboard.writeText(msg.content)
    ElMessage.success('已复制到剪贴板')
  } catch {
    ElMessage.error('复制失败，请手动选择复制')
  }
}

/* ---------------- 发送与流式接收 ---------------- */
const pushLocalExchange = async (message, reply) => {
  inputText.value = ''
  messages.value.push({ role: 'user', content: message, time: formatTime() })
  messages.value.push({ role: 'assistant', content: reply, time: formatTime() })
  saveMessages()
  await scrollToBottom()
}

const handleSend = async (text) => {
  const message = (text ?? inputText.value).trim()
  if (!message || loading.value) return
  if (!access.value) await loadAccess()
  const blocked = localBlockReply()
  if (blocked) {
    await pushLocalExchange(message, blocked)
    return
  }

  loading.value = true
  inputText.value = ''
  messages.value.push({ role: 'user', content: message, time: formatTime() })
  messages.value.push({ role: 'assistant', content: '', time: formatTime(), streaming: true })
  const msgIdx = messages.value.length - 1
  await scrollToBottom()

  await streamReply(message, msgIdx)
}

const sendMessage = (text) => {
  inputText.value = text
  handleSend()
}

const stopStreaming = () => {
  if (!loading.value) return
  abortedByUser = true
  if (abortController) {
    abortController.abort()
  }
}

const onEnterKey = (e) => {
  // Shift+Enter 换行；输入法组词回车不发送
  if (e.shiftKey || e.isComposing || e.keyCode === 229) return
  e.preventDefault()
  handleSend()
}

/**
 * 流式请求：优先走 SSE 接口，失败（网络/接口不存在/鉴权异常）时降级为普通接口
 * 注意：必须通过 messages.value[msgIdx]（响应式代理）更新内容，直接改原始对象不会触发视图更新
 */
const streamReply = async (userText, msgIdx) => {
  abortedByUser = false
  abortController = new AbortController()
  loading.value = true
  let content = ''
  let streamStarted = false

  const setContent = (val) => {
    messages.value[msgIdx].content = val
  }

  try {
    const base = (import.meta.env.VITE_API_BASE_URL?.trim() || '/api').replace(/\/+$/, '')
    const url = `${base}/chat/send/stream`
    const headers = { 'Content-Type': 'application/json', Accept: 'text/event-stream' }

    const res = await authenticatedFetch(url, {
      method: 'POST',
      headers,
      body: JSON.stringify({ message: userText }),
      signal: abortController.signal
    })

    if (res.status === 402) {
      access.value = { allowed: false, configured: true }
      const error = new Error(MEMBERSHIP_REPLY)
      error.membership = true
      throw error
    }
    if (!res.ok || !res.body) {
      const failure = await res.json().catch(() => ({}))
      const error = new Error(failure.message || `HTTP ${res.status}`)
      error.fallback = true
      throw error
    }
    streamStarted = true

    const reader = res.body.getReader()
    const decoder = new TextDecoder('utf-8')
    let buffer = ''

    while (true) {
      const { done, value } = await reader.read()
      if (done) break
      buffer += decoder.decode(value, { stream: true })

      let sep
      while ((sep = buffer.indexOf('\n\n')) !== -1) {
        const block = buffer.slice(0, sep)
        buffer = buffer.slice(sep + 2)
        const evt = parseSseBlock(block)
        if (!evt) continue
        if (evt.name === 'context') {
          messages.value[msgIdx].contextNote = evt.data
        } else if (evt.name === 'thinking') {
          // 推理模型的思考过程
          const msg = messages.value[msgIdx]
          if (!msg.thinkingStart) msg.thinkingStart = Date.now()
          msg.thinking = (msg.thinking || '') + evt.data
          scheduleScroll()
        } else if (evt.name === 'token') {
          const msg = messages.value[msgIdx]
          if (msg.thinkingStart && !msg.thinkingDuration) {
            msg.thinkingDuration = Math.max(1, Math.round((Date.now() - msg.thinkingStart) / 1000))
          }
          content += evt.data
          setContent(content)
          scheduleScroll()
        } else if (evt.name === 'done') {
          content = evt.data || content
          setContent(content)
          return
        } else if (evt.name === 'error') {
          const error = new Error(evt.data || 'AI 服务出错')
          error.streamed = true
          throw error
        }
      }
    }
    const closed = new Error('连接意外关闭')
    closed.streamed = true
    throw closed
  } catch (e) {
    if (abortedByUser) {
      setContent(content
        ? `${content}\n\n*（已停止生成）*`
        : '*（已停止生成）*')
    } else if (e?.membership) {
      setContent(MEMBERSHIP_REPLY)
    } else if (e?.fallback && !streamStarted) {
      setContent('')
      try {
        const res = await request.post('/chat/send', { message: userText })
        const reply = res?.data?.reply || res?.reply
        messages.value[msgIdx].contextNote = res?.data?.contextNote || ''
        setContent(reply || '抱歉，AI 服务暂时不可用，请稍后重试。')
      } catch (fallbackError) {
        if (fallbackError?.status === 402) {
          access.value = { allowed: false, configured: true }
          setContent(MEMBERSHIP_REPLY)
        } else {
          setContent(content || '抱歉，AI 服务暂时不可用，请稍后重试。')
        }
      }
    } else {
      setContent(content || e?.message || '抱歉，AI 服务暂时不可用，请稍后重试。')
    }
  } finally {
    // 结束后主动关闭流连接（Vite 代理下服务端关闭可能不传递 EOF）
    if (abortController) {
      abortController.abort()
      abortController = null
    }
    messages.value[msgIdx].streaming = false
    loading.value = false
    saveMessages()
    await scrollToBottom()
  }
}

/** 解析一个 SSE 事件块（event: xxx / data: xxx） */
const parseSseBlock = (block) => {
  let name = 'message'
  const dataLines = []
  for (const rawLine of block.split('\n')) {
    const line = rawLine.replace(/\r$/, '')
    if (line.startsWith('event:')) {
      name = line.slice(6).trim()
    } else if (line.startsWith('data:')) {
      dataLines.push(line.slice(5).replace(/^\s/, ''))
    }
  }
  if (dataLines.length === 0) return null
  const raw = dataLines.join('\n')
  let data
  try {
    data = JSON.parse(raw)
  } catch {
    data = raw
  }
  return { name, data }
}

const toggleThinking = (idx) => {
  messages.value[idx].thinkingOpen = !messages.value[idx].thinkingOpen
}

/** 重新生成：移除该条 AI 回复（及其后的消息），用上一条用户消息重发 */
const regenerate = async (idx) => {
  const msg = messages.value[idx]
  if (!msg || msg.role !== 'assistant' || loading.value) return
  if (!access.value) await loadAccess()
  const blocked = localBlockReply()
  if (blocked) {
    messages.value[idx].content = blocked
    messages.value[idx].streaming = false
    messages.value[idx].thinking = undefined
    saveMessages()
    return
  }

  let userMsg = null
  for (let i = idx - 1; i >= 0; i--) {
    if (messages.value[i].role === 'user') {
      userMsg = messages.value[i]
      break
    }
  }
  if (!userMsg) return

  messages.value = messages.value.slice(0, idx)
  messages.value.push({ role: 'assistant', content: '', time: formatTime(), streaming: true })
  const msgIdx = messages.value.length - 1
  await streamReply(userMsg.content, msgIdx)
}

/* ---------------- 生命周期 ---------------- */
onMounted(() => {
  loadMessages()
  loadAccess()
})

onBeforeUnmount(() => {
  if (abortController) {
    abortedByUser = true
    abortController.abort()
  }
})

watch(() => userStore.userInfo?.id, () => {
  access.value = null
  loadMessages()
  loadAccess()
})
</script>

<style scoped>
.ai-chat-page {
  display: flex;
  flex-direction: column;
  height: calc(100vh - 64px);
  background: #F4F7F6;
  overflow: hidden;
}

/* ---------- 头部 ---------- */
.chat-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16px;
  padding: 18px 28px 14px;
  background: #F4F7F6;
  border-bottom: 1px solid #D7E2DC;
  flex-shrink: 0;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: 0;
}

.header-text h2 {
  margin: 0;
  font-family: "Noto Serif SC", "Songti SC", serif;
  font-size: 22px;
  font-weight: 600;
  color: #1C3D36;
}

.header-text p {
  margin: 4px 0 0;
  max-width: 46em;
  font-size: 13px;
  line-height: 1.55;
  color: #5E736C;
}

.clear-btn {
  color: #64748b;
  flex-shrink: 0;
}

.clear-btn:hover {
  color: #ef4444;
}

/* ---------- 滚动区 ---------- */
.chat-scroll {
  flex: 1;
  overflow-y: auto;
  scroll-behavior: smooth;
}

.chat-scroll::-webkit-scrollbar {
  width: 6px;
}

.chat-scroll::-webkit-scrollbar-thumb {
  background: #cbd5e1;
  border-radius: 3px;
}

.chat-column {
  max-width: 760px;
  margin: 0 auto;
  padding: 8px 28px 12px;
  display: flex;
  flex-direction: column;
  gap: 20px;
}

/* ---------- 欢迎页 ---------- */
.welcome {
  text-align: left;
  padding: 56px 4px 8px;
}

.welcome h3 {
  margin: 0;
  max-width: 8em;
  font-family: "Noto Serif SC", "Songti SC", serif;
  font-size: clamp(36px, 5vw, 52px);
  font-weight: 600;
  line-height: 1.2;
  color: #1C3D36;
}

.welcome-sub {
  margin: 16px 0 0;
  max-width: 36em;
  font-size: 15px;
  line-height: 1.7;
  color: #5E736C;
}

.quick-list {
  display: flex;
  flex-direction: column;
  max-width: 560px;
  margin-top: 32px;
}

.quick-card {
  padding: 14px 2px;
  border: none;
  border-top: 1px solid #D7E2DC;
  background: transparent;
  cursor: pointer;
  font-size: 15px;
  line-height: 1.5;
  color: #1C3D36;
  text-align: left;
  font-family: inherit;
}

.quick-card:last-child {
  border-bottom: 1px solid #D7E2DC;
}

.quick-card:hover {
  color: #B23A2F;
}

.quick-card:focus-visible {
  outline: 2px solid #1C3D36;
  outline-offset: 2px;
}

/* ---------- 消息 ---------- */
.msg-row {
  display: flex;
  gap: 12px;
  align-items: flex-start;
}

.msg-row.user {
  justify-content: flex-end;
}

.avatar {
  width: 34px;
  height: 34px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  font-size: 14px;
  font-weight: 600;
}

.assistant-avatar {
  color: #F4F7F6;
  background: #1C3D36;
}

.user-avatar {
  color: #1C3D36;
  background: #fff;
  border: 1px solid #D7E2DC;
}

.msg-main {
  max-width: calc(100% - 46px);
  min-width: 0;
}

.msg-row.user .msg-main {
  max-width: 75%;
}

.msg-meta {
  display: flex;
  align-items: baseline;
  gap: 8px;
  margin: 0 4px 5px;
}

.msg-name {
  font-size: 12.5px;
  font-weight: 600;
  color: #475569;
}

.msg-time {
  font-size: 11px;
  color: #94a3b8;
}

.typing-label {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  font-size: 11.5px;
  color: #2563eb;
}

.label-dot {
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: #2563eb;
  animation: label-pulse 1.2s ease-in-out infinite;
}

@keyframes label-pulse {
  0%, 100% { opacity: 0.25; transform: scale(0.8); }
  50% { opacity: 1; transform: scale(1.15); }
}

/* ---------- 思考过程面板 ---------- */
.thinking-panel {
  margin: 0 4px 8px;
  border: 1px solid #dbe4f5;
  border-radius: 12px;
  background: linear-gradient(135deg, #f6f8ff, #f3f5fc);
  font-size: 12.5px;
  color: #5b6b85;
  overflow: hidden;
}

.thinking-header {
  display: flex;
  align-items: center;
  gap: 7px;
  padding: 8px 12px;
  cursor: pointer;
  user-select: none;
  font-weight: 600;
  color: #475569;
}

.think-icon {
  color: #6366f1;
}

.think-pulse {
  color: #6366f1;
  animation: think-spin 1s linear infinite;
}

@keyframes think-spin {
  to { transform: rotate(360deg); }
}

.thinking-live-text {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.chevron {
  margin-left: auto;
  color: #94a3b8;
  transition: transform 0.2s ease;
}

.chevron.rotated {
  transform: rotate(180deg);
}

.thinking-body {
  padding: 8px 12px 10px;
  line-height: 1.7;
  white-space: pre-wrap;
  word-break: break-word;
  max-height: 240px;
  overflow-y: auto;
  border-top: 1px solid #e7edf8;
  color: #64748b;
  font-size: 12px;
}

.bubble {
  padding: 11px 15px;
  border-radius: 16px;
  line-height: 1.75;
  font-size: 14.5px;
  word-break: break-word;
}

.assistant-bubble {
  background: #fff;
  color: #1C3D36;
  border: none;
  border-left: 3px solid #B23A2F;
  border-radius: 2px;
  max-width: 100%;
}

.user-bubble {
  background: #1C3D36;
  color: #F4F7F6;
  border-radius: 2px 14px 14px 14px;
  white-space: pre-wrap;
}

/* 流式光标 */
.stream-cursor {
  display: inline-block;
  width: 2px;
  height: 1.05em;
  margin-left: 2px;
  background: #2563eb;
  vertical-align: -2px;
  animation: cursor-blink 1s steps(2) infinite;
}

@keyframes cursor-blink {
  0%, 49% { opacity: 1; }
  50%, 100% { opacity: 0; }
}

/* 操作按钮 */
.msg-actions {
  display: flex;
  gap: 4px;
  margin: 6px 4px 0;
  opacity: 0;
  transition: opacity 0.18s ease;
}

.msg-row:hover .msg-actions {
  opacity: 1;
}

@media (hover: none) {
  .msg-actions {
    opacity: 1;
  }
}

.action-btn {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  border: none;
  background: transparent;
  color: #64748b;
  font-size: 12px;
  cursor: pointer;
  padding: 3px 8px;
  border-radius: 8px;
  font-family: inherit;
  transition: all 0.15s ease;
}

.action-btn:hover {
  background: #eef2f7;
  color: #1d4ed8;
}

/* 预约卡片 */
.appointment-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin: 10px 4px 0;
  padding: 10px 14px;
  border: 1px solid #d3e2fd;
  border-radius: 14px;
  background: linear-gradient(135deg, #eff5ff, #f3f0ff);
  flex-wrap: wrap;
}

.appt-info {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
}

.appt-icon {
  font-size: 20px;
  width: 38px;
  height: 38px;
  border-radius: 12px;
  background: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 2px 6px rgba(37, 99, 235, 0.12);
  flex-shrink: 0;
}

.appt-title {
  font-size: 13px;
  font-weight: 600;
  color: #0f172a;
}

.appt-sub {
  font-size: 11.5px;
  color: #64748b;
  margin-top: 1px;
}

/* 打字动画 */
.typing-bubble {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 13px 16px;
}

.dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: #94a3b8;
  animation: dot-bounce 1.4s infinite ease-in-out both;
}

.dot:nth-child(1) { animation-delay: -0.32s; }
.dot:nth-child(2) { animation-delay: -0.16s; }
.dot:nth-child(3) { animation-delay: 0s; }

@keyframes dot-bounce {
  0%, 80%, 100% { transform: scale(0.6); opacity: 0.5; }
  40% { transform: scale(1); opacity: 1; }
}

/* ---------- Markdown 样式 ---------- */
.bubble-text :deep(p) {
  margin: 0.5em 0;
}

.bubble-text :deep(p:first-child) {
  margin-top: 0;
}

.bubble-text :deep(p:last-child) {
  margin-bottom: 0;
}

.bubble-text :deep(h1),
.bubble-text :deep(h2),
.bubble-text :deep(h3),
.bubble-text :deep(h4) {
  margin: 0.8em 0 0.4em;
  line-height: 1.4;
}

.bubble-text :deep(h1:first-child),
.bubble-text :deep(h2:first-child),
.bubble-text :deep(h3:first-child),
.bubble-text :deep(h4:first-child) {
  margin-top: 0;
}

.bubble-text :deep(strong) {
  font-weight: 600;
  color: #0f172a;
}

.bubble-text :deep(ul),
.bubble-text :deep(ol) {
  margin: 0.5em 0;
  padding-left: 1.5em;
}

.bubble-text :deep(li) {
  margin: 0.35em 0;
}

.bubble-text :deep(li::marker) {
  color: #2563eb;
}

.bubble-text :deep(a) {
  color: #2563eb;
  text-decoration: none;
  border-bottom: 1px dashed #93c5fd;
}

.bubble-text :deep(a:hover) {
  color: #1d4ed8;
  border-bottom-style: solid;
}

.bubble-text :deep(code) {
  background: #eef2f7;
  color: #be185d;
  padding: 2px 6px;
  border-radius: 5px;
  font-family: 'SF Mono', 'Consolas', 'Monaco', monospace;
  font-size: 0.88em;
}

.user-bubble :deep(code) {
  background: rgba(255, 255, 255, 0.22);
  color: #fff;
}

.bubble-text :deep(pre) {
  background: #0f172a;
  color: #e2e8f0;
  padding: 12px 14px;
  border-radius: 10px;
  overflow-x: auto;
  margin: 0.6em 0;
  line-height: 1.5;
}

.bubble-text :deep(pre code) {
  background: transparent;
  color: inherit;
  padding: 0;
  font-size: 13px;
}

.bubble-text :deep(blockquote) {
  margin: 0.6em 0;
  padding: 4px 12px;
  border-left: 3px solid #93c5fd;
  background: #f0f6ff;
  border-radius: 0 8px 8px 0;
  color: #475569;
}

.bubble-text :deep(blockquote p) {
  margin: 0.3em 0;
}

.bubble-text :deep(hr) {
  border: none;
  border-top: 1px solid #e2e8f0;
  margin: 0.8em 0;
}

.bubble-text :deep(table) {
  border-collapse: collapse;
  margin: 0.6em 0;
  width: 100%;
  font-size: 13px;
}

.bubble-text :deep(th),
.bubble-text :deep(td) {
  border: 1px solid #e2e8f0;
  padding: 6px 10px;
  text-align: left;
}

.bubble-text :deep(th) {
  background: #f1f5f9;
  font-weight: 600;
}

.bubble-text :deep(tr:nth-child(even) td) {
  background: #f8fafc;
}

/* ---------- 底部输入区 ---------- */
.chat-footer {
  flex-shrink: 0;
  padding: 4px 20px 16px;
}

.input-panel {
  max-width: 780px;
  margin: 0 auto;
  background: #fff;
  border: 1px solid #D7E2DC;
  border-radius: 2px;
  padding: 10px 12px 8px;
}

.input-panel:focus-within {
  border-color: #1C3D36;
  box-shadow: 0 0 0 1px #1C3D36;
}

.input-buttons :deep(.el-button--primary) {
  --el-button-bg-color: #B23A2F;
  --el-button-border-color: #B23A2F;
  --el-button-hover-bg-color: #8E2E25;
  --el-button-hover-border-color: #8E2E25;
  --el-button-active-bg-color: #8E2E25;
  --el-button-active-border-color: #8E2E25;
  border-radius: 2px;
}

.chat-textarea :deep(.el-textarea__inner) {
  border: none;
  box-shadow: none !important;
  background: transparent;
  padding: 2px 6px;
  font-size: 14px;
  line-height: 1.6;
  color: #0f172a;
}

.chat-textarea :deep(.el-textarea__inner::placeholder) {
  color: #94a3b8;
}

.input-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  margin-top: 4px;
  padding: 0 4px;
}

.context-note {
  margin: 6px 0 0;
  color: #64748b;
  font-size: 12px;
  line-height: 1.6;
}

.input-hint {
  font-size: 11px;
  color: #64748b;
  white-space: normal;
  line-height: 1.5;
}

.input-buttons {
  flex-shrink: 0;
  display: flex;
  gap: 6px;
}

.input-buttons .el-button {
  margin: 0;
}

@media (max-width: 768px) {
  .ai-chat-page {
    height: calc(100vh - 56px);
  }

  .chat-header,
  .chat-column,
  .chat-footer {
    padding-left: 14px;
    padding-right: 14px;
  }

  .welcome {
    padding-top: 28px;
  }
}

@media (prefers-reduced-motion: reduce) {
  .stream-cursor,
  .label-dot,
  .dot,
  .think-pulse {
    animation: none;
  }
}
</style>
