<template>
  <div class="history-container">
    <el-card>
      <template #header>
        <div class="card-header">
          <el-icon :size="20" color="#409EFF"><Clock /></el-icon>
          <span>历史记录</span>
        </div>
      </template>

      <!-- 筛选条件 -->
      <el-form :inline="true" class="filter-form">
        <el-form-item label="记录类型">
          <el-select v-model="filterType" placeholder="全部类型" clearable>
            <el-option label="健康记录" value="health" />
            <el-option label="运动记录" value="sport" />
            <el-option label="提醒记录" value="reminder" />
          </el-select>
        </el-form-item>
        <el-form-item label="日期范围">
          <el-date-picker
            v-model="dateRange"
            type="daterange"
            range-separator="至"
            start-placeholder="开始日期"
            end-placeholder="结束日期"
            format="YYYY-MM-DD"
            value-format="YYYY-MM-DD"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleFilter">查询</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>

      <!-- 历史记录列表 -->
      <el-table :data="historyList" style="width: 100%" v-loading="loading">
        <el-table-column prop="date" label="日期" width="180" />
        <el-table-column prop="type" label="类型" width="120">
          <template #default="scope">
            <el-tag :type="getTypeTag(scope.row.type)">
              {{ getTypeName(scope.row.type) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="title" label="标题" />
        <el-table-column prop="content" label="内容" />
        <el-table-column prop="createTime" label="记录时间" width="180" />
        <el-table-column label="操作" width="100">
          <template #default="scope">
            <el-button type="text" size="small" @click="handleView(scope.row)">查看</el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- 分页 -->
      <el-pagination
        v-model:current-page="currentPage"
        v-model:page-size="pageSize"
        :page-sizes="[10, 20, 50, 100]"
        layout="total, sizes, prev, pager, next, jumper"
        :total="total"
        @size-change="handleSizeChange"
        @current-change="handleCurrentChange"
        style="margin-top: 20px; justify-content: flex-end"
      />
    </el-card>

      <!-- 查看/编辑对话框 -->
      <el-dialog
        v-model="dialogVisible"
        :title="currentRecord ? '查看历史记录' : '编辑历史记录'"
        width="60%"
        destroy-on-close
      >
        <el-form :model="editForm" label-width="80px">
          <el-form-item label="标题">
            <el-input
              v-model="editForm.title"
              placeholder="请输入标题"
              :disabled="!currentRecord"
            />
          </el-form-item>
          <el-form-item label="内容">
            <el-input
              v-model="editForm.content"
              type="textarea"
              rows="6"
              placeholder="请输入内容"
              :disabled="!currentRecord"
            />
          </el-form-item>
        </el-form>
        <template #footer>
          <span class="dialog-footer">
            <el-button @click="dialogVisible = false">取消</el-button>
            <el-button
              type="primary"
              @click="handleSave"
              v-if="currentRecord"
            >
              保存
            </el-button>
          </span>
        </template>
      </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, onBeforeUnmount } from 'vue'
import { ElMessage, ElDialog } from 'element-plus'
import { Clock } from '@element-plus/icons-vue'
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
const editForm = ref({
  title: '',
  content: ''
})
let unbindHistorySync = null

// 获取类型标签颜色
const getTypeTag = (type) => {
  const tagMap = {
    health: 'success',
    sport: 'warning',
    reminder: 'info'
  }
  return tagMap[type] || ''
}

// 获取类型名称
const getTypeName = (type) => {
  const nameMap = {
    health: '健康记录',
    sport: '运动记录',
    reminder: '提醒记录'
  }
  return nameMap[type] || type
}

// 加载历史记录
const loadHistory = async () => {
  loading.value = true
  try {
    const userId = userStore.userInfo?.id || 1
    
    // 构建查询参数
    const params = {
      pageNum: currentPage.value,
      pageSize: pageSize.value
    }
    
    if (filterType.value) {
      params.type = filterType.value
    }
    
    if (dateRange.value && dateRange.value.length === 2) {
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

// 筛选
const handleFilter = () => {
  currentPage.value = 1
  loadHistory()
}

// 重置
const handleReset = () => {
  filterType.value = ''
  dateRange.value = []
  currentPage.value = 1
  loadHistory()
}

// 查看详情
const handleView = (row) => {
  currentRecord.value = row
  editForm.value.title = row.title
  editForm.value.content = row.content
  dialogVisible.value = true
}

// 分页
const handleSizeChange = (val) => {
  pageSize.value = val
  loadHistory()
}

const handleCurrentChange = (val) => {
  currentPage.value = val
  loadHistory()
}

// 保存编辑
const handleSave = async () => {
  if (!currentRecord.value) return

  try {
    const updateData = {
      title: editForm.value.title,
      content: editForm.value.content,
      recordDate: currentRecord.value.date
    }
    
    await request.put(`/history/record/${currentRecord.value.id}`, updateData)
    
    ElMessage.success('保存成功')
    dialogVisible.value = false
    currentRecord.value = null
    
    // 重新加载列表以获取最新数据
    await loadHistory()
  } catch (error) {
    console.error('保存失败:', error)
    ElMessage.error(error.message || '保存失败')
  }
}

onMounted(() => {
  loadHistory()
  unbindHistorySync = bindHistorySync(loadHistory)
})

onBeforeUnmount(() => {
  if (unbindHistorySync) {
    unbindHistorySync()
    unbindHistorySync = null
  }
})
</script>

<style scoped>
.history-container {
  padding: 20px;
}

.card-header {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 18px;
  font-weight: 600;
}

.filter-form {
  margin-bottom: 20px;
}
</style>
