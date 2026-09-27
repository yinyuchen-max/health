<template>
  <div class="health-record-page">
    <!-- 顶部统计卡片 -->
    <el-row :gutter="16">
      <el-col :xs="24" :sm="12" :lg="6" v-for="item in summaryCards" :key="item.label">
        <div class="summary-card">
          <div class="summary-label">{{ item.label }}</div>
          <div class="summary-value">{{ item.value }}</div>
          <div class="summary-note">{{ item.note }}</div>
        </div>
      </el-col>
    </el-row>

    <!-- 主内容区 -->
    <div class="main-card">
      <div class="toolbar">
        <div class="toolbar-left">
          <div class="section-icon">
            <el-icon :size="18"><Document /></el-icon>
          </div>
          <h3 class="section-title">健康记录</h3>
          <div class="tab-bar">
            <el-button
              v-for="tab in tabs"
              :key="tab.key"
              :type="activeTab === tab.key ? 'primary' : ''"
              :plain="activeTab !== tab.key"
              class="tab-btn"
              @click="activeTab = tab.key"
            >
              {{ tab.label }}
            </el-button>
          </div>
        </div>
        <div class="toolbar-right">
          <el-input
            v-model="searchKeyword"
            placeholder="搜索日期、项目或备注..."
            clearable
            class="search-input"
            :prefix-icon="Search"
          />
          <el-date-picker
            v-model="dateRange"
            type="daterange"
            range-separator="~"
            start-placeholder="开始日期"
            end-placeholder="结束日期"
            value-format="YYYY-MM-DD"
            class="date-range"
          />
          <el-button type="primary" @click="openCreateDialog">
            <el-icon><Plus /></el-icon>
            新增记录
          </el-button>
        </div>
      </div>

      <!-- 表格 -->
      <el-table :data="pagedRecords" :loading="loading" class="data-table">
        <el-table-column width="45">
          <template #header><el-checkbox v-model="selectAll" @change="toggleSelectAll" /></template>
          <template #default="{ row }"><el-checkbox v-model="row.selected" /></template>
        </el-table-column>
        <el-table-column label="日期" width="160" sortable>
          <template #default="{ row }">
            <span class="date-cell">{{ row.recordDate }}</span>
            <span class="time-cell">{{ row.recordTime || '08:30' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="血压" width="140" sortable>
          <template #default="{ row }">
            <span class="value-cell">{{ formatBP(row) }}</span>
            <el-tag :type="getBPStatus(row) === '正常' ? 'success' : getBPStatus(row) === '偏高' ? 'danger' : 'warning'" size="small" class="status-tag">
              {{ getBPStatus(row) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="心率" width="130" sortable>
          <template #default="{ row }">
            <span class="value-cell">{{ row.heartRate ?? '--' }} bpm</span>
            <el-tag :type="getHRStatus(row) === '正常' ? 'success' : 'danger'" size="small" class="status-tag">
              {{ getHRStatus(row) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="血糖" width="140" sortable>
          <template #default="{ row }">
            <span class="value-cell">{{ row.bloodSugar ?? '--' }} mmol/L</span>
            <el-tag :type="getBSStatus(row) === '正常' ? 'success' : getBSStatus(row) === '偏高' ? 'danger' : 'warning'" size="small" class="status-tag">
              {{ getBSStatus(row) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="体重" width="120" sortable>
          <template #default="{ row }">
            <span class="value-cell">{{ row.weight ?? '--' }} kg</span>
            <el-tag :type="getWeightStatus(row) === '正常' ? 'success' : 'warning'" size="small" class="status-tag">
              {{ getWeightStatus(row) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="备注" min-width="160" show-overflow-tooltip>
          <template #default="{ row }">
            <span class="notes-cell">{{ row.notes || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="140" fixed="right">
          <template #default="{ row }">
            <el-button size="small" text type="primary" @click="openEditDialog(row)">编辑</el-button>
            <el-button size="small" text type="danger" @click="removeRecord(row.id)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-area">
        <span class="total-text">共 {{ filteredRecords.length }} 条</span>
        <el-select v-model="pageSize" size="small" style="width: 100px; margin: 0 12px">
          <el-option :value="10" label="10条/页" />
          <el-option :value="20" label="20条/页" />
          <el-option :value="50" label="50条/页" />
        </el-select>
        <el-pagination
          v-model:current-page="currentPage"
          :total="filteredRecords.length"
          :page-size="pageSize"
          layout="prev, pager, next"
          small
        />
      </div>
    </div>

    <!-- 新增/编辑对话框 -->
    <el-dialog
      v-model="dialogVisible"
      :title="editingRecordId ? '编辑健康记录' : '新增健康记录'"
      width="640px"
      @closed="resetForm"
    >
      <el-form ref="formRef" :model="formState" :rules="rules" label-width="90px">
        <el-row :gutter="16">
          <el-col :xs="24" :sm="12">
            <el-form-item label="日期" prop="recordDate">
              <el-date-picker v-model="formState.recordDate" type="date" value-format="YYYY-MM-DD" placeholder="选择日期" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :sm="12">
            <el-form-item label="体重" prop="weight">
              <el-input-number v-model="formState.weight" :min="20" :max="300" :step="0.1" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :xs="24" :sm="12">
            <el-form-item label="收缩压" prop="bloodPressureSystolic">
              <el-input-number v-model="formState.bloodPressureSystolic" :min="60" :max="250" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :sm="12">
            <el-form-item label="舒张压" prop="bloodPressureDiastolic">
              <el-input-number v-model="formState.bloodPressureDiastolic" :min="40" :max="150" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :xs="24" :sm="12">
            <el-form-item label="心率" prop="heartRate">
              <el-input-number v-model="formState.heartRate" :min="40" :max="200" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :sm="12">
            <el-form-item label="血糖" prop="bloodSugar">
              <el-input-number v-model="formState.bloodSugar" :min="2" :max="30" :step="0.1" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="备注">
          <el-input v-model="formState.notes" type="textarea" :rows="3" maxlength="500" show-word-limit />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitForm">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { Search, Plus, Document } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '../utils/request'
import { useUserStore } from '../store/user'
import { useAnalyticsStore } from '../store/analytics'
import { notifyHistoryChanged } from '../utils/historySync'

const userStore = useUserStore()
const analyticsStore = useAnalyticsStore()

const loading = ref(false)
const saving = ref(false)
const dialogVisible = ref(false)
const searchKeyword = ref('')
const dateRange = ref([])
const activeTab = ref('all')
const currentPage = ref(1)
const pageSize = ref(10)
const editingRecordId = ref(null)
const records = ref([])
const formRef = ref(null)
const selectAll = ref(false)

const tabs = [
  { key: 'all', label: '全部' },
  { key: 'bp', label: '血压' },
  { key: 'hr', label: '心率' },
  { key: 'bs', label: '血糖' },
  { key: 'weight', label: '体重' },
  { key: 'notes', label: '备注' }
]

const emptyForm = () => ({
  recordDate: '',
  bloodPressureSystolic: null,
  bloodPressureDiastolic: null,
  heartRate: null,
  bloodSugar: null,
  weight: null,
  notes: ''
})

const formState = reactive(emptyForm())

const rules = {
  recordDate: [{ required: true, message: '请选择日期', trigger: 'change' }],
  heartRate: [{ required: true, message: '请输入心率', trigger: 'blur' }],
  weight: [{ required: true, message: '请输入体重', trigger: 'blur' }]
}

const formatBP = (row) => {
  if (!row?.bloodPressureSystolic || !row?.bloodPressureDiastolic) return '--'
  return `${row.bloodPressureSystolic}/${row.bloodPressureDiastolic}`
}

const getBPStatus = (row) => {
  if (!row?.bloodPressureSystolic) return '--'
  if (row.bloodPressureSystolic > 140 || row.bloodPressureDiastolic > 90) return '偏高'
  if (row.bloodPressureSystolic < 90 || row.bloodPressureDiastolic < 60) return '偏低'
  return '正常'
}

const getHRStatus = (row) => {
  if (!row?.heartRate) return '--'
  if (row.heartRate > 100) return '偏高'
  if (row.heartRate < 60) return '偏低'
  return '正常'
}

const getBSStatus = (row) => {
  if (!row?.bloodSugar) return '--'
  if (row.bloodSugar > 7.0) return '偏高'
  if (row.bloodSugar < 3.9) return '偏低'
  return '正常'
}

const getWeightStatus = () => '正常'

const filteredRecords = computed(() => {
  let list = [...records.value]
  if (activeTab.value !== 'all') {
    const tabFieldMap = { bp: 'bloodPressureSystolic', hr: 'heartRate', bs: 'bloodSugar', weight: 'weight', notes: 'notes' }
    const field = tabFieldMap[activeTab.value]
    list = list.filter(r => r[field] != null && r[field] !== '')
  }
  const keyword = searchKeyword.value.trim().toLowerCase()
  if (keyword) {
    list = list.filter(r =>
      (r.recordDate || '').toLowerCase().includes(keyword) ||
      (r.notes || '').toLowerCase().includes(keyword)
    )
  }
  if (dateRange.value?.length === 2) {
    list = list.filter(r => r.recordDate >= dateRange.value[0] && r.recordDate <= dateRange.value[1])
  }
  list.sort((a, b) => new Date(b.recordDate) - new Date(a.recordDate))
  return list
})

const pagedRecords = computed(() => {
  const start = (currentPage.value - 1) * pageSize.value
  return filteredRecords.value.slice(start, start + pageSize.value)
})

const latestRecord = computed(() => filteredRecords.value[0] || null)

const summaryCards = computed(() => {
  const latest = latestRecord.value
  const prev = filteredRecords.value[1]
  const weightDiff = latest?.weight != null && prev?.weight != null
    ? (latest.weight - prev.weight).toFixed(1) : null

  return [
    { label: '最新血压', value: latest ? formatBP(latest) : '--', note: 'mmHg' },
    { label: '最新心率', value: latest?.heartRate != null ? `${latest.heartRate}` : '--', note: 'bpm' },
    { label: '最新血糖', value: latest?.bloodSugar != null ? `${latest.bloodSugar}` : '--', note: 'mmol/L' },
    { label: '体重变化', value: weightDiff == null ? '--' : `${Number(weightDiff) > 0 ? '+' : ''}${weightDiff} kg`, note: '对比上次' }
  ]
})

const toggleSelectAll = (val) => {
  pagedRecords.value.forEach(r => r.selected = val)
}

const loadRecords = async () => {
  loading.value = true
  try {
    const userId = userStore.userInfo?.id || 1
    const response = await request.get(`/health/records/${userId}`, { params: { pageNum: 1, pageSize: 1000 } })
    records.value = (response?.data?.records || []).map(r => ({ ...r, selected: false }))
    analyticsStore.setHealthRecords(records.value, userId)
  } catch (error) {
    console.error('加载健康记录失败', error)
    ElMessage.error('加载健康记录失败')
    records.value = []
  } finally {
    loading.value = false
  }
}

const resetForm = () => {
  Object.assign(formState, emptyForm())
  editingRecordId.value = null
  formRef.value?.clearValidate()
}

const openCreateDialog = () => { resetForm(); dialogVisible.value = true }

const openEditDialog = (row) => {
  resetForm()
  editingRecordId.value = row.id
  Object.assign(formState, {
    recordDate: row.recordDate,
    bloodPressureSystolic: row.bloodPressureSystolic,
    bloodPressureDiastolic: row.bloodPressureDiastolic,
    heartRate: row.heartRate,
    bloodSugar: row.bloodSugar != null ? Number(row.bloodSugar) : null,
    weight: row.weight,
    notes: row.notes || ''
  })
  dialogVisible.value = true
}

const submitForm = async () => {
  if (!formRef.value) return
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  saving.value = true
  try {
    const userId = userStore.userInfo?.id || 1
    const payload = { userId, ...formState }
    if (editingRecordId.value) {
      await request.put(`/health/record/${editingRecordId.value}`, payload)
      ElMessage.success('健康记录已更新')
    } else {
      await request.post('/health/record', payload)
      ElMessage.success('健康记录已新增')
    }
    dialogVisible.value = false
    await loadRecords()
    notifyHistoryChanged()
  } catch (error) {
    console.error('保存健康记录失败', error)
    ElMessage.error('保存健康记录失败')
  } finally {
    saving.value = false
  }
}

const removeRecord = async (id) => {
  try {
    await ElMessageBox.confirm('确定删除这条健康记录吗？', '提示', { type: 'warning' })
    await request.delete(`/health/record/${id}`)
    ElMessage.success('健康记录已删除')
    await loadRecords()
    notifyHistoryChanged()
  } catch (error) {
    if (error !== 'cancel') {
      console.error('删除健康记录失败', error)
      ElMessage.error('删除健康记录失败')
    }
  }
}

onMounted(async () => { await loadRecords() })
</script>

<style scoped>
.health-record-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.summary-card {
  background: #fff;
  border-radius: 16px;
  padding: 20px;
  border: 1px solid #F0F4F8;
  min-height: 110px;
}

.summary-label {
  font-size: 14px;
  color: #64748B;
  font-weight: 500;
}

.summary-value {
  margin: 10px 0 6px;
  font-size: 28px;
  font-weight: 700;
  color: #1E293B;
}

.summary-note {
  font-size: 13px;
  color: #94A3B8;
}

.main-card {
  background: #fff;
  border-radius: 16px;
  padding: 20px 24px;
  border: 1px solid #F0F4F8;
}

.toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
  flex-wrap: wrap;
  gap: 12px;
}

.toolbar-left {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.section-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  background: #EBF5FF;
  border-radius: 10px;
  color: #3B82F6;
}

.section-title {
  margin: 0;
  font-size: 18px;
  font-weight: 700;
  color: #1E293B;
}

.tab-bar {
  display: flex;
  gap: 6px;
  margin-left: 8px;
}

.tab-btn {
  border-radius: 8px;
  font-size: 13px;
  padding: 5px 14px;
}

.toolbar-right {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.search-input {
  width: 220px;
}

.date-range {
  width: 240px;
}

.data-table {
  border-radius: 12px;
}

.data-table :deep(.el-table__header th) {
  background: #F8FAFC;
  color: #64748B;
  font-weight: 600;
  font-size: 13px;
}

.date-cell {
  font-weight: 600;
  color: #1E293B;
  font-size: 13px;
}

.time-cell {
  color: #94A3B8;
  font-size: 12px;
  margin-left: 6px;
}

.value-cell {
  font-weight: 600;
  color: #1E293B;
  font-size: 13px;
  margin-right: 8px;
}

.status-tag {
  font-size: 11px;
  border-radius: 4px;
}

.notes-cell {
  color: #64748B;
  font-size: 13px;
}

.pagination-area {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  margin-top: 16px;
  gap: 8px;
}

.total-text {
  font-size: 13px;
  color: #94A3B8;
}

@media (max-width: 768px) {
  .summary-value { font-size: 22px; }
  .toolbar { flex-direction: column; align-items: stretch; }
  .toolbar-left, .toolbar-right { width: 100%; }
  .search-input, .date-range { width: 100% !important; }
  .tab-bar { overflow-x: auto; flex-wrap: nowrap; padding-bottom: 4px; }
  .tab-btn { white-space: nowrap; flex-shrink: 0; }
}
</style>
