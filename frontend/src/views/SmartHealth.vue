<template>
  <div class="smart-health-page">
    <el-card class="page-hero">
      <div class="page-hero-content">
        <div class="hero-left">
          <div class="hero-icon-box">
            <el-icon :size="28" color="#0d9488"><Opportunity /></el-icon>
          </div>
          <div>
            <p class="hero-kicker">Smart Health Center</p>
            <h2>智能健康中心</h2>
            <p class="hero-desc">基于真实健康记录和运动记录生成风险评分、恢复状态与个性化建议，帮助快速判断本周重点。</p>
          </div>
        </div>
        <el-button type="primary" :loading="loading" @click="refreshData(true)">重新生成</el-button>
      </div>
    </el-card>

    <el-card v-if="loading" class="analysis-loading-card" shadow="never">
      <div class="analysis-loading-shell">
        <div class="analysis-loading-copy">
          <p class="analysis-loading-eyebrow">HEALTH AI PIPELINE</p>
          <div class="analysis-loading-title-row">
            <h3>正在加载健康分析</h3>
            <span class="analysis-loading-pulse"></span>
          </div>
          <p class="analysis-loading-text">
            正在读取健康记录和运动数据，请稍候。
          </p>
          <div class="analysis-loading-tags">
            <span>风险识别</span>
            <span>恢复评估</span>
            <span>饮食建议</span>
            <span>运动计划</span>
          </div>
        </div>

        <div class="analysis-loading-preview">
          <div class="analysis-loading-preview-header">
            <span>分析进度</span>
            <strong>正在加载</strong>
          </div>
          <el-skeleton animated>
            <template #template>
              <el-skeleton-item variant="p" style="width: 82%; height: 18px; margin-bottom: 14px;" />
              <el-skeleton-item variant="p" style="width: 100%; height: 14px; margin-bottom: 10px;" />
              <el-skeleton-item variant="p" style="width: 93%; height: 14px; margin-bottom: 10px;" />
              <el-skeleton-item variant="p" style="width: 76%; height: 14px; margin-bottom: 18px;" />
              <div class="analysis-loading-bars">
                <el-skeleton-item variant="rect" style="width: 100%; height: 10px;" />
                <el-skeleton-item variant="rect" style="width: 88%; height: 10px;" />
                <el-skeleton-item variant="rect" style="width: 94%; height: 10px;" />
              </div>
            </template>
          </el-skeleton>
        </div>
      </div>
    </el-card>

    <el-alert v-if="!loading && overview?.analysisSource === 'RULES'" type="info" :closable="false" :title="overview.message" style="margin-bottom: 16px" />
    <el-alert v-if="!loading && overview && !noData && overview.evidence?.length" type="info" :closable="false" style="margin-bottom: 16px"
      :title="overview.fromCache ? '记录没有变化，这是上次的分析。想要新结论请点重新生成。' : '这次分析使用了以下记录'">
      <p v-for="item in overview.evidence" :key="item">{{ item }}</p>
    </el-alert>
    <el-card v-if="!loading" class="lifestyle-card">
      <template #header><div class="card-header">日常记录</div></template>
      <p>保存后的目标和睡眠会进入 AI 对话和健康报告。微信步数在首页同步后也会一起使用。</p>
      <div class="goal-grid">
        <label>每日步数<el-input-number v-model="goals.steps" :min="1000" :max="100000" :step="500" /></label>
        <label>运动分钟<el-input-number v-model="goals.exerciseMinutes" :min="10" :max="600" /></label>
        <label>饮水毫升<el-input-number v-model="goals.waterMl" :min="500" :max="8000" :step="100" /></label>
        <label>睡眠小时<el-input-number v-model="goals.sleepHours" :min="4" :max="12" /></label>
        <label>热量千卡<el-input-number v-model="goals.calories" :min="800" :max="6000" :step="50" /></label>
      </div>
      <el-button :loading="savingGoals" @click="saveGoals">保存目标</el-button>
      <div class="sleep-form">
        <el-date-picker v-model="sleepForm.date" type="date" value-format="YYYY-MM-DD" placeholder="睡眠日期" />
        <el-time-picker v-model="sleepForm.bedTime" format="HH:mm" value-format="HH:mm" placeholder="入睡" />
        <el-time-picker v-model="sleepForm.wakeTime" format="HH:mm" value-format="HH:mm" placeholder="醒来" />
        <el-select v-model="sleepForm.quality" placeholder="睡眠质量">
          <el-option v-for="item in sleepQualities" :key="item" :label="item" :value="item" />
        </el-select>
        <el-button :loading="savingSleep" @click="saveSleep">保存睡眠</el-button>
      </div>
    </el-card>
    <el-button text @click="$router.push('/app/ai-chat')">去 AI 问答</el-button>
    <HealthDataNotice v-if="!loading && noData" :message="overview?.message" />

    <template v-if="!loading && overview && !noData">
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
                <li v-for="item in risk.recommendations" :key="item">
                  {{ item }}
                  <el-button v-if="reminderDraft(item)" text @click="addReminder(item)">设为提醒</el-button>
                </li>
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
                <li v-for="item in overview?.nutritionAdvice?.recommendations || []" :key="item">{{ item }} <el-button v-if="reminderDraft(item)" text @click="addReminder(item)">设为提醒</el-button></li>
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
                <li v-for="item in overview?.exercisePlan?.weeklyPlan || []" :key="item">{{ item }} <el-button v-if="reminderDraft(item)" text @click="addReminder(item)">设为提醒</el-button></li>
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
              <span>{{ overview?.sleepInferred ? '睡眠（未记录，不是实测）' : '睡眠评分' }}</span>
              <strong>{{ overview?.sleepInferred ? '—' : (overview?.sleepInsight?.score ?? '--') }}</strong>
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
              <span>压力估计，不是测量</span>
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
    </template>
  </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import * as echarts from 'echarts'
import request from '../utils/request'
import { runtime } from '../config/runtime'
import { useUserStore } from '../store/user'
import { useAnalyticsStore } from '../store/analytics'
import { ElMessage } from 'element-plus'
import { reminderDraft, sleepMinutes } from '../utils/ai-assistant'
import { Opportunity } from '@element-plus/icons-vue'
import HealthDataNotice from '../components/HealthDataNotice.vue'

const userStore = useUserStore()
const analyticsStore = useAnalyticsStore()
const overview = ref(null)
const loading = ref(false)
const savingGoals = ref(false)
const savingSleep = ref(false)
const sleepQualities = ['优秀', '良好', '一般', '较差']
const today = new Date().toISOString().slice(0, 10)
const goals = ref({ steps: 10000, exerciseMinutes: 60, waterMl: 2000, sleepHours: 8, calories: 1800 })
const sleepForm = ref({ date: today, bedTime: '23:00', wakeTime: '07:00', quality: '良好' })
const noData = computed(() => overview.value?.dataStatus === 'NO_DATA')

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
    name: overview.value?.sleepInferred ? '睡眠未记录' : '睡眠恢复',
    value: overview.value?.sleepInferred ? 0 : Number(overview.value?.sleepInsight?.score || 0),
    color: '#10b981'
  },
  {
    name: '压力估计',
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
    instance = null
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
      formatter: ({ name, value }) => name === '睡眠未记录' ? `${name}<br/>没有睡眠记录` : name === '压力估计' ? `${name}<br/>估计 ${value} 分，不是测量` : `${name}<br/>${value} 分`
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

const refreshData = async (force = false) => {
  const userId = userStore.userInfo?.id
  if (!userId) { ElMessage.error('用户信息未就绪，请稍后重试'); return }
  loading.value = true

  try {
    // 并行获取数据
    const [overviewResponse, sportRecordsResponse] = await Promise.all([
      request.get('/smart-health/overview', {
        params: { userId, refresh: force },
        timeout: runtime.smartHealthTimeoutMs
      }),
      analyticsStore.fetchSportRecords(userId, { pageNum: 1, pageSize: 1000 })
    ])

    console.log('智能健康概览响应:', overviewResponse)
    console.log('运动记录响应:', sportRecordsResponse)

    if (overviewResponse && overviewResponse.data) {
      overview.value = overviewResponse.data
      console.log('设置后的 overview:', overview.value)
    } else {
      console.error('智能健康概览数据为空')
      ElMessage.warning('未能加载智能健康数据，请检查后端服务是否正常运行')
    }
  } catch (error) {
    overview.value = null
    console.error('加载智能健康数据失败:', error)
    ElMessage.error('加载智能健康数据失败：' + (error.message || '未知错误'))
  } finally {
    loading.value = false
  }
}

watch([riskData, recoveryMetrics, loading, noData], async () => {
  if (loading.value || noData.value) {
    riskChart?.dispose()
    recoveryChart?.dispose()
    riskChart = null
    recoveryChart = null
    return
  }
  await nextTick()
  renderRiskChart()
  renderRecoveryChart()
}, { deep: true })

const loadLifestyle = async () => {
  try {
    const [goalResponse, sleepResponse] = await Promise.all([
      request.get('/lifestyle/goals'),
      request.get('/lifestyle/sleep', { params: { limit: 1 } })
    ])
    if (goalResponse.code === 200 && goalResponse.data) goals.value = goalResponse.data
    const latest = sleepResponse.code === 200 ? sleepResponse.data?.[0] : null
    if (latest) sleepForm.value = { date: latest.date, bedTime: latest.bedTime, wakeTime: latest.wakeTime, quality: latest.quality }
  } catch (error) {
    console.error('读取日常记录失败', error)
  }
}
const saveGoals = async () => {
  savingGoals.value = true
  try {
    const response = await request.put('/lifestyle/goals', goals.value)
    if (response.code !== 200) throw new Error(response.message)
    ElMessage.success('每日目标已保存，下次分析会用到')
  } catch (error) {
    ElMessage.error(error.message || '保存目标失败')
  } finally { savingGoals.value = false }
}
const saveSleep = async () => {
  const minutes = sleepMinutes(sleepForm.value.bedTime, sleepForm.value.wakeTime)
  if (minutes < 30) { ElMessage.error('请填写有效的入睡和醒来时间'); return }
  savingSleep.value = true
  try {
    const response = await request.put('/lifestyle/sleep', { ...sleepForm.value, sleepMinutes: minutes })
    if (response.code !== 200) throw new Error(response.message)
    ElMessage.success('睡眠已保存，下次分析会用到')
  } catch (error) {
    ElMessage.error(error.message || '保存睡眠失败')
  } finally { savingSleep.value = false }
}
const addReminder = async (text) => {
  const draft = reminderDraft(text)
  if (!draft || !userStore.userInfo?.id) return
  try {
    const response = await request.post('/reminder/preferences', { ...draft, userId: userStore.userInfo.id })
    if (response.code !== 200) throw new Error(response.message)
    ElMessage.success('已添加到提醒，可在提醒页调整时间')
  } catch (error) {
    ElMessage.error(error.message || '添加提醒失败')
  }
}

onMounted(async () => {
  try {
    await Promise.all([refreshData(), loadLifestyle()])
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
.goal-grid, .sleep-form { display: flex; flex-wrap: wrap; gap: 12px; align-items: center; margin: 12px 0; }
.goal-grid label { display: flex; flex-direction: column; gap: 6px; color: #475569; font-size: 13px; }

/* Hero */
.page-hero {
  border: none;
  background: #fff;
  border-radius: 16px;
}

.page-hero-content {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  align-items: center;
  flex-wrap: wrap;
}

.hero-left {
  display: flex;
  align-items: flex-start;
  gap: 16px;
}

.hero-icon-box {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 52px;
  height: 52px;
  border-radius: 14px;
  background: #0d948818;
  flex-shrink: 0;
}

.hero-kicker {
  margin: 0 0 6px;
  font-size: 12px;
  letter-spacing: 0.14em;
  text-transform: uppercase;
  color: #0d9488;
  font-weight: 600;
}

.page-hero-content h2 {
  margin: 0 0 6px;
  font-size: 22px;
  color: #1E293B;
}

.hero-desc {
  margin: 0;
  color: #64748B;
  font-size: 14px;
  line-height: 1.6;
}

/* Analysis Loading */
.analysis-loading-card {
  border: none;
  overflow: hidden;
  background:
    radial-gradient(circle at top left, rgba(37, 99, 235, 0.08), transparent 36%),
    linear-gradient(135deg, #f8fafc, #fff 58%, #f0fdfa);
  border-radius: 16px;
}

.analysis-loading-shell {
  display: grid;
  grid-template-columns: minmax(0, 1.25fr) minmax(280px, 0.95fr);
  gap: 18px;
  align-items: stretch;
}

.analysis-loading-copy,
.analysis-loading-preview {
  border-radius: 16px;
}

.analysis-loading-copy {
  padding: 24px;
  background: rgba(255, 255, 255, 0.9);
}

.analysis-loading-eyebrow {
  margin: 0 0 10px;
  color: #2563eb;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.18em;
}

.analysis-loading-title-row {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
}

.analysis-loading-title-row h3 {
  margin: 0;
  color: #0f172a;
  font-size: 26px;
}

.analysis-loading-pulse {
  width: 12px;
  height: 12px;
  border-radius: 999px;
  background: #22c55e;
  box-shadow: 0 0 0 rgba(34, 197, 94, 0.45);
  animation: analysisPulse 1.6s ease-out infinite;
}

.analysis-loading-text {
  margin: 0;
  color: #475569;
  line-height: 1.7;
}

.analysis-loading-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 18px;
}

.analysis-loading-tags span {
  padding: 8px 12px;
  border-radius: 999px;
  background: rgba(37, 99, 235, 0.1);
  color: #1d4ed8;
  font-size: 13px;
  font-weight: 600;
}

.analysis-loading-preview {
  padding: 22px;
  border: 1px solid #F0F4F8;
  background: #F8FAFC;
}

.analysis-loading-preview-header {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: center;
  margin-bottom: 18px;
  color: #334155;
  font-size: 14px;
}

.analysis-loading-preview-header strong {
  color: #0f766e;
  font-size: 13px;
}

.analysis-loading-bars {
  display: grid;
  gap: 10px;
}

@keyframes analysisPulse {
  0% {
    transform: scale(1);
    box-shadow: 0 0 0 0 rgba(34, 197, 94, 0.45);
  }
  70% {
    transform: scale(1.05);
    box-shadow: 0 0 0 12px rgba(34, 197, 94, 0);
  }
  100% {
    transform: scale(1);
    box-shadow: 0 0 0 0 rgba(34, 197, 94, 0);
  }
}

/* Charts & Panels */
.chart-card {
  min-height: 420px;
  border-radius: 16px;
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
  color: #1E293B;
  display: flex;
  align-items: center;
  gap: 8px;
}

.risk-list {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  gap: 12px;
}

.panel-card {
  padding: 16px;
  border-radius: 12px;
  background: #F8FAFC;
  border: 1px solid #F0F4F8;
}

.panel-card.solid {
  background: #F8FAFC;
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
  color: #64748B;
}

.panel-card ul {
  margin: 0;
  padding-left: 18px;
  color: #475569;
}

@media (max-width: 960px) {
  .analysis-loading-shell {
    grid-template-columns: 1fr;
  }
  .analysis-loading-copy,
  .analysis-loading-preview {
    padding: 18px;
  }
  .analysis-loading-title-row h3 {
    font-size: 22px;
  }
}

/* 移动端适配 */
@media (max-width: 768px) {
  .page-hero-content {
    flex-direction: column;
    align-items: flex-start;
    gap: 14px;
  }
  .hero-left {
    flex-direction: column;
    align-items: flex-start;
  }
  .page-hero-content h2 {
    font-size: 20px;
  }
  .hero-desc {
    font-size: 13px;
  }
  .chart-card {
    min-height: auto;
  }
  .chart {
    height: 240px;
  }
  .risk-list {
    grid-template-columns: 1fr;
  }
  .panel-card {
    padding: 14px;
  }
  .panel-card ul {
    padding-left: 14px;
    font-size: 13px;
  }
  .card-header {
    font-size: 14px;
  }
}
</style>
