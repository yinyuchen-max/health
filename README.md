# 健康管理系统 

基于 SpringBoot + Vue 3 + MySQL 的智能健康管理平台，集成 AI 健康顾问功能。

## ✨ 主要特性

- 🔐 **用户认证**: JWT Token 登录，角色权限管理
- 📊 **健康数据**: 血压、血糖、心率、体重等指标记录与追踪
- 🏃‍♂️ **运动记录**: 运动类型、时长、消耗热量统计
- 💡 **智能分析**: 自动计算 BMI、风险评估、营养建议
- 🤖 **AI 健康顾问**: 基于 DeepSeek/Qwen 大模型的个性化健康咨询
- 💾 **会话隔离**: 每个用户的 AI 对话独立存储，支持上下文记忆
- 🎨 **现代化 UI**: Element Plus + 响应式设计

## 🛠️ 技术栈

### 后端
- **框架**: Spring Boot 3.x
- **ORM**: MyBatis Plus
- **数据库**: MySQL 8.0
- **AI 集成**: LangChain4j 1.17.0 (DeepSeek/Qwen)
- **安全**: JWT (jjwt)
- **工具**: Lombok, Hutool

### 前端
- **框架**: Vue 3 (Composition API)
- **UI 库**: Element Plus
- **状态管理**: Pinia
- **路由**: Vue Router 4
- **HTTP 客户端**: Axios
- **Markdown 渲染**: marked.js
- **图表**: ECharts

## 📦 功能模块

### 1. 用户管理
- ✅ 用户注册/登录
- ✅ JWT Token 认证
- ✅ 个人信息管理（身高、体重、年龄）
- ✅ 密码修改

### 2. 健康记录
- ✅ 血压记录（收缩压/舒张压）
- ✅ 心率监测
- ✅ 血糖检测
- ✅ 体重追踪
- ✅ 历史记录查询与分页

### 3. 运动记录
- ✅ 运动类型选择（跑步、游泳、骑行等）
- ✅ 运动时长与卡路里消耗
- ✅ 强度等级（低/中/高）
- ✅ 运动数据统计

### 4. 智能健康分析
- ✅ BMI 计算与健康评估
- ✅ 血压风险评估
- ✅ 心血管风险分析
- ✅ 睡眠质量分析
- ✅ 压力水平评估
- ✅ 个性化饮食建议
- ✅ 运动计划推荐

### 5. AI 健康顾问 🤖
- ✅ 自然语言健康咨询
- ✅ 基于用户真实数据的个性化建议
- ✅ 多轮对话上下文记忆
- ✅ Markdown 格式化输出
- ✅ 会话历史本地持久化
- ✅ 支持 DeepSeek / 通义千问模型

### 6. 提醒设置
- ✅ 用药提醒
- ✅ 体检提醒
- ✅ 运动提醒
- ✅ 自定义提醒时间

## 🚀 快速开始

### 前置要求

- JDK 17+
- Node.js 16+
- MySQL 8.0+
- Maven 3.6+

### 1. 数据库准备

```bash
# 创建数据库
CREATE DATABASE health_system CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

# 导入 SQL 脚本
mysql -u root -p health_system < database/health-system.sql
```

### 2. 后端启动

```bash
cd backend

# 配置环境变量（必需）
# Windows PowerShell
$env:DASHSCOPE_API_KEY="sk-your-api-key-here"

# Linux/Mac
export DASHSCOPE_API_KEY="sk-your-api-key-here"

# 或者创建 application-local.yml（见下方配置说明）

# 编译并运行
mvn clean package -DskipTests
java -jar target/health-system-backend-1.0.0.jar

# 或使用 Maven 插件直接运行
mvn spring-boot:run
```

后端服务将运行在 `http://localhost:8080`

### 3. 前端启动

```bash
cd frontend

# 安装依赖
npm install

# 开发模式运行
npm run dev

# 生产环境构建
npm run build
```

前端应用将运行在 `http://localhost:5173`

## ⚙️ 环境配置

### 敏感信息管理

**重要**: 不要将 API Key 等敏感信息提交到 Git！

#### 方法一：环境变量（推荐）

在项目根目录创建 `.env` 文件（已加入 `.gitignore`）：

```env
# AI API Key（必需）
DASHSCOPE_API_KEY=sk-your-actual-api-key-here

# 可选：AI 模型配置
AI_MODEL_NAME=deepseek-v4-flash
AI_BASE_URL=https://api.deepseek.com/v1
AI_TEMPERATURE=0.3
AI_TIMEOUT=60

# 可选：数据库密码覆盖
DB_PASSWORD=your-database-password

# 可选：JWT Secret（至少 256 位）
JWT_SECRET=your-jwt-secret-key-min-256-bits-long
```

#### 方法二：本地配置文件

创建 `backend/src/main/resources/application-local.yml`（已加入 `.gitignore`）：

```yaml
langchain4j:
  openai:
    api-key: sk-your-actual-api-key-here  # 替换为你的真实 API Key

spring:
  datasource:
    password: your-database-password  # 如果需要覆盖默认密码

jwt:
  secret: your-jwt-secret-key-min-256-bits-long  # 至少 256 位
```

然后在 IDE 运行配置中添加 VM options：
```
-Dspring.profiles.active=local
```

### AI 模型配置

系统支持多种大语言模型，在 `application.yml` 中配置：

#### DeepSeek（默认）
```yaml
langchain4j:
  openai:
    api-key: ${DASHSCOPE_API_KEY}
    model-name: deepseek-v4-flash
    base-url: https://api.deepseek.com/v1
```

#### 通义千问（阿里云）
```yaml
langchain4j:
  openai:
    api-key: ${DASHSCOPE_API_KEY}
    model-name: qwen-plus
    base-url: https://dashscope.aliyuncs.com/compatible-mode/v1
```

获取 API Key：
- DeepSeek: [https://platform.deepseek.com](https://platform.deepseek.com)
- 通义千问: [https://dashscope.aliyun.com](https://dashscope.aliyun.com)

## 📁 项目结构

```
health/
├── backend/                          # Spring Boot 后端
│   ├── src/main/java/com/health/
│   │   ├── common/                   # 公共组件
│   │   │   ├── config/               # 配置类（CORS, JWT, LangChain4j）
│   │   │   ├── controller/           # 全局异常处理
│   │   │   ├── enums/                # 枚举定义
│   │   │   ├── filter/               # JWT 过滤器
│   │   │   └── utils/                # 工具类（Result, JwtUtils）
│   │   ├── controller/               # REST 控制器
│   │   │   ├── UserController.java
│   │   │   ├── HealthRecordController.java
│   │   │   ├── SportRecordController.java
│   │   │   ├── SmartHealthController.java
│   │   │   ├── ChatController.java   # AI 对话接口
│   │   │   └── ...
│   │   ├── domain/                   # 领域对象
│   │   │   ├── entity/               # 实体类
│   │   │   ├── dto/                  # 数据传输对象
│   │   │   └── vo/                   # 视图对象
│   │   ├── mapper/                   # MyBatis Mapper
│   │   ├── service/                  # 业务逻辑层
│   │   │   ├── impl/                 # 服务实现
│   │   │   │   ├── ChatServiceImpl.java      # AI 对话服务
│   │   │   │   ├── SmartHealthServiceImpl.java # 智能分析服务
│   │   │   │   └── ...
│   │   │   └── ...
│   │   └── HealthSystemBackendApplication.java
│   ├── src/main/resources/
│   │   ├── application.yml           # 主配置文件
│   │   └── static/                   # 静态资源（前端打包后）
│   ├── pom.xml                       # Maven 依赖
│   └── Dockerfile                    # Docker 构建文件
│
├── frontend/                         # Vue 3 前端
│   ├── src/
│   │   ├── components/               # 可复用组件
│   │   │   ├── Layout.vue            # 主布局
│   │   │   └── AnimatedCharacters.vue # 动画角色
│   │   ├── views/                    # 页面视图
│   │   │   ├── Login.vue             # 登录页
│   │   │   ├── Register.vue          # 注册页
│   │   │   ├── Dashboard.vue         # 首页仪表盘
│   │   │   ├── HealthRecord.vue      # 健康记录
│   │   │   ├── SportRecord.vue       # 运动记录
│   │   │   ├── Analytics.vue         # 数据分析
│   │   │   ├── AiChat.vue            # AI 对话页面
│   │   │   ├── SmartHealth.vue       # 智能健康分析
│   │   │   ├── ReminderConfig.vue    # 提醒设置
│   │   │   ├── UserProfile.vue       # 个人信息
│   │   │   └── UserManagement.vue    # 用户管理
│   │   ├── store/                    # Pinia 状态管理
│   │   │   ├── user.js               # 用户状态
│   │   │   ├── analytics.js          # 分析数据
│   │   │   └── reminder.js           # 提醒配置
│   │   ├── router/                   # 路由配置
│   │   ├── utils/                    # 工具函数
│   │   │   ├── request.js            # Axios 封装
│   │   │   └── historySync.js        # 历史同步
│   │   ├── App.vue                   # 根组件
│   │   └── main.js                   # 入口文件
│   ├── public/                       # 公共资源
│   ├── index.html                    # HTML 模板
│   ├── vite.config.js                # Vite 配置
│   ├── package.json                  # npm 依赖
│   └── Dockerfile                    # Docker 构建文件
│
├── database/                         # 数据库脚本
│   └── health-system.sql             # 建表与初始数据
│
├── docker-compose.yml                # Docker Compose 编排
├── .gitignore                        # Git 忽略文件
└── README.md                         # 项目文档
```

## 🔑 核心 API 接口

### 认证相关
- `POST /api/auth/register` - 用户注册
- `POST /api/auth/login` - 用户登录
- `GET /api/auth/info` - 获取当前用户信息

### 健康记录
- `POST /api/health/records` - 添加健康记录
- `GET /api/health/records/{userId}` - 查询健康记录列表
- `DELETE /api/health/records/{id}` - 删除健康记录
- `PUT /api/health/records/{id}` - 更新健康记录

### 运动记录
- `POST /api/sport/records` - 添加运动记录
- `GET /api/sport/records/{userId}` - 查询运动记录列表
- `DELETE /api/sport/records/{id}` - 删除运动记录
- `PUT /api/sport/records/{id}` - 更新运动记录

### 智能分析
- `GET /api/smart-health/overview/{userId}` - 获取健康概览
- `GET /api/smart-health/risk-assessment/{userId}` - 风险评估
- `GET /api/smart-health/nutrition-advice/{userId}` - 营养建议
- `GET /api/smart-health/exercise-plan/{userId}` - 运动计划

### AI 对话
- `POST /api/chat/send` - 发送 AI 对话请求
  ```json
  {
    "message": "我的血压正常吗？",
    "userId": 1
  }
  ```

## 🧪 测试

### 后端测试
```bash
cd backend
mvn test
```

### 前端测试
```bash
cd frontend
npm run test
```

##  Docker 部署

```bash
# 使用 Docker Compose 一键启动
docker-compose up -d

# 查看日志
docker-compose logs -f

# 停止服务
docker-compose down
```

## 📝 注意事项

1. **首次使用前请修改默认密码**
   - 管理员账号: `admin / admin123`
   - 普通用户: `user1 / user123`

2. **AI 功能需要配置 API Key**
   - 未配置 API Key 时，AI 对话功能将无法使用
   - 系统会自动回退到规则引擎提供基础建议

3. **数据库连接**
   - 默认数据库: `health_system`
   - 默认用户名: `root`
   - 默认密码: `123456`（请在生产环境中修改）

4. **端口占用**
   - 后端: 8080
   - 前端: 5173
   - MySQL: 3306

## 🤝 贡献指南

欢迎提交 Issue 和 Pull Request！

1. Fork 本仓库
2. 创建功能分支 (`git checkout -b feature/AmazingFeature`)
3. 提交更改 (`git commit -m 'Add some AmazingFeature'`)
4. 推送到分支 (`git push origin feature/AmazingFeature`)
5. 开启 Pull Request

## 📄 许可证

本项目采用 MIT License - 详见 [LICENSE](LICENSE) 文件

## 👨‍ 作者

- 开发团队

## 🙏 致谢

感谢以下开源项目：
- [Spring Boot](https://spring.io/projects/spring-boot)
- [Vue.js](https://vuejs.org/)
- [Element Plus](https://element-plus.org/)
- [LangChain4j](https://github.com/langchain4j/langchain4j)
- [MyBatis Plus](https://baomidou.com/)
- [ECharts](https://echarts.apache.org/)

---

⭐ 如果这个项目对你有帮助，请给个 Star 支持一下！
