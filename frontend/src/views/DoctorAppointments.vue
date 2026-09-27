<template>
  <div class="appointments-page">
    <div class="page-header">
      <div class="header-left">
        <div class="header-icon-box">
          <el-icon :size="24" color="#F59E0B"><Calendar /></el-icon>
        </div>
        <div>
          <h1>预约患者</h1>
          <p class="page-desc">查看预约您的患者，了解他们的健康状况并及时沟通</p>
        </div>
      </div>
      <div class="header-stats">
        <div class="mini-stat">
          <span class="mini-number">{{ appointments.length }}</span>
          <span class="mini-label">总预约</span>
        </div>
        <div class="mini-divider"></div>
        <div class="mini-stat">
          <span class="mini-number">{{ pendingCount }}</span>
          <span class="mini-label">待确认</span>
        </div>
      </div>
    </div>

    <div class="filter-bar">
      <el-radio-group v-model="statusFilter" size="default">
        <el-radio-button label="all">全部</el-radio-button>
        <el-radio-button label="pending">待确认</el-radio-button>
        <el-radio-button label="confirmed">已确认</el-radio-button>
        <el-radio-button label="completed">已完成</el-radio-button>
        <el-radio-button label="cancelled">已取消</el-radio-button>
      </el-radio-group>
      <el-button :icon="Refresh" circle @click="fetchAppointments" />
    </div>

    <div v-loading="loading" class="card-list">
      <div v-if="filteredList.length === 0 && !loading" class="empty-state">
        <el-icon :size="56" color="#cbd5e1"><Calendar /></el-icon>
        <p>暂无{{ statusFilterText }}预约患者</p>
      </div>
      <div v-for="item in filteredList" :key="item.id" class="patient-card">
        <div class="card-top">
          <el-avatar :size="48" class="patient-avatar">{{ (item.patientName || '患').slice(0, 1) }}</el-avatar>
          <div class="patient-info">
            <div class="patient-name">
              {{ item.patientName }}
              <el-tag :type="statusType(item.status)" size="small" round>{{ statusText(item.status) }}</el-tag>
            </div>
            <p class="patient-meta">{{ item.age }}岁 · {{ item.department }} · {{ item.phone }}</p>
          </div>
          <div class="appt-time-box">
            <el-icon><Clock /></el-icon>
            <span>{{ formatTime(item.appointmentTime) }}</span>
          </div>
        </div>
        <div class="card-actions">
          <el-button type="primary" plain :icon="DataLine" @click="viewPatientRecords(item)">
            查看健康报告
          </el-button>
          <el-button type="success" plain :icon="ChatLineRound" @click="goChat(item)">
            与患者沟通
          </el-button>
        </div>
      </div>
    </div>

    <!-- 患者健康报告弹窗 -->
    <el-dialog v-model="showRecords" :title="'健康报告 - ' + (selectedPatientName || '')" width="720px" top="6vh">
      <div v-if="patientRecords.length === 0 && !loadingRecords" class="records-empty">
        <p>该患者暂无健康记录</p>
      </div>
      <el-table v-else :data="patientRecords" stripe v-loading="loadingRecords" max-height="420">
        <el-table-column prop="recordDate" label="日期" width="110" />
        <el-table-column label="血压(mmHg)" width="120">
          <template #default="{ row }">
            {{ row.bloodPressureSystolic }}/{{ row.bloodPressureDiastolic }}
          </template>
        </el-table-column>
        <el-table-column prop="heartRate" label="心率(bpm)" width="90" />
        <el-table-column prop="bloodSugar" label="血糖(mmol/L)" width="120" />
        <el-table-column prop="weight" label="体重(kg)" width="90" />
        <el-table-column prop="notes" label="备注" min-width="140" show-overflow-tooltip />
      </el-table>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { Calendar, Clock, DataLine, ChatLineRound, Refresh } from '@element-plus/icons-vue'
import request from '../utils/request'

const router = useRouter()
const loading = ref(false)
const appointments = ref([])
const statusFilter = ref('all')

const showRecords = ref(false)
const patientRecords = ref([])
const loadingRecords = ref(false)
const selectedPatientName = ref('')

const pendingCount = computed(() => appointments.value.filter(a => a.status === 'pending' || !a.status).length)

const filteredList = computed(() => {
  if (statusFilter.value === 'all') return appointments.value
  if (statusFilter.value === 'pending') {
    return appointments.value.filter(a => a.status === 'pending' || !a.status)
  }
  return appointments.value.filter(a => a.status === statusFilter.value)
})

const statusFilterText = computed(() => ({
  all: '', pending: '待确认的', confirmed: '已确认的', completed: '已完成的', cancelled: '已取消的'
}[statusFilter.value]))

const fetchAppointments = async () => {
  loading.value = true
  try {
    const res = await request.get('/doctor/my-appointments')
    appointments.value = res?.data || res || []
  } catch {
    appointments.value = []
  } finally {
    loading.value = false
  }
}

const viewPatientRecords = async (item) => {
  showRecords.value = true
  loadingRecords.value = true
  selectedPatientName.value = item.patientName
  patientRecords.value = []
  try {
    const res = await request.get(`/health/records/${item.userId}`, {
      params: { pageNum: 1, pageSize: 30 }
    })
    patientRecords.value = res?.data?.records || res?.records || []
  } catch {
    patientRecords.value = []
  } finally {
    loadingRecords.value = false
  }
}

const goChat = (item) => {
  router.push(`/doctor/conversations?userId=${item.userId}`)
}

const formatTime = (t) => t ? new Date(t).toLocaleString('zh-CN', { month: 'numeric', day: 'numeric', hour: '2-digit', minute: '2-digit' }) : '-'
const statusText = (s) => ({ pending: '待确认', confirmed: '已确认', completed: '已完成', cancelled: '已取消' }[s] || '待确认')
const statusType = (s) => ({ pending: 'warning', confirmed: 'primary', completed: 'success', cancelled: 'info' }[s] || 'warning')

onMounted(fetchAppointments)
</script>

<style scoped>
.appointments-page {
  max-width: 1000px;
  margin: 0 auto;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 14px;
}

.header-icon-box {
  width: 48px;
  height: 48px;
  border-radius: 14px;
  background: rgba(245, 158, 11, 0.1);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.page-header h1 {
  margin: 0 0 4px;
  font-size: 24px;
  color: #1E293B;
}

.page-desc {
  margin: 0;
  color: #64748b;
  font-size: 14px;
}

.header-stats {
  display: flex;
  align-items: center;
  gap: 20px;
  padding: 14px 24px;
  background: #fff;
  border-radius: 16px;
  box-shadow: 0 8px 24px rgba(15, 23, 42, 0.06);
}

.mini-stat {
  display: flex;
  flex-direction: column;
  align-items: center;
}

.mini-number {
  font-size: 22px;
  font-weight: 800;
  color: #1e40af;
}

.mini-label {
  font-size: 12px;
  color: #64748b;
}

.mini-divider {
  width: 1px;
  height: 30px;
  background: #e2e8f0;
}

.filter-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}

.card-list {
  display: flex;
  flex-direction: column;
  gap: 14px;
  min-height: 200px;
}

.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
  padding: 60px 0;
  color: #94a3b8;
}

.patient-card {
  padding: 20px 24px;
  background: #fff;
  border-radius: 18px;
  box-shadow: 0 8px 24px rgba(15, 23, 42, 0.06);
  transition: transform 0.2s, box-shadow 0.2s;
}

.patient-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 14px 30px rgba(15, 23, 42, 0.1);
}

.card-top {
  display: flex;
  align-items: center;
  gap: 14px;
}

.patient-avatar {
  background: #3B82F6;
  color: #fff;
  font-weight: 700;
  font-size: 18px;
}

.patient-info { flex: 1; }

.patient-name {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 16px;
  font-weight: 700;
  color: #0f172a;
}

.patient-meta {
  margin: 4px 0 0;
  font-size: 13px;
  color: #64748b;
}

.appt-time-box {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 14px;
  background: #eff6ff;
  border-radius: 12px;
  color: #1e40af;
  font-size: 13px;
  font-weight: 600;
}

.card-actions {
  display: flex;
  gap: 10px;
  margin-top: 16px;
  padding-top: 14px;
  border-top: 1px dashed #F0F4F8;
}

.records-empty {
  padding: 40px;
  text-align: center;
  color: #94a3b8;
}

@media (max-width: 720px) {
  .page-header { flex-direction: column; align-items: flex-start; gap: 12px; }
  .card-top { flex-wrap: wrap; }
}
</style>
