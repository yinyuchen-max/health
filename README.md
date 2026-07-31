# 健康管理系统 

基于 Spring Boot + Vue 3 + MySQL 的智能健康管理平台，集成 AI 健康顾问、健康知识 RAG、自然语言医生预约、邮件提醒调度（RabbitMQ）和 Redis 全栈缓存。

## ✨ 主要特性

- 🔐 **用户认证**: JWT Token 登录 + Redis 黑名单注销，角色权限管理（ADMIN/USER）
- 🛡️ **安全防护**: Spring Security 过滤链、HTTP 安全响应头、接口限流（Redis 分布式）、密码自动迁移
- 📊 **健康数据**: 血压、血糖、心率、体重等指标记录与追踪
- 🏃‍♂️ **运动记录**: 运动类型、时长、消耗热量统计
- 💡 **智能分析**: 自动计算 BMI、风险评估、营养建议，Redis 缓存热点结果
- 🤖 **AI 健康顾问**: 基于 DeepSeek/Qwen 大模型的个性化健康咨询，Redis 持久化会话记忆
- 🏥 **AI 医生预约**: 自然语言提取预约信息，多轮补充，确认后写入数据库
- 📚 **健康知识 RAG**: 基于 Redis 向量存储的本地 Markdown 知识库检索与增强回答
- 💾 **会话隔离**: 每个用户的 AI 对话独立存储于 Redis，支持上下文记忆
- ⏰ **邮件提醒**: 定时调度 → RabbitMQ 消息队列 → 异步发送 HTML 邮件通知
- 🌐 **CORS 跨域**: 可配置的跨域访问支持，开发/生产环境灵活切换
- 🎨 **现代化 UI**: Element Plus + 响应式设计

## 🛠️ 技术栈

### 后端
- **框架**: Spring Boot 3.1.0 + Spring Security
- **ORM**: MyBatis Plus 3.5.3
- **数据库**: MySQL 8.0
- **缓存/消息**: Redis 7.4（Lettuce + 连接池）、Spring Cache
- **消息队列**: RabbitMQ（Spring AMQP + Topic 交换机 + 死信队列）
- **邮件**: Spring Boot Mail（JavaMailSender + HTML 模板）
- **定时任务**: @EnableScheduling + @Scheduled
- **AI 集成**: LangChain4j 1.17.0（DeepSeek/Qwen）
- **安全**: JWT (jjwt 0.11.5)、Spring Security 6、Redis Token 黑名单
- **AOP**: 自定义 @RateLimit 限流注解 + RateLimitAspect
- **工具**: Lombok, Hutool, MapStruct

### 前端
- **框架**: Vue 3 (Composition API)
- **UI 库**: Element Plus
- **状态管理**: Pinia
- **路由**: Vue Router 4
- **HTTP 客户端**: Axios
- **Markdown 渲染**: marked.js
- **图表**: ECharts

### 基础设施
- **容器化**: Docker Compose（MySQL + Redis + RabbitMQ + Backend + Frontend）
- **Web 服务器**: Nginx（前端静态资源 + 反向代理）

## 📦 功能模块

### 1. 用户管理与安全
- ✅ 用户注册/登录（JWT Token 认证）
- ✅ 角色权限（USER / ADMIN），Spring Security 方法级鉴权
- ✅ JWT Token 黑名单（Redis 存储，支持注销/强制下线）
- ✅ 个人信息管理（身高、体重、年龄、邮箱）
- ✅ 密码修改 + BCrypt 自动迁移（PasswordMigrationRunner）
- ✅ 接口限流（@RateLimit 注解，Redis 滑动窗口，按 IP/用户维度）
- ✅ HTTP 安全响应头（X-Frame-Options、X-XSS-Protection、Referrer-Policy 等）
- ✅ 401/403 统一 JSON 错误响应

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
- ✅ 血压风险评估 + 心血管风险分析
- ✅ 睡眠质量分析 + 压力水平评估
- ✅ 个性化饮食建议 + 运动计划推荐
- ✅ Redis 缓存智能分析结果（10 分钟 TTL）

### 5. AI 健康顾问 🤖
- ✅ 自然语言健康咨询
- ✅ 基于用户真实数据的个性化建议
- ✅ 多轮对话上下文记忆（Redis 持久化，RedisChatMemoryStore）
- ✅ Markdown 格式化输出
- ✅ 会话历史 Redis 持久化
- ✅ 支持 DeepSeek / 通义千问模型
- ✅ AI 超时降级处理（60s 超时自动回退）

### 6. 提醒设置与邮件通知 ⏰
- ✅ 用药提醒、体检提醒、运动提醒、血压/血糖/体重提醒
- ✅ 自定义提醒时间（HH:mm 格式）
- ✅ 频率控制（每日/每周/自定义）
- ✅ 定时调度器（ReminderScheduler）每分钟扫描匹配提醒
- ✅ Redis 去重（setIfAbsent + 25h TTL，防止当日重复发送）
- ✅ RabbitMQ 异步投递（Topic 交换机 + 按类型路由）
- ✅ 死信队列（重试 3 次失败后进入，便于排查）
- ✅ HTML 格式邮件通知（Java Text Block 模板）
- ✅ 通知记录落库（reminder_notification 表）

### 7. AI 医生预约 🏥
- ✅ AI 从自然语言中结构化提取姓名、年龄、预约时间、电话和科室
- ✅ 字段顺序任意，支持"泌尿科"等科室别名归一化
- ✅ 缺少或无法识别字段时给出明确提示
- ✅ 支持多轮补充和修改预约信息
- ✅ 展示完整预约信息并要求用户确认
- ✅ 只有用户回复"确认预约"后才写入 MySQL
- ✅ 支持取消预约填写
- ✅ 应用启动时自动检查并创建 `doctor_appointment` 表

### 8. CORS 跨域 🌐
- ✅ 可配置允许的源（`cors.allowed-origins`，多个逗号分隔）
- ✅ 支持 GET/POST/PUT/DELETE/OPTIONS 方法
- ✅ 允许携带凭证（Cookie/Authorization）
- ✅ 预检请求缓存 1 小时

## 🚀 快速开始

### 前置要求

- JDK 17+
- Node.js 16+
- MySQL 8.0+
- Redis 7.x
- RabbitMQ 3.x（management 版本）
- Maven 3.6+

### 1. 数据库准备

首次安装，执行完整数据库脚本：

```bash
mysql -u root -p < backend/database/health_system.sql
```

已有数据库只需要增加医生预约表：

```bash
mysql -u root -p < backend/database/add_doctor_appointment.sql
```

`DoctorAppointmentServiceImpl` 在应用启动时也会执行 `CREATE TABLE IF NOT EXISTS`，但生产环境仍建议通过 SQL 脚本管理数据库结构。

### 2. 中间件准备

#### Redis

```bash
# Docker 方式（推荐）
docker run -d --name health-redis -p 6379:6379 redis/redis-stack-server:7.4.0-v3

# 验证连接
redis-cli ping
# 应返回 PONG
```

#### RabbitMQ

```bash
# Docker 方式（推荐，必须使用 management 版本以启用 Web 管理界面）
docker run -d --name health-rabbitmq \
  -p 5672:5672 \
  -p 15672:15672 \
  -e RABBITMQ_DEFAULT_USER=admin \
  -e RABBITMQ_DEFAULT_PASS=admin123 \
  rabbitmq:3-management
```

> **注意**: RabbitMQ 4.x 取消了默认 `guest` 用户，必须通过 `RABBITMQ_DEFAULT_USER` 和 `RABBITMQ_DEFAULT_PASS` 显式创建用户。使用 IDEA Docker 面板创建时，确保环境变量名和值之间没有多余空格。

管理界面: http://localhost:15672（admin / admin123）

### 3. 后端启动

```bash
cd backend

# 配置环境变量（AI 对话与预约信息提取必需）
# Windows PowerShell
$env:DEEPSEEK_API_KEY="sk-your-deepseek-key"
$env:DASHSCOPE_API_KEY="sk-your-dashscope-key"

# Linux/Mac
export DEEPSEEK_API_KEY="sk-your-deepseek-key"
export DASHSCOPE_API_KEY="sk-your-dashscope-key"

# 邮件配置（可选，用于提醒邮件发送）
export MAIL_USERNAME="your-email@qq.com"
export MAIL_PASSWORD="your-smtp-auth-code"

# 或者创建 application-local.yml（见下方配置说明）

# 编译并运行
mvn clean package -DskipTests
java -jar target/health-system-backend-1.0.0.jar

# 或使用 Maven 插件直接运行
mvn spring-boot:run
```

后端服务将运行在 `http://localhost:8080`

### 4. 前端启动

```bash
cd frontend

# 安装依赖
npm install

# 开发模式运行
npm run dev

# 生产环境构建
npm run build
```

前端应用将运行在 `http://localhost:3000`

## ⚙️ 环境配置

### 敏感信息管理

**重要**: 不要将 API Key 等敏感信息提交到 Git！

#### 方法一：环境变量（推荐）

在项目根目录创建 `.env` 文件（已加入 `.gitignore`）：

```env
# DeepSeek 聊天模型 Key（AI 对话和预约信息提取必需）
DEEPSEEK_API_KEY=sk-your-deepseek-key

# DashScope Embedding Key（健康知识 RAG 必需）
DASHSCOPE_API_KEY=sk-your-dashscope-key

# 邮件配置（QQ邮箱为例，需开启 SMTP 并获取授权码）
MAIL_HOST=smtp.qq.com
MAIL_PORT=465
MAIL_USERNAME=your-email@qq.com
MAIL_PASSWORD=your-smtp-auth-code

# CORS 跨域配置（多个用逗号分隔）
CORS_ALLOWED_ORIGINS=http://localhost:3000,http://localhost:80

# RabbitMQ
RABBITMQ_USER=admin
RABBITMQ_PASS=admin123

# 提醒调度器
REMINDER_SCHEDULER_ENABLED=true

# 可选：Spring 数据源覆盖
SPRING_DATASOURCE_URL=jdbc:mysql://localhost:3306/health_system?useUnicode=true&characterEncoding=utf-8&useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true
SPRING_DATASOURCE_USERNAME=root
SPRING_DATASOURCE_PASSWORD=your-database-password

# 可选：JWT Secret（至少 256 位）
JWT_SECRET=your-jwt-secret-key-min-256-bits-long
```

Docker Compose 会自动读取项目根目录的 `.env`。如果通过 `mvn spring-boot:run` 或 IDE 直接启动后端，需要将这些变量导出到当前终端，或配置到 IDE 的 Environment variables 中。

> **IDEA 用户注意**: IDEA 直接运行 Spring Boot 不会读取项目根目录的 `.env` 文件。需要在 IDEA 运行配置的 Environment variables 中手动填写，或在 `application.yml` 中写入默认值。

#### 方法二：本地配置文件

创建 `backend/src/main/resources/application-local.yml`（已加入 `.gitignore`）：

```yaml
langchain4j:
  openai:
    chat-api-key: sk-your-deepseek-key
    embedding-api-key: sk-your-dashscope-key

spring:
  datasource:
    url: jdbc:mysql://localhost:3306/health_system?useUnicode=true&characterEncoding=utf-8&useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true
    username: root
    password: your-database-password

  mail:
    host: smtp.qq.com
    port: 465
    username: your-email@qq.com
    password: your-smtp-auth-code
    properties:
      mail:
        smtp:
          auth: true
          ssl:
            enable: true

jwt:
  secret: your-jwt-secret-key-min-256-bits-long  # 至少 256 位

cors:
  allowed-origins: http://localhost:3000
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
    chat-api-key: ${DEEPSEEK_API_KEY:}
    model-name: deepseek-v4-flash
    base-url: https://api.deepseek.com/v1
```

#### 通义千问（阿里云）
```yaml
langchain4j:
  openai:
    chat-api-key: ${DASHSCOPE_API_KEY:}
    model-name: qwen-plus
    base-url: https://dashscope.aliyuncs.com/compatible-mode/v1
```

获取 API Key：
- DeepSeek: [https://platform.deepseek.com](https://platform.deepseek.com)
- 通义千问: [https://dashscope.aliyun.com](https://dashscope.aliyun.com)

### 邮件提醒配置

系统使用 QQ 邮箱 SMTP 发送提醒邮件：

1. 登录 QQ 邮箱 → 设置 → 账户
2. 找到「POP3/IMAP/SMTP/Exchange/CardDAV/CalDAV 服务」
3. 开启「IMAP/SMTP 服务」
4. 按提示获取授权码
5. 将邮箱地址和授权码填入 `application.yml` 或环境变量

## 📁 项目结构

```
health/
├── backend/                          # Spring Boot 后端
│   ├── src/main/java/com/health/
│   │   ├── common/                   # 公共组件
│   │   │   ├── annotation/           # 自定义注解（@RateLimit）
│   │   │   ├── aspect/               # AOP 切面（RateLimitAspect）
│   │   │   ├── config/               # 配置类
│   │   │   │   ├── SecurityConfig.java       # Spring Security + CORS
│   │   │   │   ├── RedisConfig.java          # Redis 序列化 + 缓存管理器
│   │   │   │   ├── RabbitMQConfig.java       # RabbitMQ 队列/交换机/死信
│   │   │   │   ├── RagRedisProperties.java   # RAG Redis 配置属性
│   │   │   │   └── PasswordMigrationRunner.java # 密码 BCrypt 自动迁移
│   │   │   ├── exception/            # 自定义异常（Forbidden, Unauthorized, RateLimit）
│   │   │   ├── filter/               # JWT 认证过滤器
│   │   │   └── utils/                # 工具类（Result, JwtUtil, SecurityUtil）
│   │   ├── controller/               # REST 控制器
│   │   │   ├── UserController.java
│   │   │   ├── HealthRecordController.java
│   │   │   ├── SportRecordController.java
│   │   │   ├── SmartHealthController.java
│   │   │   ├── ChatController.java   # AI 对话接口
│   │   │   ├── ReminderController.java
│   │   │   └── ...
│   │   ├── domain/                   # 领域对象
│   │   │   ├── entity/               # 实体类（含 ReminderNotification）
│   │   │   ├── dto/                  # 数据传输对象（含 ReminderMessage）
│   │   │   └── vo/                   # 视图对象
│   │   ├── mapper/                   # MyBatis Mapper
│   │   │   └── ReminderNotificationMapper.java
│   │   ├── service/                  # 业务逻辑层
│   │   │   ├── impl/                 # 服务实现
│   │   │   │   ├── ChatServiceImpl.java
│   │   │   │   ├── RedisChatMemoryStore.java       # Redis 对话记忆
│   │   │   │   ├── HealthKnowledgeRedisRepository.java # Redis 向量存储
│   │   │   │   ├── TokenBlacklistServiceImpl.java  # JWT 黑名单
│   │   │   │   ├── SmartHealthServiceImpl.java
│   │   │   │   └── ...
│   │   │   ├── ReminderScheduler.java       # 定时调度（MQ 生产者）
│   │   │   ├── ReminderEmailConsumer.java   # MQ 消费者（发邮件）
│   │   │   ├── ReminderEmailService.java    # HTML 邮件构建与发送
│   │   │   ├── RateLimitService.java        # 限流服务
│   │   │   ├── TokenBlacklistService.java   # Token 黑名单接口
│   │   │   └── ...
│   │   └── HealthSystemBackendApplication.java  # @EnableScheduling + @EnableAsync
│   ├── src/main/resources/
│   │   ├── application.yml           # 主配置文件
│   │   ├── rag/health-knowledge.md    # 本地健康知识库
│   │   └── static/                   # 静态资源（前端打包后）
│   ├── database/
│   │   ├── health_system.sql          # 完整建库与初始数据
│   │   ├── add_doctor_appointment.sql # 预约表增量脚本
│   │   └── add_role_column.sql        # 角色字段增量脚本
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
│   ├── nginx.conf                    # Nginx 配置
│   ├── package.json                  # npm 依赖
│   └── Dockerfile                    # Docker 构建文件
│
├── docker-compose.yml                # Docker Compose 编排（5 个服务）
├── .env.example                      # 环境变量示例
├── .gitignore                        # Git 忽略文件
└── README.md                         # 项目文档
```

## 🔄 提醒邮件调度架构

```
┌─────────────────┐     ┌──────────────┐     ┌────────────────────┐
│ ReminderScheduler│────▶│  RabbitMQ    │────▶│ ReminderEmailConsumer│
│ (@Scheduled)     │     │  Topic交换机  │     │ (@RabbitListener)    │
│ 每分钟扫描匹配    │     │ → 邮件队列    │     │ 查询用户 + 发送邮件   │
│ Redis去重 + 频率  │     │ → 死信队列    │     │ 失败自动重试 3 次     │
└─────────────────┘     └──────────────┘     └────────────────────┘
         │                      │                       │
         ▼                      ▼                       ▼
  reminder_notification    消息持久化          HTML 邮件（QQ SMTP）
  表（记录通知历史）       死信人工排查        通知用户按时健康检查
```

**RabbitMQ 队列结构**:
- `reminder.exchange`（Topic 交换机）
- `reminder.email.queue`（持久化，按提醒类型路由）
- `reminder.email.dead.queue`（死信队列，重试 3 次后进入）

**消息格式**（JSON 序列化）:
```json
{
  "userId": 10,
  "preferenceId": 3,
  "type": "bloodPressure",
  "scheduledTime": "21:05"
}
```

## 🔒 接口限流

基于 Redis + Lua 脚本的分布式限流机制，通过 `@RateLimit` 注解声明式配置，AOP 切面自动拦截。

### 实现原理

```
客户端请求 → RateLimitAspect（AOP 切面）
                ↓
        解析限流维度（IP / 用户）
                ↓
        RateLimitService（Lua 脚本原子性 INCR + EXPIRE）
                ↓
        未超限 → 执行业务逻辑
        已超限 → 抛出 RateLimitException（HTTP 429）
```

### 已配置限流规则

| 接口 | Key | 上限 | 窗口 | 维度 |
|------|-----|------|------|------|
| 登录 | `login` | 5 次 | 1 分钟 | IP |
| 注册 | `register` | 3 次 | 1 分钟 | IP |
| AI 对话发送 | `chat-send` | 10 次 | 1 分钟 | 用户 |
| 智能健康分析 | `smart-overview` | 10 次 | 1 分钟 | 用户 |
| 健康记录写入 | `health-record-write` | 30 次 | 1 分钟 | 用户 |
| 运动记录写入 | `sport-record-write` | 30 次 | 1 分钟 | 用户 |
| 历史记录写入 | `history-record-write` | 30 次 | 1 分钟 | 用户 |
| 提醒偏好写入 | `reminder-write` | 20 次 | 1 分钟 | 用户 |
| 提醒智能推荐 | `smart-recommendations` | 10 次 | 1 分钟 | 用户 |
| 提醒批量操作 | `reminder-bulk` | 5 次 | 1 分钟 | 用户 |

### 使用方式

在 Controller 方法上添加 `@RateLimit` 注解：

```java
// 每个 IP 每分钟最多 5 次登录请求
@RateLimit(key = "login", maxRequests = 5, timeWindow = 1, timeUnit = TimeUnit.MINUTES)
public Result<?> login(@RequestBody LoginDTO dto) { ... }

// 每个登录用户每分钟最多 10 次聊天请求
@RateLimit(key = "chat-send", maxRequests = 10, timeWindow = 1, timeUnit = TimeUnit.MINUTES, limitBy = LimitType.USER)
public Result<?> sendMessage(@RequestBody ChatRequest request) { ... }
```

### 限流维度

- **IP 维度** (`LimitType.IP`): 适用于未登录接口（登录、注册），基于客户端真实 IP（支持 Nginx X-Forwarded-For）
- **用户维度** (`LimitType.USER`): 适用于已登录接口，基于当前 JWT 解析的用户名

### 容错机制

- Redis 异常时**自动放行**，不影响正常业务
- Lua 脚本保证 INCR + EXPIRE 的原子性，避免计数器和 TTL 不一致
- 超限返回 HTTP 429 + `请求过于频繁，请稍后再试`

## 🔑 核心 API 接口

### 认证相关
- `POST /api/user/register` - 用户注册
- `POST /api/user/login` - 用户登录（返回 JWT Token）
- `POST /api/user/logout` - 用户注销（Token 加入 Redis 黑名单）
- `GET /api/user/info` - 获取当前用户信息
- `PUT /api/user/info` - 更新当前用户信息

### 健康记录
- `POST /api/health/record` - 添加健康记录
- `GET /api/health/records/{userId}` - 查询健康记录列表
- `DELETE /api/health/record/{id}` - 删除健康记录
- `PUT /api/health/record/{id}` - 更新健康记录

### 运动记录
- `POST /api/sport/record` - 添加运动记录
- `GET /api/sport/records/{userId}` - 查询运动记录列表
- `DELETE /api/sport/record/{id}` - 删除运动记录
- `PUT /api/sport/record/{id}` - 更新运动记录

### 智能分析
- `GET /api/smart-health/overview?userId={userId}` - 获取智能健康概览（Redis 缓存 10 分钟）

### AI 对话
- `POST /api/chat/send` - 发送 AI 对话请求
  ```json
  {
    "message": "我的血压正常吗？",
    "userId": 1
  }
  ```

医生预约也复用该接口，不额外开放直接写数据库的预约接口。

### 提醒配置
- `GET /api/reminder/preferences/{userId}` - 获取用户提醒偏好
- `POST /api/reminder/preference` - 创建提醒偏好
- `PUT /api/reminder/preference/{id}` - 更新提醒偏好
- `DELETE /api/reminder/preference/{id}` - 删除提醒偏好

## 🏥 AI 预约流程

预约信息由 AI 提取为结构化 JSON，Java 后端负责字段校验、科室标准化、未来时间校验和数据库保存。

必填字段：

- 姓名
- 年龄（1～120）
- 预约时间（必须晚于当前时间）
- 用户电话
- 预约科室

示例对话：

```text
用户：我叫利口，56岁，想在2026年7月30日上午9点半预约泌尿科，电话17865387668。

系统：
请确认预约信息：
姓名：利口
年龄：56岁
预约科室：泌尿外科
预约时间：2026-07-30 09:30
联系电话：17865387668

信息正确请回复"确认预约"，需要修改请直接说明，取消请回复"取消预约"。

用户：确认预约

系统：预约成功！
```

预约流程说明：

1. 普通健康回答结尾会询问用户是否需要预约医生。
2. AI 将自然语言转换为 `AppointmentExtractionResult`。
3. Java 后端不会信任模型结果，会再次校验所有字段。
4. 信息不完整时进入多轮补充，不会写入数据库。
5. 信息完整后展示确认摘要，仍不会写入数据库。
6. 只有用户明确回复"确认预约"后，才写入 `doctor_appointment` 表。
7. 用户回复"取消预约"会清除当前内存预约草稿。

当前预约草稿保存在后端内存中，后端重启后，尚未确认的草稿会丢失。若部署多实例，建议后续将草稿迁移到 Redis。

## 🧪 测试

### 后端测试
```bash
cd backend
mvn test
```

### 前端测试
```bash
cd frontend
npm run lint
npm run build
```

## 🐳 Docker 部署

### Docker Compose 一键启动

项目提供完整的 Docker Compose 编排，包含 5 个服务：

| 服务 | 镜像 | 端口 | 说明 |
|------|------|------|------|
| MySQL | mysql:8.0 | 3308:3306 | 数据库，自动初始化 |
| Redis | redis/redis-stack-server:7.4.0-v3 | 6379 | 缓存 + 向量存储 + 会话 |
| RabbitMQ | rabbitmq:3-management | 5672 + 15672 | 消息队列 + 管理界面 |
| Backend | 本地构建 | 8080 | Spring Boot 后端 |
| Frontend | 本地构建 | 80 | Nginx 前端 |

```bash
# 配置环境变量
cp .env.example .env
# 编辑 .env 填入 API Key 等

# 启动所有服务
docker compose up -d

# 查看日志
docker compose logs -f

# 查看单个服务日志
docker compose logs -f backend

# 停止服务
docker compose down
```

### 访问地址

- 前端: http://localhost
- 后端 API: http://localhost:8080
- RabbitMQ 管理: http://localhost:15672（admin / admin123）

## 📝 注意事项

1. **首次使用前请修改默认密码**
   - 完整 SQL 示例管理员账号: `admin / 123456`
   - 完整 SQL 示例普通账号: `user1 / 456`
   - 这些账号仅用于本地演示，部署前应修改密码并使用安全的密码编码方式

2. **AI 功能需要配置 API Key**
   - `DEEPSEEK_API_KEY` 用于健康对话和预约字段提取
   - `DASHSCOPE_API_KEY` 用于健康知识 Embedding
   - 预约字段提取失败时，系统会提示用户使用明确格式重新填写

3. **数据库连接**
   - 默认数据库: `health_system`
   - 默认用户名: `root`
   - 请通过 `SPRING_DATASOURCE_PASSWORD` 或本地配置文件设置实际密码

4. **Redis 必须运行**
   - 应用启动时会检查 Redis 连接（@PostConstruct）
   - Redis 不可用将导致启动失败
   - 用途：JWT 黑名单、AI 对话记忆、限流、缓存、RAG 向量存储、提醒去重

5. **RabbitMQ 配置**
   - 必须使用 `management` 版本镜像（自带 Web 管理界面）
   - RabbitMQ 4.x 无默认用户，必须通过环境变量创建
   - IDEA Docker 面板填写环境变量时，注意变量名和值不要有多余空格
   - 消息重试策略：失败后 3 次指数退避（3s → 6s → 12s）
   - 重试全部失败后消息进入死信队列 `reminder.email.dead.queue`

6. **端口占用**
   - 后端: 8080
   - 前端开发服务器: 3000
   - 前端 Docker: 80
   - 本地 MySQL: 3306
   - Docker MySQL 宿主机端口: 3308
   - Redis: 6379
   - RabbitMQ AMQP: 5672
   - RabbitMQ 管理界面: 15672

7. **邮件发送**
   - QQ 邮箱需开启 SMTP 服务并获取授权码（非登录密码）
   - 提醒调度器默认开启（`reminder.scheduler.enabled: true`）
   - 邮件未配置时不影响其他功能正常运行
   - 每封邮件包含渐变头部、健康小贴士等 HTML 美化内容

8. **Spring Controller 参数名错误**
   - 项目已在 Maven 编译器中启用 `<parameters>true</parameters>`
   - 请使用 `mvn clean package` 或 IDE 的 Maven 构建重新编译
   - 不要使用缺少 `-parameters` 的裸 `javac` 覆盖 `target/classes`

9. **接口限流**
   - 使用 `@RateLimit` 注解标注需要限流的接口
   - 基于 Redis 滑动窗口实现分布式限流
   - 支持按 IP 或按用户维度限流
   - 超限时返回 429 Too Many Requests

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
- [RabbitMQ](https://www.rabbitmq.com/)
- [Redis](https://redis.io/)

## RAG 健康知识库说明

当前项目已经接入基于 LangChain4j + Redis 的健康知识 RAG，用于在智能健康分析时检索本地 Markdown 健康知识，并把相关片段注入 AI 提示词。

### 实现流程

```text
backend/src/main/resources/rag/health-knowledge.md
  -> 按 Markdown 标题切分知识片段
  -> 使用 EmbeddingModel 生成向量
  -> 写入 Redis 向量存储（HealthKnowledgeRedisRepository）
  -> 智能健康分析时向量检索相关知识
  -> 检索结果注入 SmartHealth AI prompt
```

### 相关依赖

项目没有使用 `langchain4j-easy-rag`，因为当前 `1.17.0` 版本下该 artifact 不可用。RAG 使用 LangChain4j 原生组件完成，相关 Maven 依赖如下：

```xml
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j</artifactId>
    <version>1.17.0</version>
</dependency>

<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-core</artifactId>
    <version>1.17.0</version>
</dependency>

<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-open-ai</artifactId>
    <version>1.17.0</version>
</dependency>
```

这些依赖已经包含 `EmbeddingModel`、`OpenAiEmbeddingModel`、`TextSegment`、`EmbeddingSearchRequest`、`InMemoryEmbeddingStore`，可以完成 Markdown 向量化、检索和 Redis 持久化。

### 配置项

配置位于 `backend/src/main/resources/application.yml`：

```yaml
langchain4j:
  openai:
    chat-api-key: ${DEEPSEEK_API_KEY:}
    embedding-api-key: ${DASHSCOPE_API_KEY:}
    model-name: deepseek-v4-flash
    base-url: https://api.deepseek.com/v1
    embedding-model-name: ${EMBEDDING_MODEL_NAME:text-embedding-v4}
    embedding-base-url: ${EMBEDDING_BASE_URL:https://dashscope.aliyuncs.com/compatible-mode/v1}

rag:
  health:
    vector-store-path: ${HEALTH_RAG_VECTOR_STORE_PATH:data/rag/health-knowledge-vector-store.json}
    redis:
      host: ${HEALTH_RAG_REDIS_HOST:${REDIS_HOST:localhost}}
      port: ${HEALTH_RAG_REDIS_PORT:${REDIS_PORT:6379}}
      key-prefix: ${HEALTH_RAG_REDIS_KEY_PREFIX:health:knowledge}
```

### 如何更新健康知识

1. 编辑 `backend/src/main/resources/rag/health-knowledge.md`。
2. 建议使用 `##` 二级标题拆分知识主题，例如血压、血糖、BMI、运动、饮食等。
3. 重启后端或下一次触发智能健康分析时，系统会检查 Markdown 内容 hash。
4. 如果 Markdown 有变化，会自动重新生成向量并写入 Redis。

### 常见问题

**为什么没有加 `langchain4j-easy-rag`？**

当前项目使用 `dev.langchain4j:langchain4j:1.17.0`，该版本下没有可用的 `langchain4j-easy-rag` artifact。项目直接使用 LangChain4j 原生 Embedding 和 EmbeddingStore API 实现 RAG。

**启动时报 `No default constructor found` 怎么办？**

确认 `HealthKnowledgeRagServiceImpl` 的正式构造器带有 `@Autowired`，然后重新编译：

```bash
cd backend
mvn clean compile
```

**什么时候会调用外部 embedding 接口？**

应用启动时不会立即生成向量。首次调用智能健康分析并触发 RAG 检索时，如果 Redis 中不存在向量数据或 Markdown 内容已变化，才会调用 embedding 模型生成向量。

---

⭐ 如果这个项目对你有帮助，请给个 Star 支持一下！
