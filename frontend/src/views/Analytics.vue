<template>
  <div class="analytics-page">
    <!-- 顶部总览卡片 -->
    <el-card class="hero-card">
      <div class="hero-content">
        <div class="hero-copy">
          <div class="hero-icon-box">
            <el-icon :size="22" color="#3B82F6"><DataLine /></el-icon>
          </div>
          <div>
            <h2>数据分析总览</h2>
            <p class="hero-desc">
              汇总健康记录、运动记录和智能分析结果，集中查看趋势、风险与个性化建议。
            </p>
          </div>
        </div>
        <div class="hero-meta">
          <div class="meta-item">
            <span>整体状态</span>
            <strong>{{ overview?.overallStatus || '--' }}</strong>
          </div>
          <div class="meta-item">
            <span>分析时间</span>
            <strong>{{ generatedAtText }}</strong>
          </div>
          <el-button type="primary" :loading="refreshing" @click="loadData" round>
            <el-icon><Refresh /></el-icon>刷新分析
          </el-button>
        </div>
      </div>
    </el-card>

    <!-- 指标卡片 -->
    <el-row :gutter="16">
      <el-col :xs="24" :sm="12" :lg="8" :xl="4" v-for="item in metricCards" :key="item.label">
        <el-card class="metric-card">
          <div class="metric-label">{{ item.label }}</div>
          <div class="metric-value">{{ item.value }}</div>
          <div class="metric-note">{{ item.note }}</div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 图表区 -->
    <el-row :gutter="16">
      <el-col :xs="24" :xl="14">
        <el-card class="chart-card">
          <template #header>
            <div class="card-header">
              <div class="card-header-icon" style="background:#EFF6FF;"><el-icon :size="16" color="#3B82F6"><TrendCharts /></el-icon></div>
              体征趋势分析
            </div>
          </template>
          <el-empty v-if="!healthTrend.length" description="暂无健康记录" />
          <div v-else ref="healthMetricsRef" class="chart"></div>
        </el-card>
      </el-col>
      <el-col :xs="24" :xl="10">
        <el-card class="chart-card">
          <template #header>
            <div class="card-header">
              <div class="card-header-icon" style="background:#FEF2F2;"><el-icon :size="16" color="#EF4444"><Warning /></el-icon></div>
              风险评分对比
            </div>
          </template>
          <el-empty v-if="!risks.length" description="暂无风险评估" />
          <div v-else ref="riskBarRef" class="chart"></div>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="16">
      <el-col :xs="24" :xl="12">
        <el-card class="chart-card">
          <template #header>
            <div class="card-header">
              <div class="card-header-icon" style="background:#F0FDF4;"><el-icon :size="16" color="#22C55E"><Stopwatch /></el-icon></div>
              运动时长趋势
            </div>
          </template>
          <el-empty v-if="!exerciseTrend.length" description="暂无运动记录" />
          <div v-else ref="exerciseTrendRef" class="chart"></div>
        </el-card>
      </el-col>
      <el-col :xs="24" :xl="12">
        <el-card class="chart-card">
          <template #header>
            <div class="card-header">
              <div class="card-header-icon" style="background:#FFFBEB;"><el-icon :size="16" color="#F59E0B"><PieChart /></el-icon></div>
              运动类型分布
            </div>
          </template>
          <el-empty v-if="!sportDistribution.length" description="暂无运动记录" />
          <div v-else ref="sportDistributionRef" class="chart"></div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 面板区 -->
    <el-row :gutter="16">
      <el-col :xs="24" :lg="8">
        <el-card class="panel-card">
          <template #header>
            <div class="card-header">
              <div class="card-header-icon" style="background:#EFF6FF;"><el-icon :size="16" color="#3B82F6"><Goal /></el-icon></div>
              周目标进度
            </div>
          </template>
          <div class="progress-panel">
            <div class="summary-line"><span>已完成运动</span><strong>{{ weeklyExerciseMinutes }} 分钟</strong></div>
            <div class="summary-line"><span>本周目标</span><strong>{{ weeklyExerciseTarget }} 分钟</strong></div>
            <div class="summary-line"><span>达成率</span><strong>{{ exerciseCompletionRate }}%</strong></div>
            <el-progress :percentage="exerciseCompletionRate" :stroke-width="12" :show-text="false" />
            <p>{{ overview?.exercisePlan?.goal || '等待分析' }}</p>
          </div>
        </el-card>
      </el-col>
      <el-col :xs="24" :lg="8">
        <el-card class="panel-card">
          <template #header>
            <div class="card-header">
              <div class="card-header-icon" style="background:#F0FDF4;"><el-icon :size="16" color="#22C55E"><Moon /></el-icon></div>
              恢复状态
            </div>
          </template>
          <div class="content-panel">
            <div class="summary-line"><span>睡眠评分</span><strong>{{ overview?.sleepInsight?.score ?? '--' }}</strong></div>
            <div class="summary-line"><span>压力评分</span><strong>{{ overview?.stressInsight?.score ?? '--' }}</strong></div>
            <div class="summary-line"><span>压力等级</span><strong>{{ stressLevelText }}</strong></div>
            <p>{{ overview?.sleepInsight?.summary || '暂无睡眠分析' }}</p>
          </div>
        </el-card>
      </el-col>
      <el-col :xs="24" :lg="8">
        <el-card class="panel-card">
          <template #header>
            <div class="card-header">
              <div class="card-header-icon" style="background:#FFFBEB;"><el-icon :size="16" color="#F59E0B"><Document /></el-icon></div>
              最近摘要
            </div>
          </template>
          <div class="content-panel">
            <div class="summary-line"><span>最新健康记录</span><strong>{{ latestHealthRecord?.recordDate || '--' }}</strong></div>
            <div class="summary-line"><span>最新运动记录</span><strong>{{ latestSportRecord?.recordDate || '--' }}</strong></div>
            <div class="summary-line"><span>最新血压</span><strong>{{ latestBloodPressure }}</strong></div>
            <div class="summary-line"><span>最新心率</span><strong>{{ latestHeartRate }}</strong></div>
            <div class="summary-line"><span>最新血糖</span><strong>{{ latestBloodSugar }}</strong></div>
            <div class="summary-line"><span>最新体重</span><strong>{{ latestWeight }}</strong></div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 提示与风险 -->
    <el-row :gutter="16">
      <el-col :xs="24" :xl="10">
        <el-card class="panel-card">
          <template #header>
            <div class="card-header">
              <div class="card-header-icon" style="background:#EEF2FF;"><el-icon :size="16" color="#6366F1"><ChatDotSquare /></el-icon></div>
              快速提示
            </div>
          </template>
          <el-empty v-if="!quickTips.length" description="暂无分析提示" />
          <div v-else class="tip-list">
            <div v-for="tip in quickTips" :key="tip" class="tip-item">{{ tip }}</div>
          </div>
        </el-card>
      </el-col>
      <el-col :xs="24" :xl="14">
        <el-card class="panel-card">
          <template #header>
            <div class="card-header">
              <div class="card-header-icon" style="background:#FEF2F2;"><el-icon :size="16" color="#EF4444"><Warning /></el-icon></div>
              风险明细
            </div>
          </template>
          <el-empty v-if="!risks.length" description="暂无风险评估" />
          <div v-else class="risk-grid">
            <div v-for="risk in sortedRisks" :key="risk.assessmentType" class="risk-item">
              <div class="risk-head">
                <strong>{{ riskLabels[risk.assessmentType] || risk.assessmentType }}</strong>
                <el-tag :type="tagType(risk.riskLevel)" size="small">{{ levelText(risk.riskLevel) }}</el-tag>
              </div>
              <div class="summary-line compact"><span>风险分数</span><strong>{{ risk.riskScore ?? '--' }}</strong></div>
              <p>{{ risk.summary || '暂无说明' }}</p>
              <ul><li v-for="item in risk.recommendations || []" :key="item">{{ item }}</li></ul>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 饮食 & 运动 -->
    <el-row :gutter="16">
      <el-col :xs="24" :lg="12">
        <el-card class="panel-card">
          <template #header><div class="card-header"><div class="card-header-icon" style="background:#F0FDF4;"><el-icon :size="16" color="#22C55E"><Bowl /></el-icon></div>饮食建议</div></template>
          <div class="content-panel">
            <div class="summary-line"><span>建议热量</span><strong>{{ overview?.nutritionAdvice?.dailyCalories ?? '--' }} kcal</strong></div>
            <div class="summary-line"><span>近 7 天消耗</span><strong>{{ weeklyCalories }} kcal</strong></div>
            <p>{{ overview?.nutritionAdvice?.summary || '暂无饮食建议' }}</p>
            <ul><li v-for="item in overview?.nutritionAdvice?.recommendations || []" :key="item">{{ item }}</li></ul>
          </div>
        </el-card>
      </el-col>
      <el-col :xs="24" :lg="12">
        <el-card class="panel-card">
          <template #header><div class="card-header"><div class="card-header-icon" style="background:#EFF6FF;"><el-icon :size="16" color="#3B82F6"><D-arrow-right /></el-icon></div>运动计划</div></template>
          <div class="content-panel">
            <div class="summary-line"><span>目标时长</span><strong>{{ overview?.exercisePlan?.weeklyMinutesTarget ?? '--' }} 分钟</strong></div>
            <div class="summary-line"><span>训练目标</span><strong>{{ overview?.exercisePlan?.goal || '--' }}</strong></div>
            <div class="summary-line"><span>建议强度</span><strong>{{ overview?.exercisePlan?.intensity || '--' }}</strong></div>
            <ul><li v-for="item in overview?.exercisePlan?.weeklyPlan || []" :key="item">{{ item }}</li></ul>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 睡眠 & 压力 -->
    <el-row :gutter="16">
      <el-col :xs="24" :lg="12">
        <el-card class="panel-card">
          <template #header><div class="card-header"><div class="card-header-icon" style="background:#EEF2FF;"><el-icon :size="16" color="#6366F1"><Moon /></el-icon></div>睡眠分析</div></template>
          <div class="content-panel">
            <div class="summary-line"><span>睡眠评分</span><strong>{{ overview?.sleepInsight?.score ?? '--' }}</strong></div>
            <p>{{ overview?.sleepInsight?.summary || '暂无睡眠分析' }}</p>
            <ul><li v-for="item in overview?.sleepInsight?.recommendations || []" :key="item">{{ item }}</li></ul>
          </div>
        </el-card>
      </el-col>
      <el-col :xs="24" :lg="12">
        <el-card class="panel-card">
          <template #header><div class="card-header"><div class="card-header-icon" style="background:#FFFBEB;"><el-icon :size="16" color="#F59E0B"><Lightning /></el-icon></div>压力分析</div></template>
          <div class="content-panel">
            <div class="summary-line"><span>压力评分</span><strong>{{ overview?.stressInsight?.score ?? '--' }}</strong></div>
            <div class="summary-line"><span>压力等级</span><strong>{{ stressLevelText }}</strong></div>
            <p>{{ overview?.stressInsight?.summary || '暂无压力分析' }}</p>
            <ul><li v-for="item in overview?.stressInsight?.recommendations || []" :key="item">{{ item }}</li></ul>
          </div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import * as echarts from 'echarts'
import { ElMessage } from 'element-plus'
import request from '../utils/request'
import { useUserStore } from '../store/user'
import { useAnalyticsStore } from '../store/analytics'
import {
  DataLine, TrendCharts, Warning, Stopwatch, PieChart,
  Document, ChatDotSquare, Bowl, Moon, Lightning, Refresh
} from '@element-plus/icons-vue'

const userStore = useUserStore()
const analyticsStore = useAnalyticsStore()
const overview = ref(null)
const refreshing = ref(false)

const healthMetricsRef = ref(null)
const riskBarRef = ref(null)
const exerciseTrendRef = ref(null)
const sportDistributionRef = ref(null)
let healthMetricsChart = null, riskBarChart = null, exerciseTrendChart = null, sportDistributionChart = null

const riskLabels = { BMI: 'BMI 风险', BLOOD_PRESSURE: '血压风险', DIABETES: '血糖风险', CARDIO: '心血管风险' }

const sortedHealthRecords = computed(() =>
  [...analyticsStore.healthRecords].filter(i => i.recordDate).sort((a, b) => new Date(b.recordDate) - new Date(a.recordDate))
)
const sortedSportRecords = computed(() =>
  [...analyticsStore.sportRecords].filter(i => i.recordDate).sort((a, b) => new Date(b.recordDate) - new Date(a.recordDate))
)
const latestHealthRecord = computed(() => sortedHealthRecords.value[0] || null)
const latestSportRecord = computed(() => sortedSportRecords.value[0] || null)

const healthTrend = computed(() =>
  [...sortedHealthRecords.value].sort((a, b) => new Date(a.recordDate) - new Date(b.recordDate)).slice(-12)
)
const exerciseTrend = computed(() =>
  [...sortedSportRecords.value].sort((a, b) => new Date(a.recordDate) - new Date(b.recordDate)).slice(-12)
)
const sportDistribution = computed(() => {
  const g = analyticsStore.sportRecords.reduce((a, i) => { const k = i.sportType || '其他'; a[k] = (a[k] || 0) + Number(i.duration || 0); return a }, {})
  return Object.entries(g).map(([n, v]) => ({ name: n, value: v })).sort((a, b) => b.value - a.value)
})

const risks = computed(() => overview.value?.riskAssessments || [])
const sortedRisks = computed(() => [...risks.value].sort((a, b) => Number(b.riskScore || 0) - Number(a.riskScore || 0)))
const quickTips = computed(() => overview.value?.quickTips || [])

const weeklyExerciseMinutes = computed(() => {
  const t = new Date(); t.setDate(t.getDate() - 6)
  return analyticsStore.sportRecords.filter(r => r.recordDate && new Date(r.recordDate) >= t).reduce((s, r) => s + Number(r.duration || 0), 0)
})
const weeklyCalories = computed(() => {
  const t = new Date(); t.setDate(t.getDate() - 6)
  return analyticsStore.sportRecords.filter(r => r.recordDate && new Date(r.recordDate) >= t).reduce((s, r) => s + Number(r.calories || 0), 0)
})
const weeklyExerciseTarget = computed(() => Number(overview.value?.exercisePlan?.weeklyMinutesTarget || 0))
const exerciseCompletionRate = computed(() => {
  if (!weeklyExerciseTarget.value) return 0
  return Math.min(100, Math.round((weeklyExerciseMinutes.value / weeklyExerciseTarget.value) * 100))
})
const averageRiskScore = computed(() => {
  if (!risks.value.length) return '--'
  return (risks.value.reduce((s, i) => s + Number(i.riskScore || 0), 0) / risks.value.length).toFixed(1)
})
const highRiskCount = computed(() => risks.value.filter(i => i.riskLevel === 'HIGH').length)

const latestBloodPressure = computed(() => {
  const r = latestHealthRecord.value
  if (!r?.bloodPressureSystolic || !r?.bloodPressureDiastolic) return '--'
  return `${r.bloodPressureSystolic}/${r.bloodPressureDiastolic} mmHg`
})
const latestHeartRate = computed(() => latestHealthRecord.value?.heartRate != null ? `${latestHealthRecord.value.heartRate} bpm` : '--')
const latestBloodSugar = computed(() => latestHealthRecord.value?.bloodSugar != null ? `${latestHealthRecord.value.bloodSugar} mmol/L` : '--')
const latestWeight = computed(() => latestHealthRecord.value?.weight != null ? `${latestHealthRecord.value.weight} kg` : '--')
const stressLevelText = computed(() => levelText(overview.value?.stressInsight?.level))
const generatedAtText = computed(() => {
  const v = overview.value?.generatedAt; if (!v) return '--'
  const d = new Date(v); return Number.isNaN(d.getTime()) ? v : d.toLocaleString('zh-CN')
})

const metricCards = computed(() => [
  { label: '健康记录数', value: analyticsStore.healthRecords.length, note: '累计健康监测条目' },
  { label: '运动记录数', value: analyticsStore.sportRecords.length, note: '累计运动记录条目' },
  { label: 'BMI', value: overview.value?.bmi ?? '--', note: overview.value?.overallStatus || '等待分析' },
  { label: '平均风险分', value: averageRiskScore.value, note: `高风险项 ${highRiskCount.value} 个` },
  { label: '近 7 天运动', value: `${weeklyExerciseMinutes.value} 分钟`, note: `目标 ${weeklyExerciseTarget.value || '--'} 分钟` },
  { label: '最新体重', value: latestWeight.value, note: latestHealthRecord.value?.recordDate || '暂无记录' }
])

const tagType = (l) => l === 'HIGH' ? 'danger' : l === 'MEDIUM' ? 'warning' : 'success'
const levelText = (l) => l === 'HIGH' ? '高' : l === 'MEDIUM' ? '中' : l === 'LOW' ? '低' : '--'

const disposeCharts = () => {
  healthMetricsChart?.dispose(); riskBarChart?.dispose(); exerciseTrendChart?.dispose(); sportDistributionChart?.dispose()
  healthMetricsChart = riskBarChart = exerciseTrendChart = sportDistributionChart = null
}

const renderHealthMetricsChart = () => {
  if (!healthMetricsRef.value || !healthTrend.value.length) return
  if (healthMetricsChart && healthMetricsChart.getDom() !== healthMetricsRef.value) { healthMetricsChart.dispose(); healthMetricsChart = null }
  if (!healthMetricsChart) healthMetricsChart = echarts.init(healthMetricsRef.value)
  healthMetricsChart.setOption({
    tooltip: { trigger: 'axis' },
    legend: { data: ['体重', '心率', '血糖'], top: 0 },
    grid: { left: 50, right: 42, top: 48, bottom: 36 },
    xAxis: { type: 'category', data: healthTrend.value.map(i => i.recordDate) },
    yAxis: [{ type: 'value', name: 'kg / mmol/L', position: 'left' }, { type: 'value', name: 'bpm', position: 'right' }],
    series: [
      { name: '体重', type: 'line', smooth: true, data: healthTrend.value.map(i => i.weight ?? null), lineStyle: { color: '#0ea5e9' }, itemStyle: { color: '#0ea5e9' } },
      { name: '心率', type: 'line', smooth: true, yAxisIndex: 1, data: healthTrend.value.map(i => i.heartRate ?? null), lineStyle: { color: '#ef4444' }, itemStyle: { color: '#ef4444' } },
      { name: '血糖', type: 'bar', data: healthTrend.value.map(i => i.bloodSugar ?? null), itemStyle: { color: '#f59e0b' } }
    ]
  })
}

const renderRiskBarChart = () => {
  if (!riskBarRef.value || !risks.value.length) return
  if (riskBarChart && riskBarChart.getDom() !== riskBarRef.value) { riskBarChart.dispose(); riskBarChart = null }
  if (!riskBarChart) riskBarChart = echarts.init(riskBarRef.value)
  riskBarChart.setOption({
    tooltip: { trigger: 'axis' },
    grid: { left: 86, right: 24, top: 20, bottom: 24 },
    xAxis: { type: 'value', max: 100 },
    yAxis: { type: 'category', data: sortedRisks.value.map(i => riskLabels[i.assessmentType] || i.assessmentType) },
    series: [{
      type: 'bar', barWidth: 18,
      data: sortedRisks.value.map(i => ({ value: i.riskScore, itemStyle: { color: i.riskLevel === 'HIGH' ? '#ef4444' : i.riskLevel === 'MEDIUM' ? '#f59e0b' : '#22c55e' } })),
      label: { show: true, position: 'right' }
    }]
  })
}

const renderExerciseTrendChart = () => {
  if (!exerciseTrendRef.value || !exerciseTrend.value.length) return
  if (exerciseTrendChart && exerciseTrendChart.getDom() !== exerciseTrendRef.value) { exerciseTrendChart.dispose(); exerciseTrendChart = null }
  if (!exerciseTrendChart) exerciseTrendChart = echarts.init(exerciseTrendRef.value)
  exerciseTrendChart.setOption({
    tooltip: { trigger: 'axis' },
    legend: { data: ['运动时长', '消耗热量'], top: 0 },
    grid: { left: 48, right: 42, top: 48, bottom: 36 },
    xAxis: { type: 'category', data: exerciseTrend.value.map(i => i.recordDate) },
    yAxis: [{ type: 'value', name: '分钟' }, { type: 'value', name: 'kcal' }],
    series: [
      { name: '运动时长', type: 'bar', data: exerciseTrend.value.map(i => i.duration ?? 0), itemStyle: { color: '#2563eb' } },
      { name: '消耗热量', type: 'line', smooth: true, yAxisIndex: 1, data: exerciseTrend.value.map(i => i.calories ?? 0), lineStyle: { color: '#10b981' }, itemStyle: { color: '#10b981' } }
    ]
  })
}

const renderSportDistributionChart = () => {
  if (!sportDistributionRef.value || !sportDistribution.value.length) return
  if (sportDistributionChart && sportDistributionChart.getDom() !== sportDistributionRef.value) { sportDistributionChart.dispose(); sportDistributionChart = null }
  if (!sportDistributionChart) sportDistributionChart = echarts.init(sportDistributionRef.value)
  sportDistributionChart.setOption({
    tooltip: { trigger: 'item', formatter: '{b}: {c} 分钟 ({d}%)' },
    legend: { orient: 'vertical', right: 0, top: 'middle' },
    series: [{ type: 'pie', radius: ['36%', '66%'], center: ['38%', '50%'], itemStyle: { borderColor: '#fff', borderWidth: 3 }, data: sportDistribution.value }]
  })
}

const renderCharts = async () => { await nextTick(); renderHealthMetricsChart(); renderRiskBarChart(); renderExerciseTrendChart(); renderSportDistributionChart() }
const resizeCharts = () => { healthMetricsChart?.resize(); riskBarChart?.resize(); exerciseTrendChart?.resize(); sportDistributionChart?.resize() }

const loadData = async () => {
  refreshing.value = true
  try {
    const userId = userStore.userInfo?.id || 1
    await Promise.all([
      analyticsStore.fetchHealthRecords(userId, { pageNum: 1, pageSize: 1000 }),
      analyticsStore.fetchSportRecords(userId, { pageNum: 1, pageSize: 1000 })
    ])
    const response = await request.get('/smart-health/overview', { params: { userId } })
    overview.value = response.data
    await renderCharts()
  } catch (error) {
    console.error('加载分析页失败', error)
    ElMessage.error('加载分析页失败')
  } finally { refreshing.value = false }
}

watch([healthTrend, exerciseTrend, sportDistribution, risks], renderCharts, { deep: true })
onMounted(async () => { await loadData(); window.addEventListener('resize', resizeCharts) })
onBeforeUnmount(() => { window.removeEventListener('resize', resizeCharts); disposeCharts() })
</script>

<style scoped>
.analytics-page { display: flex; flex-direction: column; gap: 16px; }

.hero-card { border: none; background: #fff; border-radius: 16px; }
.hero-content { display: flex; justify-content: space-between; gap: 24px; align-items: center; flex-wrap: wrap; }
.hero-copy { display: flex; align-items: center; gap: 14px; }
.hero-icon-box {
  width: 44px; height: 44px; border-radius: 12px; background: #EFF6FF;
  display: flex; align-items: center; justify-content: center; flex-shrink: 0;
}
.hero-copy h2 { margin: 0; font-size: 22px; color: #1E293B; }
.hero-desc { margin: 6px 0 0; max-width: 760px; line-height: 1.7; color: #64748B; font-size: 14px; }

.hero-meta { display: flex; gap: 12px; align-items: stretch; flex-wrap: wrap; }
.meta-item {
  min-width: 120px; padding: 10px 14px; border-radius: 12px;
  background: #F8FAFC; border: 1px solid #F0F4F8;
}
.meta-item span { display: block; margin-bottom: 4px; font-size: 12px; color: #94A3B8; }
.meta-item strong { font-size: 15px; color: #1E293B; }

.metric-card { min-height: 110px; border-radius: 16px; }
.metric-label, .metric-note { color: #64748B; font-size: 13px; }
.metric-value { margin: 10px 0 6px; font-size: 28px; font-weight: 700; color: #1E293B; }

.card-header { display: flex; align-items: center; gap: 8px; font-weight: 600; font-size: 15px; color: #1E293B; }
.card-header-icon {
  width: 30px; height: 30px; border-radius: 8px;
  display: flex; align-items: center; justify-content: center;
}

.chart-card, .panel-card { border-radius: 16px; min-height: 100%; }
.chart { width: 100%; height: 340px; }

.progress-panel, .content-panel { padding: 4px 0; }
.summary-line {
  display: flex; justify-content: space-between; gap: 12px; align-items: center;
  padding: 10px 0; border-bottom: 1px solid #F0F4F8;
}
.summary-line.compact { padding: 8px 0; }
.summary-line:first-child { padding-top: 0; }

.progress-panel p, .content-panel p { margin: 12px 0 0; line-height: 1.7; color: #475569; }
.content-panel ul, .risk-item ul { margin: 10px 0 0; padding-left: 18px; color: #334155; }

.tip-list { display: flex; flex-direction: column; gap: 10px; }
.tip-item {
  padding: 12px 14px; border-radius: 12px;
  background: #F8FAFC; border: 1px solid #F0F4F8;
  color: #334155; line-height: 1.7; font-size: 14px;
}

.risk-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(220px, 1fr)); gap: 12px; }
.risk-item { padding: 14px; border-radius: 14px; background: #F8FAFC; border: 1px solid #F0F4F8; }
.risk-head { display: flex; justify-content: space-between; gap: 10px; align-items: center; }
.risk-item p { margin: 10px 0 0; line-height: 1.7; color: #475569; }

@media (max-width: 768px) {
  .hero-copy h2 { font-size: 18px; }
  .chart { height: 280px; }
  .hero-meta { flex-direction: column; }
}
</style>
