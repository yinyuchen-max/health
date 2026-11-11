<template>
  <div class="reminder-config">
    <div class="main-card">
      <div class="card-header-area">
        <div class="section-icon">
          <el-icon :size="18"><Bell /></el-icon>
        </div>
        <h3 class="section-title">提醒设置</h3>
      </div>

      <el-form :model="form" label-width="100px" :rules="rules" ref="formRef" class="reminder-form">
        <el-row :gutter="24">
          <el-col :xs="24" :sm="12">
            <el-form-item label="提醒类型" prop="type">
              <el-select v-model="form.type" placeholder="选择提醒类型" style="width: 100%">
                <el-option label="血压测量" value="bloodPressure" />
                <el-option label="血糖检测" value="bloodSugar" />
                <el-option label="体重记录" value="weight" />
                <el-option label="运动提醒" value="exercise" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :xs="24" :sm="12">
            <el-form-item label="提醒时间" prop="time">
              <el-time-picker v-model="form.time" placeholder="选择时间" format="HH:mm" value-format="HH:mm" clearable style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="24">
          <el-col :xs="24" :sm="12">
            <el-form-item label="重复频率" prop="frequency">
              <el-select v-model="form.frequency" placeholder="选择频率" style="width: 100%">
                <el-option label="每天" value="daily" />
                <el-option label="每周一" value="weekly" />
                <el-option label="隔天" value="custom" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :xs="24" :sm="12">
            <el-form-item label="智能模式">
              <el-switch v-model="form.smartMode" active-text="开启" inactive-text="关闭" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item>
          <div class="action-group">
            <el-button type="primary" @click="saveConfig" :loading="isLoading">
              {{ editingId == null ? '添加提醒' : '保存修改' }}
            </el-button>
            <el-button @click="testNotification" :disabled="!form.type">测试提醒</el-button>
            <el-button @click="resetForm()">{{ editingId == null ? '重置' : '取消编辑' }}</el-button>
          </div>
        </el-form-item>
        <el-alert v-if="hasError" title="错误" :description="reminderStore.error" type="error" show-icon closable @close="reminderStore.clearError()" />
      </el-form>
    </div>

    <section class="main-card reminders-card" aria-label="已设置的提醒">
      <div class="reminders-header">
        <div>
          <h3 class="section-title">已设置的提醒 <span class="reminder-total">{{ reminderCount }} 条</span></h3>
          <p class="list-description">保存后的提醒会显示在这里，可编辑或删除。</p>
        </div>
        <el-button :loading="isLoading" @click="loadSettings">刷新列表</el-button>
      </div>
      <el-table :data="reminderStore.preferences" v-loading="isLoading" row-key="id" empty-text="暂无提醒，请先在上方添加提醒">
        <el-table-column label="提醒类型" min-width="140">
          <template #default="{ row }">{{ row.typeName || getTypeName(row.type) }}</template>
        </el-table-column>
        <el-table-column prop="time" label="提醒时间" min-width="110" />
        <el-table-column label="重复频率" min-width="150">
          <template #default="{ row }">{{ getFrequencyName(row.frequency) }}</template>
        </el-table-column>
        <el-table-column label="状态" min-width="100">
          <template #default="{ row }"><el-tag :type="row.enabled ? 'success' : 'info'">{{ row.enabled ? '已启用' : '已停用' }}</el-tag></template>
        </el-table-column>
        <el-table-column label="智能模式" min-width="100">
          <template #default="{ row }">{{ row.smartMode ? '开启' : '关闭' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="140" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link :disabled="isLoading" @click="editReminder(row)">编辑</el-button>
            <el-button type="danger" link :disabled="isLoading" @click="deleteReminder(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </section>

    <el-row :gutter="16" style="margin-top: 16px">
      <el-col :xs="24" :sm="12">
        <div class="stat-card">
          <div class="stat-header">
            <el-icon :size="16" color="#10B981"><TrendCharts /></el-icon>
            <span>提醒统计</span>
          </div>
          <div class="stat-content">
            <div class="stat-item"><span class="label">已设置提醒:</span><span class="value">{{ activeReminderCount }}/{{ reminderCount }}</span></div>
            <div class="stat-item"><span class="label">今日提醒:</span><span class="value">{{ todayReminders }}</span></div>
            <div class="stat-item"><span class="label">完成率:</span><span class="value">{{ completionRate }}%</span></div>
          </div>
        </div>
      </el-col>
      <el-col :xs="24" :sm="12">
        <div class="stat-card">
          <div class="stat-header">
            <el-icon :size="16" color="#F59E0B"><Lightning /></el-icon>
            <span>健康建议</span>
          </div>
          <ul class="advice-list">
            <li v-for="(advice, index) in healthAdvices" :key="index" class="advice-item">
              <el-icon class="advice-icon" :color="advice.color"><Check /></el-icon>
              <span>{{ advice.text }}</span>
            </li>
          </ul>
        </div>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, watch, nextTick } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useReminderStore } from '../store/reminder'
import { useUserStore } from '../store/user'
import { Bell, TrendCharts, Lightning, Check } from '@element-plus/icons-vue'

const formRef = ref()
const reminderStore = useReminderStore()
const userStore = useUserStore()
const editingId = ref(null)

const form = reactive({ type: '', time: null, frequency: '', smartMode: false, enabled: true })
const draftKey = () => `reminderDraft:${userStore.userInfo?.id || ''}`

watch(form, (newForm) => {
  if (newForm.type && newForm.time && newForm.frequency) {
    if (editingId.value == null) localStorage.setItem(draftKey(), JSON.stringify(newForm))
  }
}, { deep: true })

onMounted(async () => {
  const draft = localStorage.getItem(draftKey())
  if (draft) { try { Object.assign(form, JSON.parse(draft)) } catch {} }
  if ('Notification' in window && Notification.permission === 'default') Notification.requestPermission()
  await reminderStore.fetchPreferences()
})

const rules = {
  type: [{ required: true, message: '请选择提醒类型', trigger: 'change' }],
  time: [{ required: true, message: '请选择提醒时间', trigger: 'change' }],
  frequency: [{ required: true, message: '请选择重复频率', trigger: 'change' }]
}

const reminderCount = computed(() => reminderStore.preferences.length)
const activeReminderCount = computed(() => reminderStore.activeReminders.length)
const todayReminders = computed(() => {
  const today = new Date().toDateString()
  return reminderStore.notifications.filter(n => n.timestamp && new Date(n.timestamp).toDateString() === today).length
})
const completionRate = computed(() => reminderStore.completionRate)
const isLoading = computed(() => reminderStore.loading)
const hasError = computed(() => reminderStore.error !== null)

const healthAdvices = computed(() => {
  const advices = []
  switch (form.type) {
    case 'bloodPressure': advices.push({ text: '建议早晚各测量一次血压', color: '#EF4444' }, { text: '测量前静坐5分钟', color: '#F59E0B' }); break
    case 'bloodSugar': advices.push({ text: '空腹血糖正常值: 3.9-6.1 mmol/L', color: '#10B981' }, { text: '餐后2小时血糖应低于7.8 mmol/L', color: '#10B981' }); break
    case 'weight': advices.push({ text: '建议每周测量1-2次体重', color: '#3B82F6' }, { text: '测量时间建议在早晨起床后', color: '#3B82F6' }); break
    case 'exercise': advices.push({ text: '成年人每周至少150分钟中等强度运动', color: '#10B981' }, { text: '运动前后注意热身和拉伸', color: '#10B981' }); break
    default: advices.push({ text: '保持规律的健康监测习惯', color: '#94A3B8' }, { text: '及时记录异常指标并咨询医生', color: '#94A3B8' })
  }
  return advices
})

const saveConfig = async () => {
  if (!formRef.value || isLoading.value) return
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid || isLoading.value) return
  const userId = userStore.userInfo?.id
  if (!userId) { ElMessage.error('用户信息未就绪，请稍后重试'); return }
  const preferenceData = { ...form, ...(editingId.value == null ? {} : { id: editingId.value }) }
  const success = await reminderStore.savePreference(preferenceData)
  if (success) {
    await resetForm(false)
    if (reminderStore.error) ElMessage.warning(reminderStore.error)
    else ElMessage.success('提醒设置已保存')
  } else ElMessage.error(reminderStore.error || '保存失败')
}

const editReminder = (reminder) => {
  editingId.value = reminder.id
  Object.assign(form, { type: reminder.type, time: reminder.time, frequency: reminder.frequency,
    smartMode: !!reminder.smartMode, enabled: reminder.enabled !== false })
  nextTick(() => formRef.value?.clearValidate())
}

const deleteReminder = async (reminder) => {
  if (isLoading.value) return
  try {
    await ElMessageBox.confirm(`确定删除「${getTypeName(reminder.type)} ${reminder.time}」吗？删除后将停止这条提醒。`, '删除提醒',
      { confirmButtonText: '删除', cancelButtonText: '取消', type: 'warning' })
  } catch { return }
  if (await reminderStore.deletePreference(reminder.id)) {
    if (editingId.value === reminder.id) await resetForm(false)
    ElMessage.success('提醒已删除')
  } else ElMessage.error(reminderStore.error || '删除失败')
}

const testNotification = () => {
  if (!form.type) { ElMessage.warning('请先选择提醒类型'); return }
  ElMessageBox.confirm('确定要发送测试提醒吗？', '测试提醒', { confirmButtonText: '确定', cancelButtonText: '取消', type: 'info' })
    .then(() => {
      ElMessage.success(`测试提醒已发送！`)
      if ('Notification' in window && Notification.permission === 'granted') {
        new Notification('健康提醒测试', { body: `这是关于${getTypeName(form.type)}的测试提醒` })
      }
    }).catch(() => {})
}

const loadSettings = async () => {
  if (await reminderStore.fetchPreferences()) ElMessage.success('提醒列表已刷新')
  else ElMessage.error(reminderStore.error || '加载设置失败')
}

const resetForm = async (notify = true) => {
  editingId.value = null
  formRef.value?.resetFields()
  Object.assign(form, { type: '', time: null, frequency: '', smartMode: false, enabled: true })
  localStorage.removeItem(draftKey())
  await nextTick()
  formRef.value?.clearValidate()
  if (notify) ElMessage.info('表单已重置')
}

const getTypeName = (type) => ({ bloodPressure: '血压测量', bloodSugar: '血糖检测', weight: '体重记录', exercise: '运动提醒' }[type] || type)
const getFrequencyName = (frequency) => ({ daily: '每天', weekly: '每周一', custom: '自定义（隔天）' }[frequency] || frequency)
</script>

<style scoped>
.reminder-config { display: flex; flex-direction: column; }
.main-card { background: #fff; border-radius: 16px; padding: 24px; border: 1px solid #F0F4F8; }
.card-header-area { display: flex; align-items: center; gap: 10px; margin-bottom: 20px; }
.section-icon { display: flex; align-items: center; justify-content: center; width: 36px; height: 36px; background: #FEF3C7; border-radius: 10px; color: #F59E0B; }
.section-title { margin: 0; font-size: 18px; font-weight: 700; color: #1E293B; }
.reminder-form { max-width: 700px; }
.reminders-card { margin-top: 16px; }
.reminders-header { display: flex; align-items: center; justify-content: space-between; gap: 16px; margin-bottom: 16px; flex-wrap: wrap; }
.reminder-total { margin-left: 8px; color: #64748B; font-size: 14px; font-weight: 400; }
.list-description { margin: 8px 0 0; color: #64748B; font-size: 13px; }
.action-group { display: flex; gap: 8px; flex-wrap: wrap; }
.stat-card { background: #fff; border-radius: 16px; padding: 20px; border: 1px solid #F0F4F8; }
.stat-header { display: flex; align-items: center; gap: 8px; font-size: 15px; font-weight: 600; color: #1E293B; margin-bottom: 12px; }
.stat-content { }
.stat-item { display: flex; justify-content: space-between; align-items: center; padding: 10px 0; border-bottom: 1px solid #F1F5F9; }
.stat-item:last-child { border-bottom: none; }
.label { color: #94A3B8; font-size: 14px; }
.value { color: #1E293B; font-weight: 600; font-size: 15px; }
.advice-list { list-style: none; padding: 0; margin: 0; }
.advice-item { display: flex; align-items: center; padding: 10px 0; border-bottom: 1px solid #F1F5F9; }
.advice-item:last-child { border-bottom: none; }
.advice-icon { margin-right: 10px; flex-shrink: 0; }
@media (max-width: 768px) {
  .action-group { justify-content: center; }
}
</style>
