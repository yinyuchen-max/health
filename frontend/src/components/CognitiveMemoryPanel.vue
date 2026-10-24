<template>
  <section class="cognitive-panel" v-loading="loading" aria-label="记忆经历与演化">
    <div class="panel-header"><h4>记忆经历与演化</h4><el-button size="small" :disabled="loading || busy" @click="load">刷新</el-button></div>
    <p class="hint">经历保留具体场景，知识汇总稳定事实。检索反馈会逐步调整后续记忆的筛选和召回；你的明确修改优先。</p>
    <p v-if="pendingJobs" class="hint">有 {{ pendingJobs }} 项记忆任务正在排队或处理，稍后刷新查看。</p>
    <el-alert v-if="failedJobs" type="warning" :closable="false" :title="`${failedJobs} 项记忆处理未成功，不影响聊天。可重新发送需要记住的信息。`" />
    <el-tabs v-model="tab">
      <el-tab-pane label="经历时间线" name="episodes">
        <el-empty v-if="!episodes.length" description="暂无已编码经历" :image-size="60" />
        <article v-for="episode in episodes" :key="episode.id" class="item">
          <div class="meta"><el-tag size="small" :type="episode.state === 'ARCHIVED' ? 'info' : ''">{{ episode.state === 'ARCHIVED' ? '已归档' : '经历' }}</el-tag>
            <span>{{ episode.occurredAt ? `发生于 ${formatDate(episode.occurredAt)}` : `记录于 ${formatDate(episode.recordedAt)}，发生时间未明确` }}</span></div>
          <p>{{ episode.content }}</p><p v-if="episode.goal" class="hint">相关目标：{{ episode.goal }}</p>
          <details><summary>查看原话</summary><p class="evidence">{{ episode.evidence }}</p></details>
          <el-button size="small" type="danger" text :disabled="busy" @click="removeEpisode(episode)">删除经历及依赖结论</el-button>
        </article>
        <p class="hint">展示最近 100 条经历。归档经历在高度相关的历史问题中仍可被召回。</p>
      </el-tab-pane>
      <el-tab-pane label="知识与版本" name="knowledge">
        <el-empty v-if="!semantics.length" description="暂无巩固后的知识" :image-size="60" />
        <article v-for="memory in semantics" :key="memory.id" class="item">
          <div class="meta"><el-tag size="small" :type="memory.status === 'CONFLICT' ? 'warning' : 'info'">{{ statuses[memory.status] || memory.status }}</el-tag>
            <span>第 {{ memory.version }} 版</span><span v-if="memory.pinned">已固定保留</span></div>
          <p>{{ memory.content }}</p>
          <div class="actions"><el-button size="small" :disabled="busy" @click="history(memory)">版本与证据</el-button>
            <el-button size="small" :disabled="busy" @click="pin(memory)">{{ memory.pinned ? '取消固定' : memory.status === 'ARCHIVED' ? '固定并恢复' : '固定保留' }}</el-button></div>
        </article>
      </el-tab-pane>
      <el-tab-pane label="最近召回与反馈" name="recall">
        <el-empty v-if="!retrievals.length" description="成功聊天后可查看最近召回" :image-size="60" />
        <article v-for="trace in retrievals" :key="trace.id" class="item">
          <div class="meta"><span>{{ formatDate(trace.createdAt) }}</span><span>{{ trace.items.length }} 条相关记忆</span></div>
          <ul v-if="trace.items.length"><li v-for="item in trace.items" :key="`${item.type}:${item.id}`">{{ recalledText(item) }}</li></ul>
          <p v-else class="hint">本轮没有召回相关长期记忆。</p>
          <p v-if="trace.feedback" class="hint">已反馈：{{ feedbackLabels[trace.feedback] }}</p>
          <div v-else class="actions">
            <el-button v-for="(label, value) in feedbackLabels" :key="value" size="small" :disabled="busy" @click="feedback(trace, value)">{{ label }}</el-button>
          </div>
        </article>
        <p class="hint">“事实有误”会提高后续筛选要求；请同时在上方修改对应事实。召回次数不会自动提高事实可信度。</p>
      </el-tab-pane>
    </el-tabs>
    <el-dialog v-model="showHistory" title="知识版本与来源证据" width="min(680px, 94vw)">
      <h4>版本</h4><article v-for="version in versions" :key="version.id" class="item">
        <div class="meta">第 {{ version.version }} 版 · {{ statuses[version.status] || version.status }} · {{ formatDate(version.validFrom) }} {{ version.validTo ? `至 ${formatDate(version.validTo)}` : '起' }}</div>
        <p>{{ version.content }}</p><p v-if="version.pendingContent" class="pending">待确认说法：{{ version.pendingContent }}</p><p class="evidence">{{ version.sourceText }}</p>
      </article>
      <h4>支持或冲突的经历</h4><article v-for="episode in evidence" :key="episode.id" class="item"><p>{{ episode.evidence }}</p><span class="hint">记录于 {{ formatDate(episode.recordedAt) }}</span></article>
      <p v-if="!evidence.length" class="hint">该记忆来自旧版迁移或用户手动确认，原始来源见版本记录。</p>
    </el-dialog>
  </section>
</template>

<script setup>
import { ref, onMounted, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '../utils/request'

const props = defineProps({ refreshKey: { type: Number, default: 0 } })
const emit = defineEmits(['changed'])
const loading = ref(false)
const busy = ref(false)
const episodes = ref([])
const semantics = ref([])
const retrievals = ref([])
const pendingJobs = ref(0)
const failedJobs = ref(0)
const tab = ref('episodes')
const showHistory = ref(false)
const versions = ref([])
const evidence = ref([])
const statuses = { ACTIVE: '有效', CONFLICT: '待确认冲突', ARCHIVED: '已归档' }
const feedbackLabels = { USEFUL: '有帮助', IRRELEVANT: '不相关', MISSING: '遗漏记忆', FACT_ERROR: '事实有误' }
const formatDate = value => String(value || '').replace('T', ' ').slice(0, 16)
const check = result => {
  if (result.code !== 200) throw new Error(result.message || '操作失败')
  return result.data
}
const load = async () => {
  loading.value = true
  try {
    const data = check(await request.get('/chat/memories/cognition'))
    episodes.value = data.episodes; semantics.value = data.semantics; retrievals.value = data.retrievals
    pendingJobs.value = data.pendingJobs; failedJobs.value = data.failedJobs
  } catch (e) { ElMessage.error(e.message || '读取记忆经历失败') }
  finally { loading.value = false }
}
const mutate = async (operation, message) => {
  if (busy.value) return
  busy.value = true
  try { check(await operation()); await load(); emit('changed'); ElMessage.success(message) }
  catch (e) { ElMessage.error(e.message || '操作失败') }
  finally { busy.value = false }
}
const recalledText = item => {
  const entry = item.type === 'SEMANTIC' ? semantics.value.find(m => String(m.id) === item.id && m.version === item.version) : episodes.value.find(e => e.id === item.id)
  return entry?.content || '该记忆已更新或不在当前展示范围内'
}
const history = async memory => {
  busy.value = true
  try {
    const [v, e] = await Promise.all([request.get(`/chat/memories/${memory.id}/versions`), request.get(`/chat/memories/${memory.id}/evidence`)])
    versions.value = check(v); evidence.value = check(e); showHistory.value = true
  } catch (e) { ElMessage.error(e.message || '读取版本失败') }
  finally { busy.value = false }
}
const pin = memory => mutate(() => request.put(`/chat/memories/${memory.id}/pin`, { pinned: !memory.pinned }), memory.pinned ? '已取消固定' : '已固定保留')
const feedback = (trace, value) => mutate(() => request.post('/chat/memories/feedback', { retrievalId: trace.id, feedback: value }), '反馈已记录')
const removeEpisode = async episode => {
  try { await ElMessageBox.confirm('删除这条经历及依赖它的知识版本？剩余证据会重新巩固，同时清除服务端最近对话上下文。', '删除经历', { type: 'warning' }) }
  catch { return }
  await mutate(() => request.delete(`/chat/memories/episodes/${episode.id}`), '经历及依赖结论已删除')
}
onMounted(load)
watch(() => props.refreshKey, load)
</script>

<style scoped>
.cognitive-panel { margin-top: 24px; border-top: 1px solid #e2e8f0; padding-top: 16px; }
.panel-header, .meta, .actions { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; }.panel-header { justify-content: space-between; }
h4 { margin: 8px 0; }.item { border-bottom: 1px solid #e2e8f0; padding: 14px 0; }.item p, li { white-space: pre-wrap; overflow-wrap: anywhere; line-height: 1.7; }
.meta, .hint, summary, .evidence { color: #64748b; font-size: 13px; line-height: 1.7; }.pending { color: #92400e; }summary { cursor: pointer; }.actions { margin-top: 12px; }.actions .el-button { margin-left: 0; }
</style>
