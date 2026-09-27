<template>
  <router-view />
</template>

<style>
#app {
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif;
  -webkit-font-smoothing: antialiased;
  -moz-osx-font-smoothing: grayscale;
  color: #1E293B;
}

body {
  margin: 0;
  padding: 0;
  background: #EBF5FF;
}

/* 全局 Element Plus 微调 */
.el-button--primary {
  --el-button-bg-color: #3B82F6;
  --el-button-border-color: #3B82F6;
  --el-button-hover-bg-color: #2563eb;
  --el-button-hover-border-color: #2563eb;
}

.el-card {
  --el-card-border-color: #F0F4F8;
  border-radius: 16px;
}

.el-table {
  --el-table-border-color: #F0F4F8;
}
</style>