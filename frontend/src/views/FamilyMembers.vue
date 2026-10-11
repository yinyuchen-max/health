<template>
  <div class="family-page" v-loading="loading">
    <el-card class="hero" shadow="never">
      <div class="heading">
        <div><span class="eyebrow">FAMILY CARE</span><h2>家庭成员</h2><p>把关心带回家，了解家人的健康近况。</p></div>
        <el-button :disabled="busy" @click="load">刷新</el-button>
      </div>
      <el-alert type="info" :closable="false" show-icon title="手机号邀请需对方确认。同意后双方可以查看健康报告、发送关怀提醒，随时可解除绑定。" />
      <p class="hint">请家人先登录，并在个人资料中填写手机号。手机号用于查找账号。</p>
      <el-form class="invite-form" @submit.prevent="invite">
        <el-form-item label="家人手机号"><el-input v-model="phone" maxlength="11" inputmode="tel" placeholder="请输入家人的手机号" /></el-form-item>
        <el-form-item label="称呼"><el-input v-model="relationship" maxlength="20" placeholder="例如：爸爸、妈妈" /></el-form-item>
        <el-button type="primary" native-type="submit" :loading="busy" :disabled="loading">发送邀请</el-button>
      </el-form>
      <el-button text type="primary" @click="router.push('/app/profile')">填写我的手机号</el-button>
    </el-card>

    <el-alert v-if="loadError" :title="loadError" type="error" :closable="false" />
    <el-card v-if="pending.length" shadow="never">
      <h3>绑定邀请 <span class="count">{{ pending.length }}</span></h3>
      <div v-for="member in pending" :key="member.id" class="list-row">
        <div><strong>{{ member.memberName }}</strong><p>{{ member.maskedPhone }} · {{ member.incoming ? '邀请你成为家人' : '等待对方确认' }}</p><p class="hint">邀请称呼：{{ member.relationship }}</p></div>
        <div class="actions">
          <template v-if="member.incoming">
            <el-button type="primary" :disabled="busy" @click="decide(member, true)">同意</el-button>
            <el-button :disabled="busy" @click="decide(member, false)">拒绝</el-button>
          </template>
          <el-button v-else :disabled="busy" @click="remove(member)">撤回邀请</el-button>
        </div>
      </div>
    </el-card>

    <section><h3>我的家人 <span class="count">{{ active.length }}</span></h3>
      <el-empty v-if="!active.length && !loading && !loadError" description="还没有绑定家人，发送第一份邀请吧" />
      <div class="member-grid">
        <el-card v-for="member in active" :key="member.id" shadow="never">
          <div class="member-head"><el-avatar :size="48" class="avatar">{{ member.memberName.slice(0, 1) }}</el-avatar><div><strong>{{ member.incoming ? member.memberName : member.relationship + ' · ' + member.memberName }}</strong><p>{{ member.maskedPhone }}</p></div></div>
          <div class="actions"><el-button type="primary" :disabled="busy" @click="viewReport(member)">查看健康报告</el-button><el-button text type="danger" :disabled="busy" @click="remove(member)">解除绑定</el-button></div>
          <p class="hint">给家人发一份提醒</p>
          <div class="reminder-buttons"><el-button v-for="type in types" :key="type.value" :disabled="busy" @click="remind(member, type)">{{ type.label }}</el-button></div>
        </el-card>
      </div>
    </section>

    <el-card shadow="never">
      <div class="heading"><h3>收到的关怀提醒</h3><el-tag v-if="unread" type="warning">{{ unread }} 条未读</el-tag></div>
      <p class="hint">提醒在此页面接收，显示最近100条。标记完成不会自动生成测量或运动记录。</p>
      <el-empty v-if="!reminders.length && !loadError" description="暂无关怀提醒" :image-size="70" />
      <div v-for="item in reminders" :key="item.id" class="list-row">
        <div><strong>{{ item.senderName }} 提醒你{{ typeLabel(item.type) }}</strong><p>{{ formatTime(item.createdAt) }} · {{ item.completed ? '已完成' : item.readStatus ? '已读' : '未读' }}</p></div>
        <div class="actions"><el-button v-if="!item.readStatus" :disabled="busy" @click="acknowledge(item, false)">标记已读</el-button><el-button v-if="!item.completed" type="success" plain :disabled="busy" @click="acknowledge(item, true)">我已完成</el-button></div>
      </div>
    </el-card>

    <el-dialog v-model="reportVisible" :title="(selected?.memberName || '家人') + '的健康报告'" width="min(760px, 94vw)" @closed="clearReport">
      <div v-if="reportLoading" class="report-loading"><el-skeleton :rows="6" animated /><p>正在整理家人的健康报告，请稍候…</p></div>
      <el-alert v-else-if="reportError" :title="reportError" type="error" :closable="false" />
      <template v-else-if="report">
        <el-empty v-if="report.dataStatus === 'NO_DATA'" :description="report.message || '家人还没有健康记录'" />
        <template v-else>
          <div class="report-summary"><el-tag>{{ report.overallStatus || '健康概览' }}</el-tag><span v-if="report.bmi != null">BMI：{{ report.bmi }}</span><span>{{ formatTime(report.generatedAt) }}</span></div>
          <div v-for="risk in report.riskAssessments || []" :key="risk.assessmentType" class="report-panel">
            <strong>{{ riskLabels[risk.assessmentType] || risk.assessmentType }}</strong>
            <el-tag :type="risk.riskLevel === 'HIGH' ? 'danger' : risk.riskLevel === 'MEDIUM' ? 'warning' : 'success'">{{ levels[risk.riskLevel] || risk.riskLevel }}风险 · {{ risk.riskScore }}</el-tag>
            <p>{{ risk.summary }}</p><ul><li v-for="tip in risk.recommendations || []" :key="tip">{{ tip }}</li></ul>
          </div>
          <div v-if="report.nutritionAdvice" class="report-panel"><strong>营养建议</strong><p>{{ report.nutritionAdvice.summary }}</p><p v-if="report.nutritionAdvice.dailyCalories">每日热量建议：{{ report.nutritionAdvice.dailyCalories }} kcal</p><ul><li v-for="tip in report.nutritionAdvice.recommendations || []" :key="tip">{{ tip }}</li></ul></div>
          <div v-if="report.exercisePlan" class="report-panel"><strong>运动计划</strong><p>{{ report.exercisePlan.goal }} · {{ report.exercisePlan.intensity }}</p><p>每周目标：{{ report.exercisePlan.weeklyMinutesTarget }} 分钟</p><ul><li v-for="tip in report.exercisePlan.weeklyPlan || []" :key="tip">{{ tip }}</li></ul></div>
          <div v-for="entry in insights" :key="entry.label" class="report-panel"><strong>{{ entry.label }}</strong><p>{{ entry.value.summary }}</p><ul><li v-for="tip in entry.value.recommendations || []" :key="tip">{{ tip }}</li></ul></div>
          <div v-if="report.quickTips?.length" class="report-panel"><strong>日常建议</strong><ul><li v-for="tip in report.quickTips" :key="tip">{{ tip }}</li></ul></div>
          <p class="hint">报告来自家人已记录的数据与智能分析，仅供健康管理参考。</p>
        </template>
      </template>
      <template #footer><el-button v-if="reportError" @click="viewReport(selected)">重试</el-button><el-button @click="reportVisible = false">关闭</el-button></template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '../utils/request'
import { runtime } from '../config/runtime'

const router = useRouter()
const members = ref([]), reminders = ref([]), loading = ref(false), busy = ref(false), loadError = ref('')
const phone = ref(''), relationship = ref('家人')
const pending = computed(() => members.value.filter(m => m.status === 'PENDING'))
const active = computed(() => members.value.filter(m => m.status === 'ACTIVE'))
const unread = computed(() => reminders.value.filter(r => !r.readStatus).length)
const types = [{ value: 'bloodPressure', label: '量血压' }, { value: 'exercise', label: '运动' }, { value: 'bloodSugar', label: '测血糖' }, { value: 'weight', label: '记体重' }]
const typeLabel = value => types.find(t => t.value === value)?.label || value
const formatTime = value => value ? String(value).replace('T', ' ').slice(0, 19) : ''
const riskLabels = { BMI: 'BMI风险', BLOOD_PRESSURE: '血压风险', DIABETES: '血糖风险', CARDIO: '心血管风险' }
const levels = { HIGH: '高', MEDIUM: '中', LOW: '低' }
const reportVisible = ref(false), selected = ref(null), report = ref(null), reportLoading = ref(false), reportError = ref('')
const insights = computed(() => [{ label: '睡眠分析', value: report.value?.sleepInsight }, { label: '压力分析', value: report.value?.stressInsight }].filter(i => i.value))
let reportSequence = 0, loadSequence = 0
const unwrap = response => {
  if (response.code !== 200 && response.code !== 0) throw new Error(response.message || '请求失败')
  return response.data
}
async function load() {
  const sequence = ++loadSequence
  loading.value = true
  loadError.value = ''
  try {
    const [a, b] = await Promise.all([request.get('/family/members'), request.get('/family/reminders')])
    if (sequence !== loadSequence) return
    members.value = unwrap(a) || []
    reminders.value = unwrap(b) || []
    if (selected.value && !active.value.some(m => m.id === selected.value.id)) { reportVisible.value = false; clearReport() }
  } catch (e) {
    if (sequence === loadSequence) { members.value = []; reminders.value = []; loadError.value = e.message || '加载失败，请刷新重试'; reportVisible.value = false; clearReport() }
  } finally { if (sequence === loadSequence) loading.value = false }
}
async function mutate(action, message) {
  if (busy.value) return
  busy.value = true
  try { unwrap(await action()); ElMessage.success(message); await load() }
  catch (e) { ElMessage.error(e.message || '操作失败') }
  finally { busy.value = false }
}
async function confirm(message, title) {
  try { await ElMessageBox.confirm(message, title, { confirmButtonText: '确认', cancelButtonText: '取消' }); return true } catch { return false }
}
async function invite() {
  if (!/^1[3-9]\d{9}$/.test(phone.value.trim())) return ElMessage.warning('请输入有效的11位手机号')
  if (!relationship.value.trim()) return ElMessage.warning('请填写对家人的称呼')
  const payload = { phone: phone.value.trim(), relationship: relationship.value.trim() }
  await mutate(() => request.post('/family/invitations', payload), '邀请已发送，请等待家人确认')
}
async function decide(member, accept) {
  if (accept && !await confirm(`确认认识“${member.memberName}”（${member.maskedPhone}）吗？同意后，你们可以互相查看健康报告、发送提醒。`, '确认健康报告共享')) return
  await mutate(() => request.put(`/family/invitations/${member.id}`, { accept }), accept ? '已绑定家人' : '已拒绝邀请')
}
async function remove(member) {
  if (!await confirm(member.status === 'ACTIVE' ? '解除后双方将无法继续查看健康报告或发送新提醒。' : '确定撤回这份邀请吗？', '确认操作')) return
  await mutate(() => request.delete(`/family/members/${member.id}`), member.status === 'ACTIVE' ? '已解除绑定' : '已撤回邀请')
}
async function remind(member, type) {
  if (!await confirm(`提醒“${member.memberName}”${type.label}？对方可在家庭成员页面查看，同类提醒30分钟内只能发送一次。`, '发送关怀提醒')) return
  await mutate(() => request.post(`/family/members/${member.id}/reminders`, { type: type.value }), '提醒已发送')
}
async function acknowledge(item, complete) {
  await mutate(() => request.put(`/family/reminders/${item.id}/${complete ? 'complete' : 'read'}`), complete ? '已标记完成' : '已标记已读')
}
function clearReport() { ++reportSequence; report.value = null; selected.value = null; reportLoading.value = false; reportError.value = '' }
async function viewReport(member) {
  if (!member) return
  const sequence = ++reportSequence
  selected.value = member; report.value = null; reportError.value = ''; reportVisible.value = true; reportLoading.value = true
  try {
    const data = unwrap(await request.get(`/family/members/${member.id}/report`, { timeout: runtime.smartHealthTimeoutMs }))
    if (sequence === reportSequence && reportVisible.value) report.value = data
  } catch (e) { if (sequence === reportSequence) reportError.value = e.message || '报告加载失败，请重试' }
  finally { if (sequence === reportSequence) reportLoading.value = false }
}
onMounted(load)
onBeforeUnmount(() => { ++loadSequence; clearReport() })
</script>

<style scoped>
.family-page { display: grid; gap: 20px; color: #1e293b; }
.family-page :deep(.el-card) { border: 0; border-radius: 16px; }
.heading, .member-head, .list-row { display: flex; align-items: center; justify-content: space-between; gap: 16px; }
.member-head { justify-content: flex-start; margin-bottom: 20px; }
.heading { margin-bottom: 16px; }
.eyebrow { color: #0d9488; font-size: 12px; letter-spacing: 2px; font-weight: 700; }
h2 { margin: 8px 0; } h3 { margin: 0 0 16px; }
p, li { color: #64748b; line-height: 1.7; } p { margin: 6px 0; }
.hint { font-size: 13px; } .count { color: #0d9488; font-weight: 400; }
.invite-form { display: flex; flex-wrap: wrap; gap: 12px; margin-top: 20px; align-items: flex-start; }
.member-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(min(100%, 340px), 1fr)); gap: 16px; }
.avatar { background: #ccfbf1; color: #0f766e; font-weight: 700; flex-shrink: 0; }
.actions, .reminder-buttons { display: flex; flex-wrap: wrap; gap: 8px; }
.family-page :deep(.el-button + .el-button) { margin-left: 0; }
.list-row { padding: 16px 0; border-top: 1px solid #f1f5f9; }
.report-summary { display: flex; flex-wrap: wrap; gap: 16px; align-items: center; color: #64748b; }
.report-panel { padding: 16px; background: #f8fafc; border-radius: 12px; margin-top: 14px; }
.report-panel .el-tag { margin-left: 10px; } .report-panel ul { padding-left: 20px; margin-bottom: 0; }
.report-loading { padding: 20px 0; }
@media (max-width: 600px) { .list-row { align-items: flex-start; flex-direction: column; } .invite-form { display: grid; } .invite-form .el-form-item { margin-bottom: 0; } }
</style>
