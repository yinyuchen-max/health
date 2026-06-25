# 健康管理系统使用帮助

## 系统功能

### 1. 用户管理
- **登录/注册**: 用户可以注册账号并登录系统
- **个人信息**: 查看和编辑个人资料、身高体重等信息

### 2. 健康记录管理
- **血压记录**: 记录收缩压和舒张压
- **心率监测**: 记录每日心率数据
- **血糖检测**: 记录血糖水平
- **体重管理**: 跟踪体重变化趋势

### 3. 运动记录管理
- **运动类型**: 支持跑步、游泳、骑车等多种运动
- **时长统计**: 记录每次运动的时长
- **卡路里计算**: 估算运动消耗的卡路里
- **强度分级**: 低、中、高三个强度等级

## 快速开始

### 环境要求
- JDK 17+
- Node.js 16+
- MySQL 8.0+
- Maven 3.6+

### 安装步骤

1. **数据库设置**
   ```sql
   CREATE DATABASE health_system;
   USE health_system;
   SOURCE database/health_system.sql;
   ```

2. **修改数据库配置**
   编辑 `backend/src/main/resources/application.yml`，更新数据库连接信息：
   ```yaml
   spring:
     datasource:
       url: jdbc:mysql://localhost:3306/health_system?useSSL=false&serverTimezone=UTC
       username: your_username
       password: your_password
   ```

3. **启动后端服务**
   ```bash
   cd backend
   mvn clean compile
   mvn spring-boot:run
   ```

4. **启动前端服务**
   ```bash
   cd frontend
   npm install
   npm run dev
   ```

5. **访问系统**
   - 打开浏览器访问 http://localhost:3000
   - 使用默认账号登录（用户名: admin，密码: 123456）

## 常见问题

### Q: 无法连接到数据库？
A: 请检查：
1. MySQL服务是否已启动
2. 数据库用户名和密码是否正确
3. 数据库名称是否为health_system

### Q: 前端页面显示异常？
A: 请尝试：
1. 清除浏览器缓存
2. 重新运行npm install
3. 检查控制台是否有错误信息

### Q: API接口调用失败？
A: 请检查：
1. 后端服务是否正常启动
2. 跨域配置是否正确
3. JWT token是否有效

## 技术支持

如有问题，请联系开发者或查看代码文档。