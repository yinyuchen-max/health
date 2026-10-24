<template>
  <section class="memories" v-loading="loading" aria-label="长期记忆管理">
    <el-divider />
    <div class="memory-header">
      <h3>长期记忆</h3>
      <el-switch :model-value="enabled" :disabled="busy || loading || !loaded" active-text="启用" @change="setEnabled" />
    </div>
    <p class="hint">启用后，AI 会在聊天结束后提取你明确说过的习惯、偏好、目标和健康情况，供以后的聊天参考。记忆提取使用平台模型，包含在已开通的 AI 服务中。</p>
    <p class="hint">关闭后停止提取和使用已保存的记忆，已有记忆仍可管理。修改、删除记忆或关闭功能时，会同时清除服务端最近对话上下文。</p>
    <div class="memory-actions">
      <el-button :disabled="busy || loading" @click="load">刷新记忆</el-button>
      <el-button type="danger" plain :disabled="busy || loading || !loaded" @click="clearAll">清空全部记忆</el-button>
    </div>
    <el-empty v-if="loaded && !memories.length" description="暂无长期记忆，聊天后可刷新查看" :image-size="70" />
    <article v-for="memory in memories" :key="memory.id" class="memory-item">
      <div class="memory-meta">
        <el-tag size="small">{{ categories[memory.category] || memory.category }}</el-tag>
        <el-tag v-if="memory.status === 'CONFLICT'" size="small" type="warning">待确认冲突</el-tag>
        <span v-if="memory.expiresAt" class="hint">有效至 {{ formatDate(memory.expiresAt) }}</span>
      </div>
      <p class="memory-content">{{ memory.content }}</p>
      <p v-if="memory.pendingContent" class="pending">新说法：{{ memory.pendingContent }}。确认前，这条记忆不会用于长期上下文。</p>
      <details>
        <summary>查看来源</summary>
        <p class="evidence">{{ memory.sourceText }}</p>
        <p v-if="memory.pendingSourceText" class="evidence">新说法来源：{{ memory.pendingSourceText }}</p>
        <span class="hint">更新于 {{ formatDate(memory.updatedAt) }}</span>
      </details>
      <div class="memory-actions">
        <el-button size="small" :disabled="busy" @click="edit(memory)">{{ memory.status === 'CONFLICT' ? '修改并确认' : '修改' }}</el-button>
        <el-button size="small" type="danger" text :disabled="busy" @click="remove(memory)">删除</el-button>
      </div>
    </article>
    <p class="hint">健康信息属于你的自述，不代表医学诊断。经历会逐步巩固为知识，不常用的信息可能归档。记忆提取可能出错，请在这里纠正；删除后若在新消息中再次提及，可能重新记住。</p>
    <CognitiveMemoryPanel :refresh-key="refreshKey" @changed="load" />
    <el-dialog v-model="editing" title="修改并确认记忆" width="min(520px, 92vw)" :close-on-click-modal="!busy" :close-on-press-escape="!busy" :show-close="!busy">
      <el-input v-model="draft" type="textarea" :rows="4" maxlength="500" show-word-limit :disabled="busy" />
      <template #footer>
        <el-button :disabled="busy" @click="editing = false">取消</el-button>
        <el-button type="primary" :loading="busy" :disabled="!draft.trim()" @click="saveEdit">确认保存</el-button>
      </template>
    </el-dialog>
  </section>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '../utils/request'
import CognitiveMemoryPanel from './CognitiveMemoryPanel.vue'

const enabled = ref(false)
const loaded = ref(false)
const loading = ref(false)
const busy = ref(false)
const memories = ref([])
const editing = ref(false)
const editingId = ref(null)
const draft = ref('')
const refreshKey = ref(0)
const categories = { health: '健康情况', diet: '饮食偏好', exercise: '运动习惯', goal: '个人目标', lifestyle: '生活习惯', family: '家族病史' }
const formatDate = value => String(value || '').replace('T', ' ').slice(0, 16)
const check = result => {
  if (result.code !== 200) throw new Error(result.message || '操作失败')
  return result.data
}
const load = async () => {
  loading.value = true
  try {
    const data = check(await request.get('/chat/memories'))
    enabled.value = data.enabled
    memories.value = data.memories
    loaded.value = true
    refreshKey.value++
  } catch (e) { ElMessage.error(e.message || '读取记忆失败') }
  finally { loading.value = false }
}
const mutate = async (operation, message) => {
  if (busy.value) return false
  busy.value = true
  try {
    check(await operation())
    await load()
    ElMessage.success(message)
    return true
  } catch (e) { ElMessage.error(e.message || '操作失败'); return false }
  finally { busy.value = false }
}
const setEnabled = value => mutate(() => request.put('/chat/memories/settings', { enabled: value }), value ? '长期记忆已启用' : '长期记忆已关闭')
const edit = memory => {
  editingId.value = memory.id
  draft.value = memory.pendingContent || memory.content
  editing.value = true
}
const saveEdit = async () => {
  if (await mutate(() => request.put(`/chat/memories/${editingId.value}`, { content: draft.value.trim() }), '记忆已确认保存')) editing.value = false
}
const remove = async memory => {
  try { await ElMessageBox.confirm('删除这条记忆，并清除服务端最近对话上下文？', '删除记忆', { type: 'warning' }) }
  catch { return }
  await mutate(() => request.delete(`/chat/memories/${memory.id}`), '记忆已删除')
}
const clearAll = async () => {
  try { await ElMessageBox.confirm('删除所有长期记忆，并清除服务端最近对话上下文？此操作不可恢复。', '清空全部记忆', { type: 'warning' }) }
  catch { return }
  await mutate(() => request.delete('/chat/memories'), '全部记忆已清空')
}
onMounted(load)
</script>

<style scoped>
.memory-header, .memory-meta, .memory-actions { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; }
.memory-header { justify-content: space-between; } h3 { margin: 0; }
.hint { color: #64748b; font-size: 13px; line-height: 1.7; }
.memory-item { margin: 14px 0; padding: 16px; border: 1px solid #e2e8f0; border-radius: 10px; }
.memory-content, .evidence, .pending { white-space: pre-wrap; overflow-wrap: anywhere; line-height: 1.7; }
.pending { color: #92400e; font-size: 13px; }.evidence { color: #64748b; font-size: 13px; }
summary { cursor: pointer; color: #64748b; font-size: 13px; }.memory-actions { margin-top: 12px; }
.memory-actions .el-button { margin-left: 0; }
</style>
