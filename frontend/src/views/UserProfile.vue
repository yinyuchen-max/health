<template>
  <div class="user-profile">
    <el-card class="profile-card fade-in">
      <div class="profile-header">
        <div class="avatar-circle">
          <el-icon :size="60" color="#409EFF"><UserFilled /></el-icon>
        </div>
        <h3 class="title-animation">个人信息</h3>
        <p class="subtitle-animation">完善您的个人资料</p>
      </div>

      <div v-if="isLoading" class="loading-container">
        <el-skeleton :rows="8" animated />
      </div>

      <el-form
        v-else
        :model="profile"
        label-width="100px"
        class="profile-form form-animation"
        style="max-width: 500px"
      >
        <el-form-item label="用户名" class="form-item-animate">
          <el-input v-model="profile.username" disabled class="blue-input">
            <template #prefix>
              <el-icon><User /></el-icon>
            </template>
          </el-input>
        </el-form-item>

        <el-form-item label="邮箱" class="form-item-animate" style="animation-delay: 0.1s;">
          <el-input v-model="profile.email" class="blue-input">
            <template #prefix>
              <el-icon><Message /></el-icon>
            </template>
          </el-input>
        </el-form-item>

        <el-form-item label="手机号" class="form-item-animate" style="animation-delay: 0.2s;">
          <el-input v-model="profile.phone" class="blue-input">
            <template #prefix>
              <el-icon><Phone /></el-icon>
            </template>
          </el-input>
        </el-form-item>

        <el-form-item label="性别" class="form-item-animate" style="animation-delay: 0.3s;">
          <el-select v-model="profile.gender" class="blue-select" placeholder="请选择性别">
            <el-option label="男" value="男" />
            <el-option label="女" value="女" />
          </el-select>
        </el-form-item>

        <el-form-item label="年龄" class="form-item-animate" style="animation-delay: 0.4s;">
          <el-input-number v-model="profile.age" :min="1" :max="120" class="blue-number" />
        </el-form-item>

        <el-form-item label="身高 (cm)" class="form-item-animate" style="animation-delay: 0.5s;">
          <el-input-number
            v-model="profile.height"
            :min="50"
            :max="250"
            :precision="1"
            class="blue-number"
          />
        </el-form-item>

        <el-form-item label="体重 (kg)" class="form-item-animate" style="animation-delay: 0.6s;">
          <el-input-number
            v-model="profile.weight"
            :min="20"
            :max="200"
            :precision="1"
            class="blue-number"
          />
        </el-form-item>

        <el-form-item label="账号状态" class="form-item-animate" style="animation-delay: 0.65s;">
          <el-tag :type="profile.status === 1 ? 'success' : 'danger'" size="large">
            {{ profile.status === 1 ? '正常' : '已禁用' }}
          </el-tag>
        </el-form-item>

        <el-form-item class="button-animate" style="animation-delay: 0.7s;">
          <el-button
            type="primary"
            @click="updateProfile"
            class="save-button"
            :loading="isSaving"
            :disabled="isSaving"
          >
            <el-icon v-if="!isSaving"><Check /></el-icon>
            {{ isSaving ? '保存中...' : '保存修改' }}
          </el-button>
        </el-form-item>
      </el-form>

      <div class="stats-row form-animation" style="animation-delay: 0.8s;">
        <div class="stat-item">
          <div class="stat-icon">
            <el-icon :size="24" color="#409EFF"><TrendCharts /></el-icon>
          </div>
          <div class="stat-label">BMI 指数</div>
          <div class="stat-value">{{ bmiDisplay }}</div>
        </div>
        <div class="stat-item">
          <div class="stat-icon">
            <el-icon :size="24" color="#67C23A"><Star /></el-icon>
          </div>
          <div class="stat-label">健康状态</div>
          <div class="stat-value">{{ healthStatus }}</div>
        </div>
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import {
  Check,
  Message,
  Phone,
  Star,
  TrendCharts,
  User,
  UserFilled
} from '@element-plus/icons-vue'
import { useUserStore } from '../store/user'
import request from '../utils/request'

const userStore = useUserStore()

const isLoading = ref(true)
const isSaving = ref(false)

const profile = ref({
  id: '',
  username: '',
  email: '',
  phone: '',
  gender: '',
  age: null,
  height: null,
  weight: null,
  status: 1
})

const mapGenderToText = (gender) => {
  if (gender === 1 || gender === '1' || gender === '男') return '男'
  if (gender === 2 || gender === '2' || gender === '女') return '女'
  return ''
}

const mapGenderToValue = (gender) => {
  if (gender === '男') return 1
  if (gender === '女') return 2
  return null
}

const unwrapResultData = (result) => {
  if (!result || typeof result !== 'object') return null

  if (Object.prototype.hasOwnProperty.call(result, 'code')) {
    if (result.code !== 200) {
      throw new Error(result.message || '接口返回失败')
    }
    return result.data ?? null
  }

  return result
}

const applyProfileData = (userData) => {
  if (!userData || typeof userData !== 'object') {
    throw new Error('未获取到用户信息')
  }

  profile.value = {
    id: userData.id ?? '',
    username: userData.username ?? '',
    email: userData.email ?? '',
    phone: userData.phone ?? '',
    gender: mapGenderToText(userData.gender),
    age: userData.age ?? null,
    height: userData.height ?? null,
    weight: userData.weight ?? null,
    status: userData.status ?? 1
  }
}

const bmiDisplay = computed(() => {
  const { height, weight } = profile.value

  if (!height || !weight || Number(height) <= 0 || Number(weight) <= 0) {
    return '--'
  }

  const heightInMeters = Number(height) / 100
  const bmi = Number(weight) / (heightInMeters * heightInMeters)
  return bmi.toFixed(1)
})

const healthStatus = computed(() => {
  const bmi = Number.parseFloat(bmiDisplay.value)

  if (Number.isNaN(bmi)) return '--'
  if (bmi < 18.5) return '偏瘦'
  if (bmi < 24) return '正常'
  if (bmi < 28) return '偏胖'
  return '肥胖'
})

const fetchProfile = async () => {
  try {
    isLoading.value = true
    const response = await request.get('/user/info')
    const userData = unwrapResultData(response)
    applyProfileData(userData)
  } catch (error) {
    console.error('Failed to fetch profile:', error)
    ElMessage.error(error.message || '加载个人信息失败')
  } finally {
    isLoading.value = false
  }
}

const updateProfile = async () => {
  if (!profile.value.id) {
    ElMessage.error('缺少用户ID，无法保存')
    return
  }

  if (!profile.value.email || !profile.value.phone) {
    ElMessage.warning('请完善必填信息：邮箱、手机号')
    return
  }

  const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/
  if (!emailRegex.test(profile.value.email)) {
    ElMessage.warning('请输入正确的邮箱格式')
    return
  }

  try {
    isSaving.value = true

    const payload = {
      ...profile.value,
      gender: mapGenderToValue(profile.value.gender)
    }

    const response = await request.put('/user/info', payload)
    unwrapResultData(response)

    userStore.updateUserInfo({
      id: profile.value.id,
      username: profile.value.username,
      email: profile.value.email,
      phone: profile.value.phone,
      gender: payload.gender,
      age: profile.value.age,
      height: profile.value.height,
      weight: profile.value.weight
    })

    ElMessage.success('个人信息更新成功')
    await fetchProfile()
  } catch (error) {
    console.error('Failed to update profile:', error)
    ElMessage.error(error.message || '保存失败，请稍后重试')
  } finally {
    isSaving.value = false
  }
}

onMounted(() => {
  fetchProfile()
})

watch(
  () => userStore.userInfo?.id,
  (newId, oldId) => {
    if (newId && newId !== oldId) {
      fetchProfile()
    }
  }
)
</script>

<style scoped>
.user-profile {
  padding: 20px;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  min-height: 100vh;
}

.profile-card {
  background: rgba(255, 255, 255, 0.95);
  backdrop-filter: blur(10px);
  border-radius: 16px;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.1);
  border: 1px solid rgba(255, 255, 255, 0.2);
  transition: all 0.4s cubic-bezier(0.4, 0, 0.2, 1);
  max-width: 650px;
  margin: 0 auto;
  overflow: hidden;
}

.profile-card:hover {
  transform: translateY(-5px);
  box-shadow: 0 12px 40px rgba(64, 158, 255, 0.3);
}

.profile-header {
  background: linear-gradient(135deg, #409EFF 0%, #66b1ff 100%);
  padding: 40px 20px;
  text-align: center;
  position: relative;
  overflow: hidden;
}

.profile-header::before {
  content: '';
  position: absolute;
  top: -50%;
  left: -50%;
  width: 200%;
  height: 200%;
  background: radial-gradient(circle, rgba(255,255,255,0.1) 0%, transparent 70%);
  animation: shimmer 3s infinite;
}

@keyframes shimmer {
  0% { transform: translate(-50%, -50%); }
  100% { transform: translate(50%, 50%); }
}

.avatar-circle {
  width: 100px;
  height: 100px;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.2);
  backdrop-filter: blur(10px);
  display: flex;
  align-items: center;
  justify-content: center;
  margin: 0 auto 20px;
  border: 3px solid rgba(255, 255, 255, 0.3);
  animation: bounceIn 1s ease-out;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.2);
}

.title-animation {
  color: white;
  font-size: 28px;
  font-weight: 700;
  margin: 0 0 8px 0;
  text-shadow: 0 2px 4px rgba(0, 0, 0, 0.2);
  animation: fadeInDown 0.8s ease-out;
  position: relative;
  z-index: 1;
}

.subtitle-animation {
  color: rgba(255, 255, 255, 0.9);
  font-size: 14px;
  margin: 0;
  animation: fadeInUp 0.8s ease-out;
  position: relative;
  z-index: 1;
}

.profile-form {
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

.blue-input :deep(.el-input__wrapper) {
  border-radius: 10px;
  background: linear-gradient(135deg, #f0f9ff 0%, #e0f2fe 100%);
  box-shadow: 0 2px 8px rgba(64, 158, 255, 0.1);
  transition: all 0.3s ease;
  border: 1px solid rgba(64, 158, 255, 0.2);
}

.blue-input :deep(.el-input__wrapper:hover) {
  box-shadow: 0 4px 16px rgba(64, 158, 255, 0.3);
  border-color: #409EFF;
}

.blue-input :deep(.el-input__wrapper.is-focus) {
  box-shadow: 0 0 0 3px rgba(64, 158, 255, 0.2);
}

.blue-number :deep(.el-input-number__decrease),
.blue-number :deep(.el-input-number__increase) {
  background: linear-gradient(135deg, #409EFF 0%, #66b1ff 100%);
  color: white;
  border: none;
}

.blue-number :deep(.el-input-number__decrease:hover),
.blue-number :deep(.el-input-number__increase:hover) {
  background: linear-gradient(135deg, #66b1ff 0%, #85ce61 100%);
}

.save-button {
  width: 100%;
  height: 45px;
  font-size: 16px;
  font-weight: 600;
  background: linear-gradient(135deg, #409EFF 0%, #66b1ff 100%);
  border: none;
  border-radius: 12px;
  transition: all 0.3s ease;
  box-shadow: 0 4px 16px rgba(64, 158, 255, 0.4);
  display: flex;
  align-items: center;
  justify-content: center;
}

.save-button:hover {
  transform: translateY(-3px);
  box-shadow: 0 6px 20px rgba(64, 158, 255, 0.6);
  background: linear-gradient(135deg, #66b1ff 0%, #85ce61 100%);
}

.save-button:active {
  transform: translateY(-1px);
}

.stats-row {
  display: flex;
  justify-content: space-around;
  padding: 20px;
  margin-top: 10px;
  background: linear-gradient(135deg, #f0f9ff 0%, #e0f2fe 100%);
  border-radius: 12px;
  margin: 10px 20px 20px;
  border: 1px solid rgba(64, 158, 255, 0.2);
}

.stat-item {
  text-align: center;
  flex: 1;
  padding: 10px;
  transition: all 0.3s ease;
}

.stat-item:hover {
  transform: scale(1.05);
}

.stat-icon {
  margin-bottom: 8px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 50px;
  height: 50px;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.8);
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
}

.stat-label {
  font-size: 13px;
  color: #909399;
  margin-bottom: 6px;
}

.stat-value {
  font-size: 20px;
  font-weight: 700;
  color: #409EFF;
  transition: all 0.3s ease;
}

.stat-item:hover .stat-value {
  transform: scale(1.2);
}

:deep(.el-form-item__label) {
  font-weight: 600;
  color: #606266;
  font-size: 14px;
}

:deep(.el-select) {
  width: 100%;
}

.fade-in {
  animation: fadeIn 0.6s ease-out;
}

@keyframes fadeIn {
  0% {
    opacity: 0;
  }
  100% {
    opacity: 1;
  }
}

@keyframes fadeInDown {
  0% {
    opacity: 0;
    transform: translateY(-20px);
  }
  100% {
    opacity: 1;
    transform: translateY(0);
  }
}

@keyframes fadeInUp {
  0% {
    opacity: 0;
    transform: translateY(20px);
  }
  100% {
    opacity: 1;
    transform: translateY(0);
  }
}

@keyframes bounceIn {
  0% {
    opacity: 0;
    transform: scale(0.5);
  }
  100% {
    opacity: 1;
    transform: scale(1);
  }
}

@media (max-width: 768px) {
  .user-profile {
    padding: 10px;
  }

  .profile-card {
    padding: 15px;
  }

  .stats-row {
    flex-direction: column;
    gap: 15px;
  }
}

.loading-container {
  padding: 40px 20px;
}
</style>
