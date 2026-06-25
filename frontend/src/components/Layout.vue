<template>
  <div class="layout-container">
    <el-aside width="240px" class="sidebar">
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
        <el-menu-item index="/app/dashboard">
          <el-icon><HomeFilled /></el-icon>
          <span>首页</span>
        </el-menu-item>
        <el-menu-item index="/app/health">
          <el-icon><Document /></el-icon>
          <span>健康记录</span>
        </el-menu-item>
        <el-menu-item index="/app/sport">
          <el-icon><Timer /></el-icon>
          <span>运动记录</span>
        </el-menu-item>
        <el-menu-item index="/app/reminder">
          <el-icon><Bell /></el-icon>
          <span>提醒设置</span>
        </el-menu-item>
        <el-menu-item index="/app/history">
          <el-icon><Clock /></el-icon>
          <span>历史记录</span>
        </el-menu-item>
        <el-menu-item index="/app/analytics">
          <el-icon><TrendCharts /></el-icon>
          <span>数据分析</span>
        </el-menu-item>
        <el-menu-item index="/app/smart-health">
          <el-icon><Opportunity /></el-icon>
          <span>智能健康</span>
        </el-menu-item>
        <el-menu-item index="/app/profile">
          <el-icon><User /></el-icon>
          <span>个人信息</span>
        </el-menu-item>
        <el-menu-item v-if="isAdmin" index="/app/user-management">
          <el-icon><Setting /></el-icon>
          <span>用户管理</span>
        </el-menu-item>
      </el-menu>
    </el-aside>

    <el-container class="main-container">
      <el-header class="header">
        <div class="header-content">
          <div>
            <h2 class="title">健康管理系统</h2>
            <p class="subtitle">记录数据，生成建议，持续改善</p>
          </div>
          <div class="user-info">
            <el-avatar :size="36">{{ (userStore.userInfo.username || 'U').slice(0, 1).toUpperCase() }}</el-avatar>
            <span class="username">{{ userStore.userInfo.username || '用户' }}</span>
            <el-button text @click="handleLogout" class="logout-btn">
              <el-icon><SwitchButton /></el-icon>
              <span>退出登录</span>
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
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '../store/user'
import {
  Bell,
  Clock,
  Document,
  HomeFilled,
  MagicStick,
  Opportunity,
  Setting,
  SwitchButton,
  Timer,
  TrendCharts,
  User
} from '@element-plus/icons-vue'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const activeMenu = computed(() => route.path)
const isAdmin = computed(() => userStore.userInfo?.username === 'admin')

const handleLogout = () => {
  userStore.logout()
  router.push('/')
}
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

.sidebar-menu {
  border-right: none;
  padding: 10px 8px;
}

.sidebar-menu .el-menu-item {
  margin-bottom: 6px;
  border-radius: 12px;
}

.main-container {
  display: flex;
  flex: 1;
  flex-direction: column;
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
</style>
