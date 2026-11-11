<template>
  <div class="wechat-login-panel" aria-live="polite">
    <div class="qr-frame" v-loading="creating">
      <img v-if="session && !error && !expired" :src="session.qrCode" alt="微信扫码登录小程序码" />
      <div v-else-if="!creating" class="qr-placeholder">{{ expired ? '二维码已过期' : '暂时无法获取二维码' }}</div>
    </div>
    <p class="qr-title">{{ consuming ? '已确认，正在登录…' : '微信扫一扫，在小程序确认登录' }}</p>
    <p class="qr-note">首次登录自动注册，与小程序共用健康记录。</p>
    <p class="qr-note">微信步数需在小程序授权同步，昵称和头像可自主选择。</p>
    <p v-if="error" class="qr-error">{{ error }}</p>
    <p v-else-if="session && !expired" class="qr-note">二维码 {{ remaining }} 秒后失效</p>
    <el-button :disabled="creating || consuming" @click="create">{{ expired || error ? '重新获取二维码' : '刷新二维码' }}</el-button>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { createWechatSession, wechatSessionStatus, loginWithWechat } from '../utils/auth'

const emit = defineEmits(['success'])
const session = ref(null)
const creating = ref(false)
const consuming = ref(false)
const error = ref('')
const now = ref(Date.now())
const remaining = computed(() => Math.max(0, Math.ceil(((session.value?.expiresAt || 0) - now.value) / 1000)))
const expired = computed(() => !!session.value && remaining.value === 0)
let version = 0
let timer
let clock
const friendlyError = (err, fallback) => {
  const message = err?.message || ''
  return !message || /Request failed|Network Error|timeout/i.test(message) ? fallback : message
}

async function create() {
  const mine = ++version
  clearTimeout(timer)
  creating.value = true
  error.value = ''
  session.value = null
  try {
    const result = await createWechatSession()
    if (mine !== version) return
    session.value = result
    now.value = Date.now()
    timer = setTimeout(() => poll(mine), 2000)
  } catch (err) {
    if (mine === version) error.value = friendlyError(err, '暂时无法连接登录服务，请稍后重试')
  } finally { if (mine === version) creating.value = false }
}
async function poll(mine) {
  if (mine !== version || expired.value) return
  try {
    const status = await wechatSessionStatus(session.value)
    if (mine !== version) return
    if (status === 'CONFIRMED') {
      consuming.value = true
      await loginWithWechat(session.value)
      if (mine === version) emit('success')
      return
    }
    if (status === 'EXPIRED' || status === 'CONSUMED') {
      session.value.expiresAt = 0
      return
    }
    timer = setTimeout(() => poll(mine), 2000)
  } catch (err) {
    if (mine === version) error.value = friendlyError(err, '登录状态获取失败，请重新扫码')
  } finally { if (mine === version) consuming.value = false }
}
onMounted(() => { create(); clock = setInterval(() => { now.value = Date.now() }, 1000) })
onBeforeUnmount(() => { version++; clearTimeout(timer); clearInterval(clock) })
</script>

<style scoped>
.wechat-login-panel { text-align: center; }
.qr-frame { box-sizing: border-box; width: min(240px, 100%); height: 240px; margin: 0 auto 20px; padding: 10px; border: 1px solid #e1e6ee; border-radius: 18px; display: grid; place-items: center; }
.qr-frame img { width: 100%; height: 100%; object-fit: contain; }
.qr-placeholder { color: #828c9b; }
.qr-title { color: #253047; font-weight: 600; }
.qr-note { color: #6e7787; font-size: 13px; line-height: 1.7; }
.qr-error { color: #c43e3e; font-size: 13px; }
</style>
