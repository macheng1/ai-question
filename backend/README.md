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
### 初始化默认演示企业

后端启动时由 Flyway 自动执行 `V27_1__seed_default_enterprise.sql` 创建默认企业账号，
随后执行 `V27_2__seed_demo_training_exams.sql` 创建演示培训和考试。发布新版本并成功启动后端后生效。
Flyway 保存执行记录，后续重启和重复部署不会再次执行，也不会重置密码或考试时间。

以下为兼容的手动入口，通常无需运行；若需运行，先切换到 `backend` 目录并完成 V1—V27 表结构迁移：

```bash
mysql -h <数据库地址> -P <端口> -u <用户名> -p --default-character-set=utf8mb4 <数据库名> < scripts/init-default-enterprise.sql
```

- 企业名称：默认演示企业
- 管理员名称：默认企业管理员
- 登录手机号：`13800000000`
- 初始密码：`12345678`，登录后可在个人设置中修改。

脚本与正常企业注册使用相同的账号模型及 BCrypt 密码格式。同手机号或同名账号存在时跳过，
不会重置已有密码或修改账号状态。执行后请核对输出的企业名称及状态；若已有账号发生冲突，使用已有账号的密码。

### 初始化企业培训演示内容

演示内容已由上述 Flyway 迁移自动初始化；兼容的手动调用方式如下（在 `backend` 目录执行）：

```bash
mysql -h <数据库地址> -P <端口> -u <用户名> -p --default-character-set=utf8mb4 <数据库名> < scripts/init-demo-training-exams.sql
```

包含「新员工入职与职业规范」「员工信息安全与数据保护」「质量意识与异常问题闭环」
三套题库、试卷、考试及关联培训。每套 5 题（2 单选、1 多选、2 判断），每题 20 分，
总分 100 分、60 分及格，限时 30 分钟，最多答题 2 次。每场培训有 3 道满意度评估题。
题目为原创通用岗位培训示例，具备对应答案与解析，正式使用前应结合企业制度审核。

考试开始时间为首次初始化前一天，结束时间为首次初始化后 30 天，初始化后可直接演示。
重复执行不会延长考试时间或重置已有资料；过期后请在考试管理中调整时间。
初始化后可在管理页面查看培训和考试，并通过考试二维码或 `/kaoshi/{id}` 入口演示。

不预置虚假答卷、成绩、反馈或无效的视频地址。可先演示题库、组卷、考试、交卷及评估；
需要演示视频学习时，在培训编辑页上传真实视频到已配置的 OSS。
本脚本不依赖改造分支的员工、部门、培训材料等新增表。
如手动执行，使用 MySQL 8 客户端串行执行，不加 `--force`。默认账号身份信息冲突时，
演示数据迁移会中止，不会将数据绑定给其他账号；应核对冲突后再处理迁移。
版本号使用 27.1、27.2，位于已有 V27 与改造分支 V28 之间；不要修改已执行迁移文件的内容。

### 前端跨域访问

后端默认允许任意来源、请求方法和请求头的跨域访问，无需配置域名白名单。
登录、上传及携带 Authorization 的预检请求由 Spring CORS 处理；
下载文件名、跟踪 ID 和视频范围响应头对浏览器可见。
当前前端使用 Authorization Bearer 令牌，不依赖跨站 Cookie；接口身份认证和数据权限仍正常校验。
此修改需要重新构建部署后端后生效。

跨域放行不会解除浏览器的 HTTPS 混合内容限制：HTTPS 前端仍应请求 HTTPS 后端或同源代理。
网关拦截、服务未启动和连接失败需要在对应层处理。
