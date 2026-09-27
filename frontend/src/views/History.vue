<template>
  <div class="history-page">
    <!-- 统计卡片 -->
    <el-row :gutter="16">
      <el-col :xs="24" :sm="8" v-for="item in summaryCards" :key="item.label">
        <el-card class="summary-card">
          <div class="summary-icon" :style="{ background: item.bg }">
            <el-icon :size="20" :color="item.color"><component :is="item.icon" /></el-icon>
          </div>
          <div class="summary-value">{{ item.value }}</div>
          <div class="summary-label">{{ item.label }}</div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 主内容区 -->
    <el-card class="main-card">
      <div class="toolbar">
        <div class="toolbar-left">
          <div class="toolbar-icon" style="background: #EEF2FF;">
            <el-icon :size="18" color="#6366F1"><Clock /></el-icon>
          </div>
          <h3>历史记录</h3>
          <div class="tab-group">
            <button :class="['tab-btn', { active: filterType === '' }]" @click="setFilter('')">全部</button>
            <button :class="['tab-btn', { active: filterType === 'health' }]" @click="setFilter('health')">
              <span class="tab-dot" style="background:#22c55e"></span>健康
            </button>
            <button :class="['tab-btn', { active: filterType === 'sport' }]" @click="setFilter('sport')">
              <span class="tab-dot" style="background:#f59e0b"></span>运动
            </button>
            <button :class="['tab-btn', { active: filterType === 'reminder' }]" @click="setFilter('reminder')">
              <span class="tab-dot" style="background:#6366f1"></span>提醒
            </button>
          </div>
        </div>
        <div class="toolbar-right">
          <el-date-picker
            v-model="dateRange"
            type="daterange"
            range-separator="至"
            start-placeholder="开始日期"
            end-placeholder="结束日期"
            value-format="YYYY-MM-DD"
            unlink-panels
          />
          <el-button @click="handleReset">重置</el-button>
        </div>
      </div>

      <el-table :data="historyList" style="width: 100%" v-loading="loading">
        <el-table-column prop="date" label="日期" width="180" />
        <el-table-column prop="type" label="类型" width="120">
          <template #default="scope">
            <span :class="['type-badge', 'type-' + scope.row.type]">
              {{ getTypeName(scope.row.type) }}
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="title" label="标题" min-width="160" show-overflow-tooltip />
        <el-table-column prop="content" label="内容" min-width="200" show-overflow-tooltip />
        <el-table-column prop="createTime" label="记录时间" width="180" />
        <el-table-column label="操作" width="100" fixed="right">
          <template #default="scope">
            <el-button type="primary" link size="small" @click="handleView(scope.row)">查看</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination">
        <el-pagination
          v-model:current-page="currentPage"
          v-model:page-size="pageSize"
          :page-sizes="[10, 20, 50, 100]"
          layout="total, sizes, prev, pager, next, jumper"
          :total="total"
          @size-change="handleSizeChange"
          @current-change="handleCurrentChange"
        />
      </div>
    </el-card>

    <!-- 查看/编辑对话框 -->
    <el-dialog
      v-model="dialogVisible"
      title="查看历史记录"
      width="600px"
      destroy-on-close
    >
      <el-form :model="editForm" label-width="80px">
        <el-form-item label="标题">
          <el-input v-model="editForm.title" placeholder="标题" :disabled="!currentRecord" />
        </el-form-item>
        <el-form-item label="内容">
          <el-input v-model="editForm.content" type="textarea" rows="6" placeholder="内容" :disabled="!currentRecord" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">关闭</el-button>
        <el-button type="primary" @click="handleSave" v-if="currentRecord">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onBeforeUnmount } from 'vue'
import { ElMessage } from 'element-plus'
import { Clock, Document, TrendCharts, Bell } from '@element-plus/icons-vue'
import request from '../utils/request'
import { useUserStore } from '../store/user'
import { bindHistorySync } from '../utils/historySync'

const userStore = useUserStore()
const filterType = ref('')
const dateRange = ref([])
const currentPage = ref(1)
const pageSize = ref(10)
const total = ref(0)
const loading = ref(false)
const historyList = ref([])
const dialogVisible = ref(false)
const currentRecord = ref(null)
const editForm = ref({ title: '', content: '' })
let unbindHistorySync = null

const summaryCards = computed(() => [
  {
    label: '总记录数',
    value: total.value,
    icon: 'Document',
    color: '#3B82F6',
    bg: '#EFF6FF'
  },
  {
    label: '健康/运动',
    value: historyList.value.filter(r => r.type === 'health' || r.type === 'sport').length,
    icon: 'TrendCharts',
    color: '#22C55E',
    bg: '#F0FDF4'
  },
  {
    label: '提醒记录',
    value: historyList.value.filter(r => r.type === 'reminder').length,
    icon: 'Bell',
    color: '#6366F1',
    bg: '#EEF2FF'
  }
])

const getTypeName = (type) => ({ health: '健康记录', sport: '运动记录', reminder: '提醒记录' }[type] || type)

const setFilter = (type) => {
  filterType.value = type
  currentPage.value = 1
  loadHistory()
}

const loadHistory = async () => {
  loading.value = true
  try {
    const userId = userStore.userInfo?.id || 1
    const params = { pageNum: currentPage.value, pageSize: pageSize.value }
    if (filterType.value) params.type = filterType.value
    if (dateRange.value?.length === 2) {
      params.startDate = dateRange.value[0]
      params.endDate = dateRange.value[1]
    }
    const response = await request.get(`/history/records/${userId}`, { params })
    if (response.code === 200 && response.data) {
      historyList.value = response.data.records || []
      total.value = response.data.total || 0
    }
  } catch (error) {
    console.error('加载历史记录失败:', error)
    ElMessage.error('加载历史记录失败')
  } finally {
    loading.value = false
  }
}

const handleReset = () => {
  filterType.value = ''
  dateRange.value = []
  currentPage.value = 1
  loadHistory()
}

const handleView = (row) => {
  currentRecord.value = row
  editForm.value.title = row.title
  editForm.value.content = row.content
  dialogVisible.value = true
}

const handleSizeChange = () => loadHistory()
const handleCurrentChange = () => loadHistory()

const handleSave = async () => {
  if (!currentRecord.value) return
  try {
    await request.put(`/history/record/${currentRecord.value.id}`, {
      title: editForm.value.title,
      content: editForm.value.content,
      recordDate: currentRecord.value.date
    })
    ElMessage.success('保存成功')
    dialogVisible.value = false
    currentRecord.value = null
    await loadHistory()
  } catch (error) {
    ElMessage.error(error.message || '保存失败')
  }
}

onMounted(() => {
  loadHistory()
  unbindHistorySync = bindHistorySync(loadHistory)
})

onBeforeUnmount(() => {
  if (unbindHistorySync) { unbindHistorySync(); unbindHistorySync = null }
})
</script>

<style scoped>
.history-page { display: flex; flex-direction: column; gap: 16px; }

.summary-card {
  display: flex; align-items: center; gap: 14px; padding: 16px 20px;
}
.summary-card :deep(.el-card__body) { display: flex; align-items: center; gap: 14px; padding: 16px 20px; width: 100%; }
.summary-icon {
  width: 44px; height: 44px; border-radius: 12px;
  display: flex; align-items: center; justify-content: center; flex-shrink: 0;
}
.summary-value { font-size: 24px; font-weight: 800; color: #1E293B; }
.summary-label { font-size: 12px; color: #64748B; margin-top: 2px; }

.main-card { border-radius: 16px; }

.toolbar {
  display: flex; justify-content: space-between; align-items: center;
  flex-wrap: wrap; gap: 12px; margin-bottom: 16px;
}
.toolbar-left { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; }
.toolbar-left h3 { margin: 0; font-size: 16px; color: #1E293B; white-space: nowrap; }
.toolbar-right { display: flex; gap: 8px; align-items: center; }

.toolbar-icon {
  width: 34px; height: 34px; border-radius: 10px;
  display: flex; align-items: center; justify-content: center;
}

.tab-group { display: flex; gap: 4px; }
.tab-btn {
  padding: 5px 12px; border-radius: 8px; border: 1px solid #E2E8F0;
  background: #fff; color: #64748B; font-size: 13px; cursor: pointer;
  display: flex; align-items: center; gap: 4px; transition: all 0.15s;
  font-family: inherit;
}
.tab-btn:hover { border-color: #93C5FD; color: #2563EB; }
.tab-btn.active { background: #EFF6FF; border-color: #93C5FD; color: #2563EB; font-weight: 600; }
.tab-dot { width: 6px; height: 6px; border-radius: 50%; }

.type-badge {
  display: inline-block; padding: 3px 10px; border-radius: 999px;
  font-size: 12px; font-weight: 600;
}
.type-health { background: #F0FDF4; color: #16A34A; }
.type-sport { background: #FFFBEB; color: #D97706; }
.type-reminder { background: #EEF2FF; color: #6366F1; }

.pagination { margin-top: 16px; display: flex; justify-content: flex-end; }

@media (max-width: 768px) {
  .toolbar { flex-direction: column; align-items: flex-start; }
  .toolbar-right { flex-wrap: wrap; }
  .tab-group { flex-wrap: wrap; }
}
</style>
