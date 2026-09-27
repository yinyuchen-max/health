<template>
  <div class="conversations-page">
    <div class="chat-shell">
      <!-- 左侧会话列表 -->
      <div class="list-panel">
        <div class="list-header">
          <span class="list-title">患者会话</span>
          <el-badge v-if="unreadTotal > 0" :value="unreadTotal" :max="99" />
        </div>
        <div class="list-body">
          <div v-if="conversations.length === 0" class="empty-state">
            <el-icon :size="44" color="#cbd5e1"><ChatLineRound /></el-icon>
            <p>暂无患者咨询</p>
          </div>
          <div
            v-for="conv in conversations"
            :key="conv.userId"
            :class="['conv-item', { active: selectedUserId === conv.userId }]"
            @click="selectConversation(conv)"
          >
            <el-avatar :size="44" class="conv-avatar">{{ (conv.userName || '患').slice(0, 1) }}</el-avatar>
            <div class="conv-info">
              <div class="conv-top">
                <span class="conv-name">{{ conv.userName }}</span>
                <span class="conv-time">{{ formatShortTime(conv.lastMessageTime) }}</span>
              </div>
              <div class="conv-bottom">
                <p class="conv-last">{{ conv.lastMessage || '暂无消息' }}</p>
                <el-badge v-if="conv.unreadCount > 0" :value="conv.unreadCount" :max="99" />
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- 右侧聊天区 -->
      <div class="chat-panel">
        <div v-if="!selectedUserId" class="empty-chat">
          <el-icon :size="56" color="#cbd5e1"><ChatDotRound /></el-icon>
          <h3>选择一位患者开始对话</h3>
          <p>及时回复患者咨询，提供专业的健康建议</p>
        </div>
        <template v-else>
          <div class="chat-header">
            <el-avatar :size="36" class="chat-avatar">{{ (selectedUserName || '患').slice(0, 1) }}</el-avatar>
            <div class="chat-title-box">
              <span class="chat-with">{{ selectedUserName }}</span>
              <span class="chat-sub">患者咨询</span>
            </div>
          </div>

          <div class="chat-messages" ref="chatBoxRef">
            <div
              v-for="msg in messages"
              :key="msg.id"
              :class="['msg', msg.senderType === 'doctor' ? 'msg-right' : 'msg-left']"
            >
              <div class="msg-bubble">
                <p>{{ msg.content }}</p>
                <span class="msg-time">{{ formatShortTime(msg.createdAt) }}</span>
              </div>
            </div>
            <div v-if="messages.length === 0" class="msg-empty">
              <p>还没有消息，主动问候一下患者吧</p>
            </div>
          </div>

          <div class="chat-input-area">
            <el-input
              v-model="chatInput"
              type="textarea"
              :rows="2"
              resize="none"
              placeholder="输入回复内容，Enter 发送"
              @keydown.enter.exact.prevent="sendMessage"
              :disabled="sending"
            />
            <el-button type="primary" size="large" class="send-btn" @click="sendMessage" :loading="sending">
              发送
            </el-button>
          </div>
        </template>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, nextTick } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ChatLineRound, ChatDotRound } from '@element-plus/icons-vue'
import request from '../utils/request'

const route = useRoute()

const conversations = ref([])
const selectedUserId = ref(null)
const selectedUserName = ref('')
const messages = ref([])
const chatInput = ref('')
const sending = ref(false)
const chatBoxRef = ref(null)

const unreadTotal = computed(() => conversations.value.reduce((sum, c) => sum + (c.unreadCount || 0), 0))

const fetchConversations = async () => {
  try {
    const res = await request.get('/doctor-chat/doctor/conversations')
    conversations.value = res?.data || res || []
  } catch {
    conversations.value = []
  }
}

const selectConversation = async (conv) => {
  selectedUserId.value = conv.userId
  selectedUserName.value = conv.userName
  await loadMessages(conv.userId)
}

const loadMessages = async (userId) => {
  try {
    const res = await request.get(`/doctor-chat/doctor/conversation/${userId}`)
    messages.value = res?.data || res || []
    await nextTick()
    scrollToBottom()
  } catch {
    messages.value = []
  }
}

const sendMessage = async () => {
  if (!chatInput.value.trim() || !selectedUserId.value) return
  sending.value = true
  try {
    await request.post(`/doctor-chat/doctor/send-to-user/${selectedUserId.value}`, {
      content: chatInput.value.trim()
    })
    chatInput.value = ''
    await loadMessages(selectedUserId.value)
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

onMounted(async () => {
  await fetchConversations()
  // 支持从其他页面带参数跳转，直接打开指定患者的会话
  const targetUserId = route.query.userId
  if (targetUserId) {
    const conv = conversations.value.find(c => String(c.userId) === String(targetUserId))
    if (conv) {
      selectConversation(conv)
    } else {
      // 会话不存在（比如患者还没发过消息），直接打开空会话
      selectedUserId.value = Number(targetUserId)
      selectedUserName.value = '患者'
      messages.value = []
    }
  }
})
</script>

<style scoped>
.conversations-page {
  max-width: 1100px;
  margin: 0 auto;
}

.chat-shell {
  display: grid;
  grid-template-columns: 300px 1fr;
  height: calc(100vh - 180px);
  min-height: 480px;
  background: #fff;
  border-radius: 24px;
  overflow: hidden;
  box-shadow: 0 14px 40px rgba(15, 23, 42, 0.08);
}

/* 左侧列表 */
.list-panel {
  display: flex;
  flex-direction: column;
  background: #f8fafc;
  border-right: 1px solid #e2e8f0;
}

.list-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 18px 20px;
  border-bottom: 1px solid #e2e8f0;
}

.list-title {
  font-size: 15px;
  font-weight: 700;
  color: #0f172a;
}

.list-body {
  flex: 1;
  overflow-y: auto;
  padding: 8px;
}

.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
  padding: 50px 20px;
  color: #94a3b8;
}

.conv-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px;
  border-radius: 14px;
  cursor: pointer;
  transition: background 0.15s;
  margin-bottom: 4px;
}

.conv-item:hover { background: #EBF5FF; }
.conv-item.active { background: #EBF5FF; }

.conv-avatar {
  background: #10B981;
  color: #fff;
  font-weight: 600;
}

.conv-info {
  flex: 1;
  min-width: 0;
}

.conv-top {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.conv-name {
  font-weight: 600;
  font-size: 14px;
  color: #1e293b;
}

.conv-time {
  font-size: 11px;
  color: #94a3b8;
}

.conv-bottom {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 4px;
}

.conv-last {
  margin: 0;
  font-size: 12px;
  color: #94a3b8;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  max-width: 160px;
}

/* 右侧聊天区 */
.chat-panel {
  display: flex;
  flex-direction: column;
}

.empty-chat {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;
  gap: 8px;
  color: #94a3b8;
}

.empty-chat h3 {
  margin: 8px 0 0;
  color: #475569;
  font-size: 17px;
}

.empty-chat p {
  margin: 0;
  font-size: 13px;
}

.chat-header {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 14px 20px;
  border-bottom: 1px solid #e2e8f0;
  background: #fff;
}

.chat-avatar {
  background: #10B981;
  color: #fff;
  font-weight: 600;
}

.chat-title-box {
  display: flex;
  flex-direction: column;
}

.chat-with {
  font-weight: 700;
  font-size: 15px;
  color: #0f172a;
}

.chat-sub {
  font-size: 12px;
  color: #94a3b8;
}

.chat-messages {
  flex: 1;
  overflow-y: auto;
  padding: 20px;
  display: flex;
  flex-direction: column;
  gap: 14px;
  background: #F8FAFC;
}

.msg-empty {
  margin: auto;
  color: #94a3b8;
  font-size: 13px;
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
  box-shadow: 0 2px 8px rgba(15, 23, 42, 0.06);
}

.msg-left .msg-bubble {
  background: #fff;
  color: #1e293b;
  border-bottom-left-radius: 4px;
}

.msg-right .msg-bubble {
  background: #3B82F6;
  color: #fff;
  border-bottom-right-radius: 4px;
}

.msg-bubble p { margin: 0; word-break: break-word; }

.msg-time {
  display: block;
  margin-top: 5px;
  font-size: 11px;
  opacity: 0.6;
}

.chat-input-area {
  display: flex;
  gap: 10px;
  align-items: flex-end;
  padding: 14px 20px;
  border-top: 1px solid #e2e8f0;
  background: #fff;
}

.chat-input-area :deep(.el-textarea__inner) {
  border-radius: 14px;
  box-shadow: 0 0 0 1px #e1e6ee;
}

.chat-input-area :deep(.el-textarea__inner:focus) {
  box-shadow: 0 0 0 1px #1e40af;
}

.send-btn {
  height: 52px;
  padding: 0 26px;
  border-radius: 14px;
  font-weight: 700;
  background: #3B82F6;
  border: none;
}

.send-btn:hover {
  background: #2563eb;
}

@media (max-width: 768px) {
  .chat-shell { grid-template-columns: 1fr; height: auto; }
  .list-panel { display: none; }
}
</style>
