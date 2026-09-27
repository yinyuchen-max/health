<template>
  <div class="sport-record-page">
    <el-row :gutter="16">
      <el-col :xs="24" :sm="12" :lg="6" v-for="item in summaryCards" :key="item.label">
        <div class="summary-card">
          <div class="summary-label">{{ item.label }}</div>
          <div class="summary-value">{{ item.value }}</div>
          <div class="summary-note">{{ item.note }}</div>
        </div>
      </el-col>
    </el-row>

    <div class="main-card">
      <div class="toolbar">
        <div class="toolbar-left">
          <div class="section-icon">
            <el-icon :size="18"><Timer /></el-icon>
          </div>
          <h3 class="section-title">运动记录</h3>
        </div>
        <div class="toolbar-right">
          <el-date-picker v-model="dateRange" type="daterange" range-separator="~" start-placeholder="开始日期" end-placeholder="结束日期" value-format="YYYY-MM-DD" class="date-range" />
          <el-input v-model="searchKeyword" placeholder="搜索备注或类型" clearable class="search-input" :prefix-icon="Search" />
          <el-button type="primary" @click="openCreateDialog"><el-icon><Plus /></el-icon>新增记录</el-button>
        </div>
      </div>

      <el-table :data="pagedRecords" :loading="loading" class="data-table">
        <el-table-column prop="recordDate" label="日期" width="120" />
        <el-table-column prop="sportType" label="运动类型" width="120" />
        <el-table-column label="强度" width="110">
          <template #default="{ row }">
            <el-tag :type="intensityTagType(row.intensity)" size="small" class="status-tag">{{ intensityText(row.intensity) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="duration" label="时长" width="100">
          <template #default="{ row }">{{ row.duration }} 分钟</template>
        </el-table-column>
        <el-table-column prop="calories" label="热量" width="110">
          <template #default="{ row }">{{ row.calories ?? 0 }} kcal</template>
        </el-table-column>
        <el-table-column prop="notes" label="备注" min-width="220" show-overflow-tooltip />
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
          <el-option :value="10" label="10条/页" /><el-option :value="20" label="20条/页" /><el-option :value="50" label="50条/页" />
        </el-select>
        <el-pagination v-model:current-page="currentPage" :total="filteredRecords.length" :page-size="pageSize" layout="prev, pager, next" small />
      </div>
    </div>

    <el-dialog v-model="dialogVisible" :title="editingRecordId ? '编辑运动记录' : '新增运动记录'" width="600px" @closed="resetForm">
      <el-form ref="formRef" :model="formState" :rules="rules" label-width="90px">
        <el-row :gutter="16">
          <el-col :xs="24" :sm="12">
            <el-form-item label="日期" prop="recordDate">
              <el-date-picker v-model="formState.recordDate" type="date" value-format="YYYY-MM-DD" placeholder="选择日期" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :sm="12">
            <el-form-item label="运动类型" prop="sportType">
              <el-select v-model="formState.sportType" placeholder="选择运动类型" style="width: 100%">
                <el-option v-for="item in sportOptions" :key="item" :label="item" :value="item" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :xs="24" :sm="12">
            <el-form-item label="时长" prop="duration">
              <el-input-number v-model="formState.duration" :min="1" :max="1440" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :sm="12">
            <el-form-item label="强度" prop="intensity">
              <el-select v-model="formState.intensity" placeholder="选择强度" style="width: 100%">
                <el-option label="低强度" value="low" /><el-option label="中等强度" value="medium" /><el-option label="高强度" value="high" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="热量"><el-input-number v-model="formState.calories" :min="0" :max="5000" style="width: 100%" /></el-form-item>
        <el-form-item label="备注"><el-input v-model="formState.notes" type="textarea" :rows="3" maxlength="500" show-word-limit /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitForm">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { Search, Plus, Timer } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '../utils/request'
import { useUserStore } from '../store/user'
import { useAnalyticsStore } from '../store/analytics'
import { notifyHistoryChanged } from '../utils/historySync'

const userStore = useUserStore()
const analyticsStore = useAnalyticsStore()

const loading = ref(false), saving = ref(false), dialogVisible = ref(false)
const searchKeyword = ref(''), currentPage = ref(1), pageSize = ref(10)
const editingRecordId = ref(null), dateRange = ref([]), records = ref([]), formRef = ref(null)
const sportOptions = ['跑步', '游泳', '骑行', '健身', '瑜伽', '步行', '球类', '其他']

const emptyForm = () => ({ recordDate: '', sportType: '', duration: null, calories: null, intensity: '', notes: '' })
const formState = reactive(emptyForm())
const rules = {
  recordDate: [{ required: true, message: '请选择日期', trigger: 'change' }],
  sportType: [{ required: true, message: '请选择运动类型', trigger: 'change' }],
  duration: [{ required: true, message: '请输入时长', trigger: 'blur' }],
  intensity: [{ required: true, message: '请选择强度', trigger: 'change' }]
}

const filteredRecords = computed(() => {
  const keyword = searchKeyword.value.trim().toLowerCase()
  return records.value.filter(item => {
    const inKeyword = !keyword || (item.notes || '').toLowerCase().includes(keyword) || (item.sportType || '').toLowerCase().includes(keyword)
    const inDateRange = !dateRange.value?.length || (item.recordDate >= dateRange.value[0] && item.recordDate <= dateRange.value[1])
    return inKeyword && inDateRange
  })
})

const pagedRecords = computed(() => {
  const start = (currentPage.value - 1) * pageSize.value
  return filteredRecords.value.slice(start, start + pageSize.value)
})

const summaryCards = computed(() => {
  const threshold = new Date(); threshold.setDate(threshold.getDate() - 6)
  const weekly = records.value.filter(item => new Date(item.recordDate) >= threshold)
  const duration = weekly.reduce((sum, item) => sum + Number(item.duration || 0), 0)
  const calories = weekly.reduce((sum, item) => sum + Number(item.calories || 0), 0)
  return [
    { label: '近 7 天运动', value: duration, note: '分钟' },
    { label: '近 7 天热量', value: calories, note: 'kcal' },
    { label: '近 7 天次数', value: weekly.length, note: '条记录' },
    { label: '平均强度', value: weekly.length ? '中等' : '--', note: weekly.length ? '基于近期数据' : '暂无数据' }
  ]
})

const intensityText = (i) => ({ low: '低强度', medium: '中等强度', high: '高强度' }[i] || '--')
const intensityTagType = (i) => ({ low: 'info', medium: 'warning', high: 'danger' }[i] || '')

const estimateCalories = () => {
  if (formState.calories != null || !formState.duration || !formState.sportType) return
  const factorMap = { 跑步: 10, 游泳: 12, 骑行: 8, 健身: 7, 瑜伽: 4, 步行: 4, 球类: 9, 其他: 5 }
  formState.calories = Math.round((factorMap[formState.sportType] || 5) * Number(formState.duration))
}

const loadRecords = async () => {
  loading.value = true
  try {
    const userId = userStore.userInfo?.id || 1
    const response = await request.get(`/sport/records/${userId}`, { params: { pageNum: 1, pageSize: 1000 } })
    records.value = response?.data?.records || []
    analyticsStore.setSportRecords(records.value, userId)
  } catch (error) { console.error('加载运动记录失败', error); ElMessage.error('加载运动记录失败'); records.value = [] }
  finally { loading.value = false }
}

const resetForm = () => { Object.assign(formState, emptyForm()); editingRecordId.value = null; formRef.value?.clearValidate() }
const openCreateDialog = () => { resetForm(); dialogVisible.value = true }
const openEditDialog = (row) => {
  resetForm(); editingRecordId.value = row.id
  Object.assign(formState, { recordDate: row.recordDate, sportType: row.sportType, duration: row.duration, calories: row.calories != null ? Number(row.calories) : null, intensity: row.intensity, notes: row.notes || '' })
  dialogVisible.value = true
}

const submitForm = async () => {
  if (!formRef.value) return
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  estimateCalories(); saving.value = true
  try {
    const userId = userStore.userInfo?.id || 1
    const payload = { userId, ...formState }
    if (editingRecordId.value) { await request.put(`/sport/record/${editingRecordId.value}`, payload); ElMessage.success('运动记录已更新') }
    else { await request.post('/sport/record', payload); ElMessage.success('运动记录已新增') }
    dialogVisible.value = false; await loadRecords(); notifyHistoryChanged()
  } catch (error) { console.error('保存运动记录失败', error); ElMessage.error('保存运动记录失败') }
  finally { saving.value = false }
}

const removeRecord = async (id) => {
  try {
    await ElMessageBox.confirm('确定删除这条运动记录吗？', '提示', { type: 'warning' })
    await request.delete(`/sport/record/${id}`); ElMessage.success('运动记录已删除'); await loadRecords(); notifyHistoryChanged()
  } catch (error) { if (error !== 'cancel') { console.error('删除运动记录失败', error); ElMessage.error('删除运动记录失败') } }
}

watch(() => [formState.duration, formState.sportType], () => { if (!editingRecordId.value) estimateCalories() })
onMounted(async () => { await loadRecords() })
</script>

<style scoped>
.sport-record-page { display: flex; flex-direction: column; gap: 16px; }
.summary-card { background: #fff; border-radius: 16px; padding: 20px; border: 1px solid #F0F4F8; min-height: 110px; }
.summary-label { font-size: 14px; color: #64748B; font-weight: 500; }
.summary-value { margin: 10px 0 6px; font-size: 28px; font-weight: 700; color: #1E293B; }
.summary-note { font-size: 13px; color: #94A3B8; }
.main-card { background: #fff; border-radius: 16px; padding: 20px 24px; border: 1px solid #F0F4F8; }
.toolbar { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; flex-wrap: wrap; gap: 12px; }
.toolbar-left { display: flex; align-items: center; gap: 12px; }
.section-icon { display: flex; align-items: center; justify-content: center; width: 36px; height: 36px; background: #D1FAE5; border-radius: 10px; color: #10B981; }
.section-title { margin: 0; font-size: 18px; font-weight: 700; color: #1E293B; }
.toolbar-right { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; }
.search-input { width: 220px; }
.date-range { width: 240px; }
.data-table { border-radius: 12px; }
.data-table :deep(.el-table__header th) { background: #F8FAFC; color: #64748B; font-weight: 600; font-size: 13px; }
.status-tag { font-size: 11px; border-radius: 4px; }
.pagination-area { display: flex; align-items: center; justify-content: flex-end; margin-top: 16px; gap: 8px; }
.total-text { font-size: 13px; color: #94A3B8; }
@media (max-width: 768px) {
  .summary-value { font-size: 22px; }
  .toolbar { flex-direction: column; align-items: stretch; }
  .toolbar-left, .toolbar-right { width: 100%; }
  .search-input, .date-range { width: 100% !important; }
}
</style>
