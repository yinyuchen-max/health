<template>
  <div class="layout-container">
    <!-- 侧边栏 -->
    <el-aside v-if="!isMobile" width="220px" class="sidebar">
      <div class="logo">
        <div class="logo-icon">
          <svg viewBox="0 0 24 24" fill="none" width="28" height="28">
            <path d="M12 21.35l-1.45-1.32C5.4 15.36 2 12.28 2 8.5 2 5.42 4.42 3 7.5 3c1.74 0 3.41.81 4.5 2.09C13.09 3.81 14.76 3 16.5 3 19.58 3 22 5.42 22 8.5c0 3.78-3.4 6.86-8.55 11.54L12 21.35z" fill="#3B82F6"/>
          </svg>
        </div>
        <span class="logo-text">健康管理系统</span>
      </div>

      <el-menu
        :default-active="activeMenu"
        router
        class="sidebar-menu"
      >
        <template v-for="item in menuItems" :key="item.index">
          <el-menu-item v-if="!item.hidden" :index="item.index">
            <div class="menu-icon-wrapper" :style="{ background: item.color + '18' }">
              <el-icon :style="{ color: item.color }"><component :is="item.icon" /></el-icon>
            </div>
            <span>{{ item.label }}</span>
          </el-menu-item>
        </template>
      </el-menu>

      <div class="sidebar-footer">
        <p class="footer-slogan">关注健康</p>
        <p class="footer-slogan-sub">从现在开始</p>
      </div>
    </el-aside>

    <!-- 移动端抽屉菜单 -->
    <el-drawer
      v-model="drawerVisible"
      direction="ltr"
      :size="260"
      :show-close="false"
      :with-header="false"
      class="mobile-drawer"
    >
      <div class="drawer-content">
        <div class="logo">
          <div class="logo-icon">
            <svg viewBox="0 0 24 24" fill="none" width="28" height="28">
              <path d="M12 21.35l-1.45-1.32C5.4 15.36 2 12.28 2 8.5 2 5.42 4.42 3 7.5 3c1.74 0 3.41.81 4.5 2.09C13.09 3.81 14.76 3 16.5 3 19.58 3 22 5.42 22 8.5c0 3.78-3.4 6.86-8.55 11.54L12 21.35z" fill="#3B82F6"/>
            </svg>
          </div>
          <span class="logo-text">健康管理系统</span>
        </div>

        <el-menu
          :default-active="activeMenu"
          router
          class="sidebar-menu"
          @select="drawerVisible = false"
        >
          <template v-for="item in menuItems" :key="item.index">
            <el-menu-item v-if="!item.hidden" :index="item.index">
              <div class="menu-icon-wrapper" :style="{ background: item.color + '18' }">
                <el-icon :style="{ color: item.color }"><component :is="item.icon" /></el-icon>
              </div>
              <span>{{ item.label }}</span>
            </el-menu-item>
          </template>
        </el-menu>
      </div>
    </el-drawer>

    <el-container class="main-container">
      <el-header class="header">
        <div class="header-content">
          <div class="header-left">
            <el-button v-if="isMobile" class="menu-toggle" text @click="drawerVisible = true">
              <el-icon :size="22"><Operation /></el-icon>
            </el-button>
            <div class="greeting-area">
              <h2 class="greeting-title">{{ greeting }}，{{ userStore.userInfo.username || '用户' }} <span class="wave">👋</span></h2>
              <p class="greeting-sub">关爱自己，从每一次记录开始</p>
            </div>
          </div>
          <div class="header-right">
            <div class="weather-info">
              <span class="weather-icon">☀️</span>
              <span class="weather-date">{{ currentDate }}</span>
              <span class="weather-location">济南 18°C 晴</span>
            </div>
            <el-dropdown trigger="click" @command="handleCommand">
              <div class="user-dropdown">
                <el-avatar :size="36" class="user-avatar">{{ (userStore.userInfo.username || 'U').slice(0, 1).toUpperCase() }}</el-avatar>
                <span class="username">{{ userStore.userInfo.username || '用户' }}</span>
                <el-icon class="dropdown-arrow"><ArrowDown /></el-icon>
              </div>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item command="profile">个人信息</el-dropdown-item>
                  <el-dropdown-item command="password">修改密码</el-dropdown-item>
                  <el-dropdown-item command="logout" divided>退出登录</el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </div>
        </div>
      </el-header>

      <el-main class="main-content">
        <router-view />
      </el-main>
    </el-container>

    <!-- 桌宠 AI 助手 -->
    <DoctorPet />
  </div>
</template>

<script setup>
import { computed, ref, onMounted, onBeforeUnmount } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '../store/user'
import request from '../utils/request'
import DoctorPet from './DoctorPet.vue'
import {
  ArrowDown,
  Bell,
  Clock,
  DataAnalysis,
  Document,
  FirstAidKit,
  HomeFilled,
  Operation,
  Opportunity,
  Setting,
  Timer,
  User,
  UserFilled,
  ChatLineRound
} from '@element-plus/icons-vue'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const activeMenu = computed(() => route.path)
const isAdmin = computed(() => userStore.userInfo?.username === 'admin')

const isMobile = ref(false)
const drawerVisible = ref(false)

const checkMobile = () => {
  isMobile.value = window.innerWidth < 768
}

const greeting = computed(() => {
  const hour = new Date().getHours()
  if (hour < 12) return '早上好'
  if (hour < 18) return '下午好'
  return '晚上好'
})

const currentDate = computed(() => {
  const now = new Date()
  const days = ['星期日', '星期一', '星期二', '星期三', '星期四', '星期五', '星期六']
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(now.getDate()).padStart(2, '0')}  ${days[now.getDay()]}`
})

const menuItems = computed(() => [
  { index: '/app/dashboard', icon: HomeFilled, label: '首页', color: '#3B82F6', hidden: false },
  { index: '/app/health', icon: Document, label: '健康记录', color: '#3B82F6', hidden: false },
  { index: '/app/sport', icon: Timer, label: '运动记录', color: '#10B981', hidden: false },
  { index: '/app/reminder', icon: Bell, label: '提醒设置', color: '#F59E0B', hidden: false },
  { index: '/app/history', icon: Clock, label: '历史记录', color: '#8B5CF6', hidden: false },
  { index: '/app/analytics', icon: DataAnalysis, label: '数据分析', color: '#06B6D4', hidden: false },
  { index: '/app/smart-health', icon: Opportunity, label: '智能健康', color: '#EC4899', hidden: false },
  { index: '/app/doctors', icon: FirstAidKit, label: '医生列表', color: '#14B8A6', hidden: false },
  { index: '/app/doctor-chat', icon: ChatLineRound, label: '医生咨询', color: '#F97316', hidden: false },
  { index: '/doctor/dashboard', icon: UserFilled, label: '医生工作台', color: '#3B82F6', hidden: !isDoctor.value },
  { index: '/app/profile', icon: User, label: '个人信息', color: '#64748B', hidden: false },
  { index: '/app/user-management', icon: Setting, label: '用户管理', color: '#64748B', hidden: !isAdmin.value },
  { index: '/app/doctor-management', icon: Setting, label: '医生审核', color: '#64748B', hidden: !isAdmin.value }
])

const isDoctor = ref(false)
const checkDoctorStatus = async () => {
  try {
    const res = await request.get('/doctor/check')
    isDoctor.value = res?.data === true || res === true
  } catch {
    isDoctor.value = false
  }
}

const handleCommand = (command) => {
  if (command === 'logout') {
    userStore.logout()
    router.push('/')
  } else if (command === 'profile') {
    router.push('/app/profile')
  } else if (command === 'password') {
    router.push('/app/change-password')
  }
}

onMounted(() => {
  checkDoctorStatus()
  checkMobile()
  window.addEventListener('resize', checkMobile)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', checkMobile)
})
</script>

<style scoped>
.layout-container {
  display: flex;
  min-height: 100vh;
  background: #EBF5FF;
}

.sidebar {
  background: #fff;
  border-right: 1px solid #E8F0FE;
  display: flex;
  flex-direction: column;
}

.logo {
  display: flex;
  align-items: center;
  gap: 10px;
  height: 64px;
  padding: 0 20px;
  border-bottom: 1px solid #F0F4F8;
}

.logo-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  background: #EBF5FF;
  border-radius: 10px;
}

.logo-text {
  font-size: 17px;
  font-weight: 700;
  color: #1E293B;
}

.sidebar-menu {
  border-right: none;
  padding: 12px 10px;
  flex: 1;
  background: transparent;
}

.sidebar-menu .el-menu-item {
  height: 44px;
  margin-bottom: 4px;
  border-radius: 10px;
  color: #475569;
  font-size: 14px;
  display: flex;
  align-items: center;
  gap: 10px;
}

.sidebar-menu .el-menu-item:hover {
  background: #F1F5F9;
  color: #1E293B;
}

.sidebar-menu .el-menu-item.is-active {
  background: #EBF5FF;
  color: #3B82F6;
  font-weight: 600;
}

.menu-icon-wrapper {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  border-radius: 8px;
  flex-shrink: 0;
}

.menu-icon-wrapper .el-icon {
  font-size: 16px;
}

.sidebar-footer {
  padding: 16px 20px;
  border-top: 1px solid #F0F4F8;
}

.footer-slogan {
  margin: 0;
  font-size: 13px;
  color: #94A3B8;
  font-weight: 500;
}

.footer-slogan-sub {
  margin: 2px 0 0;
  font-size: 12px;
  color: #CBD5E1;
}

.drawer-content {
  height: 100%;
  background: #fff;
  display: flex;
  flex-direction: column;
}

.main-container {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-width: 0;
}

.header {
  height: 64px;
  padding: 0 24px;
  background: #fff;
  border-bottom: 1px solid #F0F4F8;
}

.header-content {
  display: flex;
  height: 100%;
  justify-content: space-between;
  align-items: center;
  gap: 16px;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: 0;
}

.menu-toggle {
  font-size: 22px;
  padding: 6px;
  color: #64748B;
}

.greeting-title {
  margin: 0;
  font-size: 20px;
  color: #1E293B;
  font-weight: 700;
}

.wave {
  display: inline-block;
  animation: wave 2s ease-in-out infinite;
  transform-origin: 70% 70%;
}

@keyframes wave {
  0%, 100% { transform: rotate(0deg); }
  25% { transform: rotate(20deg); }
  75% { transform: rotate(-10deg); }
}

.greeting-sub {
  margin: 2px 0 0;
  color: #94A3B8;
  font-size: 13px;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 20px;
}

.weather-info {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  color: #64748B;
}

.weather-icon {
  font-size: 18px;
}

.weather-date {
  font-weight: 500;
}

.user-dropdown {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  padding: 4px 8px;
  border-radius: 8px;
  transition: background 0.2s;
}

.user-dropdown:hover {
  background: #F1F5F9;
}

.user-avatar {
  background: #3B82F6;
  color: #fff;
  font-weight: 600;
  font-size: 14px;
}

.username {
  color: #334155;
  font-weight: 600;
  font-size: 14px;
}

.dropdown-arrow {
  color: #94A3B8;
  font-size: 12px;
}

.main-content {
  padding: 20px 24px;
  background: #EBF5FF;
  min-height: 0;
}

@media (max-width: 768px) {
  .header {
    height: 56px;
    padding: 0 12px;
  }

  .greeting-title {
    font-size: 15px;
  }

  .greeting-sub {
    display: none;
  }

  .weather-info {
    display: none;
  }

  .main-content {
    padding: 12px;
  }

  .username {
    display: none;
  }
}
</style>

<style>
.mobile-drawer .el-drawer__body {
  padding: 0;
  background: #fff;
}
</style>
