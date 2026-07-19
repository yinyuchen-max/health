# 健康管理系统 

基于 Spring Boot + Vue 3 + MySQL 的智能健康管理平台，集成 AI 健康顾问、健康知识 RAG 和自然语言医生预约功能。

## ✨ 主要特性

- 🔐 **用户认证**: JWT Token 登录，角色权限管理
- 📊 **健康数据**: 血压、血糖、心率、体重等指标记录与追踪
- 🏃‍♂️ **运动记录**: 运动类型、时长、消耗热量统计
- 💡 **智能分析**: 自动计算 BMI、风险评估、营养建议
- 🤖 **AI 健康顾问**: 基于 DeepSeek/Qwen 大模型的个性化健康咨询
- 🏥 **AI 医生预约**: 自然语言提取预约信息，多轮补充，确认后写入数据库
- 📚 **健康知识 RAG**: 基于本地 Markdown 知识库进行向量检索与增强回答
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

### 7. AI 医生预约 🏥
- ✅ AI 从自然语言中结构化提取姓名、年龄、预约时间、电话和科室
- ✅ 字段顺序任意，支持“泌尿科”等科室别名归一化
- ✅ 缺少或无法识别字段时给出明确提示
- ✅ 支持多轮补充和修改预约信息
- ✅ 展示完整预约信息并要求用户确认
- ✅ 只有用户回复“确认预约”后才写入 MySQL
- ✅ 支持取消预约填写
- ✅ 应用启动时自动检查并创建 `doctor_appointment` 表

## 🚀 快速开始

### 前置要求

- JDK 17+
- Node.js 16+
- MySQL 8.0+
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

### 2. 后端启动

```bash
cd backend

# 配置环境变量（AI 对话与预约信息提取必需）
# Windows PowerShell
$env:DEEPSEEK_API_KEY="sk-your-deepseek-key"
$env:DASHSCOPE_API_KEY="sk-your-dashscope-key"

# Linux/Mac
export DEEPSEEK_API_KEY="sk-your-deepseek-key"
export DASHSCOPE_API_KEY="sk-your-dashscope-key"

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

# 可选：覆盖 AI 模型配置
LANGCHAIN4J_OPENAI_MODEL_NAME=deepseek-v4-flash
LANGCHAIN4J_OPENAI_BASE_URL=https://api.deepseek.com/v1
LANGCHAIN4J_OPENAI_TEMPERATURE=0.3
LANGCHAIN4J_OPENAI_TIMEOUT=60

# 可选：Spring 数据源覆盖
SPRING_DATASOURCE_URL=jdbc:mysql://localhost:3306/health_system?useUnicode=true&characterEncoding=utf-8&useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true
SPRING_DATASOURCE_USERNAME=root
SPRING_DATASOURCE_PASSWORD=your-database-password

# 可选：JWT Secret（至少 256 位）
JWT_SECRET=your-jwt-secret-key-min-256-bits-long
```

Docker Compose 会自动读取项目根目录的 `.env`。如果通过 `mvn spring-boot:run` 或 IDE 直接启动后端，需要将这些变量导出到当前终端，或配置到 IDE 的 Environment variables 中。

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
│   │   │   │   ├── AiAppointmentInformationExtractor.java # AI 预约字段提取
│   │   │   │   ├── AppointmentConversationServiceImpl.java # 预约会话与确认
│   │   │   │   ├── DoctorAppointmentServiceImpl.java # 预约落库与自动建表
│   │   │   │   ├── SmartHealthServiceImpl.java # 智能分析服务
│   │   │   │   └── ...
│   │   │   └── ...
│   │   └── HealthSystemBackendApplication.java
│   ├── src/main/resources/
│   │   ├── application.yml           # 主配置文件
│   │   ├── rag/health-knowledge.md    # 本地健康知识库
│   │   └── static/                   # 静态资源（前端打包后）
│   ├── database/
│   │   ├── health_system.sql          # 完整建库与初始数据
│   │   └── add_doctor_appointment.sql # 已有数据库的预约表增量脚本
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
├── docker-compose.yml                # Docker Compose 编排
├── .gitignore                        # Git 忽略文件
└── README.md                         # 项目文档
```

## 🔑 核心 API 接口

### 认证相关
- `POST /api/user/register` - 用户注册
- `POST /api/user/login` - 用户登录
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
- `GET /api/smart-health/overview?userId={userId}` - 获取智能健康概览

### AI 对话
- `POST /api/chat/send` - 发送 AI 对话请求
  ```json
  {
    "message": "我的血压正常吗？",
    "userId": 1
  }
  ```

医生预约也复用该接口，不额外开放直接写数据库的预约接口。

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

信息正确请回复“确认预约”，需要修改请直接说明，取消请回复“取消预约”。

用户：确认预约

系统：预约成功！
```

预约流程说明：

1. 普通健康回答结尾会询问用户是否需要预约医生。
2. AI 将自然语言转换为 `AppointmentExtractionResult`。
3. Java 后端不会信任模型结果，会再次校验所有字段。
4. 信息不完整时进入多轮补充，不会写入数据库。
5. 信息完整后展示确认摘要，仍不会写入数据库。
6. 只有用户明确回复“确认预约”后，才写入 `doctor_appointment` 表。
7. 用户回复“取消预约”会清除当前内存预约草稿。

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

```bash
# 使用 Docker Compose 一键启动
docker compose up -d

# 查看日志
docker compose logs -f

# 停止服务
docker compose down
```

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

4. **端口占用**
   - 后端: 8080
   - 前端开发服务器: 3000
   - 本地 MySQL: 3306
   - Docker MySQL 宿主机端口: 3308
   - Docker 前端: 80

5. **Spring Controller 参数名错误**
   - 项目已在 Maven 编译器中启用 `<parameters>true</parameters>`
   - 请使用 `mvn clean package` 或 IDE 的 Maven 构建重新编译
   - 不要使用缺少 `-parameters` 的裸 `javac` 覆盖 `target/classes`

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

## RAG 健康知识库说明

当前项目已经接入基于 LangChain4j 的健康知识 RAG，用于在智能健康分析时检索本地 Markdown 健康知识，并把相关片段注入 AI 提示词。

### 实现流程

```text
backend/src/main/resources/rag/health-knowledge.md
  -> 按 Markdown 标题切分知识片段
  -> 使用 EmbeddingModel 生成向量
  -> 写入 InMemoryEmbeddingStore
  -> 持久化到 data/rag/health-knowledge-vector-store.json
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

这些依赖已经包含 `EmbeddingModel`、`OpenAiEmbeddingModel`、`TextSegment`、`EmbeddingSearchRequest`、`InMemoryEmbeddingStore`，可以完成 Markdown 向量化、检索和本地持久化。

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
```

Windows PowerShell 示例：

```powershell
$env:DEEPSEEK_API_KEY="sk-your-deepseek-key"
$env:DASHSCOPE_API_KEY="sk-your-dashscope-key"
$env:EMBEDDING_MODEL_NAME="text-embedding-v4"
$env:EMBEDDING_BASE_URL="https://dashscope.aliyuncs.com/compatible-mode/v1"
```

### 如何更新健康知识

1. 编辑 `backend/src/main/resources/rag/health-knowledge.md`。
2. 建议使用 `##` 二级标题拆分知识主题，例如血压、血糖、BMI、运动、饮食等。
3. 重启后端或下一次触发智能健康分析时，系统会检查 Markdown 内容 hash。
4. 如果 Markdown 有变化，会自动重新生成向量库文件。

生成文件：

```text
data/rag/health-knowledge-vector-store.json
data/rag/health-knowledge-vector-store.json.meta
```

### 常见问题

**为什么没有加 `langchain4j-easy-rag`？**

当前项目使用 `dev.langchain4j:langchain4j:1.17.0`，该版本下没有可用的 `langchain4j-easy-rag` artifact。项目直接使用 LangChain4j 原生 Embedding 和 EmbeddingStore API 实现 RAG。

**启动时报 `No default constructor found` 怎么办？**

确认 `HealthKnowledgeRagServiceImpl` 的正式构造器带有 `@Autowired`，然后重新编译：

```powershell
cd backend
mvn clean compile
```

**什么时候会调用外部 embedding 接口？**

应用启动时不会立即生成向量。首次调用智能健康分析并触发 RAG 检索时，如果本地向量库不存在或 Markdown 内容已变化，才会调用 embedding 模型生成向量。

---

⭐ 如果这个项目对你有帮助，请给个 Star 支持一下！
