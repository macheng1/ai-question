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

### 数据访问（MyBatis）

- 运行时数据访问统一使用 `repository` 下的 `@Mapper` 接口和 `resources/mapper` 下的 XML；不在 Controller、Service 中使用 JdbcTemplate，也不使用注解 SQL。
- Repository 方法按业务动作命名，参数明确类型；多个参数使用 `@Param`，XML 使用 `#{参数名}`。动态条件使用 `if`、`choose`、`foreach`，不透传任意 SQL。
- Service 通过构造器注入 Repository 和其他 Service，不手动 `new` 业务 Service。事务、业务校验和对象存储回滚清理仍由 Service 管理。
- 自增主键使用 `useGeneratedKeys` 回填 `GeneratedId`；复制用的 `INSERT ... SELECT` 同样验证回填结果，不单独查询 `LAST_INSERT_ID()`。
- 现有复杂查询暂保留 Map 返回值及字段别名，空值字段也须保留。`RepositoryRows.one` 维持旧代码的单行查询语义；新增稳定数据模型优先使用明确的实体或查询结果类型。
- 保持 `local-cache-scope: STATEMENT`、`cache-enabled: false` 和 `call-setters-on-nulls: true`，避免锁定查询、实时计时读取旧缓存，或响应丢失空值字段。
- MyBatis 与 Spring 事务使用同一数据源；迁移或修改锁定查询时，保持原有加锁顺序、隔离级别和事务边界。
- `MapperContractTest` 检查 XML 解析、Repository 绑定及运行时 SQL 分层；实际 SQL/事务测试使用独立 MySQL，不能只依赖模拟 Repository 的单元测试。
- 历史 Flyway Java 迁移保留 JDBC 实现，不因运行时代码规范修改已执行迁移。

## 数据库

- 新增表使用小写蛇形命名。
- 主键字段统一为 `id`，时间字段统一为 `created_at`、`updated_at`。
- 所有迁移必须新建 `V3__...sql`、`V4__...sql` 文件，禁止修改已执行脚本。
- 高频查询字段必须建立索引，唯一业务字段必须建立唯一约束。
