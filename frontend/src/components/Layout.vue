<template>
  <div class="layout-container">
    <!-- 侧边栏 -->
    <el-aside v-if="!isMobile" width="220px" class="sidebar">
      <router-link to="/app/ai-chat" class="logo">
        <span class="logo-mark" aria-hidden="true">问</span>
        <span class="logo-text">医学问答</span>
      </router-link>

      <el-menu
        :default-active="activeMenu"
        router
        class="sidebar-menu"
      >
        <el-menu-item-group v-for="section in menuSections" :key="section.key" :title="section.title">
          <el-menu-item v-for="item in section.items" :key="item.index" :index="item.index">
            <div class="menu-icon-wrapper">
              <el-icon><component :is="item.icon" /></el-icon>
            </div>
            <span>{{ item.label }}</span>
          </el-menu-item>
        </el-menu-item-group>
      </el-menu>

      <div class="sidebar-footer">
        <p class="footer-slogan">回答不能代替医生诊断</p>
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
        <router-link to="/app/ai-chat" class="logo" @click="drawerVisible = false">
          <span class="logo-mark" aria-hidden="true">问</span>
          <span class="logo-text">医学问答</span>
        </router-link>

        <el-menu
          :default-active="activeMenu"
          router
          class="sidebar-menu"
          @select="drawerVisible = false"
        >
          <el-menu-item-group v-for="section in menuSections" :key="section.key" :title="section.title">
            <el-menu-item v-for="item in section.items" :key="item.index" :index="item.index">
              <div class="menu-icon-wrapper">
                <el-icon><component :is="item.icon" /></el-icon>
              </div>
              <span>{{ item.label }}</span>
            </el-menu-item>
          </el-menu-item-group>
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
              <h2 class="greeting-title">{{ greeting }}，{{ displayName(userStore.userInfo) }}</h2>
              <p class="greeting-sub">有症状、用药或检查结果，直接问</p>
            </div>
          </div>
          <div class="header-right">
            <span class="header-date">{{ currentDate }}</span>
            <el-dropdown trigger="click" @command="handleCommand">
              <div class="user-dropdown">
                <el-avatar :size="36" :src="avatarUrl(userStore.userInfo)" class="user-avatar">{{ displayName(userStore.userInfo).slice(0, 1).toUpperCase() }}</el-avatar>
                <span class="username">{{ displayName(userStore.userInfo) }}</span>
                <el-icon class="dropdown-arrow"><ArrowDown /></el-icon>
              </div>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item command="profile">个人信息</el-dropdown-item>
                  <el-dropdown-item command="family">家庭成员</el-dropdown-item>
                  <el-dropdown-item command="ai-chat">提问</el-dropdown-item>
                  <el-dropdown-item command="password">修改密码</el-dropdown-item>
                  <el-dropdown-item command="logout" divided>退出登录</el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </div>
        </div>
      </el-header>

      <el-main class="main-content" :class="{ 'is-ask': route.path === '/app/ai-chat' }">
        <router-view />
      </el-main>
    </el-container>

    <!-- 桌宠 AI 助手 -->
    <DoctorPet />
  </div>
</template>

<script setup>
import { ElMessage } from 'element-plus'
import { computed, ref, onMounted, onBeforeUnmount } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '../store/user'
import request from '../utils/request'
import { displayName, avatarUrl } from '../utils/user-display'
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
  ChatDotRound,
  ChatLineRound
} from '@element-plus/icons-vue'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const activeMenu = computed(() => route.path)
const isAdmin = computed(() => userStore.userInfo?.role === 'admin')

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

const menuSections = computed(() => [
  {
    key: 'ask',
    title: '',
    items: [
      { index: '/app/ai-chat', icon: ChatDotRound, label: '提问' }
    ]
  },
  {
    key: 'records',
    title: '档案',
    items: [
      { index: '/app/dashboard', icon: HomeFilled, label: '健康总览' },
      { index: '/app/health', icon: Document, label: '健康记录' },
      { index: '/app/sport', icon: Timer, label: '运动记录' },
      { index: '/app/reminder', icon: Bell, label: '提醒设置' },
      { index: '/app/analytics', icon: DataAnalysis, label: '数据分析' },
      { index: '/app/history', icon: Clock, label: '历史记录' }
    ]
  },
  {
    key: 'care',
    title: '就诊',
    items: [
      { index: '/app/smart-health', icon: Opportunity, label: '智能健康' },
      { index: '/app/doctors', icon: FirstAidKit, label: '医生列表' },
      { index: '/app/doctor-chat', icon: ChatLineRound, label: '医生咨询' },
      { index: '/doctor/dashboard', icon: UserFilled, label: '医生工作台', hidden: !isDoctor.value }
    ].filter(item => !item.hidden)
  },
  {
    key: 'account',
    title: '账户',
    items: [
      { index: '/app/profile', icon: User, label: '个人信息' },
      { index: '/app/family', icon: UserFilled, label: '家庭成员' },
      { index: '/app/user-management', icon: Setting, label: '用户管理', hidden: !isAdmin.value },
      { index: '/app/doctor-management', icon: Setting, label: '医生审核', hidden: !isAdmin.value }
    ].filter(item => !item.hidden)
  }
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

const handleCommand = async (command) => {
  if (command === 'logout') {
    try { await userStore.logout() } catch (error) { ElMessage.error(error.message || '退出失败，请稍后重试'); return }
    router.push('/')
  } else if (command === 'profile') {
    router.push('/app/profile')
  } else if (command === 'family') {
    router.push('/app/family')
  } else if (command === 'ai-chat') {
    router.push('/app/ai-chat')
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
  background: #F4F7F6;
  font-family: "Noto Sans SC", "PingFang SC", "Microsoft YaHei", sans-serif;
}

.sidebar {
  background: #1C3D36;
  display: flex;
  flex-direction: column;
}

.logo {
  display: flex;
  align-items: center;
  gap: 10px;
  height: 72px;
  padding: 0 18px;
  text-decoration: none;
  color: #F4F7F6;
}

.logo:focus-visible {
  outline: 2px solid #F4F7F6;
  outline-offset: -4px;
}

.logo-mark {
  display: grid;
  place-items: center;
  width: 28px;
  height: 28px;
  background: #B23A2F;
  color: #F4F7F6;
  font-family: "Noto Serif SC", "Songti SC", serif;
  font-size: 16px;
  font-weight: 700;
  line-height: 1;
}

.logo-text {
  font-family: "Noto Serif SC", "Songti SC", serif;
  font-size: 20px;
  font-weight: 600;
  color: #F4F7F6;
}

.sidebar-menu {
  --el-menu-bg-color: transparent;
  --el-menu-text-color: #D5E3DC;
  --el-menu-hover-text-color: #F4F7F6;
  --el-menu-hover-bg-color: rgba(244, 247, 246, 0.08);
  --el-menu-active-color: #F4F7F6;
  border-right: none;
  padding: 4px 10px 12px;
  flex: 1;
  overflow-y: auto;
  background: transparent;
}

.sidebar-menu :deep(.el-menu-item-group__title) {
  padding: 16px 12px 6px;
  font-size: 12px;
  line-height: 1.4;
  color: rgba(213, 227, 220, 0.62);
}

.sidebar-menu :deep(.el-menu-item-group__title:empty) {
  display: none;
}

.sidebar-menu :deep(.el-menu-item) {
  height: 42px;
  margin-bottom: 2px;
  border-radius: 2px;
  color: #D5E3DC;
  font-size: 14px;
}

.sidebar-menu :deep(.el-menu-item:hover) {
  background: rgba(244, 247, 246, 0.08);
  color: #F4F7F6;
}

.sidebar-menu :deep(.el-menu-item.is-active) {
  background: rgba(244, 247, 246, 0.14);
  color: #F4F7F6;
  font-weight: 600;
  box-shadow: inset 3px 0 0 #B23A2F;
}

.sidebar-menu :deep(.el-menu-item:focus-visible) {
  outline: 2px solid #F4F7F6;
  outline-offset: -2px;
}

.menu-icon-wrapper {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 22px;
  margin-right: 8px;
  flex-shrink: 0;
}

.menu-icon-wrapper .el-icon {
  font-size: 16px;
  color: inherit;
}

.sidebar-footer {
  padding: 14px 18px 18px;
}

.footer-slogan {
  margin: 0;
  font-size: 12px;
  line-height: 1.5;
  color: rgba(213, 227, 220, 0.72);
}

.drawer-content {
  height: 100%;
  background: #1C3D36;
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
  background: #F4F7F6;
  border-bottom: 1px solid #D7E2DC;
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
  font-family: "Noto Serif SC", "Songti SC", serif;
  font-size: 20px;
  color: #1C3D36;
  font-weight: 600;
}

.greeting-sub {
  margin: 2px 0 0;
  color: #5E736C;
  font-size: 13px;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 20px;
}

.header-date {
  font-size: 13px;
  color: #5E736C;
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
  background: #E7EEEA;
}

.user-dropdown:focus-visible {
  outline: 2px solid #1C3D36;
  outline-offset: 2px;
}

.user-avatar {
  background: #1C3D36;
  color: #F4F7F6;
  font-weight: 600;
  font-size: 14px;
}

.username {
  color: #1C3D36;
  font-weight: 600;
  font-size: 14px;
}

.dropdown-arrow {
  color: #94A3B8;
  font-size: 12px;
}

.main-content {
  padding: 20px 24px;
  background: #F4F7F6;
  min-height: 0;
}

.main-content.is-ask {
  padding: 0;
  overflow: hidden;
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

  .header-date {
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
  background: #1C3D36;
}
</style>
