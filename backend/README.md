# 后端说明

Spring Boot 3 + Java 17 + MyBatis + MySQL + Redis + Swagger/OpenAPI + Flyway。数据库和缓存通过 Docker Compose 启动。

```bash
cd backend
cp .env.example .env
./scripts/start-infra.sh
./scripts/run-local.sh
```

接口前缀：`http://localhost:18080/api`

迁移脚本位于 `src/main/resources/db/migration`，建表和初始化数据都必须通过新增 `V3__...sql` 处理，不要直接修改已经执行过的迁移文件。

Swagger 地址：`http://localhost:18080/api/swagger-ui.html`
OpenAPI JSON：`http://localhost:18080/api/v3/api-docs`

停止基础设施：`./scripts/stop-infra.sh`

一键构建并启动 MySQL、Redis、Spring Boot 和 Next.js：

```bash
docker compose up -d --build
```

前端：`http://localhost:3000`
后端：`http://localhost:18080/api`

JWT、阿里云短信和 OSS 均只从环境变量读取，真实密钥不要写入 Git。短信还需要补充 `ALIYUN_SMS_SIGN_NAME` 和 `ALIYUN_SMS_TEMPLATE_CODE`。

通用上传接口：`POST /api/common/upload`，multipart 字段名为 `files`。支持动态参数：`scene`、`maxSizeMb`、`maxCount`、`allowedExtensions`。例如单文件图片上传可传 `maxCount=1&maxSizeMb=5&allowedExtensions=jpg,png`；多文件资料上传可传 `maxCount=10&maxSizeMb=20&allowedExtensions=pdf,doc,docx`。
