<template>
  <div class="doctor-chat-page">
    <div class="page-header">
      <h1>医生咨询</h1>
      <p>与医生在线沟通健康问题</p>
    </div>

    <div class="chat-layout" :class="{ 'has-selection': selectedDoctorId }">
      <!-- 左侧对话列表 -->
      <div class="sidebar-panel">
        <div class="sidebar-header">
          <span>我的咨询医生</span>
          <el-button text size="small" @click="$router.push('/app/doctors')">+ 找医生</el-button>
        </div>
        <div v-if="conversations.length === 0" class="empty-state">
          <p>还没有咨询过医生</p>
          <el-button size="small" @click="$router.push('/app/doctors')">去查看医生列表</el-button>
        </div>
        <div
          v-for="conv in conversations"
          :key="conv.doctorId"
          :class="['conv-item', { active: selectedDoctorId === conv.doctorId }]"
          @click="selectConversation(conv)"
        >
          <div class="conv-avatar">
            <el-avatar :size="40" style="background: #1e40af; font-size: 16px">
              {{ conv.doctorName?.charAt(0) || '医' }}
            </el-avatar>
          </div>
          <div class="conv-info">
            <div class="conv-top">
              <span class="conv-name">{{ conv.doctorName }}</span>
              <el-badge v-if="conv.unreadCount > 0" :value="conv.unreadCount" :max="99" />
            </div>
            <p class="conv-last">{{ conv.lastMessage || '暂无消息' }}</p>
          </div>
          <span class="conv-time">{{ formatShortTime(conv.lastMessageTime) }}</span>
        </div>
      </div>

      <!-- 右侧聊天区 -->
      <div class="chat-panel">
        <div v-if="!selectedDoctorId" class="empty-chat">
          <el-icon :size="48" color="#cbd5e1"><ChatDotRound /></el-icon>
          <p>选择一位医生开始咨询</p>
        </div>
        <template v-else>
          <div class="chat-header">
            <el-button v-if="isMobileView" text size="small" @click="selectedDoctorId = null" style="margin-right: 8px;">
              ← 返回
            </el-button>
            <span class="chat-with">与 {{ selectedDoctorName }} 的对话</span>
          </div>
          <div class="chat-messages" ref="chatBoxRef">
            <div
              v-for="msg in messages"
              :key="msg.id"
              :class="['msg', msg.senderType === 'user' ? 'msg-right' : 'msg-left']"
            >
              <div class="msg-bubble">
                <p>{{ msg.content }}</p>
                <span class="msg-time">{{ formatShortTime(msg.createdAt) }}</span>
              </div>
            </div>
          </div>
          <div class="chat-input-area">
            <el-input
              v-model="chatInput"
              placeholder="输入您的健康问题..."
              @keydown.enter.prevent="sendMessage"
              :disabled="sending"
              size="large"
            />
            <el-button type="primary" size="large" @click="sendMessage" :loading="sending">发送</el-button>
          </div>
        </template>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, onBeforeUnmount, nextTick } from 'vue'
import { ElMessage } from 'element-plus'
import { ChatDotRound } from '@element-plus/icons-vue'
import request from '../utils/request'

const conversations = ref([])
const selectedDoctorId = ref(null)
const selectedDoctorName = ref('')
const messages = ref([])
const chatInput = ref('')
const sending = ref(false)
const chatBoxRef = ref(null)

const fetchConversations = async () => {
  try {
    const res = await request.get('/doctor-chat/my-conversations')
    conversations.value = res?.data || res || []
  } catch {
    conversations.value = []
  }
}

const selectConversation = async (conv) => {
  selectedDoctorId.value = conv.doctorId
  selectedDoctorName.value = conv.doctorName
  await loadMessages(conv.doctorId)
}

const loadMessages = async (doctorId) => {
  try {
    const res = await request.get(`/doctor-chat/conversation/${doctorId}`)
    messages.value = res?.data || res || []
    await nextTick()
    scrollToBottom()
  } catch {
    messages.value = []
  }
}

const sendMessage = async () => {
  if (!chatInput.value.trim() || !selectedDoctorId.value) return
  sending.value = true
  try {
    await request.post('/doctor-chat/send-to-doctor', {
      doctorId: selectedDoctorId.value,
      content: chatInput.value.trim()
    })
    chatInput.value = ''
    await loadMessages(selectedDoctorId.value)
    await fetchConversations()
  } catch (error) {
    ElMessage.error(error?.message || '发送失败')
  } finally {
    sending.value = false
  }
}

const scrollToBottom = () => {
  if (chatBoxRef.value) {
    chatBoxRef.value.scrollTop = chatBoxRef.value.scrollHeight
  }
}

const formatShortTime = (t) => {
  if (!t) return ''
  const d = new Date(t)
  const today = new Date()
  if (d.toDateString() === today.toDateString()) {
    return d.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
  }
  return d.toLocaleDateString('zh-CN', { month: 'short', day: 'numeric' })
}

const isMobileView = ref(false)
const checkMobileView = () => {
  isMobileView.value = window.innerWidth < 768
}

onMounted(() => {
  fetchConversations()
  checkMobileView()
  window.addEventListener('resize', checkMobileView)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', checkMobileView)
})
</script>

<style scoped>
.doctor-chat-page {
  max-width: 1000px;
  margin: 0 auto;
}

.page-header h1 { margin: 0 0 4px; font-size: 24px; color: #1E293B; }
.page-header p { margin: 0 0 20px; color: #64748B; }

.chat-layout {
  display: grid;
  grid-template-columns: 300px 1fr;
  height: 560px;
  border: 1px solid #F0F4F8;
  border-radius: 16px;
  overflow: hidden;
  background: #fff;
  box-shadow: 0 8px 24px rgba(15, 23, 42, 0.06);
}

.sidebar-panel {
  border-right: 1px solid #F0F4F8;
  background: #F8FAFC;
  display: flex;
  flex-direction: column;
}

.sidebar-header {
  padding: 16px;
  font-weight: 600;
  font-size: 14px;
  color: #1E293B;
  border-bottom: 1px solid #F0F4F8;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.conversation-list { flex: 1; overflow-y: auto; }

.empty-state {
  padding: 40px 20px;
  text-align: center;
  color: #94a3b8;
}

.conv-item {
  display: flex;
  gap: 12px;
  padding: 14px 16px;
  cursor: pointer;
  border-bottom: 1px solid #f1f5f9;
  align-items: center;
  transition: background 0.15s;
}

.conv-item:hover { background: #EBF5FF; }
.conv-item.active { background: #EBF5FF; }

.conv-info { flex: 1; min-width: 0; }

.conv-top {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.conv-name { font-weight: 600; font-size: 14px; color: #1e293b; }

.conv-last {
  margin: 4px 0 0;
  font-size: 12px;
  color: #94a3b8;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.conv-time { font-size: 11px; color: #94a3b8; white-space: nowrap; }

.chat-panel {
  display: flex;
  flex-direction: column;
}

.chat-header {
  padding: 14px 20px;
  border-bottom: 1px solid #F0F4F8;
  font-weight: 600;
  color: #1E293B;
  background: #fff;
}

.empty-chat {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;
  color: #94a3b8;
  gap: 12px;
}

.chat-messages {
  flex: 1;
  overflow-y: auto;
  padding: 20px;
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.msg { display: flex; }
.msg-left { justify-content: flex-start; }
.msg-right { justify-content: flex-end; }

.msg-bubble {
  max-width: 65%;
  padding: 12px 16px;
  border-radius: 16px;
  font-size: 14px;
  line-height: 1.6;
}

.msg-left .msg-bubble {
  background: #f1f5f9;
  color: #1e293b;
  border-bottom-left-radius: 4px;
}

.msg-right .msg-bubble {
  background: #3B82F6;
  color: #fff;
  border-bottom-right-radius: 4px;
}

.msg-bubble p { margin: 0; }

.msg-time {
  display: block;
  margin-top: 4px;
  font-size: 11px;
  opacity: 0.6;
}

.chat-input-area {
  display: flex;
  gap: 10px;
  padding: 14px 20px;
  border-top: 1px solid #F0F4F8;
  background: #fff;
}

.chat-input-area .el-input { flex: 1; }

/* 移动端适配 */
@media (max-width: 768px) {
  .page-header h1 { font-size: 20px; }
  .page-header p { font-size: 13px; margin-bottom: 14px; }

  .chat-layout {
    grid-template-columns: 1fr;
    height: calc(100vh - 220px);
    min-height: 400px;
  }

  /* 移动端：未选择医生时显示列表，选择后显示聊天 */
  .chat-layout.has-selection .sidebar-panel {
    display: none;
  }

  .chat-layout:not(.has-selection) .chat-panel {
    display: none;
  }

  .sidebar-panel {
    border-right: none;
  }

  .chat-messages {
    padding: 14px;
  }

  .msg-bubble {
    max-width: 80%;
  }

  .chat-input-area {
    padding: 12px;
  }
}
</style>
