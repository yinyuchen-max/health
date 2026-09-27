<template>
  <div class="doctor-dashboard">
    <!-- 欢迎横幅 -->
    <div class="welcome-banner">
      <div class="banner-left">
        <div class="banner-icon-box">
          <el-icon :size="28" color="#3B82F6"><Calendar /></el-icon>
        </div>
        <div class="banner-text">
          <p class="banner-date">{{ todayText }}</p>
          <h1>欢迎回来，{{ doctorName }}医生</h1>
          <p class="banner-desc">今天也要用心守护每一位患者的健康 ✨</p>
        </div>
      </div>
    </div>

    <!-- 数据统计 -->
    <div class="stats-grid">
      <div class="stat-card" @click="router.push('/doctor/appointments')">
        <div class="stat-icon icon-blue"><el-icon :size="24"><Calendar /></el-icon></div>
        <div class="stat-body">
          <span class="stat-number">{{ appointments.length }}</span>
          <span class="stat-label">预约总数</span>
        </div>
        <el-icon class="stat-arrow"><ArrowRight /></el-icon>
      </div>
      <div class="stat-card" @click="router.push('/doctor/appointments')">
        <div class="stat-icon icon-amber"><el-icon :size="24"><Clock /></el-icon></div>
        <div class="stat-body">
          <span class="stat-number">{{ pendingCount }}</span>
          <span class="stat-label">待确认预约</span>
        </div>
        <el-icon class="stat-arrow"><ArrowRight /></el-icon>
      </div>
      <div class="stat-card" @click="router.push('/doctor/conversations')">
        <div class="stat-icon icon-green"><el-icon :size="24"><ChatLineRound /></el-icon></div>
        <div class="stat-body">
          <span class="stat-number">{{ conversations.length }}</span>
          <span class="stat-label">咨询患者</span>
        </div>
        <el-icon class="stat-arrow"><ArrowRight /></el-icon>
      </div>
      <div class="stat-card" @click="router.push('/doctor/conversations')">
        <div class="stat-icon icon-red"><el-icon :size="24"><Bell /></el-icon></div>
        <div class="stat-body">
          <span class="stat-number">{{ unreadTotal }}</span>
          <span class="stat-label">未读消息</span>
        </div>
        <el-icon class="stat-arrow"><ArrowRight /></el-icon>
      </div>
    </div>

    <div class="content-grid">
      <!-- 最近预约 -->
      <div class="panel">
        <div class="panel-header">
          <h3>最近预约</h3>
          <el-button text type="primary" @click="router.push('/doctor/appointments')">查看全部</el-button>
        </div>
        <div v-if="appointments.length === 0" class="panel-empty">
          <el-icon :size="40" color="#cbd5e1"><Calendar /></el-icon>
          <p>暂无预约患者</p>
        </div>
        <div v-else class="appointment-list">
          <div v-for="item in appointments.slice(0, 5)" :key="item.id" class="appt-item">
            <el-avatar :size="40" class="appt-avatar">{{ (item.patientName || '患').slice(0, 1) }}</el-avatar>
            <div class="appt-info">
              <div class="appt-name">
                {{ item.patientName }}
                <span class="appt-age">{{ item.age }}岁</span>
              </div>
              <p class="appt-time">{{ formatTime(item.appointmentTime) }}</p>
            </div>
            <el-tag :type="statusType(item.status)" size="small" round>{{ statusText(item.status) }}</el-tag>
          </div>
        </div>
      </div>

      <!-- 最近对话 -->
      <div class="panel">
        <div class="panel-header">
          <h3>患者消息</h3>
          <el-button text type="primary" @click="router.push('/doctor/conversations')">去回复</el-button>
        </div>
        <div v-if="conversations.length === 0" class="panel-empty">
          <el-icon :size="40" color="#cbd5e1"><ChatLineRound /></el-icon>
          <p>暂无患者咨询</p>
        </div>
        <div v-else class="conversation-list">
          <div
            v-for="conv in conversations.slice(0, 5)"
            :key="conv.userId"
            class="conv-item"
            @click="goChat(conv.userId)"
          >
            <el-avatar :size="40" class="conv-avatar">{{ (conv.userName || '患').slice(0, 1) }}</el-avatar>
            <div class="conv-info">
              <div class="conv-top">
                <span class="conv-name">{{ conv.userName }}</span>
                <el-badge v-if="conv.unreadCount > 0" :value="conv.unreadCount" :max="99" />
              </div>
              <p class="conv-last">{{ conv.lastMessage || '暂无消息' }}</p>
            </div>
            <span class="conv-time">{{ formatShortTime(conv.lastMessageTime) }}</span>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { Calendar, Clock, ChatLineRound, Bell, ArrowRight } from '@element-plus/icons-vue'
import request from '../utils/request'

const router = useRouter()

const doctorInfo = ref(null)
const appointments = ref([])
const conversations = ref([])

const doctorName = computed(() => doctorInfo.value?.realName || '')
const pendingCount = computed(() => appointments.value.filter(a => a.status === 'pending' || !a.status).length)
const unreadTotal = computed(() => conversations.value.reduce((sum, c) => sum + (c.unreadCount || 0), 0))

const todayText = computed(() => {
  const d = new Date()
  const week = ['日', '一', '二', '三', '四', '五', '六'][d.getDay()]
  return `${d.getFullYear()}年${d.getMonth() + 1}月${d.getDate()}日 · 星期${week}`
})

const fetchData = async () => {
  try {
    const [infoRes, apptRes, convRes] = await Promise.allSettled([
      request.get('/doctor/my-info'),
      request.get('/doctor/my-appointments'),
      request.get('/doctor-chat/doctor/conversations')
    ])
    if (infoRes.status === 'fulfilled') doctorInfo.value = infoRes.value?.data || infoRes.value
    if (apptRes.status === 'fulfilled') appointments.value = apptRes.value?.data || apptRes.value || []
    if (convRes.status === 'fulfilled') conversations.value = convRes.value?.data || convRes.value || []
  } catch {
    // ignore
  }
}

const goChat = (userId) => {
  router.push(`/doctor/conversations?userId=${userId}`)
}

const formatTime = (t) => t ? new Date(t).toLocaleString('zh-CN', { month: 'numeric', day: 'numeric', hour: '2-digit', minute: '2-digit' }) : '-'
const formatShortTime = (t) => {
  if (!t) return ''
  const d = new Date(t)
  const today = new Date()
  if (d.toDateString() === today.toDateString()) {
    return d.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
  }
  return d.toLocaleDateString('zh-CN', { month: 'short', day: 'numeric' })
}

const statusText = (s) => ({ pending: '待确认', confirmed: '已确认', completed: '已完成', cancelled: '已取消' }[s] || '待确认')
const statusType = (s) => ({ pending: 'warning', confirmed: 'primary', completed: 'success', cancelled: 'info' }[s] || 'warning')

onMounted(fetchData)
</script>

<style scoped>
.doctor-dashboard {
  max-width: 1200px;
  margin: 0 auto;
}

/* 欢迎横幅 */
.welcome-banner {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 28px 32px;
  margin-bottom: 20px;
  border-radius: 20px;
  background: #fff;
  border: 1px solid #F0F4F8;
  box-shadow: 0 8px 24px rgba(15, 23, 42, 0.06);
  overflow: hidden;
}

.banner-left {
  display: flex;
  align-items: center;
  gap: 16px;
}

.banner-icon-box {
  width: 52px;
  height: 52px;
  border-radius: 16px;
  background: rgba(59, 130, 246, 0.1);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.banner-date {
  margin: 0 0 6px;
  font-size: 13px;
  color: #64748B;
  letter-spacing: 0.08em;
}

.banner-text h1 {
  margin: 0;
  font-size: 24px;
  color: #1E293B;
  letter-spacing: -0.02em;
}

.banner-desc {
  margin: 6px 0 0;
  font-size: 14px;
  color: #64748B;
}

/* 统计卡片 */
.stats-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
  margin-bottom: 20px;
}

.stat-card {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 20px;
  background: #fff;
  border-radius: 18px;
  cursor: pointer;
  box-shadow: 0 8px 24px rgba(15, 23, 42, 0.06);
  transition: transform 0.2s, box-shadow 0.2s;
}

.stat-card:hover {
  transform: translateY(-3px);
  box-shadow: 0 14px 30px rgba(15, 23, 42, 0.1);
}

.stat-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 50px;
  height: 50px;
  border-radius: 14px;
}

.icon-blue { background: rgba(59, 130, 246, 0.1); color: #3B82F6; }
.icon-amber { background: rgba(245, 158, 11, 0.1); color: #F59E0B; }
.icon-green { background: rgba(16, 185, 129, 0.1); color: #10B981; }
.icon-red { background: rgba(244, 63, 94, 0.1); color: #F43F5E; }

.stat-body {
  display: flex;
  flex: 1;
  flex-direction: column;
}

.stat-number {
  font-size: 24px;
  font-weight: 800;
  color: #0f172a;
  letter-spacing: -0.02em;
}

.stat-label {
  font-size: 12px;
  color: #64748b;
}

.stat-arrow {
  color: #cbd5e1;
}

/* 内容区 */
.content-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
}

.panel {
  background: #fff;
  border-radius: 18px;
  padding: 20px;
  border: 1px solid #F0F4F8;
  box-shadow: 0 8px 24px rgba(15, 23, 42, 0.06);
}

.panel-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}

.panel-header h3 {
  margin: 0;
  font-size: 16px;
  color: #0f172a;
}

.panel-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  padding: 40px 0;
  color: #94a3b8;
}

/* 预约列表 */
.appt-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 8px;
  border-radius: 12px;
  transition: background 0.15s;
}

.appt-item:hover { background: #f8fafc; }

.appt-avatar {
  background: linear-gradient(135deg, #60a5fa, #3b82f6);
  color: #fff;
  font-weight: 600;
}

.appt-info { flex: 1; }

.appt-name {
  font-weight: 600;
  font-size: 14px;
  color: #1e293b;
}

.appt-age {
  margin-left: 6px;
  font-size: 12px;
  color: #94a3b8;
  font-weight: 400;
}

.appt-time {
  margin: 3px 0 0;
  font-size: 12px;
  color: #94a3b8;
}

/* 会话列表 */
.conv-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 8px;
  border-radius: 12px;
  cursor: pointer;
  transition: background 0.15s;
}

.conv-item:hover { background: #f8fafc; }

.conv-avatar {
  background: linear-gradient(135deg, #34d399, #059669);
  color: #fff;
  font-weight: 600;
}

.conv-info { flex: 1; min-width: 0; }

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

.conv-last {
  margin: 3px 0 0;
  font-size: 12px;
  color: #94a3b8;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.conv-time {
  font-size: 11px;
  color: #94a3b8;
}

@media (max-width: 1000px) {
  .stats-grid { grid-template-columns: repeat(2, 1fr); }
  .content-grid { grid-template-columns: 1fr; }
}
</style>
