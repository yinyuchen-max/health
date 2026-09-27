<template>
  <div class="doctor-management">
    <div class="page-header">
      <div class="header-left">
        <div class="header-icon-box">
          <el-icon :size="24" color="#F59E0B"><Stamp /></el-icon>
        </div>
        <div>
          <h1>医生审核管理</h1>
          <p>审核医生注册申请</p>
        </div>
      </div>
    </div>

    <div class="table-card">
      <el-table :data="pendingDoctors" stripe v-loading="loading" style="width: 100%">
      <el-table-column prop="realName" label="真实姓名" width="120" />
      <el-table-column prop="hospital" label="医院" width="150" />
      <el-table-column prop="department" label="科室" width="100" />
      <el-table-column prop="title" label="职称" width="100" />
      <el-table-column prop="licenseNumber" label="执业证书号" width="150" />
      <el-table-column prop="specialization" label="擅长" min-width="150" />
      <el-table-column label="申请时间" width="160">
        <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="180" fixed="right">
        <template #default="{ row }">
          <el-button size="small" type="success" @click="approve(row.id)">通过</el-button>
          <el-button size="small" type="danger" @click="showReject(row.id)">驳回</el-button>
        </template>
      </el-table-column>
    </el-table>

      <el-empty v-if="!loading && pendingDoctors.length === 0" description="暂无待审核申请" />
    </div>

    <!-- 驳回弹窗 -->
    <el-dialog v-model="rejectDialog" title="驳回申请" width="400px">
      <el-input v-model="rejectReason" type="textarea" :rows="3" placeholder="请输入驳回原因" />
      <template #footer>
        <el-button @click="rejectDialog = false">取消</el-button>
        <el-button type="danger" :loading="rejecting" @click="confirmReject">确认驳回</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Stamp } from '@element-plus/icons-vue'
import request from '../utils/request'

const loading = ref(false)
const pendingDoctors = ref([])
const rejectDialog = ref(false)
const rejectReason = ref('')
const rejectingDoctorId = ref(null)
const rejecting = ref(false)

const fetchPending = async () => {
  loading.value = true
  try {
    const res = await request.get('/doctor/admin/pending')
    pendingDoctors.value = res?.data || res || []
  } catch {
    pendingDoctors.value = []
  } finally {
    loading.value = false
  }
}

const approve = async (id) => {
  try {
    await request.post(`/doctor/admin/${id}/approve`)
    ElMessage.success('已通过审核')
    await fetchPending()
  } catch (error) {
    ElMessage.error(error?.message || '操作失败')
  }
}

const showReject = (id) => {
  rejectingDoctorId.value = id
  rejectReason.value = ''
  rejectDialog.value = true
}

const confirmReject = async () => {
  rejecting.value = true
  try {
    await request.post(`/doctor/admin/${rejectingDoctorId.value}/reject`, rejectReason.value)
    ElMessage.success('已驳回')
    rejectDialog.value = false
    await fetchPending()
  } catch (error) {
    ElMessage.error(error?.message || '操作失败')
  } finally {
    rejecting.value = false
  }
}

const formatTime = (t) => t ? new Date(t).toLocaleString('zh-CN') : '-'

onMounted(fetchPending)
</script>

<style scoped>
.doctor-management {
  max-width: 1200px;
  margin: 0 auto;
}

.page-header {
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

.page-header h1 { margin: 0 0 4px; font-size: 24px; color: #1E293B; }
.page-header p { margin: 0; color: #64748B; font-size: 14px; }

.table-card {
  background: #fff;
  border-radius: 16px;
  padding: 20px;
  border: 1px solid #F0F4F8;
  box-shadow: 0 8px 24px rgba(15, 23, 42, 0.06);
}
</style>
