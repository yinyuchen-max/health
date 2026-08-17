<template>
  <div class="register-page">
    <section class="register-shell">
      <div class="hero-panel">
        <div class="brand-copy">
          <p class="brand-tag">Health System</p>
          <h1>加入我们</h1>
          <p class="brand-desc">
            创建账号，开始管理您的健康数据、运动记录和智能提醒。
          </p>
        </div>

        <!-- 装饰插图区 -->
        <div class="hero-illustration">
          <svg viewBox="0 0 400 220" fill="none" xmlns="http://www.w3.org/2000/svg">
            <!-- 背景圆 -->
            <circle cx="200" cy="110" r="90" fill="#e8edf5" opacity="0.6"/>
            <circle cx="200" cy="110" r="65" fill="#f0f4fa" opacity="0.8"/>
            <!-- 心率线 -->
            <polyline points="110,110 140,110 155,80 170,140 185,95 200,120 215,110 250,110"
              stroke="#1f2430" stroke-width="2.5" fill="none" stroke-linecap="round" stroke-linejoin="round"/>
            <!-- 心形 -->
            <path d="M195 98c0-6 5-10 10-10s10 4 10 10c0 10-10 16-10 16s-10-6-10-16z"
              fill="#e74c3c" opacity="0.8"/>
            <!-- 运动人形 -->
            <circle cx="290" cy="75" r="10" fill="#1f2430" opacity="0.7"/>
            <line x1="290" y1="85" x2="290" y2="115" stroke="#1f2430" stroke-width="2.5" stroke-linecap="round"/>
            <line x1="290" y1="95" x2="275" y2="105" stroke="#1f2430" stroke-width="2.5" stroke-linecap="round"/>
            <line x1="290" y1="95" x2="305" y2="88" stroke="#1f2430" stroke-width="2.5" stroke-linecap="round"/>
            <line x1="290" y1="115" x2="278" y2="135" stroke="#1f2430" stroke-width="2.5" stroke-linecap="round"/>
            <line x1="290" y1="115" x2="302" y2="135" stroke="#1f2430" stroke-width="2.5" stroke-linecap="round"/>
            <!-- 数据柱状图 -->
            <rect x="105" y="140" width="14" height="30" rx="3" fill="#1f2430" opacity="0.15"/>
            <rect x="125" y="130" width="14" height="40" rx="3" fill="#1f2430" opacity="0.25"/>
            <rect x="145" y="120" width="14" height="50" rx="3" fill="#1f2430" opacity="0.35"/>
            <rect x="165" y="135" width="14" height="35" rx="3" fill="#1f2430" opacity="0.2"/>
            <!-- 浮动装饰 -->
            <circle cx="320" cy="60" r="5" fill="#e74c3c" opacity="0.3"/>
            <circle cx="100" cy="80" r="4" fill="#1f2430" opacity="0.15"/>
            <circle cx="330" cy="160" r="6" fill="#1f2430" opacity="0.1"/>
          </svg>
        </div>

        <!-- 数据统计 -->
        <div class="hero-stats">
          <div class="stat-item">
            <span class="stat-number">5+</span>
            <span class="stat-label">健康指标</span>
          </div>
          <div class="stat-divider"></div>
          <div class="stat-item">
            <span class="stat-number">24h</span>
            <span class="stat-label">智能监测</span>
          </div>
          <div class="stat-divider"></div>
          <div class="stat-item">
            <span class="stat-number">AI</span>
            <span class="stat-label">健康分析</span>
          </div>
        </div>

        <div class="hero-footer">
          <div class="feature-item">
            <el-icon :size="20"><DataLine /></el-icon>
            <span>健康数据可视化</span>
          </div>
          <div class="feature-item">
            <el-icon :size="20"><ChatDotRound /></el-icon>
            <span>AI 智能健康顾问</span>
          </div>
          <div class="feature-item">
            <el-icon :size="20"><Bell /></el-icon>
            <span>个性化提醒通知</span>
          </div>
        </div>
      </div>

      <div class="form-panel">
        <div class="form-card">
          <div class="card-header">
            <p>创建账号</p>
            <h2>注册新账户</h2>
            <span>填写以下信息开始您的健康之旅</span>
          </div>

          <el-form
            ref="registerFormRef"
            :model="registerForm"
            :rules="rules"
            label-position="top"
            class="register-form"
          >
            <el-form-item label="用户名" prop="username">
              <el-input
                v-model="registerForm.username"
                placeholder="请输入用户名（3-20个字符）"
                :prefix-icon="User"
                size="large"
                clearable
                @keydown.enter.prevent="$refs.passwordInputRef?.focus?.()"
              />
            </el-form-item>

            <el-form-item label="密码" prop="password">
              <el-input
                ref="passwordInputRef"
                v-model="registerForm.password"
                type="password"
                placeholder="请输入密码（至少6个字符）"
                :prefix-icon="Lock"
                size="large"
                show-password
                @keydown.enter.prevent="$refs.confirmInputRef?.focus?.()"
              />
            </el-form-item>

            <el-form-item label="确认密码" prop="confirmPassword">
              <el-input
                ref="confirmInputRef"
                v-model="registerForm.confirmPassword"
                type="password"
                placeholder="请再次输入密码"
                :prefix-icon="Lock"
                size="large"
                show-password
                @keydown.enter.prevent="$refs.emailInputRef?.focus?.()"
              />
            </el-form-item>

            <el-form-item label="邮箱" prop="email">
              <el-input
                ref="emailInputRef"
                v-model="registerForm.email"
                placeholder="请输入邮箱地址"
                :prefix-icon="Message"
                size="large"
                clearable
                @keydown.enter.prevent="$refs.phoneInputRef?.focus?.()"
              />
            </el-form-item>

            <el-form-item label="手机号（选填）" prop="phone">
              <el-input
                ref="phoneInputRef"
                v-model="registerForm.phone"
                placeholder="请输入手机号"
                :prefix-icon="Phone"
                size="large"
                clearable
                @keydown.enter.prevent="handleRegister"
              />
            </el-form-item>

            <div class="actions">
              <el-button
                type="primary"
                class="register-button"
                :loading="loading"
                @click="handleRegister"
              >
                注册
              </el-button>
              <el-button class="login-button" @click="goToLogin">
                返回登录
              </el-button>
            </div>
          </el-form>
        </div>
      </div>
    </section>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Lock, User, Message, Phone, DataLine, ChatDotRound, Bell } from '@element-plus/icons-vue'
import request from '../utils/request'

const router = useRouter()
const registerFormRef = ref(null)
const passwordInputRef = ref(null)
const confirmInputRef = ref(null)
const emailInputRef = ref(null)
const phoneInputRef = ref(null)
const loading = ref(false)

const registerForm = reactive({
  username: '',
  password: '',
  confirmPassword: '',
  email: '',
  phone: ''
})

const validateConfirmPassword = (rule, value, callback) => {
  if (value !== registerForm.password) {
    callback(new Error('两次输入的密码不一致'))
  } else {
    callback()
  }
}

const rules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 3, max: 20, message: '用户名长度为3-20个字符', trigger: 'blur' }
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, message: '密码至少6个字符', trigger: 'blur' }
  ],
  confirmPassword: [
    { required: true, message: '请确认密码', trigger: 'blur' },
    { validator: validateConfirmPassword, trigger: 'blur' }
  ],
  email: [
    { required: true, message: '请输入邮箱', trigger: 'blur' },
    { type: 'email', message: '邮箱格式不正确', trigger: 'blur' }
  ],
  phone: [
    { pattern: /^1[3-9]\d{9}$/, message: '手机号格式不正确', trigger: 'blur' }
  ]
}

const handleRegister = () => {
  registerFormRef.value.validate(async (valid) => {
    if (!valid) return

    loading.value = true
    try {
      const { confirmPassword, ...submitData } = registerForm
      await request.post('/user/register', submitData)
      ElMessage.success({
        message: '注册成功，请登录',
        duration: 2000,
        showClose: true
      })
      setTimeout(() => {
        router.push('/')
      }, 500)
    } catch (error) {
      const message = error?.message || '注册失败，请稍后重试'
      if (message.includes('已存在')) {
        ElMessage.warning({
          message: '该用户名已被注册，请换一个',
          duration: 3000,
          showClose: true
        })
        // 清空用户名输入，让用户重新输入
        registerForm.username = ''
        registerFormRef.value?.validateField?.('username')
      } else {
        ElMessage.error({
          message,
          duration: 3000,
          showClose: true
        })
      }
    } finally {
      loading.value = false
    }
  })
}

const goToLogin = () => {
  router.push('/')
}
</script>

<style scoped>
.register-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
  background: linear-gradient(180deg, #edf1f6 0%, #e7ecf3 100%);
}

.register-shell {
  width: min(1280px, 100%);
  min-height: min(820px, calc(100vh - 48px));
  display: grid;
  grid-template-columns: 0.94fr 1.06fr;
  background: #fff;
  border-radius: 40px;
  overflow: hidden;
  box-shadow: 0 28px 90px rgba(43, 52, 69, 0.14);
}

.hero-panel {
  padding: 58px 48px 34px;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  gap: 24px;
  background:
    radial-gradient(circle at top right, rgba(255, 255, 255, 0.84), transparent 25%),
    linear-gradient(180deg, #f0f4fa 0%, #e8edf5 100%);
}

.brand-copy {
  max-width: 520px;
}

.brand-tag {
  margin: 0 0 18px;
  color: #7d8797;
  font-size: 13px;
  letter-spacing: 0.18em;
  text-transform: uppercase;
  font-weight: 700;
}

.brand-copy h1 {
  margin: 0;
  color: #1f2430;
  font-size: clamp(34px, 4vw, 54px);
  line-height: 1.04;
  letter-spacing: -0.03em;
}

.brand-desc {
  margin: 18px 0 0;
  max-width: 480px;
  color: #5f6979;
  font-size: 16px;
  line-height: 1.85;
}

/* 装饰插图 */
.hero-illustration {
  display: flex;
  justify-content: center;
  align-items: center;
  flex: 1;
  min-height: 0;
}

.hero-illustration svg {
  width: 100%;
  max-width: 380px;
  height: auto;
}

/* 数据统计 */
.hero-stats {
  display: flex;
  align-items: center;
  gap: 20px;
  padding: 16px 0;
}

.stat-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
}

.stat-number {
  font-size: 24px;
  font-weight: 800;
  color: #1f2430;
  letter-spacing: -0.02em;
}

.stat-label {
  font-size: 12px;
  color: #7d8797;
  font-weight: 500;
}

.stat-divider {
  width: 1px;
  height: 32px;
  background: #d0d7e2;
}

.hero-footer {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.feature-item {
  display: flex;
  align-items: center;
  gap: 12px;
  color: #5f6979;
  font-size: 14px;
  font-weight: 500;
}

.feature-item .el-icon {
  color: #1f2430;
}

.form-panel {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 32px;
  background: linear-gradient(180deg, #ffffff 0%, #f9fbfd 100%);
}

.form-card {
  width: min(430px, 100%);
  padding: 8px 6px;
}

.card-header {
  margin-bottom: 28px;
}

.card-header p {
  margin: 0;
  color: #828c9b;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.16em;
  text-transform: uppercase;
}

.card-header h2 {
  margin: 12px 0 10px;
  color: #1f2430;
  font-size: 36px;
  line-height: 1.1;
}

.card-header span {
  color: #6e7787;
  font-size: 15px;
  line-height: 1.75;
}

.actions {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 14px;
  margin-top: 18px;
}

.register-button,
.login-button {
  height: 52px;
  border-radius: 14px;
  font-size: 15px;
  font-weight: 700;
}

.register-button {
  border: none;
  background: #1f2430;
  box-shadow: 0 16px 28px rgba(31, 36, 48, 0.18);
}

.login-button {
  border: 1px solid #d8dee7;
  color: #33394a;
  background: #fff;
}

:deep(.el-form-item) {
  margin-bottom: 18px;
}

:deep(.el-form-item__label) {
  color: #2f3545;
  font-weight: 700;
  padding-bottom: 6px;
}

:deep(.el-input__wrapper) {
  min-height: 50px;
  padding-left: 14px;
  padding-right: 14px;
  border-radius: 14px;
  box-shadow: 0 0 0 1px #e1e6ee;
  background: #fff;
}

:deep(.el-input__wrapper.is-focus) {
  box-shadow: 0 0 0 1px #1f2430;
}

:deep(.el-input__inner) {
  font-size: 15px;
}

:deep(.el-input__inner::placeholder) {
  color: #9aa3b3;
}

@media (max-width: 1080px) {
  .register-shell {
    grid-template-columns: 1fr;
  }

  .hero-panel {
    min-height: 280px;
    gap: 16px;
  }

  .hero-illustration svg {
    max-width: 280px;
  }

  .hero-stats {
    gap: 16px;
    padding: 12px 0;
  }

  .stat-number {
    font-size: 20px;
  }
}

@media (max-width: 720px) {
  .register-page {
    padding: 12px;
  }

  .register-shell {
    min-height: auto;
    border-radius: 24px;
  }

  .hero-panel {
    padding: 28px 20px 16px;
    min-height: 200px;
    gap: 12px;
  }

  .hero-illustration {
    display: none;
  }

  .hero-stats {
    padding: 8px 0;
  }

  .stat-number {
    font-size: 18px;
  }

  .form-panel {
    padding: 22px 18px 26px;
  }

  .brand-copy h1,
  .card-header h2 {
    font-size: 28px;
  }

  .brand-desc,
  .card-header span {
    font-size: 14px;
  }

  .actions {
    grid-template-columns: 1fr;
  }
}
</style>
