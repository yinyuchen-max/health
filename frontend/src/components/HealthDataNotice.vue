<template>
  <el-card shadow="never">
    <el-alert
      title="请先添加数据"
      :description="message || '暂无可分析的数据，请先添加健康记录或运动记录。'"
      type="info"
      show-icon
      :closable="false"
    />
    <div class="notice-actions">
      <el-button type="primary" @click="$router.push('/app/health')">添加健康记录</el-button>
      <el-button @click="$router.push('/app/sport')">添加运动记录</el-button>
    </div>
  </el-card>
</template>

<script setup>
defineProps({ message: { type: String, default: '' } })
</script>

<style scoped>
.notice-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  margin-top: 20px;
}
.notice-actions .el-button { margin-left: 0; }
</style>
