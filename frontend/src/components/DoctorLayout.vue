<template>
  <div class="doctor-layout">
    <!-- 桌面端侧边栏 -->
    <el-aside v-if="!isMobile" width="240px" class="sidebar">
      <div class="logo">
        <div class="logo-icon">
          <el-icon :size="22" color="#3B82F6"><FirstAidKit /></el-icon>
        </div>
        <span class="logo-text">医生工作站</span>
      </div>

      <el-menu
        :default-active="activeMenu"
        router
        class="sidebar-menu"
      >
        <el-menu-item index="/doctor/dashboard">
          <div class="menu-icon-wrapper" style="background: #3B82F618">
            <el-icon :style="{ color: '#3B82F6' }"><HomeFilled /></el-icon>
          </div>
          <span>工作台</span>
        </el-menu-item>
        <el-menu-item index="/doctor/appointments">
          <div class="menu-icon-wrapper" style="background: #F59E0B18">
            <el-icon :style="{ color: '#F59E0B' }"><Calendar /></el-icon>
          </div>
          <span>预约患者</span>
        </el-menu-item>
        <el-menu-item index="/doctor/conversations">
          <div class="menu-icon-wrapper" style="background: #10B98118">
            <el-icon :style="{ color: '#10B981' }"><ChatLineRound /></el-icon>
          </div>
          <span>患者对话</span>
        </el-menu-item>
      </el-menu>

      <div class="sidebar-footer">
        <el-button class="switch-btn" text @click="router.push('/app/dashboard')">
          <el-icon><Switch /></el-icon>
          <span>切换到用户端</span>
        </el-button>
      </div>
    </el-aside>

    <!-- 移动端抽屉菜单 -->
    <el-drawer
      v-model="drawerVisible"
      direction="ltr"
      :size="260"
      :show-close="false"
      :with-header="false"
      class="mobile-doctor-drawer"
    >
      <div class="drawer-content">
        <div class="logo">
          <div class="logo-icon">
            <el-icon :size="22" color="#3B82F6"><FirstAidKit /></el-icon>
          </div>
          <span class="logo-text">医生工作站</span>
        </div>

        <el-menu
          :default-active="activeMenu"
          router
          class="sidebar-menu"
          @select="drawerVisible = false"
        >
          <el-menu-item index="/doctor/dashboard">
            <div class="menu-icon-wrapper" style="background: #3B82F618">
              <el-icon :style="{ color: '#3B82F6' }"><HomeFilled /></el-icon>
            </div>
            <span>工作台</span>
          </el-menu-item>
          <el-menu-item index="/doctor/appointments">
            <div class="menu-icon-wrapper" style="background: #F59E0B18">
              <el-icon :style="{ color: '#F59E0B' }"><Calendar /></el-icon>
            </div>
            <span>预约患者</span>
          </el-menu-item>
          <el-menu-item index="/doctor/conversations">
            <div class="menu-icon-wrapper" style="background: #10B98118">
              <el-icon :style="{ color: '#10B981' }"><ChatLineRound /></el-icon>
            </div>
            <span>患者对话</span>
          </el-menu-item>
        </el-menu>

        <div class="sidebar-footer">
          <el-button class="switch-btn" text @click="switchToUser">
            <el-icon><Switch /></el-icon>
            <span>切换到用户端</span>
          </el-button>
        </div>
      </div>
    </el-drawer>

    <el-container class="main-container">
      <el-header class="header">
        <div class="header-content">
          <div class="header-left">
            <el-button v-if="isMobile" class="menu-toggle" text @click="drawerVisible = true">
              <el-icon :size="22"><Operation /></el-icon>
            </el-button>
            <div class="welcome-area">
              <h2 class="welcome-title">欢迎，{{ doctorName }}医生 👋</h2>
              <p class="welcome-subtitle">{{ doctorInfo?.hospital || '健康管理系统' }} · {{ doctorInfo?.department || '' }}{{ doctorInfo?.title ? ' · ' + doctorInfo.title : '' }}</p>
            </div>
          </div>
          <div class="user-info">
            <el-avatar :size="36" class="doctor-avatar">{{ (doctorName || 'D').slice(0, 1) }}</el-avatar>
            <span v-if="!isMobile" class="username">{{ userStore.userInfo.username || '医生' }}</span>
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
  HomeFilled,
  Calendar,
  ChatLineRound,
  FirstAidKit,
  Operation,
  Switch,
  SwitchButton
} from '@element-plus/icons-vue'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const activeMenu = computed(() => route.path)

// 响应式：检测移动端
const isMobile = ref(false)
const drawerVisible = ref(false)

const checkMobile = () => {
  isMobile.value = window.innerWidth < 768
}

const doctorInfo = ref(null)
const doctorName = computed(() => doctorInfo.value?.realName || userStore.userInfo.username || '医生')

const fetchDoctorInfo = async () => {
  try {
    const res = await request.get('/doctor/my-info')
    doctorInfo.value = res?.data || res
  } catch {
    doctorInfo.value = null
  }
}

const switchToUser = () => {
  drawerVisible.value = false
  router.push('/app/dashboard')
}

const handleLogout = () => {
  userStore.logout()
  router.push('/')
}

onMounted(() => {
  fetchDoctorInfo()
  checkMobile()
  window.addEventListener('resize', checkMobile)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', checkMobile)
})
</script>

<style scoped>
.doctor-layout {
  display: flex;
  min-height: 100vh;
  background: #EBF5FF;
}

.sidebar {
  display: flex;
  flex-direction: column;
  background: #fff;
  border-right: 1px solid #E8F0FE;
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

.drawer-content {
  height: 100%;
  background: #fff;
  display: flex;
  flex-direction: column;
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
  padding: 12px;
  border-top: 1px solid #F0F4F8;
}

.switch-btn {
  width: 100%;
  color: #64748B;
}

.switch-btn:hover {
  color: #3B82F6;
  background: #F1F5F9;
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
}

.welcome-title {
  margin: 0;
  font-size: 20px;
  color: #1E293B;
  font-weight: 700;
}

.welcome-subtitle {
  margin: 3px 0 0;
  color: #64748b;
  font-size: 13px;
}

.user-info {
  display: flex;
  align-items: center;
  gap: 10px;
}

.doctor-avatar {
  background: linear-gradient(135deg, #1e40af, #3b82f6);
  color: #fff;
  font-weight: 700;
}

.username {
  color: #334155;
  font-weight: 600;
}

.logout-btn {
  color: #475569;
}

.main-content {
  padding: 20px 24px;
  background: #EBF5FF;
  min-height: 0;
}

/* 移动端适配 */
@media (max-width: 768px) {
  .header {
    height: 56px;
    padding: 0 12px;
  }

  .welcome-title {
    font-size: 15px;
  }

  .welcome-subtitle {
    display: none;
  }

  .main-content {
    padding: 12px;
  }

  .user-info {
    gap: 6px;
  }

  .username {
    display: none;
  }
}
</style>

<style>
/* 抽屉全局样式覆盖 */
.mobile-doctor-drawer .el-drawer__body {
  padding: 0;
  background: #fff;
}
</style>
