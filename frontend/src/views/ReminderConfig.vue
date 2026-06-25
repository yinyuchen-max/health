<template>
  <div class="reminder-config">
    <el-card>
      <template #header>
        <div class="card-header">
          <el-icon :size="20" color="#409EFF"><Bell /></el-icon>
          <span>提醒设置</span>
        </div>
      </template>

      <el-form :model="form" label-width="120px" :rules="rules" ref="formRef">
        <el-form-item label="提醒类型" prop="type">
          <el-select v-model="form.type" placeholder="选择提醒类型">
            <el-option label="血压测量" value="bloodPressure" />
            <el-option label="血糖检测" value="bloodSugar" />
            <el-option label="体重记录" value="weight" />
            <el-option label="运动提醒" value="exercise" />
          </el-select>
        </el-form-item>

        <el-form-item label="提醒时间" prop="time">
          <el-time-picker
            v-model="form.time"
            placeholder="选择时间"
            format="HH:mm"
            value-format="HH:mm"
            clearable
          />
        </el-form-item>

        <el-form-item label="重复频率" prop="frequency">
          <el-select v-model="form.frequency" placeholder="选择频率">
            <el-option label="每天" value="daily" />
            <el-option label="每周" value="weekly" />
            <el-option label="自定义" value="custom" />
          </el-select>
        </el-form-item>

        <el-form-item label="智能模式">
          <el-switch v-model="form.smartMode" active-text="开启" inactive-text="关闭" />
          <el-tooltip content="系统会根据您的使用习惯自动优化提醒时间" placement="top">
            <el-icon><QuestionFilled /></el-icon>
          </el-tooltip>
        </el-form-item>

        <el-form-item label="操作">
          <div class="action-group">
            <el-button type="primary" @click="saveConfig" :loading="isLoading">
              <span v-if="reminderCount === 0">创建提醒</span>
              <span v-else>更新设置</span>
            </el-button>
            <el-button @click="loadSettings" :loading="isLoading">加载设置</el-button>
            <el-button @click="testNotification" :disabled="!form.type">测试提醒</el-button>
            <el-button @click="resetForm">重置</el-button>
            <el-button @click="importSettings" icon="Upload">导入</el-button>
            <el-button @click="exportSettings" icon="Download">导出</el-button>
            <el-button @click="clearAllSettings" type="danger" plain v-if="reminderCount > 0">
              清除全部
            </el-button>
          </div>
        </el-form-item>

        <!-- 错误显示 -->
        <el-alert
          v-if="hasError"
          title="错误"
          :description="reminderStore.error"
          type="error"
          show-icon
          closable
          @close="reminderStore.clearError()"
        />
      </el-form>
    </el-card>

    <!-- 提醒统计卡片 -->
    <el-row :gutter="20" class="stats-row" style="margin-top: 20px;">
      <el-col :span="12">
        <el-card class="stat-card">
          <template #header>
            <div class="card-header">
              <el-icon :size="16" color="#67C23A"><TrendCharts /></el-icon>
              <span>提醒统计</span>
            </div>
          </template>
          <div class="stat-content">
            <div class="stat-item">
              <span class="label">已设置提醒:</span>
              <span class="value">{{ activeReminderCount }}/{{ reminderCount }}</span>
            </div>
            <div class="stat-item">
              <span class="label">今日提醒:</span>
              <span class="value">{{ todayReminders }}</span>
            </div>
            <div class="stat-item">
              <span class="label">完成率:</span>
              <span class="value">{{ completionRate }}%</span>
            </div>
            <div class="stat-item" v-if="reminderCount > 0">
              <span class="label">平均评分:</span>
              <span class="value">
                {{ reminderStore.preferences.length > 0 ?
                  (reminderStore.preferences.reduce((sum, p) => sum + (p.effectivenessScore || 0), 0) / reminderStore.preferences.length).toFixed(1) : 'N/A' }}
              </span>
            </div>
          </div>
        </el-card>
      </el-col>

      <el-col :span="12">
        <el-card class="stat-card">
          <template #header>
            <div class="card-header">
              <el-icon :size="16" color="#E6A23C"><Lightning /></el-icon>
              <span>健康建议</span>
            </div>
          </template>
          <ul class="advice-list">
            <li v-for="(advice, index) in healthAdvices" :key="index" class="advice-item">
              <el-icon class="advice-icon" :color="advice.color"><Check /></el-icon>
              <span>{{ advice.text }}</span>
            </li>
          </ul>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useReminderStore } from '../store/reminder'
import {
  Bell,
  QuestionFilled,
  TrendCharts,
  Lightning,
  Check
} from '@element-plus/icons-vue'

const formRef = ref()
const reminderStore = useReminderStore()

const form = reactive({
  type: '',
  time: null,
  frequency: '',
  smartMode: false
})

// 监听表单变化，自动保存草稿
watch(form, (newForm) => {
  if (newForm.type && newForm.time && newForm.frequency) {
    localStorage.setItem('reminderDraft', JSON.stringify(newForm))
  }
}, { deep: true })

// 加载本地草稿
onMounted(() => {
  const draft = localStorage.getItem('reminderDraft')
  if (draft) {
    try {
      const parsedDraft = JSON.parse(draft)
      Object.assign(form, parsedDraft)
    } catch (error) {
      console.warn('加载草稿失败:', error)
    }
  }

  // 从store获取现有偏好设置
  // 暂时注释掉自动加载，避免错误
  // if (!reminderStore.preferences.length) {
  //   reminderStore.fetchPreferences()
  // }

  // 请求通知权限
  if ('Notification' in window && Notification.permission === 'default') {
    Notification.requestPermission()
  }

  console.log('提醒配置页面已加载')
})

const rules = {
  type: [
    { required: true, message: '请选择提醒类型', trigger: 'change' },
    { pattern: /^(bloodPressure|bloodSugar|weight|exercise)$/, message: '无效的提醒类型', trigger: 'change' }
  ],
  time: [
    { required: true, message: '请选择提醒时间', trigger: 'change' },
    { validator: validateTimeFormat, trigger: 'change' }
  ],
  frequency: [
    { required: true, message: '请选择重复频率', trigger: 'change' },
    { pattern: /^(daily|weekly|custom)$/, message: '无效的频率设置', trigger: 'change' }
  ]
}

// 时间格式验证
function validateTimeFormat(rule, value, callback) {
  if (!value) return callback(new Error('请选择时间'))
  const timeRegex = /^([01]?[0-9]|2[0-3]):[0-5][0-9]$/
  if (!timeRegex.test(value)) {
    callback(new Error('时间格式应为HH:mm'))
  } else {
    callback()
  }
}

// 计算属性
const reminderCount = computed(() => {
  return reminderStore.preferences.length
})

const activeReminderCount = computed(() => {
  return reminderStore.activeReminders.length
})

const todayReminders = computed(() => {
  // 从通知中获取今日提醒
  const today = new Date().toDateString()
  return reminderStore.notifications.filter(n =>
    n.timestamp && new Date(n.timestamp).toDateString() === today
  ).length
})

const completionRate = computed(() => {
  return reminderStore.completionRate
})

const isLoading = computed(() => {
  return reminderStore.loading
})

const hasError = computed(() => {
  return reminderStore.error !== null
})

const healthAdvices = computed(() => {
  const type = form.type
  const advices = []

  switch (type) {
    case 'bloodPressure':
      advices.push(
        { text: '建议早晚各测量一次血压', color: '#f56c6c' },
        { text: '测量前静坐5分钟，避免剧烈运动后立即测量', color: '#E6A23C' }
      )
      break
    case 'bloodSugar':
      advices.push(
        { text: '空腹血糖正常值: 3.9-6.1 mmol/L', color: '#67C23A' },
        { text: '餐后2小时血糖: 应低于7.8 mmol/L', color: '#67C23A' }
      )
      break
    case 'weight':
      advices.push(
        { text: '建议每周测量1-2次体重', color: '#409EFF' },
        { text: '测量时间建议在早晨起床后', color: '#409EFF' }
      )
      break
    case 'exercise':
      advices.push(
        { text: '成年人每周至少150分钟中等强度运动', color: '#67C23A' },
        { text: '运动前后注意热身和拉伸', color: '#67C23A' }
      )
      break
    default:
      advices.push(
        { text: '保持规律的健康监测习惯', color: '#909399' },
        { text: '及时记录异常指标并咨询医生', color: '#909399' }
      )
  }

  return advices
})

// 方法
const saveConfig = async () => {
  if (!formRef.value) return

  await formRef.value.validate(async (valid) => {
    if (valid) {
      try {
        const preferenceData = {
          ...form,
          id: Date.now(), // 临时ID，实际应该从后端获取
          userId: reminderStore.userInfo?.id || 1,
          enabled: true,
          createdAt: new Date().toISOString(),
          updatedAt: new Date().toISOString()
        }

        const success = await reminderStore.savePreference(preferenceData)

        if (success) {
          ElMessage.success('提醒设置已保存')

          // 清除草稿
          localStorage.removeItem('reminderDraft')

          ElMessage.success(`${getTypeName(form.type)}提醒将在${form.time}准时发送`)
        } else {
          ElMessage.error(reminderStore.error || '保存失败，请重试')
        }
      } catch (error) {
        console.error('保存失败:', error)
        ElMessage.error('保存失败，请检查网络连接')
      }
    }
  })
}

const testNotification = () => {
  if (!form.type) {
    ElMessage.warning('请先选择提醒类型')
    return
  }

  ElMessageBox.confirm(
    '确定要发送测试提醒吗？',
    '测试提醒',
    {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'info'
    }
  ).then(() => {
    ElMessage.success(`测试提醒已发送！类型为"${getTypeName(form.type)}"`)

    // 模拟浏览器通知（如果支持）
    if ('Notification' in window && Notification.permission === 'granted') {
      new Notification('健康提醒测试', {
        body: `这是关于${getTypeName(form.type)}的测试提醒`,
        icon: '/favicon.ico'
      })
    }
  }).catch(() => {
    // 用户取消了
  })
}

const loadSettings = async () => {
  try {
    await reminderStore.fetchPreferences()
    ElMessage.success('设置加载成功')
  } catch (error) {
    ElMessage.error('加载设置失败，请检查网络连接')
  }
}

const resetForm = () => {
  if (formRef.value) {
    formRef.value.resetFields()
  }
  localStorage.removeItem('reminderDraft')
  ElMessage.info('表单已重置')
}

// 导入设置
const importSettings = () => {
  const input = document.createElement('input')
  input.type = 'file'
  input.accept = '.json'
  input.onchange = (e) => {
    const file = e.target.files[0]
    if (!file) return

    const reader = new FileReader()
    reader.onload = (event) => {
      try {
        const importedData = JSON.parse(event.target.result)
        if (Array.isArray(importedData)) {
          // 批量导入
          const successCount = importedData.filter(item =>
            reminderStore.savePreference({
              ...item,
              id: Date.now() + Math.random(),
              createdAt: new Date().toISOString(),
              updatedAt: new Date().toISOString()
            })
          ).length

          ElMessage.success(`成功导入 ${successCount} 个提醒设置`)
        } else {
          // 单个导入
          Object.assign(form, importedData)
          ElMessage.success('导入成功')
        }
      } catch (error) {
        ElMessage.error('文件格式错误，导入失败')
      }
    }
    reader.readAsText(file)
  }
  input.click()
}

// 导出设置
const exportSettings = () => {
  const dataStr = reminderStore.exportPreferences()
  const dataUri = 'data:application/json;charset=utf-8,'+ encodeURIComponent(dataStr)

  const exportFileDefaultName = `reminder-settings-${new Date().toISOString().split('T')[0]}.json`

  const linkElement = document.createElement('a')
  linkElement.setAttribute('href', dataUri)
  linkElement.setAttribute('download', exportFileDefaultName)
  linkElement.click()

  ElMessage.success('设置已导出')
}

// 清除所有设置
const clearAllSettings = () => {
  ElMessageBox.confirm(
    '确定要清除所有提醒设置吗？此操作无法撤销。',
    '警告',
    {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    }
  ).then(async () => {
    for (const preference of reminderStore.preferences) {
      await reminderStore.deletePreference(preference.id)
    }
    ElMessage.success('所有设置已清除')
  }).catch(() => {})
}

const getTypeName = (type) => {
  const types = {
    bloodPressure: '血压测量',
    bloodSugar: '血糖检测',
    weight: '体重记录',
    exercise: '运动提醒'
  }
  return types[type] || type
}
</script>

<style scoped>
.reminder-config {
  max-width: 800px;
  margin: 0 auto;
  padding: 20px;
}

.card-header {
  display: flex;
  align-items: center;
  font-size: 16px;
  font-weight: 600;
}

.card-header span {
  margin-left: 8px;
}

.stats-row {
  margin-bottom: 20px;
}

.stat-card {
  background: rgba(255, 255, 255, 0.9);
  backdrop-filter: blur(10px);
  border-radius: 12px;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.1);
  transition: all 0.3s ease;
}

.stat-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.15);
}

.stat-content {
  padding: 15px;
}

.stat-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 8px 0;
  border-bottom: 1px solid #e4e7ed;
}

.stat-item:last-child {
  border-bottom: none;
}

.label {
  color: #909399;
  font-size: 14px;
}

.value {
  color: #303133;
  font-weight: 600;
  font-size: 16px;
}

.advice-list {
  list-style: none;
  padding: 0;
  margin: 0;
}

.advice-item {
  display: flex;
  align-items: center;
  padding: 10px 0;
  border-bottom: 1px solid #e4e7ed;
  transition: all 0.3s ease;
}

.advice-item:hover {
  background: rgba(64, 158, 255, 0.05);
  padding-left: 10px;
  border-radius: 6px;
}

.advice-item:last-child {
  border-bottom: none;
}

.advice-icon {
  margin-right: 10px;
  flex-shrink: 0;
}

.action-group {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

/* 响应式按钮组 */
@media (max-width: 768px) {
  .action-group {
    justify-content: center;
  }

  .el-form-item__content {
    display: flex;
    justify-content: center;
  }
}

/* 响应式设计 */
@media (max-width: 768px) {
  .reminder-config {
    padding: 10px;
  }

  .stats-row {
    margin-top: 15px;
  }

  .el-form-item__label {
    width: 100px !important;
  }

  .el-form-item__content {
    margin-left: 100px !important;
  }
}
</style>