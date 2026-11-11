<template>
  <router-view />
</template>

<style>
#app {
  font-family: "Noto Sans SC", "PingFang SC", "Microsoft YaHei", sans-serif;
  -webkit-font-smoothing: antialiased;
  -moz-osx-font-smoothing: grayscale;
  color: #1C3D36;
}

body {
  margin: 0;
  padding: 0;
  background: #F4F7F6;
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