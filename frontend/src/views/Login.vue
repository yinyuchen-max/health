<template>
  <div class="login-page">
    <section class="login-shell">
      <div class="hero-panel">
        <div class="brand-copy">
          <p class="brand-tag">Health System</p>
<!--          <h1>会“避嫌”的登录页</h1>-->
<!--          <p class="brand-desc">-->
<!--            鼠标移动时眼睛跟随，用户名输入时中间角色彼此对视，密码框聚焦时整体避开视线。-->
<!--          </p>-->
        </div>

        <AnimatedCharacters
          :is-typing="isTypingUsername"
          :is-password-guard-mode="passwordFocused"
        />
      </div>

      <div class="form-panel">
        <div class="form-card">
          <div class="card-header">
            <p>账号登录</p>
            <h2>欢迎回来</h2>
            <span>登录后进入健康数据总览与管理中心</span>
          </div>

          <el-form
            ref="loginFormRef"
            :model="loginForm"
            :rules="rules"
            label-position="top"
            class="login-form"
          >
            <el-form-item label="用户名" prop="username">
              <el-input
                ref="usernameInputRef"
                v-model="loginForm.username"
                placeholder="请输入用户名"
                :prefix-icon="User"
                size="large"
                clearable
                @focus="handleUsernameFocus"
                @blur="handleUsernameBlur"
                @keydown.enter.prevent="focusPasswordInput"
              />
            </el-form-item>

            <el-form-item label="密码" prop="password">
              <el-input
                ref="passwordInputRef"
                v-model="loginForm.password"
                type="password"
                placeholder="请输入密码"
                :prefix-icon="Lock"
                size="large"
                show-password
                @focus="handlePasswordFocus"
                @blur="handlePasswordBlur"
                @keydown.enter.prevent="handleLogin"
              />
            </el-form-item>

            <div class="actions">
              <el-button
                type="primary"
                class="login-button"
                :loading="loading"
                @click="handleLogin"
              >
                登录
              </el-button>
              <el-button class="register-button" @click="handleRegister">
                去注册
              </el-button>
            </div>
            <div class="doctor-link">
              <span class="link-text" @click="handleDoctorRegister">注册医生 →</span>
            </div>
          </el-form>
        </div>
      </div>
    </section>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Lock, User } from '@element-plus/icons-vue'
import AnimatedCharacters from '../components/AnimatedCharacters.vue'
import request from '../utils/request'
import { useUserStore } from '../store/user'

const router = useRouter()
const userStore = useUserStore()

const loginForm = reactive({
  username: '',
  password: ''
})

const loading = ref(false)
const isTypingUsername = ref(false)
const passwordFocused = ref(false)
const loginFormRef = ref(null)
const passwordInputRef = ref(null)

const rules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

const handleUsernameFocus = () => {
  isTypingUsername.value = true
  passwordFocused.value = false
}

const handleUsernameBlur = () => {
  isTypingUsername.value = false
}

const handlePasswordFocus = () => {
  passwordFocused.value = true
  isTypingUsername.value = false
}

const handlePasswordBlur = () => {
  passwordFocused.value = false
}

const focusPasswordInput = () => {
  passwordInputRef.value?.focus?.()
}

const handleLogin = () => {
  loginFormRef.value.validate(async (valid) => {
    if (!valid) {
      return
    }

    loading.value = true
    try {
      const response = await request.post('/user/login', loginForm)
      const token = typeof response?.data === 'string'
        ? response.data
        : typeof response === 'string'
          ? response
          : ''

      if (!token || token.trim().split('.').length !== 3) {
        throw new Error('登录成功，但服务端返回的 token 格式无效')
      }

      userStore.setToken(token)

      try {
        const userInfoResponse = await request.get('/user/info')
        const responseData = userInfoResponse?.data ?? userInfoResponse

        if (!responseData) {
          throw new Error('User info is empty')
        }

        userStore.setUserInfo(responseData)
      } catch (error) {
        userStore.setUserInfo({
          username: loginForm.username,
          email: `${loginForm.username}@health-system.com`,
          role: loginForm.username === 'admin' ? 'admin' : 'user'
        })
      }

      ElMessage.success({
        message: '登录成功',
        duration: 1500,
        showClose: true
      })

      // 判断是否为已审核医生，医生自动进入医生端
      let isDoctor = false
      try {
        const doctorCheck = await request.get('/doctor/check')
        isDoctor = doctorCheck?.data === true || doctorCheck === true
      } catch {
        isDoctor = false
      }

      setTimeout(() => {
        router.push(isDoctor ? '/doctor/dashboard' : '/app/dashboard')
      }, 500)
    } catch (error) {
      let errorMessage = error?.message || '登录失败'

      if (!error?.message && error?.response?.data) {
        const responseData = error.response.data
        if (typeof responseData === 'string') {
          errorMessage = responseData
        } else if (responseData.message) {
          errorMessage = responseData.message
        }
      }

      if (errorMessage.includes('账号已被禁用')) {
        ElMessage.error({
          message: '账号已被禁用，请联系管理员',
          duration: 3000,
          showClose: true
        })
      } else {
        ElMessage.error({
          message: errorMessage,
          duration: 2000,
          showClose: true
        })
      }
    } finally {
      loading.value = false
    }
  })
}

const handleRegister = () => {
  router.push('/register')
}

const handleDoctorRegister = () => {
  router.push('/doctor-register')
}
</script>

<style scoped>
.login-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
  background: linear-gradient(180deg, #edf1f6 0%, #e7ecf3 100%);
}

.login-shell {
  width: min(1280px, 100%);
  min-height: min(780px, calc(100vh - 48px));
  display: grid;
  grid-template-columns: 1.06fr 0.94fr;
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
  background:
    radial-gradient(circle at top left, rgba(255, 255, 255, 0.84), transparent 25%),
    linear-gradient(180deg, #f7f9fd 0%, #eef2f7 100%);
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
  margin-bottom: 34px;
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

.doctor-link {
  text-align: center;
  margin-top: 16px;
}

.link-text {
  font-size: 13px;
  color: #6e7787;
  cursor: pointer;
  transition: color 0.2s;
}

.link-text:hover {
  color: #1f2430;
}

.login-button,
.register-button {
  height: 52px;
  border-radius: 14px;
  font-size: 15px;
  font-weight: 700;
}

.login-button {
  border: none;
  background: #1f2430;
  box-shadow: 0 16px 28px rgba(31, 36, 48, 0.18);
}

.register-button {
  border: 1px solid #d8dee7;
  color: #33394a;
  background: #fff;
}

:deep(.el-form-item) {
  margin-bottom: 22px;
}

:deep(.el-form-item__label) {
  color: #2f3545;
  font-weight: 700;
  padding-bottom: 8px;
}

:deep(.el-input__wrapper) {
  min-height: 54px;
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
  .login-shell {
    grid-template-columns: 1fr;
  }

  .hero-panel {
    min-height: 430px;
  }
}

@media (max-width: 720px) {
  .login-page {
    padding: 12px;
  }

  .login-shell {
    min-height: auto;
    border-radius: 24px;
  }

  .hero-panel {
    padding: 28px 20px 16px;
    min-height: 340px;
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
