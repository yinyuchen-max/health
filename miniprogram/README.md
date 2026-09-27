# 健康管理系统微信小程序

## 快速开始

### 1. 准备工作

1. 下载 [微信开发者工具](https://developers.weixin.qq.com/miniprogram/dev/devtools/download.html)
2. 注册微信小程序账号（[mp.weixin.qq.com](https://mp.weixin.qq.com)），获取 AppID 和 AppSecret
   - 开发阶段可以使用「测试号」

### 2. 配置后端

1. 执行数据库迁移脚本：
   ```sql
   -- 在 MySQL 中执行
   source /path/to/health/backend/database/add_wechat_openid.sql;
   source /path/to/health/backend/database/add_subscribe_message.sql;
   ```

2. 配置环境变量（或在 `application.yml` 中直接填写）：
   ```bash
   export WECHAT_APP_ID=你的小程序AppID
   export WECHAT_APP_SECRET=你的小程序AppSecret
   export WECHAT_SUBSCRIBE_TEMPLATE_ID=订阅消息模板ID  # 在小程序后台配置
   ```

3. 启动后端服务：
   ```bash
   cd health/backend
   ./mvnw spring-boot:run
   ```

### 3. 打开小程序

1. 用微信开发者工具打开 `health/miniprogram` 目录
2. 在 `project.config.json` 中填写你的 AppID（或使用测试号）
3. 勾选「不校验合法域名」（开发阶段）
4. 在 `app.js` 中修改 `baseUrl` 为你的后端地址

### 4. 添加 TabBar 图标

需要在 `miniprogram/assets/icons/` 目录下添加以下图标文件（建议 81x81 像素 PNG）：

- `home.png` / `home-active.png` - 首页图标
- `chat.png` / `chat-active.png` - AI 问诊图标
- `user.png` / `user-active.png` - 我的图标

可以从 [iconfont](https://www.iconfont.cn/) 下载免费图标。

## 功能说明

| 页面 | 功能 | 对应后端接口 |
|---|---|---|
| 登录 | 微信一键登录 / 账号密码登录 | `/api/user/wechat-login` `/api/user/login` |
| 首页 | 健康数据总览、快捷操作 | `/api/smart/overview` `/api/health-records` `/api/sport-records` |
| 健康记录 | 增删改查健康数据 | `/api/health-records` |
| AI 聊天 | 流式 AI 健康咨询 | `/api/chat/stream` |
| 提醒配置 | 管理健康提醒 | `/api/reminders/preferences` |
| 我的 | 个人中心、订阅消息授权 | - |

## 上线前检查清单

- [ ] 后端部署到已备案的域名 + HTTPS
- [ ] 小程序后台配置合法域名白名单
- [ ] 配置正式的 AppID 和 AppSecret
- [ ] 在小程序后台配置订阅消息模板
- [ ] 添加 TabBar 图标文件
- [ ] 测试所有功能正常

## 目录结构

```
miniprogram/
├── app.js              # 全局入口
├── app.json            # 全局配置
├── app.wxss            # 全局样式
├── project.config.json # 项目配置
├── sitemap.json        # 站点地图
├── utils/
│   ├── request.js      # 请求封装（带 JWT）
│   └── auth.js         # 登录态管理
└── pages/
    ├── login/          # 登录页
    ├── dashboard/      # 首页
    ├── health-record/  # 健康记录
    ├── ai-chat/        # AI 聊天
    ├── reminder/       # 提醒配置
    └── mine/           # 个人中心
```

## 后端新增文件

- `WechatAuthService.java` - 微信登录服务
- `WechatAccessTokenService.java` - access_token 管理（Redis 缓存）
- `WechatMessageService.java` - 订阅消息发送服务
- `WechatLoginDTO.java` / `WechatLoginResponseDTO.java` - 登录 DTO
- `SubscribeMessage.java` / `SubscribeMessageMapper.java` - 订阅消息实体和 Mapper
