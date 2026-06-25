<template>
  <div class="user-management">
    <el-card class="management-card fade-in">
      <div class="card-header">
        <h3>
          <el-icon :size="24" color="#409EFF"><UserFilled /></el-icon>
          用户管理
        </h3>
        <p class="subtitle">管理系统用户账号状态</p>
      </div>

      <!-- 搜索和筛选 -->
      <div class="filter-section">
        <el-input
          v-model="searchKeyword"
          placeholder="搜索用户名..."
          clearable
          class="search-input"
          @input="handleSearch"
        >
          <template #prefix>
            <el-icon><Search /></el-icon>
          </template>
        </el-input>

        <el-select
          v-model="statusFilter"
          placeholder="状态筛选"
          clearable
          class="status-filter"
          @change="handleFilter"
        >
          <el-option label="全部" value="" />
          <el-option label="正常" :value="1" />
          <el-option label="已禁用" :value="0" />
        </el-select>
      </div>

      <!-- 用户列表 -->
      <el-table
        v-loading="loading"
        :data="filteredUsers"
        style="width: 100%"
        class="user-table"
      >
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="username" label="用户名" width="150" />
        <el-table-column prop="email" label="邮箱" min-width="200" />
        <el-table-column prop="phone" label="手机号" width="130" />
        <el-table-column label="状态" width="120">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'danger'" size="small">
              {{ row.status === 1 ? '正常' : '已禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="注册时间" width="180">
          <template #default="{ row }">
            {{ formatDate(row.createTime) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <el-button
              v-if="row.status === 1"
              type="warning"
              size="small"
              @click="toggleUserStatus(row, 0)"
              :loading="operatingUserId === row.id"
            >
              <el-icon><Lock /></el-icon>
              禁用
            </el-button>
            <el-button
              v-else
              type="success"
              size="small"
              @click="toggleUserStatus(row, 1)"
              :loading="operatingUserId === row.id"
            >
              <el-icon><Unlock /></el-icon>
              启用
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- 分页 -->
      <div class="pagination-container">
        <el-pagination
          v-model:current-page="currentPage"
          v-model:page-size="pageSize"
          :total="total"
          :page-sizes="[10, 20, 50, 100]"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="handleSizeChange"
          @current-change="handleCurrentChange"
        />
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { UserFilled, Search, Lock, Unlock } from '@element-plus/icons-vue'
import request from '../utils/request'

const loading = ref(false)
const operatingUserId = ref(null)
const searchKeyword = ref('')
const statusFilter = ref('')
const currentPage = ref(1)
const pageSize = ref(10)
const total = ref(0)
const users = ref([])

// 过滤后的用户列表
const filteredUsers = computed(() => {
  let result = users.value

  // 按关键词搜索
  if (searchKeyword.value) {
    const keyword = searchKeyword.value.toLowerCase()
    result = result.filter(user =>
      user.username?.toLowerCase().includes(keyword) ||
      user.email?.toLowerCase().includes(keyword)
    )
  }

  // 按状态筛选
  if (statusFilter.value !== '') {
    result = result.filter(user => user.status === statusFilter.value)
  }

  return result
})

// 格式化日期
const formatDate = (dateStr) => {
  if (!dateStr) return '-'
  const date = new Date(dateStr)
  return date.toLocaleString('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit'
  })
}

// 获取用户列表
const fetchUsers = async () => {
  try {
    loading.value = true
    const response = await request.get('/user/admin/list', {
      params: {
        pageNum: currentPage.value,
        pageSize: pageSize.value
      }
    })

    if (response && response.data) {
      users.value = response.data.records || []
      total.value = response.data.total || 0
    }
  } catch (error) {
    console.error('获取用户列表失败:', error)
    ElMessage.error('获取用户列表失败')
  } finally {
    loading.value = false
  }
}

// 切换用户状态
const toggleUserStatus = async (user, newStatus) => {
  const actionText = newStatus === 1 ? '启用' : '禁用'

  try {
    await ElMessageBox.confirm(
      `确定要${actionText}用户 "${user.username}" 吗？`,
      '确认操作',
      {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      }
    )

    operatingUserId.value = user.id

    const response = await request.put(`/user/admin/toggle-status/${user.id}`, null, {
      params: { status: newStatus }
    })

    if (response && response.code === 200) {
      ElMessage.success(response.message || `${actionText}成功`)
      // 更新本地数据
      const targetUser = users.value.find(u => u.id === user.id)
      if (targetUser) {
        targetUser.status = newStatus
      }
    } else {
      ElMessage.error(response?.message || '操作失败')
    }
  } catch (error) {
    if (error !== 'cancel') {
      console.error('操作失败:', error)
      ElMessage.error(error.message || '操作失败')
    }
  } finally {
    operatingUserId.value = null
  }
}

// 搜索处理
const handleSearch = () => {
  currentPage.value = 1
}

// 筛选处理
const handleFilter = () => {
  currentPage.value = 1
}

// 分页大小变化
const handleSizeChange = (val) => {
  pageSize.value = val
  fetchUsers()
}

// 页码变化
const handleCurrentChange = (val) => {
  currentPage.value = val
  fetchUsers()
}

onMounted(() => {
  fetchUsers()
})
</script>

<style scoped>
.user-management {
  padding: 20px;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  min-height: 100vh;
}

.management-card {
  background: rgba(255, 255, 255, 0.95);
  backdrop-filter: blur(10px);
  border-radius: 16px;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.1);
  border: 1px solid rgba(255, 255, 255, 0.2);
  transition: all 0.4s cubic-bezier(0.4, 0, 0.2, 1);
  overflow: hidden;
}

.card-header {
  padding: 20px;
  border-bottom: 2px solid #f0f0f0;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
}

.card-header h3 {
  margin: 0 0 8px 0;
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 24px;
}

.subtitle {
  margin: 0;
  opacity: 0.9;
  font-size: 14px;
}

.filter-section {
  padding: 20px;
  display: flex;
  gap: 15px;
  background: #f9fafb;
  border-bottom: 1px solid #e5e7eb;
}

.search-input {
  flex: 1;
  max-width: 400px;
}

.status-filter {
  width: 150px;
}

.user-table {
  margin: 20px;
  width: calc(100% - 40px);
}

.pagination-container {
  padding: 20px;
  display: flex;
  justify-content: flex-end;
  border-top: 1px solid #e5e7eb;
}

.fade-in {
  animation: fadeIn 0.5s ease-in;
}

@keyframes fadeIn {
  from {
    opacity: 0;
    transform: translateY(20px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}
</style>
