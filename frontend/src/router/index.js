import { createRouter, createWebHistory } from 'vue-router'

const hasValidToken = () => {
  const token = localStorage.getItem('token')
  return typeof token === 'string' && token.trim().split('.').length === 3
}

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

router.beforeEach((to, from, next) => {
  const token = hasValidToken()

  if (to.meta.requiresAuth && !token) {
    next('/')
  } else if (to.meta.requiresAdmin) {
    // 检查是否为管理员
    const userInfo = JSON.parse(localStorage.getItem('userInfo') || '{}')
    if (userInfo.username === 'admin') {
      next()
    } else {
      alert('权限不足，仅管理员可访问')
      next('/app/dashboard')
    }
  } else if (to.path === '/') {
    if (token) {
      next('/app/dashboard')
    } else {
      next()
    }
  } else if (to.path === '/register' && token) {
    next('/app/dashboard')
  } else {
    next()
  }
})

export default router
