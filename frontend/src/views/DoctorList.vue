<template>
  <div class="doctor-list-page">
    <div class="page-header">
      <div class="header-left">
        <div class="header-icon-box">
          <el-icon :size="24" color="#3B82F6"><FirstAidKit /></el-icon>
        </div>
        <div>
          <h1>医生列表</h1>
          <p>选择医生进行在线咨询</p>
        </div>
      </div>
    </div>

    <!-- 科室筛选 -->
    <div class="filter-bar">
      <el-radio-group v-model="selectedDept" @change="fetchDoctors" size="large">
        <el-radio-button value="">全部科室</el-radio-button>
        <el-radio-button v-for="dept in departments" :key="dept" :value="dept">{{ dept }}</el-radio-button>
      </el-radio-group>
    </div>

    <!-- 医生卡片列表 -->
    <div class="doctor-grid" v-loading="loading">
      <el-card v-for="doctor in doctors" :key="doctor.id" class="doctor-card" shadow="hover">
        <div class="doctor-info">
          <div class="doctor-avatar">
            <el-avatar :size="64" style="background: #1e40af; font-size: 24px">
              {{ doctor.realName?.charAt(0) || '医' }}
            </el-avatar>
          </div>
          <div class="doctor-detail">
            <h3>{{ doctor.realName }}</h3>
            <p class="doctor-title">
              <el-tag size="small" type="primary">{{ doctor.title || '医生' }}</el-tag>
              <span class="hospital">{{ doctor.hospital }}</span>
            </p>
            <p class="doctor-dept">{{ doctor.department }}</p>
            <p class="doctor-spec" v-if="doctor.specialization">擅长：{{ doctor.specialization }}</p>
            <p class="doctor-intro" v-if="doctor.introduction">{{ doctor.introduction }}</p>
          </div>
          <div class="doctor-action">
            <el-button type="primary" @click="startChat(doctor.id)">在线咨询</el-button>
          </div>
        </div>
      </el-card>

      <el-empty v-if="!loading && doctors.length === 0" description="暂无该科室的医生" />
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { FirstAidKit } from '@element-plus/icons-vue'
import request from '../utils/request'

const router = useRouter()
const loading = ref(false)
const doctors = ref([])
const selectedDept = ref('')

const departments = [
  '内科', '外科', '儿科', '妇科', '皮肤科', '眼科', '耳鼻喉科', '口腔科',
  '骨科', '神经内科', '心血管内科', '消化内科', '呼吸内科', '泌尿外科'
]

const fetchDoctors = async () => {
  loading.value = true
  try {
    const params = selectedDept.value ? `?department=${selectedDept.value}` : ''
    const res = await request.get(`/doctor/list${params}`)
    doctors.value = res?.data || res || []
  } catch {
    doctors.value = []
  } finally {
    loading.value = false
  }
}

const startChat = async (doctorId) => {
  try {
    await request.post('/doctor-chat/send-to-doctor', {
      doctorId,
      content: '您好，我想咨询一些健康问题。'
    })
    ElMessage.success('已发送消息，医生将尽快回复')
    router.push('/app/doctor-chat')
  } catch (error) {
    ElMessage.error(error?.message || '发送失败')
  }
}

onMounted(fetchDoctors)
</script>

<style scoped>
.doctor-list-page {
  max-width: 1000px;
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
  background: rgba(59, 130, 246, 0.1);
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

.page-header p {
  margin: 0;
  color: #64748B;
  font-size: 14px;
}

.filter-bar {
  margin-bottom: 20px;
  overflow-x: auto;
  padding-bottom: 4px;
}

.filter-bar .el-radio-group {
  flex-wrap: wrap;
}

.doctor-grid {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.doctor-card {
  border-radius: 16px;
  border: 1px solid #F0F4F8;
  box-shadow: 0 8px 24px rgba(15, 23, 42, 0.06);
  transition: transform 0.2s, box-shadow 0.2s;
}

.doctor-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 14px 30px rgba(15, 23, 42, 0.1);
}

.doctor-info {
  display: flex;
  gap: 20px;
  align-items: flex-start;
}

.doctor-detail {
  flex: 1;
}

.doctor-detail h3 {
  margin: 0 0 8px;
  font-size: 18px;
  color: #1e293b;
}

.doctor-title {
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 0 0 4px;
}

.hospital {
  color: #64748b;
  font-size: 13px;
}

.doctor-dept {
  color: #475569;
  font-size: 13px;
  margin: 0 0 4px;
}

.doctor-spec {
  color: #64748b;
  font-size: 13px;
  margin: 0 0 4px;
}

.doctor-intro {
  color: #94a3b8;
  font-size: 12px;
  margin: 4px 0 0;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.doctor-action {
  display: flex;
  align-items: center;
}

.doctor-action .el-button {
  border-radius: 10px;
  height: 40px;
  padding: 0 20px;
  background: #3B82F6;
  border-color: #3B82F6;
}

/* 移动端适配 */
@media (max-width: 768px) {
  .page-header h1 {
    font-size: 20px;
  }

  .page-header p {
    font-size: 13px;
    margin-bottom: 14px;
  }

  .filter-bar :deep(.el-radio-button__inner) {
    padding: 8px 12px;
    font-size: 13px;
  }

  .doctor-info {
    flex-direction: column;
    gap: 12px;
  }

  .doctor-avatar {
    align-self: flex-start;
  }

  .doctor-action {
    width: 100%;
  }

  .doctor-action .el-button {
    width: 100%;
  }

  .doctor-detail h3 {
    font-size: 16px;
  }
}
</style>
