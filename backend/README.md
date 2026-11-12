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

### 初始化默认演示企业

后端启动时由 Flyway 自动执行 `V27_1__seed_default_enterprise.sql` 创建默认企业账号，
随后执行 `V27_2__seed_demo_training_exams.sql` 创建演示培训和考试。发布新版本并成功启动后端后生效。
Flyway 保存执行记录，后续重启和重复部署不会再次执行，也不会重置密码或考试时间。

以下为旧版兼容的手动入口，仅初始化管理员，不包含当前分支完整员工与名单配置。当前分支应优先通过后端启动执行全部 Flyway 迁移；若使用旧入口，先切换到 `backend` 目录并完成 V1—V27 表结构迁移：

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

考试开始时间为首次初始化前一天，结束时间为首次初始化后 30 天。
重复执行不会延长考试时间或重置已有资料；过期后请在考试管理中调整时间。
当前分支通过 `V41__align_demo_training_with_employee_scope.sql` 补齐演示数据：

- 部门：培训示范部。
- 员工：演示员工，手机号 `13900000001`，初始密码 `12345678`，通过员工登录入口登录。
- 三场培训分别设置入职、安全、制度分类，起止时间沿用关联考试，学习范围为指定演示员工。
- 仅补齐尚未配置名单且无学习、答题、评估记录的原始演示培训，已有业务配置不覆盖。
- 不预置虚假答卷、成绩、反馈或无效视频地址；演示材料学习需管理员上传真实材料，OSS 须完成配置。

两条手动初始化脚本仅包含旧版管理员和培训考试数据，不等同于完整迁移，
不能只执行手动脚本就认为当前员工学习流程已准备完成。
如手动执行，使用 MySQL 8 客户端串行执行，不加 `--force`。
默认企业或演示员工身份冲突时迁移会中止，不会将数据绑定给其他企业；应核对冲突后再处理迁移。
已存在账号不会重置密码。已执行过的迁移文件不得改写。

若数据库已执行 V28 或更高版本，但缺少 V27.1、V27.2，
`SeedMigrationConfiguration` 会仅针对这两条已知的历史初始化迁移允许补执行，随后执行后续迁移。
无需设置全局 `SPRING_FLYWAY_OUT_OF_ORDER=true`；若出现其他未执行的低版本迁移，会停止启动并提示核对。
新库仍按版本顺序执行。V41 同时校正历史培训用途字段，接口统一按 `assessment_id` 判断是否关联考试。
部署前应备份并核对迁移历史；迁移成功后再检查员工登录、培训名单与真实材料学习流程。

### 前端跨域访问

后端默认允许任意来源、请求方法和请求头的跨域访问，无需配置域名白名单。
登录、上传及携带 Authorization 的预检请求由 Spring CORS 处理；
下载文件名、跟踪 ID 和视频范围响应头对浏览器可见。
当前前端使用 Authorization Bearer 令牌，不依赖跨站 Cookie；接口身份认证和数据权限仍正常校验。
此修改需要重新构建部署后端后生效。

跨域放行不会解除浏览器的 HTTPS 混合内容限制：HTTPS 前端仍应请求 HTTPS 后端或同源代理。
网关拦截、服务未启动和连接失败需要在对应层处理。

### 培训副本关联考试的清理

`V45__training_copied_assessment.sql` 增加 `training.copied_assessment_id`，仅记录复制培训时自动创建的考试。
取消关联、换绑或删除副本时，在同一事务内清理该空考试；原考试、共用试卷和手动关联考试保留。
有学习、答题或其他培训引用时禁止清理，保存失败时整体回滚。删除培训仍需满足原有材料与反馈检查。
为避免误删，历史副本不通过名称推断来源；迁移前已遗留的考试需要人工核对后处理。
部署需执行 V45（正常启动时由 Flyway 执行），无需新增环境变量。

### MyBatis 迁移回归测试

运行时 SQL 统一放在 `src/main/resources/mapper`。常规 `mvn test` 包含 Mapper 绑定与架构检查。
`MyBatisMigrationIntegrationTest` 会真实启动 Spring 服务、执行 Flyway 和 MySQL SQL；它只允许显式指定的本地 `aiquestion_mybatis_test` 数据库。

仅在单独的临时 MySQL 实例上运行，不能使用开发或生产数据库：

```bash
# 示例口令仅用于下面的临时容器。
docker run -d --name aiquestion-mybatis-test \
  -e MYSQL_ROOT_PASSWORD=local-mapper-test-only \
  -e MYSQL_DATABASE=aiquestion_mybatis_test \
  -p 127.0.0.1:13316:3306 mysql:8.0 --default-time-zone=+00:00

# 等待容器内 MySQL 就绪后，在 backend 目录运行：
MYBATIS_TEST_URL='jdbc:mysql://127.0.0.1:13316/aiquestion_mybatis_test?connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true&useSSL=false&allowPublicKeyRetrieval=true' \
MYBATIS_TEST_PASSWORD='local-mapper-test-only' mvn test

# 仅清理上面专门创建的测试容器。
docker rm -f aiquestion-mybatis-test
```

未提供上述变量时，这组集成测试会跳过，不影响常规单元测试。测试会创建测试账号和业务数据，使用后应销毁临时实例。
覆盖学员保存和身份隔离、培训保存/失败回滚、复制考试和材料的主键、复制失败清理、阅读顺序/并发计时、视频进度、固定/随机考试及恢复、重复交卷、统计筛选。
