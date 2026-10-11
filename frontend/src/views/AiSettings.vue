<template>
  <el-card class="ai-settings" v-loading="loading">
    <template #header><h2>AI 设置</h2></template>
    <el-alert :closable="false" type="info" title="使用你自己的 API Key，模型费用由对应服务商从你的账户扣除。健康问题及分析所需的健康数据会发送给所选服务商。" />
    <p>{{ configured ? '已配置 API Key。留空表示保留现有密钥。' : '尚未配置。保存后即可使用 AI 聊天和健康分析。' }}</p>
    <el-form label-position="top" @submit.prevent="save">
      <el-form-item label="服务商">
        <el-select v-model="form.provider" @change="providerChanged" :disabled="busy">
          <el-option label="DeepSeek" value="deepseek" /><el-option label="阿里云百炼（北京）" value="dashscope" />
        </el-select>
      </el-form-item>
      <el-form-item label="模型名称"><el-input v-model="form.model" maxlength="100" :disabled="busy" /></el-form-item>
      <el-form-item label="API Key">
        <el-input v-model="form.apiKey" type="password" show-password autocomplete="off" :spellcheck="false" maxlength="512" :disabled="busy" placeholder="仅输入新密钥；保存后不会回显" />
      </el-form-item>
      <el-divider />
      <el-form-item label="百炼向量 API Key（可选）">
        <el-input v-model="form.embeddingApiKey" type="password" show-password autocomplete="off" :spellcheck="false" maxlength="512" :disabled="busy" placeholder="不填时使用本地知识检索，无向量模型费用" />
      </el-form-item>
      <p class="hint">{{ embeddingConfigured ? '已配置向量密钥，留空保留。' : '未配置向量密钥。' }}填写后首次构建健康知识向量会消耗你的百炼额度，后续复用缓存。</p>
      <el-checkbox v-if="embeddingConfigured" v-model="form.clearEmbeddingKey" :disabled="busy">删除已保存的向量密钥</el-checkbox>
      <div class="actions"><el-button type="primary" native-type="submit" :loading="busy" :disabled="loading">保存配置</el-button>
        <el-button type="danger" plain :disabled="!configured || busy || loading" @click="remove">删除全部密钥</el-button>
        <el-button @click="$router.push('/app/ai-chat')">前往 AI 聊天</el-button></div>
    </el-form>
    <p class="hint">密钥按账号加密保存，不会保存在浏览器本地。保存配置不调用模型；密钥、额度和模型可用性会在实际使用时检查。未配置时健康报告仍提供规则分析。</p>
  </el-card>
</template>
<script setup>
import { reactive, ref, onMounted, onBeforeUnmount } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '../utils/request'
const loading = ref(true)
const busy = ref(false)
const configured = ref(false)
const embeddingConfigured = ref(false)
const form = reactive({ provider: 'deepseek', model: 'deepseek-flash', apiKey: '', embeddingApiKey: '', clearEmbeddingKey: false })
const clearSecrets = () => { form.apiKey = ''; form.embeddingApiKey = ''; form.clearEmbeddingKey = false }
const providerChanged = () => { form.model = form.provider === 'deepseek' ? 'deepseek-flash' : 'qwen-plus'; form.apiKey = '' }
const load = async () => {
  loading.value = true
  try {
    const { data } = await request.get('/ai/settings')
    configured.value = data.configured; embeddingConfigured.value = data.embeddingConfigured
    form.provider = data.provider; form.model = data.model
  } catch (e) { ElMessage.error(e.response?.data?.message || e.message || '读取 AI 配置失败') }
  finally { loading.value = false }
}
const save = async () => {
  if (busy.value) return
  busy.value = true
  try { await request.put('/ai/settings', { ...form }); clearSecrets(); await load(); ElMessage.success('配置已保存') }
  catch (e) { ElMessage.error(e.response?.data?.message || e.message || '保存失败，请重试') }
  finally { busy.value = false }
}
const remove = async () => {
  try { await ElMessageBox.confirm('删除后将停止使用你的密钥，健康报告改用规则分析。', '删除 AI 密钥', { type: 'warning' }) }
  catch { return }
  busy.value = true
  try { await request.delete('/ai/settings'); clearSecrets(); await load(); ElMessage.success('密钥已删除') }
  catch (e) { ElMessage.error(e.response?.data?.message || e.message || '删除失败，请重试') }
  finally { busy.value = false }
}
onMounted(load)
onBeforeUnmount(clearSecrets)
</script>
<style scoped>
.ai-settings { max-width: 760px; margin: 0 auto; }
h2 { margin: 0; }.el-form { margin-top: 24px; }.el-select { width: 100%; }
.hint { color: #64748b; font-size: 13px; line-height: 1.7; }.actions { display: flex; gap: 12px; flex-wrap: wrap; margin: 24px 0; }
.actions .el-button { margin: 0; }
</style>
