<template>
  <div class="sport-record-page">
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
          <h3>运动记录</h3>
          <el-radio-group v-model="viewMode" size="small">
            <el-radio-button label="table">列表</el-radio-button>
            <el-radio-button label="chart">图表</el-radio-button>
          </el-radio-group>
        </div>
        <div class="toolbar-right">
          <el-date-picker
            v-model="dateRange"
            type="daterange"
            range-separator="至"
            start-placeholder="开始日期"
            end-placeholder="结束日期"
            value-format="YYYY-MM-DD"
            unlink-panels
          />
          <el-input v-model="searchKeyword" placeholder="搜索备注或类型" clearable style="width: 220px" />
          <el-button type="primary" @click="openCreateDialog">新增记录</el-button>
        </div>
      </div>

      <div v-if="viewMode === 'table'">
        <el-table :data="pagedRecords" :loading="loading" style="width: 100%">
          <el-table-column prop="recordDate" label="日期" width="120" />
          <el-table-column prop="sportType" label="运动类型" width="120" />
          <el-table-column label="强度" width="110">
            <template #default="{ row }">
              <el-tag :type="intensityTagType(row.intensity)">{{ intensityText(row.intensity) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="duration" label="时长" width="100">
            <template #default="{ row }">{{ row.duration }} 分钟</template>
          </el-table-column>
          <el-table-column prop="calories" label="热量" width="110">
            <template #default="{ row }">{{ row.calories ?? 0 }} kcal</template>
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
              <template #header><div class="inner-title">运动趋势</div></template>
              <el-empty v-if="!chartRecords.length" description="暂无可视化数据" />
              <div v-else ref="trendChartRef" class="chart"></div>
            </el-card>
          </el-col>
          <el-col :xs="24" :xl="10">
            <el-card shadow="never" class="inner-card">
              <template #header><div class="inner-title">类型分布与强度结构</div></template>
              <el-empty v-if="!chartRecords.length" description="暂无可视化数据" />
              <div v-else ref="distributionChartRef" class="chart chart-half"></div>
              <div v-if="chartRecords.length" ref="intensityChartRef" class="chart chart-half"></div>
            </el-card>
          </el-col>
        </el-row>
      </div>
    </el-card>

    <el-dialog
      v-model="dialogVisible"
      :title="editingRecordId ? '编辑运动记录' : '新增运动记录'"
      width="680px"
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
            <el-form-item label="运动类型" prop="sportType">
              <el-select v-model="formState.sportType" placeholder="选择运动类型" style="width: 100%">
                <el-option v-for="item in sportOptions" :key="item" :label="item" :value="item" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="时长" prop="duration">
              <el-input-number v-model="formState.duration" :min="1" :max="1440" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="强度" prop="intensity">
              <el-select v-model="formState.intensity" placeholder="选择强度" style="width: 100%">
                <el-option label="低强度" value="low" />
                <el-option label="中等强度" value="medium" />
                <el-option label="高强度" value="high" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>

        <el-form-item label="热量">
          <el-input-number v-model="formState.calories" :min="0" :max="5000" style="width: 100%" />
        </el-form-item>

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
const dateRange = ref([])
const records = ref([])
const formRef = ref(null)

const trendChartRef = ref(null)
const distributionChartRef = ref(null)
const intensityChartRef = ref(null)
let trendChart = null
let distributionChart = null
let intensityChart = null

const disposeCharts = () => {
  trendChart?.dispose()
  distributionChart?.dispose()
  intensityChart?.dispose()
  trendChart = null
  distributionChart = null
  intensityChart = null
}

const sportOptions = ['跑步', '游泳', '骑行', '健身', '瑜伽', '步行', '球类', '其他']

const emptyForm = () => ({
  recordDate: '',
  sportType: '',
  duration: null,
  calories: null,
  intensity: '',
  notes: ''
})

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
    const inKeyword = !keyword
      || (item.notes || '').toLowerCase().includes(keyword)
      || (item.sportType || '').toLowerCase().includes(keyword)
    const inDateRange = !dateRange.value?.length
      || (item.recordDate >= dateRange.value[0] && item.recordDate <= dateRange.value[1])
    return inKeyword && inDateRange
  })
})

const pagedRecords = computed(() => {
  const start = (currentPage.value - 1) * pageSize.value
  return filteredRecords.value.slice(start, start + pageSize.value)
})

const chartRecords = computed(() =>
  [...filteredRecords.value]
    .sort((a, b) => new Date(a.recordDate) - new Date(b.recordDate))
    .slice(-12)
)

const summaryCards = computed(() => {
  const threshold = new Date()
  threshold.setDate(threshold.getDate() - 6)
  const weekly = records.value.filter(item => new Date(item.recordDate) >= threshold)
  const duration = weekly.reduce((sum, item) => sum + Number(item.duration || 0), 0)
  const calories = weekly.reduce((sum, item) => sum + Number(item.calories || 0), 0)
  const intensityMap = { low: 1, medium: 2, high: 3 }
  const avgIntensityValue = weekly.length
    ? (weekly.reduce((sum, item) => sum + (intensityMap[item.intensity] || 0), 0) / weekly.length).toFixed(1)
    : '--'

  return [
    {
      label: '近 7 天运动',
      value: duration,
      note: '分钟'
    },
    {
      label: '近 7 天热量',
      value: calories,
      note: 'kcal'
    },
    {
      label: '近 7 天次数',
      value: weekly.length,
      note: '条记录'
    },
    {
      label: '平均强度',
      value: avgIntensityValue,
      note: weekly.length ? intensityText(avgIntensityValue < 1.5 ? 'low' : avgIntensityValue < 2.5 ? 'medium' : 'high') : '暂无数据'
    }
  ]
})

const intensityText = (intensity) => {
  switch (intensity) {
    case 'low':
      return '低强度'
    case 'medium':
      return '中等强度'
    case 'high':
      return '高强度'
    default:
      return '--'
  }
}

const intensityTagType = (intensity) => {
  switch (intensity) {
    case 'low':
      return 'info'
    case 'medium':
      return 'warning'
    case 'high':
      return 'danger'
    default:
      return ''
  }
}

const estimateCalories = () => {
  if (formState.calories != null || !formState.duration || !formState.sportType) return
  const factorMap = {
    跑步: 10,
    游泳: 12,
    骑行: 8,
    健身: 7,
    瑜伽: 4,
    步行: 4,
    球类: 9,
    其他: 5
  }
  formState.calories = Math.round((factorMap[formState.sportType] || 5) * Number(formState.duration))
}

const loadRecords = async () => {
  loading.value = true
  try {
    const userId = userStore.userInfo?.id || 1
    const response = await request.get(`/sport/records/${userId}`, {
      params: { pageNum: 1, pageSize: 1000 }
    })
    records.value = response?.data?.records || []
    analyticsStore.setSportRecords(records.value, userId)
  } catch (error) {
    console.error('加载运动记录失败', error)
    ElMessage.error('加载运动记录失败')
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
    sportType: row.sportType,
    duration: row.duration,
    calories: row.calories != null ? Number(row.calories) : null,
    intensity: row.intensity,
    notes: row.notes || ''
  })
  dialogVisible.value = true
}

const submitForm = async () => {
  if (!formRef.value) return
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  estimateCalories()
  saving.value = true
  try {
    const userId = userStore.userInfo?.id || 1
    const payload = {
      userId,
      ...formState
    }
    if (editingRecordId.value) {
      await request.put(`/sport/record/${editingRecordId.value}`, payload)
      ElMessage.success('运动记录已更新')
    } else {
      await request.post('/sport/record', payload)
      ElMessage.success('运动记录已新增')
    }
    dialogVisible.value = false
    await loadRecords()
    notifyHistoryChanged()
  } catch (error) {
    console.error('保存运动记录失败', error)
    ElMessage.error('保存运动记录失败')
  } finally {
    saving.value = false
  }
}

const removeRecord = async (id) => {
  try {
    await ElMessageBox.confirm('确定删除这条运动记录吗？', '提示', { type: 'warning' })
    await request.delete(`/sport/record/${id}`)
    ElMessage.success('运动记录已删除')
    await loadRecords()
    notifyHistoryChanged()
  } catch (error) {
    if (error !== 'cancel') {
      console.error('删除运动记录失败', error)
      ElMessage.error('删除运动记录失败')
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
      data: ['运动时长', '热量'],
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
      { type: 'value', name: '分钟' },
      { type: 'value', name: 'kcal' }
    ],
    series: [
      {
        name: '运动时长',
        type: 'bar',
        data: chartRecords.value.map(item => item.duration ?? 0),
        itemStyle: { color: '#2563eb' }
      },
      {
        name: '热量',
        type: 'line',
        smooth: true,
        yAxisIndex: 1,
        data: chartRecords.value.map(item => item.calories ?? 0),
        lineStyle: { color: '#10b981' },
        itemStyle: { color: '#10b981' }
      }
    ]
  })
}

const renderDistributionChart = () => {
  if (!distributionChartRef.value || !chartRecords.value.length) return
  if (distributionChart && distributionChart.getDom() !== distributionChartRef.value) {
    distributionChart.dispose()
    distributionChart = null
  }
  if (!distributionChart) distributionChart = echarts.init(distributionChartRef.value)
  const grouped = chartRecords.value.reduce((acc, item) => {
    acc[item.sportType] = (acc[item.sportType] || 0) + Number(item.duration || 0)
    return acc
  }, {})
  distributionChart.setOption({
    tooltip: {
      trigger: 'item',
      formatter: '{b}: {c} 分钟 ({d}%)'
    },
    series: [
      {
        type: 'pie',
        radius: ['40%', '68%'],
        center: ['50%', '50%'],
        itemStyle: {
          borderColor: '#fff',
          borderWidth: 3
        },
        data: Object.entries(grouped).map(([name, value]) => ({ name, value }))
      }
    ]
  })
}

const renderIntensityChart = () => {
  if (!intensityChartRef.value || !chartRecords.value.length) return
  if (intensityChart && intensityChart.getDom() !== intensityChartRef.value) {
    intensityChart.dispose()
    intensityChart = null
  }
  if (!intensityChart) intensityChart = echarts.init(intensityChartRef.value)
  const grouped = chartRecords.value.reduce((acc, item) => {
    acc[item.intensity] = (acc[item.intensity] || 0) + Number(item.duration || 0)
    return acc
  }, { low: 0, medium: 0, high: 0 })
  intensityChart.setOption({
    tooltip: { trigger: 'axis' },
    grid: {
      left: 36,
      right: 24,
      top: 20,
      bottom: 24
    },
    xAxis: {
      type: 'category',
      data: ['低强度', '中等强度', '高强度']
    },
    yAxis: {
      type: 'value',
      name: '分钟'
    },
    series: [
      {
        type: 'bar',
        data: [grouped.low, grouped.medium, grouped.high],
        itemStyle: {
          color: (params) => ['#94a3b8', '#f59e0b', '#ef4444'][params.dataIndex]
        }
      }
    ]
  })
}

const resizeCharts = () => {
  trendChart?.resize()
  distributionChart?.resize()
  intensityChart?.resize()
}

watch(chartRecords, async () => {
  if (viewMode.value !== 'chart') return
  await nextTick()
  renderTrendChart()
  renderDistributionChart()
  renderIntensityChart()
}, { deep: true })

watch(viewMode, async (mode) => {
  if (mode !== 'chart') {
    disposeCharts()
    return
  }
  await nextTick()
  renderTrendChart()
  renderDistributionChart()
  renderIntensityChart()
})

watch(() => [formState.duration, formState.sportType], () => {
  if (!editingRecordId.value) {
    estimateCalories()
  }
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
.sport-record-page {
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

.chart-half {
  height: 150px;
}
</style>
