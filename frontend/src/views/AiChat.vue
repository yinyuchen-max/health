<template>
  <div class="ai-chat-page">
    <el-card class="page-hero">
      <div class="page-hero-content">
        <div>
          <p class="hero-kicker">AI Health Assistant</p>
          <h2>AI 健康顾问</h2>
          <p>向 AI 咨询健康、饮食、运动、睡眠等问题，获取专业建议（仅供参考，不替代医生诊断）</p>
        </div>
        <el-button 
          v-if="messages.length > 0"
          type="danger" 
          plain
          size="small"
          @click="handleClearHistory"
        >
          清空对话
        </el-button>
      </div>
    </el-card>

    <div class="chat-container">
      <div class="chat-messages" ref="msgContainer">
        <div v-if="messages.length === 0" class="welcome-hint">
          <el-icon :size="48" color="#93c5fd"><ChatDotRound /></el-icon>
          <p>👋 你好！我是你的 AI 健康顾问，有什么健康问题想咨询吗？</p>
          <div class="quick-questions">
            <el-tag
              v-for="q in quickQuestions"
              :key="q"
              class="quick-tag"
              @click="sendMessage(q)"
            >{{ q }}</el-tag>
          </div>
        </div>

        <div
          v-for="(msg, idx) in messages"
          :key="idx"
          :class="['message-row', msg.role]"
        >
          <div class="message-bubble">
            <div class="bubble-text" v-html="renderMarkdown(msg.content)"></div>
            <div class="bubble-time">{{ msg.time }}</div>
          </div>
        </div>

        <div v-if="loading" class="message-row assistant">
          <div class="message-bubble typing">
            <span class="dot"></span><span class="dot"></span><span class="dot"></span>
          </div>
        </div>
      </div>

      <div class="chat-input-area">
        <el-input
          v-model="inputText"
          type="textarea"
          :rows="2"
          placeholder="输入你的健康问题..."
          @keydown.enter.exact.prevent="handleSend"
          :disabled="loading"
          resize="none"
        />
        <el-button
          type="primary"
          :icon="Promotion"
          :disabled="!inputText.trim() || loading"
          @click="handleSend"
          :loading="loading"
        >发送</el-button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { nextTick, ref, onMounted, watch } from 'vue'
import { ChatDotRound, Promotion } from '@element-plus/icons-vue'
import request from '../utils/request'
import { marked } from 'marked'
import { useUserStore } from '../store/user'
import { ElMessageBox, ElMessage } from 'element-plus'

const userStore = useUserStore()
const messages = ref([])
const inputText = ref('')
const loading = ref(false)
const msgContainer = ref(null)

// 从 localStorage 加载对话历史
const loadMessages = () => {
  const userId = userStore.userInfo?.id || 'default'
  const saved = localStorage.getItem(`ai-chat-history-${userId}`)
  if (saved) {
    try {
      messages.value = JSON.parse(saved)
    } catch (e) {
      console.error('加载对话历史失败:', e)
      messages.value = []
    }
  }
}

// 保存对话历史到 localStorage
const saveMessages = () => {
  const userId = userStore.userInfo?.id || 'default'
  try {
    localStorage.setItem(`ai-chat-history-${userId}`, JSON.stringify(messages.value))
  } catch (e) {
    console.error('保存对话历史失败:', e)
  }
}

// 清空当前用户的对话历史
const clearMessages = () => {
  const userId = userStore.userInfo?.id || 'default'
  localStorage.removeItem(`ai-chat-history-${userId}`)
  messages.value = []
}

// 处理清空对话（带确认）
const handleClearHistory = async () => {
  try {
    await ElMessageBox.confirm(
      '确定要清空所有对话记录吗？此操作不可恢复。',
      '清空对话',
      {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      }
    )
    clearMessages()
    ElMessage.success('对话记录已清空')
  } catch {
    // 用户取消
  }
}

// 组件挂载时加载历史
onMounted(() => {
  loadMessages()
})

// 监听用户切换，重新加载对应对话历史
watch(() => userStore.userInfo?.id, () => {
  loadMessages()
}, { immediate: false })

const quickQuestions = [
  '每天应该喝多少水？',
  '如何改善睡眠质量？',
  '久坐办公怎么缓解腰痛？',
  '什么样的运动适合减脂？'
]

const formatTime = () => {
  const now = new Date()
  return now.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
}

const scrollToBottom = async () => {
  await nextTick()
  if (msgContainer.value) {
    msgContainer.value.scrollTop = msgContainer.value.scrollHeight
  }
}

const handleSend = async () => {
  const text = inputText.value.trim()
  if (!text || loading.value) return

  messages.value.push({
    role: 'user',
    content: text,
    time: formatTime()
  })
  inputText.value = ''
  await scrollToBottom()

  loading.value = true
  try {
    const payload = {
      message: text,
      userId: userStore.userInfo?.id || null
    }
    console.log('发送 AI 请求:', payload)
    console.log('当前用户信息:', userStore.userInfo)
    
    const res = await request.post('/chat/send', payload)
    messages.value.push({
      role: 'assistant',
      content: res?.data?.reply || '抱歉，未能获取回复。',
      time: formatTime()
    })
    // 保存对话历史
    saveMessages()
  } catch {
    messages.value.push({
      role: 'assistant',
      content: '抱歉，AI 服务暂时不可用，请稍后重试。',
      time: formatTime()
    })
  } finally {
    loading.value = false
    await scrollToBottom()
  }
}

const sendMessage = (text) => {
  inputText.value = text
  handleSend()
}

const renderMarkdown = (content) => {
  if (!content) return ''
  // 配置 marked 选项
  marked.setOptions({
    breaks: true,      // 支持换行
    gfm: true,         // GitHub Flavored Markdown
    sanitize: false    // 允许 HTML（因为内容是 AI 生成的，可信）
  })
  return marked(content)
}
</script>

<style scoped>
.ai-chat-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
  height: calc(100vh - 132px);
}

.page-hero {
  border: none;
  background: linear-gradient(135deg, rgba(37, 99, 235, 0.88), rgba(99, 102, 241, 0.9));
  color: #fff;
  flex-shrink: 0;
}

.page-hero-content {
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-wrap: wrap;
  gap: 16px;
}

.hero-kicker {
  margin: 0 0 6px;
  font-size: 12px;
  letter-spacing: 0.14em;
  text-transform: uppercase;
  opacity: 0.78;
}

.page-hero h2 {
  margin: 0;
}

.page-hero p {
  margin: 6px 0 0;
  opacity: 0.9;
  font-size: 14px;
}

.chat-container {
  flex: 1;
  display: flex;
  flex-direction: column;
  background: #fff;
  border-radius: 16px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.06);
  overflow: hidden;
}

.chat-messages {
  flex: 1;
  overflow-y: auto;
  padding: 20px 24px;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.welcome-hint {
  text-align: center;
  padding: 60px 20px 30px;
  color: #64748b;
}

.welcome-hint p {
  margin: 16px 0 20px;
  font-size: 16px;
}

.quick-questions {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 10px;
}

.quick-tag {
  cursor: pointer;
  transition: all 0.2s;
}

.quick-tag:hover {
  background: #2563eb;
  color: #fff;
  border-color: #2563eb;
}

.message-row {
  display: flex;
}

.message-row.user {
  justify-content: flex-end;
}

.message-row.assistant {
  justify-content: flex-start;
}

.message-bubble {
  max-width: 72%;
  padding: 12px 16px;
  border-radius: 16px;
  line-height: 1.65;
  font-size: 14px;
}

.message-row.user .message-bubble {
  background: linear-gradient(135deg, #2563eb, #4f46e5);
  color: #fff;
  border-bottom-right-radius: 4px;
}

.message-row.assistant .message-bubble {
  background: #f1f5f9;
  color: #1e293b;
  border-bottom-left-radius: 4px;
}

.bubble-text {
  word-break: break-word;
}

.bubble-text :deep(p) {
  margin: 0.5em 0;
}

.bubble-text :deep(p:first-child) {
  margin-top: 0;
}

.bubble-text :deep(p:last-child) {
  margin-bottom: 0;
}

.bubble-text :deep(strong) {
  font-weight: 600;
  color: inherit;
}

.bubble-text :deep(ul),
.bubble-text :deep(ol) {
  margin: 0.5em 0;
  padding-left: 1.5em;
}

.bubble-text :deep(li) {
  margin: 0.25em 0;
}

.bubble-text :deep(code) {
  background: rgba(0, 0, 0, 0.08);
  padding: 2px 6px;
  border-radius: 4px;
  font-family: 'Consolas', 'Monaco', monospace;
  font-size: 0.9em;
}

.message-row.user .bubble-text :deep(code) {
  background: rgba(255, 255, 255, 0.2);
}

.bubble-time {
  font-size: 11px;
  margin-top: 6px;
  opacity: 0.6;
}

.message-bubble.typing {
  display: flex;
  align-items: center;
  gap: 5px;
  padding: 14px 20px;
}

.dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #94a3b8;
  animation: bounce 1.4s infinite ease-in-out both;
}

.dot:nth-child(1) { animation-delay: -0.32s; }
.dot:nth-child(2) { animation-delay: -0.16s; }
.dot:nth-child(3) { animation-delay: 0s; }

@keyframes bounce {
  0%, 80%, 100% { transform: scale(0); }
  40% { transform: scale(1); }
}

.chat-input-area {
  display: flex;
  align-items: flex-end;
  gap: 12px;
  padding: 16px 24px;
  border-top: 1px solid #e2e8f0;
  background: #f8fafc;
}

.chat-input-area .el-button {
  height: 40px;
  flex-shrink: 0;
}
</style>
