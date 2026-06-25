# 健康后台管理系统

基于 SpringBoot + Vue + MySQL 的健康管理后台系统

## 技术栈

- **后端**: Spring Boot 3.x, MyBatis Plus, MySQL
- **前端**: Vue 3, Element Plus, Axios
- **数据库**: MySQL 8.0

## 功能模块

### 用户管理
- 用户注册/登录
- 角色权限管理
- 个人信息管理

### 健康数据管理
- 健康指标记录
- 运动数据统计
- 饮食记录管理
- 睡眠质量监测

### 健康建议
- 个性化健康建议
- 健康知识库
- 专家咨询

## 快速开始

1. 启动 MySQL 数据库
2. 导入 `health-system.sql` 数据库脚本
3. 启动后端服务
4. 启动前端应用

## 开发环境

- JDK 17+
- Node.js 16+
- MySQL 8.0+
- Maven 3.6+

## 目录结构

```
health-system/
├── backend/          # Spring Boot 后端
├── frontend/         # Vue 前端
└── database/         # 数据库脚本
```

## 许可证

MIT License