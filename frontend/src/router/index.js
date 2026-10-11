import { createRouter, createWebHistory } from 'vue-router'

import { useUserStore } from '../store/user'
import { ElMessage } from 'element-plus'

const routes = [
  {
    path: '/',
    name: 'Login',
    component: () => import('../views/Login.vue')
  },
  {
    path: '/register',
    name: 'Register',
    component: () => import('../views/Register.vue')
  },
  {
    path: '/doctor-register',
    name: 'DoctorRegister',
    component: () => import('../views/DoctorRegister.vue')
  },
  {
    path: '/doctor',
    component: () => import('../components/DoctorLayout.vue'),
    children: [
      {
        path: 'dashboard',
        name: 'DoctorDashboard',
        component: () => import('../views/DoctorDashboard.vue'),
        meta: { requiresAuth: true }
      },
      {
        path: 'appointments',
        name: 'DoctorAppointments',
        component: () => import('../views/DoctorAppointments.vue'),
        meta: { requiresAuth: true }
      },
      {
        path: 'conversations',
        name: 'DoctorConversations',
        component: () => import('../views/DoctorConversations.vue'),
        meta: { requiresAuth: true }
      }
    ]
  },
  {
    path: '/app',
    component: () => import('../components/Layout.vue'),
    children: [
      {
        path: 'dashboard',
        name: 'Dashboard',
        component: () => import('../views/Dashboard.vue'),
        meta: { requiresAuth: true }
      },
      {
        path: 'family',
        name: 'Family',
        component: () => import('../views/FamilyMembers.vue'),
        meta: { requiresAuth: true }
      },
      {
        path: 'health',
        name: 'Health',
        component: () => import('../views/HealthRecord.vue'),
        meta: { requiresAuth: true }
      },
      {
        path: 'sport',
        name: 'Sport',
        component: () => import('../views/SportRecord.vue'),
        meta: { requiresAuth: true }
      },
      {
        path: 'profile',
        name: 'Profile',
        component: () => import('../views/UserProfile.vue'),
        meta: { requiresAuth: true }
      },
      {
        path: 'change-password',
        name: 'ChangePassword',
        component: () => import('../views/ChangePassword.vue'),
        meta: { requiresAuth: true }
      },
      {
        path: 'reminder',
        name: 'Reminder',
        component: () => import('../views/ReminderConfig.vue'),
        meta: { requiresAuth: true }
      },
      {
        path: 'history',
        name: 'History',
        component: () => import('../views/History.vue'),
        meta: { requiresAuth: true }
      },
      {
        path: 'analytics',
        name: 'Analytics',
        component: () => import('../views/Analytics.vue'),
        meta: { requiresAuth: true }
      },
      {
        path: 'smart-health',
        name: 'SmartHealth',
        component: () => import('../views/SmartHealth.vue'),
        meta: { requiresAuth: true }
      },
      { path: 'ai-settings', name: 'AiSettings', component: () => import('../views/AiSettings.vue'), meta: { requiresAuth: true } },
      {
        path: 'ai-chat',
        name: 'AiChat',
        component: () => import('../views/AiChat.vue'),
        meta: { requiresAuth: true }
      },
      {
        path: 'user-management',
        name: 'UserManagement',
        component: () => import('../views/UserManagement.vue'),
        meta: { requiresAuth: true, requiresAdmin: true }
      },
      {
        path: 'doctors',
        name: 'DoctorList',
        component: () => import('../views/DoctorList.vue'),
        meta: { requiresAuth: true }
      },
      {
        path: 'doctor-chat',
        name: 'DoctorChat',
        component: () => import('../views/DoctorChat.vue'),
        meta: { requiresAuth: true }
      },
      {
        path: 'doctor-management',
        name: 'DoctorManagement',
        component: () => import('../views/DoctorManagement.vue'),
        meta: { requiresAuth: true, requiresAdmin: true }
      }
    ]
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach(async to => {
  const user = useUserStore()
  if (to.meta.requiresAuth || to.path === '/' || to.path === '/register') {
    try { await user.ensureSession() }
    catch {
      if (to.meta.requiresAuth && !user.token) { ElMessage.error("认证服务暂时不可用，请稍后重试"); return false }
    }
  }
  if (to.meta.requiresAuth && !user.token) return '/'
  if (user.token && !user.userInfo.id) {
    try {
      const { default: request } = await import('../utils/request')
      const response = await request.get('/user/info')
      if (!response.data?.id) throw new Error('用户信息读取失败')
      user.setUserInfo(response.data)
    } catch {
      if (!user.token) return '/'
      ElMessage.error('用户信息暂时不可用，请稍后重试')
      return false
    }
  }
  if (to.meta.requiresAdmin) {
    try {
      const { default: request } = await import('../utils/request')
      const response = await request.get('/user/info')
      user.setUserInfo(response.data)
    } catch { return '/app/dashboard' }
    if (user.userInfo.role !== 'admin') return '/app/dashboard'
  }
  if ((to.path === '/' || to.path === '/register') && user.token) return '/app/dashboard'
})

export default router
