# 智企云考

面向企业培训、岗位认证和人才测评的在线考试平台。平台覆盖企业题库、分类管理、固定/随机组卷、考试发布、员工答题、自动判分、成绩统计和公告资讯等完整流程。

## 项目结构

```text
ai-question/
├── backend/                 # Spring Boot API、业务服务、数据库迁移
├── frontend/                # Next.js 企业考试管理与答题前端
├── docs/                    # 开发规范与安全说明
├── .gitignore
└── README.md
```

## 技术栈

- 前端：Next.js 15、React 19、TypeScript、Tailwind CSS、Semi UI、Zustand
- 后端：Spring Boot 3、Java 17、MyBatis、Flyway、Spring Security、OpenAPI
- 数据：MySQL、Redis
- 基础设施：Docker Compose

## 功能范围

- 企业账号注册、登录和个人中心
- 企业题库、题库分类和题目管理
- 单选、多选、判断题维护
- 固定组卷和随机组卷
- 考试时间、次数、及格分数和答题规则配置
- 员工通过考试链接或二维码参加考试
- 答题进度保存、超时处理和交卷
- 成绩列表、通过率、统计图表和答卷查看
- 公告资讯从后端数据库读取
- 有偿出题、帮助手册等静态页面

## 本地启动

### 1. 准备环境

- Node.js 20+ 和 npm
- Java 17+
- Maven 3.9+（或项目环境中的 Maven Wrapper）
- Docker Desktop（用于 MySQL 和 Redis）

### 2. 配置后端

```bash
cd backend
cp .env.example .env
```

按需修改 `backend/.env`。真实数据库密码、JWT 密钥、阿里云短信和 OSS 密钥只放在本地环境变量中，不要提交到 Git。

### 3. 启动 MySQL 和 Redis

```bash
cd backend
./scripts/start-infra.sh
```

### 4. 启动后端

```bash
cd backend
./scripts/run-local.sh
```

默认 API 地址：<http://localhost:18080/api>

### 5. 启动前端

```bash
cd frontend
npm install
npm run dev -- --hostname 0.0.0.0 --port 13000
```

默认前端地址：<http://localhost:13000>

前端接口地址由 `NEXT_PUBLIC_API_BASE_URL` 控制，默认值为 `http://localhost:18080/api`。

### 停止基础设施

```bash
cd backend
./scripts/stop-infra.sh
```

## Docker 启动

后端的 Docker Compose 文件同时包含 API 和前端服务。使用前先准备环境变量：

```bash
cd backend
cp .env.example .env
docker compose up -d --build
```

Compose 默认端口可通过 `API_PORT` 和 `FRONTEND_PORT` 修改；本地开发推荐使用上面的 `18080` 和 `13000` 配置。

## 常用地址

| 服务 | 地址 |
| --- | --- |
| 前端 | <http://localhost:13000> |
| 后端 API | <http://localhost:18080/api> |
| Swagger UI | <http://localhost:18080/api/swagger-ui.html> |
| OpenAPI JSON | <http://localhost:18080/api/v3/api-docs> |

## 开发约定

- 数据库结构和初始化数据必须通过新增 Flyway 迁移文件维护，不直接修改已执行的迁移。
- 数据库表名使用小写蛇形命名，例如 `question_bank`、`assessment_task`。
- 对外接口统一返回 `ApiResponse<T>`。
- 前端请求统一经过 `frontend/lib/api/client.ts`，接口按业务 feature 拆分。
- 页面级功能放在 `frontend/app`，可复用组件放在 `frontend/components` 或对应 feature 目录。
- `main` 用于稳定代码，`dev` 用于日常开发和联调。

## 检查命令

```bash
cd frontend
npm run build
npx tsc --noEmit
```

```bash
cd backend
mvn test
```

## 安全说明

请阅读 [`docs/security-secrets.md`](docs/security-secrets.md)。禁止提交以下内容：

- `.env` 和任何真实密钥
- 数据库密码、JWT 密钥、短信/OSS 凭证
- `node_modules`、`.next`、`target` 等构建产物
