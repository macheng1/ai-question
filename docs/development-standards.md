# 开发规范

## 前端

- `components` 只放跨页面可复用的展示组件，例如 Header、Footer、PageShell。
- `features` 按业务域拆分，每个业务域维护自己的 `api.ts`、`types.ts` 和组件。
- 页面只负责组合业务组件，不直接拼接请求地址。
- 所有后端请求经过 `lib/api/client.ts`，接口返回类型必须显式声明。
- 表单和按钮组件需要覆盖加载、成功、失败和空数据状态。

## 后端

- `controller` 只负责参数接收和响应组装，不写业务规则。
- `service` 负责业务流程和事务边界。
- `repository` 只负责数据访问。
- `domain` 只维护实体、字段映射和领域行为。
- 异常统一交给 `GlobalExceptionHandler`，禁止在 Controller 中重复 try/catch。
- 对外接口统一返回 `ApiResponse<T>`。

## 数据库

- 新增表使用小写蛇形命名。
- 主键字段统一为 `id`，时间字段统一为 `created_at`、`updated_at`。
- 所有迁移必须新建 `V3__...sql`、`V4__...sql` 文件，禁止修改已执行脚本。
- 高频查询字段必须建立索引，唯一业务字段必须建立唯一约束。
