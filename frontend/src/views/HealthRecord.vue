<template>
  <div class="health-record-page">
    <el-row :gutter="16">
      <el-col :xs="24" :sm="12" :lg="6" v-for="item in summaryCards" :key="item.label">
        <el-card class="summary-card">
          <div class="summary-label">{{ item.label }}</div>
          <div class="summary-value">{{ item.value }}</div>
          <div class="summary-note">{{ item.note }}</div>
        </el-card>
      </el-col>
    </el-row>

    <el-card class="main-card">
      <div class="toolbar">
        <div class="toolbar-left">
          <h3>健康记录</h3>
          <el-radio-group v-model="viewMode" size="small">
            <el-radio-button label="table">列表</el-radio-button>
            <el-radio-button label="chart">图表</el-radio-button>
          </el-radio-group>
        </div>
        <div class="toolbar-right">
          <el-input
            v-model="searchKeyword"
            placeholder="搜索备注"
            clearable
            style="width: 220px"
          />
          <el-button type="primary" @click="openCreateDialog">新增记录</el-button>
        </div>
      </div>

      <div v-if="viewMode === 'table'">
        <el-table :data="pagedRecords" :loading="loading" style="width: 100%">
          <el-table-column prop="recordDate" label="日期" width="120" />
          <el-table-column label="血压" width="140">
            <template #default="{ row }">
              {{ formatBloodPressure(row) }}
            </template>
          </el-table-column>
          <el-table-column prop="heartRate" label="心率" width="100">
            <template #default="{ row }">
              {{ row.heartRate ?? '--' }} bpm
            </template>
          </el-table-column>
          <el-table-column prop="bloodSugar" label="血糖" width="110">
            <template #default="{ row }">
              {{ row.bloodSugar ?? '--' }} mmol/L
            </template>
          </el-table-column>
          <el-table-column prop="weight" label="体重" width="100">
            <template #default="{ row }">
              {{ row.weight ?? '--' }} kg
            </template>
          </el-table-column>
          <el-table-column prop="notes" label="备注" min-width="220" show-overflow-tooltip />
          <el-table-column label="操作" width="160" fixed="right">
            <template #default="{ row }">
              <el-button size="small" @click="openEditDialog(row)">编辑</el-button>
              <el-button size="small" type="danger" @click="removeRecord(row.id)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>

        <div class="pagination">
          <el-pagination
            v-model:current-page="currentPage"
            v-model:page-size="pageSize"
            :total="filteredRecords.length"
            :page-sizes="[10, 20, 50]"
            layout="total, sizes, prev, pager, next, jumper"
          />
        </div>
      </div>

      <div v-else class="charts-wrap">
        <el-row :gutter="16">
          <el-col :xs="24" :xl="14">
            <el-card shadow="never" class="inner-card">
              <template #header><div class="inner-title">血压与体重趋势</div></template>
              <el-empty v-if="!chartRecords.length" description="暂无可视化数据" />
              <div v-else ref="trendChartRef" class="chart"></div>
            </el-card>
          </el-col>
          <el-col :xs="24" :xl="10">
            <el-card shadow="never" class="inner-card">
              <template #header><div class="inner-title">心率与血糖概览</div></template>
              <el-empty v-if="!chartRecords.length" description="暂无可视化数据" />
              <div v-else ref="vitalsChartRef" class="chart"></div>
            </el-card>
          </el-col>
        </el-row>
      </div>
    </el-card>

    <el-dialog
      v-model="dialogVisible"
      :title="editingRecordId ? '编辑健康记录' : '新增健康记录'"
      width="720px"
      @closed="resetForm"
    >
      <el-form ref="formRef" :model="formState" :rules="rules" label-width="100px">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="日期" prop="recordDate">
              <el-date-picker
                v-model="formState.recordDate"
                type="date"
                value-format="YYYY-MM-DD"
                placeholder="选择日期"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="体重" prop="weight">
              <el-input-number v-model="formState.weight" :min="20" :max="300" :step="0.1" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="收缩压" prop="bloodPressureSystolic">
              <el-input-number v-model="formState.bloodPressureSystolic" :min="60" :max="250" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="舒张压" prop="bloodPressureDiastolic">
              <el-input-number v-model="formState.bloodPressureDiastolic" :min="40" :max="150" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="心率" prop="heartRate">
              <el-input-number v-model="formState.heartRate" :min="40" :max="200" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
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
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import * as echarts from 'echarts'
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
const viewMode = ref('table')
const searchKeyword = ref('')
const currentPage = ref(1)
const pageSize = ref(10)
const editingRecordId = ref(null)
const records = ref([])
const formRef = ref(null)

const trendChartRef = ref(null)
const vitalsChartRef = ref(null)
let trendChart = null
let vitalsChart = null

const disposeCharts = () => {
  trendChart?.dispose()
  vitalsChart?.dispose()
  trendChart = null
  vitalsChart = null
}

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

const filteredRecords = computed(() => {
  const keyword = searchKeyword.value.trim().toLowerCase()
  return records.value.filter(item => !keyword || (item.notes || '').toLowerCase().includes(keyword))
})

const pagedRecords = computed(() => {
  const start = (currentPage.value - 1) * pageSize.value
  return filteredRecords.value.slice(start, start + pageSize.value)
})

const latestRecord = computed(() => filteredRecords.value[0] || null)
const chartRecords = computed(() =>
  [...filteredRecords.value]
    .sort((a, b) => new Date(a.recordDate) - new Date(b.recordDate))
    .slice(-12)
)

const summaryCards = computed(() => {
  const latest = latestRecord.value
  const previousWeight = filteredRecords.value[1]?.weight
  const currentWeight = latest?.weight
  const weightDiff = currentWeight != null && previousWeight != null
    ? (currentWeight - previousWeight).toFixed(1)
    : null

  return [
    {
      label: '最新血压',
      value: latest ? formatBloodPressure(latest) : '--',
      note: 'mmHg'
    },
    {
      label: '最新心率',
      value: latest?.heartRate != null ? `${latest.heartRate}` : '--',
      note: 'bpm'
    },
    {
      label: '最新血糖',
      value: latest?.bloodSugar != null ? `${latest.bloodSugar}` : '--',
      note: 'mmol/L'
    },
    {
      label: '体重变化',
      value: weightDiff == null ? '--' : `${Number(weightDiff) > 0 ? '+' : ''}${weightDiff} kg`,
      note: '对比上一条记录'
    }
  ]
})

const formatBloodPressure = (row) => {
  if (!row?.bloodPressureSystolic || !row?.bloodPressureDiastolic) return '--'
  return `${row.bloodPressureSystolic}/${row.bloodPressureDiastolic}`
}

const loadRecords = async () => {
  loading.value = true
  try {
    const userId = userStore.userInfo?.id || 1
    const response = await request.get(`/health/records/${userId}`, {
      params: { pageNum: 1, pageSize: 1000 }
    })
    records.value = response?.data?.records || []
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

const openCreateDialog = () => {
  resetForm()
  dialogVisible.value = true
}

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
    const payload = {
      userId,
      ...formState
    }
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
    await ElMessageBox.confirm('确定删除这条健康记录吗？', '提示', {
      type: 'warning'
    })
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

const renderTrendChart = () => {
  if (!trendChartRef.value || !chartRecords.value.length) return
  if (trendChart && trendChart.getDom() !== trendChartRef.value) {
    trendChart.dispose()
    trendChart = null
  }
  if (!trendChart) trendChart = echarts.init(trendChartRef.value)
  trendChart.setOption({
    tooltip: { trigger: 'axis' },
    legend: {
      data: ['体重', '收缩压', '舒张压'],
      top: 0
    },
    grid: {
      left: 46,
      right: 36,
      top: 42,
      bottom: 28
    },
    xAxis: {
      type: 'category',
      data: chartRecords.value.map(item => item.recordDate)
    },
    yAxis: [
      { type: 'value', name: 'kg' },
      { type: 'value', name: 'mmHg' }
    ],
    series: [
      {
        name: '体重',
        type: 'line',
        smooth: true,
        data: chartRecords.value.map(item => item.weight ?? null),
        lineStyle: { color: '#0ea5e9' },
        itemStyle: { color: '#0ea5e9' }
      },
      {
        name: '收缩压',
        type: 'line',
        smooth: true,
        yAxisIndex: 1,
        data: chartRecords.value.map(item => item.bloodPressureSystolic ?? null),
        lineStyle: { color: '#ef4444' },
        itemStyle: { color: '#ef4444' }
      },
      {
        name: '舒张压',
        type: 'line',
        smooth: true,
        yAxisIndex: 1,
        data: chartRecords.value.map(item => item.bloodPressureDiastolic ?? null),
        lineStyle: { color: '#f59e0b' },
        itemStyle: { color: '#f59e0b' }
      }
    ]
  })
}

const renderVitalsChart = () => {
  if (!vitalsChartRef.value || !chartRecords.value.length) return
  if (vitalsChart && vitalsChart.getDom() !== vitalsChartRef.value) {
    vitalsChart.dispose()
    vitalsChart = null
  }
  if (!vitalsChart) vitalsChart = echarts.init(vitalsChartRef.value)
  vitalsChart.setOption({
    tooltip: { trigger: 'axis' },
    legend: {
      data: ['心率', '血糖'],
      top: 0
    },
    grid: {
      left: 46,
      right: 32,
      top: 42,
      bottom: 28
    },
    xAxis: {
      type: 'category',
      data: chartRecords.value.map(item => item.recordDate)
    },
    yAxis: [
      { type: 'value', name: 'bpm' },
      { type: 'value', name: 'mmol/L' }
    ],
    series: [
      {
        name: '心率',
        type: 'bar',
        data: chartRecords.value.map(item => item.heartRate ?? null),
        itemStyle: { color: '#8b5cf6' }
      },
      {
        name: '血糖',
        type: 'line',
        smooth: true,
        yAxisIndex: 1,
        data: chartRecords.value.map(item => item.bloodSugar ?? null),
        lineStyle: { color: '#10b981' },
        itemStyle: { color: '#10b981' }
      }
    ]
  })
}

const resizeCharts = () => {
  trendChart?.resize()
  vitalsChart?.resize()
}

watch(chartRecords, async () => {
  if (viewMode.value !== 'chart') return
  await nextTick()
  renderTrendChart()
  renderVitalsChart()
}, { deep: true })

watch(viewMode, async (mode) => {
  if (mode !== 'chart') {
    disposeCharts()
    return
  }
  await nextTick()
  renderTrendChart()
  renderVitalsChart()
})

onMounted(async () => {
  await loadRecords()
  window.addEventListener('resize', resizeCharts)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', resizeCharts)
  disposeCharts()
})
</script>

<style scoped>
.health-record-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.summary-card {
  min-height: 126px;
}

.summary-label,
.summary-note {
  color: #64748b;
}

.summary-value {
  margin: 14px 0 8px;
  font-size: 30px;
  font-weight: 700;
  color: #0f172a;
}

.main-card {
  border-radius: 18px;
}

.toolbar {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  align-items: center;
  margin-bottom: 18px;
  flex-wrap: wrap;
}

.toolbar-left,
.toolbar-right {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.toolbar-left h3 {
  margin: 0;
}

.pagination {
  display: flex;
  justify-content: center;
  margin-top: 16px;
}

.charts-wrap {
  padding-top: 4px;
}

.inner-card {
  min-height: 420px;
}

.inner-title {
  font-weight: 600;
}

.chart {
  width: 100%;
  height: 320px;
}
</style>
