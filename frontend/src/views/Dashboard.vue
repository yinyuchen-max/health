<template>
  <div class="dashboard">
    <!-- 健康指标卡片 -->
    <el-row :gutter="16">
      <el-col :xs="24" :sm="12" :lg="6" v-for="card in metricCards" :key="card.label">
        <div class="metric-card">
          <div class="metric-header">
            <div class="metric-icon" :style="{ background: card.iconBg }">
              <el-icon :style="{ color: card.iconColor }" :size="22"><component :is="card.icon" /></el-icon>
            </div>
            <span class="metric-label">{{ card.label }}</span>
          </div>
          <div class="metric-body">
            <span class="metric-value">{{ card.value }}</span>
            <el-tag :type="card.statusType" size="small" class="metric-status">{{ card.statusText }}</el-tag>
          </div>
          <div class="metric-unit">{{ card.unit }}</div>
          <div class="metric-sparkline" :ref="el => setSparklineRef(card.refKey, el)"></div>
          <div class="metric-trend" v-if="card.trend">
            <span :class="card.trendClass">{{ card.trend }}</span>
          </div>
        </div>
      </el-col>
    </el-row>

    <!-- 健康记录区域 -->
    <div class="records-section">
      <div class="records-header">
        <div class="records-title-area">
          <div class="records-icon">
            <el-icon :size="20"><Document /></el-icon>
          </div>
          <h3 class="records-title">健康记录</h3>
        </div>
        <div class="records-actions">
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
          <el-button type="primary" @click="router.push('/app/health')">
            <el-icon><Plus /></el-icon>
            新增记录
          </el-button>
        </div>
      </div>

      <!-- Tab 筛选 -->
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

      <!-- 记录表格 -->
      <el-table :data="filteredRecords" :loading="loading" class="records-table">
        <el-table-column width="50">
          <template #header>
            <el-checkbox v-model="selectAll" @change="toggleSelectAll" />
          </template>
          <template #default="{ row }">
            <el-checkbox v-model="row.selected" />
          </template>
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
            <el-button size="small" text type="primary" @click="router.push('/app/health')">编辑</el-button>
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
  </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { Search, Plus, Document, Monitor, FirstAidKit, Odometer, DataLine } from '@element-plus/icons-vue'
import * as echarts from 'echarts'
import request from '../utils/request'
import { useUserStore } from '../store/user'
import { useAnalyticsStore } from '../store/analytics'

const router = useRouter()
const userStore = useUserStore()
const analyticsStore = useAnalyticsStore()

const loading = ref(false)
const records = ref([])
const searchKeyword = ref('')
const dateRange = ref([])
const activeTab = ref('all')
const currentPage = ref(1)
const pageSize = ref(10)
const selectAll = ref(false)

const sparklineRefs = {}
const sparklineCharts = {}

const setSparklineRef = (key, el) => {
  if (el) {
    sparklineRefs[key] = el
  }
}

const tabs = [
  { key: 'all', label: '全部' },
  { key: 'bp', label: '血压' },
  { key: 'hr', label: '心率' },
  { key: 'bs', label: '血糖' },
  { key: 'weight', label: '体重' },
  { key: 'notes', label: '备注' }
]

const latestRecord = computed(() => {
  const sorted = [...records.value].filter(r => r.recordDate).sort((a, b) => new Date(b.recordDate) - new Date(a.recordDate))
  return sorted[0] || null
})

const previousRecord = computed(() => {
  const sorted = [...records.value].filter(r => r.recordDate).sort((a, b) => new Date(b.recordDate) - new Date(a.recordDate))
  return sorted[1] || null
})

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

const getWeightStatus = (row) => {
  if (!row?.weight) return '--'
  return '正常'
}

const metricCards = computed(() => {
  const latest = latestRecord.value
  const prev = previousRecord.value
  const bp = latest ? `${latest.bloodPressureSystolic || '--'}/${latest.bloodPressureDiastolic || '--'}` : '--'
  const hr = latest?.heartRate ?? '--'
  const bs = latest?.bloodSugar ?? '--'
  const weightDiff = latest?.weight != null && prev?.weight != null
    ? (latest.weight - prev.weight).toFixed(1)
    : null

  return [
    {
      label: '最新血压', value: bp, unit: 'mmHg',
      statusText: getBPStatus(latest || {}), statusType: getBPStatus(latest || {}) === '正常' ? 'success' : 'danger',
      icon: FirstAidKit, iconBg: '#FEE2E2', iconColor: '#EF4444',
      refKey: 'bp',
      trend: latest?.bloodPressureSystolic ? `较上次 ${latest.bloodPressureSystolic - (prev?.bloodPressureSystolic || latest.bloodPressureSystolic) > 0 ? '+' : ''}${latest.bloodPressureSystolic - (prev?.bloodPressureSystolic || latest.bloodPressureSystolic)}` : null,
      trendClass: latest?.bloodPressureSystolic && latest.bloodPressureSystolic > (prev?.bloodPressureSystolic || 0) ? 'trend-up' : 'trend-down'
    },
    {
      label: '最新心率', value: hr, unit: 'bpm',
      statusText: getHRStatus(latest || {}), statusType: getHRStatus(latest || {}) === '正常' ? 'success' : 'danger',
      icon: Monitor, iconBg: '#DBEAFE', iconColor: '#3B82F6',
      refKey: 'hr',
      trend: latest?.heartRate ? `较上次 ${latest.heartRate - (prev?.heartRate || latest.heartRate) > 0 ? '+' : ''}${latest.heartRate - (prev?.heartRate || latest.heartRate)}` : null,
      trendClass: latest?.heartRate && latest.heartRate > (prev?.heartRate || 0) ? 'trend-up' : 'trend-down'
    },
    {
      label: '最新血糖', value: bs, unit: 'mmol/L',
      statusText: getBSStatus(latest || {}), statusType: getBSStatus(latest || {}) === '正常' ? 'success' : 'danger',
      icon: Odometer, iconBg: '#F3E8FF', iconColor: '#8B5CF6',
      refKey: 'bs',
      trend: latest?.bloodSugar ? `较上次 ${latest.bloodSugar - (prev?.bloodSugar || latest.bloodSugar) > 0 ? '+' : ''}${(latest.bloodSugar - (prev?.bloodSugar || latest.bloodSugar)).toFixed(1)}` : null,
      trendClass: latest?.bloodSugar && latest.bloodSugar > (prev?.bloodSugar || 0) ? 'trend-up' : 'trend-down'
    },
    {
      label: '体重变化', value: weightDiff != null ? `${Number(weightDiff) > 0 ? '+' : ''}${weightDiff} kg` : '--', unit: '',
      statusText: '', statusType: 'info',
      icon: DataLine, iconBg: '#D1FAE5', iconColor: '#10B981',
      refKey: 'weight',
      trend: weightDiff != null ? `较上次 ${Number(weightDiff) > 0 ? '+' : ''}${weightDiff} kg` : null,
      trendClass: weightDiff != null && Number(weightDiff) > 0 ? 'trend-up' : 'trend-down'
    }
  ]
})

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

const toggleSelectAll = (val) => {
  filteredRecords.value.forEach(r => r.selected = val)
}

const loadRecords = async () => {
  loading.value = true
  try {
    const userId = userStore.userInfo?.id || 1
    const response = await request.get(`/health/records/${userId}`, {
      params: { pageNum: 1, pageSize: 200 }
    })
    records.value = (response?.data?.records || []).map(r => ({ ...r, selected: false }))
    analyticsStore.setHealthRecords(records.value, userId)
  } catch (error) {
    console.error('加载健康记录失败', error)
    records.value = []
  } finally {
    loading.value = false
  }
}

const removeRecord = async (id) => {
  try {
    await request.delete(`/health/record/${id}`)
    await loadRecords()
  } catch (error) {
    console.error('删除失败', error)
  }
}

const renderSparklines = () => {
  const colors = { bp: '#EF4444', hr: '#3B82F6', bs: '#8B5CF6', weight: '#10B981' }
  const dataMap = {
    bp: records.value.map(r => r.bloodPressureSystolic).filter(Boolean),
    hr: records.value.map(r => r.heartRate).filter(Boolean),
    bs: records.value.map(r => r.bloodSugar).filter(Boolean),
    weight: records.value.map(r => r.weight).filter(Boolean)
  }

  Object.keys(sparklineRefs).forEach(key => {
    const el = sparklineRefs[key]
    if (!el) return
    if (sparklineCharts[key]) sparklineCharts[key].dispose()
    const chart = echarts.init(el)
    sparklineCharts[key] = chart
    chart.setOption({
      grid: { left: 0, right: 0, top: 4, bottom: 0 },
      xAxis: { show: false, type: 'category', data: dataMap[key].map((_, i) => i) },
      yAxis: { show: false, type: 'value' },
      series: [{
        type: 'line',
        smooth: true,
        symbol: 'none',
        data: dataMap[key],
        lineStyle: { color: colors[key], width: 2 },
        areaStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: colors[key] + '30' },
            { offset: 1, color: colors[key] + '05' }
          ])
        }
      }]
    })
  })
}

const resizeCharts = () => {
  Object.values(sparklineCharts).forEach(c => c?.resize())
}

onMounted(async () => {
  await loadRecords()
  await nextTick()
  renderSparklines()
  window.addEventListener('resize', resizeCharts)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', resizeCharts)
  Object.values(sparklineCharts).forEach(c => c?.dispose())
})
</script>

<style scoped>
.dashboard {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.metric-card {
  background: #fff;
  border-radius: 16px;
  padding: 20px;
  border: 1px solid #F0F4F8;
  transition: all 0.3s;
}

.metric-card:hover {
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.06);
}

.metric-header {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 12px;
}

.metric-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 42px;
  height: 42px;
  border-radius: 12px;
}

.metric-label {
  font-size: 14px;
  color: #64748B;
  font-weight: 500;
}

.metric-body {
  display: flex;
  align-items: baseline;
  gap: 10px;
  margin-bottom: 4px;
}

.metric-value {
  font-size: 32px;
  font-weight: 700;
  color: #1E293B;
  line-height: 1.2;
}

.metric-status {
  font-size: 12px;
}

.metric-unit {
  font-size: 13px;
  color: #94A3B8;
  margin-bottom: 8px;
}

.metric-sparkline {
  width: 100%;
  height: 40px;
}

.metric-trend {
  font-size: 12px;
  margin-top: 4px;
}

.trend-up {
  color: #EF4444;
}

.trend-down {
  color: #10B981;
}

.records-section {
  background: #fff;
  border-radius: 16px;
  padding: 20px 24px;
  border: 1px solid #F0F4F8;
}

.records-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
  flex-wrap: wrap;
  gap: 12px;
}

.records-title-area {
  display: flex;
  align-items: center;
  gap: 10px;
}

.records-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  background: #EBF5FF;
  border-radius: 10px;
  color: #3B82F6;
}

.records-title {
  margin: 0;
  font-size: 18px;
  font-weight: 700;
  color: #1E293B;
}

.records-actions {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.search-input {
  width: 240px;
}

.date-range {
  width: 260px;
}

.tab-bar {
  display: flex;
  gap: 8px;
  margin-bottom: 16px;
  flex-wrap: wrap;
}

.tab-btn {
  border-radius: 8px;
  font-size: 13px;
  padding: 6px 16px;
}

.records-table {
  border-radius: 12px;
}

.records-table :deep(.el-table__header th) {
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
  .metric-value {
    font-size: 24px;
  }

  .records-header {
    flex-direction: column;
    align-items: stretch;
  }

  .records-actions {
    flex-direction: column;
  }

  .search-input,
  .date-range {
    width: 100% !important;
  }

  .tab-bar {
    overflow-x: auto;
    flex-wrap: nowrap;
    padding-bottom: 4px;
  }

  .tab-btn {
    white-space: nowrap;
    flex-shrink: 0;
  }
}
</style>
