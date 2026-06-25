<template>
  <div class="dashboard">
    <el-card class="hero-card">
      <div class="hero-content">
        <div>
          <p class="eyebrow">Smart Health Dashboard</p>
          <h2>{{ greeting }}，{{ userStore.userInfo.username || '用户' }}</h2>
          <p class="hero-desc">
            今天是 {{ todayLabel }}。这里汇总健康记录、运动记录和智能分析结果，方便快速查看趋势、风险与恢复状态。
          </p>
        </div>
        <div class="hero-actions">
          <el-button type="primary" @click="router.push('/app/smart-health')">查看智能健康中心</el-button>
          <el-button @click="router.push('/app/health')">新增健康记录</el-button>
        </div>
      </div>
    </el-card>

    <el-row :gutter="16">
      <el-col :xs="24" :sm="12" :lg="6" v-for="item in summaryCards" :key="item.label">
        <el-card class="stat-card">
          <div class="stat-label">{{ item.label }}</div>
          <div class="stat-value">{{ item.value }}</div>
          <div class="stat-hint">{{ item.hint }}</div>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="16">
      <el-col :xs="24" :xl="14">
        <el-card class="chart-card">
          <template #header>
            <div class="section-title">健康指标趋势</div>
          </template>
          <el-empty v-if="!healthTrend.length" description="暂无健康记录" />
          <div v-else ref="healthTrendRef" class="chart"></div>
        </el-card>
      </el-col>
      <el-col :xs="24" :xl="10">
        <el-card class="chart-card">
          <template #header>
            <div class="section-title">运动类型分布</div>
          </template>
          <el-empty v-if="!sportDistribution.length" description="暂无运动记录" />
          <div v-else ref="sportPieRef" class="chart"></div>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="16">
      <el-col :xs="24" :lg="14">
        <el-card>
          <template #header>
            <div class="section-title">今日重点建议</div>
          </template>
          <el-empty v-if="!quickTips.length" description="暂无建议" />
          <div v-else class="tip-list">
            <div v-for="tip in quickTips" :key="tip" class="tip-item">
              <el-icon class="tip-icon"><Opportunity /></el-icon>
              <span>{{ tip }}</span>
            </div>
          </div>
        </el-card>
      </el-col>
      <el-col :xs="24" :lg="10">
        <el-card>
          <template #header>
            <div class="section-title">风险优先级</div>
          </template>
          <el-empty v-if="!priorityRisks.length" description="暂无风险数据" />
          <div v-else class="risk-list">
            <div v-for="risk in priorityRisks" :key="risk.assessmentType" class="risk-item">
              <div class="risk-head">
                <strong>{{ riskLabels[risk.assessmentType] || risk.assessmentType }}</strong>
                <el-tag :type="tagType(risk.riskLevel)">{{ levelText(risk.riskLevel) }}</el-tag>
              </div>
              <div class="risk-score-line">
                <span>风险分</span>
                <strong>{{ risk.riskScore }}</strong>
              </div>
              <p>{{ risk.summary }}</p>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { Opportunity } from '@element-plus/icons-vue'
import * as echarts from 'echarts'
import request from '../utils/request'
import { useUserStore } from '../store/user'
import { useAnalyticsStore } from '../store/analytics'

const router = useRouter()
const userStore = useUserStore()
const analyticsStore = useAnalyticsStore()
const overview = ref(null)

const healthTrendRef = ref(null)
const sportPieRef = ref(null)
let healthTrendChart = null
let sportPieChart = null

const riskLabels = {
  BMI: 'BMI 风险',
  BLOOD_PRESSURE: '血压风险',
  DIABETES: '血糖风险',
  CARDIO: '心血管风险'
}

const chartPalette = ['#2563eb', '#10b981', '#f59e0b', '#8b5cf6', '#ef4444', '#14b8a6']

const todayLabel = new Date().toLocaleDateString('zh-CN', {
  year: 'numeric',
  month: 'long',
  day: 'numeric',
  weekday: 'long'
})

const greeting = computed(() => {
  const hour = new Date().getHours()
  if (hour < 12) return '早上好'
  if (hour < 18) return '下午好'
  return '晚上好'
})

const healthTrend = computed(() =>
  [...analyticsStore.healthRecords]
    .filter(record => record.recordDate)
    .sort((a, b) => new Date(a.recordDate) - new Date(b.recordDate))
    .slice(-10)
)

const latestHealthRecord = computed(() => {
  const records = [...analyticsStore.healthRecords]
    .filter(record => record.recordDate)
    .sort((a, b) => new Date(b.recordDate) - new Date(a.recordDate))
  return records[0] || null
})

const weeklySportMinutes = computed(() => {
  const threshold = new Date()
  threshold.setDate(threshold.getDate() - 6)
  return analyticsStore.sportRecords
    .filter(record => record.recordDate && new Date(record.recordDate) >= threshold)
    .reduce((sum, record) => sum + Number(record.duration || 0), 0)
})

const sportDistribution = computed(() => {
  const grouped = analyticsStore.sportRecords.reduce((acc, item) => {
    const key = item.sportType || '其他'
    acc[key] = (acc[key] || 0) + Number(item.duration || 0)
    return acc
  }, {})

  return Object.entries(grouped)
    .map(([name, value]) => ({ name, value }))
    .sort((a, b) => b.value - a.value)
})

const quickTips = computed(() => overview.value?.quickTips || [])
const priorityRisks = computed(() =>
  [...(overview.value?.riskAssessments || [])]
    .sort((a, b) => Number(b.riskScore || 0) - Number(a.riskScore || 0))
    .slice(0, 3)
)

const averageWeight = computed(() => {
  const values = healthTrend.value.map(item => Number(item.weight)).filter(value => !Number.isNaN(value))
  if (!values.length) return null
  return Number((values.reduce((sum, value) => sum + value, 0) / values.length).toFixed(1))
})

const summaryCards = computed(() => [
  {
    label: 'BMI',
    value: overview.value?.bmi ?? '--',
    hint: overview.value?.overallStatus || '等待分析'
  },
  {
    label: '最新血压',
    value: latestHealthRecord.value?.bloodPressureSystolic
      ? `${latestHealthRecord.value.bloodPressureSystolic}/${latestHealthRecord.value.bloodPressureDiastolic}`
      : '--',
    hint: 'mmHg'
  },
  {
    label: '近 7 天运动',
    value: weeklySportMinutes.value,
    hint: '分钟'
  },
  {
    label: '压力等级',
    value: overview.value?.stressInsight?.level || '--',
    hint: overview.value?.stressInsight?.summary || '等待分析'
  }
])

const tagType = (level) => {
  if (level === 'HIGH') return 'danger'
  if (level === 'MEDIUM') return 'warning'
  return 'success'
}

const levelText = (level) => {
  if (level === 'HIGH') return '高'
  if (level === 'MEDIUM') return '中'
  if (level === 'LOW') return '低'
  return '--'
}

const ensureChart = (instance, chartRef) => {
  if (instance && instance.getDom() !== chartRef.value) {
    instance.dispose()
    return null
  }
  return instance || echarts.init(chartRef.value)
}

const renderHealthTrendChart = () => {
  if (!healthTrendRef.value || !healthTrend.value.length) return
  healthTrendChart = ensureChart(healthTrendChart, healthTrendRef)

  const dates = healthTrend.value.map(item => item.recordDate)
  const weights = healthTrend.value.map(item => item.weight ?? null)
  const systolic = healthTrend.value.map(item => item.bloodPressureSystolic ?? null)
  const diastolic = healthTrend.value.map(item => item.bloodPressureDiastolic ?? null)

  healthTrendChart.setOption({
    color: ['#2563eb', '#ef4444', '#f59e0b'],
    tooltip: {
      trigger: 'axis',
      backgroundColor: 'rgba(15, 23, 42, 0.92)',
      borderWidth: 0,
      textStyle: { color: '#fff' }
    },
    legend: {
      top: 0,
      itemWidth: 10,
      itemHeight: 10,
      textStyle: { color: '#475569' },
      data: ['体重', '收缩压', '舒张压']
    },
    grid: {
      left: 48,
      right: 24,
      top: 52,
      bottom: 44
    },
    xAxis: {
      type: 'category',
      boundaryGap: false,
      axisTick: { show: false },
      axisLine: { lineStyle: { color: '#cbd5e1' } },
      axisLabel: { color: '#64748b' },
      data: dates
    },
    yAxis: [
      {
        type: 'value',
        name: 'kg',
        position: 'left',
        splitLine: { lineStyle: { color: '#e2e8f0' } },
        axisLabel: { color: '#64748b' }
      },
      {
        type: 'value',
        name: 'mmHg',
        position: 'right',
        splitLine: { show: false },
        axisLabel: { color: '#64748b' }
      }
    ],
    series: [
      {
        name: '体重',
        type: 'line',
        smooth: true,
        symbolSize: 8,
        yAxisIndex: 0,
        data: weights,
        areaStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: 'rgba(37, 99, 235, 0.28)' },
            { offset: 1, color: 'rgba(37, 99, 235, 0.02)' }
          ])
        },
        markLine: averageWeight.value == null
          ? undefined
          : {
              symbol: 'none',
              label: {
                formatter: `均值 ${averageWeight.value}kg`,
                color: '#1d4ed8'
              },
              lineStyle: {
                type: 'dashed',
                color: 'rgba(37, 99, 235, 0.55)'
              },
              data: [{ yAxis: averageWeight.value }]
            }
      },
      {
        name: '收缩压',
        type: 'line',
        smooth: true,
        symbolSize: 7,
        yAxisIndex: 1,
        data: systolic,
        markLine: {
          symbol: 'none',
          lineStyle: {
            type: 'dashed',
            color: 'rgba(239, 68, 68, 0.45)'
          },
          label: {
            formatter: '警戒 140',
            color: '#b91c1c'
          },
          data: [{ yAxis: 140 }]
        }
      },
      {
        name: '舒张压',
        type: 'line',
        smooth: true,
        symbolSize: 7,
        yAxisIndex: 1,
        data: diastolic,
        markLine: {
          symbol: 'none',
          lineStyle: {
            type: 'dashed',
            color: 'rgba(245, 158, 11, 0.45)'
          },
          label: {
            formatter: '警戒 90',
            color: '#b45309'
          },
          data: [{ yAxis: 90 }]
        }
      }
    ]
  })
}

const renderSportPieChart = () => {
  if (!sportPieRef.value || !sportDistribution.value.length) return
  sportPieChart = ensureChart(sportPieChart, sportPieRef)

  sportPieChart.setOption({
    color: chartPalette,
    tooltip: {
      trigger: 'item',
      formatter: ({ name, value, percent }) => `${name}<br/>${value} 分钟，占比 ${percent}%`
    },
    legend: {
      bottom: 0,
      icon: 'circle',
      itemWidth: 10,
      itemHeight: 10,
      textStyle: { color: '#475569' }
    },
    series: [
      {
        name: '运动时长',
        type: 'pie',
        radius: ['42%', '72%'],
        center: ['50%', '46%'],
        minAngle: 8,
        avoidLabelOverlap: true,
        itemStyle: {
          borderRadius: 12,
          borderColor: '#fff',
          borderWidth: 4
        },
        label: {
          formatter: ({ name, value }) => `${name}\n${value} 分钟`,
          color: '#334155',
          fontSize: 12
        },
        labelLine: {
          lineStyle: { color: '#94a3b8' }
        },
        emphasis: {
          scale: true,
          scaleSize: 8
        },
        data: sportDistribution.value
      }
    ],
    graphic: {
      type: 'group',
      left: 'center',
      top: '36%',
      children: [
        {
          type: 'text',
          style: {
            text: `${weeklySportMinutes.value}`,
            fill: '#0f172a',
            fontSize: 24,
            fontWeight: 700,
            textAlign: 'center'
          }
        },
        {
          type: 'text',
          top: 28,
          style: {
            text: '近 7 天分钟数',
            fill: '#64748b',
            fontSize: 12,
            textAlign: 'center'
          }
        }
      ]
    }
  })
}

const resizeCharts = () => {
  healthTrendChart?.resize()
  sportPieChart?.resize()
}

const loadData = async () => {
  const userId = userStore.userInfo?.id || 1
  await Promise.all([
    analyticsStore.fetchHealthRecords(userId, { pageNum: 1, pageSize: 1000 }),
    analyticsStore.fetchSportRecords(userId, { pageNum: 1, pageSize: 1000 })
  ])

  const response = await request.get('/smart-health/overview', {
    params: { userId }
  })
  overview.value = response.data
}

watch([healthTrend, sportDistribution, weeklySportMinutes], async () => {
  await nextTick()
  renderHealthTrendChart()
  renderSportPieChart()
}, { deep: true })

onMounted(async () => {
  try {
    await loadData()
    await nextTick()
    renderHealthTrendChart()
    renderSportPieChart()
    window.addEventListener('resize', resizeCharts)
  } catch (error) {
    console.error('加载首页数据失败', error)
  }
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', resizeCharts)
  healthTrendChart?.dispose()
  sportPieChart?.dispose()
})
</script>

<style scoped>
.dashboard {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.hero-card {
  border: none;
  background:
    radial-gradient(circle at top left, rgba(77, 184, 255, 0.35), transparent 35%),
    linear-gradient(135deg, #0f4c81, #1c7ed6 55%, #7dd3fc);
  color: #fff;
}

.hero-content {
  display: flex;
  justify-content: space-between;
  gap: 24px;
  align-items: center;
  flex-wrap: wrap;
}

.eyebrow {
  margin: 0 0 8px;
  font-size: 12px;
  letter-spacing: 0.12em;
  text-transform: uppercase;
  opacity: 0.8;
}

.hero-desc {
  margin: 8px 0 0;
  max-width: 680px;
  line-height: 1.7;
}

.hero-actions {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
}

.stat-card {
  min-height: 128px;
}

.stat-label {
  color: #64748b;
  font-size: 14px;
}

.stat-value {
  margin-top: 12px;
  font-size: 34px;
  font-weight: 700;
  color: #0f172a;
}

.stat-hint {
  margin-top: 10px;
  color: #2563eb;
  font-size: 13px;
  line-height: 1.5;
}

.section-title {
  font-weight: 600;
}

.chart-card {
  min-height: 420px;
}

.chart {
  width: 100%;
  height: 340px;
}

.tip-list,
.risk-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.tip-item,
.risk-item {
  padding: 14px 16px;
  border-radius: 14px;
  background: #f8fafc;
}

.tip-item {
  display: flex;
  gap: 10px;
  align-items: flex-start;
}

.tip-icon {
  margin-top: 2px;
  color: #2563eb;
}

.risk-head,
.risk-score-line {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: center;
}

.risk-score-line {
  margin: 12px 0 8px;
  color: #1d4ed8;
}

.risk-item p {
  margin: 0;
  color: #4b5563;
  line-height: 1.6;
}
</style>
