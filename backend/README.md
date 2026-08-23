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
