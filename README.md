# 登科企考本地工程

企业公开考试平台的前后端基础工程。

## 技术栈

- 前端：Next.js App Router + React + TypeScript
- 后端：Spring Boot 3 + Java 17 + MyBatis + Flyway
- 数据库：MySQL；缓存：Redis（通过 Docker Compose 启动基础设施）

## 启动

### 前端

```bash
cd frontend
npm install
npm run dev
```

默认地址：`http://localhost:3000`

### 后端

```bash
cd backend
mvn spring-boot:run
```

默认地址：`http://localhost:8080/api`

后端基础设施：MySQL + Redis + Docker Compose。首次启动：

```bash
cd backend
cp .env.example .env
./scripts/start-infra.sh
./scripts/run-local.sh
```

## 工程约定

- 数据库表名使用小写蛇形命名，例如 `question_bank`、`kaoshi_record`。
- 主键统一使用 `BIGINT`，由应用侧生成 UUID/雪花 ID 时可替换。
- 所有业务表包含 `created_at`、`updated_at`。
- 对外接口统一返回 `ApiResponse<T>`。
- 前端请求统一经过 `lib/api/client.ts`，业务接口按 feature 拆分。
