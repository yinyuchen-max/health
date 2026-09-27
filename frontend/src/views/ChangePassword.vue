<template>
  <div class="change-password">
    <el-card class="password-card fade-in">
      <div class="password-header">
        <div class="icon-circle">
          <el-icon :size="50" color="#ffffff"><Lock /></el-icon>
        </div>
        <h3 class="title-animation">修改密码</h3>
        <p class="subtitle-animation">为保障账号安全，修改成功后需重新登录</p>
      </div>

      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-width="100px"
        class="password-form form-animation"
        style="max-width: 500px"
      >
        <el-form-item label="旧密码" prop="oldPassword" class="form-item-animate">
          <el-input
            v-model="form.oldPassword"
            type="password"
            show-password
            placeholder="请输入当前密码"
            class="gray-input"
          >
            <template #prefix>
              <el-icon><Lock /></el-icon>
            </template>
          </el-input>
        </el-form-item>

        <el-form-item label="新密码" prop="newPassword" class="form-item-animate" style="animation-delay: 0.1s;">
          <el-input
            v-model="form.newPassword"
            type="password"
            show-password
            placeholder="6-50 个字符"
            class="gray-input"
          >
            <template #prefix>
              <el-icon><Key /></el-icon>
            </template>
          </el-input>
        </el-form-item>

        <el-form-item label="确认新密码" prop="confirmPassword" class="form-item-animate" style="animation-delay: 0.2s;">
          <el-input
            v-model="form.confirmPassword"
            type="password"
            show-password
            placeholder="请再次输入新密码"
            class="gray-input"
          >
            <template #prefix>
              <el-icon><Key /></el-icon>
            </template>
          </el-input>
        </el-form-item>

        <el-form-item class="button-animate" style="animation-delay: 0.3s;">
          <el-button
            type="primary"
            class="submit-button"
            :loading="isSubmitting"
            :disabled="isSubmitting"
            @click="submitChangePassword"
          >
            <el-icon v-if="!isSubmitting"><Check /></el-icon>
            {{ isSubmitting ? '提交中...' : '确认修改' }}
          </el-button>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Check, Key, Lock } from '@element-plus/icons-vue'
import { useUserStore } from '../store/user'
import request from '../utils/request'

const router = useRouter()
const userStore = useUserStore()

const formRef = ref(null)
const isSubmitting = ref(false)

const form = reactive({
  oldPassword: '',
  newPassword: '',
  confirmPassword: ''
})

const validateConfirmPassword = (rule, value, callback) => {
  if (value !== form.newPassword) {
    callback(new Error('两次输入的新密码不一致'))
  } else {
    callback()
  }
}

const rules = {
  oldPassword: [{ required: true, message: '请输入旧密码', trigger: 'blur' }],
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 6, max: 50, message: '新密码长度需在6-50个字符之间', trigger: 'blur' }
  ],
  confirmPassword: [
    { required: true, message: '请再次输入新密码', trigger: 'blur' },
    { validator: validateConfirmPassword, trigger: 'blur' }
  ]
}

const submitChangePassword = async () => {
  if (!formRef.value) return

  try {
    await formRef.value.validate()
  } catch {
    return
  }

  try {
    isSubmitting.value = true
    const response = await request.put('/user/password', {
      oldPassword: form.oldPassword,
      newPassword: form.newPassword
    })

    if (response && response.code !== undefined && response.code !== 200) {
      throw new Error(response.message || '修改失败')
    }

    ElMessage.success('密码修改成功，请重新登录')

    // 后端已将当前 token 加入黑名单，前端清除登录态并跳转登录页
    userStore.logout()
    router.push('/')
  } catch (error) {
    console.error('Failed to change password:', error)
    ElMessage.error(error.message || '密码修改失败，请稍后重试')
  } finally {
    isSubmitting.value = false
  }
}
</script>

<style scoped>
.change-password {
  padding: 20px;
  background: transparent;
  min-height: auto;
}

.password-card {
  background: #fff;
  border-radius: 16px;
  box-shadow: 0 8px 24px rgba(15, 23, 42, 0.06);
  border: 1px solid #F0F4F8;
  max-width: 650px;
  margin: 0 auto;
  overflow: hidden;
}

.password-card:hover {
  transform: none;
  box-shadow: 0 8px 24px rgba(15, 23, 42, 0.06);
}

.password-header {
  background: #fff;
  padding: 32px 20px;
  text-align: center;
  position: relative;
  border-bottom: 1px solid #F0F4F8;
}

.password-header::before {
  display: none;
}

@keyframes shimmer {
  0% { transform: translate(-50%, -50%); }
  100% { transform: translate(50%, 50%); }
}

.icon-circle {
  width: 80px;
  height: 80px;
  border-radius: 50%;
  background: #EBF5FF;
  display: flex;
  align-items: center;
  justify-content: center;
  margin: 0 auto 16px;
  border: 3px solid #F0F4F8;
}

.icon-circle .el-icon {
  color: #3B82F6 !important;
}

.title-animation {
  color: #1E293B;
  font-size: 24px;
  font-weight: 700;
  margin: 0 0 8px 0;
  text-shadow: none;
}

.subtitle-animation {
  color: #64748B;
  font-size: 14px;
  margin: 0;
}

.password-form {
  animation: fadeInUp 0.6s ease-out;
  padding: 30px 20px 20px;
}

.form-item-animate {
  animation: fadeInUp 0.6s ease-out forwards;
  opacity: 0;
}

.button-animate {
  animation: fadeInUp 0.6s ease-out forwards;
  opacity: 0;
}

.gray-input :deep(.el-input__wrapper) {
  border-radius: 10px;
  background: #F8FAFC;
  box-shadow: none;
  transition: all 0.3s ease;
  border: 1px solid #F0F4F8;
}

.gray-input :deep(.el-input__wrapper:hover) {
  box-shadow: 0 0 0 2px rgba(59, 130, 246, 0.1);
  border-color: #3B82F6;
}

.gray-input :deep(.el-input__wrapper.is-focus) {
  box-shadow: 0 0 0 2px rgba(59, 130, 246, 0.15);
}

.submit-button {
  width: 100%;
  height: 45px;
  font-size: 16px;
  font-weight: 600;
  background: #3B82F6;
  border: none;
  border-radius: 12px;
  transition: all 0.3s ease;
  display: flex;
  align-items: center;
  justify-content: center;
}

.submit-button:hover {
  background: #2563EB;
}

.submit-button:active {
  background: #1D4ED8;
}

:deep(.el-form-item__label) {
  font-weight: 600;
  color: #606266;
  font-size: 14px;
}

.fade-in {
  animation: fadeIn 0.6s ease-out;
}

@keyframes fadeIn {
  0% { opacity: 0; }
  100% { opacity: 1; }
}

@keyframes fadeInDown {
  0% { opacity: 0; transform: translateY(-20px); }
  100% { opacity: 1; transform: translateY(0); }
}

@keyframes fadeInUp {
  0% { opacity: 0; transform: translateY(20px); }
  100% { opacity: 1; transform: translateY(0); }
}

@keyframes bounceIn {
  0% { opacity: 0; transform: scale(0.5); }
  100% { opacity: 1; transform: scale(1); }
}

@media (max-width: 768px) {
  .change-password {
    padding: 10px;
  }

  .password-card {
    padding: 15px;
  }
}
</style>
