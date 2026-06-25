<template>
  <div class="smart-health-page">
    <el-card class="page-hero">
      <div class="page-hero-content">
        <div>
          <p class="hero-kicker">Smart Health Center</p>
          <h2>智能健康中心</h2>
          <p>基于真实健康记录和运动记录生成风险评分、恢复状态与个性化建议，帮助快速判断本周重点。</p>
        </div>
        <el-button type="primary" @click="refreshData">刷新分析</el-button>
      </div>
    </el-card>

    <el-row :gutter="16">
      <el-col :xs="24" :xl="14">
        <el-card class="chart-card">
          <template #header><div class="card-header">风险评分总览</div></template>
          <el-empty v-if="!riskData.length" description="暂无风险评分" />
          <div v-else ref="riskChartRef" class="chart"></div>
        </el-card>
      </el-col>
      <el-col :xs="24" :xl="10">
        <el-card class="chart-card">
          <template #header><div class="card-header">恢复状态</div></template>
          <el-empty v-if="!overview" description="暂无恢复数据" />
          <div v-else ref="recoveryChartRef" class="chart"></div>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="16">
      <el-col :xs="24" :xl="12">
        <el-card>
          <template #header><div class="card-header">健康风险评估</div></template>
          <div class="risk-list">
            <div v-for="risk in riskData" :key="risk.assessmentType" class="panel-card">
              <div class="risk-header">
                <strong>{{ labels[risk.assessmentType] || risk.assessmentType }}</strong>
                <el-tag :type="tagType(risk.riskLevel)">{{ levelText(risk.riskLevel) }}</el-tag>
              </div>
              <div class="score-line">
                <span>风险分</span>
                <strong>{{ risk.riskScore }}</strong>
              </div>
              <p>{{ risk.summary }}</p>
              <ul>
                <li v-for="item in risk.recommendations" :key="item">{{ item }}</li>
              </ul>
            </div>
          </div>
        </el-card>
      </el-col>

      <el-col :xs="24" :xl="12">
        <div class="stack">
          <el-card>
            <template #header><div class="card-header">饮食建议</div></template>
            <div class="panel-card solid">
              <div class="score-line">
                <span>建议热量</span>
                <strong>{{ overview?.nutritionAdvice?.dailyCalories || '--' }} kcal</strong>
              </div>
              <p>{{ overview?.nutritionAdvice?.summary }}</p>
              <ul>
                <li v-for="item in overview?.nutritionAdvice?.recommendations || []" :key="item">{{ item }}</li>
              </ul>
            </div>
          </el-card>

          <el-card>
            <template #header><div class="card-header">运动计划</div></template>
            <div class="panel-card solid">
              <div class="score-line">
                <span>本周目标</span>
                <strong>{{ overview?.exercisePlan?.weeklyMinutesTarget || '--' }} 分钟</strong>
              </div>
              <p>{{ overview?.exercisePlan?.goal }} / {{ overview?.exercisePlan?.intensity }}</p>
              <ul>
                <li v-for="item in overview?.exercisePlan?.weeklyPlan || []" :key="item">{{ item }}</li>
              </ul>
            </div>
          </el-card>
        </div>
      </el-col>
    </el-row>

    <el-row :gutter="16">
      <el-col :xs="24" :lg="12">
        <el-card>
          <template #header><div class="card-header">睡眠分析</div></template>
          <div class="panel-card solid">
            <div class="score-line">
              <span>睡眠评分</span>
              <strong>{{ overview?.sleepInsight?.score || '--' }}</strong>
            </div>
            <p>{{ overview?.sleepInsight?.summary }}</p>
            <ul>
              <li v-for="item in overview?.sleepInsight?.recommendations || []" :key="item">{{ item }}</li>
            </ul>
          </div>
        </el-card>
      </el-col>
      <el-col :xs="24" :lg="12">
        <el-card>
          <template #header><div class="card-header">压力监测</div></template>
          <div class="panel-card solid">
            <div class="score-line">
              <span>压力分值</span>
              <strong>{{ overview?.stressInsight?.score || '--' }}</strong>
            </div>
            <p>{{ overview?.stressInsight?.summary }}</p>
            <ul>
              <li v-for="item in overview?.stressInsight?.recommendations || []" :key="item">{{ item }}</li>
            </ul>
          </div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import * as echarts from 'echarts'
import request from '../utils/request'
import { useUserStore } from '../store/user'
import { useAnalyticsStore } from '../store/analytics'

const userStore = useUserStore()
const analyticsStore = useAnalyticsStore()
const overview = ref(null)

const riskChartRef = ref(null)
const recoveryChartRef = ref(null)
let riskChart = null
let recoveryChart = null

const labels = {
  BMI: 'BMI 风险',
  BLOOD_PRESSURE: '血压风险',
  DIABETES: '血糖风险',
  CARDIO: '心血管风险'
}

const riskData = computed(() =>
  [...(overview.value?.riskAssessments || [])].sort((a, b) => Number(b.riskScore || 0) - Number(a.riskScore || 0))
)

const weeklyExerciseMinutes = computed(() => {
  const threshold = new Date()
  threshold.setDate(threshold.getDate() - 6)
  return analyticsStore.sportRecords
    .filter(record => record.recordDate && new Date(record.recordDate) >= threshold)
    .reduce((sum, record) => sum + Number(record.duration || 0), 0)
})

const weeklyExerciseTarget = computed(() => Number(overview.value?.exercisePlan?.weeklyMinutesTarget || 0))

const exerciseCompletionRate = computed(() => {
  if (!weeklyExerciseTarget.value) return 0
  return Math.min(100, Math.round((weeklyExerciseMinutes.value / weeklyExerciseTarget.value) * 100))
})

const averageRiskScore = computed(() => {
  if (!riskData.value.length) return 0
  return Math.round(
    riskData.value.reduce((sum, item) => sum + Number(item.riskScore || 0), 0) / riskData.value.length
  )
})

const recoveryMetrics = computed(() => [
  {
    name: '睡眠恢复',
    value: Number(overview.value?.sleepInsight?.score || 0),
    color: '#10b981'
  },
  {
    name: '压力平衡',
    value: Number(overview.value?.stressInsight?.score || 0),
    color: '#f59e0b'
  },
  {
    name: '运动达成',
    value: exerciseCompletionRate.value,
    color: '#2563eb'
  },
  {
    name: '总体风险控制',
    value: Math.max(0, 100 - averageRiskScore.value),
    color: '#8b5cf6'
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

const riskBarColor = (level) => {
  if (level === 'HIGH') return '#ef4444'
  if (level === 'MEDIUM') return '#f59e0b'
  return '#22c55e'
}

const renderRiskChart = () => {
  if (!riskChartRef.value || !riskData.value.length) return
  riskChart = ensureChart(riskChart, riskChartRef)

  riskChart.setOption({
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'shadow' },
      formatter: (params) => {
        const current = params[0]
        const item = riskData.value[current.dataIndex]
        return `${item ? (labels[item.assessmentType] || item.assessmentType) : current.name}<br/>风险分：${current.value}<br/>等级：${levelText(item?.riskLevel)}`
      }
    },
    grid: {
      left: 96,
      right: 24,
      top: 28,
      bottom: 24
    },
    xAxis: {
      type: 'value',
      max: 100,
      splitLine: { lineStyle: { color: '#e2e8f0' } },
      axisLabel: { color: '#64748b' }
    },
    yAxis: {
      type: 'category',
      axisTick: { show: false },
      axisLine: { show: false },
      axisLabel: { color: '#334155' },
      data: riskData.value.map(item => labels[item.assessmentType] || item.assessmentType)
    },
    series: [
      {
        type: 'bar',
        barWidth: 18,
        showBackground: true,
        backgroundStyle: {
          color: '#eef2ff',
          borderRadius: 999
        },
        itemStyle: {
          borderRadius: 999,
          color: (params) => riskBarColor(riskData.value[params.dataIndex]?.riskLevel)
        },
        label: {
          show: true,
          position: 'right',
          color: '#0f172a',
          fontWeight: 600
        },
        markLine: {
          symbol: 'none',
          lineStyle: {
            type: 'dashed',
            color: '#94a3b8'
          },
          label: {
            formatter: '高风险线 70',
            color: '#475569'
          },
          data: [{ xAxis: 70 }]
        },
        data: riskData.value.map(item => Number(item.riskScore || 0))
      }
    ]
  })
}

const renderRecoveryChart = () => {
  if (!recoveryChartRef.value || !overview.value) return
  recoveryChart = ensureChart(recoveryChart, recoveryChartRef)

  recoveryChart.setOption({
    tooltip: {
      trigger: 'item',
      formatter: ({ name, value }) => `${name}<br/>${value} 分`
    },
    radar: {
      radius: '62%',
      splitNumber: 4,
      axisName: {
        color: '#334155',
        fontSize: 12
      },
      splitLine: {
        lineStyle: { color: ['#e2e8f0', '#dbeafe', '#bfdbfe', '#93c5fd'] }
      },
      splitArea: {
        areaStyle: { color: ['rgba(239, 246, 255, 0.3)', 'rgba(239, 246, 255, 0.55)'] }
      },
      axisLine: {
        lineStyle: { color: '#cbd5e1' }
      },
      indicator: recoveryMetrics.value.map(item => ({
        name: item.name,
        max: 100
      }))
    },
    series: [
      {
        type: 'radar',
        symbol: 'circle',
        symbolSize: 8,
        data: [
          {
            value: recoveryMetrics.value.map(item => item.value),
            name: '恢复状态',
            areaStyle: {
              color: 'rgba(37, 99, 235, 0.18)'
            },
            lineStyle: {
              width: 3,
              color: '#2563eb'
            },
            itemStyle: {
              color: '#2563eb'
            }
          }
        ]
      }
    ],
    graphic: recoveryMetrics.value.map((item, index) => ({
      type: 'group',
      right: 8,
      top: 12 + index * 24,
      children: [
        {
          type: 'circle',
          shape: { cx: 5, cy: 5, r: 5 },
          style: { fill: item.color }
        },
        {
          type: 'text',
          left: 14,
          style: {
            text: `${item.name} ${item.value}`,
            fill: '#475569',
            fontSize: 12
          }
        }
      ]
    }))
  })
}

const resizeCharts = () => {
  riskChart?.resize()
  recoveryChart?.resize()
}

const refreshData = async () => {
  const userId = userStore.userInfo?.id || 1
  const [overviewResponse] = await Promise.all([
    request.get('/smart-health/overview', {
      params: { userId }
    }),
    analyticsStore.fetchSportRecords(userId, { pageNum: 1, pageSize: 1000 })
  ])
  overview.value = overviewResponse.data
}

watch([riskData, recoveryMetrics], async () => {
  await nextTick()
  renderRiskChart()
  renderRecoveryChart()
}, { deep: true })

onMounted(async () => {
  try {
    await refreshData()
    await nextTick()
    renderRiskChart()
    renderRecoveryChart()
    window.addEventListener('resize', resizeCharts)
  } catch (error) {
    console.error('加载智能健康数据失败', error)
  }
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', resizeCharts)
  riskChart?.dispose()
  recoveryChart?.dispose()
})
</script>

<style scoped>
.smart-health-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.page-hero {
  border: none;
  background:
    linear-gradient(135deg, rgba(13, 148, 136, 0.95), rgba(14, 116, 144, 0.92)),
    linear-gradient(135deg, #0f766e, #155e75);
  color: #fff;
}

.page-hero-content {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  align-items: center;
  flex-wrap: wrap;
}

.hero-kicker {
  margin: 0 0 8px;
  font-size: 12px;
  letter-spacing: 0.14em;
  text-transform: uppercase;
  opacity: 0.78;
}

.chart-card {
  min-height: 420px;
}

.chart {
  width: 100%;
  height: 320px;
}

.stack {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.card-header {
  font-weight: 600;
}

.risk-list {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  gap: 12px;
}

.panel-card {
  padding: 16px;
  border-radius: 16px;
  background: linear-gradient(180deg, #f8fafc, #eef6ff);
}

.panel-card.solid {
  background: #f8fafc;
}

.risk-header,
.score-line {
  display: flex;
  justify-content: space-between;
  gap: 10px;
  align-items: center;
}

.score-line {
  margin: 14px 0;
}

.panel-card p {
  margin: 0 0 10px;
  line-height: 1.6;
  color: #475569;
}

.panel-card ul {
  margin: 0;
  padding-left: 18px;
  color: #334155;
}
</style>
