<template>
  <div class="layout-container">
    <!-- 桌面端侧边栏 -->
    <el-aside v-if="!isMobile" width="240px" class="sidebar">
      <div class="logo">
        <el-icon :size="28" color="#38bdf8"><MagicStick /></el-icon>
        <span>健康管理系统</span>
      </div>

      <el-menu
        :default-active="activeMenu"
        router
        class="sidebar-menu"
        background-color="#12314a"
        text-color="#dbeafe"
        active-text-color="#7dd3fc"
      >
        <template v-for="item in menuItems" :key="item.index">
          <el-menu-item v-if="!item.hidden" :index="item.index">
            <el-icon><component :is="item.icon" /></el-icon>
            <span>{{ item.label }}</span>
          </el-menu-item>
        </template>
      </el-menu>
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
          <el-icon :size="28" color="#38bdf8"><MagicStick /></el-icon>
          <span>健康管理系统</span>
        </div>

        <el-menu
          :default-active="activeMenu"
          router
          class="sidebar-menu"
          background-color="#12314a"
          text-color="#dbeafe"
          active-text-color="#7dd3fc"
          @select="drawerVisible = false"
        >
          <template v-for="item in menuItems" :key="item.index">
            <el-menu-item v-if="!item.hidden" :index="item.index">
              <el-icon><component :is="item.icon" /></el-icon>
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
            <div>
              <h2 class="title">健康管理系统</h2>
              <p class="subtitle">记录数据，生成建议，持续改善</p>
            </div>
          </div>
          <div class="user-info">
            <el-avatar :size="36">{{ (userStore.userInfo.username || 'U').slice(0, 1).toUpperCase() }}</el-avatar>
            <span v-if="!isMobile" class="username">{{ userStore.userInfo.username || '用户' }}</span>
            <el-button text @click="handleLogout" class="logout-btn">
              <el-icon><SwitchButton /></el-icon>
              <span v-if="!isMobile">退出登录</span>
            </el-button>
          </div>
        </div>
      </el-header>

      <el-main class="main-content">
        <router-view />
      </el-main>
    </el-container>
  </div>
</template>

<script setup>
import { computed, ref, onMounted, onBeforeUnmount } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '../store/user'
import request from '../utils/request'
import {
  Bell,
  ChatDotRound,
  ChatLineRound,
  Clock,
  Document,
  FirstAidKit,
  HomeFilled,
  MagicStick,
  Operation,
  Opportunity,
  Setting,
  SwitchButton,
  Timer,
  TrendCharts,
  User,
  UserFilled
} from '@element-plus/icons-vue'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const activeMenu = computed(() => route.path)
const isAdmin = computed(() => userStore.userInfo?.username === 'admin')

// 响应式：检测移动端
const isMobile = ref(false)
const drawerVisible = ref(false)

const checkMobile = () => {
  isMobile.value = window.innerWidth < 768
}

// 菜单配置
const menuItems = computed(() => [
  { index: '/app/dashboard', icon: HomeFilled, label: '首页', hidden: false },
  { index: '/app/health', icon: Document, label: '健康记录', hidden: false },
  { index: '/app/sport', icon: Timer, label: '运动记录', hidden: false },
  { index: '/app/reminder', icon: Bell, label: '提醒设置', hidden: false },
  { index: '/app/history', icon: Clock, label: '历史记录', hidden: false },
  { index: '/app/analytics', icon: TrendCharts, label: '数据分析', hidden: false },
  { index: '/app/smart-health', icon: Opportunity, label: '智能健康', hidden: false },
  { index: '/app/ai-chat', icon: ChatDotRound, label: 'AI 对话', hidden: false },
  { index: '/app/doctors', icon: FirstAidKit, label: '医生列表', hidden: false },
  { index: '/app/doctor-chat', icon: ChatLineRound, label: '医生咨询', hidden: false },
  { index: '/doctor/dashboard', icon: UserFilled, label: '医生工作台', hidden: !isDoctor.value },
  { index: '/app/profile', icon: User, label: '个人信息', hidden: false },
  { index: '/app/user-management', icon: Setting, label: '用户管理', hidden: !isAdmin.value },
  { index: '/app/doctor-management', icon: Setting, label: '医生审核', hidden: !isAdmin.value }
])

// 医生角色检测
const isDoctor = ref(false)
const checkDoctorStatus = async () => {
  try {
    const res = await request.get('/doctor/check')
    isDoctor.value = res?.data === true || res === true
  } catch {
    isDoctor.value = false
  }
}

const handleLogout = () => {
  userStore.logout()
  router.push('/')
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
  background: #eff6ff;
}

.sidebar {
  background: linear-gradient(180deg, #12314a, #0b2236);
  color: #fff;
}

.logo {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  height: 64px;
  color: #fff;
  font-weight: 700;
  border-bottom: 1px solid rgba(255, 255, 255, 0.08);
}

.drawer-content {
  height: 100%;
  background: linear-gradient(180deg, #12314a, #0b2236);
  display: flex;
  flex-direction: column;
}

.sidebar-menu {
  border-right: none;
  padding: 10px 8px;
  flex: 1;
}

.sidebar-menu .el-menu-item {
  margin-bottom: 6px;
  border-radius: 12px;
}

.main-container {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-width: 0;
}

.header {
  height: 72px;
  padding: 0 20px;
  background: rgba(255, 255, 255, 0.9);
  backdrop-filter: blur(12px);
  border-bottom: 1px solid rgba(15, 23, 42, 0.06);
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
}

.title {
  margin: 0;
  font-size: 24px;
  color: #0f172a;
}

.subtitle {
  margin: 4px 0 0;
  color: #64748b;
  font-size: 13px;
}

.user-info {
  display: flex;
  align-items: center;
  gap: 10px;
}

.username {
  color: #334155;
  font-weight: 600;
}

.logout-btn {
  color: #475569;
}

.main-content {
  padding: 20px;
  background:
    radial-gradient(circle at top right, rgba(125, 211, 252, 0.18), transparent 24%),
    linear-gradient(180deg, #eff6ff, #f8fafc);
}

/* 移动端适配 */
@media (max-width: 768px) {
  .header {
    height: 60px;
    padding: 0 12px;
  }

  .title {
    font-size: 16px;
  }

  .subtitle {
    display: none;
  }

  .main-content {
    padding: 12px;
  }

  .user-info {
    gap: 6px;
  }
}
</style>

<style>
/* 抽屉全局样式覆盖 */
.mobile-drawer .el-drawer__body {
  padding: 0;
  background: linear-gradient(180deg, #12314a, #0b2236);
}
</style>
