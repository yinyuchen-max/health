<template>
  <div class="doctor-pet-wrapper">
    <!-- 桌宠本体 -->
    <div
      ref="petRef"
      class="doctor-pet"
      :class="{ 'is-dragging': isDragging, 'is-thinking': isThinking }"
      :style="petStyle"
      @mousedown="onMouseDown"
      @mouseenter="isHovered = true"
      @mouseleave="isHovered = false"
    >
      <!-- 对话气泡 -->
      <Transition name="bubble">
        <div v-if="showBubble" class="speech-bubble" @click.stop>
          <span class="bubble-text">{{ bubbleText }}</span>
          <div class="bubble-close" @click.stop="showBubble = false">×</div>
        </div>
      </Transition>

      <!-- SVG 医生角色 -->
      <svg class="pet-svg" viewBox="0 0 120 160" xmlns="http://www.w3.org/2000/svg">
        <!-- 身体/白大褂 -->
        <ellipse cx="60" cy="130" rx="35" ry="28" fill="#fff" stroke="#E8F0FE" stroke-width="1.5"/>
        <!-- 衣领 -->
        <path d="M 45 108 L 60 118 L 75 108" fill="none" stroke="#3B82F6" stroke-width="2" stroke-linecap="round"/>
        
        <!-- 听诊器 -->
        <path d="M 50 112 Q 45 125 50 135" fill="none" stroke="#64748B" stroke-width="2" stroke-linecap="round"/>
        <circle cx="50" cy="137" r="4" fill="#64748B"/>
        <circle cx="50" cy="137" r="2" fill="#94A3B8"/>
        
        <!-- 头部 -->
        <circle cx="60" cy="60" r="38" fill="#FDDCB5"/>
        <!-- 头发 -->
        <path d="M 25 50 Q 25 20 60 18 Q 95 20 95 50" fill="#4A3728"/>
        <path d="M 28 48 Q 30 25 60 22 Q 90 25 92 48" fill="#5C4033"/>
        
        <!-- 护士帽/医生帽 -->
        <rect x="40" y="12" width="40" height="16" rx="4" fill="#fff" stroke="#E8F0FE" stroke-width="1"/>
        <path d="M 55 15 L 55 25 M 60 15 L 60 25 M 65 15 L 65 25" stroke="#F43F5E" stroke-width="1.5"/>
        <path d="M 50 20 L 70 20" stroke="#F43F5E" stroke-width="1.5"/>
        
        <!-- 眼睛 -->
        <g class="eyes-group">
          <ellipse class="eye-white left" :class="{ blinking: isBlinking }" cx="45" cy="62" rx="8" ry="9" fill="#fff"/>
          <ellipse class="eye-white right" :class="{ blinking: isBlinking }" cx="75" cy="62" rx="8" ry="9" fill="#fff"/>
          <circle class="pupil left" :style="pupilStyle" cx="45" cy="63" r="5" fill="#2D3748"/>
          <circle class="pupil right" :style="pupilStyle" cx="75" cy="63" r="5" fill="#2D3748"/>
          <!-- 眼睛高光 -->
          <circle class="eye-shine" :style="pupilStyle" cx="47" cy="61" r="2" fill="#fff"/>
          <circle class="eye-shine" :style="pupilStyle" cx="77" cy="61" r="2" fill="#fff"/>
        </g>
        
        <!-- 腮红 -->
        <ellipse cx="32" cy="72" rx="6" ry="4" fill="rgba(255,150,150,0.4)"/>
        <ellipse cx="88" cy="72" rx="6" ry="4" fill="rgba(255,150,150,0.4)"/>
        
        <!-- 嘴巴 -->
        <path :class="['mouth-path', { 'mouth-happy': isHovered }]" 
              :d="isHovered ? 'M 50 80 Q 60 90 70 80' : 'M 52 80 Q 60 86 68 80'" 
              fill="none" stroke="#C4A882" stroke-width="2.5" stroke-linecap="round"/>
        
        <!-- 手臂 -->
        <g :class="['arm-left', { wave: isHovered }]">
          <ellipse cx="22" cy="125" rx="8" ry="18" fill="#fff" stroke="#E8F0FE" stroke-width="1"/>
          <circle cx="22" cy="140" r="6" fill="#FDDCB5"/>
        </g>
        <g class="arm-right">
          <ellipse cx="98" cy="125" rx="8" ry="18" fill="#fff" stroke="#E8F0FE" stroke-width="1"/>
          <circle cx="98" cy="140" r="6" fill="#FDDCB5"/>
        </g>
        
        <!-- 口袋 -->
        <rect x="50" y="125" width="20" height="12" rx="3" fill="none" stroke="#E8F0FE" stroke-width="1"/>
        <!-- 口袋里的笔 -->
        <rect x="55" y="122" width="3" height="8" rx="1" fill="#3B82F6"/>
        <rect x="62" y="123" width="3" height="7" rx="1" fill="#F43F5E"/>
      </svg>

      <!-- 底部阴影 -->
      <div class="pet-shadow"></div>

      <!-- 思考动画点 -->
      <div v-if="isThinking" class="thinking-dots">
        <span></span><span></span><span></span>
      </div>
    </div>

    <!-- 浮动聊天面板 -->
    <Transition name="panel">
      <div v-if="panelOpen" class="chat-panel">
        <div class="panel-header">
          <div class="panel-header-left">
            <div class="panel-avatar">
              <svg viewBox="0 0 40 40" width="24" height="24">
                <circle cx="20" cy="18" r="12" fill="#FDDCB5"/>
                <path d="M 10 15 Q 10 5 20 4 Q 30 5 30 15" fill="#5C4033"/>
                <rect x="14" y="0" width="12" height="6" rx="2" fill="#fff" stroke="#E8F0FE" stroke-width="0.5"/>
                <path d="M 17 1 L 17 5 M 20 1 L 20 5 M 23 1 L 23 5" stroke="#F43F5E" stroke-width="0.8"/>
                <circle cx="16" cy="18" r="2" fill="#2D3748"/>
                <circle cx="24" cy="18" r="2" fill="#2D3748"/>
                <path d="M 16 23 Q 20 26 24 23" fill="none" stroke="#C4A882" stroke-width="1.2" stroke-linecap="round"/>
              </svg>
            </div>
            <div class="panel-title">
              <h4>AI 健康顾问</h4>
              <span class="panel-status">
                <span class="status-dot" :class="{ online: !isThinking }"></span>
                {{ isThinking ? '思考中...' : '在线' }}
              </span>
            </div>
          </div>
          <div class="panel-actions">
            <button class="panel-btn" @click.stop="handleClearChat" title="清空对话">
              <el-icon :size="14"><Delete /></el-icon>
            </button>
            <button class="panel-btn" @click.stop="panelOpen = false" title="关闭">
              <el-icon :size="14"><Close /></el-icon>
            </button>
          </div>
        </div>

        <!-- 消息区 -->
        <div class="panel-messages" ref="panelScrollRef">
          <div v-if="panelMessages.length === 0" class="panel-welcome">
            <div class="welcome-avatar">
              <svg viewBox="0 0 80 80" width="56" height="56">
                <circle cx="40" cy="36" r="24" fill="#FDDCB5"/>
                <path d="M 20 30 Q 20 10 40 8 Q 60 10 60 30" fill="#5C4033"/>
                <rect x="28" y="2" width="24" height="10" rx="3" fill="#fff" stroke="#E8F0FE" stroke-width="1"/>
                <path d="M 34 4 L 34 10 M 40 4 L 40 10 M 46 4 L 46 10" stroke="#F43F5E" stroke-width="1.2"/>
                <path d="M 30 8 L 50 8" stroke="#F43F5E" stroke-width="1.2"/>
                <circle cx="33" cy="36" r="3.5" fill="#2D3748"/>
                <circle cx="47" cy="36" r="3.5" fill="#2D3748"/>
                <circle cx="34.5" cy="34.5" r="1.5" fill="#fff"/>
                <circle cx="48.5" cy="34.5" r="1.5" fill="#fff"/>
                <path d="M 32 46 Q 40 52 48 46" fill="none" stroke="#C4A882" stroke-width="2" stroke-linecap="round"/>
                <ellipse cx="24" cy="42" rx="4" ry="3" fill="rgba(255,150,150,0.4)"/>
                <ellipse cx="56" cy="42" rx="4" ry="3" fill="rgba(255,150,150,0.4)"/>
                <ellipse cx="40" cy="68" rx="18" ry="12" fill="#fff" stroke="#E8F0FE" stroke-width="1"/>
                <path d="M 32 60 L 40 66 L 48 60" fill="none" stroke="#3B82F6" stroke-width="1.5" stroke-linecap="round"/>
              </svg>
            </div>
            <p class="welcome-text">你好！我是小健医生</p>
            <p class="welcome-sub">有任何健康问题都可以问我哦~</p>
            <div class="quick-questions">
              <button
                v-for="q in quickQuestions"
                :key="q.text"
                class="quick-btn"
                @click.stop="sendPanelMessage(q.text)"
              >
                <span>{{ q.icon }}</span> {{ q.text }}
              </button>
            </div>
          </div>

          <div
            v-for="(msg, idx) in panelMessages"
            :key="idx"
            :class="['panel-msg', msg.role]"
          >
            <div v-if="msg.role === 'assistant'" class="msg-avatar-small">
              <svg viewBox="0 0 28 28" width="20" height="20">
                <circle cx="14" cy="12" r="8" fill="#FDDCB5"/>
                <path d="M 7 10 Q 7 3 14 2 Q 21 3 21 10" fill="#5C4033"/>
                <circle cx="11" cy="12" r="1.5" fill="#2D3748"/>
                <circle cx="17" cy="12" r="1.5" fill="#2D3748"/>
                <path d="M 11 15 Q 14 17 17 15" fill="none" stroke="#C4A882" stroke-width="1" stroke-linecap="round"/>
              </svg>
            </div>
            <div class="msg-bubble">
              <div v-if="msg.role === 'assistant' && msg.streaming && !msg.content" class="typing-indicator">
                <span></span><span></span><span></span>
              </div>
              <div v-else class="msg-content" v-html="renderMarkdown(msg.content)"></div>
            </div>
            <div v-if="msg.role === 'user'" class="msg-avatar-small user-avatar-small">
              {{ userInitial }}
            </div>
          </div>
        </div>

        <!-- 输入区 -->
        <div class="panel-input">
          <textarea
            v-model="panelInput"
            placeholder="输入你的健康问题…"
            rows="1"
            :disabled="isThinking"
            @keydown.enter.exact="onPanelEnter"
            @input="autoResizeTextarea"
            ref="panelTextareaRef"
          ></textarea>
          <button
            v-if="isThinking"
            class="send-btn stop-btn"
            @click.stop="stopStreaming"
          >
            <el-icon :size="14"><VideoPause /></el-icon>
          </button>
          <button
            v-else
            class="send-btn"
            :disabled="!panelInput.trim()"
            @click.stop="handlePanelSend"
          >
            <el-icon :size="14"><Promotion /></el-icon>
          </button>
        </div>
      </div>
    </Transition>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onBeforeUnmount, nextTick, watch } from 'vue'
import { Close, Delete, Promotion, VideoPause } from '@element-plus/icons-vue'
import { useUserStore } from '../store/user'
import { renderMarkdown } from '../utils/markdown'
import request from '../utils/request'

const userStore = useUserStore()

// ===== 桌宠状态 =====
const petRef = ref(null)
const isDragging = ref(false)
const isHovered = ref(false)
const isBlinking = ref(false)
const isThinking = ref(false)
const showBubble = ref(false)
const bubbleText = ref('')
const panelOpen = ref(false)

// 位置
const petPos = ref({ x: 30, y: 80 })
const dragStart = ref({ x: 0, y: 0, posX: 0, posY: 0 })
const hasMoved = ref(false)

const petStyle = computed(() => ({
  right: `${petPos.value.x}px`,
  bottom: `${petPos.value.y}px`
}))

// 眼球追踪
const pupilOffset = ref({ x: 0, y: 0 })
const pupilStyle = computed(() => ({
  transform: `translate(${pupilOffset.value.x}px, ${pupilOffset.value.y}px)`
}))

// ===== 聊天逻辑 =====
const panelMessages = ref([])
const panelInput = ref('')
const panelScrollRef = ref(null)
const panelTextareaRef = ref(null)
let abortController = null
let abortedByUser = false
let rafPending = false

const userInitial = computed(() =>
  (userStore.userInfo?.username || '我').slice(0, 1).toUpperCase()
)

const quickQuestions = [
  { icon: '💧', text: '每天喝多少水？' },
  { icon: '🌙', text: '改善睡眠' },
  { icon: '🧘', text: '缓解腰痛' },
  { icon: '🏃', text: '减脂运动' }
]

// ===== 历史 =====
const historyKey = () => `ai-chat-pet-${userStore.userInfo?.id || 'default'}`

const loadMessages = () => {
  const saved = localStorage.getItem(historyKey())
  if (saved) {
    try { panelMessages.value = JSON.parse(saved) } catch { panelMessages.value = [] }
  }
}

const saveMessages = () => {
  try {
    const compact = panelMessages.value.map(({ role, content, time }) => ({ role, content, time }))
    localStorage.setItem(historyKey(), JSON.stringify(compact))
  } catch {}
}

// ===== 工具 =====
const formatTime = () =>
  new Date().toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })

const scrollToBottom = async () => {
  await nextTick()
  if (panelScrollRef.value) {
    panelScrollRef.value.scrollTo({ top: panelScrollRef.value.scrollHeight, behavior: 'smooth' })
  }
}

const scheduleScroll = () => {
  if (rafPending) return
  rafPending = true
  requestAnimationFrame(() => { rafPending = false; scrollToBottom() })
}

// ===== 拖拽桌宠（修复点击问题）=====
const DRAG_THRESHOLD = 5

const onMouseDown = (e) => {
  if (e.target.closest('.speech-bubble')) return
  isDragging.value = false
  hasMoved.value = false
  dragStart.value = { 
    x: e.clientX, 
    y: e.clientY, 
    posX: petPos.value.x, 
    posY: petPos.value.y 
  }
  document.addEventListener('mousemove', onMouseMove)
  document.addEventListener('mouseup', onMouseUp)
  e.preventDefault()
}

const onMouseMove = (e) => {
  const dx = e.clientX - dragStart.value.x
  const dy = e.clientY - dragStart.value.y
  
  // 只有移动超过阈值才算拖拽
  if (!hasMoved.value && Math.hypot(dx, dy) > DRAG_THRESHOLD) {
    hasMoved.value = true
    isDragging.value = true
  }
  
  if (isDragging.value) {
    petPos.value = {
      x: Math.max(0, Math.min(window.innerWidth - 100, dragStart.value.posX - dx)),
      y: Math.max(0, Math.min(window.innerHeight - 100, dragStart.value.posY + dy))
    }
  }
}

const onMouseUp = () => {
  document.removeEventListener('mousemove', onMouseMove)
  document.removeEventListener('mouseup', onMouseUp)
  
  // 如果没有移动，则视为点击
  if (!hasMoved.value) {
    panelOpen.value = !panelOpen.value
    if (panelOpen.value) {
      showBubble.value = false
      scrollToBottom()
      nextTick(() => panelTextareaRef.value?.focus())
    }
  }
  
  isDragging.value = false
  hasMoved.value = false
}

// ===== 眼球追踪 =====
const onMouseMoveGlobal = (e) => {
  if (!petRef.value || isDragging.value) return
  const rect = petRef.value.getBoundingClientRect()
  const cx = rect.left + rect.width / 2
  const cy = rect.top + rect.height * 0.35
  const dx = e.clientX - cx
  const dy = e.clientY - cy
  const dist = Math.max(Math.hypot(dx, dy), 1)
  const maxMove = 2.5
  pupilOffset.value = {
    x: Math.max(-maxMove, Math.min(maxMove, (dx / dist) * maxMove)),
    y: Math.max(-maxMove, Math.min(maxMove, (dy / dist) * maxMove))
  }
}

// ===== 眨眼 =====
let blinkTimer = null
const startBlinking = () => {
  const doBlink = () => {
    isBlinking.value = true
    setTimeout(() => { isBlinking.value = false }, 150)
    blinkTimer = setTimeout(doBlink, 2500 + Math.random() * 3000)
  }
  blinkTimer = setTimeout(doBlink, 2000 + Math.random() * 2000)
}

// ===== 气泡 =====
const tips = [
  '今天喝水了吗？💧',
  '久坐记得活动一下 🧘',
  '保持好心情 😊',
  '每天运动 30 分钟 🏃',
  '有什么问我哦~',
  '点我聊天 👆',
  '早睡早起 🌙',
  '多吃蔬果 🥦'
]

let tipTimer = null
const startTips = () => {
  const showTip = () => {
    if (!panelOpen.value && !isDragging.value) {
      bubbleText.value = tips[Math.floor(Math.random() * tips.length)]
      showBubble.value = true
      setTimeout(() => { showBubble.value = false }, 4000)
    }
    tipTimer = setTimeout(showTip, 15000 + Math.random() * 20000)
  }
  tipTimer = setTimeout(showTip, 6000 + Math.random() * 8000)
}

// ===== 发送消息 =====
const handlePanelSend = async () => {
  const text = (panelInput.value || '').trim()
  if (!text || isThinking.value) return
  panelInput.value = ''
  await sendPanelMessage(text)
}

const sendPanelMessage = async (text) => {
  if (isThinking.value) return
  panelMessages.value.push({ role: 'user', content: text, time: formatTime() })
  panelMessages.value.push({ role: 'assistant', content: '', time: formatTime(), streaming: true })
  const msgIdx = panelMessages.value.length - 1
  await scrollToBottom()
  await streamReply(text, msgIdx)
}

const onPanelEnter = (e) => {
  if (e.shiftKey || e.isComposing || e.keyCode === 229) return
  e.preventDefault()
  handlePanelSend()
}

const autoResizeTextarea = () => {
  const el = panelTextareaRef.value
  if (el) {
    el.style.height = 'auto'
    el.style.height = Math.min(el.scrollHeight, 80) + 'px'
  }
}

const stopStreaming = () => {
  if (!isThinking.value) return
  abortedByUser = true
  if (abortController) abortController.abort()
}

const streamReply = async (userText, msgIdx) => {
  abortedByUser = false
  abortController = new AbortController()
  isThinking.value = true
  let content = ''
  const setContent = (val) => { panelMessages.value[msgIdx].content = val }

  try {
    const base = (import.meta.env.VITE_API_BASE_URL?.trim() || '/api').replace(/\/+$/, '')
    const url = `${base}/chat/send/stream`
    const headers = { 'Content-Type': 'application/json', Accept: 'text/event-stream' }
    if (userStore.token) headers.Authorization = `Bearer ${userStore.token.trim()}`

    const res = await fetch(url, {
      method: 'POST', headers,
      body: JSON.stringify({ message: userText }),
      signal: abortController.signal
    })

    if (!res.ok || !res.body) throw new Error(`HTTP ${res.status}`)
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
        const evt = parseSse(block)
        if (!evt) continue
        if (evt.name === 'token') {
          content += evt.data
          setContent(content)
          scheduleScroll()
        } else if (evt.name === 'done') {
          setContent(evt.data || content)
          return
        } else if (evt.name === 'error') {
          throw new Error(evt.data || 'AI 服务出错')
        }
      }
    }
    throw new Error('连接关闭')
  } catch (e) {
    if (abortedByUser) {
      setContent(content ? `${content}\n\n*（已停止）*` : '*（已停止）*')
    } else {
      setContent('')
      try {
        const res = await request.post('/chat/send', { message: userText })
        const reply = res?.data?.reply || res?.reply
        setContent(reply || '抱歉，AI 暂时不可用~')
      } catch {
        setContent('抱歉，AI 暂时不可用~')
      }
    }
  } finally {
    if (abortController) { abortController.abort(); abortController = null }
    panelMessages.value[msgIdx].streaming = false
    isThinking.value = false
    saveMessages()
    await scrollToBottom()
  }
}

const parseSse = (block) => {
  let name = 'message'
  const dataLines = []
  for (const rawLine of block.split('\n')) {
    const line = rawLine.replace(/\r$/, '')
    if (line.startsWith('event:')) name = line.slice(6).trim()
    else if (line.startsWith('data:')) dataLines.push(line.slice(5).replace(/^\s/, ''))
  }
  if (dataLines.length === 0) return null
  const raw = dataLines.join('\n')
  let data
  try { data = JSON.parse(raw) } catch { data = raw }
  return { name, data }
}

const handleClearChat = async () => {
  panelMessages.value = []
  saveMessages()
}

// ===== 生命周期 =====
onMounted(() => {
  loadMessages()
  startBlinking()
  startTips()
  document.addEventListener('mousemove', onMouseMoveGlobal)
})

onBeforeUnmount(() => {
  if (blinkTimer) clearTimeout(blinkTimer)
  if (tipTimer) clearTimeout(tipTimer)
  if (abortController) { abortedByUser = true; abortController.abort() }
  document.removeEventListener('mousemove', onMouseMoveGlobal)
  document.removeEventListener('mousemove', onMouseMove)
  document.removeEventListener('mouseup', onMouseUp)
})

watch(() => userStore.userInfo?.id, () => { loadMessages() })
</script>

<style scoped>
.doctor-pet-wrapper {
  pointer-events: none;
  position: fixed;
  inset: 0;
  z-index: 9999;
}

/* ===== 桌宠本体 ===== */
.doctor-pet {
  pointer-events: auto;
  position: absolute;
  width: 100px;
  height: 140px;
  cursor: pointer;
  user-select: none;
  transition: filter 0.3s;
  animation: pet-float 3s ease-in-out infinite;
}

.doctor-pet.is-dragging { 
  animation: none; 
  cursor: grabbing;
}

@keyframes pet-float {
  0%, 100% { transform: translateY(0); }
  50% { transform: translateY(-8px); }
}

.doctor-pet.is-thinking {
  animation: pet-think 1.5s ease-in-out infinite;
}

@keyframes pet-think {
  0%, 100% { transform: translateY(0) rotate(0deg); }
  25% { transform: translateY(-4px) rotate(-3deg); }
  75% { transform: translateY(-4px) rotate(3deg); }
}

.pet-svg {
  width: 100%;
  height: 100%;
  overflow: visible;
}

/* 眨眼动画 */
.eye-white.blinking {
  ry: 1.5;
}

/* 手臂挥手动画 */
.arm-left {
  transform-origin: 30px 110px;
  transition: transform 0.3s;
}

.arm-left.wave {
  animation: arm-wave 0.5s ease-in-out 3;
}

@keyframes arm-wave {
  0%, 100% { transform: rotate(0deg); }
  50% { transform: rotate(-15deg); }
}

/* 底部阴影 */
.pet-shadow {
  position: absolute;
  bottom: -4px;
  left: 50%;
  transform: translateX(-50%);
  width: 60px;
  height: 10px;
  background: rgba(0,0,0,0.1);
  border-radius: 50%;
  filter: blur(3px);
}

/* 思考动画 */
.thinking-dots {
  position: absolute;
  top: -10px;
  left: 50%;
  transform: translateX(-50%);
  display: flex;
  gap: 4px;
}

.thinking-dots span {
  width: 6px;
  height: 6px;
  background: #3B82F6;
  border-radius: 50%;
  animation: dot-bounce 1.2s ease-in-out infinite;
}

.thinking-dots span:nth-child(2) { animation-delay: 0.2s; }
.thinking-dots span:nth-child(3) { animation-delay: 0.4s; }

@keyframes dot-bounce {
  0%, 100% { opacity: 0.3; transform: translateY(0); }
  50% { opacity: 1; transform: translateY(-5px); }
}

/* ===== 对话气泡 ===== */
.speech-bubble {
  pointer-events: auto;
  position: absolute;
  bottom: 100%;
  right: 10px;
  margin-bottom: 10px;
  background: #fff;
  border-radius: 16px 16px 4px 16px;
  padding: 10px 30px 10px 14px;
  box-shadow: 0 4px 20px rgba(0,0,0,0.1);
  font-size: 13px;
  color: #334155;
  max-width: 180px;
  line-height: 1.5;
}

.bubble-close {
  position: absolute;
  top: 4px;
  right: 8px;
  font-size: 16px;
  color: #94A3B8;
  cursor: pointer;
  line-height: 1;
}

.bubble-close:hover { color: #64748B; }

.bubble-enter-active { transition: all 0.3s cubic-bezier(0.34, 1.56, 0.64, 1); }
.bubble-leave-active { transition: all 0.2s ease; }
.bubble-enter-from, .bubble-leave-to {
  opacity: 0;
  transform: translateY(8px) scale(0.8);
}

/* ===== 聊天面板 ===== */
.chat-panel {
  pointer-events: auto;
  position: absolute;
  right: 20px;
  bottom: 160px;
  width: 380px;
  height: 520px;
  background: #fff;
  border-radius: 20px;
  box-shadow: 0 12px 48px rgba(0,0,0,0.15);
  display: flex;
  flex-direction: column;
  overflow: hidden;
  border: 1px solid #E8F0FE;
}

.panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 16px;
  background: linear-gradient(135deg, #EBF5FF, #F0F7FF);
  border-bottom: 1px solid #E8F0FE;
  flex-shrink: 0;
}

.panel-header-left {
  display: flex;
  align-items: center;
  gap: 10px;
}

.panel-avatar {
  width: 38px;
  height: 38px;
  background: linear-gradient(135deg, #EBF5FF, #fff);
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 2px 8px rgba(59, 130, 246, 0.15);
}

.panel-title h4 {
  margin: 0;
  font-size: 14px;
  font-weight: 600;
  color: #1E293B;
}

.panel-status {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 11px;
  color: #64748B;
}

.status-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #CBD5E1;
}

.status-dot.online { background: #10B981; }

.panel-actions {
  display: flex;
  gap: 4px;
}

.panel-btn {
  width: 30px;
  height: 30px;
  border: none;
  background: rgba(255,255,255,0.8);
  border-radius: 8px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #64748B;
  transition: all 0.2s;
}

.panel-btn:hover {
  background: #fff;
  color: #3B82F6;
}

/* 消息区 */
.panel-messages {
  flex: 1;
  overflow-y: auto;
  padding: 16px;
  background: #F8FAFC;
}

.panel-messages::-webkit-scrollbar { width: 4px; }
.panel-messages::-webkit-scrollbar-thumb {
  background: #CBD5E1;
  border-radius: 2px;
}

.panel-welcome {
  text-align: center;
  padding: 20px 12px;
}

.welcome-avatar {
  margin-bottom: 12px;
}

.welcome-text {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
  color: #1E293B;
}

.welcome-sub {
  margin: 6px 0 18px;
  font-size: 12px;
  color: #94A3B8;
}

.quick-questions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  justify-content: center;
}

.quick-btn {
  padding: 8px 14px;
  background: #fff;
  border: 1px solid #E8F0FE;
  border-radius: 20px;
  font-size: 12px;
  color: #475569;
  cursor: pointer;
  transition: all 0.2s;
  display: flex;
  align-items: center;
  gap: 4px;
}

.quick-btn:hover {
  background: #EBF5FF;
  border-color: #3B82F6;
  color: #3B82F6;
  transform: translateY(-1px);
}

/* 消息 */
.panel-msg {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  margin-bottom: 14px;
}

.panel-msg.user { flex-direction: row-reverse; }

.msg-avatar-small {
  width: 30px;
  height: 30px;
  border-radius: 50%;
  background: #EBF5FF;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  overflow: hidden;
}

.user-avatar-small {
  background: #3B82F6;
  color: #fff;
  font-size: 12px;
  font-weight: 600;
}

.msg-bubble {
  max-width: 75%;
  padding: 10px 14px;
  border-radius: 16px;
  font-size: 13px;
  line-height: 1.6;
}

.panel-msg.assistant .msg-bubble {
  background: #fff;
  color: #334155;
  border: 1px solid #F0F4F8;
  border-top-left-radius: 4px;
}

.panel-msg.user .msg-bubble {
  background: #3B82F6;
  color: #fff;
  border-top-right-radius: 4px;
}

.msg-content :deep(p) { margin: 0 0 6px; }
.msg-content :deep(p:last-child) { margin-bottom: 0; }
.msg-content :deep(code) {
  background: rgba(0,0,0,0.06);
  padding: 2px 5px;
  border-radius: 4px;
  font-size: 12px;
}

/* 打字指示器 */
.typing-indicator {
  display: flex;
  gap: 4px;
  padding: 4px 0;
}

.typing-indicator span {
  width: 7px;
  height: 7px;
  background: #94A3B8;
  border-radius: 50%;
  animation: typing-dot 1.4s ease-in-out infinite;
}

.typing-indicator span:nth-child(2) { animation-delay: 0.2s; }
.typing-indicator span:nth-child(3) { animation-delay: 0.4s; }

@keyframes typing-dot {
  0%, 100% { opacity: 0.3; transform: scale(0.8); }
  50% { opacity: 1; transform: scale(1); }
}

/* 输入区 */
.panel-input {
  display: flex;
  align-items: flex-end;
  gap: 8px;
  padding: 12px 14px;
  border-top: 1px solid #F0F4F8;
  background: #fff;
  flex-shrink: 0;
}

.panel-input textarea {
  flex: 1;
  border: none;
  outline: none;
  resize: none;
  font-size: 13px;
  font-family: inherit;
  line-height: 1.5;
  padding: 10px 14px;
  background: #F8FAFC;
  border-radius: 14px;
  color: #334155;
  max-height: 80px;
}

.panel-input textarea::placeholder { color: #94A3B8; }

.send-btn {
  width: 36px;
  height: 36px;
  border: none;
  background: #3B82F6;
  border-radius: 50%;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  transition: all 0.2s;
  flex-shrink: 0;
}

.send-btn:hover { background: #2563EB; transform: scale(1.05); }
.send-btn:disabled { background: #CBD5E1; cursor: not-allowed; transform: none; }

.stop-btn { background: #F43F5E; }
.stop-btn:hover { background: #E11D48; }

/* 面板动画 */
.panel-enter-active { transition: all 0.35s cubic-bezier(0.34, 1.56, 0.64, 1); }
.panel-leave-active { transition: all 0.25s ease-in; }
.panel-enter-from {
  opacity: 0;
  transform: translateY(20px) scale(0.9);
}
.panel-leave-to {
  opacity: 0;
  transform: translateY(10px) scale(0.95);
}

/* 移动端 */
@media (max-width: 768px) {
  .chat-panel {
    right: 8px;
    left: 8px;
    bottom: 140px;
    width: auto;
    height: 60vh;
    max-height: 500px;
  }

  .doctor-pet {
    transform: scale(0.85);
    transform-origin: bottom center;
  }
}
</style>
