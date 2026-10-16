<template>
  <el-card class="ai-service" v-loading="loading">
    <template #header><h2>AI 服务</h2></template>
    <el-alert :closable="false" type="info" title="模型和知识检索由平台统一提供，无需准备 API Key。开通后可使用 AI 服务，管理员免费。" />
    <el-result v-if="error" icon="warning" title="暂时无法读取服务状态" :sub-title="error">
      <template #extra><el-button @click="load">重试</el-button></template>
    </el-result>
    <template v-else-if="status && catalog">
      <div class="service-status">
        <el-tag :type="status.allowed ? 'success' : 'warning'" size="large">
          {{ status.admin ? '管理员 · 免费使用' : status.allowed ? 'AI 服务已开通' : 'AI 服务未开通或已到期' }}
        </el-tag>
        <p v-if="status.expiresAt && !status.admin">{{ planName(catalog.currentPlan) }} · 有效期至 {{ formatTime(status.expiresAt) }}</p>
        <p>AI 健康顾问、智能报告、对话预约。三档当前功能相同，均包含平台知识检索。</p>
        <el-alert v-if="!status.configured" type="warning" :closable="false" title="平台 AI 服务暂未配置，请联系管理员。" />
      </div>
      <template v-if="!status.admin">
        <div class="plans">
          <button v-for="plan in catalog.plans" :key="plan.code" type="button"
            class="plan" :class="{ selected: selectedPlan === plan.code }" :aria-pressed="selectedPlan === plan.code"
            :disabled="busy || (status.allowed && catalog.currentPlan !== plan.code) || pendingOrder"
            @click="selectPlan(plan.code)">
            <strong>{{ plan.name }}</strong><span class="price">¥{{ money(plan.amountCents) }}<small>/月</small></span>
            <span>全部 AI 功能 · 含知识检索</span>
          </button>
        </div>
        <p class="hint">{{ catalog.message }}</p>
        <div class="actions">
          <el-select v-model="selectedChannel" :disabled="busy || pendingOrder || !catalog.available" @change="requestId = null" aria-label="支付方式">
            <el-option v-for="channel in catalog.channels" :key="channel" :value="channel" :label="channelName(channel)" />
          </el-select>
          <el-button type="primary" :loading="busy" :disabled="!catalog.available || !selectedChannel || pendingOrder" @click="purchase">
            {{ status.allowed ? '支付续费一个月' : '支付开通一个月' }}
          </el-button>
        </div>
        <div v-if="order" class="checkout">
          <h3>{{ order.status === 'PAID' ? '支付成功，服务已开通' : order.status === 'CLOSED' ? '订单已关闭' : '请扫码支付' }}</h3>
          <p>{{ planName(order.plan) }} · {{ channelName(order.channel) }} · ¥{{ money(order.amountCents) }}</p>
          <img v-if="pendingOrder && qrImage" :src="qrImage" width="240" height="240" alt="请使用所选支付应用扫描付款二维码" />
          <el-button v-if="pendingOrder && !qrImage" :loading="busy" @click="retryCheckout">重新获取付款码</el-button>
          <p v-if="pendingOrder">订单有效期至 {{ formatTime(order.expiresAt) }}。支付结果以服务端核验为准。</p>
          <p v-if="paymentError" class="payment-error" role="alert">{{ paymentError }}</p>
          <el-button v-if="pendingOrder" :loading="checking" @click="checkPayment">我已付款，查询结果</el-button>
          <el-button v-else @click="clearOrder">返回套餐</el-button>
        </div>
        <el-table v-if="orders.length" :data="orders" class="orders">
          <el-table-column label="套餐"><template #default="{ row }">{{ planName(row.plan) }}</template></el-table-column>
          <el-table-column label="金额"><template #default="{ row }">¥{{ money(row.amountCents) }}</template></el-table-column>
          <el-table-column label="状态"><template #default="{ row }">{{ { PENDING: '待核验/待支付', PAID: '已支付', CLOSED: '已关闭' }[row.status] }}</template></el-table-column>
          <el-table-column label="操作"><template #default="{ row }"><el-button text :disabled="busy" @click="showOrder(row)">查看</el-button></template></el-table-column>
        </el-table>
      </template>
      <div class="actions">
        <el-button v-if="status.allowed && status.configured" type="primary" @click="$router.push('/app/ai-chat')">进入 AI 聊天</el-button>
        <el-button :disabled="busy" @click="load">刷新服务状态</el-button>
      </div>
    </template>
    <UserMemories />
  </el-card>
</template>
<script setup>
import { ref, computed, onMounted, onBeforeUnmount } from 'vue'
import QRCode from 'qrcode'
import request from '../utils/request'
import UserMemories from '../components/UserMemories.vue'
const loading = ref(true), busy = ref(false), checking = ref(false)
const status = ref(null), catalog = ref(null), error = ref(''), paymentError = ref('')
const selectedPlan = ref('plus'), selectedChannel = ref(''), requestId = ref(null)
const order = ref(null), orders = ref([]), qrImage = ref('')
const pendingOrder = computed(() => order.value?.status === 'PENDING')
let timer = null, disposed = false
const money = cents => (cents / 100).toFixed(2)
const channelName = code => code === 'wechat' ? '微信支付' : '支付宝'
const planName = code => ({ plus: 'Plus', pro: 'Pro', promax: 'Pro Max' }[code] || code)
const formatTime = value => value ? new Date(value.replace(' ', 'T') + 'Z').toLocaleString('zh-CN', { hour12: false }) : ''
const data = response => {
  if (response.code !== 200) throw new Error(response.message || '请求失败')
  return response.data
}
const stopPolling = () => { if (timer) clearTimeout(timer); timer = null }
const scheduleCheck = () => {
  stopPolling()
  if (!disposed && pendingOrder.value) timer = setTimeout(checkPayment, 5000)
}
const showOrder = async value => {
  stopPolling(); order.value = value; qrImage.value = ''; paymentError.value = ''
  if (value.status === 'PENDING') {
    selectedPlan.value = value.plan; selectedChannel.value = value.channel
    if (value.checkoutUrl) {
      const image = await QRCode.toDataURL(value.checkoutUrl, { width: 240, margin: 2 })
      if (!disposed && order.value?.id === value.id) qrImage.value = image
    }
    scheduleCheck()
  }
}
const load = async () => {
  loading.value = true; error.value = ''
  try {
    const [accessResponse, catalogResponse, ordersResponse] = await Promise.all([
      request.get('/ai/access'), request.get('/ai/payments/catalog'), request.get('/ai/payments/orders')
    ])
    if (disposed) return
    status.value = data(accessResponse); catalog.value = data(catalogResponse); orders.value = data(ordersResponse)
    if (status.value.allowed && catalog.value.currentPlan) selectedPlan.value = catalog.value.currentPlan
    if (!catalog.value.channels.includes(selectedChannel.value)) selectedChannel.value = catalog.value.channels[0] || ''
    if (!order.value) {
      const pending = orders.value.find(value => value.status === 'PENDING')
      if (pending) await showOrder(pending)
    }
  } catch (e) {
    status.value = null; catalog.value = null; error.value = e.message || '读取服务状态失败'
  } finally { loading.value = false }
}
const selectPlan = code => { selectedPlan.value = code; requestId.value = null }
const purchase = async () => {
  if (busy.value || !catalog.value?.available || status.value?.admin || pendingOrder.value) return
  busy.value = true; paymentError.value = ''
  try {
    requestId.value ||= crypto.randomUUID()
    const result = data(await request.post('/ai/payments/orders', {
      plan: selectedPlan.value, channel: selectedChannel.value, requestId: requestId.value
    }))
    await showOrder(result); requestId.value = null
    await load()
  } catch (e) {
    paymentError.value = e.message || '下单失败'
    // Recover an order created before a lost response without creating another one.
    await load()
    if (!order.value) error.value = paymentError.value
  } finally { busy.value = false }
}
const checkPayment = async () => {
  if (checking.value || !order.value || disposed) return
  stopPolling(); checking.value = true
  const id = order.value.id
  try {
    const result = data(await request.post(`/ai/payments/orders/${id}/refresh`))
    if (disposed || order.value?.id !== id) return
    order.value = result; paymentError.value = ''
    if (result.status !== 'PENDING') { qrImage.value = ''; requestId.value = null; await load() }
  } catch (e) { paymentError.value = e.message || '暂时无法确认到账，请稍后查询' }
  finally { checking.value = false; scheduleCheck() }
}
const retryCheckout = async () => {
  if (busy.value || !pendingOrder.value) return
  busy.value = true; stopPolling()
  const id = order.value.id
  try {
    const result = data(await request.post(`/ai/payments/orders/${id}/checkout`))
    if (disposed || order.value?.id !== id) return
    await showOrder(result)
    if (result.status !== 'PENDING') await load()
  } catch (e) { paymentError.value = e.message || '暂时无法获取付款码，请稍后重试' }
  finally { busy.value = false; scheduleCheck() }
}
const clearOrder = () => { stopPolling(); order.value = null; qrImage.value = ''; requestId.value = null; paymentError.value = '' }
onMounted(load)
onBeforeUnmount(() => { disposed = true; stopPolling() })
</script>
<style scoped>
.ai-service { max-width: 900px; margin: 0 auto; }
h2 { margin: 0; }.service-status { margin: 24px 0; }
p { color: #64748b; line-height: 1.8; }.plans { display: grid; grid-template-columns: repeat(3, 1fr); gap: 14px; }
.plan { display: flex; flex-direction: column; gap: 14px; text-align: left; padding: 24px 18px; border: 1px solid #dbe2ed; border-radius: 14px; background: white; color: #334155; cursor: pointer; }
.plan.selected { border-color: #6366f1; background: #f5f5ff; }.plan:disabled { cursor: default; opacity: .65; }
.price { font-size: 30px; color: #1e293b; }.price small { font-size: 14px; }.plan > span:last-child, .hint { font-size: 13px; }
.actions { display: flex; gap: 12px; margin: 20px 0; flex-wrap: wrap; }.actions .el-select { width: 160px; }
.checkout { padding: 24px; text-align: center; border: 1px solid #e2e8f0; border-radius: 14px; }.payment-error { color: #b91c1c; }.orders { margin-top: 24px; }
@media(max-width: 600px) { .plans { grid-template-columns: 1fr; }.plan { padding: 18px; } }
</style>
