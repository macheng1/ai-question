# 智企云考后端

后端为智企云考提供企业账号、题库、组卷、考试、答题、成绩统计、公告资讯和文件上传等 API 能力。

## 技术栈

- Java 17
- Spring Boot 3
- Spring Security + JWT
- MyBatis
- MySQL
- Redis
- Flyway
- Swagger/OpenAPI

## 目录说明

```text
backend/
├── src/main/java/com/aiquestion/
│   ├── controller/       # HTTP 接口
│   ├── service/          # 业务逻辑
│   ├── repository/       # 数据访问
│   ├── domain/           # 领域实体
│   ├── config/            # Spring 与基础设施配置
│   ├── security/          # JWT 与认证相关代码
│   ├── integration/       # 阿里云短信、OSS 等外部服务
│   └── upload/            # 文件上传接口与服务
├── src/main/resources/
│   ├── db/migration/      # Flyway 数据库迁移
│   └── mapper/            # MyBatis XML
├── scripts/               # 本地启动和停止脚本
├── docker-compose.yml
└── .env.example
```

## 配置

```bash
cp .env.example .env
```

主要配置项：

- `DB_*`：MySQL 连接信息
- `REDIS_*`：Redis 连接信息
- `SERVER_PORT`：后端服务端口，开发环境默认 `18080`
- `JWT_*`：访问令牌和刷新令牌配置
- `ALIYUN_SMS_*`：短信验证码服务配置
- `ALIYUN_OSS_*`：文件对象存储配置
- `UPLOAD_*`：上传大小和数量限制

真实配置只放在 `.env` 或部署平台的环境变量中，禁止提交到 Git。

## 启动依赖服务

```bash
./scripts/start-infra.sh
```

该脚本启动 Docker Compose 中的 MySQL 和 Redis。

## 启动后端

```bash
./scripts/run-local.sh
```

也可以直接运行：

```bash
mvn spring-boot:run
```

API 根地址：<http://localhost:18080/api>

## Docker 启动

```bash
docker compose up -d --build
```

停止基础设施：

```bash
./scripts/stop-infra.sh
```

## API 文档

- Swagger UI：<http://localhost:18080/api/swagger-ui.html>
- OpenAPI JSON：<http://localhost:18080/api/v3/api-docs>

## 文件上传

通用上传接口：

```text
POST /api/common/upload
Content-Type: multipart/form-data
```

文件字段名为 `files`，支持以下动态参数：

- `scene`
- `maxSizeMb`
- `maxCount`
- `allowedExtensions`

示例：

```text
/api/common/upload?maxCount=1&maxSizeMb=5&allowedExtensions=jpg,png
/api/common/upload?maxCount=10&maxSizeMb=20&allowedExtensions=pdf,doc,docx
```

## 数据库迁移

迁移文件位于 `src/main/resources/db/migration`。新增表、字段、索引或初始化数据时，必须新增版本文件，例如：

```text
V22__add_xxx.sql
```

不要修改已经执行过的迁移文件，否则会导致不同环境的 Flyway 校验失败。

## 开发检查

```bash
mvn test
mvn package
```

### 培训视频 OSS 存储

新培训视频复用 `ALIYUN_OSS_ACCESS_KEY_ID`、`ALIYUN_OSS_ACCESS_KEY_SECRET`、
`ALIYUN_OSS_ENDPOINT`、`ALIYUN_OSS_BUCKET` 配置上传 OSS。配置缺失或上传失败会明确报错，不会回退到本地磁盘。
对象位于 `training-videos/` 下，并在上传时设置私有 ACL；RAM 权限应包含该前缀的上传、读取、删除及设置对象 ACL 权限。
管理预览校验培训归属，考生播放校验学习凭证，然后由后端流式读取 OSS；不会向客户端暴露永久公开地址或密钥，也不要求浏览器跨域访问 OSS。
`training_video.storage_path` 保存 `oss:training-videos/<uuid>.mp4`（或 `.webm`），沿用已有字段，不新增迁移。
删除培训或视频会清理对应 OSS 对象；清理异常写入日志，可按日志对象键补偿清理。

无 `oss:` 前缀的历史记录仍兼容 `training.video-directory` 指向的本地文件。
历史文件不会自动搬迁；发布前已有的本地视频应保留原目录，或通过培训编辑页重新上传至 OSS 后替换。
新视频不依赖本地视频卷，但上传过程仍可能使用容器临时目录缓存 multipart 请求。

可选真实 OSS 验证：设置 `RUN_OSS_LIVE_TEST=true`、`OSS_TEST_VIDEO` 为 MP4 样片绝对路径以及上述 OSS 环境变量，
运行 `mvn test -Dtest=TrainingOssLiveTest`。测试会上传、完整读取比对并删除本次测试对象；默认测试不会访问 OSS。

### 手动培训邮件提醒

培训管理“更多 → 一键提醒”，培训结束时间和考试结束时间独立判断。没有培训结束时间时不判定培训逾期；关联考试仍按考试结束时间判断。培训结束后允许补学，无材料培训需主动确认完成；考试按最后一次成绩是否通过判断，满意度不参与。

发布需执行 Flyway `V39__manual_training_reminders.sql`（正常启动自动执行）。Docker Compose 已透传以下配置：

| 变量 | 说明 |
| --- | --- |
| MAIL_HOST | 企业邮箱 SMTP 地址 |
| MAIL_PORT | 默认 587，以服务商要求为准 |
| MAIL_USERNAME | SMTP 登录账号 |
| MAIL_PASSWORD | SMTP 授权码/密码，只放环境变量 |
| MAIL_FROM | 发件邮箱，与服务商授权一致 |
| MAIL_SMTP_AUTH | 默认 true |
| MAIL_STARTTLS | 默认 true；隐式 SSL 时设为 false |
| MAIL_SSL | 默认 false；隐式 SSL 时设为 true |
| APP_PUBLIC_URL | 员工可访问的平台完整地址，如 https://training.example.com，不含 /api |

配置不完整时允许查看名单，但拒绝发送。每次最多 50 人，逐封发送前重新检查；本期无自动提醒、无自动重试。发送等待期间不要关闭页面。

记录表为 `training_reminder_delivery`。SENT 表示 SMTP 服务已接收，不代表最终投递或已阅读；FAILED 表示发送异常。请求标识和员工联合唯一，重复请求不会重复发送；刷新名单后再次发送属于新提醒。

若进程在发送途中退出，SENDING 需要结合 SMTP 日志核实，不应盲目重发。失败后可检查配置，再刷新名单重新选择发送。
