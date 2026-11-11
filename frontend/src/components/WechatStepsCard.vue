<template>
  <section v-if="userStore.userInfo.wechatBound || summary.history.length" class="wechat-steps" aria-label="微信运动步数">
    <div class="steps-heading">
      <div><span class="steps-badge">微信运动</span><h3>今日步数</h3></div>
      <el-button :loading="loading" size="small" @click="load">刷新已同步数据</el-button>
    </div>
    <div class="steps-number">{{ summary.todaySteps == null ? '--' : summary.todaySteps.toLocaleString() }} <span>步</span></div>
    <p v-if="error" class="steps-error">{{ error }}</p>
    <p v-else class="steps-note">{{ summary.todaySteps == null ? '今天尚未同步' : `${summary.date} · 北京时间` }} · {{ summary.lastSyncAt ? `最近同步 ${summary.lastSyncAt.replace('T', ' ')}` : '暂无同步记录' }}</p>
    <p class="steps-note">打开小程序并授权微信运动后同步；网页显示最近保存的数据。</p>
    <div v-if="summary.history.length" class="steps-history">
      <div v-for="day in summary.history.slice(0, 7).slice().reverse()" :key="day.date" class="steps-day">
        <span>{{ day.date.slice(5) }}</span><strong>{{ day.steps.toLocaleString() }}</strong>
      </div>
    </div>
  </section>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import request from '../utils/request'
import { useUserStore } from '../store/user'
const userStore = useUserStore()
const summary = ref({ history: [], todaySteps: null, lastSyncAt: null })
const loading = ref(false)
const error = ref('')
async function load() {
  loading.value = true
  error.value = ''
  try {
    const result = await request.get('/user/wechat/steps')
    if (result.code !== 200) throw new Error(result.message || '步数加载失败')
    summary.value = result.data
  } catch (err) { error.value = err.message || '步数暂时无法加载，请重试' }
  finally { loading.value = false }
}
onMounted(load)
</script>

<style scoped>
.wechat-steps { margin-bottom: 22px; padding: 24px; border-radius: 16px; border: 1px solid #d7e8e4; background: linear-gradient(120deg, #f0fbf6, #fff); }
.steps-heading { display: flex; align-items: center; justify-content: space-between; gap: 12px; flex-wrap: wrap; }
.steps-heading h3 { display: inline; margin-left: 10px; font-size: 15px; }
.steps-badge { color: #13815b; font-size: 13px; }
.steps-number { margin-top: 18px; font-size: 36px; font-weight: 700; color: #155c48; }
.steps-number span, .steps-note { font-size: 12px; font-weight: 400; color: #677d74; }
.steps-history { display: flex; gap: 18px; flex-wrap: wrap; margin-top: 18px; }
.steps-day { display: flex; flex-direction: column; gap: 6px; font-size: 12px; color: #677d74; }
.steps-day strong { font-size: 14px; color: #155c48; }
.steps-error { color: #c43e3e; font-size: 13px; }
</style>
